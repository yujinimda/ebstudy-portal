/**
 * `<Suspense fallback>` 자리에 쓰는 목록 자리표시자.
 *
 * ★ 왜 필요한가: `useSearchParams()` 를 쓰는 컴포넌트는 프리렌더에서 빠지고
 *   가장 가까운 `<Suspense>` 경계까지가 클라이언트 렌더로 바뀐다. 경계가 없으면
 *   **개발 서버는 통과하는데 `npm run build` 가 실패한다.**
 *   경계를 만들 때 fallback 이 매번 필요하니 하나 만들어 둔다.
 *
 * 서버 컴포넌트다("use client" 없음) — 초기 HTML 에 그대로 실린다.
 */
export default function BoardListFallback() {
  return <p className="hint">목록을 불러오는 중…</p>;
}
