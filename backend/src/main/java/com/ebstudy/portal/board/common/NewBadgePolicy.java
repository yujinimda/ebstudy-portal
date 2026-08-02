package com.ebstudy.portal.board.common;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@code new} 아이콘 — 요구사항 1.1 <i>"생성 후 7일 이내. 게시판별로 다를 수 있게 설정 가능"</i>.
 *
 * <p>판정을 <b>서버가</b> 하는 이유: 화면에서 계산하면 사용자 기기의 시계가 기준이 된다.
 * 시계가 틀어진 기기에서는 {@code new} 가 안 붙거나 계속 붙는다.
 */
@Component
@Slf4j
public class NewBadgePolicy {

    private final int defaultDays;
    private final Map<BoardType, Integer> daysByBoard;

    public NewBadgePolicy(BoardProperties properties) {
        this.defaultDays = properties.newBadge().defaultDays();
        this.daysByBoard = new HashMap<>();
        properties.newBadge().byBoard().forEach((key, value) -> {
            try {
                if (value != null && value > 0) {
                    daysByBoard.put(BoardType.valueOf(key.trim().toUpperCase(Locale.ROOT)), value);
                }
            } catch (IllegalArgumentException ex) {
                // 설정 오타 하나로 서비스가 안 뜨는 쪽이 더 나쁘다 → 무시하고 로그만 남긴다
                log.warn("board.new-badge.by-board 의 알 수 없는 게시판 키 key={}", key);
            }
        });
    }

    public int days(BoardType boardType) {
        return daysByBoard.getOrDefault(boardType, defaultDays);
    }

    /** {@code now} 를 인자로 받는 이유는 테스트가 시간을 고정할 수 있어야 하기 때문이다. */
    public boolean isNew(BoardType boardType, OffsetDateTime createdAt, OffsetDateTime now) {
        if (createdAt == null || now == null) {
            return false;
        }
        return createdAt.isAfter(now.minusDays(days(boardType)));
    }
}
