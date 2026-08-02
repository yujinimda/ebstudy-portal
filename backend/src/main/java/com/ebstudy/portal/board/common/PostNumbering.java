package com.ebstudy.portal.board.common;

/**
 * 글 번호 — 요구사항 1.1 <i>"전체 게시글 수 기준 역순(행 번호가 아니다)"</i>.
 *
 * <p>왜 별도 클래스인가: 이 계산을 게시판마다 화면마다 다시 쓰면 반드시 하나가 어긋나고,
 * 어긋나면 <b>목록의 1번과 상세의 1번이 다른 글</b>이 된다. 계산은 한 곳에서만 한다.
 *
 * <p>규칙: 전체 100건 · 10개씩 볼 때
 * <ul>
 *   <li>1페이지({@code page=0}) 첫 줄 → 100</li>
 *   <li>1페이지 마지막 줄 → 91</li>
 *   <li>2페이지({@code page=1}) 첫 줄 → 90</li>
 * </ul>
 *
 * <p>⚠️ 이 번호는 <b>고정 식별자가 아니다.</b> 글이 하나 등록되면 모든 번호가 1씩 밀린다.
 * 상세 화면으로 가는 링크에는 반드시 {@code id} 를 쓴다 — 번호를 쓰면 새 글이 등록된 뒤
 * 다른 글이 열린다. 요구사항 3.1 의 고정 글이 번호 대신 {@code 알림} 을 보여주는 것도
 * 같은 이유다(고정 글은 번호 체계 밖에 있다).
 */
public final class PostNumbering {

    private PostNumbering() {
    }

    /**
     * @param totalElements 그 게시판의 전체 글 수 — 검색 결과 수가 아니다.
     *                      검색 결과 수를 넣으면 검색할 때마다 같은 글의 번호가 달라진다.
     *                      ▶ 다만 요구사항 문구는 "전체 게시글 수" 뿐이라 어느 쪽인지 단정하지
     *                      않는다. 이 구현은 <b>현재 검색 조건의 전체 결과 수</b>를 넣는 쪽도
     *                      허용한다 — 무엇을 넣을지는 호출부가 정하고 그 판단을 남긴다
     * @param page          0부터
     * @param indexInPage   현재 페이지 안에서 0부터
     */
    public static long displayNumber(long totalElements, int page, int size, int indexInPage) {
        if (page < 0 || size <= 0 || indexInPage < 0) {
            throw new IllegalArgumentException("page/size/indexInPage 는 음수일 수 없다");
        }
        return totalElements - ((long) page * size) - indexInPage;
    }

    /** 한 페이지 분량을 한 번에 — 목록 매핑에서 인덱스를 손으로 세지 않게 한다. */
    public static long[] displayNumbers(long totalElements, int page, int size, int itemCount) {
        long[] numbers = new long[Math.max(itemCount, 0)];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = displayNumber(totalElements, page, size, i);
        }
        return numbers;
    }
}
