import { describe, expect, it } from "vitest";
import { DEFAULT_DESTINATION, safeRedirectPath } from "./safe-redirect";

/**
 * AC-34 — contracts/auth-api.md 가 지목한 변형을 그대로 케이스로 옮겼다.
 *
 * 계약 원문: *"절대 URL · 스킴 생략 형태(//evil.example) · 역슬래시 ·
 * URL 인코딩된 변형 · https: 로 시작하는 값 · 제어문자 포함"*
 *
 * ★ E2E 가 아니라 단위인 이유: 변형 전부를 브라우저로 돌리면 느리고, 판정은
 *   입력·출력이 명확한 순수 함수라 단위가 맞다(test-strategy.md 3장).
 */

const EVIL = "evil.example";
const BACKSLASH = String.fromCharCode(92);

describe("safeRedirectPath — 허용", () => {
  it.each([
    ["/mypage", "/mypage"],
    ["/admin", "/admin"],
    ["/board/1?page=2", "/board/1?page=2"],
    ["/mypage#section", "/mypage#section"],
    // 상위 경로 표기는 해석되어 정규화된다. 출처가 그대로이므로 안전하다.
    ["/a/../b", "/b"],
  ])("상대 경로 %s 는 그대로 통과한다", (input, expected) => {
    expect(safeRedirectPath(input)).toBe(expected);
  });
});

describe("safeRedirectPath — 차단", () => {
  it.each([
    // 절대 URL
    [`https://${EVIL}`],
    [`http://${EVIL}/path`],
    // ★ 스킴 생략 — 가장 걸리기 쉬운 변형. "/" 로 시작해 순진한 검사를 통과한다
    [`//${EVIL}`],
    [`//${EVIL}/path`],
    // 스킴만 있는 값
    [`https:${EVIL}`],
    ["javascript:alert(1)"],
    ["data:text/html,<script>alert(1)</script>"],
    // 역슬래시 — 브라우저가 "/" 로 정규화하면 // 와 같아진다
    [`${BACKSLASH}${BACKSLASH}${EVIL}`],
    [`/${BACKSLASH}${EVIL}`],
    [`/${BACKSLASH}${BACKSLASH}${EVIL}`],
    // URL 인코딩 변형 — 디코딩하면 //evil.example
    [`/%2F%2F${EVIL}`],
    [`/%2f%2f${EVIL}`],
    [`%2F%2F${EVIL}`],
    // 잘못된 인코딩 — 디코딩이 실패하는 값은 신뢰하지 않는다
    ["/%"],
    ["/%zz"],
    // 상대 경로가 아닌 것
    ["mypage"],
    ["../etc/passwd"],
    // 빈 값
    [""],
    ["   "],
  ])("%s 는 기본 화면으로 보낸다", (input) => {
    expect(safeRedirectPath(input)).toBe(DEFAULT_DESTINATION);
  });

  it("제어문자를 끼워 넣어도 우회되지 않는다", () => {
    const tab = String.fromCharCode(9);
    const newline = String.fromCharCode(10);
    const nul = String.fromCharCode(0);
    expect(safeRedirectPath(`/${tab}${tab}//${EVIL}`)).toBe(DEFAULT_DESTINATION);
    expect(safeRedirectPath(`${newline}//${EVIL}`)).toBe(DEFAULT_DESTINATION);
    expect(safeRedirectPath(`java${nul}script:alert(1)`)).toBe(DEFAULT_DESTINATION);
  });

  it("null · undefined 는 기본 화면으로 보낸다", () => {
    expect(safeRedirectPath(null)).toBe(DEFAULT_DESTINATION);
    expect(safeRedirectPath(undefined)).toBe(DEFAULT_DESTINATION);
  });
});
