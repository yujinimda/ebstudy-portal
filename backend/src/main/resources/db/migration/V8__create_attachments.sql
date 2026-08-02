-- ─────────────────────────────────────────────────────────────
-- 첨부 — 자유게시판 첨부파일(요구사항 4.3)과 갤러리 이미지(5.3)를 한 테이블에 담는다.
--
-- 왜 하나인가: 두 경우의 컬럼이 완전히 같다(원본 이름 · 저장 위치 · 크기 · 순서).
--   다른 것은 **정책값**(허용 확장자 · 개당 최대 크기 · 최대 개수)뿐이고,
--   정책은 데이터가 아니라 설정이다(data-model.md 가 자격증명 수명을 논리 모델에서
--   뺀 것과 같은 판단) → 서버 코드의 AttachmentPolicy 가 들고 있다.
--   컬럼이 같은데 테이블을 나누면 다운로드 코드가 2벌이 된다.
--
-- ⚠️ 작성 주체: V1 헤더와 같다.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE attachments (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id       BIGINT       NOT NULL,
    -- 사용자에게 보여줄 이름. **파일시스템에는 쓰지 않는다**(아래 stored_path 주석)
    original_name VARCHAR(255) NOT NULL,
    -- 저장소 루트 기준 상대 경로. 서버가 만든 이름만 들어간다
    stored_path   VARCHAR(500) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    -- 요구사항 5.3 "첫 번째 이미지가 썸네일" · 5.2 캐러셀 순서.
    -- 순서가 데이터에 없으면 화면마다 다른 이미지가 썸네일이 된다
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_attachments_size CHECK (size_bytes > 0),
    CONSTRAINT ck_attachments_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_attachments_original_name_not_blank CHECK (btrim(original_name) <> ''),

    -- ★ 경로 순회 방어의 **마지막** 방어선이다. 1차 방어는 서버가 원본 파일명을 버리고
    --   UUID 로 새 이름을 만드는 것이고(LocalAttachmentStorage), 이 CHECK 는
    --   그 코드가 언젠가 바뀌어도 ../ 가 들어간 경로가 저장되지 않게 막는다.
    --   절대경로(/ 로 시작)도 막는다 — 루트 밖으로 나가는 두 번째 방법이다
    CONSTRAINT ck_attachments_stored_path_safe
        --   역슬래시는 LIKE 의 기본 이스케이프 문자라 패턴에 넣으면 읽기 어렵다 → strpos 로 본다
        CHECK (stored_path NOT LIKE '%..%'
           AND stored_path NOT LIKE '/%'
           AND strpos(stored_path, '\') = 0
           AND btrim(stored_path) <> ''),

    -- 글이 지워지면 첨부는 갈 곳이 없다(V7 댓글과 같은 판단).
    -- ⚠️ 행은 CASCADE 로 사라지지만 **파일은 사라지지 않는다** — 파일 삭제는 서버 코드가 한다.
    --   행보다 파일이 오래 남는 방향은 고아 파일(디스크 낭비)로 끝나지만, 반대 방향은
    --   "목록에는 있는데 열리지 않는 첨부"라 사용자에게 보인다. 그래서 이 방향을 택했다
    CONSTRAINT fk_attachments_posts FOREIGN KEY (post_id)
        REFERENCES posts (id) ON DELETE CASCADE
);

-- 상세 화면이 "이 글의 첨부를 순서대로", 목록이 "첫 이미지 + 개수"(요구사항 2장·5.1)를 부른다.
-- ★ FK 인덱스이기도 하다 — 위 CASCADE 가 이 인덱스를 요구한다(V2 와 같은 짝)
CREATE INDEX ix_attachments_post_id ON attachments (post_id, sort_order, id);

-- 같은 파일 경로가 두 행에 붙으면 한쪽을 지울 때 다른 쪽 파일까지 사라진다.
-- 서버가 UUID 로 이름을 만들므로 정상 경로에서는 겹칠 수 없지만,
-- 겹치는 순간의 증상이 "남의 첨부가 사라짐"이라 DB 로 막는다
CREATE UNIQUE INDEX uk_attachments_stored_path ON attachments (stored_path);
