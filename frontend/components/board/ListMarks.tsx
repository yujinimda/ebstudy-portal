"use client";

import styles from "./board.module.css";

/**
 * 목록 한 줄에 붙는 작은 표시들 — 요구사항 1.1 · 4.1 · 6.1.
 *
 * ★ 판정을 여기서 하지 않는다. 특히 `new` 는 **서버가 준 boolean 을 그대로** 받는다
 *   (`newBadge`(자유) · `isNew`(공지·갤러리·문의) — 필드 이름은 게시판마다 다르다).
 *   화면에서 `createdAt` 을 보고 7일을 계산하면 **사용자 기기의 시계가 기준**이 되어,
 *   시계가 틀어진 기기에서는 배지가 안 붙거나 영원히 붙는다(`NewBadgePolicy` 주석).
 *   "게시판별로 다르게 설정 가능"도 서버 설정이라 화면이 알 수 없다.
 */

/** 요구사항 1.1 `new` 아이콘. 서버 판정값을 그대로 넘긴다. */
export function NewBadge({ show }: { show: boolean }) {
  if (!show) return null;
  return (
    <span className={`${styles.badge} ${styles.badgeNew}`} aria-label="새 글">
      NEW
    </span>
  );
}

/**
 * 요구사항 4.1 첨부 아이콘.
 *
 * 개수를 받지만 기본은 클립만 그린다 — 서버가 `attachmentCount` 를 함께 주므로
 * `+3` 같은 표기가 필요해질 때 요청을 다시 만들 필요가 없다.
 */
export function AttachmentIcon({
  count,
  showCount = false,
}: {
  count: number;
  showCount?: boolean;
}) {
  if (count <= 0) return null;
  return (
    <span className={styles.attachmentIcon}>
      <svg
        width="12"
        height="12"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
      </svg>
      <span className={styles.srOnly}>첨부파일 {count}개</span>
      {showCount && <span aria-hidden="true"> {count}</span>}
    </span>
  );
}

/** 요구사항 4.1 댓글 수. 0이면 그리지 않는다 — 빈 `[0]` 은 잡음이다. */
export function CommentCount({ count }: { count: number }) {
  if (count <= 0) return null;
  return (
    <span className={styles.commentCount}>
      <span aria-hidden="true">[{count}]</span>
      <span className={styles.srOnly}>댓글 {count}개</span>
    </span>
  );
}

/** 요구사항 6.2 비밀글 자물쇠. */
export function LockIcon({ show }: { show: boolean }) {
  if (!show) return null;
  return (
    <span className={styles.attachmentIcon}>
      <svg
        width="12"
        height="12"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <rect x="4" y="10" width="16" height="11" rx="2" />
        <path d="M8 10V7a4 4 0 0 1 8 0v3" />
      </svg>
      <span className={styles.srOnly}>비밀글</span>
    </span>
  );
}

/**
 * 상태 배지 — 문의게시판의 `답변완료`/`미답변`(요구사항 6.1), 공지의 `알림`(3.1) 처럼
 * 게시판마다 문구가 다른 표시에 쓴다. 문구를 이 컴포넌트가 정하지 않는다.
 */
export function StatusBadge({
  children,
  tone = "muted",
}: {
  children: React.ReactNode;
  tone?: "ok" | "muted" | "new";
}) {
  const toneClass =
    tone === "ok" ? styles.badgeOk : tone === "new" ? styles.badgeNew : styles.badgeMuted;
  return <span className={`${styles.badge} ${toneClass}`}>{children}</span>;
}
