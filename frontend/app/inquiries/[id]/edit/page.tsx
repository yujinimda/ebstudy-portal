import { Suspense } from "react";
import EditInquiryView from "./EditInquiryView";

/**
 * 문의 수정 — 요구사항 6.4. 본인 + **미답변일 때만**(서버가 다시 확인한다).
 *
 * ⚠️ `useListQuery()`(= `useSearchParams`)를 쓰므로 `<Suspense>` 경계가 필요하다.
 */
export default function EditInquiryPage() {
  return (
    <Suspense fallback={<p className="hint">불러오는 중…</p>}>
      <EditInquiryView />
    </Suspense>
  );
}
