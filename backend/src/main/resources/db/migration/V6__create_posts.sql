-- ─────────────────────────────────────────────────────────────
-- 게시글 — 게시판 4종(공지·자유·갤러리·문의)을 board_type 으로 구분하는 단일 테이블.
--
-- ★ 왜 테이블을 나누지 않았는가 (이 결정이 아래 모든 제약의 전제다)
--   1) 자식 테이블이 결정적이다. comments·attachments 는 "글 하나"를 참조한다.
--      게시판마다 테이블을 나누면 자식이 "어느 테이블의 어느 id" 를 가리켜야 하고,
--      그러면 **외래키를 걸 수 없다**(다형 참조). 고아 행이 그대로 장애가 된다는 것은
--      V2 에서 이미 판단한 내용이고 여기서 뒤집을 이유가 없다.
--   2) 요구사항 1.1 목록 규칙(기간·분류·검색어·정렬·페이징)이 4종 전부 같다.
--      나누면 같은 쿼리와 같은 검증 코드가 4벌이 되고, 4벌은 반드시 갈라진다.
--   3) 메인 페이지(요구사항 2장)가 4종 최신글을 한 화면에 모은다. 단일 테이블이면
--      board_type 만 바꾼 같은 쿼리 4번이고, 나누면 UNION 이다.
--   4) 게시판 고유 컬럼이 3개(pinned · secret · secret_password_hash)뿐이다.
--
--   단일 테이블의 약점은 "그 게시판에 없는 컬럼도 채울 수 있다" 는 것이다.
--   → 아래 CHECK 제약들이 그 약점을 정면으로 막는다. 제약 없이 단일 테이블을 쓰면
--     자유게시판 글에 상단고정이 켜지는 종류의 버그를 DB 가 막아주지 못한다.
--
--   ▶ 분리 방아쇠: 게시판 고유 컬럼이 늘어 NULL 컬럼이 절반을 넘거나,
--     한 게시판만 다른 보존/파티션 정책이 필요해지는 시점.
--
-- ⚠️ 작성 주체: V1 헤더와 같다.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE posts (
    id                   BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    board_type           VARCHAR(20)   NOT NULL,
    -- 문의게시판만 NULL 이다(아래 ck_posts_category 가 강제한다)
    category_id          BIGINT,
    author_id            BIGINT        NOT NULL,
    -- 요구사항 1.2 "100자 미만" → 최대 99자. V1 이 "12자 미만이므로 11자" 로
    -- 길이를 DB 에도 둔 것과 같은 이유다 — 서버 검증의 마지막 방어선
    title                VARCHAR(99)   NOT NULL,
    -- 요구사항 1.2 "4000자 미만" → 최대 3999자
    content              VARCHAR(3999) NOT NULL,
    -- 요구사항 1.4 — 단순 증가. INTEGER 로 두면 21억에서 넘치고,
    -- 넘치는 순간 조용히 음수가 되는 종류의 버그다
    view_count           BIGINT        NOT NULL DEFAULT 0,
    -- 요구사항 3.1 알림글(상단 고정). 공지사항 전용
    pinned               BOOLEAN       NOT NULL DEFAULT false,
    -- 요구사항 6.2 비밀글. 문의게시판 전용
    secret               BOOLEAN       NOT NULL DEFAULT false,
    -- ★ 4자리 비밀번호를 **평문으로 저장하지 않는다**.
    --   4자리 숫자는 1만 가지뿐이라 유출되면 즉시 뚫리지만, 사용자가 다른 곳과
    --   같은 숫자를 쓸 수 있으므로 원문을 남기면 그 피해가 우리 DB 밖으로 번진다.
    --   느린 해시(bcrypt)를 쓰고, 컬럼 길이는 V1 password_hash 와 같은 이유로
    --   알고리즘 교체 여지를 남겨 255 다
    secret_password_hash VARCHAR(255),
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_posts_board_type
        CHECK (board_type IN ('NOTICE', 'FREE', 'GALLERY', 'INQUIRY')),

    -- 요구사항 0장 표 — 문의게시판만 분류가 없다. 양방향으로 막는다:
    -- 문의글에 분류가 붙는 것도, 나머지 게시판 글에 분류가 비는 것도 오류다
    CONSTRAINT ck_posts_category
        CHECK ((board_type = 'INQUIRY' AND category_id IS NULL)
            OR (board_type <> 'INQUIRY' AND category_id IS NOT NULL)),

    -- 요구사항 3.1 — 상단 고정은 공지사항만
    CONSTRAINT ck_posts_pinned
        CHECK (board_type = 'NOTICE' OR pinned = false),

    -- 요구사항 6.2 — 비밀글은 문의게시판만
    CONSTRAINT ck_posts_secret_board
        CHECK (board_type = 'INQUIRY' OR (secret = false AND secret_password_hash IS NULL)),

    -- 비밀글인데 비밀번호가 없으면 아무나 열리고, 비밀글이 아닌데 해시가 남아 있으면
    -- "비밀글 해제" 가 절반만 된 상태다. 둘 다 화면에서는 정상으로 보인다
    CONSTRAINT ck_posts_secret_password
        CHECK ((secret = true  AND secret_password_hash IS NOT NULL)
            OR (secret = false AND secret_password_hash IS NULL)),

    CONSTRAINT ck_posts_view_count CHECK (view_count >= 0),
    CONSTRAINT ck_posts_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_posts_content_not_blank CHECK (btrim(content) <> ''),

    -- 요구사항 7.2 "이미 사용 중인 분류는 삭제하지 않는다".
    -- 기본 동작(NO ACTION = RESTRICT 와 같은 판정 시점 차이만 있음)에 기대지 않고
    -- 명시한다 — 규칙을 코드가 아니라 DB 가 지키게 하는 것이 이 제약의 목적이다
    CONSTRAINT fk_posts_categories FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE RESTRICT,

    -- ★ V2 의 refresh_tickets 는 CASCADE 였는데 여기는 RESTRICT 다. 성격이 다르다:
    --   티켓은 그 사람에게 종속된 자격증명이라 사람이 사라지면 의미가 없지만,
    --   게시글은 콘텐츠다. 남의 댓글이 달려 있고 목록의 글 번호가 밀린다.
    --   유저 삭제 기능은 아직 없으므로, 생기는 시점에 "탈퇴 회원 표기" 를 먼저 정하게
    --   강제하는 쪽이 안전하다 — RESTRICT 는 그 결정을 강제하는 장치다
    CONSTRAINT fk_posts_users FOREIGN KEY (author_id)
        REFERENCES users (id) ON DELETE RESTRICT
);

