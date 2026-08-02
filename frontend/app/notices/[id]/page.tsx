import { Suspense } from "react";
import NoticeDetailView from "./NoticeDetailView";

/**
 * 공지사항 상세 — 요구사항 3.2.
 *
 * ★ 서버 컴포넌트. `<Suspense>` 경계만 만든다 — `NoticeDetailView` 가
 *   `useListQuery()`(= `useSearchParams()`) 로 검색조건 원문을 읽기 때문이다.
 *   경계가 없으면 `npm run build` 가 실패한다(개발 서버는 통과한다).
 */
export const metadata = {
  title: "공지사항",
};

export default function NoticeDetailPage() {
  return (
    <Suspense fallback={<p className="hint">불러오는 중…</p>}>
      <NoticeDetailView />
    </Suspense>
  );
}
