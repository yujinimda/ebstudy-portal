"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import Alert from "@/components/Alert";
import { StatusBadge } from "@/components/board/ListMarks";
import { useBoardList } from "@/lib/board/useBoardList";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import { formatDateTime } from "../format";
import type { NoticeDetailResponse } from "../types";
import styles from "../notices.module.css";

/**
 * 공지사항 상세 — 요구사항 3.2
 * (분류 · 제목 · 등록일 · 등록한 관리자 이름 · 조회수 · 내용 · `목록` 버튼).
 *
 * ⚠️ `useListQuery` 가 `useSearchParams()` 를 쓴다 → **`<Suspense>` 안에서만 렌더된다.**
 *    경계는 `page.tsx` 가 만든다.
 *
 * ★ 로그인 없이 볼 수 있다(요구사항 1.3). `RequireAuth` 로 감싸지 않는다.
 *
 * ★ 조회수는 이 GET 이 증가시킨 뒤의 값이다(요구사항 1.4 단순 증가).
 *   그래서 화면에서 +1 을 더하지 않는다 — 더하면 두 번 세진다.
 */
export default function NoticeDetailView() {
  // 경로 세그먼트는 문자열이다. 숫자로 바꾸지 않고 그대로 URL 에 넣는다 —
  // 잘못된 값은 서버가 판정해 `POST_NOT_FOUND`(404) 를 주고 그 detail 을 그대로 보여준다.
  const params = useParams<{ id: string | string[] }>();
  const id = Array.isArray(params.id) ? params.id[0] : params.id;

  // ★ 목록에서 실어 보낸 검색조건 원문. `목록` 버튼에 그대로 다시 붙여 조건을 복원한다
  //   (요구사항 1.1 "상세→목록 시 직전 검색조건 유지").
  const listQuery = useListQuery();
  const listHref = `/notices${listQuery}`;

  const { data, loading, error } = useBoardList<NoticeDetailResponse>(
    id === undefined ? null : `/api/notices/${encodeURIComponent(id)}`,
  );

  return (
    <main className={styles.page}>
      <h1>공지사항</h1>

      <Alert error={error} />

      {loading && data === null && error == null && <p className="hint">불러오는 중…</p>}

      {data !== null && (
        <article className={styles.detail}>
          <header className={styles.detailHead}>
            <span className="hint">
              {data.categoryName}
              {/* 요구사항 3.1 알림글. 상세에서도 같은 표시를 유지한다 */}
              {data.pinned && <StatusBadge tone="ok">알림</StatusBadge>}
            </span>
            <h2 className={styles.detailTitle}>{data.title}</h2>
            {/* dl 대신 span 을 쓴다 — globals.css 의 `dl.info` 는 2열 그리드라
                한 줄 메타 표시와 모양이 맞지 않는다. 전역을 고치지 않기로 했으므로 피한다 */}
            <p className={styles.detailMeta}>
              <span>등록자 {data.authorName}</span>
              <span>등록일 {formatDateTime(data.createdAt)}</span>
              <span>조회수 {data.viewCount.toLocaleString()}</span>
            </p>
          </header>

          {/* 서버는 본문을 평문으로 저장한다. HTML 로 해석하면 그대로 XSS 통로가 되므로
              dangerouslySetInnerHTML 을 쓰지 않고, 줄바꿈만 CSS(pre-wrap)로 살린다 */}
          <div className={styles.detailBody}>{data.content}</div>
        </article>
      )}

      <div className={styles.detailActions}>
        {/*
          수정·삭제 버튼이 없는 이유: 공지사항은 **관리자만** 등록·수정한다(요구사항 0장 표).
          사용자 화면은 읽기 전용이고, 관리 기능은 관리자 화면(`/admin/...`)에 있다.
          ★ 버튼이 없는 것은 권한 검증이 아니다 — 관리 API 를 직접 불러도 서버가 막는다.
        */}
        {/* button 을 Link 로 감싸면 a 안에 button 이 들어가 접근성 트리가 깨진다.
            링크 하나로 두고 모양만 버튼처럼 쓴다 — prefetch·새 탭 열기도 그대로 살아 있다 */}
        <Link href={listHref} className={styles.listLink}>
          목록
        </Link>
      </div>
    </main>
  );
}
