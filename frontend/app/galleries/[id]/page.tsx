import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import GalleryDetailView from "./GalleryDetailView";

/**
 * 갤러리 상세 — 요구사항 5.2.
 *
 * ★ Next 16 에서 `params` 는 **Promise** 다. 서버 컴포넌트에서 await 해 문자열만 넘긴다.
 * ★ 안쪽 뷰가 `useListQuery()`(= `useSearchParams`)를 쓰므로 `<Suspense>` 가 필요하다.
 */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <Suspense fallback={<BoardListFallback />}>
      <GalleryDetailView id={id} />
    </Suspense>
  );
}
