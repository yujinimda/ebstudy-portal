import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import CategoriesView from "./CategoriesView";

/**
 * ⚠️ `useSearchParams()`(탭의 `boardType`)를 쓰는 컴포넌트는 반드시 `<Suspense>` 안에 둔다.
 *   경계가 없으면 개발 서버는 통과하는데 `npm run build` 가 실패한다(Next 16).
 */
export default function Page() {
  return (
    <Suspense fallback={<BoardListFallback />}>
      <CategoriesView />
    </Suspense>
  );
}
