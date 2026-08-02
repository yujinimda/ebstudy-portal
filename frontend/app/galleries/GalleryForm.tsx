"use client";

import { useRouter } from "next/navigation";
import { useEffect, useId, useRef, useState, type ChangeEvent, type FormEvent } from "react";
import Alert from "@/components/Alert";
import type { CategoryOption } from "@/lib/board/types";
import styles from "./gallery.module.css";
import { idFromLocation, multipartFetch } from "./multipartFetch";
import type { GalleryImageResponse } from "./types";

/**
 * 갤러리 등록/수정 폼 — 요구사항 5.3.
 *
 * ★ **화면 검증을 만들지 않는다.** jpg/gif/png · 개당 1MB · 최대 20개 · 최소 1장은 전부
 *   서버가 판정하고(`GALLERY_IMAGE_REQUIRED` · `ATTACHMENT_TOO_LARGE` ·
 *   `ATTACHMENT_EXTENSION_NOT_ALLOWED` …) 그 `detail` 을 `<Alert>` 가 그대로 보여준다.
 *   화면에서도 막으면 규칙이 두 곳에 생기고 둘은 반드시 갈라진다(001 FR-002 와 같은 원칙).
 *   `accept` 와 hint 는 **안내**일 뿐 검증이 아니다.
 *
 * ★ **순서 = 배열 순서**이고 첫 장이 썸네일이다. 그래서 이미지를 배열로 들고
 *   `앞으로`/`뒤로` 로 옮긴다.
 *
 * ⚠️ 수정에서 서버는 `imageIds`(남길 기존 이미지, 보여줄 순서)를 먼저 놓고
 *   **새 이미지를 그 뒤에 이어 붙인다**(`GalleryController.update` 계약). 화면에서 새 이미지를
 *   기존 이미지 앞으로 끌어와도 저장 후에는 뒤로 간다 — 그래서 아래에 안내를 적어 두었다.
 *   임의 위치 삽입을 지원하려면 서버 계약이 먼저 바뀌어야 하므로 화면에서 흉내 내지 않는다.
 */

/** 폼이 들고 있는 이미지 한 장. 기존(서버에 있음)과 새로 고른 파일을 한 배열에 섞어 순서를 잡는다. */
type FormImage =
  | { kind: "existing"; key: string; id: number; name: string; url: string }
  | { kind: "new"; key: string; file: File; name: string; url: string };

export interface GalleryFormInitial {
  categoryId: number | null;
  title: string;
  content: string;
  images: GalleryImageResponse[];
}

export const EMPTY_INITIAL: GalleryFormInitial = {
  categoryId: null,
  title: "",
  content: "",
  images: [],
};

function toFormImages(images: GalleryImageResponse[]): FormImage[] {
  return images.map((image) => ({
    kind: "existing",
    key: `existing-${image.id}`,
    id: image.id,
    name: image.originalName,
    url: image.url,
  }));
}

