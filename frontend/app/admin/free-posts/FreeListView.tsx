"use client";

import Link from "next/link";
import { useState } from "react";
import Alert from "@/components/Alert";
import BoardListToolbar from "@/components/board/BoardListToolbar";
import BoardSearchBar from "@/components/board/BoardSearchBar";
import Pagination from "@/components/board/Pagination";
import { AttachmentIcon, CommentCount, NewBadge } from "@/components/board/ListMarks";
import { apiFetch } from "@/lib/api";
import { boardListUrl } from "@/lib/board/criteria";
import type { PageResponse } from "@/lib/board/types";
import { useBoardList } from "@/lib/board/useBoardList";
import { useBoardSearchParams } from "@/lib/board/useBoardSearchParams";
import { useCategories } from "@/lib/board/useCategories";
import styles from "../admin.module.css";
import { CONFIRM, confirmAction } from "../confirm";
import { formatDateTime } from "../format";
import type { FreePostListItem } from "../types";

/**
 * 자유게시판 관리 목록 — 요구사항 4.1 · 7.1.
 *
 * ★ **등록·수정 버튼이 없는 것은 의도다.** 요구사항 0장이 자유게시판의 등록 주체를
 *   "사용자" 로 못박았고, 서버에도 관리자 등록·수정 엔드포인트가 없다
 *   (`FreeBoardAdminController` — 목록·상세·삭제뿐). 관리자가 글을 쓸 때는 사용자 화면을 쓴다.
 *
 * ★ `new` 판정 필드가 이 게시판만 `newBadge` 다(다른 셋은 `isNew`). 서버 값을 그대로 쓴다 —
 *   화면에서 7일을 계산하면 사용자 기기의 시계가 기준이 된다.
 */
export default function FreeListView() {
  const { criteria, applySearch, applyOptions, goToPage, listQuery } = useBoardSearchParams();
  const { categories } = useCategories("FREE", {
    path: "/api/admin/categories?boardType=FREE",
  });

  // `/api/admin/free-posts` 는 `@DateTimeFormat(ISO.DATE) LocalDate` 라 기본 옵션("date")이 맞다
  const url = boardListUrl("/api/admin/free-posts", criteria);
  const { data, loading, error, reload } =
    useBoardList<PageResponse<FreePostListItem>>(url);

  const [actionError, setActionError] = useState<unknown>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  async function handleDelete(postId: number) {
    if (!confirmAction(CONFIRM.remove)) return;
    setDeletingId(postId);
    setActionError(null);
    try {
      await apiFetch(`/api/admin/free-posts/${postId}`, { method: "DELETE" });
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
        <h1>자유 게시판 관리</h1>
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
                    <Link href={`/admin/free-posts/${item.id}${listQuery}`}>{item.title}</Link>
                    <span className={styles.marks}>
                      <CommentCount count={item.commentCount} />
                      <NewBadge show={item.newBadge} />
                      <AttachmentIcon count={item.attachmentCount} />
                    </span>
                  </td>
                  <td className={styles.numeric}>{item.viewCount.toLocaleString()}</td>
                  <td className={styles.when}>{formatDateTime(item.createdAt)}</td>
                  <td className={styles.who}>{item.authorName ?? "-"}</td>
                  <td>
                    <div className={styles.actions}>
                      <Link href={`/admin/free-posts/${item.id}${listQuery}`}>
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
