"use client";

import {
  BOARD_SORTS,
  BOARD_SORT_LABELS,
  PAGE_SIZES,
  SORT_DIRECTIONS,
  SORT_DIRECTION_LABELS,
  type BoardListOptionPatch,
  type BoardSearchCriteria,
  type BoardSort,
  type PageSize,
  type SortDirection,
} from "@/lib/board/types";
import styles from "./board.module.css";

/**
 * 목록 툴바 — 요구사항 1.1 (개씩 보기 · 정렬 기준 · 정렬 방향).
 *
 * ★ **선택 즉시 반영된다.** 검색 버튼을 누르지 않는다. 그래서 draft 상태가 없고
 *   `onChange` 가 바로 URL 을 바꾼다(`useBoardSearchParams().applyOptions`).
 *
 * ★ 문의게시판은 `sortOptions` 에서 `CATEGORY` 를 빼야 한다. 분류가 없는 게시판에
 *   분류 정렬을 보내면 서버가 400 이다(`BoardSearchCriteria.of` — 조인이 생겨 결과가
 *   조용히 비는 것을 막으려고 받는 자리에서 거부한다).
 */
export interface BoardListToolbarProps {
  criteria: BoardSearchCriteria;
  /** 보통 `useBoardSearchParams().applyOptions` */
  onChange: (patch: BoardListOptionPatch) => void;
  /** 전체 건수. 넘기면 왼쪽에 표시한다 */
  totalElements?: number;
  /** 기본은 4종 전부. **문의게시판은 `CATEGORY` 를 뺀 배열을 넘긴다** */
  sortOptions?: readonly BoardSort[];
  /** 툴바 오른쪽에 붙일 것(예: `글 등록` 버튼) */
  children?: React.ReactNode;
  disabled?: boolean;
}

export default function BoardListToolbar({
  criteria,
  onChange,
  totalElements,
  sortOptions = BOARD_SORTS,
  children,
  disabled = false,
}: BoardListToolbarProps) {
  return (
    <div className={styles.toolbar}>
      <span className={styles.total}>
        {totalElements === undefined ? null : (
          <>
            전체 <strong>{totalElements.toLocaleString()}</strong>건
          </>
        )}
      </span>

      <div className={styles.toolbarControls}>
        <select
          className={styles.control}
          value={criteria.size}
          disabled={disabled}
          aria-label="개씩 보기"
          onChange={(e) => onChange({ size: Number(e.target.value) as PageSize })}
        >
          {PAGE_SIZES.map((size) => (
            <option key={size} value={size}>
              {size}개씩 보기
            </option>
          ))}
        </select>

        <select
          className={styles.control}
          value={criteria.sort}
          disabled={disabled}
          aria-label="정렬 기준"
          onChange={(e) => onChange({ sort: e.target.value as BoardSort })}
        >
          {sortOptions.map((sort) => (
            <option key={sort} value={sort}>
              {BOARD_SORT_LABELS[sort]}
            </option>
          ))}
        </select>

        <select
          className={styles.control}
          value={criteria.direction}
          disabled={disabled}
          aria-label="정렬 방향"
          onChange={(e) => onChange({ direction: e.target.value as SortDirection })}
        >
          {SORT_DIRECTIONS.map((direction) => (
            <option key={direction} value={direction}>
              {SORT_DIRECTION_LABELS[direction]}
            </option>
          ))}
        </select>

        {children}
      </div>
    </div>
  );
}
