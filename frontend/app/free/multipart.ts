import { ApiError, NetworkError, type ProblemDetail } from "@/lib/api";

/**
 * ★ **`apiFetch` 를 멀티파트에 쓸 수 없어서 만든 최소 래퍼다.**
 *
 * `lib/api.ts` 는 `body` 가 있으면 `Content-Type: application/json` 을 강제로 붙인다
 * (JSON 만 쓰던 001 에서는 옳았다). 멀티파트는 브라우저가 `boundary` 를 포함한 헤더를
 * 직접 만들어야 하므로, 그 헤더가 붙는 순간 서버가 본문을 파싱하지 못한다.
 * `headers` 로 덮어쓸 수도 없다 — 값을 지울 방법이 없고 `undefined` 를 넣으면
 * 문자열 `"undefined"` 가 된다.
 *
 * `lib/api.ts` 는 공용 파일이라 수정하지 않는다(다른 레인과 충돌한다). 대신
 * **오류 표현은 그대로 재사용한다** — `ApiError` · `NetworkError` 를 그대로 던지므로
 * `<Alert error={…} />` 가 서버 `detail` 을 똑같이 보여준다. 문구를 여기서 만들지 않는다.
 *
 * 401 자동 재발급도 얇게 따라간다(AC-4). `lib/api.ts` 의 동시 재발급 합치기까지는
 * 복제하지 않았다 — 폼 제출은 한 번에 하나뿐이라 경합이 없다.
 */
async function toError(response: Response): Promise<ApiError | NetworkError> {
  try {
    const problem = (await response.json()) as ProblemDetail;
    if (typeof problem?.code !== "string") return new NetworkError();
    return new ApiError(problem);
  } catch {
    return new NetworkError();
  }
}

async function send(path: string, form: FormData): Promise<Response> {
  try {
    // ★ headers 를 아예 주지 않는다. 브라우저가 boundary 를 붙인 Content-Type 을 만든다
    return await fetch(path, { method: "POST", body: form, credentials: "same-origin" });
  } catch {
    throw new NetworkError();
  }
}

/**
 * `multipart/form-data` 로 POST 한다.
 *
 * ★ 자유게시판은 **등록도 수정도 POST** 다(`PUT` 아님). 톰캣이 멀티파트 본문의 일반 필드를
 *   POST 에서만 파싱하기 때문 — 갤러리(`PUT`)와 다르다. `FreeBoardController` 주석 참조.
 *
 * @returns 본문이 있으면 JSON, 204 면 undefined
 */
export async function postMultipart<T>(path: string, form: FormData): Promise<T> {
  let response = await send(path, form);

  if (response.status === 401) {
    const refreshed = await fetch("/api/auth/refresh", {
      method: "POST",
      credentials: "same-origin",
    })
      .then((r) => r.ok)
      .catch(() => false);
    // ★ 재시도는 1회만. FormData 는 재사용할 수 있다(스트림이 아니라 값 목록이다)
    if (refreshed) response = await send(path, form);
  }

  if (!response.ok) throw await toError(response);
  if (response.status === 204) return undefined as T;

  try {
    return (await response.json()) as T;
  } catch {
    throw new NetworkError();
  }
}
