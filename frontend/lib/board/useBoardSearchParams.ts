"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useMemo, useState } from "react";
import { criteriaToUrlQuery, defaultPeriod, parseCriteria } from "./criteria";
import type {
  BoardListOptionPatch,
  BoardSearchCriteria,
  BoardSearchPatch,
} from "./types";

/**
 * ★★ **이 훅이 게시판 프론트의 중심이다** — 요구사항 1.1 "검색조건 유지".
 *
 * 검색조건을 컴포넌트 state 가 아니라 **URL 쿼리스트링**에 둔다. 그러면
 * 상세→목록·등록취소→목록에서 조건이 유지되는 것은 물론이고
 * **뒤로가기·새로고침·링크공유가 전부 공짜로** 따라온다. state 로 들고 있으면
 * 그 셋을 각각 따로 구현해야 하고, 셋 다 새로고침 한 번에 무너진다.
 *
 * ─────────────────────────────────────────────────────────────
 * ⚠️ **이 훅을 쓰는 컴포넌트는 반드시 `<Suspense>` 로 감싼다.**
 *
 * `useSearchParams()` 는 프리렌더를 클라이언트 렌더로 떨어뜨린다. 경계가 없으면
 * **개발 서버는 통과하는데 `npm run build` 가 실패한다.** 페이지는 이렇게 쓴다:
 *
 * ```tsx
 * // app/free/page.tsx  ← 서버 컴포넌트로 두고 경계만 만든다
 * import { Suspense } from "react";
 * import FreeListView from "./FreeListView";       // "use client" + 이 훅 사용
 * import BoardListFallback from "@/components/board/BoardListFallback";
 *
 * export default function Page() {
 *   return (
 *     <Suspense fallback={<BoardListFallback />}>
 *       <FreeListView />
 *     </Suspense>
 *   );
 * }
 * ```
 * ─────────────────────────────────────────────────────────────
 *
 * **push 와 replace 를 나눈 이유**
 *   - 검색·페이지 이동 → `push`. 사용자가 "한 일"이라 뒤로가기로 되돌아갈 수 있어야 한다
 *   - 개씩보기·정렬 → `replace`. 보기 설정이지 이동이 아니다. push 하면 뒤로가기를
 *     열 번 눌러야 검색 이전으로 돌아간다
 */
export interface BoardSearchParamsResult {
  /** 지금 URL 이 뜻하는 검색조건. URL 이 유일한 저장소다 */
  criteria: BoardSearchCriteria;
  /** 검색 버튼 — **page 를 0 으로 되돌린다**(3페이지에서 검색하면 결과가 없을 수 있다) */
  applySearch: (patch: BoardSearchPatch) => void;
  /** 툴바 — 선택 즉시 반영. 역시 page 를 0 으로 되돌린다 */
  applyOptions: (patch: BoardListOptionPatch) => void;
  /** 페이징 */
  goToPage: (page: number) => void;
  /** 검색조건을 전부 지우고 첫 화면으로 */
  reset: () => void;
  /**
   * 지금 URL 의 쿼리스트링(`"?a=b"` 또는 `""`).
   *
   * 상세·등록 화면으로 갈 때 **그대로 실어 보낸다** — 돌아올 때 이 값을 목록 경로에 붙이면
   * 조건이 복원된다. 파싱해서 다시 만들지 않고 원문을 넘기는 것이 핵심이다
   */
  listQuery: string;
  /** `listHref("/free")` → `"/free?page=3&size=20"`. 상세의 `목록` 버튼이 쓴다 */
  listHref: (path: string) => string;
}

export function useBoardSearchParams(): BoardSearchParamsResult {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  // 기본 1달을 **마운트 시 한 번** 고정한다. 렌더마다 new Date() 를 부르면
  // 자정을 넘기는 순간 "기본값과 같아서 URL 에 안 적은" 조건이 다른 값이 된다
  const [fallback] = useState(defaultPeriod);

  const criteria = useMemo(
    () => parseCriteria(searchParams, fallback),
    [searchParams, fallback],
  );

  const navigate = useCallback(
    (next: BoardSearchCriteria, mode: "push" | "replace") => {
      const query = criteriaToUrlQuery(next, fallback);
      const href = query === "" ? pathname : `${pathname}?${query}`;
      if (mode === "push") router.push(href);
      else router.replace(href);
    },
    [fallback, pathname, router],
  );

  const applySearch = useCallback(
    (patch: BoardSearchPatch) => {
      navigate({ ...criteria, ...patch, page: 0 }, "push");
    },
    [criteria, navigate],
  );

  const applyOptions = useCallback(
    (patch: BoardListOptionPatch) => {
      navigate({ ...criteria, ...patch, page: 0 }, "replace");
    },
    [criteria, navigate],
  );

  const goToPage = useCallback(
    (page: number) => {
      navigate({ ...criteria, page: Math.max(0, page) }, "push");
    },
    [criteria, navigate],
  );

  const reset = useCallback(() => {
    router.push(pathname);
  }, [pathname, router]);

  const raw = searchParams.toString();
  const listQuery = raw === "" ? "" : `?${raw}`;

  const listHref = useCallback((path: string) => `${path}${listQuery}`, [listQuery]);

  return { criteria, applySearch, applyOptions, goToPage, reset, listQuery, listHref };
}

/**
 * 상세·등록 화면처럼 **조건을 쓰지는 않고 되돌려주기만** 하는 화면용.
 *
 * 검색조건을 파싱할 필요가 없으므로 원문 쿼리스트링만 준다.
 * ⚠️ 이것도 `useSearchParams` 를 쓴다 — **`<Suspense>` 가 필요하다.**
 *
 * ```tsx
 * const listQuery = useListQuery();
 * <Link href={`/free${listQuery}`}>목록</Link>
 * ```
 */
export function useListQuery(): string {
  const searchParams = useSearchParams();
  const raw = searchParams.toString();
  return raw === "" ? "" : `?${raw}`;
}
