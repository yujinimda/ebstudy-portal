"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import Alert from "@/components/Alert";
import { login } from "@/lib/api";
import { safeRedirectPath } from "@/lib/safe-redirect";

/**
 * 계약 3번 — `POST /api/auth/login`.
 *
 * ★ AC-24 목적지 복귀 — 미인증으로 보호된 화면에 들어가면 `/login?next=/mypage` 로 오고,
 *   로그인에 성공하면 홈이 아니라 **원래 가려던 곳**으로 보낸다.
 *
 * ★ AC-34 목적지 검증 — 그 값을 **그대로 믿지 않는다.** `safeRedirectPath` 를 통과한
 *   값만 쓴다. 안 하면 오픈 리다이렉트가 되어 우리 로그인 화면을 거쳐 공격자 사이트로
 *   보내는 피싱 링크가 만들어진다.
 *
 * ★ ADMIN 계정도 이 진입점으로 로그인된다(계약 3번 주석 — FR-017 하나의 인증 체계).
 *   발급되는 수명은 진입점이 아니라 **계정의 역할** 기준이다(FR-033).
 */
export default function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username, password);
      // ★ 검증을 거친 경로만 쓴다(AC-34).
      const destination = safeRedirectPath(searchParams.get("next"));
      router.push(destination);
      // 헤더의 로그인 상태를 다시 읽게 한다.
      router.refresh();
    } catch (caught) {
      setError(caught);
      setSubmitting(false);
    }
  }

  return (
    <>
      <form onSubmit={handleSubmit}>
        <Alert error={error} />

        <div className="field">
          <label htmlFor="username">아이디</label>
          <input
            id="username"
            name="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>

        <button type="submit" disabled={submitting}>
          {submitting ? "로그인 중…" : "로그인"}
        </button>
      </form>

      <p className="note">
        계정이 없으면 <Link href="/signup">회원가입</Link>. 관리자는{" "}
        <Link href="/admin/login">별도 진입점</Link>을 씁니다.
      </p>
    </>
  );
}
