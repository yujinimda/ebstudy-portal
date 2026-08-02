"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useSession } from "@/lib/useSession";
import type { UserResponse } from "@/lib/api";

/**
 * ★ FR-020 · AC-24 — 미인증으로 보호된 화면에 들어오면 로그인 화면으로 보내고,
 *   **원래 목적지를 `?next=` 로 넘긴다.** 로그인 후 홈이 아니라 그곳으로 돌아간다.
 *
 * ⚠️ **이것은 권한 검증이 아니다**(FR-019 · AC-26). 화면 이동은 편의이고,
 *   실제 차단은 서버가 한다. 주소창에 직접 쳐서 들어와도 데이터는 API 가 막는다 —
 *   `/api/admin/me` 는 USER 권한에 403 을 준다.
 *
 * 목적지는 **지금 브라우저가 있는 경로**에서 만든다. 사용자가 준 값이 아니므로
 * 이 시점에는 안전하고, 검증은 받는 쪽(`LoginForm`)이 `safeRedirectPath` 로 한다 —
 * 링크는 누구나 만들 수 있으니 **믿는 자리를 받는 쪽에 둔다.**
 */
export default function RequireAuth({
  children,
  loginPath = "/login",
}: {
  children: (user: UserResponse) => ReactNode;
  /** 관리자 화면은 별도 진입점으로 보낸다(FR-032). */
  loginPath?: string;
}) {
  const session = useSession();
  const router = useRouter();

  useEffect(() => {
    if (session.status !== "anonymous") return;
    const here = `${window.location.pathname}${window.location.search}`;
    router.replace(`${loginPath}?next=${encodeURIComponent(here)}`);
  }, [session.status, router, loginPath]);

  if (session.status === "loading") {
    return <p className="hint">불러오는 중…</p>;
  }
  if (session.status === "anonymous" || session.user === null) {
    return <p className="hint">로그인 화면으로 이동합니다…</p>;
  }
  return <>{children(session.user)}</>;
}
