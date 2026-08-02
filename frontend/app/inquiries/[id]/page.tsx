import { Suspense } from "react";
import InquiryDetailView from "./InquiryDetailView";

/**
 * 문의 상세 — 요구사항 6.3. 비밀글이면 **비밀번호 입력 화면**이 대신 그려진다(6.2).
 *
 * ⚠️ 서버 컴포넌트로 두고 `<Suspense>` 경계만 만든다. 상세 화면은 `useListQuery()`
 *    (= `useSearchParams`)로 목록의 검색조건을 그대로 되돌려 주므로 경계가 필요하다 —
 *    없으면 dev 는 통과하고 `npm run build` 가 실패한다.
 *
 * ★ `params` 를 받지 않는다. Next 16 에서 서버 컴포넌트의 `params` 는 Promise 라
 *   await 해서 내려보내야 하는데, 어차피 클라이언트 컴포넌트가 `useParams()` 로 읽을 수
 *   있으므로 경계를 더 얇게 둔다.
 */
export default function InquiryDetailPage() {
  return (
    <Suspense fallback={<p className="hint">불러오는 중…</p>}>
      <InquiryDetailView />
    </Suspense>
  );
}
