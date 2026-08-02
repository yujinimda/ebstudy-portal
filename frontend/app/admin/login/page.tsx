import { Suspense } from "react";
import AdminLoginForm from "./AdminLoginForm";

/**
 * 계약 4번 — 관리자 로그인. **별도 진입점**(FR-032).
 *
 * 사용자 로그인과 화면이 거의 같지만 **부르는 엔드포인트가 다르다**
 * (`POST /api/admin/auth/login`). 인증 메커니즘은 공유하고 진입점과 정책만 분리한다 —
 * 두 벌 만들면 취약점 표면이 두 배이고 한쪽을 고칠 때 다른 쪽을 잊는다(FR-017).
 */
export default function AdminLoginPage() {
  return (
    <main>
      <h1>관리자 로그인</h1>
      <p className="subtitle">관리자 전용 진입점입니다.</p>
      <Suspense fallback={<p className="hint">불러오는 중…</p>}>
        <AdminLoginForm />
      </Suspense>
    </main>
  );
}
