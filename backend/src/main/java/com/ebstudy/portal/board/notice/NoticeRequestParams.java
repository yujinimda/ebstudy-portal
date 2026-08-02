package com.ebstudy.portal.board.notice;

import com.ebstudy.portal.board.common.BoardSearchCriteria;
import com.ebstudy.portal.board.common.BoardType;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * 쿼리 파라미터 → 검증된 값.
 *
 * <p><b>왜 컨트롤러가 {@code Long}·{@code Integer}·{@code OffsetDateTime} 으로 직접 받지 않는가.</b>
 * 스프링이 타입 변환에 실패하면 {@code MethodArgumentTypeMismatchException} 이 나는데
 * {@code common/GlobalExceptionHandler} 에 그 핸들러가 없어 <b>{@code Exception} 으로 떨어져
 * 500</b> 이 된다. {@code /api/notices?page=abc} 하나로 5xx 가 찍히는 것은
 * "잘못된 입력은 4xx" 규칙(001 {@code AC-28} 맥락) 위반이다. 그래서 전부 문자열로 받고
 * <b>여기서</b> 4xx 로 바꾼다. common 을 고칠 수 있게 되면 이 클래스는 줄어든다.
 *
 * <p>기간·개씩보기·정렬 같은 <b>정책</b> 판정은 여기서 하지 않는다 —
 * 그건 {@link BoardSearchCriteria#of} 하나의 몫이다(게시판 4종이 같은 규칙을 공유해야 한다).
 * 이 클래스는 오직 <b>문자열을 타입으로 바꾸는 일</b>만 한다.
 */
final class NoticeRequestParams {

    /**
     * 날짜만 온 경우 어느 시간대의 하루로 볼 것인가.
     *
     * <p>서버 기본 시간대를 쓴다. UTC 로 고정하면 한국 사용자가 "오늘" 을 검색했을 때
     * 오전 9시 이전에 쓴 글이 어제로 밀린다. 반대로 여기서 KST 를 하드코딩하면
     * 배포 지역이 바뀔 때 조용히 어긋난다 — 시간대는 <b>런타임 설정</b>으로 정한다.
     */
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private NoticeRequestParams() {
    }

    /** 요구사항 1.1 목록 조건 전체. 공지사항은 분류를 쓰고 "나의 글" 필터가 없다. */
    static BoardSearchCriteria toCriteria(String from, String to, String categoryId, String keyword,
            String page, String size, String sort, String direction, OffsetDateTime now) {
        return BoardSearchCriteria.of(BoardType.NOTICE,
                parseInstant(from, false),
                parseInstant(to, true),
                parseLong(categoryId),
                keyword,
                // 요구사항 6.1 "나의 문의내역만 보기" 는 문의게시판 전용이다.
                // 공지사항은 관리자만 쓰므로 "내 글만" 이라는 개념 자체가 없다
                false,
                null,
                parseInt(page),
                parseInt(size),
                sort,
                direction,
                now);
    }

    /** 경로 변수의 글 id. 숫자가 아니면 404 가 아니라 400 이다 — 형식이 틀린 요청이다. */
    static Long id(String raw) {
        Long parsed = parseLong(raw);
        if (parsed == null) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        return parsed;
    }

    static Long parseLong(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
    }

    static Integer parseInt(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
    }

    /**
     * 기간 검색의 한쪽 끝.
     *
     * <p>세 가지 표기를 모두 받는다. 화면이 무엇을 보내든 같은 값으로 모이게 하려는 것이다.
     * <ul>
     *   <li>{@code 2026-07-31T00:00:00+09:00} — 시간대까지 온 값. 그대로 쓴다</li>
     *   <li>{@code 2026-07-31T00:00:00} — 서버 시간대로 본다</li>
     *   <li>{@code 2026-07-31} — <b>날짜만.</b> 날짜 선택기가 보내는 가장 흔한 모양이다</li>
     * </ul>
     *
     * @param endOfDay 날짜만 온 경우 하루의 <b>끝</b>으로 볼지. 끝을 {@code 00:00} 으로 잡으면
     *                 {@code from=to=오늘} 검색이 <b>오늘 쓴 글을 한 건도 못 찾는다</b>
     *                 ({@code PostSpecifications} 의 {@code between} 은 양끝 포함이다)
     */
    static OffsetDateTime parseInstant(String raw, boolean endOfDay) {
        if (isBlank(raw)) {
            return null;
        }
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // 다음 표기를 시도한다
        }
        try {
            return LocalDateTime.parse(value).atZone(ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            // 다음 표기를 시도한다
        }
        try {
            LocalDate date = LocalDate.parse(value);
            LocalDateTime moment = endOfDay ? date.atTime(23, 59, 59, 999_999_999)
                    : date.atStartOfDay();
            return moment.atZone(ZONE).toOffsetDateTime();
        } catch (DateTimeParseException ex) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
    }

    private static boolean isBlank(String raw) {
        return raw == null || raw.isBlank();
    }
}
