import { ApiError } from "@/lib/api";

/**
 * 오류 표시 — **문구는 서버가 준 것을 그대로** 쓴다.
 *
 * ★ 프론트에서 code 별로 문구를 다시 만들지 않는다. 만들면 AC-2·AC-3·AC-32 의
 *   "실패는 전부 같은 얼굴" 이 화면 층에서 갈라질 수 있다 — 서버가 통일한 것을
 *   프론트가 다시 나눠 버리면 계정 열거가 화면으로 새 나간다.
 *
 * traceId 를 함께 보여주는 이유(AC-27 · FR-024): 사용자가 "이 값으로 오류가 났다"고
 * 말할 수 있어야 로그에서 그 요청을 찾을 수 있다.
 */
export default function Alert({ error }: { error: unknown }) {
  if (error == null) return null;

  const message = error instanceof Error ? error.message : "오류가 발생했습니다";
  const traceId = error instanceof ApiError ? error.traceId : null;

  return (
    <p className="alert error" role="alert">
      {message}
      {traceId !== null && <code className="trace">추적 번호: {traceId}</code>}
    </p>
  );
}
