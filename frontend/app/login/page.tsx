import { Suspense } from "react";
import LoginForm from "./LoginForm";

/**
 * 계약 3번 — 사용자 로그인.
 *
 * ⚠️ `useSearchParams` 를 쓰는 컴포넌트는 **Suspense 로 감싸야** 한다.
 *    감싸지 않으면 프로덕션 빌드가 "Missing Suspense boundary" 로 실패한다.
 *    개발 서버는 요청마다 렌더해서 통과하므로 `npm run build` 전까지 안 드러난다.
 */
export default function LoginPage() {
  return (
    <main>
      <h1>로그인</h1>
      <p className="subtitle">아이디와 비밀번호를 입력해 주세요.</p>
      <Suspense fallback={<p className="hint">불러오는 중…</p>}>
        <LoginForm />
      </Suspense>
    </main>
  );
}
