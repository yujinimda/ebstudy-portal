import { apiFetch } from "@/lib/api";

/**
 * 메인 페이지(요구사항 2장) 전용 타입·페처.
 *
 * ★ 목록 API 가 아니라 `/latest` 를 쓴다 — 목록은 기간이 **기본 1달**이라(요구사항 1.1)
 *   메인에 그대로 쓰면 한 달 넘게 글이 없는 게시판이 빈 칸으로 보인다.
 */

export interface NoticeLatest {
  id: number;
  displayNumber: number | null;
  categoryName: string | null;
  title: string;
  createdAt: string;
  pinned: boolean;
  isNew: boolean;
}

export interface FreeLatest {
  id: number;
  number: number;
  categoryName: string | null;
  title: string;
  commentCount: number;
  hasAttachment: boolean;
  newBadge: boolean;
  createdAt: string;
}

export interface GalleryLatest {
  id: number;
  number: number;
  categoryName: string | null;
  title: string;
  excerpt: string | null;
  thumbnailUrl: string | null;
  extraImageCount: number;
  isNew: boolean;
  createdAt: string;
}

export interface InquiryLatest {
  id: number;
  number: number;
  title: string;
  secret: boolean;
  answered: boolean;
  isNew: boolean;
  createdAt: string;
}

/** 한 영역이 실패해도 나머지는 보인다 — 메인이 통째로 죽지 않게 한다. */
async function safe<T>(promise: Promise<T[]>): Promise<T[]> {
  try {
    return await promise;
  } catch {
    return [];
  }
}

export function fetchMainSummary() {
  return Promise.all([
    safe(apiFetch<NoticeLatest[]>("/api/notices/latest?limit=5")),
    safe(apiFetch<FreeLatest[]>("/api/free-posts/latest?limit=5")),
    safe(apiFetch<GalleryLatest[]>("/api/galleries/latest?limit=3")),
    safe(apiFetch<InquiryLatest[]>("/api/inquiries/latest?limit=5")),
  ]);
}
