# Phase 3 (client review / confirmation / delivery / archive) end-to-end check.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File infra\scripts\e2e-phase3.ps1
# Requires the Compose stack (http://localhost) and the demo users am_demo/bo_demo/ds_demo.
# Results: %TEMP%\ordermgmt-e2e-phase3.log
$ErrorActionPreference = 'Stop'
$log = Join-Path $env:TEMP 'ordermgmt-e2e-phase3.log'
$tmp = Join-Path $env:TEMP 'ordermgmt-e2e-phase3'
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
function CreateVersion($taskId, $token, $name, $note) {
  $s = UploadFile $taskId $token (NewFile $name)
  (Call 'POST' '/versions/confirm-upload' $token @{ upload_session_id = $s.upload_session_id; design_note = $note }).data
}
function Submit($versionId, $token) { (Call 'POST' "/versions/$versionId/submit-review" $token $null).data }
function Release($versionId, $token) { (Call 'POST' "/versions/$versionId/release" $token @{ release_remark = 'Released to client' }).data }
function TaskStatus($taskId, $token) { (Call 'GET' "/material-tasks/$taskId" $token $null).data.task.status }
function Sql($query) {
  $db = (docker exec ordermgmt-mysql-1 printenv MYSQL_DATABASE).Trim()
  $pwd = (docker exec ordermgmt-mysql-1 printenv MYSQL_ROOT_PASSWORD).Trim()
  $sqlFile = Join-Path $tmp ("sql-" + [guid]::NewGuid() + ".sql")
  [IO.File]::WriteAllText($sqlFile, "$query`n")
  $out = Get-Content $sqlFile -Raw | docker exec -i -e "MYSQL_PWD=$pwd" ordermgmt-mysql-1 mysql -uroot -N -B $db 2>$null
  @($out | Where-Object { $_ -ne '' })
}

