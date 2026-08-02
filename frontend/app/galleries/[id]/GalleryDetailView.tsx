"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import Alert from "@/components/Alert";
import { NewBadge } from "@/components/board/ListMarks";
import { apiFetch } from "@/lib/api";
import { useBoardList } from "@/lib/board/useBoardList";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import GalleryCarousel from "../GalleryCarousel";
import { formatDateTime } from "../format";
import styles from "../gallery.module.css";
import type { GalleryDetailResponse } from "../types";

/**
 * 갤러리 상세 — 요구사항 5.2 (분류 · 제목 · 등록일시 · 등록자 · 조회수 · 내용 + 캐러셀).
 *
 * ★ 조회수는 이 GET 이 올린 뒤의 값이 응답에 담겨 온다(요구사항 1.4). 화면이 +1 하지 않는다.
 * ★ `목록` 은 `useListQuery()` 원문을 그대로 붙인다 — 파싱해서 다시 만들지 않아야
 *   조건이 한 글자도 안 틀리고 복원된다(요구사항 1.1 "검색조건 유지").
 */
export default function GalleryDetailView({ id }: { id: string }) {
  const router = useRouter();
  const listQuery = useListQuery();
  const { data, loading, error } = useBoardList<GalleryDetailResponse>(`/api/galleries/${id}`);

  const [deleteError, setDeleteError] = useState<unknown>(null);
  const [deleting, setDeleting] = useState(false);

  async function handleDelete() {
    // 요구사항 1.2 — 확인 후 삭제
    if (!window.confirm("정말로 삭제 하시겠습니까")) return;
    setDeleteError(null);
    setDeleting(true);
    try {
      await apiFetch<void>(`/api/galleries/${id}`, { method: "DELETE" });
      router.push(`/galleries${listQuery}`);
    } catch (caught) {
      setDeleteError(caught);
      setDeleting(false);
    }
  }

  return (
    <main className={styles.page}>
      <Alert error={error} />
      <Alert error={deleteError} />

      {data === null && loading && <p className="hint">불러오는 중…</p>}

      {data !== null && (
        <>
          <p className="subtitle" style={{ margin: 0 }}>
            갤러리 · {data.categoryName ?? "미분류"}
          </p>
          <h1 className={styles.detailTitle}>
            {data.title}
            <NewBadge show={data.isNew} />
          </h1>
          <div className={styles.detailMeta}>
            <span>등록자 {data.authorName}</span>
            <span>등록일시 {formatDateTime(data.createdAt)}</span>
            <span>조회수 {data.viewCount}</span>
            <span>이미지 {data.images.length}장</span>
          </div>

          <GalleryCarousel images={data.images} />

          <div className={styles.content}>{data.content}</div>

          <div className={styles.actions}>
            <Link href={`/galleries${listQuery}`}>
              <button type="button" className="secondary">
                목록
              </button>
            </Link>
            <span className={styles.spacer} />
            {/* ⚠️ `owned` 로 버튼을 감추는 것은 **편의일 뿐 권한 검증이 아니다**
                (요구사항 1.3 · 001 AC-26). 주소창으로 수정 화면에 직접 들어가도
                PUT/DELETE 는 서버가 소유자를 다시 확인해 403 을 준다 */}
            {data.owned && (
              <>
                <Link href={`/galleries/${id}/edit${listQuery}`}>
                  <button type="button" className="secondary">
                    수정
                  </button>
                </Link>
                <button type="button" onClick={handleDelete} disabled={deleting}>
                  {deleting ? "삭제 중…" : "삭제"}
                </button>
              </>
            )}
          </div>
        </>
      )}
    </main>
  );
}
