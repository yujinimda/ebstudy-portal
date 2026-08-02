package com.ebstudy.portal.board.inquiry;

import com.ebstudy.portal.auth.ratelimit.Delayer;
import com.ebstudy.portal.auth.ratelimit.LoginDelayPolicy;
import com.ebstudy.portal.common.ApiException;
import com.ebstudy.portal.common.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ★★ 비밀글 4자리 무차별 대입 방어 — FR-023 · FR-024 · AC-34 · AC-35 · AC-36.
 *
 * <p><b>이 클래스가 없으면 비밀글은 비밀이 아니다.</b> 숫자 4자리는 경우의 수가 <b>10,000</b>이라
 * 초당 20회만 시도해도 10분이 안 걸린다. 해시로 저장하는 것({@code SecretPostPassword})은
 * <b>DB 가 유출됐을 때의 최소 조치</b>일 뿐이고 — 1만 번이면 오프라인에서 전부 시도된다 —
 * <b>실제 방어선은 온라인 시도 제한인 여기</b>다.
 *
 * <h2>두 축을 모두 센다 (FR-023)</h2>
 * <ul>
 *   <li><b>글 단위</b> — 한 글을 집중 공략하는 것을 막는다. 이것만 있으면 공격자가
 *       글을 번갈아 가며 시도해 그대로 우회한다</li>
 *   <li><b>클라이언트 단위</b> — 글을 돌려가며 시도하는 것을 막는다. 이것만 있으면
 *       여러 곳에서 한 글을 노리는 시도를 못 막는다</li>
 * </ul>
 *
 * <h2>왜 글 단위 "차단"이 여기서는 허용되는가 (AC-36)</h2>
 * 001 은 계정 잠금을 <b>기각</b>했다 — 아이디만 아는 공격자가 주인을 서비스에서 쫓아낼 수
 * 있기 때문이다. 여기서 글 단위 차단이 성립하는 이유는 <b>주인과 관리자가 이 경로를 쓰지
 * 않기 때문</b>이다(FR-020 — 작성자·관리자는 비밀번호를 묻지 않는다).
 * 차단으로 불편해지는 사람은 <i>작성자가 값을 알려준 제3자</i> 뿐이고, 그 통로는 부수적이다.
 * <b>같은 규칙을 다르게 적용하는 것이 아니라, 막았을 때 누가 피해를 보는지가 다르다.</b>
 *
 * <h2>알고 받는 한계</h2>
 * <ul>
 *   <li>카운터가 <b>프로세스 메모리</b>에 있다 → 인스턴스를 늘리면 제한이 인스턴스 수만큼
 *       느슨해지고 재시작으로 초기화된다. 006 spec 의 ADR 후보 2 가 결정할 항목이다</li>
 *   <li>주소를 바꿔 가며 하는 <b>분산 시도</b>는 애플리케이션에서 막지 못한다 — 인프라 몫
 *       (001 이 그은 것과 같은 경계)</li>
 * </ul>
 *
 * <p>★ AC-40 — <b>비밀번호 원문도 해시도 로그에 남기지 않는다.</b> 이 클래스는 애초에 둘 다
 * 받지 않는다(글 번호와 실패 횟수만 안다). 실패 경로의 디버깅 로그 한 줄이 곧 비밀글의 열쇠다.
 */
@Service
@Slf4j
public class SecretPasswordAttemptService {

    private final Delayer delayer;
    private final BoundedCache<String, Attempt> perPost;
    private final BoundedCache<String, Attempt> perClient;

    private final int postDelayThreshold;
    private final int postBlockThreshold;
    private final int clientBlockThreshold;
    private final Duration window;
    private final Duration blockDuration;

    /**
     * 값은 전부 설정이다 — 코드에 박으면 조정할 때마다 배포가 필요하다.
     * {@code application.yml} 은 이 단계에서 손대지 않았으므로(다른 에이전트와 동시 작업)
     * <b>기본값을 여기 둔다</b>. 환경변수로 바로 덮을 수 있다.
     *
     * <p>기본값 선정 근거 — 006 spec Assumptions 2 는 <i>"1분 창"</i>을 가정했으나
     * <b>10분 창으로 잡았다</b>. 1분 창이면 카운터가 창마다 0으로 돌아가서, 점진적 지연으로
     * 시도 간격이 벌어지는 순간 <b>차단에 닿기 전에 창이 갱신되어 영원히 차단되지 않는다</b>
     * (지연 8회차 32초 → 9회차 64초 &gt; 1분 창). 그러면 SC-003 이 무너진다.
     * 10분 창 + 임계 10회 + 차단 10분이면 지속 가능한 시도 속도가 시간당 60회 수준으로 떨어져
     * 1만 가지를 훑는 데 <b>일 단위</b>가 걸린다. AC-34·AC-35 는 값이 아니라 동작만 규정하므로
     * 이 변경으로 AC 는 바뀌지 않는다.
     */
    public SecretPasswordAttemptService(Delayer delayer,
            @Value("${board.inquiry.secret.post-delay-threshold:3}") int postDelayThreshold,
            @Value("${board.inquiry.secret.post-block-threshold:10}") int postBlockThreshold,
            @Value("${board.inquiry.secret.client-block-threshold:30}") int clientBlockThreshold,
            @Value("${board.inquiry.secret.window:PT10M}") Duration window,
            @Value("${board.inquiry.secret.block-duration:PT10M}") Duration blockDuration,
            @Value("${board.inquiry.secret.cache-max-entries:10000}") int cacheMaxEntries) {
        this.delayer = delayer;
        this.postDelayThreshold = postDelayThreshold;
        this.postBlockThreshold = postBlockThreshold;
        this.clientBlockThreshold = clientBlockThreshold;
        this.window = window;
        this.blockDuration = blockDuration;
        this.perPost = new BoundedCache<>(cacheMaxEntries);
        this.perClient = new BoundedCache<>(cacheMaxEntries);
    }

