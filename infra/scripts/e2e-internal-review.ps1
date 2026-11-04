# Service 7 (internal review) end-to-end check against a running Compose stack (http://localhost).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File infra\scripts\e2e-internal-review.ps1
# Each run creates one new demo order with two material tasks. Results: %TEMP%\ordermgmt-e2e-review.log
$ErrorActionPreference = 'Stop'
$log = Join-Path $env:TEMP 'ordermgmt-e2e-review.log'
$tmp = Join-Path $env:TEMP 'ordermgmt-e2e-review'
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
# Steps 1 and 2 of the version upload; returns the start payload.
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
function Submit($versionId, $token) { (Call 'POST' "/versions/$versionId/submit-review" $token $null).data }
function TaskStatus($taskId, $token) { (Call 'GET' "/material-tasks/$taskId" $token $null).data.task.status }
function Sql($query) {
  # SQL goes through stdin: PowerShell 5 mangles nested double quotes in native command lines,
  # which broke `mysql -e "..."` when the statement contained quoted literals.
  $db = (docker exec ordermgmt-mysql-1 printenv MYSQL_DATABASE).Trim()
  $pwd = (docker exec ordermgmt-mysql-1 printenv MYSQL_ROOT_PASSWORD).Trim()
  $sqlFile = Join-Path $tmp ("sql-" + [guid]::NewGuid() + ".sql")
  [IO.File]::WriteAllText($sqlFile, "$query`n")
  $out = Get-Content $sqlFile -Raw | docker exec -i -e "MYSQL_PWD=$pwd" ordermgmt-mysql-1 mysql -uroot -N -B $db 2>$null
  @($out | Where-Object { $_ -ne '' })
}

