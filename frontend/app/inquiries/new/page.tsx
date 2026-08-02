import { Suspense } from "react";
import NewInquiryView from "./NewInquiryView";

/**
 * 문의 등록 — 요구사항 6.4.
 *
 * ⚠️ `useListQuery()`(= `useSearchParams`)를 쓰므로 `<Suspense>` 경계가 필요하다.
 *    목록에서 실어 보낸 검색조건을 취소·등록 후 그대로 되돌려 준다.
 */
export default function NewInquiryPage() {
  return (
    <Suspense fallback={<p className="hint">불러오는 중…</p>}>
      <NewInquiryView />
    </Suspense>
  );
}
