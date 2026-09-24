# 부하테스트 결과 (k6 + Prometheus/Grafana)

## 측정 조건

- 도구: k6(Docker `grafana/k6`), `loadtest/register.js` + `loadtest/run.ps1`
- 시나리오: 정원 500석 시험 1개에 20초간 `constant-arrival-rate` 요청(요청마다 고유 userId/idempotencyKey), 결제 호출 없음(`PAY=false`)
- 환경: 개발 PC 1대에 앱(`bootRun`)·Postgres·Redis·k6·Prometheus·Grafana 전부 동거 → 절대 수치가 아니라 **전략 간 상대 비교**용. 앱 시작 직후 워밍업 실행을 버리고 측정
- 앱 설정: Hikari 기본 풀(10), Tomcat 기본 스레드(200)
- 지표 확인: Grafana `http://localhost:3000` (대시보드 "접수 트래픽 제어 개요"), Prometheus `http://localhost:9090`

## 동기 API — 전략별 비교 (300 req/s × 20s, 정원 500, 라운드로빈 3회 반복)

앱 워밍업 후 `loadtest/repeat.ps1 -Runs 3`으로 4개 전략을 번갈아 측정. 표의 3개 값은 1/2/3회차.

| 전략 | 확정(201) 건수 | DB 등록 행 | 정원 초과 | 응답 p95 (s) | 응답 평균 (s) | k6 미발사(dropped) / 6,000 |
|---|---|---|---|---|---|---|
| none (락 없음) | 3,032 / 4,243 / 4,222 | 확정 건수와 동일 | **매번 발생 (6〜8.5배)** | 21.6 / 14.4 / 15.4 | 13.9 / 8.5 / 8.8 | 2,969 / 1,758 / 1,779 |
| pessimistic | 500 / 500 / 500 | 500 | 없음 | 10.6 / 3.8 / 3.6 | 7.6 / 2.6 / 2.2 | 2,007 / 653 / 486 |
| optimistic (CAS 재시도) | 500 / 500 / 500 | 500 | 없음 | 32.0 / 34.3 / 32.5 | 25.8 / 27.9 / 26.0 | 3,725 / 3,750 / 3,724 |
| redis (원자적 차감) | 500 / 500 / 500 | 500 | 없음 | 0.13 / 0.14 / 0.10 | 0.025 / 0.028 / 0.022 | 0 / 0 / 0 |

- none: 3회 모두 정원 초과. 확정 건수가 매번 달라 lost update의 비결정성이 드러남
- pessimistic: 정확하지만 1회차만 p95 10.6s로 튐(이전 none 회차의 잔여 요청 영향 추정 — 원인 미확정). 2〜3회차는 약 3.6s
- redis: DB 락 경합이 없어 3회 모두 p95 150ms 미만, 발사 누락 0
- 이전 1회 측정에서 redis p95가 401ms였던 것은 콜드 스타트(JIT·풀 초기화) 영향 — 워밍업 후 재측정으로 정정

### optimistic이 느린 원인 (확인 완료)

- 정합성은 문제 없음: 3회 모두 정확히 500건, DB 행 500. 서버 지표상 재시도 소진(`retry_exhausted`)은 누적 6건뿐
- 느림은 k6 한계가 아니라 서버 측 현상: 서버가 직접 측정한 `registration.duration` 평균이 success 9.5s, retry_exhausted 32.4s
- 저부하에서도 재현: 50 req/s에서도 p95 14.7s, 미발사 254건
- 원인(지표로 확인): 100 req/s에서 Hikari 커넥션 active 10/10(최대), pending 189, Tomcat busy 스레드 200(포화). 같은 값을 읽은 요청들이 CAS에서 한 건만 성공하고 나머지는 백오프 없이 즉시 재시도(최대 30회, 시도마다 `REQUIRES_NEW` 트랜잭션·커넥션)하며 풀을 다 써버림. 같은 조건의 redis는 active 0〜1, pending 0
- k6 미발사(dropped) 약 3,700건은 서버 응답이 느려 k6 VU가 소진(maxVUs 2,000)된 결과로, 위 원인의 부수 효과
- 개선 후보: 재시도에 지터 백오프, 커넥션 풀 확대, 낮은 경합 워크로드에서만 사용

## 대기열 API (Redis Stream, `POST .../registrations/queue`)

### 300 req/s × 20s, 정원 500 (워밍업 후 3회)

| 회차 | 적재 성공 | 적재 응답 평균 | 적재 응답 p95 | DB 최종 확정 |
|---|---|---|---|---|
| 1 | 6,000 | 6.6ms | 13.7ms | 500 |
| 2 | 6,001 | 6.0ms | 10.8ms | 500 |
| 3 | 6,001 | 5.8ms | 10.3ms | 500 |

- 3회 모두 발사 누락 0, 예상 외 응답 0, 백로그가 0이 된 뒤 확정 건수 500(정원 정확)
- 동기 redis 전략(p95 0.10〜0.14s) 대비 적재 응답이 약 10배 빠름
- 이전 1회 측정의 p95 32ms는 콜드 스타트 값

### 1,000 req/s × 20s (각 1회, 참고용)

| 조건 | 적재 응답 평균 | 적재 응답 p95 | 적재 성공 |
|---|---|---|---|
| 앱 재시작 직후 | 1.57s | 4.81s | 13,590 (≈555/s) |
| 두 번째 실행 | 295ms | 760ms | 17,812 (≈847/s) |

- 같은 PC에서 k6가 CPU를 나눠 쓰므로 목표 1,000/s를 못 채움 — 절대 한계가 아니라 측정 환경 한계이며, 콜드/워밍에 따라 편차가 큼(1회 측정이라 결론용 아님)
- 컨슈머는 단일 워커로 초당 약 300건 소화: 백로그 약 1.7만 건이 1분 이상 걸려 비워짐. 정원 500석은 초기에 확정되지만 마감 이후 요청도 같은 경로로 처리되어 백로그가 남음
- 개선 후보: 정원 소진 후 컨슈머가 마감 요청을 빠르게 폐기(사전 조회), 컨슈머 다중화

## 대시보드 검증

- Grafana 화면을 헤드리스 Edge로 캡처해 11개 패널 전부 데이터가 그려지는 것을 육안 확인(접수 처리량·p95, HTTP 처리량·p95, 대기열 적재/처리·백로그, 결제 결과, Hikari, JVM CPU·힙, Tomcat 스레드)
- 육안 확인 중 발견해 수정한 결함: 결제 패널이 실제보다 낮게 표시됨(고정 `[10s]` 구간이 Grafana 조회 간격 15s 사이 트래픽을 누락) → `$__rate_interval` + 데이터소스 `timeInterval: 5s`로 변경
- 같은 방식으로 발견해 수정: CPU(0〜1)와 힙(수백 MiB)을 한 축에 그려 CPU가 0으로 보이던 문제 → 패널 분리
- Tomcat 스레드 지표는 `server.tomcat.mbeanregistry.enabled=true`가 있어야 노출됨

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
./loadtest/repeat.ps1 -Runs 3         # 4개 전략 라운드로빈 반복 측정
```

- k6 요약은 `loadtest/results/*.json`(git 제외), k6 자체 지표는 Prometheus remote write로도 전송됨
