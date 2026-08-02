"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import Alert from "@/components/Alert";
import { apiFetch } from "@/lib/api";
import { useBoardList } from "@/lib/board/useBoardList";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import styles from "../../admin.module.css";
import { CONFIRM, confirmAction } from "../../confirm";
import { formatBytes, formatDateTime } from "../../format";
import type { FreePostDetail } from "../../types";

/**
 * 자유게시판 관리 상세 — 요구사항 4.2 · 7.1.
 *
 * ★ 관리 상세는 **조회수를 올리지 않는다**(`GET /api/admin/free-posts/{id}`).
 *   운영자가 들여다본 것으로 지표가 부풀지 않게 서버가 갈라 두었다.
 *
 * ★ 첨부는 이미지라도 **다운로드**로 처리한다(요구사항 4.2). 그래서 `<img>` 로 그리지 않고
 *   링크만 준다 — 서버가 `Content-Disposition: attachment` 로 내려 준다.
 *
 * ★ 댓글 삭제는 **사용자 API 를 그대로 쓴다**(`DELETE /api/free-posts/{postId}/comments/{id}`).
 *   그 경로가 이미 "본인 또는 관리자" 를 통과시키므로(요구사항 1.3) 관리자 전용 경로를
 *   따로 만들지 않았다 — 같은 판정을 두 벌로 두면 반드시 갈라진다.
 */
export default function FreeDetailView({ postId }: { postId: number }) {
  const router = useRouter();
  const listQuery = useListQuery();
  const listHref = `/admin/free-posts${listQuery}`;

  const { data, loading, error, reload } = useBoardList<FreePostDetail>(
    `/api/admin/free-posts/${postId}`,
  );

  const [actionError, setActionError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function handleDeletePost() {
    if (!confirmAction(CONFIRM.remove)) return;
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch(`/api/admin/free-posts/${postId}`, { method: "DELETE" });
      router.push(listHref);
      router.refresh();
    } catch (caught) {
      setActionError(caught);
      setBusy(false);
    }
  }

  async function handleDeleteComment(commentId: number) {
    if (!confirmAction(CONFIRM.remove)) return;
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch(`/api/free-posts/${postId}/comments/${commentId}`, { method: "DELETE" });
      reload();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setBusy(false);
    }
  }

  if (error !== null) return <Alert error={error} />;
  if (data === null) return <p className="hint">{loading ? "불러오는 중…" : "내용이 없습니다."}</p>;

  return (
    <>
      <div className={styles.pageHead}>
        <h1>{data.title}</h1>
      </div>

      <Alert error={actionError} />

      <dl className="info">
        <dt>분류</dt>
        <dd>{data.categoryName ?? "-"}</dd>
        <dt>등록자</dt>
        <dd>{data.authorName ?? "-"}</dd>
        <dt>등록일시</dt>
        <dd>{formatDateTime(data.createdAt)}</dd>
        <dt>조회수</dt>
        <dd>{data.viewCount.toLocaleString()}</dd>
      </dl>

      <div className={styles.card}>
        <div className={styles.detailBody}>{data.content}</div>
      </div>

      <h2>첨부파일</h2>
      {data.attachments.length === 0 ? (
        <p className="hint">첨부파일이 없습니다.</p>
      ) : (
        <ul className={styles.fileList}>
          {data.attachments.map((file) => (
            <li key={file.id}>
              {/* 이미지도 인라인으로 열지 않는다 — 요구사항 4.2 */}
              <a href={file.downloadUrl} download>
                {file.originalName}
              </a>{" "}
              <span className="hint">{formatBytes(file.sizeBytes)}</span>
            </li>
          ))}
        </ul>
      )}

      <h2>댓글 {data.comments.length > 0 && `(${data.comments.length})`}</h2>
      {data.comments.length === 0 ? (
        <p className="hint">등록된 댓글이 없습니다.</p>
      ) : (
        <ul className={styles.commentList}>
          {data.comments.map((comment) => (
            <li key={comment.id} className={styles.comment}>
              <div className={styles.commentMeta}>
                <strong>{comment.authorName ?? "-"}</strong>
                <span>{formatDateTime(comment.createdAt)}</span>
                {/* 관리자에게는 모든 댓글에 삭제가 보인다(요구사항 1.3).
                    ★ 이 표시는 편의일 뿐이고 실제 권한 검증은 서버가 한다 */}
                <button
                  type="button"
                  className={`${styles.smallButton} ${styles.dangerButton}`}
                  onClick={() => handleDeleteComment(comment.id)}
                  disabled={busy}
                >
                  삭제
                </button>
              </div>
              {comment.content}
            </li>
          ))}
        </ul>
      )}

      <div className={styles.buttonRow}>
        <Link href={listHref}>
          <button type="button" className="secondary">
            목록
          </button>
        </Link>
        <button
          type="button"
          className={styles.dangerButton}
          onClick={handleDeletePost}
          disabled={busy}
        >
          삭제
        </button>
      </div>
    </>
  );
}
