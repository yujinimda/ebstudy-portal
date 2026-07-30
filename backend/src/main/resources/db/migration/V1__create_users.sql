-- ─────────────────────────────────────────────────────────────
-- 유저 — data-model.md 논리 설계 + 물리 설계 1·2·5.5·5.6절
--
-- ⚠️ 작성 주체: process.md 6.7 은 "마이그레이션 SQL 은 사람 단독"으로 정했다.
--    이 파일은 그 규칙의 예외이며 사용자 지시(바이브 코딩 전제)로 AI 가 작성했다.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE users (
    -- ADR-002 자동증가 숫자 PK. Postgres 표기는 IDENTITY 다(물리 설계 1절)
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- FR-003 이 12자 미만이므로 최대 11자. FR-002(서버 검증)의 마지막 방어선을 DB 에도 둔다
    username      VARCHAR(11)  NOT NULL,
    -- FR-008 되돌릴 수 없는 느린 해시. bcrypt 는 60자지만 알고리즘 교체 여지를 남긴다
    password_hash VARCHAR(255) NOT NULL,
    -- FR-029. 길이는 문자 수이고 Postgres VARCHAR(n) 도 문자 수로 센다
    name          VARCHAR(50)  NOT NULL,
    -- FR-017. ENUM 타입을 쓰지 않는다 — 값 추가에 ALTER TYPE 이 필요하고 JPA 매핑이 까다롭다.
    -- CHECK 는 제약이 스키마에 그대로 보인다
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- ★ 대소문자 무시 유일 — CITEXT 확장을 쓰지 않는다(ADR-003 파생 규칙).
--   입력 표기는 그대로 보존하고 유일성 판정만 대소문자를 무시한다.
--   ⚠️ ddl-auto=validate 는 함수 인덱스 일치를 검증하지 않는다 →
--      동시 가입 경합 테스트(Kim vs kim)가 이 인덱스를 확인하는 유일한 장치다.
CREATE UNIQUE INDEX uk_users_username_lower ON users (LOWER(username));
