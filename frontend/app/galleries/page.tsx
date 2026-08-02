import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import GalleryListView from "./GalleryListView";

/**
 * 갤러리 목록 — 요구사항 5.1.
 *
 * ★ 서버 컴포넌트로 두고 **`<Suspense>` 경계만** 만든다. 안쪽 `GalleryListView` 가
 *   `useSearchParams()`(= `useBoardSearchParams`)를 쓰기 때문이다. 경계가 없으면
 *   개발 서버는 통과하는데 `npm run build` 가 실패한다(Next 16).
 */
export default function Page() {
  return (
    <Suspense fallback={<BoardListFallback />}>
      <GalleryListView />
    </Suspense>
  );
}
