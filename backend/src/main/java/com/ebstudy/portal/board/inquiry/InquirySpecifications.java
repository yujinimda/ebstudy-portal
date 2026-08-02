package com.ebstudy.portal.board.inquiry;

import com.ebstudy.portal.board.common.BoardSearchCriteria;
import com.ebstudy.portal.board.common.Post;
import com.ebstudy.portal.board.common.PostSpecifications;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * ★★ 문의게시판 목록 조건 — <b>공통 규칙 1.1 에서 의도적으로 이탈하는 유일한 지점</b>이다
 * (FR-004 · AC-5 · AC-6 · 판단 3).
 *
 * <h2>왜 검색 조건을 따로 만드는가 — 검색은 열람 통로다</h2>
 * 공통 규칙 1.1 은 문의게시판의 검색 범위를 <b>제목·내용·등록자</b>로 정했다.
 * 그런데 그대로 두면 <b>비밀번호를 한 번도 맞히지 않고 비밀글의 내용을 복원</b>할 수 있다:
 * <pre>
 *   검색 "환불계좌"    → 걸린다  ⇒ 그 글 내용에 "환불계좌" 가 있다
 *   검색 "환불계좌 1"  → 걸린다
 *   검색 "환불계좌 12" → 안 걸린다 ⇒ 다음 글자는 2가 아니다
 * </pre>
 * <b>검색 결과의 유무가 내용을 한 글자씩 알려준다.</b> 비밀번호 시도 제한
 * ({@link SecretPasswordAttemptService})을 아무리 촘촘하게 걸어도 이 통로는 그 옆으로 지나간다.
 * 비밀글을 만든 이유가 그 자리에서 무너지므로, <b>내용 조건만</b> 열람 권한과 묶는다.
 *
 * <h2>왜 {@code PostSpecifications} 를 통째로 다시 쓰지 않는가</h2>
 * 기간·나의 글 같은 조건이 4벌이 되면 반드시 갈라진다(공통 기반이 경고한 그것).
 * 그래서 <b>검색어 절만</b> 여기서 만들고 나머지는 공통 것을 그대로 위임한다.
 * 검색어를 뺀 조건으로 공통을 부르고, 검색어는 {@code AND} 로 덧붙인다.
 */
final class InquirySpecifications {

    /** {@code LIKE ... ESCAPE '\'} — {@code BoardSearchCriteria.likePattern()} 과 짝이다. */
    private static final char LIKE_ESCAPE = '\\';

    private InquirySpecifications() {
    }

    /**
     * @param viewerId       로그인한 사용자 id. 비로그인이면 {@code null}
     * @param contentAlways  {@code true} 면 내용 조건에 제한을 두지 않는다({@code ADMIN} — AC-6).
     *                       관리자는 어차피 본문을 볼 수 있으므로 검색에서 빼면 기능만 불편해지고
     *                       막아지는 것이 없다
     */
    static Specification<Post> search(BoardSearchCriteria criteria, Long viewerId,
            boolean contentAlways) {
        Specification<Post> base = PostSpecifications.search(withoutKeyword(criteria), viewerId);
        if (!criteria.hasKeyword()) {
            return base;
        }
        return base.and(keyword(criteria.likePattern(), viewerId, contentAlways));
    }

    /**
     * 검색어만 뺀 조건. {@code BoardSearchCriteria} 는 record 라 정규 생성자로 다시 만들 수 있고,
     * 여기 들어오는 값들은 <b>이미 {@code of(...)} 의 검증을 통과한 것</b>이다
     * (검증을 우회하는 것이 아니라, 검증된 값을 한 칸만 비운다).
     */
    private static BoardSearchCriteria withoutKeyword(BoardSearchCriteria criteria) {
        return new BoardSearchCriteria(criteria.boardType(), criteria.from(), criteria.to(),
                criteria.categoryId(), null, criteria.mineOnly(), criteria.page(), criteria.size(),
                criteria.sort(), criteria.direction());
    }

    private static Specification<Post> keyword(String pattern, Long viewerId,
            boolean contentAlways) {
        return (root, query, cb) -> {
            // OR 조건이므로 LEFT 다 — INNER 로 붙이면 제목만 맞는 행까지 사라진다
            Join<Object, Object> author = root.join("author", JoinType.LEFT);

            Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE);
            Predicate authorMatch = cb.like(cb.lower(author.get("name")), pattern, LIKE_ESCAPE);
            Predicate contentMatch = cb.like(cb.lower(root.get("content")), pattern, LIKE_ESCAPE);

            // ★ 제목과 등록자는 누구에게나 검색된다 — 목록에서 이미 보이는 값이라 새로 새는 것이
            //   없고(AC-22), 가리면 사용자가 자기 글조차 목록에서 못 찾는다(판단 14).
            //   막는 것은 내용 하나다
            return cb.or(titleMatch, authorMatch, cb.and(contentMatch,
                    contentVisible(root, cb, viewerId, contentAlways)));
        };
    }

    private static Predicate contentVisible(jakarta.persistence.criteria.Root<Post> root,
            jakarta.persistence.criteria.CriteriaBuilder cb, Long viewerId, boolean contentAlways) {
        if (contentAlways) {
            return cb.conjunction();
        }
        Predicate notSecret = cb.isFalse(root.get("secret"));
        if (viewerId == null) {
            return notSecret;
        }
        // 자기 비밀글의 내용은 자기에게 검색된다(AC-6)
        return cb.or(notSecret, cb.equal(root.get("author").get("id"), viewerId));
    }
}
