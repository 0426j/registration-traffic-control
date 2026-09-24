// 선착순 접수 부하 시나리오.
//   MODE=sync  : POST /registrations?strategy=<STRATEGY> (none|pessimistic|optimistic|redis), 201=성공 / 409=정원 마감
//   MODE=queue : POST /registrations/queue (202 = 큐 적재 완료, 실제 확정은 컨슈머가 비동기 처리)
// 정원 초과 검증: MODE=sync 이고 PAY=false 일 때 확정(201) 건수가 CAPACITY 이하여야 한다(threshold).
//   none 전략은 lost update로 이 threshold가 실패하는 것이 정상(버그 재현).
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

http.setResponseCallback(http.expectedStatuses(201, 202, 409));

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const MODE = __ENV.MODE || 'sync';
const STRATEGY = __ENV.STRATEGY || 'redis';
const CAPACITY = parseInt(__ENV.CAPACITY || '500');
const RATE = parseInt(__ENV.RATE || '300');
const DURATION = __ENV.DURATION || '20s';
const PAY = (__ENV.PAY || 'false') === 'true';

const accepted = new Counter('registrations_accepted');
const soldOut = new Counter('registrations_sold_out');
const unexpected = new Counter('registrations_unexpected');

const thresholds = {
  registrations_unexpected: ['count==0'],
};
if (MODE === 'sync' && !PAY) {
  thresholds['registrations_accepted'] = [`count<=${CAPACITY}`];
}

export const options = {
  scenarios: {
    burst: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.min(RATE, 500),
      maxVUs: 2000,
    },
  },
  thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };

export function setup() {
  const res = http.post(
    `${BASE_URL}/api/exam-sessions`,
    JSON.stringify({ name: `load-${MODE}-${STRATEGY}-${Date.now()}`, capacity: CAPACITY }),
    JSON_HEADERS,
  );
  if (res.status !== 201) {
    throw new Error(`exam session 생성 실패: ${res.status} ${res.body}`);
  }
  const id = res.json('id');
  console.log(`exam session id=${id} capacity=${CAPACITY} mode=${MODE} strategy=${STRATEGY} rate=${RATE}/s duration=${DURATION}`);
  return { examSessionId: id };
}

export default function (data) {
  const userId = `u-${__VU}-${__ITER}`;
  const body = JSON.stringify({ userId, idempotencyKey: `${data.examSessionId}-${userId}` });
  const base = `${BASE_URL}/api/exam-sessions/${data.examSessionId}/registrations`;

  if (MODE === 'queue') {
    const res = http.post(`${base}/queue`, body, JSON_HEADERS);
    if (res.status === 202) accepted.add(1);
    else unexpected.add(1);
    check(res, { 'enqueued (202)': (r) => r.status === 202 });
    return;
  }

  const res = http.post(`${base}?strategy=${STRATEGY}`, body, JSON_HEADERS);
  if (res.status === 201) {
    accepted.add(1);
    if (PAY) {
      http.post(
        `${BASE_URL}/api/registrations/${res.json('id')}/payment`,
        JSON.stringify({ amount: 50000 }),
        JSON_HEADERS,
      );
    }
  } else if (res.status === 409) {
    soldOut.add(1);
  } else {
    unexpected.add(1);
  }
  check(res, { 'expected status (201/409)': (r) => r.status === 201 || r.status === 409 });
}
