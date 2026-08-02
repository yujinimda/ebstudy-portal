package com.ebstudy.portal.board.gallery;

/**
 * 등록·수정 본문 — 요구사항 5.3 <i>"분류 · 제목 · 내용"</i>.
 *
 * <p>이미지는 여기 없다. 파일은 {@code multipart/form-data} 의 별도 파트로 오고
 * 이 record 는 <b>스칼라 필드만</b> 담는다 — 컨트롤러가 폼 필드에서 조립한다.
 *
 * <p>검증은 이 record 가 하지 않는다. {@code @NotNull} 같은 애너테이션을 붙이면
 * "record 를 거치지 않은 호출" 이 검증을 건너뛰게 되므로,
 * 판정은 {@link GalleryService} 한 곳에서만 한다(001 {@code FR-002} 와 같은 원칙).
 */
public record GalleryWriteRequest(Long categoryId, String title, String content) {
}
