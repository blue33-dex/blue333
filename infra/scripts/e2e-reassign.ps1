# P2-T08/T09 (A-07 reassign + A-23 cancel) end-to-end check against a running Compose stack.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File infra\scripts\e2e-reassign.ps1
# Seeds a second designer (id 9, ds_demo2) and creates one new demo order per run.
# Results: %TEMP%\ordermgmt-e2e-reassign.log
$ErrorActionPreference = 'Stop'
$log = Join-Path $env:TEMP 'ordermgmt-e2e-reassign.log'
$tmp = Join-Path $env:TEMP 'ordermgmt-e2e-reassign'
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$api = 'http://localhost/api/v1'
"E2E START $(Get-Date -Format o)" | Out-File $log -Encoding utf8
$results = @()

function Log($m) { $m | Out-File $log -Append -Encoding utf8 }
function Check($name, $ok, $detail) {
  $script:results += [pscustomobject]@{ Name = $name; Ok = [bool]$ok }
  Log ("[{0}] {1} :: {2}" -f ($(if ($ok) { 'PASS' } else { 'FAIL' })), $name, $detail)
}
function Call($method, $path, $token, $body) {
  $h = @{}
  if ($token) { $h['Authorization'] = "Bearer $token" }
  $a = @{ Method = $method; Uri = "$api$path"; Headers = $h; ContentType = 'application/json; charset=utf-8' }
  if ($null -ne $body) { $a['Body'] = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8)) }
  Invoke-RestMethod @a
}
# Sends a JSON request with curl and returns the HTTP status code only.
function Code($method, $path, $token, $body) {
  $a = @('-s', '-o', 'NUL', '-w', '%{http_code}', '-X', $method, '-H', 'Content-Type: application/json')
  if ($token) { $a += @('-H', "Authorization: Bearer $token") }
  if ($null -ne $body) {
    $bf = Join-Path $tmp ("body-" + [guid]::NewGuid() + ".json")
    [IO.File]::WriteAllText($bf, ($body | ConvertTo-Json -Depth 8))
    $a += @('--data-binary', "@$bf")
  }
  $a += "$api$path"
  (& curl.exe @a)
}
function Login($u) { (Call 'POST' '/auth/login' $null @{ username = $u; password = 'demo123' }).data }
function NewFile($name) {
  $p = Join-Path $tmp $name
  [IO.File]::WriteAllText($p, "%PDF-1.4`n% $name $(Get-Date -Format o) $([guid]::NewGuid())`n")
  $p
}
function UploadFile($taskId, $token, $file) {
  $size = (Get-Item $file).Length
  $start = (Call 'POST' "/material-tasks/$taskId/versions" $token @{ file_name = (Split-Path $file -Leaf); file_size = $size }).data
  $put = & curl.exe -s -o NUL -w '%{http_code}' -X PUT --upload-file $file $start.presigned_url
  if ($put -ne '200') { throw "PUT failed with $put" }
  $start
}
function Confirm($start, $token, $note, $summary) {
  $body = @{ upload_session_id = $start.upload_session_id; design_note = $note }
  if ($null -ne $summary) { $body['change_summary'] = $summary }
  (Call 'POST' '/versions/confirm-upload' $token $body).data
}
function TaskStatus($taskId, $token) { (Call 'GET' "/material-tasks/$taskId" $token $null).data.task.status }
function MyTasks($token) { @((Call 'GET' '/material-tasks/mine?limit=50' $token $null).data) }
function Sql($query) {
  # SQL goes through stdin: PowerShell 5 mangles nested double quotes in native command lines.
  $db = (docker exec ordermgmt-mysql-1 printenv MYSQL_DATABASE).Trim()
  $pwd = (docker exec ordermgmt-mysql-1 printenv MYSQL_ROOT_PASSWORD).Trim()
  $sqlFile = Join-Path $tmp ("sql-" + [guid]::NewGuid() + ".sql")
  [IO.File]::WriteAllText($sqlFile, "$query`n")
  $out = Get-Content $sqlFile -Raw | docker exec -i -e "MYSQL_PWD=$pwd" ordermgmt-mysql-1 mysql -uroot -N -B $db 2>$null
  @($out | Where-Object { $_ -ne '' })
}

