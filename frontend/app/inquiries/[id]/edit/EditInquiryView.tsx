"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import Alert from "@/components/Alert";
import RequireAuth from "@/components/RequireAuth";
import { apiFetch } from "@/lib/api";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import { grantHeaders } from "../../grant";
import InquiryForm, { type InquiryFormValues } from "../../InquiryForm";
import styles from "../../inquiries.module.css";
import type { InquiryDetail } from "../../types";

/**
 * 요구사항 6.4 수정 — 본인 + **미답변일 때만**(FR-015 · AC-19 · AC-20).
 *
 * ★ 화면이 `editable` 을 보고 폼을 감추지만 **그것은 권한 검증이 아니다.**
 *   서버는 저장 시점에 소유자와 답변 여부를 **다시** 본다 — 수정 화면을 열어 둔 사이
 *   관리자가 답변을 달 수 있고, "화면을 열 때 미답변이었다" 는 사실은 권한의 근거가
 *   되지 않는다. 그때는 409 `INQUIRY_ALREADY_ANSWERED` 가 오고 그 detail 을 그대로 보여준다.
 *
 * ★ 기존 값을 채우려면 상세를 한 번 읽어야 한다. 비밀글이면 열람 통과가 필요하므로
 *   `X-Inquiry-Grant` 헤더를 함께 보낸다(작성자는 통과 없이도 서버가 열어 준다).
 *
 * ★ **비밀번호 칸은 비워 둔 채로 시작한다.** 기존 해시는 서버가 절대 내려주지 않고,
 *   비워서 보내면 "기존 유지" 로 해석된다(006 Edge Cases).
 */

interface Snapshot {
  id: string | null;
  detail: InquiryDetail | null;
  error: unknown;
}

export default function EditInquiryView() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const listQuery = useListQuery();

  const [snapshot, setSnapshot] = useState<Snapshot>({ id: null, detail: null, error: null });
  const [saveError, setSaveError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    // ★ effect 본문에서 동기 setState 를 하지 않는다. cancelled 로 늦은 응답을 버린다
    let cancelled = false;
    apiFetch<InquiryDetail>(`/api/inquiries/${id}`, { headers: grantHeaders(id) })
      .then((detail) => {
        if (!cancelled) setSnapshot({ id, detail, error: null });
      })
      .catch((caught: unknown) => {
        if (!cancelled) setSnapshot({ id, detail: null, error: caught });
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  const loading = snapshot.id !== id;
  const detail = snapshot.detail;

  async function handleSubmit(values: InquiryFormValues) {
    setSaveError(null);
    setSubmitting(true);
    try {
      await apiFetch<InquiryDetail>(`/api/inquiries/${id}`, {
        method: "PUT",
        body: JSON.stringify({
          title: values.title,
          content: values.content,
          secret: values.secret,
          // 빈 문자열은 보내지 않는다 — 서버가 "기존 비밀번호 유지" 로 읽는 신호다.
          // 비밀글을 끄면 서버가 저장된 비밀번호까지 지운다(AC-26)
          secretPassword:
            values.secret && values.secretPassword !== "" ? values.secretPassword : null,
        }),
      });
      router.push(`/inquiries/${id}${listQuery}`);
    } catch (caught) {
      setSaveError(caught);
      setSubmitting(false);
    }
  }

  return (
    <main className={styles.page}>
      <h1>문의 수정</h1>
      <p className="subtitle">모든 검증은 서버가 합니다. 아래 안내는 참고용입니다.</p>

      <RequireAuth>
        {() => {
          if (loading) return <p className="hint">불러오는 중…</p>;

          if (detail === null) {
            return (
              <>
                <Alert error={snapshot.error} />
                <p className={styles.actions}>
                  <Link
                    className={`${styles.linkButton} ${styles.linkButtonSecondary}`}
                    href={`/inquiries${listQuery}`}
                  >
                    목록
                  </Link>
                </p>
              </>
            );
          }

          // 본인 것이 아니거나 이미 답변이 달렸다. 서버도 같은 판정으로 거부하므로
          // 폼을 그려 왕복시키는 대신 여기서 멈춘다(편의일 뿐 — 검증은 서버가 한다)
          if (!detail.editable) {
            return (
              <>
                <p className="alert error" role="alert">
                  {detail.answered
                    ? "답변이 등록된 문의는 수정할 수 없습니다."
                    : "본인이 작성한 문의만 수정할 수 있습니다."}
                </p>
                <p className={styles.actions}>
                  <Link
                    className={`${styles.linkButton} ${styles.linkButtonSecondary}`}
                    href={`/inquiries/${id}${listQuery}`}
                  >
                    상세로
                  </Link>
                </p>
              </>
            );
          }

          return (
            <InquiryForm
              mode="edit"
              initial={{
                title: detail.title,
                content: detail.content ?? "",
                secret: detail.secret,
                // 기존 비밀번호는 서버가 내려주지 않는다. 비워 두면 유지된다
                secretPassword: "",
              }}
              listQuery={listQuery}
              error={saveError}
              submitting={submitting}
              onSubmit={handleSubmit}
            />
          );
        }}
      </RequireAuth>
    </main>
  );
}
