import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import NoticeListView from "./NoticeListView";

/** ⚠️ `useSearchParams()` 를 쓰는 목록은 `<Suspense>` 경계가 필요하다(Next 16 · build 실패 방지). */
export default function Page() {
  return (
    <Suspense fallback={<BoardListFallback />}>
      <NoticeListView />
    </Suspense>
  );
}
