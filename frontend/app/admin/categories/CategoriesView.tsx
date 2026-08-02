"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import Alert from "@/components/Alert";
import { apiFetch } from "@/lib/api";
import { useBoardList } from "@/lib/board/useBoardList";
import type { BoardType } from "@/lib/board/types";
import styles from "../admin.module.css";
import { CONFIRM, confirmAction } from "../confirm";
import type { CategoryAdminItem } from "../types";

/**
 * 분류(카테고리) 관리 — 요구사항 7.2.
 *
 * ★ **문의게시판은 분류가 없다**(요구사항 0장 표). 탭에 넣지 않는다.
 *
 * ★ 삭제 버튼을 `deletable === false` 라고 감추지 않는다. 감추면 "왜 못 지우지"가 되고,
 *   무엇보다 **화면이 감춘 것은 검증이 아니다**. 사용 중인 분류를 지우려 하면 서버가
 *   `CATEGORY_IN_USE`(409)로 거부하고 그 문구를 그대로 보여준다(요구사항 7.2 —
 *   "이미 사용 중인 분류는 삭제하지 않는다. 비활성으로 내린다").
 */
const TABS: { boardType: BoardType; label: string }[] = [
  { boardType: "NOTICE", label: "공지사항" },
  { boardType: "FREE", label: "자유게시판" },
  { boardType: "GALLERY", label: "갤러리" },
];

function isBoardType(value: string | null): value is BoardType {
  return TABS.some((tab) => tab.boardType === value);
}

/**
 * 한 줄 = 하나의 편집 폼.
 *
 * 행마다 컴포넌트를 나눈 이유: 편집 중인 값을 부모의 Map 으로 들고 있으면 한 줄을 고칠 때마다
 * 표 전체가 다시 그려진다. `key` 에 `updatedAt` 을 넣어 두면 저장 후 목록을 다시 받았을 때
 * React 가 이 컴포넌트를 새로 만들어 **서버가 준 값으로 초기화**된다(수동 동기화 불필요).
 */
function CategoryRow({
  item,
  onDone,
  onError,
}: {
  item: CategoryAdminItem;
  onDone: () => void;
  onError: (error: unknown) => void;
}) {
  const [name, setName] = useState(item.name);
  const [sortOrder, setSortOrder] = useState(String(item.sortOrder));
  const [active, setActive] = useState(item.active);
  const [busy, setBusy] = useState(false);

  async function handleSave() {
    if (!confirmAction(CONFIRM.update)) return;
    setBusy(true);
    onError(null);
    try {
      // null 인 항목은 서버가 바꾸지 않는다. 여기서는 세 값을 전부 보낸다
      await apiFetch(`/api/admin/categories/${item.id}`, {
        method: "PUT",
        body: JSON.stringify({ name, sortOrder: Number(sortOrder), active }),
      });
      onDone();
    } catch (caught) {
      onError(caught);
    } finally {
      setBusy(false);
    }
  }

  async function handleDelete() {
    if (!confirmAction(CONFIRM.remove)) return;
    setBusy(true);
    onError(null);
    try {
      await apiFetch(`/api/admin/categories/${item.id}`, { method: "DELETE" });
      onDone();
    } catch (caught) {
      // 사용 중이면 여기서 409 CATEGORY_IN_USE 가 온다 — 서버 문구를 그대로 보여준다
      onError(caught);
    } finally {
      setBusy(false);
    }
  }

  return (
    <tr>
      <td>
        <label className="hint" htmlFor={`name-${item.id}`}>
          이름
        </label>
        <input
          id={`name-${item.id}`}
          value={name}
          onChange={(e) => setName(e.target.value)}
          disabled={busy}
        />
      </td>
      <td className={styles.narrow}>
        <label className="hint" htmlFor={`order-${item.id}`}>
          표시 순서
        </label>
        <input
          id={`order-${item.id}`}
          type="number"
          value={sortOrder}
          onChange={(e) => setSortOrder(e.target.value)}
          disabled={busy}
        />
      </td>
      <td>
        <label className={styles.checkboxRow} htmlFor={`active-${item.id}`}>
          <input
            id={`active-${item.id}`}
            type="checkbox"
            checked={active}
            onChange={(e) => setActive(e.target.checked)}
            disabled={busy}
          />
          사용
        </label>
      </td>
      <td className={styles.numeric}>{item.postCount.toLocaleString()}건</td>
      <td>
        <div className={styles.actions}>
          <button
            type="button"
            className={styles.smallButton}
            onClick={handleSave}
            disabled={busy}
          >
            저장
          </button>
          <button
            type="button"
            className={`${styles.smallButton} ${styles.dangerButton}`}
            onClick={handleDelete}
            disabled={busy}
          >
            삭제
          </button>
        </div>
      </td>
    </tr>
  );
}

