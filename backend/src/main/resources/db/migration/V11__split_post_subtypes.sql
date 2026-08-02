-- ─────────────────────────────────────────────────────────────
-- 게시판별 전용 컬럼을 하위 테이블로 분리 — ADR-006 개정
--
-- 무엇이 문제였나:
--   posts 한 테이블에 4종이 다 들어 있고, pinned(공지) · secret · secret_password_hash(문의)가
--   나머지 게시판 행에서는 늘 비어 있었다. "이 칸은 이 게시판에서만 의미가 있다"를
--   CHECK 제약 3개로 지켜야 했는데, 그건 스키마가 스스로 말하지 못한다는 뜻이다.
--   컬럼이 늘수록 죽은 칸과 제약이 같이 늘어난다.
--
-- 어떻게 바꾸나 (JPA JOINED 상속):
--   posts          공통 — 제목 · 내용 · 작성자 · 조회수 · 분류 · 등록일시
--   notice_posts   pinned
--   inquiry_posts  secret · secret_password_hash
--   free_posts     (전용 컬럼 없음)
--   gallery_posts  (전용 컬럼 없음)
--
--   전용 컬럼이 없는 free/gallery 에도 표를 만든다. JOINED 는 구상 클래스마다
--   표가 하나씩 있어야 하기 때문이다. 대가는 조회 때 조인이 하나 붙는 것이고,
--   얻는 것은 "게시판마다 자기 표를 가진다"는 규칙이 예외 없이 성립하는 것이다.
--
-- ⚠️ 이 파일은 process.md 6.7("마이그레이션 SQL 은 사람 단독")의 예외이며
--    사용자 지시(바이브 코딩 전제)로 AI 가 작성했다. V1 과 같은 사유다.
-- ─────────────────────────────────────────────────────────────

-- 1. 하위 표를 만든다. PK 가 곧 posts 참조다(JOINED 의 기본 형태).
--    ON DELETE CASCADE — 글이 사라지면 전용 칸도 함께 사라진다.
CREATE TABLE notice_posts (
    post_id BIGINT PRIMARY KEY REFERENCES posts (id) ON DELETE CASCADE,
    -- 요구사항 3.1 알림글. 이제 "공지에서만 true" 를 CHECK 로 막을 필요가 없다 —
    -- 애초에 공지 표에만 있는 칸이다
    pinned  BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE free_posts (
    post_id BIGINT PRIMARY KEY REFERENCES posts (id) ON DELETE CASCADE
);

CREATE TABLE gallery_posts (
    post_id BIGINT PRIMARY KEY REFERENCES posts (id) ON DELETE CASCADE
);

CREATE TABLE inquiry_posts (
    post_id              BIGINT       PRIMARY KEY REFERENCES posts (id) ON DELETE CASCADE,
    -- 요구사항 6.2 비밀글. 원문이 아니라 해시만 둔다(V1 의 password_hash 와 같은 규칙)
    secret               BOOLEAN      NOT NULL DEFAULT FALSE,
    secret_password_hash VARCHAR(255),
    -- 짝 규칙은 남는다 — 비밀글인데 해시가 없거나, 해시가 있는데 비밀글이 아닌 상태를 막는다.
    -- 이건 게시판 구분과 무관한 "이 표 안에서의 규칙"이라 여기 있는 것이 맞다
    CONSTRAINT ck_inquiry_posts_secret_password
        CHECK ((secret = TRUE AND secret_password_hash IS NOT NULL)
            OR (secret = FALSE AND secret_password_hash IS NULL))
);

-- 2. 기존 행을 옮긴다. 순서가 중요하다 — 컬럼을 지우기 전에 읽어야 한다.
INSERT INTO notice_posts (post_id, pinned)
SELECT id, pinned FROM posts WHERE board_type = 'NOTICE';

INSERT INTO free_posts (post_id)
SELECT id FROM posts WHERE board_type = 'FREE';

INSERT INTO gallery_posts (post_id)
SELECT id FROM posts WHERE board_type = 'GALLERY';

INSERT INTO inquiry_posts (post_id, secret, secret_password_hash)
SELECT id, secret, secret_password_hash FROM posts WHERE board_type = 'INQUIRY';

-- 3. 옮긴 칸과 그것을 지키던 제약을 지운다.
--    ★ 제약을 먼저 지운다 — 컬럼을 참조하는 제약이 남아 있으면 DROP COLUMN 이 실패한다.
--    (Postgres 는 DDL 이 트랜잭션 안에서 돌므로 중간에 실패해도 반쪽 적용이 남지 않는다 — ADR-003)
ALTER TABLE posts DROP CONSTRAINT ck_posts_pinned;
ALTER TABLE posts DROP CONSTRAINT ck_posts_secret_board;
ALTER TABLE posts DROP CONSTRAINT ck_posts_secret_password;

DROP INDEX ix_posts_pinned;

ALTER TABLE posts
    DROP COLUMN pinned,
    DROP COLUMN secret,
    DROP COLUMN secret_password_hash;

-- 4. 고정 글 조회용 인덱스를 새 위치에 다시 만든다.
--    부분 인덱스라 고정된 글(최대 5개)만 담는다 — 표 전체를 훑지 않는다.
CREATE INDEX ix_notice_posts_pinned ON notice_posts (post_id) WHERE pinned;

-- 5. board_type 은 남긴다.
--    JPA 가 이 값을 구분자(discriminator)로 써서 어느 하위 표를 조인할지 정하고,
--    ix_posts_board_type_created_at 인덱스가 목록 조회의 주 경로다.
--    ck_posts_board_type · ck_posts_category 는 그대로 유효하다.
COMMENT ON COLUMN posts.board_type IS
    'JPA JOINED 상속의 구분자. 값에 따라 notice_posts / free_posts / gallery_posts / inquiry_posts 중 하나와 짝이 된다';
