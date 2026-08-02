"use client";

import Alert from "@/components/Alert";
import RequireAuth from "@/components/RequireAuth";
import { useBoardList } from "@/lib/board/useBoardList";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import { useCategories } from "@/lib/board/useCategories";
import GalleryForm from "../../GalleryForm";
import styles from "../../gallery.module.css";
import type { GalleryDetailResponse } from "../../types";

/**
 * 갤러리 수정 화면 — 요구사항 5.3.
 *
 * ★ 폼은 **원본을 다 받은 뒤에** 마운트한다. 그래야 `useState` 초기값 한 번으로 끝나고
 *   "빈 폼이 잠깐 보였다가 값이 채워지는" 깜빡임도, 응답 도착 시 effect 로 setState 하는
 *   금지 패턴도 필요 없다(폼이 다시 마운트되도록 `key` 로 못 박는다).
 *
 * ⚠️ 남의 글 수정 화면에 주소창으로 들어올 수는 있다. 그건 **막지 않는다** —
 *   PUT 을 서버가 403 으로 거부하고 그 `detail` 이 폼 위에 그대로 뜬다(요구사항 1.3 · AC-26).
 *   화면에서 막는 것은 권한 검증이 아니다.
 */
export default function GalleryEditView({ id }: { id: string }) {
  const listQuery = useListQuery();
  const { categories } = useCategories("GALLERY");
  const { data, loading, error } = useBoardList<GalleryDetailResponse>(`/api/galleries/${id}`);

  return (
    <main className={styles.page}>
      <h1>갤러리 글 수정</h1>
      <p className="subtitle">모든 검증은 서버가 합니다. 아래 안내는 참고용입니다.</p>

      <Alert error={error} />
      {data === null && loading && <p className="hint">불러오는 중…</p>}

      {data !== null && (
        <RequireAuth>
          {() => (
            <GalleryForm
              key={data.id}
              mode="edit"
              postId={data.id}
              initial={{
                categoryId: data.categoryId,
                title: data.title,
                content: data.content,
                images: data.images,
              }}
              categories={categories}
              listQuery={listQuery}
            />
          )}
        </RequireAuth>
      )}
    </main>
  );
}
