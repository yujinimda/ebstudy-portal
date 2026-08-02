-- ─────────────────────────────────────────────────────────────
-- 댓글 — 요구사항 4.2. 자유게시판만 쓴다.
--
-- 테이블을 자유게시판 전용으로 이름 짓지 않은 이유: 참조 대상이 posts 이고,
-- 다른 게시판에 댓글이 열리면 board_type CHECK 만 넓히면 된다.
-- 지금은 CHECK 로 자유게시판만 허용한다 — "열려 있는데 안 쓴다" 가 아니라 "닫혀 있다".
--
-- ⚠️ 작성 주체: V1 헤더와 같다.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE comments (
    id         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT        NOT NULL,
    author_id  BIGINT        NOT NULL,
    -- ⚠️ 기획에 댓글 길이 규정이 없다. 본문(3999자)보다 짧아야 한다는 것만 분명하므로
    --    1000자로 정하고 이 판단을 여기 남긴다(요구사항 1.4 의 "기획에 없으면 정하고 적는다").
    --    ▶ 사람 검증 대상
    content    VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_comments_content_not_blank CHECK (btrim(content) <> ''),

    -- 글이 지워지면 댓글은 갈 곳이 없다 — V2 가 티켓에 CASCADE 를 준 것과 같은 판단이다.
    -- 여기서 RESTRICT 를 주면 "댓글 달린 글은 못 지운다" 가 되어 요구사항 4.2 의 삭제와 충돌한다
    CONSTRAINT fk_comments_posts FOREIGN KEY (post_id)
        REFERENCES posts (id) ON DELETE CASCADE,

    -- 작성자는 posts 와 같은 이유로 RESTRICT (V6 fk_posts_users 주석 참조)
    CONSTRAINT fk_comments_users FOREIGN KEY (author_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

-- 상세 화면이 "이 글의 댓글을 작성 순서대로" 를 부르고,
-- 목록·메인 화면이 "이 글의 댓글 수" 를 부른다(요구사항 2장·4.1). 둘 다 이 인덱스를 탄다.
-- ★ FK 인덱스이기도 하다 — 위 CASCADE 가 도는 방향이 "자식을 찾는 방향"이므로
--   이 인덱스가 없으면 글 하나를 지울 때마다 comments 전체를 훑는다(V2 와 같은 짝)
CREATE INDEX ix_comments_post_id ON comments (post_id, created_at, id);

-- FK 인덱스 — 요구사항 1.3 "댓글 삭제는 본인 댓글만" 은 단건 조회라 이 인덱스가 필요 없지만,
-- users 쪽 FK 검사(유저 삭제·변경)가 이 인덱스를 요구한다
CREATE INDEX ix_comments_author_id ON comments (author_id);
