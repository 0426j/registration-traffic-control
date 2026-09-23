-- 기존 행은 결제 개념이 생기기 전에 만들어졌으므로 이미 확정된 것으로 간주(status=CONFIRMED).
-- strategy는 알 수 없으니 'none'으로 채워둔다(개발용 데이터라 문제 없음).
ALTER TABLE registration
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    ADD COLUMN idempotency_key VARCHAR(255),
    ADD COLUMN strategy VARCHAR(20) NOT NULL DEFAULT 'none';

-- 이후 애플리케이션 코드가 항상 명시적으로 값을 채우므로 기본값은 여기서만 쓰고 뗀다.
ALTER TABLE registration ALTER COLUMN status DROP DEFAULT;
ALTER TABLE registration ALTER COLUMN strategy DROP DEFAULT;

-- idempotency_key는 NULL을 허용하되(레거시 행), 값이 있으면 유일해야 한다.
CREATE UNIQUE INDEX idx_registration_idempotency_key ON registration (idempotency_key) WHERE idempotency_key IS NOT NULL;
