import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import FreeDetailView from "./FreeDetailView";

/**
 * 자유게시판 상세 — 요구사항 4.2.
 *
 * ★ Next 16 에서 `params` 는 **Promise** 다. 서버 컴포넌트에서 await 해서
 *   클라이언트 뷰에는 값만 넘긴다(뷰에서 `use(params)` 를 쓰는 것보다 단순하다).
 * ★ `<Suspense>` 는 뷰가 `useSearchParams`(목록 복귀 쿼리)를 쓰기 때문에 필요하다.
 */
export default async function Page({ params }: { params: Promise<{ postId: string }> }) {
  const { postId } = await params;

  return (
    <Suspense fallback={<BoardListFallback />}>
      <FreeDetailView postId={postId} />
    </Suspense>
  );
}
