import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import EditNoticeView from "./EditNoticeView";

/**
 * ★ Next 16 에서 `params` 는 **Promise** 다. 서버 컴포넌트에서 await 해서
 *   클라이언트 뷰에 값으로 넘긴다(클라이언트에서 `use(params)` 를 쓰는 것보다 단순하다).
 */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  return (
    <Suspense fallback={<BoardListFallback />}>
      <EditNoticeView noticeId={Number(id)} />
    </Suspense>
  );
}
