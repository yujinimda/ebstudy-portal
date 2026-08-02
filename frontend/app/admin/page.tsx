import Link from "next/link";
import styles from "./admin.module.css";

/**
 * 관리자 홈 — 요구사항 7.1.
 *
 * 인증·권한 확인과 LNB 는 `layout.tsx`(→ `AdminShell`)가 이미 하고 있다.
 * 그래서 이 화면은 **서버 컴포넌트**로 두고 링크만 그린다 — 상태가 없는 화면을
 * 굳이 클라이언트로 만들 이유가 없다.
 */
const CARDS = [
  { href: "/admin/notices", title: "공지사항 관리", desc: "등록 · 수정 · 삭제 · 상단 고정" },
  { href: "/admin/free-posts", title: "자유 게시판 관리", desc: "목록 · 상세 · 삭제 · 댓글 삭제" },
  { href: "/admin/galleries", title: "갤러리 게시판 관리", desc: "목록 · 상세 · 삭제" },
  { href: "/admin/inquiries", title: "문의 게시판 관리", desc: "목록 · 답변 등록/수정 · 삭제" },
  { href: "/admin/categories", title: "분류(카테고리) 관리", desc: "게시판별 분류 · 사용 여부" },
] as const;

export default function AdminHomePage() {
  return (
    <>
      <h1>관리자</h1>
      <p className="subtitle">좌측 메뉴에서 관리할 게시판을 선택하세요.</p>

      <div className={styles.homeGrid}>
        {CARDS.map((card) => (
          <Link key={card.href} href={card.href} className={styles.homeCard}>
            <strong>{card.title}</strong>
            <span>{card.desc}</span>
          </Link>
        ))}
      </div>

      <p className="note">
        관리 API 는 모두 <code>/api/admin/**</code> 입니다. 화면에서 메뉴를 감추는 것은 권한
        검증이 아니며, 모든 요청은 서버가 ADMIN 권한을 다시 확인합니다(FR-019 · AC-26).
      </p>
    </>
  );
}
