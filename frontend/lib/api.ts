/**
 * API 클라이언트 — contracts/auth-api.md.
 *
 * 두 가지만 한다.
 *   1. RFC 9457 Problem Details 를 {@link ApiError} 로 바꾼다
 *   2. ★ AC-4 자동 재발급 — 401 을 받으면 refresh 하고 원 요청을 **1회** 재시도한다
 *
 * ★ 문구를 여기서 만들지 않는다. 서버가 `detail` 에 한국어 안내를 담아 보내고
 *   화면은 그것을 그대로 쓴다. "응답을 만드는 자리는 하나"(research.md 15)가
 *   프론트까지 이어져야 AC-2·AC-3·AC-32 의 응답 동일성이 화면에서 갈라지지 않는다.
 */

/** 서버가 보내는 RFC 9457 Problem Details + 확장 필드. */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  code: string;
  traceId: string;
  errors?: { field: string; reason: string }[];
}

/** 서버가 의도적으로 보낸 실패(4xx/5xx). */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId: string;
  readonly errors: { field: string; reason: string }[];

  constructor(problem: ProblemDetail) {
    // ★ 화면에 보여줄 문구는 서버의 detail 이다.
    super(problem.detail);
    this.name = "ApiError";
    this.status = problem.status;
    this.code = problem.code;
    this.traceId = problem.traceId;
    this.errors = problem.errors ?? [];
  }
}

/** 네트워크가 끊겼거나 응답이 Problem Details 가 아닌 경우. */
export class NetworkError extends Error {
  constructor(message = "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요") {
    super(message);
    this.name = "NetworkError";
  }
}

const REFRESH_PATH = "/api/auth/refresh";

/**
 * ★ 프론트 규칙 2 — 동시 재발급을 한 번으로 합친다.
 *
 * 화면이 뜨면서 여러 요청이 동시에 401 을 받는 일이 흔하다. 각자 재발급하면
 * **로그인 1회에 티켓이 여러 개 생기고** refresh_tickets 테이블이 부풀어 오른다.
 * 진행 중인 재발급이 있으면 새로 부르지 않고 그 결과를 함께 기다린다.
 */
let inFlightRefresh: Promise<boolean> | null = null;

function refreshOnce(): Promise<boolean> {
  if (inFlightRefresh === null) {
    inFlightRefresh = fetch(REFRESH_PATH, {
      method: "POST",
      credentials: "same-origin",
    })
      .then((response) => response.ok)
      .catch(() => false)
      .finally(() => {
        inFlightRefresh = null;
      });
  }
  return inFlightRefresh;
}

async function toApiError(response: Response): Promise<ApiError | NetworkError> {
  try {
    const problem = (await response.json()) as ProblemDetail;
    // code 가 없으면 우리 서버의 응답이 아니다(프록시·게이트웨이 오류 등).
    if (typeof problem?.code !== "string") return new NetworkError();
    return new ApiError(problem);
  } catch {
    return new NetworkError();
  }
}

interface ApiFetchOptions extends RequestInit {
  /** 내부용 — 재시도 루프를 막는 플래그. 호출자가 쓰지 않는다. */
  __retried?: boolean;
}

/**
 * @throws {ApiError} 서버가 4xx/5xx 를 보냈을 때
 * @throws {NetworkError} 연결 실패 또는 형식이 다른 응답
 */
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { __retried = false, ...init } = options;

  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      // 쿠키를 실어 보낸다. 오리진이 하나이므로 same-origin 으로 충분하다.
      credentials: "same-origin",
      headers: {
        ...(init.body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...init.headers,
      },
    });
  } catch {
    throw new NetworkError();
  }

  // ★ AC-4 자동 재발급.
  //   규칙 1 — 재시도는 1회만(__retried 로 막는다). 없으면 무한 루프.
  //   규칙 3 — refresh 자신의 401 은 재시도하지 않는다. 1번과 같은 루프가 된다.
  if (response.status === 401 && !__retried && path !== REFRESH_PATH) {
    const refreshed = await refreshOnce();
    if (refreshed) {
      return apiFetch<T>(path, { ...options, __retried: true });
    }
    // 재발급도 실패했다 — 여기서 멈춘다. 화면 이동은 호출자가 정한다.
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  // 204 No Content (로그아웃) 는 본문이 없다.
  if (response.status === 204) return undefined as T;

  try {
    return (await response.json()) as T;
  } catch {
    throw new NetworkError();
  }
}

// ── 엔드포인트별 얇은 래퍼 ────────────────────────────────────
// contracts/auth-api.md 의 번호와 1:1 로 맞춘다.

export interface UserResponse {
  username: string;
  name: string;
  role: "USER" | "ADMIN";
}

/** 계약 1번 — POST /api/auth/signup */
export function signup(username: string, password: string, name: string) {
  return apiFetch<UserResponse>("/api/auth/signup", {
    method: "POST",
    body: JSON.stringify({ username, password, name }),
  });
}

/** 계약 2번 — GET /api/auth/check-id */
export function checkId(username: string) {
  return apiFetch<{ available: boolean }>(
    `/api/auth/check-id?username=${encodeURIComponent(username)}`,
  );
}

/** 계약 3번 — POST /api/auth/login */
export function login(username: string, password: string) {
  return apiFetch<UserResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

/** 계약 4번 — POST /api/admin/auth/login (별도 진입점, FR-032) */
export function adminLogin(username: string, password: string) {
  return apiFetch<UserResponse>("/api/admin/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

/** 계약 6번 — POST /api/auth/logout */
export function logout() {
  return apiFetch<void>("/api/auth/logout", { method: "POST" });
}

/** 계약 7번 — GET /api/me */
export function fetchMe() {
  return apiFetch<UserResponse>("/api/me");
}

/** 계약 8번 — GET /api/admin/me (ADMIN 권한 필요) */
export function fetchAdminMe() {
  return apiFetch<UserResponse>("/api/admin/me");
}
