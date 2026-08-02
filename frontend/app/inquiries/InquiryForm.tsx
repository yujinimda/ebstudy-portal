"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import Alert from "@/components/Alert";
import styles from "./inquiries.module.css";

/**
 * 요구사항 6.4 등록/수정 폼 — 제목 · 내용 · **비밀글 체크박스**.
 *
 * ★ **화면 검증을 하지 않는다**(요구사항 1.2 · 001 FR-002 와 같은 원칙).
 *   100자·4000자·숫자 4자리를 여기서 막으면 규칙이 두 곳에 생기고, 둘이 어긋나는 순간
 *   "화면은 통과했는데 서버가 거부" 가 된다. 서버가 방어선이고 아래 안내는 hint 일 뿐이다.
 *   (`required` 만 둔다 — 빈 요청을 왕복시키지 않는 정도의 편의다)
 *
 * ★ 등록과 수정이 **비밀번호를 다르게 다룬다**:
 *   - 등록: 비밀글이면 필수
 *   - 수정: **비워 두면 기존 비밀번호 유지**. 빈 값을 "삭제" 로 해석하면 AC-23 이 금지한
 *     *비밀번호 없는 비밀글* 이 만들어진다. 단 공개글 → 비밀글로 바꿀 때는 유지할 값이
 *     없어 필수다(서버가 `SECRET_PASSWORD_REQUIRED` 로 거부한다)
 */
export interface InquiryFormValues {
  title: string;
  content: string;
  secret: boolean;
  /** 빈 문자열이면 보내지 않는다 — 수정에서 "기존 유지" 의 뜻이 된다 */
  secretPassword: string;
}

export interface InquiryFormProps {
  mode: "create" | "edit";
  initial: InquiryFormValues;
  /** 취소·완료 후 돌아갈 목록 쿼리스트링(`"?a=b"` 또는 `""`) */
  listQuery: string;
  /** 저장 실패 시 서버 오류. 문구는 서버 detail 을 그대로 쓴다 */
  error: unknown;
  submitting: boolean;
  onSubmit: (values: InquiryFormValues) => void;
}

export default function InquiryForm({
  mode,
  initial,
  listQuery,
  error,
  submitting,
  onSubmit,
}: InquiryFormProps) {
  const router = useRouter();
  const [values, setValues] = useState<InquiryFormValues>(initial);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    onSubmit(values);
  }

  function handleCancel() {
    // 요구사항 1.2 — 확인 후 목록으로(검색조건 유지)
    if (!window.confirm("작성을 취소하시겠습니까?")) return;
    router.push(`/inquiries${listQuery}`);
  }

  return (
    <form onSubmit={handleSubmit}>
      <Alert error={error} />

      <div className="field">
        <label htmlFor="title">제목</label>
        <input
          id="title"
          name="title"
          value={values.title}
          onChange={(e) => setValues({ ...values, title: e.target.value })}
          required
        />
        <span className="hint">100자 미만</span>
      </div>

      <div className="field">
        <label htmlFor="content">내용</label>
        <textarea
          id="content"
          name="content"
          className={styles.textarea}
          value={values.content}
          onChange={(e) => setValues({ ...values, content: e.target.value })}
          required
        />
        <span className="hint">4000자 미만</span>
      </div>

      <div className={styles.secretBox}>
        <label className={styles.checkboxField}>
          <input
            type="checkbox"
            checked={values.secret}
            onChange={(e) => setValues({ ...values, secret: e.target.checked })}
          />
          비밀글로 등록
        </label>

        {/* 체크했을 때만 비밀번호 칸을 보여준다. 꺼져 있으면 서버가 값을 무시하고
            저장하지도 않는다 — 쓰이지 않는 잠금 값을 남기지 않기 위해서다 */}
        {values.secret && (
          <div className="field">
            <label htmlFor="secretPassword">잠금 비밀번호</label>
            <input
              id="secretPassword"
              name="secretPassword"
              className={styles.pinInput}
              type="password"
              inputMode="numeric"
              autoComplete="off"
              maxLength={4}
              value={values.secretPassword}
              onChange={(e) => setValues({ ...values, secretPassword: e.target.value })}
            />
            <span className="hint">
              숫자 4자리
              {mode === "edit" && " · 비워 두면 기존 비밀번호를 그대로 둡니다"}
            </span>
          </div>
        )}

        <span className="hint">
          비밀글은 작성자와 관리자, 그리고 비밀번호를 입력한 사람만 내용과 답변을 볼 수
          있습니다. 제목과 등록자는 목록에 그대로 보입니다.
        </span>
      </div>

      <div className={styles.actions}>
        <button type="button" className="secondary" onClick={handleCancel} disabled={submitting}>
          취소
        </button>
        <span className={styles.actionsSpacer} />
        <button type="submit" disabled={submitting}>
          {submitting ? "저장 중…" : mode === "create" ? "등록" : "수정"}
        </button>
      </div>
    </form>
  );
}
