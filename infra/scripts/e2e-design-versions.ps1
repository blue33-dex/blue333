# Service 6 (design versions) end-to-end check against a running Compose stack (http://localhost).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File infra\scripts\e2e-design-versions.ps1
# Each run creates one new demo order with two material tasks. Results: %TEMP%\ordermgmt-e2e-versions.log
$ErrorActionPreference = 'Stop'
$log = Join-Path $env:TEMP 'ordermgmt-e2e-versions.log'
$tmp = Join-Path $env:TEMP 'ordermgmt-e2e-versions'
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
# Steps 1 and 2 of the version upload; returns the session id.
function UploadFile($taskId, $token, $file) {
  $size = (Get-Item $file).Length
  $start = (Call 'POST' "/material-tasks/$taskId/versions" $token @{ file_name = (Split-Path $file -Leaf); file_size = $size }).data
  $put = & curl.exe -s -o NUL -w '%{http_code}' -X PUT --upload-file $file $start.presigned_url
  if ($put -ne '200') { throw "PUT failed with $put" }
  $start
}
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

  # Setup: AM order with two material specs + reference attachment, submitted and assigned to ds_demo.
  $order = (Call 'POST' '/orders' $am.access_token @{
      customer_name = 'Version E2E'; use_scene = 'Store opening'; delivery_deadline = '2026-12-31'
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

  # A-08 Step 1 validation
  Check 'A-08 rejects disallowed extension (400)' ((Code 'POST' "/material-tasks/$t1/versions" $ds.access_token @{ file_name = 'x.exe'; file_size = 10 }) -eq '400') ''
  Check 'A-08 rejects file over 200MB (400)' ((Code 'POST' "/material-tasks/$t1/versions" $ds.access_token @{ file_name = 'x.psd'; file_size = 209715201 }) -eq '400') ''
  Check 'A-08 forbidden for BO (403)' ((Code 'POST' "/material-tasks/$t1/versions" $bo.access_token @{ file_name = 'x.psd'; file_size = 10 }) -eq '403') ''

  # Steps 1-3 for V1 on task 1
  $f1 = NewFile 'design-v1.pdf'
  $s1 = UploadFile $t1 $ds.access_token $f1
  Check 'A-08 returns presigned URL and next_version_no V1' ($s1.presigned_url -like 'http://localhost:8333/*' -and $s1.next_version_no -eq 'V1') "key=$($s1.object_key)"
  Check 'S-09 requires design_note (400)' ((Code 'POST' '/versions/confirm-upload' $ds.access_token @{ upload_session_id = $s1.upload_session_id; design_note = '  ' }) -eq '400') ''
  $tampered = $s1.upload_session_id.Substring(0, $s1.upload_session_id.Length - 2) + 'AA'
  Check 'S-09 rejects tampered session (400)' ((Code 'POST' '/versions/confirm-upload' $ds.access_token @{ upload_session_id = $tampered; design_note = 'x' }) -eq '400') ''
  $v1 = (Call 'POST' '/versions/confirm-upload' $ds.access_token @{ upload_session_id = $s1.upload_session_id; design_note = '<b>First draft</b> with logo'; change_summary = 'initial' }).data
  Check 'S-09 creates V1 DRAFT with cleaned note' ($v1.version_no -eq 'V1' -and $v1.status -eq 'DRAFT' -and $v1.design_note -eq 'First draft with logo' -and -not $v1.is_overdue) "id=$($v1.id) note='$($v1.design_note)'"
  Check 'S-09 replay of the same session rejected (422)' ((Code 'POST' '/versions/confirm-upload' $ds.access_token @{ upload_session_id = $s1.upload_session_id; design_note = 'again' }) -eq '422') ''

  # V2 draft, then submit V2 (A-09)
  $s2 = UploadFile $t1 $ds.access_token (NewFile 'design-v2.pdf')
  $v2 = (Call 'POST' '/versions/confirm-upload' $ds.access_token @{ upload_session_id = $s2.upload_session_id; design_note = 'Second draft' }).data
  Check 'second upload gets V2' ($v2.version_no -eq 'V2') ''
  $sub = (Call 'POST' "/versions/$($v2.id)/submit-review" $ds.access_token $null).data
  $taskAfter = (Call 'GET' "/material-tasks/$t1" $ds.access_token $null).data.task
  Check 'A-09 version -> PENDING_INTERNAL_REVIEW, task -> PENDING_APPROVAL' ($sub.status -eq 'PENDING_INTERNAL_REVIEW' -and $sub.submitted_at -and $taskAfter.status -eq 'PENDING_APPROVAL') "version=$($sub.status) task=$($taskAfter.status)"
  Check 'A-09 second submit while under review (409)' ((Code 'POST' "/versions/$($v1.id)/submit-review" $ds.access_token $null) -eq '409') ''
  Check 'A-08 upload blocked while task PENDING_APPROVAL (409)' ((Code 'POST' "/material-tasks/$t1/versions" $ds.access_token @{ file_name = 'x.psd'; file_size = 10 }) -eq '409') ''
  Check 'A-09 forbidden for AM (403)' ((Code 'POST' "/versions/$($v1.id)/submit-review" $am.access_token $null) -eq '403') ''

  # A-26 history per role
  $boList = @((Call 'GET' "/orders/$oid/versions" $bo.access_token $null).data)
  $amList = @((Call 'GET' "/orders/$oid/versions" $am.access_token $null).data)
  $dsList = @((Call 'GET' "/orders/$oid/versions" $ds.access_token $null).data)
  Check 'A-26 BO/AM/DS see history newest first' ($boList.Count -eq 2 -and $amList.Count -eq 2 -and $dsList.Count -eq 2 -and $boList[0].version_no -eq 'V2' -and $boList[0].file.file_name -eq 'design-v2.pdf') "bo=$($boList.Count) am=$($amList.Count) ds=$($dsList.Count)"
  Check 'A-26 storage path not exposed' (-not (($boList | ConvertTo-Json -Depth 5) -match 'storage_path|versions/task-')) ''
  Check 'A-26 nonexistent order (404)' ((Code 'GET' '/orders/999999999/versions' $bo.access_token $null) -eq '404') ''

  # S-07 download rules
  $hdr = & curl.exe -s -D - -o NUL -H "Authorization: Bearer $($bo.access_token)" "$api/versions/$($v1.id)/download"
  $loc = (($hdr | Select-String -Pattern '(?i)^location:\s*(.+)$').Matches | Select-Object -First 1).Groups[1].Value.Trim()
  $dl = Join-Path $tmp 'dl.pdf'
  $getCode = & curl.exe -s -o $dl -w '%{http_code}' $loc
  Check 'S-07 BO download 302 -> identical file' ((($hdr | Select-Object -First 1) -match ' 302') -and $getCode -eq '200' -and (Get-FileHash $dl).Hash -eq (Get-FileHash $f1).Hash) "http=$getCode"
  $dsHdr = & curl.exe -s -o NUL -w '%{http_code}' -H "Authorization: Bearer $($ds.access_token)" "$api/versions/$($v1.id)/download"
  Check 'S-07 uploading DS may download (302)' ($dsHdr -eq '302') ''
  Check 'S-07 AM cannot download a non-final version (403)' ((Code 'GET' "/versions/$($v1.id)/download" $am.access_token $null) -eq '403') ''

  # Concurrency: 10 confirms on task 2 at the same time must get V1..V10 without gaps or duplicates.
  $n = 10
  $curlArgs = @('-s', '-Z', '--parallel-max', "$n")
  for ($i = 1; $i -le $n; $i++) {
    $s = UploadFile $t2 $ds.access_token (NewFile "par-$i.pdf")
    $bf = Join-Path $tmp "confirm-$i.json"
    [IO.File]::WriteAllText($bf, (@{ upload_session_id = $s.upload_session_id; design_note = "parallel $i" } | ConvertTo-Json))
    if ($i -gt 1) { $curlArgs += '--next' }
    $curlArgs += @('-s', '-o', (Join-Path $tmp "confirm-out-$i.json"), '-w', '%{http_code}\n', '-X', 'POST',
      '-H', 'Content-Type: application/json', '-H', "Authorization: Bearer $($ds.access_token)",
      '--data-binary', "@$bf", "$api/versions/confirm-upload")
  }
  $codes = @(& curl.exe @curlArgs | Where-Object { $_ })
  $nos = @(1..$n | ForEach-Object { (Get-Content (Join-Path $tmp "confirm-out-$_.json") -Raw -Encoding UTF8 | ConvertFrom-Json).data.version_no })
  $expected = @(1..$n | ForEach-Object { "V$_" })
  $sorted = @($nos | Sort-Object { [int]$_.Substring(1) })
  Check "parallel confirms: $n x 201, version_no V1..V$n unique" ((@($codes | Where-Object { $_ -eq '201' }).Count -eq $n) -and (($sorted -join ',') -eq ($expected -join ','))) "codes=$($codes -join ',') nos=$($sorted -join ',')"

  # Audit and notification side effects (read straight from MySQL)
  Start-Sleep 2
  $audit = Sql "select action_type, count(*) from audit_logs where target_type='DESIGN_VERSION' and target_id in ($($v1.id),$($v2.id)) and result='SUCCESS' group by action_type order by action_type"
  Check 'audit: UPLOAD_VERSION x2, SUBMIT_REVIEW x1' ((($audit -join ';') -replace '\s+', ' ') -eq 'SUBMIT_REVIEW 1;UPLOAD_VERSION 2') ($audit -join ';')
  $notif = Sql "select count(*) from notifications where resource_type='DESIGN_VERSION' and resource_id=$($v2.id) and notification_type='TODO'"
  Check 'notification: BO TODO for submitted version' (([int]($notif | Select-Object -First 1)) -ge 1) "count=$notif"
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
