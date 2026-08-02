"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import RequireAuth from "@/components/RequireAuth";
import { apiFetch } from "@/lib/api";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import InquiryForm, { type InquiryFormValues } from "../InquiryForm";
import styles from "../inquiries.module.css";
import type { InquiryDetail } from "../types";

/**
 * 요구사항 6.4 등록 — 로그인 필요(AC-16 · AC-17).
 *
 * ★ 미로그인으로 들어오면 `RequireAuth` 가 로그인 화면으로 보내면서 `?next=` 에
 *   **search 까지 포함한 지금 주소**를 싣는다. 그래서 로그인 후 목록이 아니라
 *   **바로 이 등록 화면**으로 돌아오고, 목록의 검색조건도 함께 살아 돌아온다
 *   (요구사항 1.3 마지막 줄 · 001 AC-24). 화면이 따로 할 일이 없다.
 *
 * ★ 로그인 여부로 버튼을 감추는 것은 편의일 뿐이다 — 등록 요청 자체를 서버가 막는다.
 */
export default function NewInquiryView() {
  const router = useRouter();
  const listQuery = useListQuery();
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(values: InquiryFormValues) {
    setError(null);
    setSubmitting(true);
    try {
      const created = await apiFetch<InquiryDetail>("/api/inquiries", {
        method: "POST",
        body: JSON.stringify({
          title: values.title,
          content: values.content,
          secret: values.secret,
          // 비밀글이 아니면 아예 보내지 않는다(서버도 무시하지만 뜻을 정확히 담는다)
          secretPassword: values.secret ? values.secretPassword : null,
        }),
      });
      // 등록한 글로 바로 보낸다. 비밀글이어도 작성자는 비밀번호를 묻지 않는다
      router.push(`/inquiries/${created.id}${listQuery}`);
    } catch (caught) {
      setError(caught);
      setSubmitting(false);
    }
  }

  return (
    <main className={styles.page}>
      <h1>문의 등록</h1>
      <p className="subtitle">모든 검증은 서버가 합니다. 아래 안내는 참고용입니다.</p>

      <RequireAuth>
        {() => (
          <InquiryForm
            mode="create"
            initial={{ title: "", content: "", secret: false, secretPassword: "" }}
            listQuery={listQuery}
            error={error}
            submitting={submitting}
            onSubmit={handleSubmit}
          />
        )}
      </RequireAuth>
    </main>
  );
}
