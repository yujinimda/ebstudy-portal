"use client";

import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import AdminShell from "./AdminShell";

/**
 * `/admin/**` 공통 레이아웃 — LNB 를 화면마다 붙이지 않기 위해 레이아웃에 둔다.
 *
 * ⚠️ **`/admin/login` 만 셸 밖이다.** 로그인 화면까지 셸로 감싸면
 *   `RequireAuth` 가 미인증을 감지해 `/admin/login` 으로 보내고 → 그 화면이 다시
 *   자기 자신을 감싸는 **무한 리다이렉트**가 된다. 로그인은 "아직 인증되지 않은 상태"가
 *   정상인 유일한 관리자 화면이라 여기서 갈라 낸다.
 *
 * 클라이언트 컴포넌트인 이유는 `usePathname` 하나 때문이다. 자식(각 page)은
 * 서버에서 렌더된 트리로 그대로 내려온다 — 클라이언트 레이아웃이 자식까지
 * 클라이언트로 만들지는 않는다.
 */
export default function AdminLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();

  if (pathname.startsWith("/admin/login")) return <>{children}</>;

  return <AdminShell>{children}</AdminShell>;
}
