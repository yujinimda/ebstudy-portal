/**
 * 비밀글 열람 통과(`grantToken`) 보관 — AC-37 "한 번 연 글은 유효 시간 동안 다시 묻지 않는다".
 *
 * ★ **sessionStorage 다.** localStorage 가 아니다:
 *   탭을 닫으면 사라져야 한다. 공용 PC 에서 앞사람이 연 비밀글이 뒷사람에게 열려 있으면
 *   비밀번호를 물은 의미가 없다. 서버 통과에도 유효 시간이 있지만(SecretReadGrantService),
 *   화면이 더 짧게 잡는 것은 언제나 안전한 방향이다.
 *
 * ★ **비밀번호는 저장하지 않는다.** 저장하는 것은 서버가 발급한 통과 문자열뿐이고,
 *   그 통과로 얻는 것은 **열람뿐**이다 — 수정·삭제는 소유자 확인 하나로만 판정된다(AC-33).
 *
 * ★ 통과를 **질의 문자열이 아니라 헤더**로 보낸다(서버 `InquiryController.GRANT_HEADER`).
 *   URL 에 실으면 접근 로그·리퍼러·북마크에 남는다.
 */

const GRANT_HEADER = "X-Inquiry-Grant";
const KEY_PREFIX = "inquiry-grant:";

function keyOf(id: string | number): string {
  return `${KEY_PREFIX}${id}`;
}

/** 서버 렌더·프라이빗 모드처럼 sessionStorage 를 못 쓰는 자리에서도 터지지 않게 감싼다. */
export function loadGrant(id: string | number): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.sessionStorage.getItem(keyOf(id));
  } catch {
    return null;
  }
}

export function saveGrant(id: string | number, token: string): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.setItem(keyOf(id), token);
  } catch {
    // 저장하지 못해도 기능은 성립한다 — 다음 진입에서 비밀번호를 한 번 더 물을 뿐이다
  }
}

export function clearGrant(id: string | number): void {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.removeItem(keyOf(id));
  } catch {
    // 무시 — 지우지 못해도 서버가 만료시킨다
  }
}

/** 통과가 없으면 **헤더 자체를 붙이지 않는다**(빈 값을 보내면 서버가 검증을 한 번 더 돈다). */
export function grantHeaders(id: string | number): Record<string, string> {
  const token = loadGrant(id);
  return token === null ? {} : { [GRANT_HEADER]: token };
}
