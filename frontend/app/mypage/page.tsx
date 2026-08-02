"use client";

import RequireAuth from "@/components/RequireAuth";

/**
 * 계약 7번 — `GET /api/me`. **인증이 필요한 화면**.
 *
 * 스텁이 아니다(research.md 12): AC-24(목적지 복귀)와 AC-4(자동 재발급)를 사람 눈으로
 * 확인하는 자리다. Access 수명(30분)이 지난 뒤 새로고침해도 **재로그인을 요구받지 않으면**
 * AC-4 가 화면 층까지 성립한 것이다.
 */
export default function MyPage() {
  return (
    <main>
      <h1>내 정보</h1>
      <p className="subtitle">로그인해야 볼 수 있는 화면입니다.</p>

      <RequireAuth>
        {(user) => (
          <>
            <dl className="info">
              <dt>아이디</dt>
              <dd>{user.username}</dd>
              <dt>이름</dt>
              <dd>{user.name}</dd>
              <dt>권한</dt>
              <dd>{user.role}</dd>
            </dl>

            <p className="note">
              이 화면은 <code>GET /api/me</code> 를 부릅니다. Access 쿠키가 만료돼도
              프론트가 <code>POST /api/auth/refresh</code> 를 한 번 부른 뒤 재시도하므로
              (AC-4) 다시 로그인하라는 말이 나오지 않습니다.
            </p>
          </>
        )}
      </RequireAuth>
    </main>
  );
}
