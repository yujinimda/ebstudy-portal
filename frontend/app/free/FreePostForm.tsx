"use client";

import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import Alert from "@/components/Alert";
import RequireAuth from "@/components/RequireAuth";
import { useBoardList } from "@/lib/board/useBoardList";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import { useCategories } from "@/lib/board/useCategories";
import { formatBytes } from "./format";
import { postMultipart } from "./multipart";
import {
  ATTACHMENT_ACCEPT,
  ATTACHMENT_MAX_COUNT,
  type CreatedIdResponse,
  type FreePostDetail,
} from "./types";
import styles from "./free.module.css";

/**
 * 등록 · 수정 — 요구사항 4.3. 한 컴포넌트가 둘을 다 한다(`postId` 유무로 갈린다).
 *
 * 나누지 않은 이유: 필드·첨부 규칙·취소 동작이 같아서 나누면 같은 코드가 두 벌이 되고,
 * 실제로 다른 것은 **보내는 경로**와 **기존 첨부 삭제** 둘뿐이다.
 *
 * ★ **화면에서 검증하지 않는다**(요구사항 1.2 · 001 FR-002 와 같은 원칙).
 *   제목 100자·내용 4000자·확장자·개당 2MB·최대 5개는 전부 **서버가** 판정하고,
 *   실패 문구는 서버 `detail` 을 `<Alert>` 가 그대로 보여준다. 아래 안내는 hint 일 뿐이다.
 *   (`accept` 속성도 파일 선택창을 편하게 할 뿐 검증이 아니다)
 *
 * ★ 미로그인 진입은 `RequireAuth` 가 `?next=` 를 붙여 로그인 화면으로 보낸다.
 *   `next` 에 **search 까지** 들어가므로 로그인 후 검색조건까지 살아서 돌아온다(요구사항 1.3).
 */
export default function FreePostForm({ postId }: { postId?: string }) {
  return <RequireAuth>{() => <FreePostFormBody postId={postId} />}</RequireAuth>;
}

