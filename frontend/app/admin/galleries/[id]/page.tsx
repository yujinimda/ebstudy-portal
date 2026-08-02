import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import GalleryDetailView from "./GalleryDetailView";

/** ★ Next 16 — `params` 는 Promise 다. `useListQuery()` 때문에 `<Suspense>` 도 필요하다. */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;

  return (
    <Suspense fallback={<BoardListFallback />}>
      <GalleryDetailView galleryId={Number(id)} />
    </Suspense>
  );
}
