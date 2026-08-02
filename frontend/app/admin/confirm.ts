/**
 * 요구사항 1.2 — 등록·수정·삭제·취소 전 확인창.
 *
 * ★ 문구를 상수로 모아 둔 이유: 같은 동작에 화면마다 다른 문장이 나오면 사용자는
 *   "다른 일이 일어나는 줄" 안다. 요구사항이 쓴 문장을 그대로 옮긴다.
 *
 * ★ `window.confirm` 을 쓰는 것은 의도적인 선택이다. 확인창 하나를 위해 모달
 *   컴포넌트·포커스 트랩·스크롤 잠금을 만드는 것보다, 브라우저가 이미 접근성까지
 *   갖춰 둔 것을 쓰는 편이 낫다. 디자인 요구가 생기면 이 함수 하나만 바꾸면 된다.
 */

export const CONFIRM = {
  create: "정말로 등록 하시겠습니까?",
  update: "정말로 수정 하시겠습니까?",
  remove: "정말로 삭제 하시겠습니까",
  cancel: "작성을 취소하시겠습니까?",
  answer: "답변 하시겠습니까?",
} as const;

export function confirmAction(message: string): boolean {
  // 서버 렌더 중에는 window 가 없다. 그때는 "확인하지 않은 것"으로 본다
  if (typeof window === "undefined") return false;
  return window.confirm(message);
}
