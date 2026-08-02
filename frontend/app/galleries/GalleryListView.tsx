"use client";

import Link from "next/link";
import Alert from "@/components/Alert";
import BoardListToolbar from "@/components/board/BoardListToolbar";
import BoardSearchBar from "@/components/board/BoardSearchBar";
import { NewBadge } from "@/components/board/ListMarks";
import Pagination from "@/components/board/Pagination";
import { boardListUrl } from "@/lib/board/criteria";
import { useBoardList } from "@/lib/board/useBoardList";
import { useBoardSearchParams } from "@/lib/board/useBoardSearchParams";
import { useCategories } from "@/lib/board/useCategories";
import type { PageResponse } from "@/lib/board/types";
import { formatDate } from "./format";
import styles from "./gallery.module.css";
import type { GalleryCardResponse } from "./types";

/**
 * 갤러리 목록 — 요구사항 5.1 <b>카드형</b>(썸네일 + 제목 + 내용 일부).
 *
 * 공통 규칙 1.1(기간·분류·검색어·개씩보기·정렬·페이징·검색조건 유지)은 전부 공통
 * 컴포넌트가 처리한다. 이 화면이 따로 하는 일은 **표(테이블) 대신 카드로 그리는 것** 뿐이다.
 *
 * ★ 기간 파라미터는 갤러리가 `String` 으로 직접 파싱하므로 기본 옵션(`"date"`)이 맞다
 *   (문의게시판만 `dateParam: "datetime"` 이다).
 */
export default function GalleryListView() {
  const { criteria, applySearch, applyOptions, goToPage, listQuery } = useBoardSearchParams();
  const { categories } = useCategories("GALLERY");

  const url = boardListUrl("/api/galleries", criteria);
  const { data, loading, error } = useBoardList<PageResponse<GalleryCardResponse>>(url);

  return (
    <main className={styles.page}>
      <div className={styles.pageHead}>
        <h1>갤러리</h1>
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
      >
        {/* 미로그인이어도 버튼을 숨기지 않는다 — 눌러서 로그인 화면으로 간 뒤
            RequireAuth 의 `?next=` 로 **검색조건까지 그대로** 등록 화면에 돌아온다(요구사항 1.3) */}
        <Link href={`/galleries/new${listQuery}`}>
          <button type="button">글 등록</button>
        </Link>
      </BoardListToolbar>

      <Alert error={error} />

      {data !== null && data.items.length === 0 && !loading && (
        <p className={styles.empty}>검색 조건에 맞는 글이 없습니다.</p>
      )}

      {loading && data === null && <p className="hint">목록을 불러오는 중…</p>}

      {data !== null && data.items.length > 0 && (
        <ul className={styles.grid}>
          {data.items.map((item) => (
            <li key={item.id} className={styles.card}>
              {/* 상세로 갈 때 지금 검색조건을 그대로 실어 보낸다 —
                  돌아올 때 `목록` 버튼이 이 값을 `/galleries` 에 붙여 조건을 복원한다 */}
              <Link href={`/galleries/${item.id}${listQuery}`} className={styles.cardLink}>
                <span className={styles.thumb}>
                  {item.thumbnailUrl === null ? (
                    <span className={styles.thumbEmpty}>이미지 없음</span>
                  ) : (
                    // eslint-disable-next-line @next/next/no-img-element -- 서버 API 가 내려주는
                    // 동적 바이너리 경로다. next/image 의 최적화는 remotePatterns 설정이 필요하고
                    // 이 이미지는 이미 서버가 immutable 로 캐시 지시를 붙여 보낸다
                    <img src={item.thumbnailUrl} alt="" loading="lazy" />
                  )}
                  {item.extraImageCount > 0 && (
                    <span className={styles.extraCount}>+{item.extraImageCount}</span>
                  )}
                </span>

                <span className={styles.cardBody}>
                  <span className={styles.cardTitle}>
                    {item.title}
                    {/* new 판정은 서버 값 그대로 — 기기 시계를 기준으로 삼지 않는다 */}
                    <NewBadge show={item.isNew} />
                  </span>
                  <span className={styles.cardExcerpt}>{item.excerpt}</span>
                  <span className={styles.cardMeta}>
                    <span>{item.categoryName ?? "미분류"}</span>
                    <span>{item.authorName}</span>
                    <span>{formatDate(item.createdAt)}</span>
                    <span>조회 {item.viewCount}</span>
                  </span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {data !== null && (
        <Pagination page={data.page} totalPages={data.totalPages} onChange={goToPage} />
      )}
    </main>
  );
}
