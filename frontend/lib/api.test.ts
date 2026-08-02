import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch, ApiError } from "./api";

/**
 * AC-4 자동 재발급 — contracts/auth-api.md "프론트 규칙 3개".
 *
 * | | 규칙 | 없으면 |
 * |---|---|---|
 * | 1 | 재시도는 1회만 | 무한 루프 |
 * | 2 | 동시 재발급을 한 번으로 합친다 | 로그인 1회에 티켓이 여러 개 생긴다 |
 * | 3 | refresh 자신의 401 은 재시도하지 않는다 | 1번과 같은 루프 |
 *
 * 단위로 두는 이유: 규칙이 "몇 번 불렀나" 로 판정되는 호출 횟수 문제라
 * 브라우저 없이 검증할 수 있다(test-strategy.md 3장).
 */

function problem(code: string, status: number) {
  return new Response(
    JSON.stringify({
      type: "/errors/authentication",
      title: "Authentication Required",
      status,
      detail: "로그인이 필요합니다",
      instance: "/api/me",
      code,
      traceId: "test01",
    }),
    { status, headers: { "Content-Type": "application/problem+json" } },
  );
}

function ok(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("apiFetch — AC-4 자동 재발급", () => {
  it("401 을 받으면 refresh 후 원 요청을 1회 재시도한다", async () => {
    fetchMock
      .mockResolvedValueOnce(problem("AUTH_REQUIRED", 401)) // GET /api/me
      .mockResolvedValueOnce(new Response(null, { status: 200 })) // POST refresh
      .mockResolvedValueOnce(ok({ username: "kim", name: "김", role: "USER" })); // 재시도

    const user = await apiFetch<{ username: string }>("/api/me");

    expect(user.username).toBe("kim");
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][0]).toBe("/api/auth/refresh");
  });

  it("규칙 1 — 재발급 후에도 401 이면 더 재시도하지 않고 던진다", async () => {
    fetchMock
      .mockResolvedValueOnce(problem("AUTH_REQUIRED", 401))
      .mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(problem("AUTH_REQUIRED", 401)); // 재시도도 401

    await expect(apiFetch("/api/me")).rejects.toBeInstanceOf(ApiError);

    // 3번에서 멈춘다 — 무한 루프가 아니다
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("규칙 2 — 동시에 401 을 받아도 재발급은 한 번만 부른다", async () => {
    fetchMock.mockImplementation((path: string) => {
      if (path === "/api/auth/refresh") {
        // 재발급이 즉시 끝나지 않게 해서 겹치는 상황을 만든다
        return new Promise((resolve) =>
          setTimeout(() => resolve(new Response(null, { status: 200 })), 10),
        );
      }
      // 첫 호출은 401, 재시도는 200
      const calls = fetchMock.mock.calls.filter((c) => c[0] === path).length;
      return Promise.resolve(calls <= 3 ? problem("AUTH_REQUIRED", 401) : ok({ ok: true }));
    });

    await Promise.allSettled([
      apiFetch("/api/a"),
      apiFetch("/api/b"),
      apiFetch("/api/c"),
    ]);

    const refreshCalls = fetchMock.mock.calls.filter((c) => c[0] === "/api/auth/refresh");
    expect(refreshCalls).toHaveLength(1);
  });

  it("규칙 3 — refresh 자신의 401 은 재시도하지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(problem("AUTH_REFRESH_INVALID", 401));

    await expect(apiFetch("/api/auth/refresh", { method: "POST" })).rejects.toBeInstanceOf(
      ApiError,
    );

    // 딱 한 번. 재발급을 부르러 가지 않는다
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("재발급이 실패하면 원래 401 을 그대로 던진다", async () => {
    fetchMock
      .mockResolvedValueOnce(problem("AUTH_REQUIRED", 401))
      .mockResolvedValueOnce(new Response(null, { status: 401 })); // refresh 실패

    await expect(apiFetch("/api/me")).rejects.toMatchObject({ code: "AUTH_REQUIRED" });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});

describe("apiFetch — 오류 변환", () => {
  it("401 이 아닌 오류는 재발급 없이 바로 ApiError 로 던진다", async () => {
    fetchMock.mockResolvedValueOnce(problem("AUTH_FORBIDDEN", 403));

    await expect(apiFetch("/api/admin/me")).rejects.toMatchObject({
      code: "AUTH_FORBIDDEN",
      status: 403,
      traceId: "test01",
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("204 는 본문 없이 성공한다", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(apiFetch("/api/auth/logout", { method: "POST" })).resolves.toBeUndefined();
  });

  it("서버 detail 을 메시지로 쓴다 — 프론트가 문구를 만들지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(problem("AUTH_FORBIDDEN", 403));
    await expect(apiFetch("/api/admin/me")).rejects.toThrow("로그인이 필요합니다");
  });
});
