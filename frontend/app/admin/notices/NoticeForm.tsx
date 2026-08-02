"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import Alert from "@/components/Alert";
import { apiFetch } from "@/lib/api";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import { useCategories } from "@/lib/board/useCategories";
import styles from "../admin.module.css";
import { CONFIRM, confirmAction } from "../confirm";

/**
 * 공지사항 등록 · 수정 폼 — 요구사항 3.3 (분류 · 제목 · 내용 · **상단 고정 체크박스**).
 *
 * ★ 등록과 수정이 한 컴포넌트인 이유는 서버와 같다 — 보내는 값이 완전히 같다
 *   (`NoticeWriteRequest`). 둘로 나누면 한쪽에만 필드가 추가되는 사고가 난다.
 *   메서드만 갈라진다: 등록 `POST /api/admin/notices`, 수정 `PUT /api/admin/notices/{id}`.
 *
 * ★ **길이 검증을 화면에서 하지 않는다**(요구사항 1.2 · 001 FR-002). 100자·4000자 제한은
 *   서버가 판정하고 그 `detail` 을 `<Alert>` 가 그대로 보여준다. 아래 안내는 hint 일 뿐이다.
 *
 * ★ 분류는 **관리자 경로**로 받는다 — 비활성 분류로 등록된 과거 글을 수정할 때
 *   선택지가 사라지면 안 된다(요구사항 7.2).
 */
export interface NoticeFormValues {
  categoryId: number | null;
  title: string;
  content: string;
  pinned: boolean;
}

export default function NoticeForm({
  noticeId,
  initial,
}: {
  /** 없으면 등록, 있으면 수정 */
  noticeId?: number;
  initial: NoticeFormValues;
}) {
  const router = useRouter();
  // 목록에서 실려 온 검색조건. 취소·완료 후 그대로 붙여 되돌아간다(요구사항 1.1)
  const listQuery = useListQuery();
  const listHref = `/admin/notices${listQuery}`;

  const { categories } = useCategories("NOTICE", {
    path: "/api/admin/categories?boardType=NOTICE",
  });

  const [categoryId, setCategoryId] = useState<number | null>(initial.categoryId);
  const [title, setTitle] = useState(initial.title);
  const [content, setContent] = useState(initial.content);
  const [pinned, setPinned] = useState(initial.pinned);
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  const editing = noticeId !== undefined;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!confirmAction(editing ? CONFIRM.update : CONFIRM.create)) return;
    setSubmitting(true);
    setError(null);
    try {
      await apiFetch(editing ? `/api/admin/notices/${noticeId}` : "/api/admin/notices", {
        method: editing ? "PUT" : "POST",
        body: JSON.stringify({ categoryId, title, content, pinned }),
      });
      router.push(listHref);
      // 목록은 서버에서 다시 받아야 방금 쓴 글이 보인다
      router.refresh();
    } catch (caught) {
      setError(caught);
      setSubmitting(false);
    }
  }

  function handleCancel() {
    if (!confirmAction(CONFIRM.cancel)) return;
    router.push(listHref);
  }

  return (
    <>
      <h1>{editing ? "공지사항 수정" : "공지사항 등록"}</h1>
      <p className="subtitle">모든 검증은 서버가 합니다. 아래 안내는 참고용입니다.</p>

      <form onSubmit={handleSubmit}>
        <Alert error={error} />

        <div className="field">
          <label htmlFor="categoryId">분류</label>
          <select
            id="categoryId"
            className={styles.select}
            value={categoryId === null ? "" : String(categoryId)}
            onChange={(e) => setCategoryId(e.target.value === "" ? null : Number(e.target.value))}
          >
            <option value="">선택하세요</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label htmlFor="title">제목</label>
          <input id="title" value={title} onChange={(e) => setTitle(e.target.value)} required />
          <span className="hint">100자 미만</span>
        </div>

        <div className="field">
          <label htmlFor="content">내용</label>
          <textarea
            id="content"
            className={styles.textarea}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            required
          />
          <span className="hint">4000자 미만</span>
        </div>

        <div className="field">
          <label className={styles.checkboxRow} htmlFor="pinned">
            <input
              id="pinned"
              type="checkbox"
              checked={pinned}
              onChange={(e) => setPinned(e.target.checked)}
            />
            상단 고정(알림글)
          </label>
          <span className="hint">
            사용자 화면에서는 모든 페이지 최상단에 노출됩니다. 5개를 넘으면 최신 5개만 보입니다.
          </span>
        </div>

        <div className={styles.buttonRow}>
          <button type="submit" disabled={submitting}>
            {submitting ? "처리 중…" : editing ? "수정" : "등록"}
          </button>
          <button type="button" className="secondary" onClick={handleCancel} disabled={submitting}>
            취소
          </button>
        </div>
      </form>
    </>
  );
}
