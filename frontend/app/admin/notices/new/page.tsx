import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import NoticeForm from "../NoticeForm";

/**
 * 공지사항 등록 — 요구사항 3.3.
 *
 * ⚠️ 폼이 `useListQuery()`(= `useSearchParams`)로 목록 검색조건을 되돌려주므로
 *   여기에도 `<Suspense>` 경계가 필요하다.
 */
export default function Page() {
  return (
    <Suspense fallback={<BoardListFallback />}>
      <NoticeForm initial={{ categoryId: null, title: "", content: "", pinned: false }} />
    </Suspense>
  );
}
