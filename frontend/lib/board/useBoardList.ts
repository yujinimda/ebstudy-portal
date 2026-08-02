"use client";

import { useCallback, useEffect, useState } from "react";
import { apiFetch } from "@/lib/api";

/**
 * 목록(과 그 밖의 GET) 데이터 페칭 — 로딩·오류 상태 포함.
 *
 * **응답 타입을 통째로 제네릭으로 받는다.** `PageResponse<T>` 를 강제하지 않는 이유는
 * 공지사항 목록이 `{ pinned, page }` 라서다(요구사항 3.1 — 상단 고정은 페이징 밖이어야 한다).
 * 한 게시판을 위해 특수 케이스를 만드느니 훅을 한 겹 얇게 두는 편이 낫다.
 *
 * ```ts
 * const url = boardListUrl("/api/free-posts", criteria);
 * const { data, loading, error } = useBoardList<PageResponse<FreePostListItem>>(url);
 * ```
 *
 * ★ **URL 문자열 하나를 의존성으로 쓴다.** 조건 객체를 넘기면 매 렌더 새 객체라
 *   effect 가 무한히 돈다. 문자열은 값 비교라 그 함정이 없고, "URL 이 같으면 같은 요청"
 *   이라는 규칙도 눈에 보인다.
 */
export interface BoardListResult<T> {
  /** 아직 못 받았으면 null. **다시 불러오는 동안에는 직전 값을 유지한다**(깜빡임 방지) */
  data: T | null;
  loading: boolean;
  /** `ApiError` 또는 `NetworkError`. 문구는 `<Alert error={error} />` 가 그대로 보여준다 */
  error: unknown;
  /** 등록·삭제 뒤 목록 갱신 */
  reload: () => void;
}

interface Snapshot<T> {
  /** 이 결과가 어느 URL 의 것인가. `loading` 판정에 쓴다 */
  url: string | null;
  data: T | null;
  error: unknown;
}

export function useBoardList<T>(url: string | null): BoardListResult<T> {
  const [snapshot, setSnapshot] = useState<Snapshot<T>>({ url: null, data: null, error: null });
  /** 값 자체에 의미는 없다. 바뀌면 effect 가 다시 돈다(useSession 과 같은 방식) */
  const [reloadToken, setReloadToken] = useState(0);

  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  useEffect(() => {
    if (url === null) return;
    // ★ effect 본문에서 동기 setState 를 하지 않는다(lint 가 막는다 · 리렌더 한 번이 더 든다).
    //   로딩은 상태가 아니라 "받아 둔 결과의 URL 이 지금 URL 과 다른가"로 **계산**한다.
    //   cancelled 는 화면을 떠난 뒤 늦게 온 응답이 상태를 덮어쓰는 것을 막는다
    let cancelled = false;

    apiFetch<T>(url)
      .then((data) => {
        if (!cancelled) setSnapshot({ url, data, error: null });
      })
      .catch((caught: unknown) => {
        if (!cancelled) setSnapshot({ url, data: null, error: caught });
      });

    return () => {
      cancelled = true;
    };
  }, [url, reloadToken]);

  return {
    data: snapshot.data,
    error: snapshot.error,
    loading: url !== null && snapshot.url !== url,
    reload,
  };
}
