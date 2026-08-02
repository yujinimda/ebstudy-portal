/**
 * 자유게시판 서버 응답 타입 — 백엔드 record 를 그대로 옮긴 것이다.
 *
 * ★ 이름을 바꾸지 않는다. `newBadge` 는 자유게시판만 쓰는 이름이고
 *   (공지·갤러리·문의는 `isNew`) 여기서 예쁘게 고치면 서버 계약과 어긋난다.
 */

/** `FreePostListItem` — 요구사항 4.1 목록 한 줄. */
export interface FreePostListItem {
  /** 요구사항 1.1 "전체 게시글 수 기준 역순". 행 번호가 아니고 링크 키도 아니다 */
  number: number;
  id: number;
  categoryId: number | null;
  categoryName: string | null;
  title: string;
  commentCount: number;
  attachmentCount: number;
  hasAttachment: boolean;
  /** ★ 서버가 판정한 `new`. 화면에서 createdAt 으로 7일을 계산하면 사용자 기기 시계가 기준이 된다 */
  newBadge: boolean;
  viewCount: number;
  authorName: string | null;
  createdAt: string;
}

/** `FreeAttachmentItem` — 요구사항 4.2 첨부 다운로드. */
export interface FreeAttachmentItem {
  id: number;
  originalName: string;
  sizeBytes: number;
  sortOrder: number;
  /** `/api/free-posts/{postId}/attachments/{id}`. 서버가 만들어 준다 */
  downloadUrl: string;
}

/** `FreeCommentItem` — 요구사항 4.2 댓글 한 줄. */
export interface FreeCommentItem {
  id: number;
  authorId: number | null;
  authorName: string | null;
  content: string;
  createdAt: string;
  /** ★ 화면 편의값. 삭제 요청이 오면 서버가 소유자를 다시 본다 */
  deletable: boolean;
}

/** `FreePostDetail` — 요구사항 4.2. */
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
  /** ★ 버튼 노출용 편의값. 서버가 다시 검증한다(요구사항 1.3) */
  editable: boolean;
  deletable: boolean;
  /** 요구사항 4.2 "입력은 로그인한 사용자만 보인다" */
  commentable: boolean;
}

/** 등록·댓글작성 응답 — `CreatedIdResponse`. */
export interface CreatedIdResponse {
  id: number;
}

/** 요구사항 4.3 — 안내(hint)에 쓰는 값. **검증은 서버가 한다.** */
export const ATTACHMENT_ACCEPT = ".jpg,.jpeg,.gif,.png,.zip";
export const ATTACHMENT_MAX_COUNT = 5;
