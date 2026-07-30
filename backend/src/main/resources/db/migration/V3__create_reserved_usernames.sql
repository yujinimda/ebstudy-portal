-- ─────────────────────────────────────────────────────────────
-- 사용불가 아이디 — FR-028. 코드가 아니라 데이터다(관리 화면은 004).
-- ⚠️ 작성 주체: V1 헤더와 같다(사용자 지시로 AI 작성).
-- ─────────────────────────────────────────────────────────────
CREATE TABLE reserved_usernames (
    id       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 유저 아이디와 달리 11자 제한을 두지 않는다 — 길이 검증이 먼저 걸러낸다(물리 설계 5.6)
    username VARCHAR(255) NOT NULL
);

-- AC-13 — admin 이 금지면 Admin·ADMIN·AdMiN 도 거부되어야 한다.
-- username 과 같은 규칙을 적용하지 않으면 대문자 하나로 우회된다(codex 검증에서 잡힌 실제 구멍)
CREATE UNIQUE INDEX uk_reserved_usernames_username_lower ON reserved_usernames (LOWER(username));
