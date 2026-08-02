/**
 * 게시판 4종이 공유하는 목록 타입 — 요구사항 1.1.
 *
 * ★ 값은 **서버 계약을 그대로** 옮긴 것이다. 프론트가 따로 정한 이름이 아니다:
 *   - 개씩 보기 10/20/30/40/50 → `BoardSearchCriteria.ALLOWED_SIZES`
 *   - 정렬 기준·방향 → `BoardSort` · `SortDirection` (서버 화이트리스트)
 *   서버가 목록에 없는 값을 조용히 기본값으로 바꾸지 않고 400 을 주므로,
 *   화면도 같은 목록만 만들어야 한다.
 */

/** 서버 `BoardType`. 문의게시판(INQUIRY)만 분류가 없다(요구사항 0장 표). */
export type BoardType = "NOTICE" | "FREE" | "GALLERY" | "INQUIRY";

/** 요구사항 1.1 "개씩 보기: 기본 10 · 20 · 30 · 40 · 50". 이 밖의 값은 서버가 400 이다. */
export const PAGE_SIZES = [10, 20, 30, 40, 50] as const;
export type PageSize = (typeof PAGE_SIZES)[number];
export const DEFAULT_PAGE_SIZE: PageSize = 10;

/** 서버 `BoardSort`. CATEGORY 는 분류가 있는 게시판에서만 쓸 수 있다 — 문의는 400. */
export const BOARD_SORTS = ["CREATED_AT", "CATEGORY", "TITLE", "VIEW_COUNT"] as const;
export type BoardSort = (typeof BOARD_SORTS)[number];
export const DEFAULT_BOARD_SORT: BoardSort = "CREATED_AT";

export const SORT_DIRECTIONS = ["DESC", "ASC"] as const;
export type SortDirection = (typeof SORT_DIRECTIONS)[number];
export const DEFAULT_SORT_DIRECTION: SortDirection = "DESC";

/** 셀렉트에 그대로 쓰는 한국어 이름. 화면 문구지 오류 문구가 아니다. */
export const BOARD_SORT_LABELS: Record<BoardSort, string> = {
  CREATED_AT: "등록일시",
  CATEGORY: "분류",
  TITLE: "제목",
  VIEW_COUNT: "조회수",
};

export const SORT_DIRECTION_LABELS: Record<SortDirection, string> = {
  DESC: "내림차순",
  ASC: "오름차순",
};

/**
 * 목록 검색조건 — **URL 쿼리스트링이 이 값의 유일한 저장소다**(요구사항 1.1 "검색조건 유지").
 *
 * `page` 는 서버와 같은 **0부터**다. URL 에는 사람이 읽는 **1부터**로 적힌다
 * (`criteria.ts` 의 `parseCriteria`/`criteriaToUrlQuery` 가 변환한다).
 */
export interface BoardSearchCriteria {
  /** `yyyy-MM-dd`. 하루의 시작으로 해석된다 */
  from: string;
  /** `yyyy-MM-dd`. 서버가 하루의 **끝**으로 넓힌다 — 그날 쓴 글이 빠지지 않게 */
  to: string;
  /** 분류 없음(전체 분류) = null. 문의게시판은 항상 null */
  categoryId: number | null;
  /** 빈 문자열 = 검색어 없음 */
  keyword: string;
  /** 요구사항 6.1 "나의 문의내역만 보기". 문의게시판 전용 */
  mine: boolean;
  /** 0부터 */
  page: number;
  size: PageSize;
  sort: BoardSort;
  direction: SortDirection;
}

/** 검색 버튼이 바꾸는 값 — 개씩보기·정렬은 여기 없다(그쪽은 즉시 반영이다). */
export type BoardSearchPatch = Partial<
  Pick<BoardSearchCriteria, "from" | "to" | "categoryId" | "keyword" | "mine">
>;

/** 툴바가 바꾸는 값 — **선택 즉시 반영**된다(요구사항 1.1). */
export type BoardListOptionPatch = Partial<
  Pick<BoardSearchCriteria, "size" | "sort" | "direction">
>;

/**
 * 서버 `PageResponse<T>` 그대로.
 *
 * ⚠️ 공지사항 목록만 이 모양이 아니다 — `{ pinned: [], page: PageResponse<T> }` 다
 * (요구사항 3.1 상단 고정이 페이징 밖에 있어야 해서). 그래서 페칭 훅은
 * `PageResponse` 를 강제하지 않고 응답 타입을 통째로 제네릭으로 받는다.
 */
export interface PageResponse<T> {
  items: T[];
  /** 요구사항 1.1 "글 번호는 전체 게시글 수 기준 역순" 의 그 수 */
  totalElements: number;
  totalPages: number;
  /** 0부터 */
  page: number;
  size: number;
}

/**
 * 분류 셀렉트 한 줄.
 *
 * 서버는 게시판마다 조금씩 다른 record 를 준다(`CategoryResponse` 에는 `boardType`,
 * `NoticeCategoryResponse` 에는 `active` 가 더 있다). 셀렉트가 쓰는 세 개만 추린다 —
 * "전체 분류" 항목은 서버가 만들지 않는다(값이 없는 선택지라 id 가 없다). 화면이 붙인다.
 */
export interface CategoryOption {
  id: number;
  name: string;
  sortOrder: number;
}
