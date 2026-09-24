# 사용: ./loadtest/run.ps1 -Mode sync -Strategy redis -Capacity 500 -Rate 300 -Duration 20s [-Run 1]
# 사전 조건: docker compose up -d, 앱이 8080에서 실행 중(./gradlew bootRun).
param(
    [ValidateSet('sync', 'queue')][string]$Mode = 'sync',
    [string]$Strategy = 'redis',
    [int]$Capacity = 500,
    [int]$Rate = 300,
    [string]$Duration = '20s',
    [bool]$Pay = $false,
    [string]$Run = '1'
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path $PSScriptRoot -Parent
$outDir = Join-Path $PSScriptRoot 'results'
New-Item -ItemType Directory -Force $outDir | Out-Null
$tag = "$Mode-$Strategy-$Rate" + "rps-run$Run"

docker run --rm `
    --add-host=host.docker.internal:host-gateway `
    -v "${PSScriptRoot}:/scripts" `
    -e K6_PROMETHEUS_RW_SERVER_URL=http://host.docker.internal:9090/api/v1/write `
    -e MODE=$Mode -e STRATEGY=$Strategy -e CAPACITY=$Capacity -e RATE=$Rate -e DURATION=$Duration -e PAY=$($Pay.ToString().ToLower()) `
    grafana/k6:latest run /scripts/register.js `
    --summary-export "/scripts/results/$tag.json" `
    -o experimental-prometheus-rw

if ($Mode -eq 'queue') {
    Start-Sleep -Seconds 5  # 컨슈머가 큐를 비울 시간
}

Write-Host "`n--- DB 등록 건수 (최신 exam session; redis 전략은 seats_remaining을 DB에 반영하지 않음) ---"
docker exec rtc-postgres psql -U rtc -d rtc -c "select es.id, es.capacity, (select count(*) from registration r where r.exam_session_id = es.id) as registrations from exam_session es order by es.id desc limit 1"
