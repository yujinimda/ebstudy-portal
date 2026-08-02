import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import GalleryCreateView from "./GalleryCreateView";

/**
 * 갤러리 등록 — 요구사항 5.3.
 *
 * 안쪽 뷰가 `useListQuery()`(= `useSearchParams`)를 쓰므로 `<Suspense>` 가 필요하다(Next 16).
 */
export default function Page() {
  return (
    <Suspense fallback={<BoardListFallback />}>
      <GalleryCreateView />
    </Suspense>
  );
}
