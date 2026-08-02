package com.ebstudy.portal.board.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 목록 응답 — 요구사항 1.1 페이징({@code << < 1~10 > >>}).
 *
 * <p>Spring 의 {@code Page} 를 그대로 응답하지 않는 이유: {@code Page} 의 JSON 모양은
 * 스프링 버전에 따라 바뀌고({@code pageable}·{@code sort} 객체가 통째로 딸려 나온다)
 * 화면이 그 모양에 묶인다. 우리가 계약을 정한다.
 *
 * @param items         현재 페이지의 항목
 * @param totalElements 전체 개수 — 요구사항 1.1 "글 번호는 전체 게시글 수 기준 역순" 의 그 수다
 * @param totalPages    전체 페이지 수 — {@code >>}(마지막) 버튼이 이 값을 쓴다
 * @param page          0부터 센다. 화면 표기(1부터)는 프론트가 한다
 */
public record PageResponse<T>(List<T> items, long totalElements, int totalPages, int page,
        int size) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize());
    }

    /**
     * 엔티티 페이지를 DTO 페이지로 바꾼다 — <b>엔티티를 그대로 응답하지 않는다</b>는 규약을
     * 호출부마다 다시 쓰지 않게 한다.
     */
    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }

    /** 이미 매핑을 끝낸 목록으로 만든다(댓글 수·썸네일을 한 번에 붙인 뒤 쓰는 경로). */
    public static <S, T> PageResponse<T> of(Page<S> page, List<T> mappedItems) {
        return new PageResponse<>(mappedItems, page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize());
    }
}
