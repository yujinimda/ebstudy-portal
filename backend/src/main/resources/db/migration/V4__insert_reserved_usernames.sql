-- ─────────────────────────────────────────────────────────────
-- 사용불가 아이디 초기 목록 — spec.md Clarifications 2026-07-27
--   "초기 목록은 마이그레이션으로 제공한다(예약어는 비밀이 아니므로 커밋해도 안전 —
--    관리자 비밀번호와 성격이 다르다)."
-- 목적은 사칭 방지다. 004 관리자 화면에서 운영자가 추가·삭제한다.
-- ⚠️ 작성 주체: V1 헤더와 같다(사용자 지시로 AI 작성).
-- ─────────────────────────────────────────────────────────────
INSERT INTO reserved_usernames (username) VALUES
    ('admin'),
    ('administrator'),
    ('root'),
    ('system'),
    ('sysop'),
    ('master'),
    ('manager'),
    ('operator'),
    ('superuser'),
    ('owner'),
    ('staff'),
    ('support'),
    ('help'),
    ('helpdesk'),
    ('info'),
    ('contact'),
    ('webmaster'),
    ('security'),
    ('moderator'),
    ('ebstudy'),
    ('portal'),
    ('api'),
    ('www'),
    ('mail'),
    ('null'),
    ('undefined'),
    ('anonymous'),
    ('guest'),
    ('test'),
    ('login'),
    ('logout'),
    ('signup'),
    ('register');
