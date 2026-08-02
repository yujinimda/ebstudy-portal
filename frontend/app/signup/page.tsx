"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import Alert from "@/components/Alert";
import { ApiError, checkId, signup } from "@/lib/api";

/**
 * 계약 1번 · 2번 — 회원가입 + 중복확인.
 *
 * ★ **FR-002 · AC-19 — 화면 검증은 편의일 뿐이다.**
 *   여기서 길이·형식을 미리 막지 **않는다.** 서버가 방어선이고, 화면을 거치지 않은
 *   직접 호출도 같은 코드로 거부돼야 한다. 화면에서도 막으면 규칙이 두 곳에 생기고
 *   둘이 어긋나는 순간 "화면은 통과했는데 서버가 거부" 가 된다.
 *   → 그래서 `required` 외에는 클라이언트 검증을 두지 않고, 규칙은 hint 로만 안내한다.
 *
 * ★ AC-20 — 중복확인에서 "사용 가능"이라고 답한 아이디는 가입에서도 통과해야 한다.
 *   두 판정이 같은 서버 로직(`SignupService`)을 쓰므로 어긋나지 않는다.
 */
export default function SignupPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [checkResult, setCheckResult] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleCheckId() {
    setError(null);
    setCheckResult(null);
    setChecking(true);
    try {
      const { available } = await checkId(username);
      setCheckResult(available ? "사용할 수 있는 아이디입니다" : "이미 사용 중이거나 사용할 수 없는 아이디입니다");
    } catch (caught) {
      // AC-30 — 분당 10회를 넘으면 429 CHECK_ID_TOO_MANY_REQUESTS 가 온다.
      setError(caught);
    } finally {
      setChecking(false);
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await signup(username, password, name);
      // 가입은 로그인시키지 않는다(계약 1번은 쿠키를 주지 않는다).
      router.push("/login?signup=done");
    } catch (caught) {
      setError(caught);
      setSubmitting(false);
    }
  }

  return (
    <main>
      <h1>회원가입</h1>
      <p className="subtitle">모든 검증은 서버가 합니다. 아래 안내는 참고용입니다.</p>

      <form onSubmit={handleSubmit}>
        <Alert error={error} />
        {checkResult !== null && error == null && (
          <p
            className={
              checkResult.startsWith("사용할 수 있는") ? "alert ok" : "alert error"
            }
            role="status"
          >
            {checkResult}
          </p>
        )}

        <div className="field">
          <label htmlFor="username">아이디</label>
          <div className="row">
            <input
              id="username"
              name="username"
              autoComplete="username"
              value={username}
              onChange={(e) => {
                setUsername(e.target.value);
                setCheckResult(null);
              }}
              required
            />
            <button
              type="button"
              className="secondary"
              onClick={handleCheckId}
              disabled={checking || username === ""}
            >
              {checking ? "확인 중…" : "중복확인"}
            </button>
          </div>
          <span className="hint">4자 이상 12자 미만 · 영문·숫자와 -, _ 만</span>
        </div>

        <div className="field">
          <label htmlFor="password">비밀번호</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <span className="hint">
            8자 이상 64자 이하 · 아이디를 포함할 수 없음 · 같은 문자 3회 연속 불가
          </span>
        </div>

        <div className="field">
          <label htmlFor="name">이름</label>
          <input
            id="name"
            name="name"
            autoComplete="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
          <span className="hint">2자 이상 50자 이하</span>
        </div>

        <button type="submit" disabled={submitting}>
          {submitting ? "가입 중…" : "가입하기"}
        </button>
      </form>

      <p className="note">
        이미 계정이 있으면 <Link href="/login">로그인</Link>.
        {error instanceof ApiError && error.code === "USER_ID_DUPLICATED" && (
          <> 아이디가 중복됐다면 다른 값을 시도해 주세요.</>
        )}
      </p>
    </main>
  );
}
