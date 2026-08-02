/**
 * 갤러리 응답 타입 — 서버 record 를 그대로 옮긴 것이다(추측한 이름이 하나도 없다).
 *
 * 출처:
 *   - `board/gallery/GalleryCardResponse.java`
 *   - `board/gallery/GalleryDetailResponse.java`
 *   - `board/gallery/GalleryImageResponse.java`
 *
 * ★ `isNew` 는 **서버 판정값**이다. 화면에서 `createdAt` 으로 7일을 계산하지 않는다 —
 *   그러면 사용자 기기의 시계가 기준이 된다(`ListMarks` 주석과 같은 이유).
 */

/** 목록 카드 한 장 — 요구사항 5.1. */
export interface GalleryCardResponse {
  /** 요구사항 1.1 "전체 게시글 수 기준 역순". 행 번호가 아니다 */
  number: number;
  id: number;
  categoryId: number | null;
  categoryName: string | null;
  title: string;
  excerpt: string;
  /** 이미지가 없으면 null — 화면이 대체 표시를 쓴다 */
  thumbnailUrl: string | null;
  imageCount: number;
  /** 첫 장을 뺀 개수. 화면이 `imageCount - 1` 을 계산하지 않게 서버가 준다 */
  extraImageCount: number;
  viewCount: number;
  authorId: number;
  authorName: string;
  createdAt: string;
  isNew: boolean;
}

/** 캐러셀 한 장 — 요구사항 5.2. `sortOrder` 0번이 썸네일이다. */
export interface GalleryImageResponse {
  id: number;
  originalName: string;
  /** `/api/galleries/{postId}/images/{imageId}`. 저장 경로는 서버 밖으로 나오지 않는다 */
  url: string;
  contentType: string;
  sizeBytes: number;
  sortOrder: number;
}

/** 상세 — 요구사항 5.2. */
export interface GalleryDetailResponse {
  id: number;
  categoryId: number | null;
  categoryName: string | null;
  title: string;
  content: string;
  viewCount: number;
  authorId: number;
  authorName: string;
  createdAt: string;
  updatedAt: string;
  isNew: boolean;
  /**
   * 본인 글인가 — 수정·삭제 버튼 노출용 **편의값**이다.
   * ⚠️ 요구사항 1.3 · 001 AC-26 — 버튼을 숨기는 것은 권한 검증이 아니다.
   *    주소창으로 직접 들어와도 서버가 매 요청 소유자를 다시 확인해 403 을 준다.
   */
  owned: boolean;
  /** **이미 순서대로** 담겨 온다. 화면에서 다시 정렬하면 목록 썸네일과 어긋난다 */
  images: GalleryImageResponse[];
}
