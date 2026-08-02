/**
 * 관리자 화면이 쓰는 서버 응답 타입 — **백엔드 record 를 그대로 옮긴 것이다.**
 *
 * 프론트가 편한 대로 이름을 바꾸지 않는다. 바꾸는 순간 "서버 필드 → 화면 필드" 매핑이
 * 한 겹 더 생기고, 서버가 필드를 바꿔도 타입 오류가 나지 않아 조용히 undefined 가 된다.
 * (출처: backend/src/main/java/com/ebstudy/portal/board/**)
 */

import type { PageResponse } from "@/lib/board/types";

/** `CategoryAdminResponse` — 요구사항 7.2. `deletable` 은 서버가 계산해 준다. */
export interface CategoryAdminItem {
  id: number;
  boardType: string;
  name: string;
  sortOrder: number;
  active: boolean;
  postCount: number;
  /** `postCount === 0`. ★ 이 값이 false 인데 삭제를 부르면 서버가 409 로 다시 막는다 */
  deletable: boolean;
  createdAt: string;
  updatedAt: string;
}

/** `NoticeListItem` — `displayNumber` 는 **고정 글이면 null**(요구사항 3.1). */
export interface NoticeListItem {
  id: number;
  displayNumber: number | null;
  categoryId: number | null;
  categoryName: string | null;
  title: string;
  viewCount: number;
  createdAt: string;
  authorName: string | null;
  pinned: boolean;
  isNew: boolean;
}

/**
 * `NoticeAdminListResponse` — 사용자 목록(`{ pinned, page }`)과 **모양이 다르다**.
 * 관리 화면은 고정 글을 따로 빼지 않고 목록에 섞어 보여준다(6번째 고정 글도 찾을 수 있어야 한다).
 */
export interface NoticeAdminListResponse {
  page: PageResponse<NoticeListItem>;
  pinnedCount: number;
  pinnedLimit: number;
}

/** `NoticeDetailResponse` */
export interface NoticeDetail {
  id: number;
  categoryId: number | null;
  categoryName: string | null;
  title: string;
  content: string;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
  authorName: string | null;
  pinned: boolean;
  editable: boolean;
}

/** `FreePostListItem` — ★ new 판정 필드가 `newBadge` 다(다른 게시판은 `isNew`). */
export interface FreePostListItem {
  number: number;
  id: number;
  categoryId: number | null;
  categoryName: string | null;
  title: string;
  commentCount: number;
  attachmentCount: number;
  hasAttachment: boolean;
  newBadge: boolean;
  viewCount: number;
  authorName: string | null;
  createdAt: string;
}

export interface FreeAttachmentItem {
  id: number;
  originalName: string;
  sizeBytes: number;
  sortOrder: number;
  downloadUrl: string;
}

export interface FreeCommentItem {
  id: number;
  authorId: number | null;
  authorName: string | null;
  content: string;
  createdAt: string;
  /** 관리자는 모든 댓글에 true 다(요구사항 1.3). 그래도 서버가 다시 검증한다 */
  deletable: boolean;
}

/** `FreePostDetail` */
export interface FreePostDetail {
  id: number;
  categoryId: number | null;
  categoryName: string | null;
  title: string;
  content: string;
  viewCount: number;
  authorId: number | null;
  authorName: string | null;
  createdAt: string;
  updatedAt: string;
  attachments: FreeAttachmentItem[];
  comments: FreeCommentItem[];
  editable: boolean;
  deletable: boolean;
  commentable: boolean;
}

/** `GalleryAdminRowResponse` — 요구사항 5.4 (썸네일 + 파일 개수 `+8`). */
export interface GalleryAdminRow {
  number: number;
  id: number;
  categoryId: number | null;
  categoryName: string | null;
  title: string;
  thumbnailUrl: string | null;
  imageCount: number;
  /** 첫 이미지를 뺀 나머지 수 — `+8` 표기에 그대로 쓴다 */
  extraImageCount: number;
  viewCount: number;
  authorId: number | null;
  authorName: string | null;
  createdAt: string;
  isNew: boolean;
}

export interface GalleryImage {
  id: number;
  originalName: string;
  url: string;
  contentType: string;
  sizeBytes: number;
  sortOrder: number;
}

/** `GalleryDetailResponse` */
export interface GalleryDetail {
  id: number;
  categoryId: number | null;
  categoryName: string | null;
  title: string;
  content: string;
  viewCount: number;
  authorId: number | null;
  authorName: string | null;
  createdAt: string;
  updatedAt: string;
  isNew: boolean;
  owned: boolean;
  images: GalleryImage[];
}

/** `InquiryListItemResponse` */
export interface InquiryListItem {
  number: number;
  id: number;
  title: string;
  secret: boolean;
  answered: boolean;
  isNew: boolean;
  viewCount: number;
  createdAt: string;
  authorName: string | null;
  mine: boolean;
}

/** `InquiryAnswerResponse` */
export interface InquiryAnswer {
  id: number;
  content: string;
  adminName: string | null;
  createdAt: string;
  updatedAt: string;
}

/** `InquiryDetailResponse` — 관리자는 비밀글도 비밀번호 없이 본다(AC-31). */
export interface InquiryDetail {
  id: number;
  title: string;
  content: string;
  secret: boolean;
  locked: boolean;
  answered: boolean;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
  authorName: string | null;
  mine: boolean;
  editable: boolean;
  deletable: boolean;
  /** 답변이 없으면 null — 요구사항 6.3 "아직 등록된 답변이 없습니다." */
  answer: InquiryAnswer | null;
}
