/**
 * 공지사항 응답 타입 — 백엔드 record 를 그대로 옮긴 것이다.
 *
 * 출처: `board/notice/NoticeListItem.java` · `NoticeListResponse.java` ·
 *      `NoticeDetailResponse.java`. 필드 이름·널 허용 여부를 추측하지 않고 맞췄다.
 */

import type { PageResponse } from "@/lib/board/types";

/**
 * 목록 한 줄.
 *
 * ★ `displayNumber` 가 **고정 글에서는 null** 이다. 요구사항 3.1 "고정된 글은 번호 대신
 *   분류명(알림)" 을 서버가 "번호를 아예 주지 않는" 방식으로 구현했다 — 화면이 실수로
 *   번호를 그릴 수 없게 만든 것이다.
 * ★ `isNew` 는 서버 판정값이다. `createdAt` 으로 7일을 계산하면 사용자 기기 시계가 기준이 된다.
 */
export interface NoticeListItem {
  id: number;
  displayNumber: number | null;
  categoryId: number;
  categoryName: string;
  title: string;
  viewCount: number;
  createdAt: string;
  authorName: string;
  pinned: boolean;
  isNew: boolean;
}

/**
 * ★★ **공지 목록만 `PageResponse` 가 아니다.**
 *
 * 고정 글은 페이징 밖에 있어야 한다(요구사항 3.1 "모든 페이지의 제일 상단").
 * 그래서 `pinned` 는 별도 배열이고 `page.items` 에는 고정 글이 없다.
 * `page.totalElements` 도 고정 글을 뺀 수다.
 */
export interface NoticeListResponse {
  /** 최대 5건. 서버가 이미 최신 5개로 잘라서 준다 */
  pinned: NoticeListItem[];
  page: PageResponse<NoticeListItem>;
}

/** 상세 — 요구사항 3.2. */
export interface NoticeDetailResponse {
  id: number;
  categoryId: number;
  categoryName: string;
  title: string;
  content: string;
  /** 이번 조회를 **포함한** 값이다(요구사항 1.4) */
  viewCount: number;
  createdAt: string;
  updatedAt: string;
  /** 등록한 관리자 이름 */
  authorName: string;
  pinned: boolean;
  /** 편의 값일 뿐이다 — 실제 차단은 관리 API 가 한다. 사용자 화면은 쓰지 않는다 */
  editable: boolean;
}
