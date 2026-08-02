"use client";

import Link from "next/link";
import Alert from "@/components/Alert";
import BoardListToolbar from "@/components/board/BoardListToolbar";
import BoardSearchBar from "@/components/board/BoardSearchBar";
import Pagination from "@/components/board/Pagination";
import { AttachmentIcon, CommentCount, NewBadge } from "@/components/board/ListMarks";
import { boardListUrl } from "@/lib/board/criteria";
import { useBoardList } from "@/lib/board/useBoardList";
import { useBoardSearchParams } from "@/lib/board/useBoardSearchParams";
import { useCategories } from "@/lib/board/useCategories";
import type { PageResponse } from "@/lib/board/types";
import { formatDateTime } from "./format";
import type { FreePostListItem } from "./types";
import styles from "./free.module.css";

/**
 * 요구사항 4.1 — 목록.
 * 컬럼: 번호 · 분류 · 제목(댓글 수 · new · 첨부 아이콘) · 조회 · 등록일시 · 등록자.
 *
 * ★ 검색조건은 이 컴포넌트의 state 가 아니라 **URL** 에 있다(`useBoardSearchParams`).
 *   그래서 상세로 갔다 돌아와도, 새로고침해도, 링크를 공유해도 조건이 그대로다
 *   (요구사항 1.1 "검색조건 유지").
 *
 * ★ 자유게시판은 기간 파라미터가 `LocalDate` 라 `boardListUrl` 의 **기본 옵션**을 쓴다.
 *   (오프셋 일시를 보내면 스프링 타입 변환이 깨져 500 이 된다 — 문의게시판만 datetime)
 */
export default function FreeListView() {
  const { criteria, applySearch, applyOptions, goToPage, listQuery } = useBoardSearchParams();
  const { categories } = useCategories("FREE");

  const url = boardListUrl("/api/free-posts", criteria);
  const { data, loading, error } = useBoardList<PageResponse<FreePostListItem>>(url);

  const items = data?.items ?? [];

  return (
    <main className={styles.wide}>
      <div className={styles.pageHead}>
        <h1>자유게시판</h1>
      </div>
      <p className="subtitle">누구나 읽을 수 있고, 글쓰기는 로그인이 필요합니다.</p>

      <BoardSearchBar
        criteria={criteria}
        onSearch={applySearch}
        categories={categories}
        disabled={loading}
      />

      <Alert error={error} />

      <BoardListToolbar
        criteria={criteria}
        onChange={applyOptions}
        totalElements={data?.totalElements}
        disabled={loading}
      >
        {/* 미로그인이어도 숨기지 않는다 — 눌러서 로그인 화면으로 가고,
            로그인하면 RequireAuth 가 넘긴 ?next= 로 바로 등록 화면에 돌아온다(요구사항 1.3) */}
        <Link className={styles.buttonLink} href={`/free/new${listQuery}`}>
          글 등록
        </Link>
      </BoardListToolbar>

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col" className={styles.colNum}>
                번호
              </th>
              <th scope="col">분류</th>
              <th scope="col" className={styles.colTitle}>
                제목
              </th>
              <th scope="col" className={styles.colViews}>
                조회
              </th>
              <th scope="col">등록일시</th>
              <th scope="col">등록자</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.id}>
                {/* 요구사항 1.1 — 번호는 전체 글 수 기준 역순이다. 링크는 반드시 id 로 건다 */}
                <td className={styles.colNum}>{item.number}</td>
                <td>{item.categoryName ?? "-"}</td>
                <td className={styles.colTitle}>
                  <Link className={styles.titleLink} href={`/free/${item.id}${listQuery}`}>
                    {item.title}
                  </Link>
                  <CommentCount count={item.commentCount} />
                  {/* ★ 서버가 판정한 값이다. createdAt 으로 7일을 계산하면 기준이 사용자 시계가 된다 */}
                  <NewBadge show={item.newBadge} />
                  <AttachmentIcon count={item.attachmentCount} />
                </td>
                <td className={styles.colViews}>{item.viewCount.toLocaleString()}</td>
                <td>{formatDateTime(item.createdAt)}</td>
                <td>{item.authorName ?? "-"}</td>
              </tr>
            ))}
            {items.length === 0 && (
              <tr>
                <td className={styles.empty} colSpan={6}>
                  {loading ? "불러오는 중…" : "등록된 글이 없습니다."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* totalPages <= 1 이면 Pagination 이 스스로 null 을 반환한다 */}
      <Pagination
        page={data?.page ?? 0}
        totalPages={data?.totalPages ?? 0}
        onChange={goToPage}
      />
    </main>
  );
}
