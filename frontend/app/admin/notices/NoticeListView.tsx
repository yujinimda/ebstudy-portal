"use client";

import Link from "next/link";
import { useState } from "react";
import Alert from "@/components/Alert";
import BoardListToolbar from "@/components/board/BoardListToolbar";
import BoardSearchBar from "@/components/board/BoardSearchBar";
import Pagination from "@/components/board/Pagination";
import { NewBadge, StatusBadge } from "@/components/board/ListMarks";
import { apiFetch } from "@/lib/api";
import { boardListUrl } from "@/lib/board/criteria";
import { useBoardList } from "@/lib/board/useBoardList";
import { useBoardSearchParams } from "@/lib/board/useBoardSearchParams";
import { useCategories } from "@/lib/board/useCategories";
import styles from "../admin.module.css";
import { CONFIRM, confirmAction } from "../confirm";
import { formatDateTime } from "../format";
import type { NoticeAdminListResponse } from "../types";

/**
 * 공지사항 관리 목록 — 요구사항 3.3 · 7.1.
 *
 * ★ 응답 모양이 사용자 목록과 다르다. 사용자쪽은 `{ pinned, page }` 로 고정 글을 빼서 주지만
 *   관리 목록은 `{ page, pinnedCount, pinnedLimit }` — 고정 글이 **일반 목록에 섞여** 온다.
 *   그래야 6번째로 고정한 글(사용자에게는 안 보인다)도 관리자가 찾아 고정을 풀 수 있다.
 *
 * ★ 분류 목록은 **관리자 경로**로 받는다 — 비활성 분류로 등록된 과거 글을 검색할 수 있어야 한다.
 */
export default function NoticeListView() {
  const { criteria, applySearch, applyOptions, goToPage, listQuery } = useBoardSearchParams();
  const { categories } = useCategories("NOTICE", {
    path: "/api/admin/categories?boardType=NOTICE",
  });

  // 공지 관리 목록은 기간 파라미터를 String 으로 받는다 → 기본 옵션("date")이 맞다
  const url = boardListUrl("/api/admin/notices", criteria);
  const { data, loading, error, reload } = useBoardList<NoticeAdminListResponse>(url);

  const [actionError, setActionError] = useState<unknown>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  async function handleDelete(id: number) {
    if (!confirmAction(CONFIRM.remove)) return;
    setDeletingId(id);
    setActionError(null);
    try {
      await apiFetch(`/api/admin/notices/${id}`, { method: "DELETE" });
      reload();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setDeletingId(null);
    }
  }

  const page = data?.page ?? null;
  const items = page?.items ?? [];

  return (
    <>
      <div className={styles.pageHead}>
        <h1>공지사항 관리</h1>
      </div>

      <BoardSearchBar
        criteria={criteria}
        onSearch={applySearch}
        categories={categories}
        keywordHint="제목 · 내용에서 부분 일치로 찾습니다"
        disabled={loading}
      />

      <BoardListToolbar
        criteria={criteria}
        onChange={applyOptions}
        totalElements={page?.totalElements}
        disabled={loading}
      >
        {/* 검색조건을 그대로 실어 보낸다 — 등록 취소로 돌아왔을 때 목록이 복원된다 */}
        <Link href={`/admin/notices/new${listQuery}`}>
          <button type="button">글 등록</button>
        </Link>
      </BoardListToolbar>

      <Alert error={error} />
      <Alert error={actionError} />

      {data !== null && data.pinnedCount > data.pinnedLimit && (
        <p className="alert ok" role="status">
          상단 고정이 {data.pinnedCount}개입니다. 사용자 화면에는 최신 {data.pinnedLimit}개만
          노출됩니다(요구사항 3.1).
        </p>
      )}

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
                <tr key={item.id} className={item.pinned ? styles.rowPinned : undefined}>
                  <td className={styles.num}>
                    {/* 요구사항 3.1 — 고정 글은 번호 대신 `알림`. 서버가 번호를 아예 주지 않는다 */}
                    {item.pinned ? <StatusBadge tone="ok">알림</StatusBadge> : item.displayNumber}
                  </td>
                  <td>{item.categoryName ?? "-"}</td>
                  <td className={styles.titleCell}>
                    <Link href={`/admin/notices/${item.id}/edit${listQuery}`}>{item.title}</Link>
                    <span className={styles.marks}>
                      <NewBadge show={item.isNew} />
                    </span>
                  </td>
                  <td className={styles.numeric}>{item.viewCount.toLocaleString()}</td>
                  <td className={styles.when}>{formatDateTime(item.createdAt)}</td>
                  <td className={styles.who}>{item.authorName ?? "-"}</td>
                  <td>
                    <div className={styles.actions}>
                      <Link href={`/admin/notices/${item.id}/edit${listQuery}`}>
                        <button type="button" className={`secondary ${styles.smallButton}`}>
                          수정
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
                  {loading ? "불러오는 중…" : "등록된 공지사항이 없습니다."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <Pagination
        page={criteria.page}
        totalPages={page?.totalPages ?? 0}
        onChange={goToPage}
      />
    </>
  );
}
