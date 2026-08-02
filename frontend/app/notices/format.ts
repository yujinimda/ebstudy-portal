/**
 * 공지 화면의 날짜 표기.
 *
 * 서버는 `OffsetDateTime` 을 ISO-8601 문자열(`2026-07-30T14:03:11+09:00`)로 준다.
 * `toLocaleString` 을 쓰지 않는 이유: 브라우저 로캘에 따라 표기가 갈려서
 * 목록의 열 폭이 사용자마다 달라진다. 표기는 우리가 고정한다.
 */

function pad2(value: number): string {
  return value < 10 ? `0${value}` : String(value);
}

/** 목록용 `yyyy-MM-dd`. */
export function formatDate(iso: string): string {
  const at = new Date(iso);
  if (Number.isNaN(at.getTime())) return iso;
  return `${at.getFullYear()}-${pad2(at.getMonth() + 1)}-${pad2(at.getDate())}`;
}

/** 상세용 `yyyy-MM-dd HH:mm`. 상세는 열 폭 제약이 없어 시각까지 보여준다. */
export function formatDateTime(iso: string): string {
  const at = new Date(iso);
  if (Number.isNaN(at.getTime())) return iso;
  return `${formatDate(iso)} ${pad2(at.getHours())}:${pad2(at.getMinutes())}`;
}
