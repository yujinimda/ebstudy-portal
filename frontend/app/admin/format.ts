/**
 * 관리 화면 표기 도우미.
 *
 * ★ 서버는 `OffsetDateTime`(`2026-07-30T12:00:00+09:00`)을 문자열로 준다.
 *   `new Date(...)` 로 파싱해 **보는 사람의 시간대**로 표시한다 — 오프셋이 문자열에
 *   들어 있으므로 시점은 어긋나지 않는다.
 */

/** `2026-07-30 12:00` */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const at = new Date(value);
  if (Number.isNaN(at.getTime())) return value;
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())} ` +
    `${pad(at.getHours())}:${pad(at.getMinutes())}`
  );
}

/** `1.2MB` — 첨부 목록에서 크기를 사람이 읽을 수 있게. */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}
