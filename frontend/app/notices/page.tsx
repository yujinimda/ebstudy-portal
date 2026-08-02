import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import NoticeListView from "./NoticeListView";

/**
 * 공지사항 목록 — 요구사항 3.1.
 *
 * ★ 이 파일은 **서버 컴포넌트**다. 하는 일은 `<Suspense>` 경계를 만드는 것뿐이다.
 *   `NoticeListView` 가 `useSearchParams()` 를 쓰는데, 경계가 없으면 프리렌더가
 *   막혀 **개발 서버는 통과하고 `npm run build` 가 실패한다.**
 */
export const metadata = {
  title: "공지사항",
};

export default function NoticesPage() {
  return (
    <Suspense fallback={<BoardListFallback />}>
      <NoticeListView />
    </Suspense>
  );
}