-- ★ 주 조회 패턴 — 요구사항 1.1 "기간 검색 + 등록일시 내림차순 + 페이징".
--   WHERE board_type = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC
--   선두가 board_type 인 이유: 등가 조건이 먼저 와야 범위 조건이 인덱스를 계속 탄다.
--   id DESC 를 뒤에 붙인 것은 **같은 시각의 글이 페이지마다 순서가 흔들리는 것**을 막기 위해서다
--   (정렬이 불안정하면 2페이지에서 1페이지의 글이 다시 나온다)
CREATE INDEX ix_posts_board_type_created_at ON posts (board_type, created_at DESC, id DESC);

-- FK 인덱스 — PostgreSQL 은 자식의 FK 컬럼에 인덱스를 자동으로 만들지 않는다
-- (data-model.md 물리 설계 5.5). 없으면 분류 1건을 지우거나 바꿀 때마다 posts 전체를 훑는다.
-- 목록의 "분류로 좁히기" 도 이 인덱스를 탄다
CREATE INDEX ix_posts_category_id ON posts (category_id);

-- FK 인덱스 겸 요구사항 6.1 "나의 문의내역만 보기".
-- created_at DESC 를 뒤에 붙여 그 화면의 정렬까지 인덱스로 받는다
CREATE INDEX ix_posts_author_id ON posts (author_id, created_at DESC);

-- 요구사항 3.1 — 상단 고정 글은 **모든 페이지 상단**에 따로 붙는다.
-- 즉 공지 목록을 열 때마다 "고정 글 최신 5개" 쿼리가 한 번 더 나간다.
-- 부분 인덱스인 이유: 고정 글은 최대 5개 남짓이고, 전체 행에 인덱스를 만들면
-- 쓰기 비용만 늘고 읽기에는 도움이 안 된다
CREATE INDEX ix_posts_pinned ON posts (created_at DESC) WHERE pinned;

-- ─────────────────────────────────────────────────────────────
-- 만들지 않은 인덱스 — 적어두지 않으면 나중에 "빠뜨린 것" 과 구분되지 않는다
-- (data-model.md 5.5 의 서술 방식을 따른다)
--
-- · (board_type, view_count) · (board_type, title) · (board_type, category_id, ...)
--   요구사항 1.1 의 정렬 기준 4종 중 기본(등록일시)만 인덱스로 받친다.
--   나머지 3종까지 복합 인덱스를 깔면 **쓰기마다 인덱스 4개를 갱신**하는데,
--   기본값이 아닌 정렬을 실제로 얼마나 쓰는지 알지 못한 채 지불하는 비용이다.
--   ▶ 방아쇠: 목록 응답이 느려지거나 글이 수만 건을 넘는 시점.
--
-- · 검색어 부분 일치(LIKE '%키워드%')
--   B-tree 는 앞이 열린 패턴을 타지 못한다. 기간·분류 조건이 먼저 범위를 좁히므로
--   현재 규모에서는 그 뒤의 필터링으로 충분하다.
--   ▶ 방아쇠: 위와 같은 시점 — 그때 pg_trgm GIN 인덱스를 검토한다.
-- ─────────────────────────────────────────────────────────────
