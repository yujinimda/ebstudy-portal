"use client";

import RequireAuth from "@/components/RequireAuth";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import { useCategories } from "@/lib/board/useCategories";
import GalleryForm, { EMPTY_INITIAL } from "../GalleryForm";
import styles from "../gallery.module.css";

/**
 * 갤러리 등록 화면 — 요구사항 5.3.
 *
 * ★ `RequireAuth` 가 미로그인 진입을 로그인 화면으로 보내며 **`?next=` 에 지금 경로 + 검색조건**을
 *   실어 준다. 그래서 로그인 후 목록이 아니라 **바로 이 등록 화면**으로, 그것도 직전 검색조건을
 *   그대로 달고 돌아온다(요구사항 1.3). 화면에서 따로 처리할 것이 없다.
 */
export default function GalleryCreateView() {
  const listQuery = useListQuery();
  const { categories } = useCategories("GALLERY");

  return (
    <main className={styles.page}>
      <h1>갤러리 글 등록</h1>
      <p className="subtitle">모든 검증은 서버가 합니다. 아래 안내는 참고용입니다.</p>

      <RequireAuth>
        {() => (
          <GalleryForm
            mode="create"
            initial={EMPTY_INITIAL}
            categories={categories}
            listQuery={listQuery}
          />
        )}
      </RequireAuth>
    </main>
  );
}