    /**
     * 비밀번호를 <b>대조하기 전에</b> 부른다.
     *
     * <ol>
     *   <li>두 축 중 하나라도 차단 중이면 즉시 429 — 차단된 요청까지 붙잡고 있을 이유가 없고,
     *       무엇보다 <b>bcrypt 대조에 도달하지 않는다</b>(느린 해시를 CPU 소모 수단으로
     *       쓰는 것을 막는다)</li>
     *   <li>아니면 <b>지금까지 쌓인 실패 횟수만큼</b> 지연한다. "이번 실패를 기록한 뒤 지연"
     *       이 아니라 "직전까지의 실패로 이번을 지연" 인 이유는, 지연이 반드시
     *       <b>트랜잭션 밖</b>에서 일어나야 하기 때문이다({@link Delayer} 주석 — 안에서 자면
     *       DB 커넥션과 잠금을 붙잡는다). 진입 직후로 옮기면 그 조건이 구조적으로 지켜진다.
     *       공격자가 치르는 누적 비용은 같다</li>
     * </ol>
     */
    public void throttle(Long postId, String clientKey, Instant now) {
        Attempt post = perPost.get(postKey(postId));
        Attempt client = perClient.get(clientKey(clientKey));
        if ((post != null && post.isBlocked(now)) || (client != null && client.isBlocked(now))) {
            throw new ApiException(ErrorCode.SECRET_PASSWORD_TOO_MANY_ATTEMPTS);
        }
        int failures = post == null ? 0 : post.countWithin(now, window);
        delayer.delay(LoginDelayPolicy.delayMillis(failures, postDelayThreshold, postBlockThreshold));
    }

    /**
     * 실패를 두 축에 기록한다.
     *
     * @return 글 단위로 <b>남은 시도 횟수</b>(판단 9 — 응답에 실어 준다). 0 이면 다음은 차단이다
     */
    public int recordFailure(Long postId, String clientKey, Instant now) {
        int postCount = perPost.computeIfAbsent(postKey(postId), key -> new Attempt())
                .fail(now, window, postBlockThreshold, blockDuration);
        int clientCount = perClient.computeIfAbsent(clientKey(clientKey), key -> new Attempt())
                .fail(now, window, clientBlockThreshold, blockDuration);
        // ★ AC-40 — 남기는 것은 글 번호와 횟수뿐이다. 입력값도 해시도 이 메서드에 들어오지 않는다.
        log.warn("문의 비밀글 비밀번호 실패 postId={} postFail={} clientFail={}",
                postId, postCount, clientCount);
        return Math.max(postBlockThreshold - postCount, 0);
    }

    /** 열람에 성공하면 <b>즉시 초기화</b>한다 — 정상 사용자가 다음 방문에서 벌을 받지 않게. */
    public void reset(Long postId, String clientKey) {
        perPost.remove(postKey(postId));
        perClient.remove(clientKey(clientKey));
    }

    public void clearAll() {
        perPost.clear();
        perClient.clear();
    }

    private static String postKey(Long postId) {
        return "post:" + postId;
    }

    private static String clientKey(String clientKey) {
        return "client:" + (clientKey == null ? "unknown" : clientKey);
    }

    /** 001 {@code LoginAttemptService.Attempt} 와 같은 구조다. 동시 실패는 <b>넘겨 세는</b> 쪽으로 둔다. */
    private static final class Attempt {

        private int count;
        private Instant lastFailure;
        private Instant blockedUntil;

        synchronized boolean isBlocked(Instant now) {
            return blockedUntil != null && blockedUntil.isAfter(now);
        }

        /** 창이 지났으면 0 — 실패가 멈추면 카운터도 식는다. */
        synchronized int countWithin(Instant now, Duration window) {
            if (lastFailure == null || Duration.between(lastFailure, now).compareTo(window) > 0) {
                return 0;
            }
            return count;
        }

        synchronized int fail(Instant now, Duration window, int blockThreshold,
                Duration blockDuration) {
            if (lastFailure == null || Duration.between(lastFailure, now).compareTo(window) > 0) {
                count = 0;
                blockedUntil = null;
            }
            count++;
            lastFailure = now;
            if (count >= blockThreshold) {
                // 006 Edge Cases — 경합으로 임계를 "넘겨서" 세는 것은 허용한다(더 빨리 막히는 쪽).
                // 덜 세는 것은 허용하지 않는다
                blockedUntil = now.plus(blockDuration);
            }
            return count;
        }
    }
}
