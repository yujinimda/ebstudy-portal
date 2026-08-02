"use client";

import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import Alert from "@/components/Alert";
import { LockIcon, StatusBadge } from "@/components/board/ListMarks";
import { ApiError, apiFetch } from "@/lib/api";
import styles from "../inquiries.module.css";
import { saveGrant } from "../grant";
import { formatDateTime, type InquiryDetail, type InquiryUnlockResult } from "../types";

/**
 * 요구사항 6.2 — **비밀글 비밀번호 입력 화면**.
 *
 * 상세가 403(`SECRET_POST_LOCKED`)을 주면 이 화면이 대신 그려진다.
 * **본인이 작성한 글이면 여기까지 오지 않는다** — 서버가 작성자·관리자에게는 비밀번호를
 * 묻지 않고 바로 상세를 준다(FR-020 · AC-31).
 *
 * ★ 라우트를 따로 파지 않고 같은 주소에서 화면만 바꾼다. 이유는 **조회수**다 —
 *   잠금해제(`POST /unlock`)가 성공하면 서버가 조회수를 올리고 **열린 상세를 함께** 준다.
 *   다른 라우트로 갔다가 상세를 다시 부르면 같은 열람이 두 번 세어진다.
 *   그래서 응답의 `inquiry` 를 그대로 부모에게 올려 재조회 없이 화면을 바꾼다.
 *
 * ★ 화면에 보여줄 제목·등록자는 `GET /{id}/preview` 로 따로 받는다. 거기 담기는 것은
 *   **목록에 이미 공개된 항목뿐**이고(본문·답변은 `null`) 조회수도 올리지 않는다.
 */
export default function SecretPasswordForm({
  id,
  listQuery,
  onUnlocked,
}: {
  id: string;
  listQuery: string;
  /** 잠금해제 응답에 들어 있는 **열린 상세**를 그대로 올린다(재조회 금지 — 조회수 이중 계산) */
  onUnlocked: (detail: InquiryDetail) => void;
}) {
  const [preview, setPreview] = useState<InquiryDetail | null>(null);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    // 잠금 안내는 실패해도 치명적이지 않다 — 비밀번호 입력 자체는 할 수 있어야 하므로
    // 오류를 Alert 로 올리지 않고 조용히 비워 둔다(입력 실패 오류와 섞이면 헷갈린다).
    // ★ effect 본문에서 동기 setState 를 하지 않는다. 응답 콜백에서만 세팅한다
    let cancelled = false;
    apiFetch<InquiryDetail>(`/api/inquiries/${id}/preview`)
      .then((found) => {
        if (!cancelled) setPreview(found);
      })
      .catch(() => {
        if (!cancelled) setPreview(null);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await apiFetch<InquiryUnlockResult>(`/api/inquiries/${id}/unlock`, {
        // ★ 본문(POST)으로 보낸다. 질의 문자열이면 비밀번호가 접근 로그·리퍼러에 남는다
        method: "POST",
        body: JSON.stringify({ password }),
      });
      // 통과를 보관해 둔다 — 새로고침해도 유효 시간 안에는 다시 묻지 않는다(AC-37)
      saveGrant(id, result.grantToken);
      onUnlocked(result.inquiry);
    } catch (caught) {
      setError(caught);
      setSubmitting(false);
    }
  }

  // 판단 9 — 서버가 남은 시도 횟수를 errors[] 로 함께 준다(코드로는 표현할 수 없는 값이다).
  // 문구는 서버 detail 이 이미 Alert 에 나오고, 여기서는 그 **숫자만** 덧붙인다
  const remaining =
    error instanceof ApiError
      ? (error.errors.find((e) => e.field === "remainingAttempts")?.reason ?? null)
      : null;

  return (
    <main>
      <h1>비밀글</h1>
      <p className="subtitle">이 문의는 비밀글입니다. 잠금 비밀번호 4자리를 입력해 주세요.</p>

      <div className={styles.lockCard}>
        <LockIcon show />
        {preview !== null && (
          <p className={styles.lockTarget}>
            <strong>{preview.title}</strong>
            <StatusBadge tone={preview.answered ? "ok" : "muted"}>
              {preview.answered ? "답변완료" : "미답변"}
            </StatusBadge>
            <br />
            {preview.authorName} · {formatDateTime(preview.createdAt)}
          </p>
        )}

        <form className={styles.lockForm} onSubmit={handleSubmit}>
          <Alert error={error} />
          {remaining !== null && (
            <p className="hint" role="status">
              남은 시도 {remaining}회
            </p>
          )}

          <div className="field">
            <label htmlFor="secret-password">잠금 비밀번호</label>
            <input
              id="secret-password"
              name="secret-password"
              className={styles.pinInput}
              // 모바일에서 숫자 키패드가 뜨게 한다. 형식 검증은 서버가 한다
              inputMode="numeric"
              autoComplete="off"
              type="password"
              maxLength={4}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoFocus
            />
            <span className="hint">숫자 4자리</span>
          </div>

          <div className={styles.actions}>
            <Link
              className={`${styles.linkButton} ${styles.linkButtonSecondary}`}
              href={`/inquiries${listQuery}`}
            >
              목록
            </Link>
            <span className={styles.actionsSpacer} />
            <button type="submit" disabled={submitting}>
              {submitting ? "확인 중…" : "확인"}
            </button>
          </div>
        </form>
      </div>
    </main>
  );
}
