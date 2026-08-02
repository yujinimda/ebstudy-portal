import type { NextConfig } from "next";

/**
 * ★ /api/* 를 백엔드로 넘긴다 — research.md 2 · contracts/auth-api.md "오리진".
 *
 * 브라우저는 <b>항상 프론트 오리진의 /api/*</b> 를 호출하고 이 rewrite 가 백엔드로 보낸다.
 * 그래서 오리진이 하나이고 <b>CORS 설정이 아예 없다</b>.
 *
 * 이 구조를 고른 이유는 "로컬에선 되는데 배포에서 깨지는" httpOnly 쿠키 문제를 처음부터
 * 없애기 위해서다 — 오리진이 둘이면 SameSite·Secure·도메인이 전부 변수가 된다.
 *
 * ⚠️ 대가: 백엔드가 보는 원격 주소가 프록시 하나가 된다. AC-30(중복확인 빈도 제한)이
 *    클라이언트 단위 키를 쓰므로 백엔드가 X-Forwarded-For 의 클라이언트 홉을
 *    신뢰 경계와 함께 처리한다(ClientIpResolver).
 */
const BACKEND_ORIGIN = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${BACKEND_ORIGIN}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
