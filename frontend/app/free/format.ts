/** 화면 표기용 포맷터. 값 판정이 아니라 보여주기만 한다. */

/**
 * `2026-07-30T14:05:11+09:00` → `2026-07-30 14:05`.
 *
 * 서버는 오프셋이 붙은 ISO-8601 을 준다. `Date` 가 그것을 **보는 사람의 시간대**로
 * 바꿔 주므로 여기서 오프셋을 직접 다루지 않는다.
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (iso == null || iso === "") return "";
  const at = new Date(iso);
  if (Number.isNaN(at.getTime())) return iso;
  const pad = (n: number) => (n < 10 ? `0${n}` : String(n));
  return (
    `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}` +
    ` ${pad(at.getHours())}:${pad(at.getMinutes())}`
  );
}

/** 날짜만 — 목록 컬럼이 좁을 때 쓴다. */
export function formatDate(iso: string | null | undefined): string {
  return formatDateTime(iso).slice(0, 10);
}

/** 바이트 → `1.2 MB`. 첨부 목록에서 크기를 알려 주는 용도. */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
