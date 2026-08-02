import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import GalleryListView from "./GalleryListView";

/** ⚠️ `useSearchParams()` 를 쓰는 목록은 `<Suspense>` 경계가 필요하다(Next 16). */
export default function Page() {
  return (
    <Suspense fallback={<BoardListFallback />}>
      <GalleryListView />
    </Suspense>
  );
}
