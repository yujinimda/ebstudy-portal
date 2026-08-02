package com.ebstudy.portal.board.common;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 게시판 설정 — 값은 전부 환경변수다(application.yml 의 {@code ${KEY}}).
 * {@code AuthProperties} 와 같은 방식이다.
 */
@ConfigurationProperties(prefix = "board")
public record BoardProperties(Attachment attachment, NewBadge newBadge) {

    public BoardProperties {
        // 설정이 통째로 비어도 부팅은 되게 하고, 값은 아래 기본값으로 채운다 —
        // 여기서 예외를 던지면 "게시판을 안 쓰는 테스트"까지 설정을 요구하게 된다
        attachment = attachment != null ? attachment : new Attachment(null);
        newBadge = newBadge != null ? newBadge : new NewBadge(0, null);
    }

    /**
     * @param root 저장소 루트. <b>설정값이다</b> — 코드에 경로를 박지 않는다.
     *             실서비스는 영속 볼륨 경로로 덮는다(기본값은 "어디서든 뜬다"용 임시 디렉터리)
     */
    public record Attachment(String root) {
    }

    /**
     * 요구사항 1.1 {@code new} 아이콘 — <i>"생성 후 7일 이내. 게시판별로 다를 수 있게
     * 설정 가능해야 한다"</i>.
     *
     * @param defaultDays 기본 일수
     * @param byBoard     게시판별 덮어쓰기. 키는 {@code NOTICE}·{@code FREE}·{@code GALLERY}·
     *                    {@code INQUIRY}. 문자열로 받는 이유는 잘못된 키가 <b>부팅을 깨지 않고</b>
     *                    무시되게 하기 위해서다 — 오타 하나로 서비스가 안 뜨는 쪽이 더 나쁘다
     */
    public record NewBadge(int defaultDays, Map<String, Integer> byBoard) {

        public NewBadge {
            defaultDays = defaultDays > 0 ? defaultDays : 7;
            byBoard = byBoard != null ? Map.copyOf(byBoard) : Map.of();
        }
    }
}
