import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import FreeDetailView from "./FreeDetailView";

/** ★ Next 16 — `params` 는 Promise 다. `useListQuery()` 때문에 `<Suspense>` 도 필요하다. */
export default async function Page({ params }: { params: Promise<{ postId: string }> }) {
  const { postId } = await params;

  return (
    <Suspense fallback={<BoardListFallback />}>
      <FreeDetailView postId={Number(postId)} />
    </Suspense>
  );
}
