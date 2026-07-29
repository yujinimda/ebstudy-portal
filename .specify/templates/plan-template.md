# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION]

**Primary Dependencies**: [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION]

**Storage**: [if applicable, e.g., PostgreSQL, CoreData, files or N/A]

**Testing**: [e.g., pytest, XCTest, cargo test or NEEDS CLARIFICATION]

**Target Platform**: [e.g., Linux server, iOS 15+, WASM or NEEDS CLARIFICATION]

**Project Type**: [e.g., library/cli/web-service/mobile-app/compiler/desktop-app or NEEDS CLARIFICATION]

**Performance Goals**: [domain-specific, e.g., 1000 req/s, 10k lines/sec, 60 fps or NEEDS CLARIFICATION]

**Constraints**: [domain-specific, e.g., <200ms p95, <100MB memory, offline-capable or NEEDS CLARIFICATION]

**Scale/Scope**: [domain-specific, e.g., 10k users, 1M LOC, 50 screens or NEEDS CLARIFICATION]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution v1.0.0 기준. 위반이 있으면 아래 Complexity Tracking 표에 근거를 적고
ADR(`specs/decisions/`)을 남긴다. 근거 없는 예외는 없다.

- [ ] **I. 테스트 우선** — 테스트를 구현보다 먼저 작성하는 순서가 계획에 반영됐는가?
      밀집도가 API 통합 중심인가(통합 주력 / 단위는 복잡 로직만 / E2E 총 5~10개)?
      응답·권한·경계값을 E2E가 아니라 API 통합 테스트로 내렸는가?
- [ ] **II. 검증 가능한 인수기준** — 스펙의 모든 AC가 `Given/When/Then + AC-ID` 이고
      사전조건(Given)이 명시됐는가? 실행 불가능한 문장이 없는가?
      **커버리지 게이트**: AC 하나도 빠짐없이 테스트를 갖도록 계획됐는가(AC 커버리지 100%)?
      묶음 AC는 참조하는 AC마다 따로 테스트가 잡혔는가?
      **줄 커버리지 숫자 목표를 게이트로 세우지 않았는가**(측정·리포트는 가능)?
- [ ] **III. 사람이 하는 결정** — DB 논리 설계와 마이그레이션 SQL이 사람 작업으로 배정됐는가?
      되돌리기 비용이 큰 결정에 ADR이 계획돼 있는가?
- [ ] **IV. 에러 누수 차단** — 전역 예외 핸들러 단일화, 예외 2분류(4xx 노출 / 5xx 비노출),
      RFC 9457 + `code`/`traceId`, 리소스 존재 여부 비노출(404)이 설계에 있는가?
- [ ] **V. 관측 가능성** — stdout 전용 로깅, MDC `traceId`(에러 응답과 동일 값),
      민감정보 마스킹이 설계에 있는가?
- [ ] **VI. 되돌릴 수 있는 스키마** — Flyway 버전 파일, `ddl-auto=validate`,
      적용된 파일 수정 금지, 파괴적 변경은 expand-contract 2단계 분할인가?
- [ ] **VII. 시크릿** — 새로 필요한 키가 `.env.example`에만 이름으로 추가되고
      설정 파일에 하드코딩되지 않는가?
- [ ] **VIII. 도구가 강제하는 게이트** — 새 검증이 CI 잡으로 추가되고 필수 상태 체크에
      등록되는가? 사람 게이트를 스펙 명확화·기능 QA 외에 늘리지 않았는가?
- [ ] **보안 요구사항** — 비밀번호 해싱(bcrypt/argon2, 빠른 해시·DB 함수 금지),
      토큰 만료 필수, `httpOnly` 쿠키 저장, 권한은 서버 검증, 비밀번호 최소 8자를 지키는가?

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# [REMOVE IF UNUSED] Option 1: Single project (DEFAULT)
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [REMOVE IF UNUSED] Option 2: Web application (when "frontend" + "backend" detected)
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [REMOVE IF UNUSED] Option 3: Mobile + API (when "iOS/Android" detected)
api/
└── [same as backend above]

ios/ or android/
└── [platform-specific structure: feature modules, UI flows, platform tests]
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
