"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import Alert from "@/components/Alert";
import { apiFetch } from "@/lib/api";
import { useBoardList } from "@/lib/board/useBoardList";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import { formatBytes, formatDateTime } from "../format";
import type { CreatedIdResponse, FreePostDetail } from "../types";
import styles from "../free.module.css";

/**
 * 요구사항 4.2 — 상세 · 댓글 · 첨부 다운로드.
 *
 * ★ **수정·삭제 버튼은 서버가 준 `editable`/`deletable` 로만 그린다.**
 *   이건 **편의일 뿐이고 실제 차단은 서버가 한다**(요구사항 1.3 · 001 AC-26) —
 *   버튼을 숨겨도 주소창으로 들어오면 `POST /api/free-posts/{id}` 가 403 을 준다.
 *   그래서 화면에서 소유자를 직접 계산하지 않는다(계산하면 규칙이 두 곳이 된다).
 *
 * ★ 첨부는 **평범한 링크**다. 서버가 `Content-Disposition: attachment` 로 내려 주므로
 *   이미지라도 인라인으로 열리지 않는다(요구사항 4.2). 화면에서 `<img>` 로 그리지 않는다.
 */
export default function FreeDetailView({ postId }: { postId: string }) {
  const router = useRouter();
  /** 목록으로 돌아갈 때 쓸 검색조건. **원문 그대로** 실어 나른다(요구사항 1.1) */
  const listQuery = useListQuery();

  const { data, loading, error, reload } = useBoardList<FreePostDetail>(
    `/api/free-posts/${postId}`,
  );

  const [comment, setComment] = useState("");
  /** 댓글·삭제처럼 **사용자가 일으킨** 실패. 조회 실패(`error`)와 자리를 나눈다 */
  const [actionError, setActionError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function handleAddComment(event: FormEvent) {
    event.preventDefault();
    setActionError(null);
    setBusy(true);
    try {
      await apiFetch<CreatedIdResponse>(`/api/free-posts/${postId}/comments`, {
        method: "POST",
        body: JSON.stringify({ content: comment }),
      });
      setComment("");
      reload();
    } catch (caught) {
      // 길이 제한 등은 서버가 판정한다 — 화면에서 미리 막지 않는다(요구사항 1.2)
      setActionError(caught);
    } finally {
      setBusy(false);
    }
  }

  async function handleDeleteComment(commentId: number) {
    if (!window.confirm("정말로 삭제 하시겠습니까")) return;
    setActionError(null);
    setBusy(true);
    try {
      await apiFetch<void>(`/api/free-posts/${postId}/comments/${commentId}`, {
        method: "DELETE",
      });
      reload();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setBusy(false);
    }
  }

  async function handleDeletePost() {
    if (!window.confirm("정말로 삭제 하시겠습니까")) return;
    setActionError(null);
    setBusy(true);
    try {
      await apiFetch<void>(`/api/free-posts/${postId}`, { method: "DELETE" });
      // 지운 글의 상세로 돌아가지 않도록 replace 가 아니라 목록으로 push 한다
      router.push(`/free${listQuery}`);
    } catch (caught) {
      setActionError(caught);
      setBusy(false);
    }
  }

  if (loading && data === null) {
    return (
      <main className={styles.wide}>
        <p className="hint">불러오는 중…</p>
      </main>
    );
  }

  if (data === null) {
    return (
      <main className={styles.wide}>
        <Alert error={error} />
        <p className="note">
          <Link href={`/free${listQuery}`}>목록으로</Link>
        </p>
      </main>
    );
  }

  return (
    <main className={styles.wide}>
      <Alert error={error} />
      <Alert error={actionError} />

      <h1 className={styles.detailTitle}>{data.title}</h1>
      <div className={styles.detailMeta}>
        <span>{data.categoryName ?? "분류 없음"}</span>
        <span>{data.authorName ?? "-"}</span>
        <span>{formatDateTime(data.createdAt)}</span>
        <span>조회 {data.viewCount.toLocaleString()}</span>
      </div>

      <div className={styles.content}>{data.content}</div>

      {data.attachments.length > 0 && (
        <section className={styles.section}>
          <h2 className={styles.sectionTitle}>첨부파일</h2>
          <ul className={styles.fileList}>
            {data.attachments.map((file) => (
              <li key={file.id} className={styles.fileRow}>
                {/* 서버가 attachment 헤더를 붙이므로 이미지도 다운로드된다(요구사항 4.2) */}
                <a className={styles.fileName} href={file.downloadUrl}>
                  {file.originalName}
                </a>
                <span className={styles.fileSize}>{formatBytes(file.sizeBytes)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className={styles.actions}>
        <Link
          className={`${styles.buttonLink} ${styles.buttonLinkSecondary}`}
          href={`/free${listQuery}`}
        >
          목록
        </Link>
        <span className={styles.spacer} />
        {/* ★ 편의일 뿐이다 — 실제 권한은 서버가 다시 확인한다(요구사항 1.3) */}
        {data.editable && (
          <Link
            className={`${styles.buttonLink} ${styles.buttonLinkSecondary}`}
            href={`/free/${data.id}/edit${listQuery}`}
          >
            수정
          </Link>
        )}
        {data.deletable && (
          <button type="button" onClick={handleDeletePost} disabled={busy}>
            삭제
          </button>
        )}
      </div>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>댓글 {data.comments.length}</h2>

        <ul className={styles.commentList}>
          {data.comments.map((item) => (
            <li key={item.id} className={styles.commentItem}>
              <div className={styles.commentHead}>
                <span className={styles.commentAuthor}>{item.authorName ?? "-"}</span>
                <span>{formatDateTime(item.createdAt)}</span>
                {/* 본인 댓글(과 관리자)에만 보인다. 판정은 서버 값 `deletable` 이다 */}
                {item.deletable && (
                  <button
                    type="button"
                    className={styles.smallButton}
                    onClick={() => handleDeleteComment(item.id)}
                    disabled={busy}
                  >
                    삭제
                  </button>
                )}
              </div>
              <div className={styles.commentBody}>{item.content}</div>
            </li>
          ))}
          {data.comments.length === 0 && (
            <li className={styles.commentItem}>
              <span className="hint">등록된 댓글이 없습니다.</span>
            </li>
          )}
        </ul>

        {/* 요구사항 4.2 — 입력은 로그인한 사용자에게만 보인다. 서버가 준 값으로 판단한다 */}
        {data.commentable ? (
          <form className={styles.commentForm} onSubmit={handleAddComment}>
            <div className="field">
              <label htmlFor="comment">댓글</label>
              <textarea
                id="comment"
                className={styles.textarea}
                rows={3}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                required
              />
            </div>
            <div>
              <button type="submit" disabled={busy}>
                {busy ? "등록 중…" : "댓글 등록"}
              </button>
            </div>
          </form>
        ) : (
          <p className="hint">
            댓글은 <Link href="/login">로그인</Link> 후 작성할 수 있습니다.
          </p>
        )}
      </section>
    </main>
  );
}
