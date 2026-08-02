"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";
import { logout } from "@/lib/api";
import { useSession } from "@/lib/useSession";

/**
 * 헤더 — GNB(요구사항 2장) + 로그인 정보.
 *
 * ⚠️ **AC-26 · FR-019** — 여기서 관리자 링크를 감추는 것은 **권한 검증이 아니다.**
 *   숨기는 이유는 편의일 뿐이고, 실제 차단은 서버가 한다
 *   (`SecurityConfig` 의 `/api/admin/** → hasRole("ADMIN")`).
 *
 * ★ 관리자 화면(`/admin/**`)에서는 GNB 를 그리지 않는다 — 관리자에는 자체 LNB 가 있고
 *   (요구사항 7.1) 둘이 겹치면 어느 쪽이 현재 위치인지 알 수 없다.
 */
const GNB = [
  { href: "/notices", label: "공지사항" },
  { href: "/free", label: "자유 게시판" },
  { href: "/galleries", label: "갤러리" },
  { href: "/inquiries", label: "문의 게시판" },
] as const;

export default function SiteHeader() {
  const session = useSession();
  const router = useRouter();
  const pathname = usePathname();
  const [loggingOut, setLoggingOut] = useState(false);

  const inAdmin = pathname.startsWith("/admin");

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
    } catch {
      // 로그아웃 실패는 사용자가 되돌릴 방법이 없다. 상태만 새로 읽는다
    } finally {
      setLoggingOut(false);
      session.reload();
      router.push("/");
      router.refresh();
    }
  }

  return (
    <header className="site-header">
      <Link href="/" className="brand">
        ebstudy
      </Link>

      {!inAdmin && (
        <nav className="gnb" aria-label="게시판">
          {GNB.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              aria-current={pathname.startsWith(item.href) ? "page" : undefined}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      )}
      {inAdmin && <span className="gnb" />}

      <nav className="account">
        {session.status === "loading" && <span className="hint">…</span>}

        {session.status === "anonymous" && (
          <>
            <Link href="/login">로그인</Link>
            <Link href="/signup">회원가입</Link>
          </>
        )}

        {session.status === "authenticated" && session.user !== null && (
          <>
            <span>
              {session.user.name}님
              {session.user.role === "ADMIN" && " (관리자)"}
            </span>
            <Link href="/mypage">내 정보</Link>
            {/* 편의를 위한 노출 제어일 뿐 — 차단은 서버가 한다(AC-26) */}
            {session.user.role === "ADMIN" && <Link href="/admin">관리자</Link>}
            <button
              type="button"
              className="secondary"
              onClick={handleLogout}
              disabled={loggingOut}
            >
              {loggingOut ? "…" : "로그아웃"}
            </button>
          </>
        )}
      </nav>
    </header>
  );
}
