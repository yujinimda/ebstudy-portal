package com.ebstudy.portal.board.notice;

import java.time.OffsetDateTime;

/**
 * 공지사항 상세 — 요구사항 3.2
 * (분류 · 제목 · 등록일 · 등록한 관리자 이름 · 조회수 · 내용 · 목록 버튼).
 *
 * @param viewCount 이번 조회를 <b>포함한</b> 값이다. 증가 전 값을 주면 사용자가 새로고침해야
 *                  자기 조회가 반영되는 것처럼 보인다
 * @param editable  요구사항 1.3 — 화면이 수정·삭제 버튼을 그릴지 판단하는 값.
 *                  <b>이 값은 편의일 뿐이고 실제 검증은 관리 API 가 다시 한다.</b>
 *                  {@code false} 를 받고도 관리 API 를 직접 부르면 거기서 403 이 난다
 */
public record NoticeDetailResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String title,
        String content,
        long viewCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String authorName,
        boolean pinned,
        boolean editable) {
}