try {
  $am = Login 'am_demo'; $bo = Login 'bo_demo'; $ds = Login 'ds_demo'

  # ---------- Order A: full client-review → confirmation → final → delivery → archive ----------
  $orderA = (Call 'POST' '/orders' $am.access_token @{
      customer_name = 'Phase3 E2E'; use_scene = 'Product launch'; delivery_deadline = '2026-12-31'
      material_specs = @(@{ material_type = 'POSTER'; spec_size = '60x90cm' })
    }).data
  $oid = $orderA.id
  $ref = NewFile 'ref-a.pdf'; $refSize = (Get-Item $ref).Length
  $pre = (Call 'POST' "/orders/$oid/attachments/presign" $am.access_token @{ file_name = 'ref-a.pdf'; file_size = $refSize }).data
  & curl.exe -s -o NUL -X PUT --upload-file $ref $pre.presigned_url | Out-Null
  Call 'POST' "/orders/$oid/attachments" $am.access_token @{ file_name = 'ref-a.pdf'; file_size = $refSize; object_key = $pre.object_key } | Out-Null
  Call 'POST' "/orders/$oid/submit" $am.access_token $null | Out-Null
  Call 'POST' "/orders/$oid/assign" $bo.access_token @{ assignee_id = 3; task_deadline = '2026-12-20T18:00:00'; priority = 'HIGH' } | Out-Null
  $tasks = @((Call 'GET' '/material-tasks/mine?limit=50' $ds.access_token $null).data | Where-Object { $_.order_id -eq $oid } | Sort-Object id)
  $t1 = $tasks[0].id
  Check 'setup A: order assigned with one task' ($tasks.Count -eq 1) "order=$oid task=$t1"

  $v1 = CreateVersion $t1 $ds.access_token 'phase3-v1.pdf' 'First draft'
  Check 'A-13 requires a released version (409 on DRAFT)' ((Code 'POST' "/versions/$($v1.id)/client-review" $am.access_token $null) -eq '409') ''
  Check 'A-13 forbidden for BO/DS (403)' (((Code 'POST' "/versions/$($v1.id)/client-review" $bo.access_token $null) -eq '403') -and ((Code 'POST' "/versions/$($v1.id)/client-review" $ds.access_token $null) -eq '403')) ''
  Check 'A-14 requires client review (409 on DRAFT)' ((Code 'POST' "/versions/$($v1.id)/feedbacks" $am.access_token @{ feedback_type = 'APPROVAL'; content = 'x' }) -eq '409') ''

  Submit $v1.id $ds.access_token | Out-Null
  Release $v1.id $bo.access_token | Out-Null
  Check 'A-16 rejects a released-only version (409 before A-13)' ((Code 'POST' "/versions/$($v1.id)/confirm" $am.access_token @{ confirmer_name = 'Client Jane' }) -eq '409') ''
  Check 'A-18 blocked while tasks are not APPROVED (409)' ((Code 'POST' "/orders/$oid/deliver" $am.access_token $null) -eq '409') ''
  Check 'A-18 forbidden for BO (403)' ((Code 'POST' "/orders/$oid/deliver" $bo.access_token $null) -eq '403') ''

  $cr1 = (Call 'POST' "/versions/$($v1.id)/client-review" $am.access_token $null).data
  Check 'A-13 creates an IN_REVIEW client review' ($cr1.status -eq 'IN_REVIEW' -and $cr1.initiator_name) "id=$($cr1.id)"
  Check 'A-13 duplicate client review (409)' ((Code 'POST' "/versions/$($v1.id)/client-review" $am.access_token $null) -eq '409') ''
  Check 'A-27 empty feedback list' (@((Call 'GET' "/versions/$($v1.id)/feedbacks" $am.access_token $null).data).Count -eq 0) ''

  Check 'A-14 blank content (400)' ((Code 'POST' "/versions/$($v1.id)/feedbacks" $am.access_token @{ feedback_type = 'APPROVAL'; content = '  ' }) -eq '400') ''
  $fb1 = (Call 'POST' "/versions/$($v1.id)/feedbacks" $am.access_token @{ feedback_type = 'MODIFICATION_SUGGESTION'; source = 'CLIENT'; content = '<b>Logo is too small</b>' }).data
  Check 'A-14 creates version-bound feedback and returns the task to PENDING_REVISE' ($fb1.feedback_no -match '^FB-\d{3,}$' -and $fb1.status -eq 'UNRESOLVED' -and $fb1.content -eq 'Logo is too small' -and (TaskStatus $t1 $ds.access_token) -eq 'PENDING_REVISE') "fb=$($fb1.feedback_no) content='$($fb1.content)'"
  $mineA = @((Call 'GET' '/orders/mine?limit=50' $am.access_token $null).data | Where-Object { $_.id -eq $oid })
  Check 'AM order list exposes client_review_status and open_feedback_count' ($mineA.Count -eq 1 -and $mineA[0].client_review_status -eq 'IN_REVIEW' -and $mineA[0].open_feedback_count -ge 1) "review=$($mineA[0].client_review_status) open=$($mineA[0].open_feedback_count)"
  Check 'A-15 forbidden for AM/BO (403)' (((Code 'PATCH' "/feedbacks/$($fb1.id)/resolve" $am.access_token $null) -eq '403') -and ((Code 'PATCH' "/feedbacks/$($fb1.id)/resolve" $bo.access_token $null) -eq '403')) ''
  $resolved = (Call 'PATCH' "/feedbacks/$($fb1.id)/resolve" $ds.access_token $null).data
  Check 'A-15 assigned designer resolves the feedback' ($resolved.status -eq 'RESOLVED') ''
  Check 'A-15 resolving twice (409)' ((Code 'PATCH' "/feedbacks/$($fb1.id)/resolve" $ds.access_token $null) -eq '409') ''

  $v2 = CreateVersion $t1 $ds.access_token 'phase3-v2.pdf' 'Second draft with larger logo'
  Submit $v2.id $ds.access_token | Out-Null
  Release $v2.id $bo.access_token | Out-Null
  Check 'A-16 rejects a stale version (409)' ((Code 'POST' "/versions/$($v1.id)/confirm" $am.access_token @{ confirmer_name = 'Client Jane' }) -eq '409') ''
  $cr2 = (Call 'POST' "/versions/$($v2.id)/client-review" $am.access_token $null).data
  Check 'A-13 second client review on V2' ($cr2.status -eq 'IN_REVIEW' -and $cr2.version_no -eq 'V2') ''
  $fb2 = (Call 'POST' "/versions/$($v1.id)/feedbacks" $am.access_token @{ feedback_type = 'APPROVAL'; source = 'BUSINESS_OWNER'; content = 'Client verbally likes V1' }).data
  Check 'A-14 records BO-sourced feedback on V1' ($fb2.feedback_no -match '^FB-\d{3,}$' -and $fb2.source -eq 'BUSINESS_OWNER') "fb=$($fb2.feedback_no)"

  Check 'A-16 blank confirmer_name (400)' ((Code 'POST' "/versions/$($v2.id)/confirm" $am.access_token @{ confirmer_name = '  ' }) -eq '400') ''
  $confirm = (Call 'POST' "/versions/$($v2.id)/confirm" $am.access_token @{ confirmer_name = 'Client Jane' }).data
  Check 'A-16 confirms V2, approves the task and returns no warning' ($confirm.version.status -eq 'CLIENT_CONFIRMED' -and $confirm.unresolved_feedback_count -eq 0 -and -not $confirm.warning -and (TaskStatus $t1 $ds.access_token) -eq 'APPROVED') "version=$($confirm.version.status) task=$(TaskStatus $t1 $ds.access_token)"
  $final = (Call 'POST' "/versions/$($v2.id)/mark-final" $ds.access_token $null).data
  Check 'A-17 marks V2 FINAL' ($final.status -eq 'FINAL' -and $final.is_final) ''
  $afterFinal = @((Call 'GET' "/versions/$($v1.id)/feedbacks" $am.access_token $null).data)
  $fb1State = ($afterFinal | Where-Object { $_.feedback_no -eq $fb1.feedback_no }).status
  $fb2State = ($afterFinal | Where-Object { $_.feedback_no -eq $fb2.feedback_no }).status
  Check 'EN-04 earlier-version feedback auto-expires on FINAL' ($fb1State -eq 'RESOLVED' -and $fb2State -eq 'EXPIRED') "resolved=$fb1State unresolved=$fb2State"
  Check 'A-15 EXPIRED feedback cannot be resolved (409)' ((Code 'PATCH' "/feedbacks/$($fb2.id)/resolve" $ds.access_token $null) -eq '409') ''
  Check 'A-17 second final version in the order (409)' ((Code 'POST' "/versions/$($v1.id)/mark-final" $ds.access_token $null) -eq '409') ''

  $delivered = (Call 'POST' "/orders/$oid/deliver" $am.access_token $null).data
  Check 'A-18 delivers the completed order' ($delivered.status -eq 'COMPLETED') "status=$($delivered.status)"
  Check 'A-18 delivering again (409)' ((Code 'POST' "/orders/$oid/deliver" $am.access_token $null) -eq '409') ''

  Check 'A-19 archive forbidden for AM (403)' ((Code 'POST' "/orders/$oid/archive" $am.access_token $null) -eq '403') ''
  $archive = (Call 'POST' "/orders/$oid/archive" $bo.access_token $null).data
  Check 'A-19 archives the completed order' ($archive.archive_report_available -and $archive.archived_at) "archived_at=$($archive.archived_at)"
  Check 'A-19 archiving twice (409)' ((Code 'POST' "/orders/$oid/archive" $bo.access_token $null) -eq '409') ''
  $completed = @((Call 'GET' '/orders/completed?limit=50' $bo.access_token $null).data | Where-Object { $_.id -eq $oid })
  Check 'BO archive queue flags the archived order' ($completed.Count -eq 1 -and $completed[0].archived -eq $true -and $completed[0].archived_at -and $completed[0].client_review_status -eq 'COMPLETED') "archived=$($completed[0].archived) at=$($completed[0].archived_at) review=$($completed[0].client_review_status)"
  Check 'BO archive queue forbidden for AM/DS (403)' (((Code 'GET' '/orders/completed' $am.access_token $null) -eq '403') -and ((Code 'GET' '/orders/completed' $ds.access_token $null) -eq '403')) ''

  $hdr = & curl.exe -s -D - -o NUL -H "Authorization: Bearer $($bo.access_token)" "$api/orders/$oid/archive-report"
  $loc = (($hdr | Select-String -Pattern '(?i)^location:\s*(.+)$').Matches | Select-Object -First 1).Groups[1].Value.Trim()
  $pdfFile = Join-Path $tmp 'archive-report.pdf'
  $pdfCode = & curl.exe -s -o $pdfFile -w '%{http_code}' $loc
  $pdfHead = (Get-Content $pdfFile -Encoding Byte -TotalCount 5 | ForEach-Object { [char]$_ }) -join ''
  Check 'A-28 BO exports the archive PDF (302 -> %PDF)' ((($hdr | Select-Object -First 1) -match ' 302') -and $pdfCode -eq '200' -and $pdfHead -eq '%PDF-') "http=$pdfCode head=$pdfHead"
  Check 'A-28 archive report forbidden for AM (403)' ((Code 'GET' "/orders/$oid/archive-report" $am.access_token $null) -eq '403') ''
  Check 'A-28 report for an unarchived order (404)' ((Code 'GET' '/orders/999999999/archive-report' $bo.access_token $null) -eq '404') ''

  Start-Sleep 2
  $audit = Sql "select action_type, count(*) from audit_logs where result='SUCCESS' and ((target_type='ORDER' and target_id=$oid) or (target_type='FEEDBACK_ITEM' and target_id in ($($fb1.id),$($fb2.id))) or (target_type='CONFIRMATION_RECORD' and target_id=(select id from confirmation_records where order_id=$oid))) group by action_type order by action_type"
  $auditText = (($audit -join ';') -replace '\s+', ' ')
  Check 'audit: RECORD_FEEDBACK 2, CONFIRM_VERSION 1, MARK_DELIVERED 1, ARCHIVE_ORDER 1' (
      $auditText -match 'ARCHIVE_ORDER 1' -and $auditText -match 'CONFIRM_VERSION 1' -and
      $auditText -match 'MARK_DELIVERED 1' -and $auditText -match 'RECORD_FEEDBACK 2') $auditText
  $db = Sql "select (select count(*) from confirmation_records where order_id=$oid), (select count(*) from delivery_archives where order_id=$oid), (select status from orders where id=$oid), (select status from feedback_items where feedback_no='$($fb2.feedback_no)')"
  Check 'DB: 1 confirmation, 1 archive, COMPLETED order, expired feedback' ((($db -join '') -replace '\s+', '|') -eq '1|1|COMPLETED|EXPIRED') ($db -join '|')

  # ---------- Order B: a CANCELLED task blocks delivery (CONFLICT-02) ----------
  $orderB = (Call 'POST' '/orders' $am.access_token @{
      customer_name = 'Phase3 Conflict'; use_scene = 'Trade show'; delivery_deadline = '2026-12-31'
      material_specs = @(@{ material_type = 'FLYER'; spec_size = 'A4' }, @{ material_type = 'BANNER'; spec_size = '80x200cm' })
    }).data
  $oidB = $orderB.id
  $refB = NewFile 'ref-b.pdf'; $refBSize = (Get-Item $refB).Length
  $preB = (Call 'POST' "/orders/$oidB/attachments/presign" $am.access_token @{ file_name = 'ref-b.pdf'; file_size = $refBSize }).data
  & curl.exe -s -o NUL -X PUT --upload-file $refB $preB.presigned_url | Out-Null
  Call 'POST' "/orders/$oidB/attachments" $am.access_token @{ file_name = 'ref-b.pdf'; file_size = $refBSize; object_key = $preB.object_key } | Out-Null
  Call 'POST' "/orders/$oidB/submit" $am.access_token $null | Out-Null
  Call 'POST' "/orders/$oidB/assign" $bo.access_token @{ assignee_id = 3; task_deadline = '2026-12-20T18:00:00'; priority = 'MEDIUM' } | Out-Null
  $tasksB = @((Call 'GET' '/material-tasks/mine?limit=50' $ds.access_token $null).data | Where-Object { $_.order_id -eq $oidB } | Sort-Object id)
  $bt1 = $tasksB[0].id; $bt2 = $tasksB[1].id
  $bv = CreateVersion $bt1 $ds.access_token 'phase3-b.pdf' 'Banner draft'
  Submit $bv.id $ds.access_token | Out-Null
  Release $bv.id $bo.access_token | Out-Null
  Call 'POST' "/versions/$($bv.id)/client-review" $am.access_token $null | Out-Null
  Call 'POST' "/versions/$($bv.id)/confirm" $am.access_token @{ confirmer_name = 'Client Bob' } | Out-Null
  Call 'POST' "/material-tasks/$bt2/cancel" $bo.access_token @{ reason = 'Customer removed the banner' } | Out-Null
  Check 'A-23 cancels the second task' ((TaskStatus $bt2 $bo.access_token) -eq 'CANCELLED') "status=$(TaskStatus $bt2 $bo.access_token)"
  Check 'CONFLICT-02: deliver blocked by a CANCELLED task (409)' ((Code 'POST' "/orders/$oidB/deliver" $am.access_token $null) -eq '409') ''
  $statusB = (Call 'GET' "/orders/$oidB" $am.access_token $null).data.status
  Check 'CONFLICT-02: order stays IN_PRODUCTION' ($statusB -eq 'IN_PRODUCTION') "status=$statusB"
  Check 'A-19 cannot archive an in-production order (409)' ((Code 'POST' "/orders/$oidB/archive" $bo.access_token $null) -eq '409') ''
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