function FreePostFormBody({ postId }: { postId?: string }) {
  const router = useRouter();
  const listQuery = useListQuery();
  const { categories } = useCategories("FREE");
  const isEdit = postId !== undefined;

  // ★ 수정 화면은 상세 API 로 값을 읽어 온다. 그래서 **수정 화면에 들어가도 조회수가 오른다** —
  //   기획에 "수정 진입은 세지 않는다"는 규칙이 없고(요구사항 1.4 는 단순 증가),
  //   그 목적만을 위한 별도 엔드포인트를 만들지 않았다. 사람 판단이 필요하면 그때 바꾼다.
  const { data: detail, error: loadError } = useBoardList<FreePostDetail>(
    isEdit ? `/api/free-posts/${postId}` : null,
  );

  const [categoryId, setCategoryId] = useState("");
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  /** 새로 올릴 파일. 제출 전까지는 브라우저에만 있다 */
  const [files, setFiles] = useState<File[]>([]);
  /** 요구사항 4.3 "기존 파일 개별 삭제" — 지금 지우지 않고 제출할 때 함께 보낸다 */
  const [removeIds, setRemoveIds] = useState<number[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  // ★ 불러온 값을 폼에 한 번만 채운다. **effect 가 아니라 렌더 중 조정**이다(React 공식 패턴).
  //   effect 로 하면 한 프레임 동안 빈 폼이 보이고, "effect 본문 동기 setState 금지" 규약에도 걸린다.
  const [filledId, setFilledId] = useState<number | null>(null);
  if (detail !== null && filledId !== detail.id) {
    setFilledId(detail.id);
    setCategoryId(detail.categoryId === null ? "" : String(detail.categoryId));
    setTitle(detail.title);
    setContent(detail.content);
  }

  function addFiles(picked: FileList | null) {
    if (picked === null) return;
    setFiles((prev) => [...prev, ...Array.from(picked)]);
    // 같은 파일을 다시 고를 수 있게 입력값을 비운다(안 비우면 change 가 안 난다)
    if (fileInput.current !== null) fileInput.current.value = "";
  }

  function toggleRemove(attachmentId: number) {
    setRemoveIds((prev) =>
      prev.includes(attachmentId)
        ? prev.filter((id) => id !== attachmentId)
        : [...prev, attachmentId],
    );
  }

  function handleCancel() {
    // 요구사항 1.2 — 확인 후 목록으로. 검색조건은 listQuery 로 살려서 돌아간다
    if (!window.confirm("작성을 취소하시겠습니까?")) return;
    router.push(`/free${listQuery}`);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    const form = new FormData();
    // 빈 값도 그대로 보낸다 — "필수 누락"을 판정하는 자리는 서버 한 곳이다
    form.set("categoryId", categoryId);
    form.set("title", title);
    form.set("content", content);
    files.forEach((file) => form.append("files", file));
    removeIds.forEach((id) => form.append("removeAttachmentIds", String(id)));

    try {
      if (isEdit) {
        // ★ 수정도 POST 다(PUT 아님). 갤러리는 PUT 이라 게시판마다 다르다
        await postMultipart<void>(`/api/free-posts/${postId}`, form);
        router.push(`/free/${postId}${listQuery}`);
      } else {
        const created = await postMultipart<CreatedIdResponse>("/api/free-posts", form);
        router.push(`/free/${created.id}${listQuery}`);
      }
    } catch (caught) {
      setError(caught);
      setSubmitting(false);
    }
  }

  const attachments = detail?.attachments ?? [];

  return (
    <main>
      <h1>{isEdit ? "글 수정" : "글 등록"}</h1>
      <p className="subtitle">모든 검증은 서버가 합니다. 아래 안내는 참고용입니다.</p>

      <form onSubmit={handleSubmit}>
        <Alert error={loadError} />
        <Alert error={error} />

        <div className="field">
          <label htmlFor="categoryId">분류</label>
          <select
            id="categoryId"
            className={styles.select}
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
          >
            <option value="">분류를 선택하세요</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </div>

        <div className="field">
          <label htmlFor="title">제목</label>
          <input id="title" value={title} onChange={(e) => setTitle(e.target.value)} />
          <span className="hint">100자 미만</span>
        </div>

        <div className="field">
          <label htmlFor="content">내용</label>
          <textarea
            id="content"
            className={styles.textarea}
            rows={14}
            value={content}
            onChange={(e) => setContent(e.target.value)}
          />
          <span className="hint">4000자 미만</span>
        </div>

        {isEdit && attachments.length > 0 && (
          <div className="field">
            <span className={styles.sectionTitle}>기존 첨부파일</span>
            <ul className={styles.fileList}>
              {attachments.map((file) => {
                const removed = removeIds.includes(file.id);
                return (
                  <li
                    key={file.id}
                    className={removed ? `${styles.fileRow} ${styles.removed}` : styles.fileRow}
                  >
                    {/* 수정 화면에서도 기존 파일을 받아볼 수 있어야 한다(요구사항 4.3) */}
                    <a className={styles.fileName} href={file.downloadUrl}>
                      {file.originalName}
                    </a>
                    <span className={styles.fileSize}>{formatBytes(file.sizeBytes)}</span>
                    <button
                      type="button"
                      className={styles.smallButton}
                      onClick={() => toggleRemove(file.id)}
                    >
                      {removed ? "되돌리기" : "삭제"}
                    </button>
                  </li>
                );
              })}
            </ul>
            <span className="hint">삭제 표시한 파일은 저장할 때 실제로 지워집니다.</span>
          </div>
        )}

        <div className="field">
          <label htmlFor="files">첨부파일</label>
          <input
            id="files"
            ref={fileInput}
            type="file"
            multiple
            accept={ATTACHMENT_ACCEPT}
            onChange={(e) => addFiles(e.target.files)}
          />
          <span className="hint">
            jpg · gif · png · zip · 개당 2MB 까지 · 최대 {ATTACHMENT_MAX_COUNT}개
          </span>
          {files.length > 0 && (
            <ul className={styles.fileList}>
              {files.map((file, index) => (
                <li key={`${file.name}-${index}`} className={styles.fileRow}>
                  <span className={styles.fileName}>{file.name}</span>
                  <span className={styles.fileSize}>{formatBytes(file.size)}</span>
                  <button
                    type="button"
                    className={styles.smallButton}
                    onClick={() => setFiles((prev) => prev.filter((_, i) => i !== index))}
                  >
                    빼기
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className={styles.actions}>
          <button type="submit" disabled={submitting}>
            {submitting ? "저장 중…" : isEdit ? "수정" : "등록"}
          </button>
          <button type="button" className="secondary" onClick={handleCancel}>
            취소
          </button>
        </div>
      </form>
    </main>
  );
}
