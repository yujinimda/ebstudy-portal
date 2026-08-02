"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import Alert from "@/components/Alert";
import BoardListToolbar from "@/components/board/BoardListToolbar";
import BoardSearchBar from "@/components/board/BoardSearchBar";
import { LockIcon, NewBadge, StatusBadge } from "@/components/board/ListMarks";
import Pagination from "@/components/board/Pagination";
import { boardListUrl } from "@/lib/board/criteria";
import type { BoardSort, PageResponse } from "@/lib/board/types";
import { useBoardList } from "@/lib/board/useBoardList";
import { useBoardSearchParams } from "@/lib/board/useBoardSearchParams";
import { useSession } from "@/lib/useSession";
import styles from "./inquiries.module.css";
import { formatDateTime, type InquiryListItem } from "./types";

/**
 * 요구사항 6.1 목록.
 *
 * ★★ **문의게시판만 다른 세 가지**(전부 서버 계약이라 틀리면 400·500 이다):
 *   1. `dateParam: "datetime"` — 컨트롤러가 `OffsetDateTime` 을 받는다.
 *      `from=2026-07-01` 을 보내면 스프링 타입 변환이 깨지고 **500** 이 된다
 *   2. `withCategory: false` — 분류가 없는 게시판이다. `categoryId` 를 보내면 400
 *   3. `sortOptions` 에서 `CATEGORY` 제거 — 분류 정렬도 서버가 400 으로 막는다
 *
 * ★ `나의 문의내역만 보기` 는 **로그인했을 때만** 보인다(요구사항 6.1).
 *   미로그인인데 URL 에 `mine=true` 가 있으면(메인 페이지의 "나의 문의내역" 링크가
 *   그렇게 들어온다) 서버가 401 로 거부한다 — 조용히 무시하고 전체를 주면 사용자는
 *   **자기 글만 본다고 믿으면서 전체를 보게 되기 때문**이다. 그래서 화면은 요청을 보내지
 *   않고 로그인으로 보낸 뒤, `?next=` 로 돌아와 필터가 걸린 목록을 그린다.
 */

/** 분류가 없으니 분류 정렬도 없다. 서버 `BoardSearchCriteria.of` 가 400 으로 막는 조합이다. */
const INQUIRY_SORTS: readonly BoardSort[] = ["CREATED_AT", "TITLE", "VIEW_COUNT"];

export default function InquiryListView() {
  const router = useRouter();
  const session = useSession();
  const { criteria, applySearch, applyOptions, goToPage, listQuery } = useBoardSearchParams();

  const loggedIn = session.status === "authenticated";
  // 미로그인 상태의 mine 요청은 보내지 않는다(401 이 뻔하다). 세션 확인이 끝나기 전에도
  // 보내지 않는다 — 로딩 중에 한 번 401 을 받고 로그인된 뒤 또 부르면 요청이 두 번이다
  const blockedByLogin = criteria.mine && !loggedIn;

  useEffect(() => {
    // 001 AC-24 와 같은 방식 — 지금 주소(검색조건 포함)를 목적지로 넘긴다.
    // ★ setState 를 effect 본문에서 동기로 부르지 않는다. 여기서 하는 것은 이동뿐이다
    if (!criteria.mine || session.status !== "anonymous") return;
    const here = `${window.location.pathname}${window.location.search}`;
    router.replace(`/login?next=${encodeURIComponent(here)}`);
  }, [criteria.mine, session.status, router]);

  const url = blockedByLogin
    ? null
    : boardListUrl("/api/inquiries", criteria, {
        dateParam: "datetime",
        withCategory: false,
        withMine: true,
      });

  const { data, loading, error } = useBoardList<PageResponse<InquiryListItem>>(url);

  return (
    <main className={styles.page}>
      <div className={styles.header}>
        <h1>문의게시판</h1>
      </div>
      <p className="subtitle">
        목록과 상세는 누구나 볼 수 있습니다. 비밀글은 비밀번호를 확인합니다.
      </p>

      <BoardSearchBar
        criteria={criteria}
        onSearch={applySearch}
        // 분류 셀렉트를 그리지 않으려면 categories 를 넘기지 않는다(문의게시판은 분류가 없다)
        showMine={loggedIn}
        keywordHint="제목 · 내용 · 등록자에서 부분 일치로 찾습니다"
        disabled={loading}
      />

      <BoardListToolbar
        criteria={criteria}
        onChange={applyOptions}
        totalElements={data?.totalElements}
        sortOptions={INQUIRY_SORTS}
        disabled={loading}
      >
        {/* 미로그인으로 눌러도 된다 — RequireAuth 가 로그인으로 보냈다가 되돌려 준다 */}
        <Link className={styles.linkButton} href={`/inquiries/new${listQuery}`}>
          문의 등록
        </Link>
      </BoardListToolbar>

      <Alert error={error} />

      {criteria.mine && session.status === "anonymous" && (
        <p className="hint">로그인 화면으로 이동합니다…</p>
      )}

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <caption className={styles.srOnly}>문의 목록</caption>
          <thead>
            <tr>
              <th scope="col" className={styles.colNum}>
                번호
              </th>
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
            {data !== null &&
              data.items.map((item) => (
                <tr key={item.id}>
                  <td className={styles.colNum}>{item.number}</td>
                  <td className={styles.colTitle}>
                    {/* 링크는 number 가 아니라 id 다 — 번호는 전체 글 수 기준이라 글마다 바뀐다.
                        listQuery 를 실어 보내면 상세에서 `목록` 을 눌렀을 때 조건이 살아 돌아온다 */}
                    <Link className={styles.titleLink} href={`/inquiries/${item.id}${listQuery}`}>
                      {item.title}
                    </Link>
                    <StatusBadge tone={item.answered ? "ok" : "muted"}>
                      {item.answered ? "답변완료" : "미답변"}
                    </StatusBadge>
                    <LockIcon show={item.secret} />
                    {/* 서버가 판정한 값을 그대로 넘긴다 */}
                    <NewBadge show={item.isNew} />
                  </td>
                  <td className={styles.colViews}>{item.viewCount.toLocaleString()}</td>
                  <td>{formatDateTime(item.createdAt)}</td>
                  <td>{item.authorName}</td>
                </tr>
              ))}

            {data !== null && data.items.length === 0 && (
              <tr>
                <td className={styles.empty} colSpan={5}>
                  {loading ? "불러오는 중…" : "조건에 맞는 문의가 없습니다."}
                </td>
              </tr>
            )}

            {data === null && !blockedByLogin && (
              <tr>
                <td className={styles.empty} colSpan={5}>
                  {error == null ? "불러오는 중…" : "목록을 불러오지 못했습니다."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {data !== null && (
        <Pagination page={criteria.page} totalPages={data.totalPages} onChange={goToPage} />
      )}
    </main>
  );
}
