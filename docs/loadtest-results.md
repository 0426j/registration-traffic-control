# 부하테스트 결과 (k6 + Prometheus/Grafana)

## 측정 조건

- 도구: k6(Docker `grafana/k6`), `loadtest/register.js` + `loadtest/run.ps1`
- 시나리오: 정원 500석 시험 1개에 20초간 `constant-arrival-rate` 요청(요청마다 고유 userId/idempotencyKey), 결제 호출 없음(`PAY=false`)
- 환경: 개발 PC 1대에 앱(`bootRun`)·Postgres·Redis·k6·Prometheus·Grafana 전부 동거 → 절대 수치가 아니라 **전략 간 상대 비교**용
- 앱 설정: Hikari 기본 풀(10), Tomcat 기본 스레드(200)
- 지표 확인: Grafana `http://localhost:3000` (대시보드 "접수 트래픽 제어 개요"), Prometheus `http://localhost:9090`

## 동기 API — 전략별 비교 (300 req/s × 20s, 정원 500)

| 전략 | 확정(201) 건수 | 정원 초과 | 응답 평균 | 응답 p95 | 완료 요청 수(전체 6,000 중) |
|---|---|---|---|---|---|
| none (락 없음) | 3,190 | **발생 (6.4배)** | 12.6s | 18.7s | 전부 |
| pessimistic | 500 | 없음 | 4.6s | 7.1s | 4,851 |
| optimistic (CAS 재시도) | 500 | 없음 | 27.5s | 33.8s | 2,262 (k6 maxVUs 2,000 소진) |
| redis (원자적 차감) | 500 | 없음 | 65ms | 401ms | 6,001 |

- none: 정원 초과가 부하에서 그대로 재현됨(DB `registration` 3,190행). 정합성 threshold(`registrations_accepted <= CAPACITY`) 실패가 정상
- pessimistic: 정확하지만 한 행 락에 요청이 직렬화되어 Hikari 풀 10개가 락 대기로 점유 → 마감 이후 거절 응답까지 대기
- optimistic: 충돌이 많은 선착순 상황에서 재시도 폭증 → 처리량이 가장 낮고 지연이 가장 큼. 저경합에서는 유리하나 이 워크로드에는 부적합
- redis: 좌석 차감이 Redis 원자 연산이라 DB 락 경합 없음. 마감 후 요청은 Redis에서 즉시 거절

## 대기열 API (Redis Stream, `POST .../registrations/queue`)

| 부하 | 적재 응답 평균 | 적재 응답 p95 | 적재 성공 | DB 최종 확정 |
|---|---|---|---|---|
| 300 req/s × 20s | 11.8ms | 31.7ms | 6,000 / 6,000 | 500 |
| 1,000 req/s × 20s | 295ms | 760ms | 17,812 (≈847/s, 목표치 미달) | 500 |

- 300 req/s: 큐가 흡수해 응답이 부하와 무관하게 빠름(동기 redis 전략 p95 401ms 대비 약 1/13)
- 1,000 req/s: 같은 PC에서 k6가 CPU를 나눠 쓰는 영향으로 목표 처리율 미달 — 절대 한계가 아니라 측정 환경 한계
- 컨슈머는 단일 워커로 초당 약 300건 소화: 1,000 req/s 20초분(약 1.8만 건) 백로그가 1분 이상 걸려 비워짐. 정원 500석은 초기에 확정되지만 마감 이후 요청도 같은 경로로 처리되어 백로그가 남음
- 개선 후보: 정원 소진 후 컨슈머가 마감 요청을 빠르게 폐기(사전 조회), 컨슈머 다중화

## 지표 목록

| 지표 | 의미 |
|---|---|
| `registration_duration_seconds` (tag: strategy, result) | 접수 처리 시간·건수. result = success/no_seats/retry_exhausted/duplicate/error |
| `payment_result_total` (tag: result) | Mock 결제 성공/실패 건수 |
| `registration_queue_enqueued_total` | 큐 적재 건수 |
| `registration_queue_processed_total` (tag: result) | 컨슈머 처리 결과 success/no_seats/dropped |
| `registration_queue_backlog` | 컨슈머 미처리 메시지(lag) + ack 전 메시지(pending) |
| `http_server_requests_seconds`, `hikaricp_*`, `tomcat_*`, `jvm_*` | Spring Boot 기본 지표 |

## 재현 방법

```powershell
docker compose up -d                 # Postgres, Redis, Prometheus, Grafana
./gradlew bootRun                    # 앱 (8080)
./loadtest/run.ps1 -Mode sync  -Strategy redis -Capacity 500 -Rate 300 -Duration 20s
./loadtest/run.ps1 -Mode queue -Capacity 500 -Rate 300 -Duration 20s
```

- k6 요약은 `loadtest/results/*.json`(git 제외), k6 자체 지표는 Prometheus remote write로도 전송됨
