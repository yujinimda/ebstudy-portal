package com.ebstudy.portal.board.inquiry;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 비밀글 <b>열람 통과</b> — FR-025 · AC-37.
 *
 * <p>비밀번호를 맞힌 뒤 새로고침·뒤로가기마다 4자리를 다시 치게 하면 사용자는
 * <b>기억하기 쉬운 값</b>(1111 같은)을 고른다. 그래서 통과를 재사용하되 두 가지로 좁힌다:
 * <ul>
 *   <li><b>글 하나에만</b> 적용된다 — A 의 통과가 B 로 번지면 <b>한 글만 뚫려도 전부 열린다</b></li>
 *   <li><b>유효 시간이 지나면 만료</b>된다(기본 30분 — 001 사용자 Access 수명과 같은 크기)</li>
 * </ul>
 *
 * <h2>왜 "서버가 들고 있는 임의 토큰" 인가</h2>
 * FR-025 는 통과 상태를 <b>서버가 관리해야 한다</b>고 못박았다. 그런데 상태를 서버에만 두고
 * 요청자를 <b>요청 출처 주소</b>로 식별하면, 같은 NAT 뒤에 있는 <b>남</b>이 그 통과를 물려받는다.
 * 그래서 통과의 사실은 서버가 들고(=서버 관리), 요청자에게는 <b>추측 불가능한 임의 토큰</b>
 * 하나를 건네 다음 요청에 다시 가져오게 한다. 토큰은 <b>서명된 값이 아니다</b> —
 * 서버 저장소에 없으면 그 자체로 무효라, 위조·만료 설계를 따로 할 필요가 없다.
 *
 * <p>토큰을 URL 이 아니라 {@code X-Inquiry-Grant} 헤더로 받는 것도 의도다.
 * 질의 문자열은 접근 로그·리퍼러·북마크에 그대로 남는다.
 *
 * <p>⚠️ 통과 기록도 <b>프로세스 메모리</b>에 있다 — 인스턴스를 늘리면 다른 인스턴스로 간 요청이
 * 다시 비밀번호를 묻는다(006 spec ADR 후보 3). 뚫리는 방향이 아니라 <b>더 묻는</b> 방향의
 * 열화라 안전한 쪽으로 틀렸다.
 */
@Service
public class SecretReadGrantService {

    /** 추측 가능한 토큰은 통과 그 자체다 → {@code SecureRandom} 만 쓴다. */
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final BoundedCache<String, Grant> grants;
    private final Duration ttl;

    public SecretReadGrantService(
            @Value("${board.inquiry.secret.grant-ttl:PT30M}") Duration ttl,
            @Value("${board.inquiry.secret.grant-max-entries:10000}") int maxEntries) {
        this.ttl = ttl;
        this.grants = new BoundedCache<>(maxEntries);
    }

    /** 비밀번호를 통과했을 때만 부른다. */
    public String issue(Long postId, Instant now) {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        grants.put(token, new Grant(postId, now.plus(ttl)));
        return token;
    }

    /**
     * ★ 이 글에 대한 통과인지까지 확인한다 — {@code postId} 비교를 빼면 FR-025 가 금지한
     * "다른 글로 확장" 이 그 자리에서 일어난다.
     */
    public boolean isGranted(Long postId, String token, Instant now) {
        if (postId == null || token == null || token.isBlank()) {
            return false;
        }
        Grant grant = grants.get(token);
        if (grant == null) {
            return false;
        }
        if (!grant.expiresAt().isAfter(now)) {
            grants.remove(token);
            return false;
        }
        return postId.equals(grant.postId());
    }

    /**
     * 그 글의 통과를 전부 회수한다.
     *
     * <p>비밀번호를 바꾸거나 비밀글을 해제·삭제하면 부른다. 부르지 않으면
     * <b>비밀번호를 바꿔도 이미 통과한 사람은 계속 읽는다</b> — 비밀번호를 바꾸는 이유가
     * 대개 "그 사람에게서 거두고 싶다" 인데 그게 안 되는 셈이 된다.
     */
    public void revokePost(Long postId) {
        grants.removeIf((token, grant) -> grant.postId().equals(postId));
    }

    public void clearAll() {
        grants.clear();
    }

    private record Grant(Long postId, Instant expiresAt) {
    }
}
