"use client";

import Link from "next/link";
import { useState } from "react";
import Alert from "@/components/Alert";
import BoardListToolbar from "@/components/board/BoardListToolbar";
import BoardSearchBar from "@/components/board/BoardSearchBar";
import Pagination from "@/components/board/Pagination";
import { LockIcon, NewBadge, StatusBadge } from "@/components/board/ListMarks";
import { apiFetch } from "@/lib/api";
import { boardListUrl } from "@/lib/board/criteria";
import type { BoardSort, PageResponse } from "@/lib/board/types";
import { useBoardList } from "@/lib/board/useBoardList";
import { useBoardSearchParams } from "@/lib/board/useBoardSearchParams";
import styles from "../admin.module.css";
import { CONFIRM, confirmAction } from "../confirm";
import { formatDateTime } from "../format";
import type { InquiryListItem } from "../types";

/**
 * 문의게시판 관리 목록 — 요구사항 6.1 · 6.5.
 *
 * ★★ **문의게시판만 규칙이 셋 다르다. 하나라도 빠뜨리면 400/500 이다.**
 *   1. 기간이 `OffsetDateTime` 이다 → `dateParam: "datetime"`. `yyyy-MM-dd` 를 보내면
 *      스프링 타입 변환이 깨져 **500** 이 난다
 *   2. **분류가 없다** → `withCategory: false`, 검색바에 `categories` 를 넘기지 않는다
 *   3. 정렬에서 **`CATEGORY` 를 뺀다** → 분류 없는 게시판에 분류 정렬을 보내면 서버가 **400**
 *
 * `mine`(나의 문의내역)은 관리자 목록에 없다 — 관리자는 전부 본다(`withMine` 기본 false).
 */
const INQUIRY_SORTS: readonly BoardSort[] = ["CREATED_AT", "TITLE", "VIEW_COUNT"];

export default function InquiryListView() {
  const { criteria, applySearch, applyOptions, goToPage, listQuery } = useBoardSearchParams();

  const url = boardListUrl("/api/admin/inquiries", criteria, {
    dateParam: "datetime",
    withCategory: false,
  });
  const { data, loading, error, reload } = useBoardList<PageResponse<InquiryListItem>>(url);

  const [actionError, setActionError] = useState<unknown>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  async function handleDelete(id: number) {
    if (!confirmAction(CONFIRM.remove)) return;
    setDeletingId(id);
    setActionError(null);
    try {
      await apiFetch(`/api/admin/inquiries/${id}`, { method: "DELETE" });
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
        <h1>문의 게시판 관리</h1>
      </div>

      {/* 분류 셀렉트를 그리지 않기 위해 categories 를 넘기지 않는다 */}
      <BoardSearchBar criteria={criteria} onSearch={applySearch} disabled={loading} />

      <BoardListToolbar
        criteria={criteria}
        onChange={applyOptions}
        totalElements={data?.totalElements}
        sortOptions={INQUIRY_SORTS}
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
                  <td className={styles.titleCell}>
                    <Link href={`/admin/inquiries/${item.id}${listQuery}`}>{item.title}</Link>
                    <span className={styles.marks}>
                      <StatusBadge tone={item.answered ? "ok" : "muted"}>
                        {item.answered ? "답변완료" : "미답변"}
                      </StatusBadge>
                      <LockIcon show={item.secret} />
                      <NewBadge show={item.isNew} />
                    </span>
                  </td>
                  <td className={styles.numeric}>{item.viewCount.toLocaleString()}</td>
                  <td className={styles.when}>{formatDateTime(item.createdAt)}</td>
                  <td className={styles.who}>{item.authorName ?? "-"}</td>
                  <td>
                    <div className={styles.actions}>
                      <Link href={`/admin/inquiries/${item.id}${listQuery}`}>
                        <button type="button" className={`secondary ${styles.smallButton}`}>
                          {item.answered ? "상세" : "답변"}
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
                <td className={styles.empty} colSpan={6}>
                  {loading ? "불러오는 중…" : "등록된 문의가 없습니다."}
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
