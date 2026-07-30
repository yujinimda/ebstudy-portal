package com.ebstudy.portal.auth.ratelimit;

import com.ebstudy.portal.auth.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ★ 프록시 뒤에서 "요청 출처 주소"가 무엇인지 정한다(research.md 5).
 *
 * <p>Next.js rewrite 로 백엔드를 프록시하므로 원격 주소는 <b>프록시 하나</b>다. 그대로 쓰면
 * 중복확인 빈도 제한이 전 사용자를 한 키로 묶어 <b>방어가 아니라 장애</b>가 된다.
 *
 * <p>그래서 {@code X-Forwarded-For} 의 클라이언트 홉을 쓰되, <b>신뢰할 프록시를 명시적으로
 * 지정</b>한다. 지정 없이 헤더를 신뢰하면 공격자가 헤더를 위조해 제한을 무력화한다.
 */
@Component
public class ClientIpResolver {

    private final List<Cidr> trusted;

    public ClientIpResolver(AuthProperties properties) {
        List<Cidr> parsed = new ArrayList<>();
        List<String> configured = properties.trustedProxyCidrs();
        if (configured != null) {
            for (String cidr : configured) {
                if (cidr != null && !cidr.isBlank()) {
                    Cidr.parse(cidr.trim()).ifPresent(parsed::add);
                }
            }
        }
        this.trusted = List.copyOf(parsed);
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote == null) {
            return "unknown";
        }
        if (!isTrusted(remote)) {
            // 신뢰하지 않는 곳에서 직접 온 요청 — 헤더를 믿지 않는다
            return remote;
        }
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isBlank()) {
            return remote;
        }
        String[] hops = header.split(",");
        // 오른쪽(우리에게 가까운 쪽)부터 신뢰하는 프록시를 건너뛰고, 처음 만나는 값이 클라이언트다
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (hop.isEmpty()) {
                continue;
            }
            if (!isTrusted(hop)) {
                return hop;
            }
        }
        return remote;
    }

    private boolean isTrusted(String address) {
        if (trusted.isEmpty()) {
            return false;
        }
        try {
            byte[] candidate = InetAddress.getByName(address).getAddress();
            for (Cidr cidr : trusted) {
                if (cidr.contains(candidate)) {
                    return true;
                }
            }
        } catch (UnknownHostException ex) {
            return false;
        }
        return false;
    }

    private record Cidr(byte[] network, int prefixBits) {

        static java.util.Optional<Cidr> parse(String value) {
            try {
                String[] parts = value.split("/");
                byte[] address = InetAddress.getByName(parts[0]).getAddress();
                int bits = parts.length > 1 ? Integer.parseInt(parts[1]) : address.length * 8;
                return java.util.Optional.of(new Cidr(address, bits));
            } catch (UnknownHostException | NumberFormatException ex) {
                return java.util.Optional.empty();
            }
        }

        boolean contains(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remaining = prefixBits % 8;
            if (remaining == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remaining);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
