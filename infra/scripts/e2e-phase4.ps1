# Phase 4 (exceptions / kanban / timeline / notifications-SSE / cascade cancel / orphan sweep) E2E.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File infra\scripts\e2e-phase4.ps1
# Requires the Compose stack (http://localhost) and the demo users am_demo/bo_demo/ds_demo.
# Results: %TEMP%\ordermgmt-e2e-phase4.log
$ErrorActionPreference = 'Stop'
$log = Join-Path $env:TEMP 'ordermgmt-e2e-phase4.log'
$tmp = Join-Path $env:TEMP 'ordermgmt-e2e-phase4'
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$api = 'http://localhost/api/v1'
"E2E PHASE4 START $(Get-Date -Format o)" | Out-File $log -Encoding utf8
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
  $start = (Call 'POST' "/material-tasks/$taskId/versions" $token @{
      file_name = (Split-Path $file -Leaf); file_size = $size }).data
  & curl.exe -s -o NUL -X PUT --upload-file $file $start.presigned_url | Out-Null
  $start
}
function CreateVersion($taskId, $token, $name, $note) {
  $s = UploadFile $taskId $token (NewFile $name)
  (Call 'POST' '/versions/confirm-upload' $token @{ upload_session_id = $s.upload_session_id; design_note = $note }).data
}
function Submit($versionId, $token) { (Call 'POST' "/versions/$versionId/submit-review" $token $null).data }
function Release($versionId, $token) { (Call 'POST' "/versions/$versionId/release" $token @{ release_remark = 'Released to client' }).data }
function TaskStatus($taskId, $token) { (Call 'GET' "/material-tasks/$taskId" $token $null).data.task.status }
function OrderFile($oid, $amToken, $name) {
  $ref = NewFile $name; $size = (Get-Item $ref).Length
  $pre = (Call 'POST' "/orders/$oid/attachments/presign" $amToken @{ file_name = $name; file_size = $size }).data
  & curl.exe -s -o NUL -X PUT --upload-file $ref $pre.presigned_url | Out-Null
  Call 'POST' "/orders/$oid/attachments" $amToken @{ file_name = $name; file_size = $size; object_key = $pre.object_key } | Out-Null
}
function Kanban($token, $query) { (Call 'GET' "/orders/kanban$query" $token $null).data }
function ColumnOf($kb, $status) { $kb.columns | Where-Object { $_.status -eq $status } }
function Sql($query) {
  $db = (docker exec ordermgmt-mysql-1 printenv MYSQL_DATABASE).Trim()
  $pwd = (docker exec ordermgmt-mysql-1 printenv MYSQL_ROOT_PASSWORD).Trim()
  $out = docker exec -e "MYSQL_PWD=$pwd" ordermgmt-mysql-1 mysql -uroot -N -B $db -e "$query" 2>$null
  @($out | Where-Object { $_ -ne '' })
}

