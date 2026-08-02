-- ─────────────────────────────────────────────────────────────
-- 문의 답변 — 요구사항 6.3 · 6.5. 문의글 하나에 답변 하나.
--
-- ★ 왜 posts 컬럼이 아니라 별도 테이블인가
--   posts 는 이미 단일 테이블이므로 컬럼 3개(내용·답변자·답변시각)를 더 붙일 수는 있다.
--   그런데 pinned·secret 은 **글의 속성**(참/거짓 한 칸)이고,
--   답변은 **누가 언제 무엇을 썼는지를 스스로 가진 별개의 작성물**이다.
--   posts 에 붙이면 나머지 3개 게시판 전 행에 3칸이 죽은 채로 남고,
--   무엇보다 "답변 수정 이력" 같은 요구가 생기면 컬럼을 또 늘려야 한다.
--   지금 나누면 그때 이 테이블만 손대면 된다.
--
--   답변 유무(요구사항 6.1 "답변완료/미답변")는 이 테이블의 존재 여부로 판정한다 —
--   posts 에 is_answered 같은 플래그를 두지 않는다. 두 곳에 있으면 반드시 어긋난다.
--
-- ⚠️ 작성 주체: V1 헤더와 같다.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE inquiry_answers (
    id         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT        NOT NULL,
    -- 답변자는 관리자다. 역할 검증은 서버가 하고(요구사항 7.1), 여기서는 users 를 가리킨다 —
    -- role 을 여기 복사하면 나중에 역할이 바뀔 때 두 값이 갈라진다
    admin_id   BIGINT        NOT NULL,
    -- 요구사항 6.5 "답변 필수 4000자 미만" — posts.content 와 같은 규칙
    content    VARCHAR(3999) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_inquiry_answers_content_not_blank CHECK (btrim(content) <> ''),

    CONSTRAINT fk_inquiry_answers_posts FOREIGN KEY (post_id)
        REFERENCES posts (id) ON DELETE CASCADE,

    CONSTRAINT fk_inquiry_answers_users FOREIGN KEY (admin_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

-- ★ 1:1 을 DB 가 지킨다. 유니크가 없으면 관리자가 "답변완료" 를 두 번 눌렀을 때
--   답변이 2개 생기고, 화면은 그중 하나만 보여준다 — 사라진 것처럼 보이는 종류의 버그다.
--   FK 인덱스(CASCADE 가 요구하는 것)도 이 유니크가 겸한다
CREATE UNIQUE INDEX uk_inquiry_answers_post_id ON inquiry_answers (post_id);

-- FK 인덱스 — users 쪽 참조 무결성 검사가 요구한다(data-model.md 5.5)
CREATE INDEX ix_inquiry_answers_admin_id ON inquiry_answers (admin_id);
