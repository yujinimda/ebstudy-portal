"use client";

import Link from "next/link";
import Alert from "@/components/Alert";
import BoardListToolbar from "@/components/board/BoardListToolbar";
import BoardSearchBar from "@/components/board/BoardSearchBar";
import { NewBadge, StatusBadge } from "@/components/board/ListMarks";
import Pagination from "@/components/board/Pagination";
import { boardListUrl } from "@/lib/board/criteria";
import { useBoardList } from "@/lib/board/useBoardList";
import { useBoardSearchParams } from "@/lib/board/useBoardSearchParams";
import { useCategories } from "@/lib/board/useCategories";
import { formatDate } from "./format";
import type { NoticeListItem, NoticeListResponse } from "./types";
import styles from "./notices.module.css";

/**
 * 공지사항 목록 — 요구사항 3.1.
 *
 * ⚠️ `useBoardSearchParams` 가 `useSearchParams()` 를 쓴다 → **`<Suspense>` 안에서만
 *    렌더돼야 한다.** 경계는 `page.tsx`(서버 컴포넌트)가 만든다. 없으면 개발 서버는
 *    통과하는데 `npm run build` 가 실패한다.
 *
 * ★ 공지 목록 응답만 `PageResponse` 가 아니다 — `{ pinned, page }` 다.
 *   고정 글이 페이징 안에 있으면 (1) 2페이지에서 사라지고 (2) "10개씩 보기"가 거짓이 되고
 *   (3) 번호 계산이 어긋난다. 그래서 서버가 두 배열로 나눠 준다.
 *
 * ★ 이 게시판은 **사용자가 읽기만 한다**(등록은 관리자 화면). 그래서 `글 등록` 버튼이 없다.
 */
export default function NoticeListView() {
  const { criteria, applySearch, applyOptions, goToPage, listQuery } = useBoardSearchParams();
  const { categories } = useCategories("NOTICE");

  // 옵션 기본값 그대로다 — 공지 컨트롤러는 기간을 String 으로 받아 직접 파싱하므로
  // `yyyy-MM-dd`(dateParam:"date") 로 충분하다. 문의게시판만 datetime 이 필요하다.
  const url = boardListUrl("/api/notices", criteria);
  const { data, loading, error } = useBoardList<NoticeListResponse>(url);

  const pinned = data?.pinned ?? [];
  const page = data?.page ?? null;
  const rows = page?.items ?? [];

  return (
    <main className={styles.page}>
      <h1>공지사항</h1>
      <p className="subtitle">중요한 안내를 확인하세요.</p>

      <BoardSearchBar
        criteria={criteria}
        onSearch={applySearch}
        categories={categories}
        // 공지사항만 검색 범위에 등록자가 없다(요구사항 0장 표).
        keywordHint="제목 · 내용에서 부분 일치로 찾습니다"
        disabled={loading}
      />

      {/* 1년 초과 기간·잘못된 정렬 등은 서버가 거부한다. 문구는 서버 detail 그대로. */}
      <Alert error={error} />

      <BoardListToolbar
        criteria={criteria}
        onChange={applyOptions}
        totalElements={page?.totalElements}
        disabled={loading}
      />

      <div className={styles.tableWrap}>
        <table className={styles.table} aria-label="공지사항 목록">
          <thead>
            <tr>
              <th scope="col" className={styles.numberCell}>번호</th>
              <th scope="col">분류</th>
              <th scope="col" className={styles.titleCell}>제목</th>
              <th scope="col">등록자</th>
              <th scope="col">등록일시</th>
              <th scope="col" className={styles.viewCell}>조회수</th>
            </tr>
          </thead>
          <tbody>
            {/* ★ 고정 글은 **모든 페이지의 제일 상단**에 온다. 페이지를 넘겨도 그대로다 */}
            {pinned.map((item) => (
              <NoticeRow key={`pinned-${item.id}`} item={item} listQuery={listQuery} />
            ))}
            {rows.map((item) => (
              <NoticeRow key={item.id} item={item} listQuery={listQuery} />
            ))}

            {pinned.length === 0 && rows.length === 0 && (
              <tr>
                <td className={styles.empty} colSpan={6}>
                  {loading
                    ? "불러오는 중…"
                    : error != null
                      ? "목록을 불러오지 못했습니다"
                      : "검색 조건에 맞는 공지사항이 없습니다"}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {/* totalPages <= 1 이면 Pagination 이 스스로 null 을 반환한다 */}
      <Pagination
        page={criteria.page}
        totalPages={page?.totalPages ?? 0}
        onChange={goToPage}
      />
    </main>
  );
}

/**
 * 목록 한 줄.
 *
 * ★ 상세로 갈 때 `listQuery`(지금 주소창의 쿼리스트링 원문)를 **그대로 실어 보낸다.**
 *   상세의 `목록` 버튼이 그 값을 `/notices` 에 다시 붙이면 검색조건이 복원된다
 *   (요구사항 1.1 "검색조건 유지"). 파싱해서 다시 만들지 않는 것이 핵심이다.
 */
function NoticeRow({ item, listQuery }: { item: NoticeListItem; listQuery: string }) {
  return (
    <tr className={item.pinned ? styles.pinnedRow : undefined}>
      <td className={styles.numberCell}>
        {/* ★ 요구사항 3.1 — 고정 글은 번호 대신 분류명을 보여준다.
            서버가 고정 글의 displayNumber 를 아예 null 로 준다(그려질 수 없게). */}
        {item.pinned ? (
          <StatusBadge tone="ok">{item.categoryName}</StatusBadge>
        ) : (
          item.displayNumber
        )}
      </td>
      <td>{item.categoryName}</td>
      <td className={styles.titleCell}>
        <Link href={`/notices/${item.id}${listQuery}`}>{item.title}</Link>
        {/* new 판정은 서버 값 그대로. 기기 시계로 계산하지 않는다 */}
        <NewBadge show={item.isNew} />
      </td>
      <td>{item.authorName}</td>
      <td className={styles.dateCell}>{formatDate(item.createdAt)}</td>
      <td className={styles.viewCell}>{item.viewCount.toLocaleString()}</td>
    </tr>
  );
}
