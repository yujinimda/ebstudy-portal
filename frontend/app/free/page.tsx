import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import FreeListView from "./FreeListView";

/**
 * 자유게시판 목록 — 요구사항 4.1.
 *
 * ★ 이 파일은 **서버 컴포넌트**로 두고 `<Suspense>` 경계만 만든다.
 *   `useSearchParams()`(= `useBoardSearchParams`)를 쓰는 컴포넌트를 경계 없이 두면
 *   개발 서버는 통과하는데 `npm run build` 가 실패한다(Next 16).
 */
export default function Page() {
  return (
    <Suspense fallback={<BoardListFallback />}>
      <FreeListView />
    </Suspense>
  );
}
