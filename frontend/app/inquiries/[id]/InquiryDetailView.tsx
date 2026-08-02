"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import Alert from "@/components/Alert";
import { LockIcon, StatusBadge } from "@/components/board/ListMarks";
import { ApiError, apiFetch } from "@/lib/api";
import { useListQuery } from "@/lib/board/useBoardSearchParams";
import { clearGrant, grantHeaders } from "../grant";
import styles from "../inquiries.module.css";
import { formatDateTime, type InquiryDetail } from "../types";
import SecretPasswordForm from "./SecretPasswordForm";

/**
 * 요구사항 6.3 상세 — 제목 · 등록일시 · 등록자 · 조회수 · 내용 · 답변완료/미답변 ·
 * 관리자 답변 영역 · 목록/수정/삭제.
 *
 * ★ `useBoardList` 를 쓰지 않는다. 그 훅은 URL 만 받고 **헤더를 실을 수 없는데**,
 *   비밀글 상세는 `X-Inquiry-Grant`(열람 통과)를 헤더로 보내야 한다.
 *
 * ★ 403 `SECRET_POST_LOCKED` 는 **오류가 아니라 화면 전환 신호**다(AC-28).
 *   Alert 로 빨간 문구를 띄우는 대신 비밀번호 입력 화면을 그린다.
 *
 * ★ 수정·삭제 버튼은 서버가 준 `editable`/`deletable`(본인 + 미답변) 로만 판단한다.
 *   ⚠️ **버튼을 숨기는 것은 권한 검증이 아니다**(FR-019 · AC-26). 주소창에 직접 쳐서
 *   들어와도 서버가 소유자와 답변 여부를 다시 보고 거부한다. 화면 판정은 편의일 뿐이다.
 */

interface Snapshot {
  /** 이 결과가 어느 글의 것인가. 로딩을 상태가 아니라 이 값으로 **계산**한다 */
  id: string | null;
  detail: InquiryDetail | null;
  /** 비밀글인데 열람 권한이 없다 → 비밀번호 입력 화면 */
  locked: boolean;
  error: unknown;
}

export default function InquiryDetailView() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const id = params.id;
  const listQuery = useListQuery();

  const [snapshot, setSnapshot] = useState<Snapshot>({
    id: null,
    detail: null,
    locked: false,
    error: null,
  });
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<unknown>(null);

  useEffect(() => {
    // ★ effect 본문에서 동기 setState 를 하지 않는다 — 응답 콜백에서만 세팅하고,
    //   cancelled 로 화면을 떠난 뒤 늦게 온 응답이 상태를 덮는 것을 막는다
    let cancelled = false;

    apiFetch<InquiryDetail>(`/api/inquiries/${id}`, { headers: grantHeaders(id) })
      .then((detail) => {
        if (!cancelled) setSnapshot({ id, detail, locked: false, error: null });
      })
      .catch((caught: unknown) => {
        if (cancelled) return;
        if (caught instanceof ApiError && caught.code === "SECRET_POST_LOCKED") {
          // 보관해 둔 통과가 만료됐을 수 있다 — 지우지 않으면 매번 쓸모없는 헤더를 보낸다
          clearGrant(id);
          setSnapshot({ id, detail: null, locked: true, error: null });
          return;
        }
        setSnapshot({ id, detail: null, locked: false, error: caught });
      });

    return () => {
      cancelled = true;
    };
  }, [id]);

  const loading = snapshot.id !== id;
  const detail = snapshot.detail;

  async function handleDelete() {
    // 요구사항 1.2 — 확인 후 진행
    if (!window.confirm("정말로 삭제 하시겠습니까")) return;
    setDeleteError(null);
    setDeleting(true);
    try {
      await apiFetch<void>(`/api/inquiries/${id}`, { method: "DELETE" });
      // 목록으로 돌아가되 검색조건은 살린다
      router.push(`/inquiries${listQuery}`);
    } catch (caught) {
      // 화면을 열어 둔 사이 관리자가 답변을 달았다면 409 INQUIRY_ALREADY_ANSWERED 가 온다.
      // 서버가 판정을 다시 한다는 뜻이고, 화면은 그 detail 을 그대로 보여준다
      setDeleteError(caught);
      setDeleting(false);
    }
  }

  if (loading) {
    return (
      <main className={styles.page}>
        <p className="hint">불러오는 중…</p>
      </main>
    );
  }

  if (snapshot.locked) {
    return (
      <SecretPasswordForm
        id={id}
        listQuery={listQuery}
        // 잠금해제 응답에 열린 상세가 들어 있다 — 재조회하지 않는다(조회수 이중 계산 방지)
        onUnlocked={(unlocked) =>
          setSnapshot({ id, detail: unlocked, locked: false, error: null })
        }
      />
    );
  }

  if (detail === null) {
    return (
      <main className={styles.page}>
        <h1>문의</h1>
        <Alert error={snapshot.error} />
        <p className={styles.actions}>
          <Link
            className={`${styles.linkButton} ${styles.linkButtonSecondary}`}
            href={`/inquiries${listQuery}`}
          >
            목록
          </Link>
        </p>
      </main>
    );
  }

  return (
    <main className={styles.page}>
      <h1 className={styles.detailTitle}>
        {detail.title}
        <StatusBadge tone={detail.answered ? "ok" : "muted"}>
          {detail.answered ? "답변완료" : "미답변"}
        </StatusBadge>
        <LockIcon show={detail.secret} />
      </h1>

      <dl className="info">
        <dt>등록자</dt>
        <dd>{detail.authorName}</dd>
        <dt>등록일시</dt>
        <dd>{formatDateTime(detail.createdAt)}</dd>
        <dt>조회수</dt>
        <dd>{detail.viewCount.toLocaleString()}</dd>
      </dl>

      {/* 서버 문자열을 텍스트 노드로 넣는다 — dangerouslySetInnerHTML 을 쓰지 않으므로
          본문에 태그가 들어 있어도 실행되지 않는다. 줄바꿈만 CSS 로 살린다 */}
      <div className={styles.content}>{detail.content}</div>

      <h2>관리자 답변</h2>
      {detail.answer === null ? (
        <p className={styles.answerEmpty}>아직 등록된 답변이 없습니다.</p>
      ) : (
        <div className={styles.answer}>
          <p className={styles.answerHead}>
            <span>{detail.answer.adminName}</span>
            <span>{formatDateTime(detail.answer.createdAt)}</span>
          </p>
          <p className={styles.answerBody}>{detail.answer.content}</p>
        </div>
      )}

      <Alert error={deleteError} />

      <div className={styles.actions}>
        <Link
          className={`${styles.linkButton} ${styles.linkButtonSecondary}`}
          href={`/inquiries${listQuery}`}
        >
          목록
        </Link>
        <span className={styles.actionsSpacer} />

        {/* 요구사항 6.3 — 본인 + **미답변일 때만**. 판정값은 서버가 준다 */}
        {detail.editable && (
          <Link className={styles.linkButton} href={`/inquiries/${detail.id}/edit${listQuery}`}>
            수정
          </Link>
        )}
        {detail.deletable && (
          <button type="button" className={styles.danger} onClick={handleDelete} disabled={deleting}>
            {deleting ? "삭제 중…" : "삭제"}
          </button>
        )}
      </div>

      <p className="note">
        수정·삭제 버튼은 <strong>본인이 쓴 미답변 문의</strong>에만 보입니다. 다만 이것은
        편의일 뿐이고, 실제 차단은 서버가 합니다 — 주소로 직접 들어와도 소유자와 답변 여부를
        다시 확인해 거부합니다.
      </p>
    </main>
  );
}
