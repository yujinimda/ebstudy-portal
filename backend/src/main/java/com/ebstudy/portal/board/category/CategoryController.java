package com.ebstudy.portal.board.category;

import com.ebstudy.portal.board.common.BoardType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분류 조회 — <b>누구나</b>. 요구사항 1.1 목록 화면의 분류 드롭다운이 부른다.
 *
 * <p>⚠️ <b>{@code SecurityConfig} 조치가 필요하다.</b> 현재 {@code .anyRequest().authenticated()}
 * 라 이 경로도 401 이 된다. {@code GET /api/categories} 를 {@code permitAll} 로 열어야 한다
 * (요구사항 1.3 "목록·상세 조회는 누구나"). {@code auth/} 는 이 패키지 담당이 아니라 손대지 않았다.
 *
 * <p>경로를 {@code /api/boards/{boardType}/categories} 로 하지 않은 이유: 게시판 4종 백엔드가
 * 지금 병렬로 {@code /api/boards/**} 아래에 컨트롤러를 만들고 있다. 같은 패턴을 두 곳이
 * 매핑하면 <b>부팅이 매핑 충돌로 깨진다</b>. 관리 경로({@code /api/admin/categories})와
 * 모양이 같아지는 이점도 있다.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryService categoryQuery;

    /**
     * 해당 게시판의 <b>활성</b> 분류를 표시 순서대로.
     *
     * <p>{@code 전체 분류} 항목(요구사항 1.1)은 <b>서버가 만들지 않는다</b> — 그것은
     * "조건 없음"이라는 화면의 상태이지 DB 에 있는 분류가 아니다. id 를 붙여 내려보내면
     * 그 가짜 id 가 검색 조건으로 되돌아온다.
     *
     * @param boardType {@code NOTICE} · {@code FREE} · {@code GALLERY}.
     *                  {@code INQUIRY} 는 분류가 없어 400 이다(요구사항 0장 표)
     */
    @GetMapping
    public List<CategoryResponse> list(@RequestParam("boardType") String boardType) {
        return categoryQuery.activeCategoryResponses(BoardType.from(boardType));
    }
}
