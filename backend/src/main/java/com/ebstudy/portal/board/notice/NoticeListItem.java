package com.ebstudy.portal.board.notice;

import java.time.OffsetDateTime;

/**
 * 공지사항 목록 한 줄 — 요구사항 3.1.
 *
 * <p>엔티티를 그대로 내보내지 않는다. 특히 {@code Post} 에는 문의게시판의
 * {@code secretPasswordHash} 가 들어 있어 그대로 직렬화하면 해시가 밖으로 나간다.
 *
 * @param displayNumber 요구사항 1.1 "전체 게시글 수 기준 역순". <b>고정 글은 {@code null}</b> —
 *                      요구사항 3.1 이 <i>"고정된 글은 번호 대신 분류명(알림)을 보여준다"</i> 로
 *                      정했다. 번호를 주면 화면이 그걸 그려 버리므로 <b>서버가 아예 주지 않는다</b>.
 *                      숫자가 아닌 표기는 화면이 {@code pinned} 를 보고 결정한다
 * @param isNew         요구사항 1.1 {@code new} 아이콘. 서버가 판정한다 —
 *                      화면에서 계산하면 사용자 기기의 시계가 기준이 된다
 * @param pinned        요구사항 3.1 알림글. 화면이 배경색으로 구분한다
 */
public record NoticeListItem(
        Long id,
        Long displayNumber,
        Long categoryId,
        String categoryName,
        String title,
        long viewCount,
        OffsetDateTime createdAt,
        String authorName,
        boolean pinned,
        boolean isNew) {
}
