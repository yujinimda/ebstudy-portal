"use client";

import { useCallback, useEffect, useState } from "react";
import { fetchMe, type UserResponse } from "./api";

/**
 * 로그인 상태 — **서버에 물어봐서** 안다.
 *
 * ★ 자격증명은 httpOnly 쿠키라 스크립트가 읽을 수 없다(AC-7 · FR-013).
 *   그래서 "토큰이 있으니 로그인 상태"라고 판단할 방법이 없고,
 *   `GET /api/me` 의 200/401 이 유일한 판정 수단이다.
 *
 * ★ 토큰을 localStorage 에 두지 않는 것도 같은 이유다 — 두는 순간 XSS 로 읽히고
 *   httpOnly 를 고른 의미가 사라진다.
 *
 * `apiFetch` 안에 자동 재발급이 들어 있으므로, Access 가 만료됐어도 여기서 401 이
 * 나오기 전에 재발급이 한 번 시도된다(AC-4).
 */
export type SessionState =
  | { status: "loading"; user: null }
  | { status: "authenticated"; user: UserResponse }
  | { status: "anonymous"; user: null };

export function useSession() {
  const [state, setState] = useState<SessionState>({ status: "loading", user: null });
  /** 값 자체에 의미는 없다. 바뀌면 effect 가 다시 돈다(= 다시 물어본다). */
  const [reloadToken, setReloadToken] = useState(0);

  const reload = useCallback(() => {
    setReloadToken((n) => n + 1);
  }, []);

  useEffect(() => {
    // ★ setState 를 effect 본문에서 동기로 부르지 않는다 — 응답이 온 뒤 콜백에서 부른다.
    //   cancelled 플래그는 화면을 떠난 뒤 늦게 도착한 응답이 상태를 덮어쓰는 것을 막는다.
    let cancelled = false;

    fetchMe()
      .then((user) => {
        if (!cancelled) setState({ status: "authenticated", user });
      })
      .catch(() => {
        // 401 은 "로그인 안 한 상태"라는 정상 신호다. 그 외(네트워크·5xx)도 미인증으로
        // 다룬다 — 인증된 것처럼 보이는 쪽이 더 위험하다.
        if (!cancelled) setState({ status: "anonymous", user: null });
      });

    return () => {
      cancelled = true;
    };
  }, [reloadToken]);

  return { ...state, reload };
}
