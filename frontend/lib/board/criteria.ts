/**
 * 검색조건 ↔ 쿼리스트링 변환. **React 를 import 하지 않는다** — 순수 함수라 그대로 테스트된다.
 *
 * 두 방향이 있고 **둘은 다른 문자열이다.**
 *   1. `parseCriteria` / `criteriaToUrlQuery` — **브라우저 주소창**용.
 *      기본값은 적지 않아 URL 이 짧고, `page` 는 사람이 읽는 1부터다.
 *   2. `toApiQuery` — **서버 호출**용. 기본값도 전부 적고 `page` 는 서버와 같은 0부터다.
 *
 * ★ 두 개를 하나로 합치지 않은 이유: 주소창은 사람이 보고 공유하는 것이고 API 는 계약이다.
 *   합치면 "URL 을 예쁘게 하려던 변경"이 조용히 API 호출을 바꾼다.
 */

import {
  BOARD_SORTS,
  DEFAULT_BOARD_SORT,
  DEFAULT_PAGE_SIZE,
  DEFAULT_SORT_DIRECTION,
  PAGE_SIZES,
  SORT_DIRECTIONS,
  type BoardSearchCriteria,
  type BoardSort,
  type PageSize,
  type SortDirection,
} from "./types";

/** `useSearchParams()` 가 주는 읽기 전용 객체와 `URLSearchParams` 를 함께 받기 위한 최소 형태. */
export interface ReadonlyParams {
  get(name: string): string | null;
}

/** 기간 기본값 — 요구사항 1.1 "기본 1달". */
export interface Period {
  from: string;
  to: string;
}

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function pad2(value: number): string {
  return value < 10 ? `0${value}` : String(value);
}

/**
 * `Date` → `yyyy-MM-dd`. **로컬 시간대 기준**이다.
 *
 * `toISOString()` 을 쓰지 않는 이유: 그건 UTC 라 KST 오전 9시 이전에는 **어제 날짜**가 나온다.
 * 사용자가 보는 "오늘"과 검색되는 "오늘"이 달라지면 그날 쓴 글이 통째로 빠진다.
 */