try {
  $am = Login 'am_demo'; $bo = Login 'bo_demo'; $ds = Login 'ds_demo'

  # ---------- Order X: overdue task, exceptions, kanban, timeline, notifications, archive gate ----------
  $orderX = (Call 'POST' '/orders' $am.access_token @{
      customer_name = 'Phase4 E2E'; use_scene = 'Product launch'; delivery_deadline = '2026-12-31'
      material_specs = @(@{ material_type = 'POSTER'; spec_size = '60x90cm' })
    }).data
  $oid = $orderX.id
  OrderFile $oid $am.access_token 'phase4-ref.pdf'
  Call 'POST' "/orders/$oid/submit" $am.access_token $null | Out-Null
  Call 'POST' "/orders/$oid/assign" $bo.access_token @{
      assignee_id = 3; task_deadline = '2026-11-03T18:00:00'; priority = 'HIGH' } | Out-Null
  $tasksX = @((Call 'GET' '/material-tasks/mine?limit=50' $ds.access_token $null).data |
      Where-Object { $_.order_id -eq $oid } | Sort-Object id)
  $t1 = $tasksX[0].id
  Check 'setup X: overdue task, order IN_PRODUCTION' (
      $tasksX.Count -eq 1 -and (TaskStatus $t1 $bo.access_token) -eq 'PENDING_DESIGN' -and
      (Call 'GET' "/orders/$oid" $bo.access_token $null).data.status -eq 'IN_PRODUCTION') "order=$oid task=$t1"

  # ---------- A-25 kanban ----------
  $kb = Kanban $bo.access_token ''
  $kbStatus = (($kb.columns | ForEach-Object { $_.status }) -join '|')
  $prod = ColumnOf $kb 'IN_PRODUCTION'
  $card = $prod.orders | Where-Object { $_.id -eq $oid }
  Check 'A-25 kanban has the four status columns' ($kb.columns.Count -eq 4 -and
      $kbStatus -eq 'PENDING_CONFIRM|IN_PRODUCTION|COMPLETED|CANCELLED') $kbStatus
  Check 'A-25 card carries task and open-feedback counters' ($card -and $card.task_count -eq 1 -and
      $card.open_feedback_count -eq 0 -and $card.order_no) "task_count=$($card.task_count) feedback=$($card.open_feedback_count)"
  Check 'A-25 am_id filter excludes other managers' (
      @((ColumnOf (Kanban $bo.access_token '?am_id=1') 'IN_PRODUCTION').orders).Count -ge 1 -and
      @((ColumnOf (Kanban $bo.access_token '?am_id=999') 'IN_PRODUCTION').orders).Count -eq 0) ''
  Check 'A-25 customer filter is sanitized and matched' (
      @((ColumnOf (Kanban $bo.access_token '?customer=%3Ci%3EPhase4%3C%2Fi%3E') 'IN_PRODUCTION').orders |
        Where-Object { $_.id -eq $oid }).Count -eq 1) ''
  $empty = Kanban $bo.access_token '?customer=NoSuchCustomer'
  Check 'A-25 unmatched filter returns empty columns' (
      @($empty.columns | Where-Object { $_.total -ne 0 -or @($_.orders).Count -ne 0 }).Count -eq 0) ''
  Check 'A-25 kanban forbidden for AM/DS (403)' (
      ((Code 'GET' '/orders/kanban' $am.access_token $null) -eq '403') -and
      ((Code 'GET' '/orders/kanban' $ds.access_token $null) -eq '403')) ''

  # ---------- A-20 / A-21 exception records ----------
  Check 'A-20 forbidden for AM (403)' (
      (Code 'POST' "/orders/$oid/exceptions" $am.access_token @{ exception_type = 'OTHER'; description = 'x' }) -eq '403') ''
  Check 'A-20 blank description (400)' (
      (Code 'POST' "/orders/$oid/exceptions" $bo.access_token @{ exception_type = 'OTHER'; description = '  ' }) -eq '400') ''
  Check 'A-20 unknown exception_type (400)' (
      (Code 'POST' "/orders/$oid/exceptions" $bo.access_token @{ exception_type = 'BOGUS'; description = 'x' }) -eq '400') ''
  $exManual = (Call 'POST' "/orders/$oid/exceptions" $bo.access_token @{
      exception_type = 'OTHER'; description = '  <b>Print</b> proof late  ' }).data
  Check 'A-20 records a PENDING_CONFIRM exception with the BO as recorder' (
      $exManual.status -eq 'PENDING_CONFIRM' -and $exManual.exception_type -eq 'OTHER' -and
      $exManual.description -eq 'Print proof late' -and $exManual.recorder_id -eq 2) "desc='$($exManual.description)' recorder=$($exManual.recorder_id)"
  Check 'A-20 order-scoped list returns the record' (
      @((Call 'GET' "/orders/$oid/exceptions" $bo.access_token $null).data).Count -eq 1) ''
  Check 'A-20 exception queue supports status filtering' (
      @((Call 'GET' '/exceptions?status=PENDING_CONFIRM&limit=50' $bo.access_token $null).data |
        Where-Object { $_.id -eq $exManual.id }).Count -eq 1) ''
  Check 'A-20 exception endpoints forbidden for AM (403)' (
      ((Code 'GET' "/orders/$oid/exceptions" $am.access_token $null) -eq '403') -and
      ((Code 'GET' '/exceptions' $am.access_token $null) -eq '403')) ''

  # ---------- P4-T05 overdue scan (manual BO trigger) ----------
  $scan1 = (Call 'POST' '/exceptions/auto-scan' $bo.access_token $null).data
  Check 'P4-T05 scan creates DELAY records for overdue tasks' ($scan1.created_count -ge 1 -and
      $scan1.created_ids.Count -ge 1) "created=$($scan1.created_count)"
  Check 'P4-T05 scan forbidden for AM (403)' ((Code 'POST' '/exceptions/auto-scan' $am.access_token $null) -eq '403') ''
  $scan2 = (Call 'POST' '/exceptions/auto-scan' $bo.access_token $null).data
  Check 'P4-T05 repeated scan is idempotent per (task, DELAY)' ($scan2.created_count -eq 0) "created=$($scan2.created_count)"
  $exAll = @((Call 'GET' "/orders/$oid/exceptions" $bo.access_token $null).data)
  $exAuto = $exAll | Where-Object { $_.exception_type -eq 'DELAY' -and $_.task_id -eq $t1 }
  Check 'P4-T05 automatic record has no recorder and points at the task' ($exAll.Count -eq 2 -and $exAuto -and
      $null -eq $exAuto.recorder_id -and $exAuto.recorder_name -and $exAuto.status -eq 'PENDING_CONFIRM') "records=$($exAll.Count) recorder='$($exAuto.recorder_name)'"

  # ---------- P4-T09 notifications + SSE ----------
  Start-Sleep 2
  $boNotif = @((Call 'GET' '/notifications?limit=50' $bo.access_token $null).data)
  $notifEx = $boNotif | Where-Object { $_.resource_type -eq 'EXCEPTION_RECORD' -and $_.resource_id -eq $exManual.id }
  Check 'P4-T09 exception event creates a BO notification' ($notifEx -and $notifEx.notification_type -eq 'TODO' -and
      -not $notifEx.read) "id=$($notifEx.id) title='$($notifEx.title)'"
  Check 'P4-T09 unread counter reflects pending notifications' (
      (Call 'GET' '/notifications/unread-count' $bo.access_token $null).data.unread_count -ge 2) ''
  $amNotif = @((Call 'GET' '/notifications?limit=50' $am.access_token $null).data)
  Check 'P4-T09 notifications are per-recipient (AM sees no exception rows)' (
      @($amNotif | Where-Object { $_.resource_type -eq 'EXCEPTION_RECORD' }).Count -eq 0) "am_notifications=$($amNotif.Count)"
  $read = (Call 'PATCH' "/notifications/$($notifEx.id)/read" $bo.access_token $null).data
  Check 'P4-T09 mark-read sets read_at' ($read.read -eq $true -and $read.read_at) ''
  Check 'P4-T09 another user cannot read the notification (404)' (
      (Code 'PATCH' "/notifications/$($notifEx.id)/read" $am.access_token $null) -eq '404') ''
  $readAll = (Call 'POST' '/notifications/read-all' $bo.access_token $null).data
  Check 'P4-T09 read-all clears the unread counter' ($readAll.updated_count -ge 1 -and
      (Call 'GET' '/notifications/unread-count' $bo.access_token $null).data.unread_count -eq 0) "updated=$($readAll.updated_count)"
  $sseHead = Join-Path $tmp 'sse-headers.txt'; $sseBody = Join-Path $tmp 'sse-body.txt'
  & curl.exe -s -N -m 3 -D $sseHead -o $sseBody -H "Authorization: Bearer $($bo.access_token)" "$api/notifications/stream" | Out-Null
  $sseText = Get-Content $sseBody -Raw
  $sseType = (Select-String -Path $sseHead -Pattern '(?i)content-type:\s*text/event-stream').Count -ge 1
  Check 'P4-T09 SSE stream sends the connected event through nginx' ($sseType -and
      $sseText -match 'event:connected' -and $sseText -match '"user_id"') "type=$sseType body=$($sseText -replace "`r`n", ' ')"
  Check 'P4-T09 SSE requires authentication (401)' ((Code 'GET' '/notifications/stream' $null $null) -eq '401') ''

  # ---------- A-29 timeline ----------
  $tl = @((Call 'GET' "/orders/$oid/timeline?limit=100" $bo.access_token $null).data)
  $actions = ($tl | ForEach-Object { $_.action_type }) -join '|'
  $labels = @($tl | Where-Object { $_.action_label -ne $_.action_type }).Count
  $ascending = $true
  for ($i = 1; $i -lt $tl.Count; $i++) { if ($tl[$i].id -le $tl[$i - 1].id) { $ascending = $false } }
  Check 'A-29 timeline lists order, task and exception entries in id order' (
      $actions -match 'CREATE_ORDER' -and $actions -match 'ASSIGN_TASK' -and
      $actions -match 'RECORD_EXCEPTION' -and $labels -ge 3 -and $ascending) $actions
  $tl1 = (Call 'GET' "/orders/$oid/timeline?limit=2" $bo.access_token $null)
  $tl2 = (Call 'GET' ("/orders/$oid/timeline?limit=2&cursor=" + [uri]::EscapeDataString($tl1.pagination.next_cursor)) $bo.access_token $null)
  Check 'A-29 cursor pagination continues the timeline' ($tl1.pagination.has_next -eq $true -and
      $tl2.data.Count -gt 0 -and $tl2.data[0].id -gt $tl1.data[$tl1.data.Count - 1].id) "first=$($tl1.data.Count) next=$($tl2.data.Count)"
  Check 'A-29 timeline visible to the assigned DS' (
      @((Call 'GET' "/orders/$oid/timeline?limit=10" $ds.access_token $null).data).Count -gt 0) ''

  # ---------- Delivery and the P4-T06 archive gate ----------
  $v1 = CreateVersion $t1 $ds.access_token 'phase4-v1.pdf' 'Final artwork'
  Submit $v1.id $ds.access_token | Out-Null
  Release $v1.id $bo.access_token | Out-Null
  Call 'POST' "/versions/$($v1.id)/client-review" $am.access_token $null | Out-Null
  Call 'POST' "/versions/$($v1.id)/confirm" $am.access_token @{ confirmer_name = 'Client Jane' } | Out-Null
  Call 'POST' "/versions/$($v1.id)/mark-final" $ds.access_token $null | Out-Null
  $delivered = (Call 'POST' "/orders/$oid/deliver" $am.access_token $null).data
  Check 'A-18 delivers the order while exceptions are still open' ($delivered.status -eq 'COMPLETED') "status=$($delivered.status)"
  Check 'P4-T06 archive blocked by open exceptions (422)' (
      (Code 'POST' "/orders/$oid/archive" $bo.access_token $null) -eq '422') ''
  Check 'P4-T06 rejected archive writes no delivery archive row' (
      ((Sql "select count(*) from delivery_archives where order_id=$oid") -join '') -eq '0') ''

  Check 'A-21 close of an unknown exception (404)' (
      (Code 'PATCH' '/exceptions/99999999/close' $bo.access_token @{ resolution = 'done' }) -eq '404') ''
  Check 'A-21 forbidden for AM (403)' (
      (Code 'PATCH' "/exceptions/$($exManual.id)/close" $am.access_token @{ resolution = 'done' }) -eq '403') ''
  $closed1 = (Call 'PATCH' "/exceptions/$($exManual.id)/close" $bo.access_token @{ resolution = '  <i>Re-proof</i> approved  ' }).data
  Check 'A-21 closes the manual exception with a sanitized resolution' ($closed1.status -eq 'CLOSED' -and
      $closed1.resolution -eq 'Re-proof approved') "resolution='$($closed1.resolution)'"
  Check 'A-21 closing twice (409)' (
      (Code 'PATCH' "/exceptions/$($exManual.id)/close" $bo.access_token @{ resolution = 'again' }) -eq '409') ''
  $closed2 = (Call 'PATCH' "/exceptions/$($exAuto.id)/close" $bo.access_token @{ resolution = 'Re-scheduled' }).data
  Check 'A-21 closes the automatic DELAY exception' ($closed2.status -eq 'CLOSED') ''
  $openRows = @((Call 'GET' "/orders/$oid/exceptions" $bo.access_token $null).data | Where-Object { $_.status -ne 'CLOSED' })
  Check 'P4-T06 all exceptions are closed before archiving' ($openRows.Count -eq 0) "open=$($openRows.Count)"
  $archive = (Call 'POST' "/orders/$oid/archive" $bo.access_token $null).data
  Check 'A-19 archives the order once every exception is closed' ($archive.archive_report_available -eq $true -and
      $archive.archived_at) "archived_at=$($archive.archived_at)"
  Check 'P4-T07 archived order keeps its kanban column' (
      @((ColumnOf (Kanban $bo.access_token '?customer=Phase4%20E2E') 'COMPLETED').orders |
        Where-Object { $_.id -eq $oid }).Count -eq 1) ''

  # ---------- A-22 cascade cancel ----------
  $orderY = (Call 'POST' '/orders' $am.access_token @{
      customer_name = 'Phase4 Cancel'; use_scene = 'Trade show'; delivery_deadline = '2026-12-31'
      material_specs = @(@{ material_type = 'FLYER'; spec_size = 'A4' }, @{ material_type = 'BANNER'; spec_size = '80x200cm' })
    }).data
  $oidY = $orderY.id
  OrderFile $oidY $am.access_token 'phase4-cancel.pdf'
  Call 'POST' "/orders/$oidY/submit" $am.access_token $null | Out-Null
  Call 'POST' "/orders/$oidY/assign" $bo.access_token @{
      assignee_id = 3; task_deadline = '2026-12-20T18:00:00'; priority = 'MEDIUM' } | Out-Null
  $tasksY = @((Call 'GET' '/material-tasks/mine?limit=50' $ds.access_token $null).data |
      Where-Object { $_.order_id -eq $oidY } | Sort-Object id)
  Check 'A-22 BO cancel requires a reason (400)' (
      (Code 'POST' "/orders/$oidY/cancel" $bo.access_token @{ reason = '  ' }) -eq '400') ''
  $cancelledY = (Call 'POST' "/orders/$oidY/cancel" $bo.access_token @{ reason = '<b>Client</b> removed the campaign' }).data
  $ys = ($tasksY | ForEach-Object { TaskStatus $_.id $bo.access_token }) -join '|'
  Check 'A-22 BO cancels an in-production order and cascades to every task' (
      $cancelledY.status -eq 'CANCELLED' -and $ys -eq 'CANCELLED|CANCELLED') "order=$($cancelledY.status) tasks=$ys"
  Check 'A-22 cancelled order is terminal (409)' (
      (Code 'POST' "/orders/$oidY/cancel" $bo.access_token @{ reason = 'again' }) -eq '409') ''
  Check 'A-22 AM cannot cancel an in-production order (409)' (
      (Code 'POST' "/orders/$oidY/cancel" $am.access_token $null) -eq '409') ''

  $orderZ = (Call 'POST' '/orders' $am.access_token @{
      customer_name = 'Phase4 Draft'; use_scene = 'Internal'; delivery_deadline = '2026-12-31'
      material_specs = @(@{ material_type = 'ROLLUP'; spec_size = '85x200cm' })
    }).data
  $oidZ = $orderZ.id
  OrderFile $oidZ $am.access_token 'phase4-draft.pdf'
  Call 'POST' "/orders/$oidZ/submit" $am.access_token $null | Out-Null
  Check 'A-22 BO cannot cancel a pending-confirmation order (409)' (
      (Code 'POST' "/orders/$oidZ/cancel" $bo.access_token @{ reason = 'n/a' }) -eq '409') ''
  $cancelledZ = (Call 'POST' "/orders/$oidZ/cancel" $am.access_token $null).data
  Check 'A-22 AM cancels their pending-confirmation draft without a reason' ($cancelledZ.status -eq 'CANCELLED') ''
  Check 'A-29 timeline of another AM order is not visible to DS (404)' (
      (Code 'GET' "/orders/$oidZ/timeline" $ds.access_token $null) -eq '404') ''
  Check 'A-20 cancelled orders cannot receive exceptions (409)' (
      (Code 'POST' "/orders/$oidZ/exceptions" $bo.access_token @{ exception_type = 'OTHER'; description = 'late' }) -eq '409') ''

  # ---------- P4-T11 orphan sweep operational trigger ----------
  $cleanup = (Call 'POST' '/storage/cleanup-orphans' $bo.access_token $null).data
  Check 'P4-T11 manual orphan sweep reports its counters' ($cleanup -and $cleanup.cleaned_at -and
      $cleanup.quarantined_count -ge 0 -and $cleanup.purged_count -ge 0 -and $cleanup.failed_count -ge 0) "quarantined=$($cleanup.quarantined_count) purged=$($cleanup.purged_count) failed=$($cleanup.failed_count)"
  Check 'P4-T11 orphan sweep forbidden for AM (403)' (
      (Code 'POST' '/storage/cleanup-orphans' $am.access_token $null) -eq '403') ''

  # ---------- DB / audit assertions ----------
  Start-Sleep 2
  $exDb = ((Sql "select status, exception_type from exception_records where order_id=$oid order by id") -join ';') -replace '\s+', ' '
  Check 'DB: two exceptions of order X are CLOSED' ($exDb -match 'CLOSED OTHER' -and $exDb -match 'CLOSED DELAY') $exDb
  $audit = ((Sql "select action_type, count(*) from audit_logs where result='SUCCESS' and ((target_type='ORDER' and target_id in ($oid,$oidY,$oidZ)) or (target_type='EXCEPTION_RECORD' and target_id in (select id from exception_records where order_id=$oid)) or (target_type='MATERIAL_TASK' and target_id in (select id from material_tasks where order_id=$oidY))) group by action_type order by action_type") -join ';') -replace '\s+', ' '
  Check 'audit: RECORD_EXCEPTION 1, CLOSE_EXCEPTION 2, CANCEL_ORDER 2, CANCEL_TASK 2' (
      $audit -match 'RECORD_EXCEPTION 1' -and $audit -match 'CLOSE_EXCEPTION 2' -and
      $audit -match 'CANCEL_ORDER 2' -and $audit -match 'CANCEL_TASK 2') $audit
  $archiveDb = ((Sql "select count(*) from delivery_archives where order_id=$oid") -join '') -replace '\s+', ''
  Check 'DB: exactly one delivery archive after the exception gate opened' ($archiveDb -eq '1') "archives=$archiveDb"
  $notifDb = ((Sql "select count(*) from notifications where resource_type='EXCEPTION_RECORD' and resource_id in (select id from exception_records where order_id=$oid)") -join '') -replace '\s+', ''
  Check 'DB: exception notifications were persisted' ([int]$notifDb -ge 2) "notifications=$notifDb"
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
