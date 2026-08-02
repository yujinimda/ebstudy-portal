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
import { formatDateTime } from "../../format";
import type { GalleryDetail } from "../../types";

/**
 * 갤러리 관리 상세 — 요구사항 5.2 · 5.4.
 *
 * 관리 화면은 **캐러셀 대신 격자**로 그린다. 운영자가 보려는 것은 "몇 번째 이미지"가 아니라
 * "이 글에 어떤 이미지가 몇 장 있나" 라서, 한 장씩 넘기는 UI 는 오히려 확인을 느리게 한다
 * (캐러셀은 사용자 화면 담당의 요구사항 5.2 다).
 *
 * ★ 관리 상세도 **조회수를 올리지 않는다**(`GET /api/admin/galleries/{id}`).
 */
export default function GalleryDetailView({ galleryId }: { galleryId: number }) {
  const router = useRouter();
  const listQuery = useListQuery();
  const listHref = `/admin/galleries${listQuery}`;

  const { data, loading, error } = useBoardList<GalleryDetail>(
    `/api/admin/galleries/${galleryId}`,
  );

  const [actionError, setActionError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function handleDelete() {
    if (!confirmAction(CONFIRM.remove)) return;
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch(`/api/admin/galleries/${galleryId}`, { method: "DELETE" });
      router.push(listHref);
      router.refresh();
    } catch (caught) {
      setActionError(caught);
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
        <dt>이미지</dt>
        <dd>{data.images.length}장</dd>
      </dl>

      <div className={styles.card}>
        <div className={styles.detailBody}>{data.content}</div>
      </div>

      <h2>이미지</h2>
      {data.images.length === 0 ? (
        <p className="hint">등록된 이미지가 없습니다.</p>
      ) : (
        <div className={styles.imageGrid}>
          {data.images.map((image) => (
            // 첫 번째 이미지가 목록의 썸네일로 쓰인다(요구사항 5.3)
            // eslint-disable-next-line @next/next/no-img-element
            <img key={image.id} src={image.url} alt={image.originalName} />
          ))}
        </div>
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
          onClick={handleDelete}
          disabled={busy}
        >
          삭제
        </button>
      </div>
    </>
  );
}
