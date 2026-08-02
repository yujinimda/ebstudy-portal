import { Suspense } from "react";
import BoardListFallback from "@/components/board/BoardListFallback";
import InquiryListView from "./InquiryListView";

/**
 * 문의게시판 목록 — 요구사항 6.1.
 *
 * ⚠️ 여기는 **서버 컴포넌트**이고 하는 일은 `<Suspense>` 경계를 만드는 것뿐이다.
 *    목록 화면은 `useSearchParams()`(검색조건 유지)를 쓰는데, 그 훅은 프리렌더를
 *    가장 가까운 Suspense 경계까지 클라이언트 렌더로 떨어뜨린다. 경계가 없으면
 *    **개발 서버는 통과하고 `npm run build` 가 실패한다.**
 */
export default function InquiriesPage() {
  return (
    <Suspense fallback={<BoardListFallback />}>
      <InquiryListView />
    </Suspense>
  );
}
