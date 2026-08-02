"use client";

import Link from "next/link";
import { useState } from "react";
import Alert from "@/components/Alert";
import BoardListToolbar from "@/components/board/BoardListToolbar";
import BoardSearchBar from "@/components/board/BoardSearchBar";
import Pagination from "@/components/board/Pagination";
import { NewBadge } from "@/components/board/ListMarks";
import { apiFetch } from "@/lib/api";
import { boardListUrl } from "@/lib/board/criteria";
import type { PageResponse } from "@/lib/board/types";
import { useBoardList } from "@/lib/board/useBoardList";
import { useBoardSearchParams } from "@/lib/board/useBoardSearchParams";
import { useCategories } from "@/lib/board/useCategories";
import styles from "../admin.module.css";
import { CONFIRM, confirmAction } from "../confirm";
import { formatDateTime } from "../format";
import type { GalleryAdminRow } from "../types";

/**
 * 갤러리 관리 목록 — 요구사항 5.4
 * (번호 · 분류 · 제목(**썸네일 + 파일 개수 `+8`**) · 조회 · 등록일시 · 등록자).
 *
 * ★ `extraImageCount` 는 **서버가 계산해서 준다**(전체 개수 − 썸네일 1장). 화면에서
 *   `imageCount - 1` 을 하지 않는 이유: 이미지가 0장인 글에서 `-1` 이 나온다.
 *
 * ★ 등록·수정이 없는 것은 의도다 — 요구사항 5.4 가 관리 화면에 **목록만** 정의했고
 *   갤러리 글의 주인은 사용자다(`GalleryAdminController` 주석).
 */
export default function GalleryListView() {
  const { criteria, applySearch, applyOptions, goToPage, listQuery } = useBoardSearchParams();
  const { categories } = useCategories("GALLERY", {
    path: "/api/admin/categories?boardType=GALLERY",
  });

  // 갤러리는 기간을 String 으로 받아 직접 파싱한다 → 기본 옵션("date")이 맞다
  const url = boardListUrl("/api/admin/galleries", criteria);
  const { data, loading, error, reload } = useBoardList<PageResponse<GalleryAdminRow>>(url);

  const [actionError, setActionError] = useState<unknown>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  async function handleDelete(id: number) {
    if (!confirmAction(CONFIRM.remove)) return;
    setDeletingId(id);
    setActionError(null);
    try {
      await apiFetch(`/api/admin/galleries/${id}`, { method: "DELETE" });
      reload();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setDeletingId(null);
    }
  }

  const items = data?.items ?? [];

  return (
    <>
      <div className={styles.pageHead}>
        <h1>갤러리 게시판 관리</h1>
      </div>

      <BoardSearchBar
        criteria={criteria}
        onSearch={applySearch}
        categories={categories}
        disabled={loading}
      />

      <BoardListToolbar
        criteria={criteria}
        onChange={applyOptions}
        totalElements={data?.totalElements}
        disabled={loading}
      />

      <Alert error={error} />
      <Alert error={actionError} />

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col" className={styles.num}>
                번호
              </th>
              <th scope="col">분류</th>
              <th scope="col">제목</th>
              <th scope="col" className={styles.numeric}>
                조회
              </th>
              <th scope="col" className={styles.when}>
                등록일시
              </th>
              <th scope="col" className={styles.who}>
                등록자
              </th>
              <th scope="col">관리</th>
            </tr>
          </thead>
          <tbody>
            {items.length > 0 ? (
              items.map((item) => (
                <tr key={item.id}>
                  <td className={styles.num}>{item.number}</td>
                  <td>{item.categoryName ?? "-"}</td>
                  <td className={styles.titleCell}>
                    {item.thumbnailUrl !== null && (
                      // 갤러리 썸네일은 이미지 자체가 정보다(자유게시판 첨부와 다르다).
                      // next/image 를 쓰지 않는 이유: 이미지가 인증이 필요한 API 경로에서 오고
                      // 크기를 미리 알 수 없다
                      // eslint-disable-next-line @next/next/no-img-element
                      <img className={styles.thumb} src={item.thumbnailUrl} alt="" />
                    )}{" "}
                    <Link href={`/admin/galleries/${item.id}${listQuery}`}>{item.title}</Link>
                    <span className={styles.marks}>
                      {item.extraImageCount > 0 && (
                        <span className="hint">+{item.extraImageCount}</span>
                      )}
                      <NewBadge show={item.isNew} />
                    </span>
                  </td>
                  <td className={styles.numeric}>{item.viewCount.toLocaleString()}</td>
                  <td className={styles.when}>{formatDateTime(item.createdAt)}</td>
                  <td className={styles.who}>{item.authorName ?? "-"}</td>
                  <td>
                    <div className={styles.actions}>
                      <Link href={`/admin/galleries/${item.id}${listQuery}`}>
                        <button type="button" className={`secondary ${styles.smallButton}`}>
                          상세
                        </button>
                      </Link>
                      <button
                        type="button"
                        className={`${styles.smallButton} ${styles.dangerButton}`}
                        onClick={() => handleDelete(item.id)}
                        disabled={deletingId === item.id}
                      >
                        삭제
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td className={styles.empty} colSpan={7}>
                  {loading ? "불러오는 중…" : "등록된 글이 없습니다."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <Pagination page={criteria.page} totalPages={data?.totalPages ?? 0} onChange={goToPage} />
    </>
  );
}
