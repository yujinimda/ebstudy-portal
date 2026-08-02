-- ─────────────────────────────────────────────────────────────
-- 분류(카테고리) — 002 요구사항 7.2 "관리자 카테고리 관리".
--   모든 목록 화면이 "관리자가 등록한 해당 게시판 분류 목록"을 참조하므로
--   게시판마다 독립된 분류 집합이 필요하다.
--
-- ⚠️ 작성 주체: V1 헤더와 같다(process.md 6.7 의 "사람 단독" 규칙의 예외 —
--    사용자 지시(바이브 코딩 전제)로 AI 가 작성했다).
-- ─────────────────────────────────────────────────────────────
CREATE TABLE categories (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 게시판 종류를 컬럼으로 둔다 — 게시판마다 분류 테이블을 따로 만들면
    -- "분류 관리" 화면 하나가 테이블 3개를 알아야 하고 SQL 이 3벌이 된다
    board_type VARCHAR(20) NOT NULL,
    name       VARCHAR(50) NOT NULL,
    -- 요구사항 7.2 "표시 순서". 기본 0 이면 순서를 안 정한 분류가 앞에 모인다 —
    -- 목록 정렬은 (sort_order, id) 로 해서 같은 값이면 등록 순으로 안정 정렬한다
    sort_order INTEGER     NOT NULL DEFAULT 0,
    -- 요구사항 7.2 "이미 사용 중인 분류는 삭제하지 않는다 — 비활성으로 내린다".
    -- 삭제 대신 이 플래그를 내리므로 과거 글의 분류명이 사라지지 않는다
    active     BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- V1 과 같은 이유로 Postgres ENUM 타입을 쓰지 않는다 — 값 추가에 ALTER TYPE 이
    -- 필요하고 JPA 매핑이 까다롭다. CHECK 는 제약이 스키마에 그대로 보인다.
    -- ★ INQUIRY 가 빠진 것은 의도다 — 문의게시판은 분류가 없다(요구사항 0장 표).
    --   여기에 INQUIRY 를 넣으면 "쓰이지 않는 분류"가 만들어질 수 있다
    CONSTRAINT ck_categories_board_type CHECK (board_type IN ('NOTICE', 'FREE', 'GALLERY')),
    -- 공백만 있는 이름은 화면에서 빈칸으로 보이고 선택할 수 없는 분류가 된다
    CONSTRAINT ck_categories_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_categories_sort_order CHECK (sort_order >= 0)
);

-- 같은 게시판 안에서 분류명이 겹치면 사용자가 어느 쪽을 고른 것인지 구분할 수 없다.
-- LOWER() 로 판정하는 것은 V1·V3 과 같은 규칙이다 — 대문자 하나로 우회되는 것을 막는다.
-- ⚠️ ddl-auto=validate 는 함수 인덱스를 검증하지 않는다(V1 주석과 같은 함정).
CREATE UNIQUE INDEX uk_categories_board_type_name ON categories (board_type, LOWER(name));

-- 목록 화면이 열릴 때마다 "이 게시판의 사용 중인 분류를 표시 순서대로" 를 부른다.
-- active 를 부분 인덱스 조건으로 두지 않은 이유: 관리 화면은 비활성 분류도 함께 본다
CREATE INDEX ix_categories_board_type_sort_order ON categories (board_type, sort_order, id);