try {
  # Seed designer #9 (ds_demo2) so a real handover target exists.
  # Single-quoted here-strings keep the bcrypt hash's $ characters literal.
  $seedUser = @'
insert into users (id, username, password_hash, name, email, phone, enabled) values (9, 'ds_demo2', '$2a$10$TXv5oSPWI9rqp2ifsDv9E.LnviEtnAyYPfxXId4DRw7bSxi9SiRsi', 'Second Designer', 'ds2@example.com', '13700000009', 1) on duplicate key update password_hash=values(password_hash), name='Second Designer', enabled=1
'@
  Sql $seedUser | Out-Null
  Sql "insert into user_roles (user_id, role_id) values (9, 3) on duplicate key update role_id=3" | Out-Null

  $am = Login 'am_demo'; $bo = Login 'bo_demo'; $ds = Login 'ds_demo'; $ds2 = Login 'ds_demo2'

  $designers = @((Call 'GET' '/users/designers' $bo.access_token $null).data)
  Check 'setup: second designer is available for handover' (@($designers | Where-Object { $_.id -eq 9 }).Count -eq 1) "designers=$(@($designers).Count)"

  # Setup: AM order with two material specs -> submit -> BO assigns both tasks to ds_demo (id 3).
  $order = (Call 'POST' '/orders' $am.access_token @{
      customer_name = 'Reassign E2E'; use_scene = 'Store opening'; delivery_deadline = '2026-12-31'
      material_specs = @(@{ material_type = 'POSTER'; spec_size = '60x90cm' }, @{ material_type = 'FLYER'; spec_size = 'A4' })
    }).data
  $oid = $order.id
  $ref = NewFile 'ref.pdf'; $refSize = (Get-Item $ref).Length
  $pre = (Call 'POST' "/orders/$oid/attachments/presign" $am.access_token @{ file_name = 'ref.pdf'; file_size = $refSize }).data
  & curl.exe -s -o NUL -X PUT --upload-file $ref $pre.presigned_url | Out-Null
  Call 'POST' "/orders/$oid/attachments" $am.access_token @{ file_name = 'ref.pdf'; file_size = $refSize; object_key = $pre.object_key } | Out-Null
  Call 'POST' "/orders/$oid/submit" $am.access_token $null | Out-Null
  Call 'POST' "/orders/$oid/assign" $bo.access_token @{ assignee_id = 3; task_deadline = '2026-12-20T18:00:00'; priority = 'HIGH' } | Out-Null
  $tasks = @((MyTasks $ds.access_token) | Where-Object { $_.order_id -eq $oid } | Sort-Object id)
  $t1 = $tasks[0].id; $t2 = $tasks[1].id
  Check 'setup: order assigned, 2 tasks' ($tasks.Count -eq 2) "order=$oid tasks=$t1,$t2"
  Check 'setup: new designer has no tasks of this order yet' (@((MyTasks $ds2.access_token) | Where-Object { $_.order_id -eq $oid }).Count -eq 0) ''

  # A-07: BO hands task 1 over to designer #9.
  $assigned = (Call 'POST' "/material-tasks/$t1/reassign" $bo.access_token @{
      assignee_id = 9; reason = 'Original designer on leave'; task_deadline = '2026-12-25T18:00:00'; priority = 'HIGH'
    }).data
  $handed = @($assigned.tasks) | Where-Object { $_.id -eq $t1 }
  Check 'A-07 reassign changes assignee' ($handed.assignee_id -eq 9 -and $handed.assignee_name -eq 'Second Designer') "assignee=$($handed.assignee_name)"
  $taskAfter = (Call 'GET' "/material-tasks/$t1" $bo.access_token $null).data.task
  Check 'A-07 reassign applies new deadline and priority' ($taskAfter.task_deadline -like '2026-12-25T18:00*' -and $taskAfter.priority -eq 'HIGH') "deadline=$($taskAfter.task_deadline) priority=$($taskAfter.priority)"
  Check 'A-07 old designer loses the task' (@((MyTasks $ds.access_token) | Where-Object { $_.id -eq $t1 }).Count -eq 0) ''
  Check 'A-07 new designer gains the task' (@((MyTasks $ds2.access_token) | Where-Object { $_.id -eq $t1 }).Count -eq 1) ''
  Check 'A-07 old designer cannot open the task anymore (404)' ((Code 'GET' "/material-tasks/$t1" $ds.access_token $null) -eq '404') ''

  # New assignee can work on the task: upload + submit for internal review.
  $v1 = Confirm (UploadFile $t1 $ds2.access_token (NewFile 'reassign-v1.pdf')) $ds2.access_token 'Draft by the new designer' 'initial'
  Call 'POST' "/versions/$($v1.id)/submit-review" $ds2.access_token $null | Out-Null
  Check 'A-07 new designer can upload and submit a version' ($v1.version_no -eq 'V1' -and (TaskStatus $t1 $bo.access_token) -eq 'PENDING_APPROVAL') "task=$(TaskStatus $t1 $bo.access_token)"
  Check 'A-07 old designer cannot upload to the task (404)' ((Code 'POST' "/material-tasks/$t1/versions" $ds.access_token @{ file_name = 'x.pdf'; file_size = 100 }) -eq '404') ''

  # A-07 validation and permissions.
  Check 'A-07 blank reason (400)' ((Code 'POST' "/material-tasks/$t1/reassign" $bo.access_token @{ assignee_id = 3; reason = '  ' }) -eq '400') ''
  Check 'A-07 same designer (400)' ((Code 'POST' "/material-tasks/$t1/reassign" $bo.access_token @{ assignee_id = 9; reason = 'same' }) -eq '400') ''
  Check 'A-07 non-designer assignee (400)' ((Code 'POST' "/material-tasks/$t1/reassign" $bo.access_token @{ assignee_id = 2; reason = 'bo' }) -eq '400') ''
  Check 'A-07 unknown assignee (404)' ((Code 'POST' "/material-tasks/$t1/reassign" $bo.access_token @{ assignee_id = 999999; reason = 'ghost' }) -eq '404') ''
  Check 'A-07 forbidden for DS (403)' ((Code 'POST' "/material-tasks/$t1/reassign" $ds2.access_token @{ assignee_id = 3; reason = 'ds' }) -eq '403') ''

  # Hand the task back while it is PENDING_APPROVAL; the handover trail must keep both rows.
  $returned = (Call 'POST' "/material-tasks/$t1/reassign" $bo.access_token @{ assignee_id = 3; reason = 'Original designer returned' }).data
  $back = @($returned.tasks) | Where-Object { $_.id -eq $t1 }
  Check 'A-07 reassign allowed while PENDING_APPROVAL' ($back.assignee_id -eq 3 -and (TaskStatus $t1 $bo.access_token) -eq 'PENDING_APPROVAL') "assignee=$($back.assignee_name)"
  Check 'A-07 deadline kept when omitted' (($back.task_deadline) -like '2026-12-25T18:00*') "deadline=$($back.task_deadline)"

  # A-23: BO cancels task 2.
  $cancelled = (Call 'POST' "/material-tasks/$t2/cancel" $bo.access_token @{ reason = 'Customer dropped this flyer' }).data
  $cancelledTask = @($cancelled.tasks) | Where-Object { $_.id -eq $t2 }
  Check 'A-23 cancel task -> CANCELLED' ($cancelledTask.status -eq 'CANCELLED') "status=$($cancelledTask.status)"
  Check 'A-23 blank reason (400)' ((Code 'POST' "/material-tasks/$t2/cancel" $bo.access_token @{ reason = '  ' }) -eq '400') ''
  Check 'A-23 cancelling twice (409)' ((Code 'POST' "/material-tasks/$t2/cancel" $bo.access_token @{ reason = 'again' }) -eq '409') ''
  Check 'A-23 forbidden for DS (403)' ((Code 'POST' "/material-tasks/$t2/cancel" $ds.access_token @{ reason = 'ds' }) -eq '403') ''
  Check 'A-07 reassigning a cancelled task (409)' ((Code 'POST' "/material-tasks/$t2/reassign" $bo.access_token @{ assignee_id = 9; reason = 'cancelled' }) -eq '409') ''
  Check 'A-23 cancelled task stays listed with CANCELLED status' (@((MyTasks $ds.access_token) | Where-Object { $_.id -eq $t2 -and $_.status -eq 'CANCELLED' }).Count -eq 1) ''
  Check 'A-07/A-23 new designer loses order visibility (404)' ((Code 'GET' "/orders/$oid" $ds2.access_token $null) -eq '404') ''

  # Persisted handover trail, audit trail and notifications.
  Start-Sleep 2
  $rows = Sql "select assignment_type, assignee_id, previous_assignee_id, remark from assignments where task_id=$t1 order by id"
  $rowText = (($rows -join ';') -replace '\s+', ' ')
  Check 'assignments: INITIAL + REASSIGN 9<-3 + REASSIGN 3<-9' ($rowText -match 'INITIAL 3' -and $rowText -match 'REASSIGN 9 3 Original designer on leave' -and $rowText -match 'REASSIGN 3 9 Original designer returned') $rowText
  $audit = Sql "select action_type, count(*) from audit_logs where target_type='MATERIAL_TASK' and target_id in ($t1,$t2) and result='SUCCESS' group by action_type order by action_type"
  $auditText = (($audit -join ';') -replace '\s+', ' ')
  Check 'audit: REASSIGN_TASK 2 + CANCEL_TASK 1' ($auditText -match 'REASSIGN_TASK 2' -and $auditText -match 'CANCEL_TASK 1') $auditText
  $notif = Sql "select recipient_id, notification_type, count(*) from notifications where resource_type='MATERIAL_TASK' and resource_id=$t1 group by recipient_id, notification_type order by recipient_id, notification_type"
  $notifText = (($notif -join ';') -replace '\s+', ' ')
  Check 'notifications reach both old and new designer' ($notifText -match '3 STATUS_CHANGE 1' -and $notifText -match '3 TODO 2' -and $notifText -match '9 STATUS_CHANGE 1' -and $notifText -match '9 TODO 1') $notifText
}
catch {
  $resp = $_.Exception.Response
  $body = ''
  if ($resp) { try { $body = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd() } catch {} }
  Check 'unexpected error' $false "$($_.Exception.Message) $body at line $($_.InvocationInfo.ScriptLineNumber)"
}
$pass = @($results | Where-Object Ok).Count
Log "SUMMARY pass=$pass total=$($results.Count)"
Log 'DONE'
Get-Content $log
if ($pass -ne $results.Count) { exit 1 }
