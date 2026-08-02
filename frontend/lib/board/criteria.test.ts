import { describe, expect, it } from "vitest";
import {
  boardListUrl,
  criteriaToUrlQuery,
  defaultPeriod,
  parseCriteria,
  toApiQuery,
  toDateInput,
} from "./criteria";
import type { BoardSearchCriteria } from "./types";

/**
 * 순수 함수라 단위로 검증한다(test-strategy.md 3장).
 *
 * 특히 두 가지를 못 놓친다:
 *   - **URL 은 1부터 / 서버는 0부터** — 한 칸 어긋나면 2페이지가 1페이지를 다시 보여준다
 *   - **게시판마다 기간 표기가 다르다** — 문의게시판에 날짜만 보내면 500 이 된다
 */

const FALLBACK = { from: "2026-07-01", to: "2026-07-31" };

function criteriaOf(patch: Partial<BoardSearchCriteria> = {}): BoardSearchCriteria {
  return {
    from: FALLBACK.from,
    to: FALLBACK.to,
    categoryId: null,
    keyword: "",
    mine: false,
    page: 0,
    size: 10,
    sort: "CREATED_AT",
    direction: "DESC",
    ...patch,
  };
}

describe("defaultPeriod — 요구사항 1.1 기본 1달", () => {
  it("끝은 오늘, 시작은 한 달 전이다", () => {
    expect(defaultPeriod(new Date(2026, 6, 15))).toEqual({
      from: "2026-06-15",
      to: "2026-07-15",
    });
  });

  it("31일의 한 달 전은 6월 31일이 아니라 6월 30일이다", () => {
    // JS 의 setMonth 는 없는 날짜를 다음 달로 굴린다(7월 1일). 그러면 기본 기간이 하루가 된다
    expect(defaultPeriod(new Date(2026, 6, 31)).from).toBe("2026-06-30");
  });

  it("3월 31일의 한 달 전은 2월 28일이다", () => {
    expect(defaultPeriod(new Date(2026, 2, 31)).from).toBe("2026-02-28");
  });

  it("로컬 시간대 기준이다 — UTC 로 밀리면 그날 쓴 글이 빠진다", () => {
    // 자정 직후. toISOString() 을 썼다면 KST 에서 전날이 나온다
    expect(toDateInput(new Date(2026, 0, 1, 0, 30))).toBe("2026-01-01");
  });
});

describe("parseCriteria — 주소창 → 조건", () => {
  it("비어 있으면 기본값이다", () => {
    expect(parseCriteria(new URLSearchParams(""), FALLBACK)).toEqual(criteriaOf());
  });

  it("page 는 URL 의 1부터를 내부의 0부터로 바꾼다", () => {
    expect(parseCriteria(new URLSearchParams("page=3"), FALLBACK).page).toBe(2);
    expect(parseCriteria(new URLSearchParams("page=1"), FALLBACK).page).toBe(0);
    // 0·음수·문자는 첫 페이지로. 주소창은 누구나 고칠 수 있다
    expect(parseCriteria(new URLSearchParams("page=0"), FALLBACK).page).toBe(0);
    expect(parseCriteria(new URLSearchParams("page=-2"), FALLBACK).page).toBe(0);
    expect(parseCriteria(new URLSearchParams("page=abc"), FALLBACK).page).toBe(0);
  });

  it("허용 목록에 없는 개씩보기·정렬은 기본값으로 되돌린다", () => {
    const parsed = parseCriteria(
      new URLSearchParams("size=7&sort=PASSWORD&direction=SIDEWAYS"),
      FALLBACK,
    );
    expect(parsed).toMatchObject({ size: 10, sort: "CREATED_AT", direction: "DESC" });
  });

  it("형식이 아닌 날짜는 기본 기간으로 되돌린다", () => {
    expect(parseCriteria(new URLSearchParams("from=어제&to=2026-08-01"), FALLBACK)).toMatchObject({
      from: "2026-07-01",
      to: "2026-08-01",
    });
  });
});

describe("criteriaToUrlQuery — 조건 → 주소창", () => {
  it("기본값은 적지 않는다", () => {
    expect(criteriaToUrlQuery(criteriaOf(), FALLBACK)).toBe("");
  });

  it("바뀐 것만 적고 page 는 1부터로 되돌린다", () => {
    const query = criteriaToUrlQuery(
      criteriaOf({ page: 2, size: 20, keyword: "공지", categoryId: 3 }),
      FALLBACK,
    );
    const params = new URLSearchParams(query);
    expect(params.get("page")).toBe("3");
    expect(params.get("size")).toBe("20");
    expect(params.get("keyword")).toBe("공지");
    expect(params.get("categoryId")).toBe("3");
    expect(params.get("sort")).toBeNull();
  });

  it("주소창 ↔ 조건이 왕복한다 — 검색조건 유지가 여기에 달려 있다", () => {
    const original = criteriaOf({
      from: "2026-01-01",
      to: "2026-03-01",
      categoryId: 5,
      keyword: "테스트",
      mine: true,
      page: 4,
      size: 50,
      sort: "VIEW_COUNT",
      direction: "ASC",
    });
    const roundTrip = parseCriteria(
      new URLSearchParams(criteriaToUrlQuery(original, FALLBACK)),
      FALLBACK,
    );
    expect(roundTrip).toEqual(original);
  });
});

describe("toApiQuery — 조건 → 서버 호출", () => {
  it("page 는 서버와 같은 0부터로 보낸다", () => {
    expect(new URLSearchParams(toApiQuery(criteriaOf({ page: 2 }))).get("page")).toBe("2");
  });

  it("기본은 날짜 표기다 — 자유·공지·갤러리가 이것을 받는다", () => {
    const params = new URLSearchParams(toApiQuery(criteriaOf()));
    expect(params.get("from")).toBe("2026-07-01");
    expect(params.get("to")).toBe("2026-07-31");
  });

  it("문의게시판은 오프셋 일시여야 한다 — 날짜만 보내면 타입 변환이 깨져 500 이다", () => {
    const params = new URLSearchParams(toApiQuery(criteriaOf(), { dateParam: "datetime" }));
    expect(params.get("from")).toMatch(/^2026-07-01T00:00:00[+-]\d{2}:\d{2}$/);
    // 끝은 하루의 끝이다. 00:00 으로 보내면 그날 쓴 글이 통째로 빠진다
    expect(params.get("to")).toMatch(/^2026-07-31T23:59:59[+-]\d{2}:\d{2}$/);
  });

  it("분류가 없는 게시판에는 categoryId 를 보내지 않는다 — 서버가 400 이다", () => {
    const params = new URLSearchParams(
      toApiQuery(criteriaOf({ categoryId: 3 }), { withCategory: false }),
    );
    expect(params.get("categoryId")).toBeNull();
  });

  it("mine 은 문의게시판에서만, 그것도 체크했을 때만 실린다", () => {
    expect(new URLSearchParams(toApiQuery(criteriaOf({ mine: true }))).get("mine")).toBeNull();
    expect(
      new URLSearchParams(toApiQuery(criteriaOf({ mine: true }), { withMine: true })).get("mine"),
    ).toBe("true");
    expect(
      new URLSearchParams(toApiQuery(criteriaOf({ mine: false }), { withMine: true })).get("mine"),
    ).toBeNull();
  });

  it("boardListUrl 은 경로에 붙인다", () => {
    expect(boardListUrl("/api/free-posts", criteriaOf())).toContain("/api/free-posts?from=");
  });
});
