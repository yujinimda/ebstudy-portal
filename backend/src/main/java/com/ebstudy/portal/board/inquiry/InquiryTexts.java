package com.ebstudy.portal.board.inquiry;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;

/**
 * 제목·내용 검증 — AC-18 · AC-46 · FR-013 · FR-032.
 *
 * <p><b>서버가 한다.</b> 화면 검증은 편의일 뿐이고 화면을 거치지 않은 직접 호출도 같은 코드로
 * 거부된다(001 {@code FR-002} 와 같은 원칙). 등록과 수정이 <b>같은 함수</b>를 부르는 것이
 * 핵심이다 — 두 벌이면 한쪽만 고쳐지고 그 한쪽이 우회로가 된다.
 */
final class InquiryTexts {

    /** 요구사항 1.2 "제목 100자 <b>미만</b>" — DB 쪽 짝은 {@code posts.title VARCHAR(99)}. */
    static final int TITLE_MAX_EXCLUSIVE = 100;
    /** 요구사항 1.2 "내용 4000자 <b>미만</b>" — DB 쪽 짝은 {@code VARCHAR(3999)}. */
    static final int CONTENT_MAX_EXCLUSIVE = 4000;

    private InquiryTexts() {
    }

    static String requireTitle(String raw) {
        String value = trimmed(raw);
        if (value.isEmpty()) {
            throw new ApiException(ErrorCode.POST_TITLE_REQUIRED);
        }
        if (charCount(value) >= TITLE_MAX_EXCLUSIVE) {
            throw new ApiException(ErrorCode.POST_TITLE_LENGTH_INVALID);
        }
        return value;
    }

    static String requireContent(String raw) {
        String value = trimmed(raw);
        if (value.isEmpty()) {
            throw new ApiException(ErrorCode.POST_CONTENT_REQUIRED);
        }
        if (charCount(value) >= CONTENT_MAX_EXCLUSIVE) {
            throw new ApiException(ErrorCode.POST_CONTENT_LENGTH_INVALID);
        }
        return value;
    }

    static String requireAnswerContent(String raw) {
        String value = trimmed(raw);
        if (value.isEmpty()) {
            throw new ApiException(ErrorCode.INQUIRY_ANSWER_CONTENT_REQUIRED);
        }
        if (charCount(value) >= CONTENT_MAX_EXCLUSIVE) {
            throw new ApiException(ErrorCode.INQUIRY_ANSWER_CONTENT_LENGTH_INVALID);
        }
        return value;
    }

    /**
     * 앞뒤 공백을 <b>지운 값을 저장</b>한다.
     *
     * <p>이유 둘: (1) 공백만 넣은 제목이 "필수" 를 통과하면 목록에 빈 줄이 생긴다,
     * (2) 지우지 않으면 "3999자 + 뒤 공백" 이 DB 길이 제약에 걸려 400 이 아니라 500 이 된다.
     * {@code trim()} 은 바깥쪽만 지우므로 <b>본문 안의 줄바꿈·들여쓰기는 그대로 남는다</b>.
     */
    private static String trimmed(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /**
     * 길이는 <b>문자 수</b>로 센다 — 이모지·한글을 바이트로 세면 훨씬 일찍 막힌다
     * (006 Edge Cases · 001 {@code SignupService} 와 같은 규칙).
     * Postgres 의 {@code VARCHAR(n)} 도 문자 수로 세므로 이 값이 DB 제약과 어긋나지 않는다.
     */
    private static int charCount(String value) {
        return value.codePointCount(0, value.length());
    }
}
