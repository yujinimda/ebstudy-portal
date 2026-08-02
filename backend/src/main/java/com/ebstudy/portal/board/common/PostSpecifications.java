package com.ebstudy.portal.board.common;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 목록 조회 조건 조립 — 요구사항 1.1.
 *
 * <p><b>여기가 게시글 목록 쿼리를 만드는 유일한 자리다.</b> JPQL 문자열을 이어 붙이지 않고
 * Criteria API 로 조립하는 이유는 두 가지다:
 * <ol>
 *   <li>사용자 입력이 <b>전부 바인딩 파라미터</b>가 된다 — 쿼리 문자열에 닿지 않는다</li>
 *   <li>조건이 선택적으로 조합되는데(기간·분류·검색어·나의 글) 문자열이면 조합마다 분기가 생긴다</li>
 * </ol>
 */
public final class PostSpecifications {

    /** {@code LIKE ... ESCAPE '\'} — {@code BoardSearchCriteria.likePattern()} 과 짝이다. */
    private static final char LIKE_ESCAPE = '\\';

    private PostSpecifications() {
    }

    /**
     * 요구사항 1.1 목록 조건 전체.
     *
     * @param viewerId 요구사항 6.1 "나의 문의내역만 보기" 의 기준. 비로그인이면 {@code null}
     *                 (그 경우 {@code criteria.mineOnly()} 는 이미 꺼져 있다)
     */
    public static Specification<Post> search(BoardSearchCriteria criteria, Long viewerId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("boardType"), criteria.boardType()));
            // 기간은 등록일시 기준(요구사항 1.1). between 은 양끝 포함이다
            predicates.add(cb.between(root.get("createdAt"), criteria.from(), criteria.to()));

            if (criteria.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), criteria.categoryId()));
            }

            if (criteria.mineOnly() && viewerId != null) {
                predicates.add(cb.equal(root.get("author").get("id"), viewerId));
            }

            if (criteria.hasKeyword()) {
                String pattern = criteria.likePattern();
                List<Predicate> keywordMatches = new ArrayList<>();
                // lower() 양쪽에 걸어 대소문자를 무시한다 — 검색은 유일성 판정이 아니므로
                // V1 처럼 함수 인덱스를 만들지 않는다(부분 일치라 어차피 인덱스를 못 탄다, V6 하단 주석)
                keywordMatches.add(cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE));
                keywordMatches.add(cb.like(cb.lower(root.get("content")), pattern, LIKE_ESCAPE));
                if (criteria.boardType().keywordIncludesAuthor()) {
                    // 요구사항 0장 — 공지사항만 등록자를 검색 범위에서 뺀다.
                    // LEFT 조인인 이유: 조건이 OR 이므로 INNER 로 붙이면 제목만 맞는 행까지 사라진다
                    Join<Object, Object> author = root.join("author", JoinType.LEFT);
                    keywordMatches.add(cb.like(cb.lower(author.get("name")), pattern, LIKE_ESCAPE));
                }
                predicates.add(cb.or(keywordMatches.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 요구사항 3.1 — 상단 고정 글은 <b>모든 페이지 상단</b>에 따로 붙는다.
     * 그래서 본 목록에서는 빼야 한다. 빼지 않으면 1페이지에 같은 글이 두 번 나온다.
     *
     * <p>{@code search(...).and(notPinned())} 처럼 붙여 쓴다.
     *
     * <p>★ V11 이후 {@code pinned} 는 {@code Post} 가 아니라 {@link NoticePost} 에 있다.
     * 그래서 {@code root.get("pinned")} 로는 못 찾는다 —
     * {@code treat()} 로 <b>하위 타입으로 내려다본 뒤</b> 칸을 읽는다.
     * ({@code JOINED} 라 SQL 로는 {@code notice_posts} 조인이 된다)
     */
    public static Specification<Post> notPinned() {
        return (root, query, cb) ->
                cb.isFalse(cb.treat(root, NoticePost.class).get("pinned"));
    }

    /** 요구사항 6.1 — "나의 문의내역" 링크로 바로 들어온 경우처럼 조건을 강제로 걸 때. */
    public static Specification<Post> authoredBy(Long authorId) {
        return (root, query, cb) -> cb.equal(root.get("author").get("id"), authorId);
    }
}
