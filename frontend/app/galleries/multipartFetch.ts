/**
 * `multipart/form-data` 전송 — 등록·수정 전용.
 *
 * ★ **왜 `apiFetch` 를 쓰지 않는가**(쓸 수 없다):
 *   `apiFetch` 는 `body` 가 있으면 `Content-Type: application/json` 을 붙인다. 멀티파트는
 *   브라우저가 `boundary=...` 를 포함한 헤더를 **직접** 만들어야 하는데, 헤더를 미리 박으면
 *   boundary 가 빠져 서버가 본문을 파싱하지 못한다. 스프레드로 합치는 구조라 밖에서
 *   그 헤더를 **지울 방법이 없다**(`Headers` 인스턴스를 넘겨도 스프레드에서 사라진다).
 *   `lib/api.ts` 는 수정 금지 대상이므로 멀티파트만 여기서 따로 보낸다.
 *
 * ★ 대신 **오류 규약은 그대로 지킨다** — `ApiError`/`NetworkError` 를 `lib/api.ts` 에서
 *   가져다 쓰고, 문구는 서버 `detail` 을 그대로 둔다. 프론트가 문구를 만들지 않는다.
 *
 * ★ 401 자동 재발급도 같은 규칙으로 흉내 낸다(AC-4) — refresh 1회 후 1회만 재시도.
 */

import { ApiError, NetworkError, type ProblemDetail } from "@/lib/api";

const REFRESH_PATH = "/api/auth/refresh";

async function toError(response: Response): Promise<ApiError | NetworkError> {
  try {
    const problem = (await response.json()) as ProblemDetail;
    if (typeof problem?.code !== "string") return new NetworkError();
    return new ApiError(problem);
  } catch {
    return new NetworkError();
  }
}

async function send(path: string, method: string, body: FormData): Promise<Response> {
  try {
    // headers 를 **아예 넘기지 않는다** — 브라우저가 boundary 를 포함해 만들게 둔다.
    return await fetch(path, { method, body, credentials: "same-origin" });
  } catch {
    throw new NetworkError();
  }
}

/**
 * @returns 서버가 준 `Location` 헤더(등록 시 `/api/galleries/{id}`). 없으면 null
 * @throws {ApiError} 서버가 의도적으로 보낸 실패 — 문구는 `<Alert>` 가 그대로 보여준다
 * @throws {NetworkError} 연결 실패 또는 형식이 다른 응답
 */
export async function multipartFetch(
  path: string,
  method: "POST" | "PUT",
  body: FormData,
): Promise<string | null> {
  let response = await send(path, method, body);

  if (response.status === 401) {
    const refreshed = await fetch(REFRESH_PATH, { method: "POST", credentials: "same-origin" })
      .then((r) => r.ok)
      .catch(() => false);
    // FormData 는 재사용할 수 있다(스트림이 아니라 값 목록이다).
    if (refreshed) response = await send(path, method, body);
  }

  if (!response.ok) throw await toError(response);
  return response.headers.get("Location");
}

/** `/api/galleries/12` → `12`. 등록 직후 상세로 보낼 때 쓴다. */
export function idFromLocation(location: string | null): number | null {
  if (location === null) return null;
  const last = location.split("/").pop();
  const id = Number(last);
  return Number.isInteger(id) && id > 0 ? id : null;
}
