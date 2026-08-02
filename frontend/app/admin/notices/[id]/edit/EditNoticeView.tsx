"use client";

import Alert from "@/components/Alert";
import { useBoardList } from "@/lib/board/useBoardList";
import NoticeForm from "../../NoticeForm";
import type { NoticeDetail } from "../../../types";

/**
 * 공지사항 수정 — 서버가 준 값으로 폼을 채운다.
 *
 * ★ 관리 상세(`GET /api/admin/notices/{id}`)는 **조회수를 올리지 않는다**.
 *   운영자가 수정 폼을 여는 것은 "조회"가 아니라서 서버가 그렇게 갈라 두었다.
 *
 * ★ 값이 오기 전에는 폼을 그리지 않는다. 빈 폼을 먼저 그린 뒤 값이 도착하면
 *   입력 중이던 내용이 덮여 쓰이거나, 초기값 동기화 코드가 한 겹 더 필요해진다.
 *   `NoticeForm` 은 `initial` 을 마운트 시 한 번만 읽는 단순한 컴포넌트로 남긴다.
 */
export default function EditNoticeView({ noticeId }: { noticeId: number }) {
  const { data, loading, error } = useBoardList<NoticeDetail>(`/api/admin/notices/${noticeId}`);

  if (error !== null) return <Alert error={error} />;
  if (data === null) return <p className="hint">{loading ? "불러오는 중…" : "내용이 없습니다."}</p>;

  return (
    <NoticeForm
      noticeId={noticeId}
      initial={{
        categoryId: data.categoryId,
        title: data.title,
        content: data.content,
        pinned: data.pinned,
      }}
    />
  );
}