export function toDateInput(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

/**
 * 요구사항 1.1 "기본 1달".
 *
 * `now` 를 인자로 받는 이유는 서버 `BoardSearchCriteria.of` 와 같다 — 테스트가 시간을 고정할
 * 수 있어야 한다. 화면에서는 훅이 **마운트 시 한 번** 계산해 들고 있는다(렌더마다 다시 계산하면
 * 자정을 넘길 때 값이 조용히 바뀐다).
 */
export function defaultPeriod(now: Date = new Date()): Period {
  const day = now.getDate();
  const from = new Date(now.getFullYear(), now.getMonth() - 1, day);
  // ★ 7월 31일의 "한 달 전" 은 6월 31일이 없어 JS 가 **7월 1일**로 굴려 버린다.
  //   그러면 기본 기간이 한 달이 아니라 하루가 된다. 날이 바뀌었으면 그 달의 마지막 날로
  //   되돌린다(= 서버 `minusMonths` 의 클램프와 같은 결과, 6월 30일).
  if (from.getDate() !== day) from.setDate(0);
  return { from: toDateInput(from), to: toDateInput(now) };
}

function parseDate(raw: string | null, fallback: string): string {
  return raw !== null && DATE_PATTERN.test(raw) ? raw : fallback;
}

function parsePageSize(raw: string | null): PageSize {
  const value = Number(raw);
  // 목록에 없는 값은 기본값으로 되돌린다. 서버는 400 을 주지만, 주소창은 누구나 고칠 수 있고
  // 오타 하나로 목록이 통째로 오류 화면이 되는 것보다 기본값으로 보이는 쪽이 낫다
  return (PAGE_SIZES as readonly number[]).includes(value)
    ? (value as PageSize)
    : DEFAULT_PAGE_SIZE;
}

function parseSort(raw: string | null): BoardSort {
  return (BOARD_SORTS as readonly string[]).includes(raw ?? "")
    ? (raw as BoardSort)
    : DEFAULT_BOARD_SORT;
}

function parseDirection(raw: string | null): SortDirection {
  return (SORT_DIRECTIONS as readonly string[]).includes(raw ?? "")
    ? (raw as SortDirection)
    : DEFAULT_SORT_DIRECTION;
}

/**
 * 주소창 → 검색조건.
 *
 * @param fallback 기간이 URL 에 없을 때 쓸 기본 1달. 훅이 마운트 시 고정한 값을 넘긴다
 */
export function parseCriteria(params: ReadonlyParams, fallback: Period): BoardSearchCriteria {
  const rawPage = Number(params.get("page"));
  // URL 은 1부터, 내부·서버는 0부터. 변환은 여기 한 곳에서만 한다
  const page = Number.isInteger(rawPage) && rawPage >= 1 ? rawPage - 1 : 0;
  const rawCategoryId = Number(params.get("categoryId"));

  return {
    from: parseDate(params.get("from"), fallback.from),
    to: parseDate(params.get("to"), fallback.to),
    categoryId: Number.isInteger(rawCategoryId) && rawCategoryId > 0 ? rawCategoryId : null,
    keyword: params.get("keyword") ?? "",
    mine: params.get("mine") === "true",
    page,
    size: parsePageSize(params.get("size")),
    sort: parseSort(params.get("sort")),
    direction: parseDirection(params.get("direction")),
  };
}

/**
 * 검색조건 → 주소창 쿼리스트링(`?` 없이. 아무것도 없으면 빈 문자열).
 *
 * **기본값과 같은 항목은 적지 않는다.** 그래야 첫 진입 URL 이 `/free` 로 깨끗하고,
 * "조건을 하나도 안 걸었다"와 "기본값을 명시적으로 골랐다"가 같은 주소가 된다.
 */
export function criteriaToUrlQuery(criteria: BoardSearchCriteria, fallback: Period): string {
  const params = new URLSearchParams();
  if (criteria.from !== fallback.from) params.set("from", criteria.from);
  if (criteria.to !== fallback.to) params.set("to", criteria.to);
  if (criteria.categoryId !== null) params.set("categoryId", String(criteria.categoryId));
  if (criteria.keyword !== "") params.set("keyword", criteria.keyword);
  if (criteria.mine) params.set("mine", "true");
  if (criteria.page > 0) params.set("page", String(criteria.page + 1));
  if (criteria.size !== DEFAULT_PAGE_SIZE) params.set("size", String(criteria.size));
  if (criteria.sort !== DEFAULT_BOARD_SORT) params.set("sort", criteria.sort);
  if (criteria.direction !== DEFAULT_SORT_DIRECTION) params.set("direction", criteria.direction);
  return params.toString();
}

/** 로컬 시간대 오프셋 `+09:00`. 서머타임이 있는 지역을 위해 **그 날짜의** 오프셋을 쓴다. */
function localOffset(date: Date): string {
  const minutes = -date.getTimezoneOffset();
  const sign = minutes >= 0 ? "+" : "-";
  const abs = Math.abs(minutes);
  return `${sign}${pad2(Math.floor(abs / 60))}:${pad2(abs % 60)}`;
}

function toOffsetDateTime(day: string, endOfDay: boolean): string {
  const [year, month, date] = day.split("-").map(Number);
  const time = endOfDay ? "23:59:59" : "00:00:00";
  const at = new Date(year, month - 1, date, endOfDay ? 23 : 0);
  return `${day}T${time}${localOffset(at)}`;
}

/**
 * ★★ **게시판마다 기간 파라미터가 받는 타입이 다르다.** 여기가 그 차이를 흡수하는 자리다.
 *
 * | 게시판 | 컨트롤러 | 받는 표기 |
 * |---|---|---|
 * | 자유 `/api/free-posts` | `@DateTimeFormat(ISO.DATE) LocalDate` | **`yyyy-MM-dd` 만** |
 * | 공지 `/api/notices` · 갤러리 `/api/galleries` | `String` 직접 파싱 | 날짜·로컬일시·오프셋일시 전부 |
 * | 문의 `/api/inquiries` | `@DateTimeFormat(ISO.DATE_TIME) OffsetDateTime` | **오프셋 붙은 일시만** |
 *
 * 문의게시판에 `from=2026-07-01` 을 보내면 스프링 타입 변환이 실패하고,
 * 그 예외는 `GlobalExceptionHandler` 의 `Exception` 핸들러로 떨어져 **500** 이 된다.
 * 자유게시판에 오프셋 일시를 보내면 반대로 `LocalDate` 변환이 깨진다.
 * → 그래서 옵션을 **기본값 없이 게시판이 고르게** 두지 않고, 안전한 `"date"` 를 기본으로 하되
 *   문의게시판만 `dateParam: "datetime"` 을 넘기게 했다.
 */
export interface ApiQueryOptions {
  /** 기본 `"date"`. **문의게시판만 `"datetime"`** */
  dateParam?: "date" | "datetime";
  /** 분류를 쓰지 않는 게시판(문의)은 `false`. `categoryId` 를 보내면 서버가 400 이다 */
  withCategory?: boolean;
  /** 요구사항 6.1 "나의 문의내역만 보기". 문의게시판만 `true` */
  withMine?: boolean;
}

/**
 * 검색조건 → **서버 호출용** 쿼리스트링(`?` 없이).
 *
 * 주소창과 달리 기본값도 전부 적는다 — 계약을 눈에 보이게 두는 편이 디버깅에 낫고,
 * 서버 기본값이 바뀌어도 화면이 보던 값이 유지된다.
 */
export function toApiQuery(
  criteria: BoardSearchCriteria,
  options: ApiQueryOptions = {},
): string {
  const { dateParam = "date", withCategory = true, withMine = false } = options;
  const params = new URLSearchParams();

  if (dateParam === "datetime") {
    params.set("from", toOffsetDateTime(criteria.from, false));
    params.set("to", toOffsetDateTime(criteria.to, true));
  } else {
    params.set("from", criteria.from);
    params.set("to", criteria.to);
  }

  if (withCategory && criteria.categoryId !== null) {
    params.set("categoryId", String(criteria.categoryId));
  }
  if (criteria.keyword !== "") params.set("keyword", criteria.keyword);
  // `mine=false` 는 보내지 않는다 — 서버는 로그인하지 않은 사람의 mine 을 무시하지만,
  // 보내지 않는 쪽이 "체크 안 함"이라는 뜻을 정확히 담는다
  if (withMine && criteria.mine) params.set("mine", "true");

  params.set("page", String(criteria.page));
  params.set("size", String(criteria.size));
  params.set("sort", criteria.sort);
  params.set("direction", criteria.direction);
  return params.toString();
}

/** `toApiQuery` 를 경로에 붙인 완성 URL. 페칭 훅에 그대로 넘긴다. */
export function boardListUrl(
  path: string,
  criteria: BoardSearchCriteria,
  options?: ApiQueryOptions,
): string {
  return `${path}?${toApiQuery(criteria, options)}`;
}
