import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import FreePostForm from "../FreePostForm";

/**
 * 글 등록 — 요구사항 4.3.
 *
 * ★ 폼이 `useListQuery`(= `useSearchParams`)로 목록 복귀 조건을 들고 있으므로
 *   `<Suspense>` 경계가 필요하다. 없으면 `npm run build` 가 실패한다(Next 16).
 */
export default function Page() {
  return (
    <Suspense fallback={<BoardListFallback />}>
      <FreePostForm />
    </Suspense>
  );
}
