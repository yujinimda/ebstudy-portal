"use client";

import styles from "./board.module.css";

/**
 * 페이징 — 요구사항 1.1 `<<` `<` 1~10 `>` `>>`.
 *
 * ★ `<` `>` 는 **한 페이지가 아니라 한 블록**(기본 10페이지)을 움직인다.
 *   숫자가 1~10 씩 묶여 나오는 UI 에서 `<<`(맨 앞) · `>>`(맨 뒤)와 짝이 되는 해석이고,
 *   `<` 가 한 페이지면 숫자 버튼과 하는 일이 겹친다.
 *
 * `page` 는 **0부터**다(서버·`BoardSearchCriteria` 와 같다). 화면에 그리는 숫자만 +1 한다 —
 * 변환을 한 곳에 몰아 두지 않으면 반드시 한 칸 어긋난다.
 */
export interface PaginationProps {
  /** 0부터 */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  /** 숫자 버튼 묶음 크기. 요구사항의 "1~10" */
  blockSize?: number;
}

export default function Pagination({
  page,
  totalPages,
  onChange,
  blockSize = 10,
}: PaginationProps) {
  // 결과가 없거나 한 페이지뿐이면 그리지 않는다 — 누를 수 없는 버튼 줄은 잡음이다
  if (totalPages <= 1) return null;

  const blockStart = Math.floor(page / blockSize) * blockSize;
  const blockEnd = Math.min(blockStart + blockSize, totalPages);
  const numbers: number[] = [];
  for (let i = blockStart; i < blockEnd; i += 1) numbers.push(i);

  const atFirstBlock = blockStart === 0;
  const atLastBlock = blockEnd >= totalPages;

  return (
    <nav className={styles.pagination} aria-label="페이지">
      <button
        type="button"
        className={styles.pageButton}
        onClick={() => onChange(0)}
        disabled={page === 0}
        aria-label="첫 페이지"
      >
        &laquo;
      </button>
      <button
        type="button"
        className={styles.pageButton}
        onClick={() => onChange(Math.max(0, blockStart - blockSize))}
        disabled={atFirstBlock}
        aria-label="이전 10페이지"
      >
        &lsaquo;
      </button>

      {numbers.map((number) => (
        <button
          key={number}
          type="button"
          className={
            number === page
              ? `${styles.pageButton} ${styles.pageButtonCurrent}`
              : styles.pageButton
          }
          onClick={() => onChange(number)}
          // 지금 페이지임을 스크린리더에도 알린다. disabled 로 막으면 "왜 못 누르지"가 된다
          aria-current={number === page ? "page" : undefined}
        >
          {number + 1}
        </button>
      ))}

      <button
        type="button"
        className={styles.pageButton}
        onClick={() => onChange(blockEnd)}
        disabled={atLastBlock}
        aria-label="다음 10페이지"
      >
        &rsaquo;
      </button>
      <button
        type="button"
        className={styles.pageButton}
        onClick={() => onChange(totalPages - 1)}
        disabled={page === totalPages - 1}
        aria-label="마지막 페이지"
      >
        &raquo;
      </button>
    </nav>
  );
}
