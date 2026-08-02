"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import Alert from "@/components/Alert";
import RequireAuth from "@/components/RequireAuth";
import { logout, type UserResponse } from "@/lib/api";
import { useBoardList } from "@/lib/board/useBoardList";
import styles from "./admin.module.css";

/**
 * 관리자 셸 — 요구사항 7.1 (좌측 LNB · 우측 화면 · 상단 우측 인사말/로그아웃).
 *
 * ★ **미인증 차단이 두 겹이다.**
 *   1. `RequireAuth` 가 세션이 없으면 `/admin/login` 으로 보낸다 — **편의**다
 *   2. `GET /api/admin/me` 가 ADMIN 이 아니면 403 을 준다 — **이쪽이 검증**이다
 *   USER 계정으로 주소창에 `/admin/notices` 를 쳐도 1번은 통과한다(로그인은 했으니까).
 *   그때 화면이 열리지 않는 이유는 2번이 403 을 주기 때문이고, 그 문구를 그대로 보여준다.
 *   (요구사항 1.3 · 001 FR-019 · AC-26 — 화면에서 숨기는 것은 권한 검증이 아니다)
 *
 * 인사말에 쓸 이름도 같은 응답에서 얻는다 — 권한 확인과 이름 조회를 두 번 하지 않는다.
 */
const MENU = [
  { href: "/admin/notices", label: "공지사항 관리" },
  { href: "/admin/free-posts", label: "자유 게시판 관리" },
  { href: "/admin/galleries", label: "갤러리 게시판 관리" },
  { href: "/admin/inquiries", label: "문의 게시판 관리" },
  { href: "/admin/categories", label: "분류(카테고리) 관리" },
] as const;

function AdminFrame({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [loggingOut, setLoggingOut] = useState(false);

  // 권한 확인 + 이름. `useBoardList` 는 GET 전용 페칭 훅이라 그대로 쓸 수 있다
  const { data: admin, loading, error } = useBoardList<UserResponse>("/api/admin/me");

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await logout();
    } catch {
      // 실패해도 사용자가 되돌릴 방법이 없다. 관리자 로그인 화면으로 보낸다
    } finally {
      router.push("/admin/login");
      router.refresh();
    }
  }

  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <p className={styles.sidebarTitle}>관리자</p>
        <nav className={styles.nav} aria-label="관리 메뉴">
          <Link
            href="/admin"
            className={
              pathname === "/admin" ? `${styles.navLink} ${styles.navLinkActive}` : styles.navLink
            }
          >
            관리자 홈
          </Link>
          {MENU.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className={
                pathname.startsWith(item.href)
                  ? `${styles.navLink} ${styles.navLinkActive}`
                  : styles.navLink
              }
              aria-current={pathname.startsWith(item.href) ? "page" : undefined}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </aside>

      <main className={styles.content}>
        <div className={styles.topbar}>
          {admin !== null && <span>{admin.name}님 안녕하세요!</span>}
          <button
            type="button"
            className={`secondary ${styles.smallButton}`}
            onClick={handleLogout}
            disabled={loggingOut}
          >
            {loggingOut ? "처리 중…" : "로그아웃"}
          </button>
        </div>

        {error !== null ? (
          <>
            <Alert error={error} />
            <p className="note">
              이 응답은 <strong>서버가</strong> 준 것입니다. 관리 화면의 모든 API 는
              <code> /api/admin/** </code>이라 ADMIN 권한을 다시 확인합니다(FR-019 · AC-26).
            </p>
          </>
        ) : loading && admin === null ? (
          <p className="hint">불러오는 중…</p>
        ) : (
          children
        )}
      </main>
    </div>
  );
}

export default function AdminShell({ children }: { children: ReactNode }) {
  // 미인증이면 **관리자 진입점**으로 보낸다(FR-032 — 사용자 로그인과 경로가 다르다)
  return <RequireAuth loginPath="/admin/login">{() => <AdminFrame>{children}</AdminFrame>}</RequireAuth>;
}
