// k6 load test whose pass/fail thresholds come from the spec (specs/quality/slo.json).
// Run: k6 run -e BASE_URL=https://... perf/orders-load.js
import http from 'k6/http';
import { check } from 'k6';

const slo = JSON.parse(open('../specs/quality/slo.json')).placeOrder;
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: slo.load.vus,
  duration: slo.load.duration,
  thresholds: {
    'http_req_duration{name:placeOrder}': [`p(95)<${slo.p95_ms}`, `p(99)<${slo.p99_ms}`],
    'http_req_failed{name:placeOrder}': [`rate<${slo.error_rate_max}`],
  },
};

const json = { headers: { 'Content-Type': 'application/json' } };

export function setup() {
  const res = http.post(`${BASE_URL}/api/customers`, JSON.stringify({ membership: 'LOYALTY' }), json);
  return { customerId: res.json('id') };
}

export default function (data) {
  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({ customerId: data.customerId, total: 150.0 }),
    { ...json, tags: { name: 'placeOrder' } },
  );
  check(res, { 'created': (r) => r.status === 201 });
}
