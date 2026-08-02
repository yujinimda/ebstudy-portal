/**
 * ★ AC-34 · FR-035 — 로그인 후 복귀할 목적지를 검증한다.
 *
 * FR-020(원래 목적지 복귀)이 이 위험을 만들었다. 목적지를 그대로 신뢰하면
 * **오픈 리다이렉트**가 되어, 우리 도메인의 로그인 화면을 거쳐 공격자 사이트로 보내는
 * 피싱 링크를 만들 수 있다. 사용자는 우리 로그인 화면을 봤으므로 의심하지 않는다.
 *
 * **같은 출처의 상대 경로만 허용**하고, 아니면 무시하고 기본 화면으로 보낸다.
 *
 * 검증 위치가 화면인 것은 FR-002·FR-019(서버 검증)의 예외가 아니다 — 그 둘은
 * **입력 검증**과 **권한 검증**을 서버에서 하라는 것이고, 목적지 이동은 브라우저가
 * 수행하는 행위라 막는 자리도 브라우저 쪽이다.
 *
 * 순수 함수로 떼어낸 이유(5번 게이트 권고 4): 변형이 많아 E2E 로 전부 돌리면 느리고,
 * 입력·출력이 명확하므로 단위 테스트가 맞다(test-strategy.md 3장).
 */

/** 목적지가 없거나 허용되지 않을 때 갈 곳. */
export const DEFAULT_DESTINATION = "/";

/** URL 로 해석해볼 때 쓰는 기준 출처. 실재하지 않는 TLD 라 실제 요청이 나갈 수 없다. */
const BASE = "https://portal.invalid";

/**
 * 제어문자(0x00~0x20)와 DEL(0x7F)을 지운다 — 브라우저가 무시하거나 잘라내는 문자들이라
 * 남겨두면 탭·개행을 끼워 넣어 판정을 우회할 수 있다.
 *
 * 정규식 대신 코드포인트로 비교한다. 제어문자를 정규식 리터럴에 담으면 소스 파일에
 * **눈에 보이지 않는 바이트**가 들어가 리뷰에서 확인할 수 없다.
 */
function stripControlChars(input: string): string {
  let out = "";
  for (const ch of input) {
    const code = ch.codePointAt(0);
    if (code !== undefined && code > 0x20 && code !== 0x7f) {
      out += ch;
    }
  }
  return out;
}

/**
 * @param raw 쿼리스트링에서 받은 목적지(`?next=...`).
 * @returns 안전하면 그 경로, 아니면 {@link DEFAULT_DESTINATION}.
 */
export function safeRedirectPath(raw: string | null | undefined): string {
  if (raw == null) return DEFAULT_DESTINATION;

  // 1. 제어문자·공백 제거.
  //    ("있으면 거부"로 하면 정상 경로의 %20 까지 막게 되므로 제거로 간다 —
  //     디코딩 후 5번에서 다시 검사하므로 충분하다.)
  const value = stripControlChars(raw);
  if (value === "") return DEFAULT_DESTINATION;

  // 2. 반드시 "/" 로 시작. 절대 URL(https://evil.example)과
  //    스킴만 있는 값(javascript:, data:)을 여기서 떨군다.
  if (!value.startsWith("/")) return DEFAULT_DESTINATION;

  // 3. ★ 프로토콜 상대 URL — "//evil.example".
  //    "/" 로 시작해 2번을 통과하지만 브라우저는 **다른 출처**로 해석한다.
  //    "http 로 시작하지 않으면 상대 경로"로 판정하면 그대로 뚫리는 대표 변형이다.
  if (value.startsWith("//")) return DEFAULT_DESTINATION;

  // 4. 역슬래시 변형. 일부 브라우저가 역슬래시를 "/" 로 정규화해 //evil.example 과 같아진다.
  if (value.includes(String.fromCharCode(92))) return DEFAULT_DESTINATION;

  // 5. URL 인코딩 변형 — "/%2F%2Fevil.example".
  //    디코딩하면 //evil.example 이 된다. 디코딩한 값으로 2·3·4 를 다시 본다.
  //    디코딩이 실패하는 값(잘못된 % 시퀀스)은 신뢰하지 않는다.
  let decoded: string;
  try {
    decoded = decodeURIComponent(value);
  } catch {
    return DEFAULT_DESTINATION;
  }
  const decodedClean = stripControlChars(decoded);
  if (
    !decodedClean.startsWith("/") ||
    decodedClean.startsWith("//") ||
    decodedClean.includes(String.fromCharCode(92))
  ) {
    return DEFAULT_DESTINATION;
  }

  // 6. 마지막 방어선 — 실제로 URL 로 해석했을 때 출처가 바뀌는지 본다.
  //    위 규칙이 놓친 변형이 있어도 여기서 걸린다.
  try {
    const resolved = new URL(value, BASE);
    if (resolved.origin !== BASE) return DEFAULT_DESTINATION;
    // 경로·쿼리·해시만 남긴다 — 오리진 문자열이 결과에 섞이지 않게 한다.
    return `${resolved.pathname}${resolved.search}${resolved.hash}`;
  } catch {
    return DEFAULT_DESTINATION;
  }
}
