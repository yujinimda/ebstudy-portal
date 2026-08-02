/**
 * 문의게시판 응답 타입 — 서버 record 를 그대로 옮긴 것이다(추측한 이름이 하나도 없다).
 *
 *   - `InquiryListItemResponse` → {@link InquiryListItem}
 *   - `InquiryDetailResponse`   → {@link InquiryDetail}
 *   - `InquiryAnswerResponse`   → {@link InquiryAnswer}
 *
 * ★ 목록 record 에는 **내용·답변 필드가 아예 없다**(FR-011 · AC-15). 화면이 안 그리는 것과
 *   서버가 안 보내는 것은 다르다 — 타입에도 자리를 만들지 않는다.
 */

/** 요구사항 6.1 목록 한 행. */
export interface InquiryListItem {
  /** 전체 게시글 수 기준 역순 번호. **고정 식별자가 아니다** — 링크에는 `id` 를 쓴다 */
  number: number;
  id: number;
  title: string;
  /** 자물쇠 표시. 비밀글이어도 제목·등록자는 그대로 보인다(AC-22) */
  secret: boolean;
  answered: boolean;
  /** ★ 서버 판정값. 화면에서 createdAt 으로 7일을 계산하지 않는다(기기 시계가 기준이 된다) */
  isNew: boolean;
  viewCount: number;
  createdAt: string;
  authorName: string;
  /** 화면 강조용. **권한 판정에는 쓰지 않는다** */
  mine: boolean;
}

/** 요구사항 6.3 관리자 답변. */
export interface InquiryAnswer {
  id: number;
  content: string;
  adminName: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * 요구사항 6.3 상세. **두 가지 모양**으로 온다.
 *   - 열린 상세: `locked=false`, `content` 와 `answer` 가 채워져 있다
 *   - 잠금 안내(`GET /{id}/preview`): `locked=true`, `content` · `answer` 가 `null`
 */
export interface InquiryDetail {
  id: number;
  title: string;
  /** 잠금 안내에서는 `null` */
  content: string | null;
  secret: boolean;
  locked: boolean;
  answered: boolean;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
  authorName: string;
  mine: boolean;
  /** 본인 + 미답변. ★ **화면 편의값이다** — 실제 차단은 서버가 다시 한다(FR-014 · AC-19) */
  editable: boolean;
  /** 본인 + 미답변. 위와 같다 — 버튼을 숨기는 것은 권한 검증이 아니다(AC-21) */
  deletable: boolean;
  answer: InquiryAnswer | null;
}

/** `POST /api/inquiries/{id}/unlock` 응답 — AC-29. */
export interface InquiryUnlockResult {
  /** 이 **글 하나**의 열람 통과. 다음 상세 조회에 `X-Inquiry-Grant` 로 실어 보낸다 */
  grantToken: string;
  /** 잠금해제와 동시에 열린 상세가 함께 온다 — 다시 조회할 필요가 없다 */
  inquiry: InquiryDetail;
}

function pad2(value: number): string {
  return value < 10 ? `0${value}` : String(value);
}

/**
 * ISO-8601 오프셋 일시 → `yyyy-MM-dd HH:mm`.
 *
 * ★ `toLocaleString()` 을 쓰지 않는다 — 브라우저 로캘에 따라 표기가 달라져 목록 컬럼 폭이
 *   사람마다 다르게 깨진다. 파싱에 실패하면 원문을 그대로 보여준다(빈 칸보다 낫다).
 */
export function formatDateTime(iso: string): string {
  const at = new Date(iso);
  if (Number.isNaN(at.getTime())) return iso;
  return (
    `${at.getFullYear()}-${pad2(at.getMonth() + 1)}-${pad2(at.getDate())}` +
    ` ${pad2(at.getHours())}:${pad2(at.getMinutes())}`
  );
}
