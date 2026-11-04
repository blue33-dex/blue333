$ErrorActionPreference = 'Stop'
# Phase 1 end-to-end check against a running Compose stack (http://localhost).
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File infra\scripts\e2e-phase1.ps1
# Results are written to e2e.log in the system temp folder. Each run creates one new demo order.
$log = Join-Path $env:TEMP 'ordermgmt-e2e.log'
$tmp = Join-Path $env:TEMP 'ordermgmt-e2e'
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
  $args = @{ Method = $method; Uri = "$api$path"; Headers = $h; ContentType = 'application/json; charset=utf-8' }
  if ($null -ne $body) { $args['Body'] = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8)) }
  Invoke-RestMethod @args
}
# Returns the HTTP status code only (no exception on 4xx/5xx).
function Status($method, $url, $token) {
  $a = @('-s', '-o', 'NUL', '-w', '%{http_code}', '-X', $method)
  if ($token) { $a += @('-H', "Authorization: Bearer $token") }
  $a += $url
  (& curl.exe @a)
}
function Login($u) { (Call 'POST' '/auth/login' $null @{ username = $u; password = 'demo123' }).data }

try {
  # 1. Login for all three demo roles
  $am = Login 'am_demo'; Check 'login am_demo/demo123' ($am.access_token) "user=$($am.user.username) roles=$($am.user.roles -join ',')"
  $bo = Login 'bo_demo'; Check 'login bo_demo/demo123' ($bo.access_token) "roles=$($bo.user.roles -join ',')"
  $ds = Login 'ds_demo'; Check 'login ds_demo/demo123' ($ds.access_token) "roles=$($ds.user.roles -join ',')"
  Check 'unauthenticated request rejected' ((Status 'GET' "$api/orders/mine" $null) -eq '401') 'GET /orders/mine without token'

  # 2. AM creates an order
  $order = (Call 'POST' '/orders' $am.access_token @{
      customer_name = 'E2E Customer'; use_scene = 'Store opening'; delivery_deadline = '2026-12-31'; remark = 'phase1 e2e'
      material_specs = @(@{ material_type = 'POSTER'; spec_size = '60x90cm' }, @{ material_type = 'FLYER'; spec_size = 'A4' })
    }).data
  $oid = $order.id
  Check 'AM create order' ($oid -and $order.status -eq 'PENDING_CONFIRM') "id=$oid no=$($order.order_no) status=$($order.status)"

  # 3. Presign -> direct PUT to SeaweedFS -> confirm
  $file = Join-Path $tmp 'brief.pdf'
  [IO.File]::WriteAllText($file, "%PDF-1.4`n% ordermgmt phase1 e2e $(Get-Date -Format o)`n")
  $size = (Get-Item $file).Length
  $pre = (Call 'POST' "/orders/$oid/attachments/presign" $am.access_token @{ file_name = 'brief.pdf'; file_size = $size }).data
  Check 'presign upload url' ($pre.presigned_url -like 'http://localhost:8333/*') "key=$($pre.object_key)"

  $cors = & curl.exe -s -D - -o NUL -X OPTIONS -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: PUT' $pre.presigned_url
  Check 'CORS preflight from http://localhost:5173' (($cors -join "`n") -match '(?i)access-control-allow-origin') (($cors | Select-String -Pattern '(?i)^(HTTP|access-control)') -join ' | ')

  $evil = & curl.exe -s -D - -o NUL -X OPTIONS -H 'Origin: http://evil.example' -H 'Access-Control-Request-Method: PUT' $pre.presigned_url
  Check 'CORS preflight from unlisted origin gets no allow header' (-not (($evil -join "`n") -match '(?i)access-control-allow-origin')) (($evil | Select-Object -First 1) -join '')

  $putCode = & curl.exe -s -o NUL -w '%{http_code}' -X PUT --upload-file $file $pre.presigned_url
  Check 'PUT file via presigned URL' ($putCode -eq '200') "http=$putCode size=$size"

  $att = (Call 'POST' "/orders/$oid/attachments" $am.access_token @{ file_name = 'brief.pdf'; file_size = $size; object_key = $pre.object_key }).data
  Check 'confirm attachment (statObject size match)' ($att.id) "attachment_id=$($att.id)"

  # 4. Submit
  $sub = (Call 'POST' "/orders/$oid/submit" $am.access_token $null).data
  Check 'AM submit order (stays PENDING_CONFIRM, BO notified)' ($sub.status -eq 'PENDING_CONFIRM') "status=$($sub.status)"

  # 5. BO review queue, review detail, designers, assign
  $queue = (Call 'GET' '/orders/review-queue?limit=50' $bo.access_token $null).data
  Check 'BO review-queue contains order' (@($queue | Where-Object { $_.id -eq $oid }).Count -eq 1) "queue_size=$(@($queue).Count)"
  $rev = (Call 'GET' "/orders/$oid/review" $bo.access_token $null).data
  Check 'BO review detail' ($rev.id -eq $oid) "attachments=$(@($rev.attachments).Count)"
  $designers = (Call 'GET' '/users/designers' $bo.access_token $null).data
  $dsUser = $designers | Where-Object { $_.username -eq 'ds_demo' } | Select-Object -First 1
  Check 'BO list designers includes ds_demo' ($dsUser) "ds_id=$($dsUser.id)"
  $asg = (Call 'POST' "/orders/$oid/assign" $bo.access_token @{ assignee_id = $dsUser.id; task_deadline = '2026-12-20T18:00:00'; priority = 'HIGH'; remark = 'e2e assign' }).data
  Check 'BO assign order to ds_demo' ($asg.status -eq 'IN_PRODUCTION') "status=$($asg.status)"

  # 6. DS sees tasks, task detail, downloads attachment
  $tasks = (Call 'GET' '/material-tasks/mine?limit=50' $ds.access_token $null).data
  $mine = @($tasks | Where-Object { $_.order_id -eq $oid })
  Check 'DS task list has 2 tasks for order' ($mine.Count -eq 2) "tasks=$($mine.Count) types=$(($mine | ForEach-Object material_type) -join ',')"
  $tid = $mine[0].id
  $td = (Call 'GET' "/material-tasks/$tid" $ds.access_token $null).data
  Check 'DS task detail {task, order}' ($td.task.id -eq $tid -and $td.order.id -eq $oid) "attachments=$(@($td.order.attachments).Count)"

  $hdr = & curl.exe -s -D - -o NUL -H "Authorization: Bearer $($ds.access_token)" "$api/attachments/$($att.id)/download"
  $loc = (($hdr | Select-String -Pattern '(?i)^location:\s*(.+)$').Matches | Select-Object -First 1).Groups[1].Value.Trim()
  Check 'DS download returns 302 presigned GET' ((($hdr | Select-Object -First 1) -match ' 302') -and $loc -like 'http://localhost:8333/*') ($hdr | Select-Object -First 1)
  $dl = Join-Path $tmp 'downloaded.pdf'
  $getCode = & curl.exe -s -o $dl -w '%{http_code}' $loc
  Check 'GET presigned download, content identical' ($getCode -eq '200' -and (Get-FileHash $dl).Hash -eq (Get-FileHash $file).Hash) "http=$getCode"

  # 7. Access-control negatives
  Check 'AM cannot read task detail (403)' ((Status 'GET' "$api/material-tasks/$tid" $am.access_token) -eq '403') ''
  Check 'DS cannot read review queue (403)' ((Status 'GET' "$api/orders/review-queue" $ds.access_token) -eq '403') ''
  Check 'nonexistent order is 404' ((Status 'GET' "$api/orders/999999999" $bo.access_token) -eq '404') ''
  Check 'unsigned bucket listing denied (403)' ((Status 'GET' 'http://localhost:8333/ordermgmt/' $null) -eq '403') ''
  Check 'unsigned object read denied (403)' ((Status 'GET' "http://localhost:8333/ordermgmt/$($pre.object_key)" $null) -eq '403') ''
}
catch {
  $resp = $_.Exception.Response
  $body = ''
  if ($resp) { try { $body = (New-Object IO.StreamReader($resp.GetResponseStream())).ReadToEnd() } catch {} }
  Check 'unexpected error' $false "$($_.Exception.Message) $body"
}
$pass = @($results | Where-Object Ok).Count
Log "SUMMARY pass=$pass total=$($results.Count)"
Log 'DONE'
Get-Content $log
if ($pass -ne $results.Count) { exit 1 }
