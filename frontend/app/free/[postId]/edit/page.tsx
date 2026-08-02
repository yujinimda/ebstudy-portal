import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import FreePostForm from "../../FreePostForm";

/**
 * 글 수정 — 요구사항 4.3. 등록과 같은 폼을 쓴다(`postId` 가 있으면 수정 모드).
 *
 * ★ 본인 글이 아니면 서버가 403 을 준다. 상세에서 버튼을 숨기는 것은 편의일 뿐이라
 *   주소로 직접 들어오는 경로도 막혀 있어야 한다(요구사항 1.3).
 */
export default async function Page({ params }: { params: Promise<{ postId: string }> }) {
  const { postId } = await params;

  return (
    <Suspense fallback={<BoardListFallback />}>
      <FreePostForm postId={postId} />
    </Suspense>
  );
}
