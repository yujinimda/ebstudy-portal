import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import GalleryEditView from "./GalleryEditView";

/**
 * 갤러리 수정 — 요구사항 5.3.
 *
 * Next 16 에서 `params` 는 Promise 다. 서버에서 await 해 문자열만 넘긴다.
 * 안쪽 뷰가 `useSearchParams` 계열 훅을 쓰므로 `<Suspense>` 가 필요하다.
 */
export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <Suspense fallback={<BoardListFallback />}>
      <GalleryEditView id={id} />
    </Suspense>
  );
}
