# 전략을 라운드로빈으로 N회 반복 측정하고 요약표를 출력한다(서버 상태 편향 방지).
# 사용: ./loadtest/repeat.ps1 -Runs 3
param([int]$Runs = 3, [int]$Capacity = 500, [int]$Rate = 300, [string]$Duration = '20s')

$strategies = 'none', 'pessimistic', 'optimistic', 'redis'
$rows = @()
for ($i = 1; $i -le $Runs; $i++) {
    foreach ($s in $strategies) {
        ./loadtest/run.ps1 -Mode sync -Strategy $s -Capacity $Capacity -Rate $Rate -Duration $Duration -Run $i *> $null
        $file = Join-Path $PSScriptRoot "results/sync-$s-${Rate}rps-run$i.json"
        $m = (Get-Content $file -Raw | ConvertFrom-Json).metrics
        $db = docker exec rtc-postgres psql -U rtc -d rtc -t -A -c "select count(*) from registration where exam_session_id=(select max(id) from exam_session)"
        $rows += [pscustomobject]@{
            run = $i; strategy = $s; accepted = $m.registrations_accepted.count; db_rows = [int]$db
            sold_out = $m.registrations_sold_out.count; completed = $m.iterations.count
            dropped = [int]$m.dropped_iterations.count
            avg_ms = [math]::Round($m.http_req_duration.avg); p95_ms = [math]::Round($m.http_req_duration.'p(95)')
        }
        Start-Sleep 3
    }
}
$rows | Format-Table -AutoSize
$rows | Group-Object strategy | ForEach-Object {
    [pscustomobject]@{
        strategy = $_.Name
        accepted = ($_.Group.accepted -join '/')
        p95_ms   = ($_.Group.p95_ms -join '/')
        avg_p95  = [math]::Round(($_.Group.p95_ms | Measure-Object -Average).Average)
        dropped  = ($_.Group.dropped -join '/')
    }
} | Format-Table -AutoSize