try {
  $am = Login 'am_demo'; $bo = Login 'bo_demo'; $ds = Login 'ds_demo'

  # Setup: AM order with two material specs, submitted and assigned to ds_demo (id 3).
  $order = (Call 'POST' '/orders' $am.access_token @{
      customer_name = 'Internal Review E2E'; use_scene = 'Store opening'; delivery_deadline = '2026-12-31'
      material_specs = @(@{ material_type = 'POSTER'; spec_size = '60x90cm' }, @{ material_type = 'FLYER'; spec_size = 'A4' })
    }).data
  $oid = $order.id
  $ref = NewFile 'ref.pdf'; $refSize = (Get-Item $ref).Length
  $pre = (Call 'POST' "/orders/$oid/attachments/presign" $am.access_token @{ file_name = 'ref.pdf'; file_size = $refSize }).data
  & curl.exe -s -o NUL -X PUT --upload-file $ref $pre.presigned_url | Out-Null
  Call 'POST' "/orders/$oid/attachments" $am.access_token @{ file_name = 'ref.pdf'; file_size = $refSize; object_key = $pre.object_key } | Out-Null
  Call 'POST' "/orders/$oid/submit" $am.access_token $null | Out-Null
  Call 'POST' "/orders/$oid/assign" $bo.access_token @{ assignee_id = 3; task_deadline = '2026-12-20T18:00:00'; priority = 'HIGH' } | Out-Null
  $tasks = @((Call 'GET' '/material-tasks/mine?limit=50' $ds.access_token $null).data | Where-Object { $_.order_id -eq $oid } | Sort-Object id)
  $t1 = $tasks[0].id; $t2 = $tasks[1].id
  Check 'setup: order assigned, 2 tasks' ($tasks.Count -eq 2) "order=$oid tasks=$t1,$t2"

  # Task 1: two DRAFT versions, V2 submitted for internal review.
  $v1 = Confirm (UploadFile $t1 $ds.access_token (NewFile 'review-v1.pdf')) $ds.access_token 'First draft: initial layout' 'initial'
  $v2 = Confirm (UploadFile $t1 $ds.access_token (NewFile 'review-v2.pdf')) $ds.access_token 'Second draft: larger logo' 'logo bigger'
  Check 'versions V1/V2 created as DRAFT' ($v1.version_no -eq 'V1' -and $v2.version_no -eq 'V2' -and $v2.status -eq 'DRAFT') "v1=$($v1.id) v2=$($v2.id)"
  $submitted = Submit $v2.id $ds.access_token
  Check 'A-09 V2 under internal review, task PENDING_APPROVAL' ($submitted.status -eq 'PENDING_INTERNAL_REVIEW' -and (TaskStatus $t1 $bo.access_token) -eq 'PENDING_APPROVAL') ''

  # A-10 comparison: BO only, two distinct versions of the same order.
  $cmp = (Call 'GET' "/orders/$oid/versions/compare?v1=$($v1.id)&v2=$($v2.id)" $bo.access_token $null).data
  Check 'A-10 BO compares V1/V2 with change flags' ($cmp.left.version_no -eq 'V1' -and $cmp.right.version_no -eq 'V2' -and $cmp.design_note_changed -and $cmp.change_summary_changed) "left=$($cmp.left.design_note) right=$($cmp.right.design_note)"
  Check 'A-10 side-by-side files exposed, storage path hidden' ($cmp.left.file.file_name -eq 'review-v1.pdf' -and -not (($cmp | ConvertTo-Json -Depth 6) -match 'storage_path|versions/task-')) ''
  Check 'A-10 forbidden for DS (403)' ((Code 'GET' "/orders/$oid/versions/compare?v1=$($v1.id)&v2=$($v2.id)" $ds.access_token $null) -eq '403') ''
  Check 'A-10 forbidden for AM (403)' ((Code 'GET' "/orders/$oid/versions/compare?v1=$($v1.id)&v2=$($v2.id)" $am.access_token $null) -eq '403') ''
  Check 'A-10 same version twice (400)' ((Code 'GET' "/orders/$oid/versions/compare?v1=$($v2.id)&v2=$($v2.id)" $bo.access_token $null) -eq '400') ''
  Check 'A-10 unknown version (404)' ((Code 'GET' "/orders/$oid/versions/compare?v1=$($v1.id)&v2=999999999" $bo.access_token $null) -eq '404') ''

  # A-11 release: version RELEASED, task stays PENDING_APPROVAL (CONFLICT-01).
  $released = (Call 'POST' "/versions/$($v2.id)/release" $bo.access_token @{ release_remark = 'Approved, send to client' }).data
  Check 'A-11 release -> version RELEASED' ($released.status -eq 'RELEASED') ''
  Check 'CONFLICT-01: task still PENDING_APPROVAL after release' ((TaskStatus $t1 $bo.access_token) -eq 'PENDING_APPROVAL') ''
  Check 'A-11 release forbidden for DS (403)' ((Code 'POST' "/versions/$($v2.id)/release" $ds.access_token @{ release_remark = 'x' }) -eq '403') ''
  Check 'A-11 releasing again (409)' ((Code 'POST' "/versions/$($v2.id)/release" $bo.access_token @{ release_remark = 'again' }) -eq '409') ''
  Check 'A-11 releasing a DRAFT (409)' ((Code 'POST' "/versions/$($v1.id)/release" $bo.access_token @{ release_remark = 'x' }) -eq '409') ''
  Check 'A-17 mark-final before client confirmation (409)' ((Code 'POST' "/versions/$($v2.id)/mark-final" $ds.access_token $null) -eq '409') ''

  # Task 2: reject path, rework and resubmission.
  $w1 = Confirm (UploadFile $t2 $ds.access_token (NewFile 'review-w1.pdf')) $ds.access_token 'First draft: to review' 'initial'
  Submit $w1.id $ds.access_token | Out-Null
  Check 'A-12 blank review comment (400)' ((Code 'POST' "/versions/$($w1.id)/reject" $bo.access_token @{ review_comment = '  ' }) -eq '400') ''
  $rejected = (Call 'POST' "/versions/$($w1.id)/reject" $bo.access_token @{ review_comment = 'Logo too small, please adjust' }).data
  Check 'A-12 reject keeps version status, task -> PENDING_REVISE' ($rejected.status -eq 'PENDING_INTERNAL_REVIEW' -and (TaskStatus $t2 $bo.access_token) -eq 'PENDING_REVISE') "version=$($rejected.status)"
  Check 'A-12 reject forbidden for DS (403)' ((Code 'POST' "/versions/$($w1.id)/reject" $ds.access_token @{ review_comment = 'x' }) -eq '403') ''

  $w2 = Confirm (UploadFile $t2 $ds.access_token (NewFile 'review-w2.pdf')) $ds.access_token 'Second draft: logo adjusted' 'logo bigger'
  Submit $w2.id $ds.access_token | Out-Null
  Check 'rework: V2 uploaded after rejection and submitted' ($w2.version_no -eq 'V2' -and (TaskStatus $t2 $bo.access_token) -eq 'PENDING_APPROVAL') ''
  Check 'A-12 stale rejected version cannot be released (409)' ((Code 'POST' "/versions/$($w1.id)/release" $bo.access_token @{ release_remark = 'stale' }) -eq '409') ''
  Check 'A-11 release after rework (200)' ((Code 'POST' "/versions/$($w2.id)/release" $bo.access_token @{ release_remark = 'ok now' }) -eq '200') ''

  # A-17: simulate the Phase 3 client confirmation, then DS marks the final version.
  Sql "update design_versions set status='CLIENT_CONFIRMED' where id=$($v2.id)" | Out-Null
  $final = (Call 'POST' "/versions/$($v2.id)/mark-final" $ds.access_token $null).data
  Check 'A-17 mark-final -> FINAL with is_final flag' ($final.status -eq 'FINAL' -and $final.is_final) ''
  Sql "update design_versions set status='CLIENT_CONFIRMED' where id=$($w2.id)" | Out-Null
  Check 'A-17 second final version in the same order (409)' ((Code 'POST' "/versions/$($w2.id)/mark-final" $ds.access_token $null) -eq '409') ''
  Check 'A-17 forbidden for BO (403)' ((Code 'POST' "/versions/$($v2.id)/mark-final" $bo.access_token $null) -eq '403') ''

  # Persisted review/release records, notifications and audit trail.
  Start-Sleep 2
  $records = Sql "select review_result, count(*) from review_records where version_id in ($($v2.id),$($w1.id),$($w2.id)) group by review_result order by review_result"
  Check 'review_records: 2 PASS + 1 REJECT' ((($records -join ';') -replace '\s+', ' ') -eq 'PASS 2;REJECT 1') ($records -join ';')
  $releases = Sql "select count(*), sum(release_remark='Approved, send to client') from release_records where version_id in ($($v2.id),$($w2.id))"
  Check 'release_records: 2 rows with releaser and remark' (([int](($releases -split "`t")[0]) -eq 2) -and ([int](($releases -split "`t")[1]) -eq 1)) ($releases -join ';')
  $notif = Sql "select count(*) from notifications where resource_type='DESIGN_VERSION' and resource_id=$($v2.id)"
  Check 'notifications written for released version' (([int]($notif | Select-Object -First 1)) -ge 1) "count=$notif"
  $audit = Sql "select action_type, count(*) from audit_logs where target_type='DESIGN_VERSION' and target_id in ($($v1.id),$($v2.id),$($w1.id),$($w2.id)) and result='SUCCESS' group by action_type order by action_type"
  $auditText = (($audit -join ';') -replace '\s+', ' ')
  Check 'audit: RELEASE_VERSION 2, REJECT_VERSION 1, MARK_FINAL 1, SUBMIT_REVIEW 3' ($auditText -match 'MARK_FINAL 1' -and $auditText -match 'REJECT_VERSION 1' -and $auditText -match 'RELEASE_VERSION 2' -and $auditText -match 'SUBMIT_REVIEW 3') $auditText
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
