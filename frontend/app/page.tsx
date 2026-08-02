"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import styles from "./home.module.css";
import { useSession } from "@/lib/useSession";
import {
  fetchMainSummary,
  type FreeLatest,
  type GalleryLatest,
  type InquiryLatest,
  type NoticeLatest,
} from "@/lib/board/main";

/**
 * 메인 페이지 — 요구사항 2장.
 *
 * 4개 게시판의 최신 글을 한 화면에 모은다. 각 영역에 `더보기+` 와 `new` 아이콘.
 *
 * ★ 한 영역이 실패해도 나머지는 보인다(`fetchMainSummary` 의 safe). 메인은 첫 화면이라
 *   게시판 하나의 오류로 전체가 빈 화면이 되면 서비스가 죽은 것처럼 보인다.
 */
export default function HomePage() {
  const session = useSession();
  const [notices, setNotices] = useState<NoticeLatest[]>([]);
  const [free, setFree] = useState<FreeLatest[]>([]);
  const [galleries, setGalleries] = useState<GalleryLatest[]>([]);
  const [inquiries, setInquiries] = useState<InquiryLatest[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    fetchMainSummary().then(([n, f, g, i]) => {
      if (cancelled) return;
      setNotices(n);
      setFree(f);
      setGalleries(g);
      setInquiries(i);
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main className="wide">
      <header className={styles.hero}>
        <h1>ebstudy 포털</h1>
        <p>
          {session.status === "authenticated" && session.user !== null
            ? `${session.user.name}님, 안녕하세요.`
            : "공지사항 · 자유게시판 · 갤러리 · 문의게시판"}
        </p>
      </header>

      <div className={styles.grid}>
        {/* ── 공지사항 ── */}
        <section className={styles.card}>
          <div className={styles.cardHead}>
            <h2>공지사항</h2>
            <Link href="/notices" className={styles.more}>
              더보기 +
            </Link>
          </div>
          <Rows
            loading={loading}
            empty={notices.length === 0}
            items={notices.map((n) => ({
              key: n.id,
              href: `/notices/${n.id}`,
              category: n.pinned ? "알림" : (n.categoryName ?? ""),
              title: n.title,
              isNew: n.isNew,
            }))}
          />
        </section>

        {/* ── 자유게시판 ── */}
        <section className={styles.card}>
          <div className={styles.cardHead}>
            <h2>자유게시판</h2>
            <Link href="/free" className={styles.more}>
              더보기 +
            </Link>
          </div>
          <Rows
            loading={loading}
            empty={free.length === 0}
            items={free.map((p) => ({
              key: p.id,
              href: `/free/${p.id}`,
              category: p.categoryName ?? "",
              title: p.title,
              isNew: p.newBadge,
              // 요구사항 2장 — 댓글 수 · 첨부 아이콘
              suffix: p.commentCount > 0 ? `(${p.commentCount})` : undefined,
              attachment: p.hasAttachment,
            }))}
          />
        </section>

        {/* ── 갤러리 ── */}
        <section className={styles.card}>
          <div className={styles.cardHead}>
            <h2>갤러리</h2>
            <Link href="/galleries" className={styles.more}>
              더보기 +
            </Link>
          </div>
          {loading ? (
            <p className={styles.empty}>불러오는 중…</p>
          ) : galleries.length === 0 ? (
            <p className={styles.empty}>등록된 글이 없습니다.</p>
          ) : (
            <div className={styles.gallery}>
              {galleries.map((g) => (
                <Link key={g.id} href={`/galleries/${g.id}`} className={styles.galleryRow}>
                  {g.thumbnailUrl !== null ? (
                    // eslint-disable-next-line @next/next/no-img-element -- 백엔드가 내려주는 첨부 스트림이라 next/image 최적화 대상이 아니다
                    <img className={styles.thumb} src={g.thumbnailUrl} alt="" />
                  ) : (
                    <span className={`${styles.thumb} ${styles.thumbEmpty}`}>없음</span>
                  )}
                  <span className={styles.galleryText}>
                    <strong>
                      {g.title}
                      {g.isNew && <Badge />}
                      {/* 요구사항 2장 — 첫 이미지를 제외한 개수 */}
                      {g.extraImageCount > 0 && (
                        <span className={styles.rowMeta}> +{g.extraImageCount}</span>
                      )}
                    </strong>
                    <span>{g.excerpt ?? ""}</span>
                  </span>
                </Link>
              ))}
            </div>
          )}
        </section>

        {/* ── 문의게시판 ── */}
        <section className={styles.card}>
          <div className={styles.cardHead}>
            <h2>문의게시판</h2>
            <span className={styles.myInquiry}>
              {/* 요구사항 2장 — 미로그인이면 로그인 화면을 거쳐 필터가 적용된 채로 돌아온다.
                  RequireAuth 가 아니라 next 파라미터로 목적지를 직접 넘긴다 */}
              <Link
                href={
                  session.status === "authenticated"
                    ? "/inquiries?mine=true"
                    : `/login?next=${encodeURIComponent("/inquiries?mine=true")}`
                }
                className={styles.more}
              >
                나의 문의내역
              </Link>
              {" · "}
              <Link href="/inquiries" className={styles.more}>
                더보기 +
              </Link>
            </span>
          </div>
          <Rows
            loading={loading}
            empty={inquiries.length === 0}
            items={inquiries.map((q) => ({
              key: q.id,
              href: `/inquiries/${q.id}`,
              category: "",
              title: q.title,
              isNew: q.isNew,
              suffix: q.answered ? "(답변완료)" : undefined,
              locked: q.secret,
            }))}
          />
        </section>
      </div>
    </main>
  );
}

// ── 표시용 조각 ────────────────────────────────────────────

function Badge() {
  return (
    <span className="badge-new" aria-label="새 글">
      N
    </span>
  );
}

interface RowItem {
  key: number;
  href: string;
  category: string;
  title: string;
  isNew: boolean;
  suffix?: string;
  attachment?: boolean;
  locked?: boolean;
}

function Rows({
  loading,
  empty,
  items,
}: {
  loading: boolean;
  empty: boolean;
  items: RowItem[];
}) {
  if (loading) return <p className={styles.empty}>불러오는 중…</p>;
  if (empty) return <p className={styles.empty}>등록된 글이 없습니다.</p>;

  return (
    <ul className={styles.rows}>
      {items.map((item) => (
        <li key={item.key}>
          <Link href={item.href} className={styles.row}>
            {item.category !== "" && <span className={styles.rowCat}>{item.category}</span>}
            <span className={styles.rowTitle}>
              {item.title}
              {item.suffix !== undefined && (
                <span className={styles.rowMeta}> {item.suffix}</span>
              )}
            </span>
            <span className={styles.rowMeta}>
              {item.locked === true && <span aria-label="비밀글">🔒</span>}
              {item.attachment === true && <span aria-label="첨부">📎</span>}
              {item.isNew && <Badge />}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
