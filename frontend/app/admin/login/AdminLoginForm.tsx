"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import Alert from "@/components/Alert";
import { adminLogin } from "@/lib/api";
import { safeRedirectPath } from "@/lib/safe-redirect";

/**
 * 계약 4번 — `POST /api/admin/auth/login`.
 *
 * ★ **AC-32** — USER 계정이 **정확한 비밀번호로** 시도해도 실패하고, 그 응답은
 *   AC-2(비밀번호 틀림)·AC-3(아이디 없음)과 **완전히 같다.**
 *   화면도 그 통일을 깨면 안 되므로 여기서 code 별로 문구를 나누지 않는다 —
 *   `Alert` 가 서버의 `detail` 을 그대로 보여준다.
 *
 *   *"관리자 권한이 없습니다"* 라고 답하면 그 아이디가 존재하고 일반 사용자라는 것을
 *   알려주게 되어, AC-3 으로 막은 계정 열거가 이 경로로 새 나간다.
 */
export default function AdminLoginForm() {
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
      await adminLogin(username, password);
      // 기본 목적지는 관리자 홈이다. next 가 있으면 검증 후 그쪽으로(AC-24 · AC-34).
      const next = searchParams.get("next");
      router.push(next === null ? "/admin" : safeRedirectPath(next));
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
          <label htmlFor="admin-username">아이디</label>
          <input
            id="admin-username"
            name="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="admin-password">비밀번호</label>
          <input
            id="admin-password"
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
        관리자 계정의 자격증명 수명은 일반 사용자보다 짧습니다(FR-033 · AC-33).
      </p>
    </>
  );
}
