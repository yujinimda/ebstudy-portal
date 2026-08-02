package com.ebstudy.portal.board.gallery;

import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 쿼리 파라미터를 <b>직접</b> 문자열에서 바꾼다.
 *
 * <p>왜 {@code @RequestParam Long page} 처럼 타입으로 받지 않는가:
 * {@code ?page=abc} 는 스프링이 {@code MethodArgumentTypeMismatchException} 을 던지고,
 * 그것은 {@code GlobalExceptionHandler} 의 마지막 {@code Exception} 핸들러로 떨어져
 * <b>500</b> 이 된다({@code AC-28} 은 5xx 에 이유를 담는 것을 금지하므로 사용자는
 * 무엇이 틀렸는지도 알 수 없다). 잘못된 입력은 4xx 여야 한다.
 * {@code common/} 에 변환 예외 핸들러를 추가하는 것이 정공법이지만 그 패키지는 손대지 않는다
 * (병렬 작업 경계) → 받는 자리에서 문자열로 받아 여기서 바꾼다.
 */
final class GalleryQueryParams {

    private GalleryQueryParams() {
    }

    static Long optionalLong(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
    }

    static Integer optionalInt(String raw) {
        Long value = optionalLong(raw);
        if (value == null) {
            return null;
        }
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
        return value.intValue();
    }

    /**
     * 기간 검색의 한쪽 끝 — 요구사항 1.1.
     *
     * <p>{@code 2026-07-01} 처럼 <b>날짜만</b> 온 경우를 특별히 다룬다. 화면의 날짜 선택기는
     * 시각을 주지 않는데, 끝날짜를 그날 {@code 00:00} 으로 잡으면
     * <b>그날 등록된 글이 전부 빠진다</b>. 그래서 끝은 그날의 마지막 순간으로 민다.
     *
     * @param endOfDay 끝날짜({@code to})면 {@code true}
     */
    static OffsetDateTime optionalDateTime(String raw, boolean endOfDay) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // 오프셋이 없는 형식도 받는다 — 화면이 로컬 시각을 그대로 보내는 경우
        }
        try {
            return LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            // 날짜만 온 경우로 넘어간다
        }
        try {
            LocalDate date = LocalDate.parse(value);
            // 마이크로초로 잘라 둔다 — Postgres timestamptz 는 마이크로초까지만 담아
            // 23:59:59.999999999 를 반올림하면 <b>다음 날 00:00</b> 이 되어 하루가 더 들어온다
            LocalTime time = endOfDay
                    ? LocalTime.MAX.truncatedTo(ChronoUnit.MICROS)
                    : LocalTime.MIDNIGHT;
            return date.atTime(time).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        } catch (DateTimeParseException ex) {
            throw new ApiException(ErrorCode.REQUEST_INVALID);
        }
    }

    static Boolean optionalBoolean(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        if (value.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (value.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        throw new ApiException(ErrorCode.REQUEST_INVALID);
    }

    /**
     * 수정 시 "남길 이미지" 목록 — 쉼표로 이은 id 다.
     *
     * <p>★ <b>값이 없는 것</b>({@code null})과 <b>빈 목록</b>({@code ""})을 구분해야 해서
     * 반복 파라미터가 아니라 문자열 하나로 받는다. {@code multipart/form-data} 에서는
     * "빈 목록"을 반복 파라미터로 표현할 방법이 없다 — 파라미터를 아예 안 보내는 것과 같아진다.
     * 화면은 <b>항상</b> 이 값을 보낸다({@code ids.join(',')}, 없으면 빈 문자열).
     *
     * @return {@code null} = 손대지 않음(전부 유지) · 빈 목록 = 기존 이미지를 전부 뺀다
     */
    static List<Long> optionalLongList(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String token : value.split(",", -1)) {
            Long id = optionalLong(token);
            if (id == null) {
                throw new ApiException(ErrorCode.REQUEST_INVALID);
            }
            ids.add(id);
        }
        return ids;
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