export default function GalleryForm({
  mode,
  postId,
  initial,
  categories,
  listQuery,
}: {
  mode: "create" | "edit";
  /** 수정일 때만. 등록에서는 쓰지 않는다 */
  postId?: number;
  initial: GalleryFormInitial;
  categories: CategoryOption[];
  /** 취소·저장 후 돌아갈 목록의 검색조건(원문 쿼리스트링) */
  listQuery: string;
}) {
  const router = useRouter();
  const fieldId = useId();

  const [categoryId, setCategoryId] = useState<number | null>(initial.categoryId);
  const [title, setTitle] = useState(initial.title);
  const [content, setContent] = useState(initial.content);
  const [items, setItems] = useState<FormImage[]>(() => toFormImages(initial.images));
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  /** 만든 objectURL 을 모아 두었다가 화면을 떠날 때 반납한다(안 하면 메모리에 남는다). */
  const objectUrls = useRef<string[]>([]);
  useEffect(() => {
    const urls = objectUrls.current;
    // 언마운트 정리만 한다 — 본문에서 setState 를 하지 않는다(규약)
    return () => {
      urls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, []);

  function handlePick(event: ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(event.target.files ?? []);
    if (picked.length === 0) return;

    const added: FormImage[] = picked.map((file, i) => {
      // ★ 요구사항 5.3 "파일 선택 시 미리보기 즉시 표시" — 업로드 전에 보여줘야 하므로
      //   서버 URL 이 아니라 브라우저가 만든 objectURL 을 쓴다
      const url = URL.createObjectURL(file);
      objectUrls.current.push(url);
      return {
        kind: "new",
        key: `new-${Date.now()}-${i}-${file.name}`,
        file,
        name: file.name,
        url,
      };
    });

    setItems((prev) => [...prev, ...added]);
    // 같은 파일을 지웠다가 다시 고를 수 있게 입력값을 비운다(안 그러면 change 가 안 난다)
    event.target.value = "";
  }

  function removeAt(index: number) {
    setItems((prev) => {
      const target = prev[index];
      if (target.kind === "new") {
        URL.revokeObjectURL(target.url);
        objectUrls.current = objectUrls.current.filter((url) => url !== target.url);
      }
      return prev.filter((_, i) => i !== index);
    });
  }

  function moveBy(index: number, delta: number) {
    setItems((prev) => {
      const next = index + delta;
      if (next < 0 || next >= prev.length) return prev;
      const copy = [...prev];
      [copy[index], copy[next]] = [copy[next], copy[index]];
      return copy;
    });
  }

  function handleCancel() {
    // 요구사항 1.2 — 취소는 확인 후 목록으로(검색조건 유지)
    if (!window.confirm("작성을 취소하시겠습니까?")) return;
    router.push(`/galleries${listQuery}`);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);

    const body = new FormData();
    if (categoryId !== null) body.append("categoryId", String(categoryId));
    body.append("title", title);
    body.append("content", content);
    if (mode === "edit") {
      // 남길 기존 이미지를 **보여줄 순서대로**. 빈 문자열이면 기존 이미지를 전부 뺀다
      body.append(
        "imageIds",
        items
          .filter((item) => item.kind === "existing")
          .map((item) => item.id)
          .join(","),
      );
    }
    items.forEach((item) => {
      if (item.kind === "new") body.append("images", item.file, item.name);
    });

    try {
      if (mode === "create") {
        const location = await multipartFetch("/api/galleries", "POST", body);
        const created = idFromLocation(location);
        router.push(created === null ? `/galleries${listQuery}` : `/galleries/${created}${listQuery}`);
      } else {
        await multipartFetch(`/api/galleries/${postId}`, "PUT", body);
        router.push(`/galleries/${postId}${listQuery}`);
      }
    } catch (caught) {
      setError(caught);
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <Alert error={error} />

      <div className="field">
        <label htmlFor={`${fieldId}-category`}>분류</label>
        <select
          id={`${fieldId}-category`}
          className={styles.select}
          value={categoryId === null ? "" : String(categoryId)}
          onChange={(e) => setCategoryId(e.target.value === "" ? null : Number(e.target.value))}
          required
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
        <label htmlFor={`${fieldId}-title`}>제목</label>
        <input
          id={`${fieldId}-title`}
          name="title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          required
        />
        <span className="hint">100자 미만</span>
      </div>

      <div className="field">
        <label htmlFor={`${fieldId}-content`}>내용</label>
        <textarea
          id={`${fieldId}-content`}
          name="content"
          className={styles.textarea}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          required
        />
        <span className="hint">4000자 미만</span>
      </div>

      <div className="field">
        <label htmlFor={`${fieldId}-images`}>이미지</label>
        <input
          id={`${fieldId}-images`}
          type="file"
          accept="image/jpeg,image/png,image/gif"
          multiple
          onChange={handlePick}
        />
        <span className="hint">
          jpg · gif · png · 개당 1MB · 최대 20개. <strong>첫 번째 이미지가 썸네일</strong>이 됩니다
          (검증은 서버가 합니다).
          {mode === "edit" && " 새로 추가한 이미지는 저장 시 기존 이미지 뒤에 붙습니다."}
        </span>

        {items.length === 0 ? (
          <p className="hint">선택한 이미지가 없습니다.</p>
        ) : (
          <ul className={styles.imageList}>
            {items.map((item, index) => (
              <li
                key={item.key}
                className={`${styles.imageItem} ${index === 0 ? styles.imageItemFirst : ""}`}
              >
                <span className={styles.imagePreview}>
                  {/* eslint-disable-next-line @next/next/no-img-element -- 업로드 전 미리보기는
                      blob: objectURL 이라 next/image 로 최적화할 대상이 아니다 */}
                  <img src={item.url} alt={item.name} />
                </span>
                <span className={styles.imageName} title={item.name}>
                  {item.name}
                </span>
                {index === 0 && <span className={styles.thumbMark}>썸네일</span>}
                <span className={styles.itemButtons}>
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => moveBy(index, -1)}
                    disabled={index === 0}
                    aria-label={`${item.name} 앞으로`}
                  >
                    ←
                  </button>
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => moveBy(index, 1)}
                    disabled={index === items.length - 1}
                    aria-label={`${item.name} 뒤로`}
                  >
                    →
                  </button>
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => removeAt(index)}
                    aria-label={`${item.name} 삭제`}
                  >
                    ✕
                  </button>
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className={styles.formActions}>
        <button type="submit" disabled={submitting}>
          {submitting ? "저장 중…" : mode === "create" ? "등록" : "수정"}
        </button>
        <button type="button" className="secondary" onClick={handleCancel} disabled={submitting}>
          취소
        </button>
      </div>
    </form>
  );
}
