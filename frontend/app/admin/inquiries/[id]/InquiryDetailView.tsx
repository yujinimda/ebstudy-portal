"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import Alert from "@/components/Alert";
import { StatusBadge } from "@/components/board/ListMarks";
import { apiFetch } from "@/lib/api";
import { useBoardList } from "@/lib/board/useBoardList";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import styles from "../../admin.module.css";
import { CONFIRM, confirmAction } from "../../confirm";
import { formatDateTime } from "../../format";
import type { InquiryAnswer, InquiryDetail } from "../../types";

/**
 * 답변 입력 — 요구사항 6.5 (`답변완료` 버튼 · `"답변 하시겠습니까?"` 확인).
 *
 * ★ 등록과 수정이 **다른 메서드**다: 없으면 `POST .../answer`(201), 있으면 `PUT`(200).
 *   같은 경로에 상태로 갈라지므로 `answer` 유무로 결정한다.
 *
 * ★ **답변 삭제 경로는 없다**(FR-034 · AC-47). 지우면 상태가 미답변으로 되돌아가
 *   작성자의 수정·삭제 권한이 되살아난다. 그래서 화면에도 삭제 버튼을 두지 않는다.
 *
 * 부모가 `key` 로 다시 만들어 주므로 이 컴포넌트는 초기값 동기화를 하지 않는다.
 */
function AnswerForm({
  inquiryId,
  answer,
  onDone,
}: {
  inquiryId: number;
  answer: InquiryAnswer | null;
  onDone: () => void;
}) {
  const [content, setContent] = useState(answer?.content ?? "");
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!confirmAction(CONFIRM.answer)) return;
    setSubmitting(true);
    setError(null);
    try {
      await apiFetch(`/api/admin/inquiries/${inquiryId}/answer`, {
        method: answer === null ? "POST" : "PUT",
        body: JSON.stringify({ content }),
      });
      onDone();
    } catch (caught) {
      // 이미 답변이 있는데 POST 를 보내면 409 INQUIRY_ALREADY_ANSWERED 가 온다
      setError(caught);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <Alert error={error} />
      <div className="field">
        <label htmlFor="answer">{answer === null ? "답변 등록" : "답변 수정"}</label>
        <textarea
          id="answer"
          className={styles.textarea}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          required
        />
        <span className="hint">4000자 미만 · 검증은 서버가 합니다</span>
      </div>
      <div className={styles.buttonRow}>
        <button type="submit" disabled={submitting}>
          {submitting ? "처리 중…" : answer === null ? "답변완료" : "답변 수정"}
        </button>
      </div>
    </form>
  );
}

/**
 * 문의게시판 관리 상세 — 요구사항 6.3 · 6.5.
 *
 * ★ 관리자는 **비밀글도 비밀번호 없이** 본다(AC-31). 그래서 잠금 화면이 없다.
 * ★ 관리 상세는 조회수를 올리지 않는다.
 */
export default function InquiryDetailView({ inquiryId }: { inquiryId: number }) {
  const router = useRouter();
  const listQuery = useListQuery();
  const listHref = `/admin/inquiries${listQuery}`;

  const { data, loading, error, reload } = useBoardList<InquiryDetail>(
    `/api/admin/inquiries/${inquiryId}`,
  );

  const [actionError, setActionError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function handleDelete() {
    if (!confirmAction(CONFIRM.remove)) return;
    setBusy(true);
    setActionError(null);
    try {
      // 관리자 삭제는 답변 여부와 무관하다(미답변 제한은 작성자에게만 적용된다)
      await apiFetch(`/api/admin/inquiries/${inquiryId}`, { method: "DELETE" });
      router.push(listHref);
      router.refresh();
    } catch (caught) {
      setActionError(caught);
      setBusy(false);
    }
  }

  if (error !== null) return <Alert error={error} />;
  if (data === null) return <p className="hint">{loading ? "불러오는 중…" : "내용이 없습니다."}</p>;

  return (
    <>
      <div className={styles.pageHead}>
        <h1>{data.title}</h1>
        <StatusBadge tone={data.answered ? "ok" : "muted"}>
          {data.answered ? "답변완료" : "미답변"}
        </StatusBadge>
      </div>

      <Alert error={actionError} />

      <dl className="info">
        <dt>등록자</dt>
        <dd>{data.authorName ?? "-"}</dd>
        <dt>등록일시</dt>
        <dd>{formatDateTime(data.createdAt)}</dd>
        <dt>조회수</dt>
        <dd>{data.viewCount.toLocaleString()}</dd>
        <dt>공개 여부</dt>
        <dd>{data.secret ? "비밀글" : "공개"}</dd>
      </dl>

      <div className={styles.card}>
        <div className={styles.detailBody}>{data.content}</div>
      </div>

      <h2>관리자 답변</h2>
      {data.answer === null ? (
        <p className="hint">아직 등록된 답변이 없습니다.</p>
      ) : (
        <div className={styles.answerBox}>
          <p className={styles.commentMeta}>
            <strong>{data.answer.adminName ?? "-"}</strong>
            <span>{formatDateTime(data.answer.updatedAt)}</span>
          </p>
          {data.answer.content}
        </div>
      )}

      {/* answer 가 바뀌면(등록·수정 후) key 가 바뀌어 폼이 서버 값으로 다시 초기화된다 */}
      <AnswerForm
        key={data.answer === null ? "new" : `${data.answer.id}-${data.answer.updatedAt}`}
        inquiryId={inquiryId}
        answer={data.answer}
        onDone={reload}
      />

      <div className={styles.buttonRow}>
        <Link href={listHref}>
          <button type="button" className="secondary">
            목록
          </button>
        </Link>
        <button
          type="button"
          className={styles.dangerButton}
          onClick={handleDelete}
          disabled={busy}
        >
          삭제
        </button>
      </div>

      <p className="note">
        답변은 <strong>삭제할 수 없습니다</strong>. 지우면 상태가 미답변으로 되돌아가 작성자의
        수정·삭제 권한이 되살아나기 때문입니다(FR-034 · AC-47). 내용을 바꿀 때는 수정하세요.
      </p>
    </>
  );
}
