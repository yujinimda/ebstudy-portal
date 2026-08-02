# ebstudy-portal

게시판 포털(게시판 4종 × 사용자/관리자)을 만들면서 자바·백엔드를 배운다.

**2026-08-02 초기화.** AI가 일괄 생성한 코드와 계획을 전부 지우고
PRD부터 손으로 다시 세운다.

## 지금 있는 것

| | |
|---|---|
| `private/` | 요구사항 원본 (커밋 안 됨) |
| `specs/process.md` | 작업 방식 규칙 |
| `specs/learning/` | 학습 노트 7개 (DB 주제) |
| `.specify/` | 스펙 템플릿 · constitution |

## 되돌리기

지운 것은 전부 `backup/ai-generated-20260802` 브랜치에 있다.

```bash
git show backup/ai-generated-20260802:backend/...   # 파일 하나 보기
git checkout backup/ai-generated-20260802 -- <경로>  # 파일 가져오기
```
