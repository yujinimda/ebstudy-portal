"use client";

import { useBoardList } from "./useBoardList";
import type { BoardType, CategoryOption } from "./types";

/**
 * 분류 셀렉트가 쓰는 목록 — 요구사항 1.1 "관리자가 등록한 해당 게시판 카테고리 목록".
 *
 * 기본 경로는 `GET /api/categories?boardType=…` 하나다. 게시판별 경로
 * (`/api/notices/categories` 등)도 같은 값을 주지만, 셀렉트 하나 때문에 게시판마다 다른
 * 경로를 외우게 하지 않는다. 관리 화면처럼 **비활성 분류까지** 필요하면 `path` 로 바꾼다
 * (`/api/admin/categories?boardType=…` · `/api/notices/categories`).
 *
 * ★ **문의게시판은 분류가 없다**(요구사항 0장 표). `boardType` 에 `"INQUIRY"` 를 넣지 말고
 *   `null` 을 넘겨라 — 그러면 요청 자체를 하지 않고 빈 목록을 준다.
 *
 * ★ "전체 분류" 항목은 여기 없다. 그건 DB 의 분류가 아니라 **"조건 없음"이라는 화면 상태**라
 *   서버가 만들지 않는다(`CategoryController` 주석). `BoardSearchBar` 가 붙인다.
 */
export interface CategoriesResult {
  /** 서버가 표시 순서대로 준다. 화면에서 다시 정렬하지 않는다 */
  categories: CategoryOption[];
  loading: boolean;
  error: unknown;
}

export function useCategories(
  boardType: BoardType | null,
  options: { path?: string } = {},
): CategoriesResult {
  const url =
    boardType === null || boardType === "INQUIRY"
      ? null
      : (options.path ?? `/api/categories?boardType=${boardType}`);

  const { data, loading, error } = useBoardList<CategoryOption[]>(url);

  // 분류를 못 불러왔다고 목록 화면 전체를 오류로 만들지 않는다 — 셀렉트가 "전체 분류" 하나만
  // 남을 뿐이고 목록 자체는 정상이다. 오류는 호출부가 보고 싶으면 보면 된다
  return { categories: data ?? [], loading, error };
}
