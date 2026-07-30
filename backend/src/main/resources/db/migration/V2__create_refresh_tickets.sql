-- ─────────────────────────────────────────────────────────────
-- 재발급 티켓 — data-model.md 물리 설계 3·4·5·5.5·5.6절
-- ⚠️ 작성 주체: V1 헤더와 같다(사용자 지시로 AI 작성).
-- ─────────────────────────────────────────────────────────────
CREATE TABLE refresh_tickets (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    -- SHA-256 은 정확히 32바이트. BYTEA 면 길이가 그 자체로 검증되고,
    -- hex 문자열로 두면 대소문자 표기가 갈려 유니크가 무의미해지는 함정이 있다
    token_hash BYTEA       NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    -- 기본값이 없으면 삽입 때마다 명시해야 하고 한 번 빠뜨리면 "무효화됐는지 알 수 없는 티켓"이 생긴다
    revoked    BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- research.md 8 — 유저 없는 티켓은 의미가 없다. 고아 행이 그대로 장애 원인이 된다
    CONSTRAINT fk_refresh_tickets_users FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

-- 재발급은 "해시로 행을 찾는다" — 없으면 매 재발급이 전 테이블 스캔이다
CREATE UNIQUE INDEX uk_refresh_tickets_token_hash ON refresh_tickets (token_hash);

-- ★ PostgreSQL 은 FK 컬럼에 인덱스를 자동으로 만들지 않는다.
--   ON DELETE CASCADE 가 도는 방향이 "자식을 찾는 방향"이므로 CASCADE 결정이 이 인덱스를 요구한다
CREATE INDEX ix_refresh_tickets_user_id ON refresh_tickets (user_id);

-- 하루 1회 청소가 expires_at < now() 로 지운다. 없으면 매일 전 테이블 스캔이다
CREATE INDEX ix_refresh_tickets_expires_at ON refresh_tickets (expires_at);