export default function CategoriesView() {
  const searchParams = useSearchParams();
  const raw = searchParams.get("boardType");
  const boardType: BoardType = isBoardType(raw) ? raw : "NOTICE";

  const {
    data: categories,
    loading,
    error: loadError,
    reload,
  } = useBoardList<CategoryAdminItem[]>(`/api/admin/categories?boardType=${boardType}`);

  const [name, setName] = useState("");
  const [sortOrder, setSortOrder] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [actionError, setActionError] = useState<unknown>(null);

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    if (!confirmAction(CONFIRM.create)) return;
    setSubmitting(true);
    setActionError(null);
    try {
      await apiFetch("/api/admin/categories", {
        method: "POST",
        // sortOrder 를 비우면 서버가 맨 뒤에 붙인다 — 빈 값을 0 으로 채우지 않는다
        body: JSON.stringify({
          boardType,
          name,
          sortOrder: sortOrder === "" ? null : Number(sortOrder),
        }),
      });
      setName("");
      setSortOrder("");
      reload();
    } catch (caught) {
      setActionError(caught);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <h1>분류(카테고리) 관리</h1>
      <p className="subtitle">
        게시판별 분류를 등록·수정합니다. 문의게시판은 분류를 쓰지 않습니다.
      </p>

      <div className={styles.tabs}>
        {TABS.map((tab) => (
          <Link
            key={tab.boardType}
            href={`/admin/categories?boardType=${tab.boardType}`}
            className={
              tab.boardType === boardType ? `${styles.tab} ${styles.tabActive}` : styles.tab
            }
            aria-current={tab.boardType === boardType ? "page" : undefined}
          >
            {tab.label}
          </Link>
        ))}
      </div>

      <Alert error={actionError} />
      <Alert error={loadError} />

      <form className={`${styles.card} ${styles.inlineForm}`} onSubmit={handleCreate}>
        <div className={styles.inlineField}>
          <label htmlFor="new-name">분류 이름</label>
          <input
            id="new-name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </div>
        <div className={styles.inlineField}>
          <label htmlFor="new-order">표시 순서</label>
          <input
            id="new-order"
            className={styles.narrow}
            type="number"
            value={sortOrder}
            onChange={(e) => setSortOrder(e.target.value)}
            placeholder="맨 뒤"
          />
        </div>
        <button type="submit" disabled={submitting}>
          {submitting ? "등록 중…" : "분류 등록"}
        </button>
      </form>

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">이름</th>
              <th scope="col">표시 순서</th>
              <th scope="col">사용 여부</th>
              <th scope="col">사용 중인 글</th>
              <th scope="col">관리</th>
            </tr>
          </thead>
          <tbody>
            {categories !== null && categories.length > 0 ? (
              categories.map((item) => (
                <CategoryRow
                  // updatedAt 을 key 에 넣어 저장 후 서버 값으로 다시 초기화되게 한다
                  key={`${item.id}-${item.updatedAt}`}
                  item={item}
                  onDone={reload}
                  onError={setActionError}
                />
              ))
            ) : (
              <tr>
                <td className={styles.empty} colSpan={5}>
                  {loading ? "불러오는 중…" : "등록된 분류가 없습니다."}
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="note">
        사용 중인 분류는 삭제되지 않습니다(서버가 409 로 거부합니다). 그때는 <strong>사용</strong>
        체크를 풀어 비활성으로 내리세요 — 과거 글의 분류 표기는 그대로 유지됩니다.
      </p>
    </>
  );
}
