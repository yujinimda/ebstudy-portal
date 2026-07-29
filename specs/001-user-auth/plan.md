# Implementation Plan: 001 인증 — 회원가입 · 로그인 · 로그아웃 · 권한 기반 접근제어

**Branch**: `001-user-auth` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)

**Input**: `specs/001-user-auth/spec.md` (AC 34개 · FR 34개) ·
`specs/data-model.md`(3.5 논리 설계) · `specs/test-strategy.md`(3.9) ·
`ADR-001`(JWT) · `ADR-002`(기본키) · `ADR-003`(PostgreSQL) · `ADR-004`(ORM)

**Phase 0**: [research.md](./research.md) — **NEEDS CLARIFICATION 0건**

---

## Summary

**만들 것**: 아이디·비밀번호로 가입하고 로그인하며, `USER`/`ADMIN` 권한에 따라 접근이 갈리는
인증 체계. **관리자는 진입점과 자격증명 수명을 분리**한다(메커니즘은 공유).

**기술 접근**: `httpOnly` 쿠키로 전달하는 **짧은 Access(JWT) + 긴 Refresh(랜덤 문자열, DB 보관)**.
Refresh를 DB에 두는 이유는 **로그아웃을 성립시키기 위한 것 하나**다(`ADR-001`).
모든 검증·권한 판정은 서버에서 하고, 실패 응답은 **아이디 존재 여부를 구분할 수 없게 통일**한다.

**이 스토리를 관통하는 설계 축**: **실패는 전부 같은 얼굴이어야 한다.**
`AC-2`(틀린 비밀번호) · `AC-3`(없는 아이디) · `AC-32`(관리자 진입점에 `USER` 계정)가
**상태코드·오류코드·메시지가 모두 같아야** 한다. 계정 열거를 막는 것이 이 스토리의
가장 깨지기 쉬운 요구사항이고, 세 경로 중 하나만 달라도 뚫린다.

---

## Technical Context

**Language/Version**: **Java 25 LTS** (백엔드) / **TypeScript** (프론트) — `process.md` 6.1

**Primary Dependencies**:
- 백엔드 — **Spring Boot 4.1** (Spring Framework 7), Spring Security(JWT 내장 · bcrypt),
  **Spring Data JPA** (`ADR-004`), Flyway, Bean Validation
- 프론트 — **Next.js** (사용자·관리자 한 앱, 경로 분리)
- 빌드 — **Gradle (Kotlin DSL)**

**Storage**: **PostgreSQL** (`ADR-003`). 테이블 3개 — 유저 · 재발급 티켓 · 사용불가 아이디
(`data-model.md`). **남용 방어 카운터는 DB가 아니라 서버 메모리**(research.md 5).

**Testing**: **API 통합 테스트 중심**(Testing Trophy) + **Testcontainers로 실제 PostgreSQL** +
**Playwright E2E 5~10개**. 격리는 트랜잭션 롤백 기본, 동시성 테스트만 초기화.
**커버리지 게이트 = AC 100%**, 줄 커버리지는 리포트만. — `test-strategy.md`

**Target Platform**: 로컬 **Docker Compose**(postgres + backend + frontend).
**배포 환경은 미정이며 방아쇠까지 미룬다** — `process.md` 6.2

**Project Type**: 웹 서비스 (자바 백엔드 + Next.js 프론트, 한 레포)

**Performance Goals**: 명시적 목표를 두지 않는다. 스펙의 Success Criteria는 전부
**기능·보안 기준**이고 처리량 요구가 없다. **다만 남용 방어의 지연이 스레드를 점유하지 않아야
한다는 제약이 있다** → 가상 스레드 활성화(research.md 6).

**Constraints**:
- `httpOnly` 쿠키 — **JS가 자격증명을 만질 수 없다**(`AC-7`). 프론트 설계가 이 제약을 받는다
- **실패 응답 3경로 동일**(`AC-2`·`AC-3`·`AC-32`)
- 관리자 초기 비밀번호가 **저장소에 어떤 형태로도 남지 않아야 한다**(`FR-023`, 저장소 공개)
- 마이그레이션 SQL은 **사람이 직접**(원칙 VI), 6.7에서 일괄

**Scale/Scope**: 화면 6개(사용자 3 + 관리자 3) · API 엔드포인트 8개 · 테이블 3개 ·
AC 34개(001에서 검증 33개, `AC-25`는 006 이관)

---

## Constitution Check

*GATE: Phase 0 이전 통과 필수. Phase 1 이후 재검토.*

**현행 constitution 기준**(버전은 `.specify/memory/constitution.md` 하단 참조 — 이 문서에
버전을 박지 않는다. 박으면 개정 때마다 어긋난다).
위반이 있으면 Complexity Tracking 표에 근거를 적고 ADR을 남긴다.

- [x] **I. 테스트 우선** — 7번 레인에서 **코덱스가 스펙만 보고 테스트를 먼저 작성**하고
      클코가 통과시킨다. 밀집도는 통합 주력이며 **응답·오류코드·권한·경계값을 전부 통합
      테스트로 내렸다**(아래 테스트 배정 표). E2E는 **6개**로 5~10개 범위 안이다.
- [x] **II. 검증 가능한 인수기준 + 커버리지 게이트** — AC 34개 전부 `Given/When/Then + AC-ID`다.
      **AC 하나당 테스트 하나 이상**을 배정했고 묶음 AC(`AC-19`)는 **참조하는 `AC-11`~`AC-18`
      각각에 따로** 잡았다. **줄 커버리지 숫자 게이트를 세우지 않았다**(리포트만).
      → `AC-25`는 001에 리소스가 없어 **006에서 검증하도록 명시 이관**한다(research.md 12).
- [x] **III. 사람이 하는 결정** — 논리 설계는 3.5에서 사람이 작성 완료.
      **마이그레이션 SQL은 6.7에서 사람 단독**으로 배정됐다.
      되돌리기 비용이 큰 결정에 ADR 4건(`ADR-001`~`004`).
- [x] **IV. 에러 누수 차단** — `@RestControllerAdvice` 하나로 단일화. 예외 2분류
      (4xx 노출 / 5xx 비노출). RFC 9457 + `code`/`traceId`.
      **`AC-28`이 테이블명·컬럼명·제약조건명 비노출을 직접 검증**한다.
- [x] **V. 관측 가능성** — stdout JSON 로깅, MDC `traceId`(오류 응답과 동일 값 — `AC-27`),
      **비밀번호·토큰 마스킹**(`FR-026`).
- [x] **VI. 되돌릴 수 있는 스키마** — Flyway 버전 파일, **`ddl-auto=validate`**
      (이것이 `ADR-004`에서 JPA를 고른 결정적 근거다), 적용된 파일 수정 금지.
      001은 신규 테이블 생성만이라 **파괴적 변경이 없어** expand-contract가 발동하지 않는다.
- [x] **VII. 시크릿** — `.env.example`에 키 이름만. `JWT_SECRET` · `ADMIN_INITIAL_*` ·
      DB 접속 정보 + 이번에 추가하는 수명·임계값 키. **설정 파일에 하드코딩 없음**(`${KEY}` 참조).
- [x] **VIII. 도구가 강제하는 게이트** — CI에 `build` / `test`(단위+통합) / `e2e`(@smoke) /
      `coverage-report` 잡을 추가하고 **필수 상태 체크에 등록**한다.
      사람 게이트는 스펙 명확화·기능 QA 둘만 유지(늘리지 않음).
- [x] **보안 요구사항** — **bcrypt**(빠른 해시·DB 함수 금지 — 원 요구사항의 `SHA2()`를 8.2에서
      기각), Access·Refresh 둘 다 만료 있음, `httpOnly` 쿠키, 권한은 서버 검증,
      비밀번호 최소 8자.
      → **티켓 식별 값에 SHA-256(빠른 해시)을 쓰는 것이 예외처럼 보이지만 아니다.**
      보안 요구사항은 **비밀번호** 해싱을 규정한다. 티켓은 256비트 난수라 무차별 대입 대상이
      아니고, **bcrypt는 솔트가 매번 달라 조회에 쓸 수 없다**(research.md 3).

**결과: 위반 0건.** Complexity Tracking 비움.

### 이 계획이 constitution에 되돌린 것

계획을 쓰다 **규칙 쪽의 빈틈 2개**가 드러났다. 규칙을 어긴 것이 아니라 규칙이 덜 적혀 있었다.

| | 무엇 | 처리 |
|---|---|---|
| 1 | 커버리지 게이트에 **"다른 스토리에서 검증되는 AC"** 규칙이 없었다 → *미룬 AC*와 *잊은 AC*가 구분되지 않는다 | `test-strategy.md` 5.3에 규칙 추가 |
| 2 | `plan-template.md`가 **"Constitution v1.0.0"을 하드코딩**하고 있었다 → 1.3.0인데 문서가 1.0.0이라고 말한다 | 템플릿에서 버전 하드코딩 제거 |

---

## Project Structure

### Documentation (this feature)

```text
specs/001-user-auth/
├── plan.md              # 이 파일
├── research.md          # Phase 0 — 기술 결정 12건
├── quickstart.md         # Phase 1 — 검증 시나리오
├── contracts/
│   └── auth-api.md       # Phase 1 — API 계약 (AC 대응 포함)
├── spec.md              # 2번 산출물
└── tasks.md             # 6번 (/speckit-tasks) — 아직 없음
```

> **`data-model.md`를 이 폴더에 만들지 않는다.**
> spec-kit 템플릿은 Phase 1에 `specs/<feature>/data-model.md`를 만들라고 하지만,
> **`process.md` 3장이 `specs/data-model.md`를 전역 누적 문서로 정했다**
> (*"ORM 선택은 001에서 결정했어도 002·003 전부에 적용되는데 001 폴더에 묻히면 찾을 수 없음"*
> 과 같은 이유). 스토리 폴더에 복제하면 **두 문서가 갈라진다.**
> `process.md`가 단일 진실이므로 전역 문서를 참조한다. **물리 설계 추가분도 그 문서에 이어 붙인다.**

### Source Code (repository root)

```text
backend/                          # Java 25 · Spring Boot 4.1 · Gradle(Kotlin DSL)
├── src/main/java/.../
│   ├── user/                     # 가입 · 조회 · 사용불가 아이디
│   ├── auth/                     # 로그인 · 로그아웃 · 재발급 · 토큰 발급/검증
│   │   └── ratelimit/            # 남용 방어 카운터 2개 (메모리)
│   ├── admin/                    # 관리자 진입점 · 초기 계정 시딩
│   └── common/                   # 전역 예외 핸들러 · traceId 필터 · 오류 코드
├── src/main/resources/
│   ├── application.yml           # ${KEY} 참조만
│   └── db/migration/             # V1__*.sql … ← 6.7에서 사람이 작성
└── src/test/java/.../
    ├── unit/                     # 비밀번호 규칙 · 지연 계산 등 복잡 로직만
    └── integration/              # ★ 주력. Testcontainers + 실제 PostgreSQL

frontend/                         # Next.js
├── app/
│   ├── (user)/login · signup · posts/new
│   └── admin/login · (dashboard)
├── next.config.ts                # ★ /api/* → 백엔드 rewrite (오리진 하나)
└── e2e/                          # Playwright
    ├── auth.setup.ts             # ★ storageState 생성 (test-strategy.md 4.1)
    └── *.spec.ts

docker-compose.yml                # postgres + backend + frontend
```

**Structure Decision**: **한 레포에 `backend/` + `frontend/` 2개 프로젝트.**

- 프론트와 백엔드가 **다른 언어·다른 빌드도구**라 물리적으로 갈라야 한다
- 한 레포에 두는 이유: **PR 하나가 스토리 하나**여야 하고(`process.md` 5장), 인증은
  프론트와 백엔드가 함께 바뀌는 기능이다. 레포를 갈랐으면 **PR 2개가 짝을 맞춰야** 한다
- **`/api/*` rewrite로 브라우저에게는 오리진이 하나**다(research.md 2)

---

## AC → 테스트 레벨 배정

`test-strategy.md` 3장의 배정 기준을 적용했다. **AC 하나당 최소 하나, 묶음은 참조마다 따로.**

| AC | 무엇 | 레벨 | 비고 |
|---|---|---|---|
| AC-1 | 로그인 성공 + 쿠키 전달 + **본문에 토큰 없음** | 통합 | |
| AC-2·3 | 실패 응답 **동일성** | 통합 | **두 응답을 서로 비교하는 테스트를 따로 둔다** |
| AC-4 | Access 만료 후 자동 재발급 | 통합 | 수명을 짧게 오버라이드해 검증 |
| AC-5 | 로그아웃 후 재발급 차단 | 통합 | |
| AC-6 | Refresh 만료 | 통합 | |
| AC-7 | 스크립트로 쿠키 못 읽음 | **E2E** | 브라우저가 있어야 의미가 있다 |
| AC-8 | 가입 성공 | 통합 | |
| AC-9 | 비밀번호 평문 아님 | 통합 | DB를 직접 조회해 확인 |
| AC-10 | 중복 아이디 | 통합 | |
| AC-11~18 | 입력 검증 8종 | 통합 | 경계값(3/4자, 11/12자, 7/8자, 64/65자, 1/2자, 50/51자) |
| **AC-19** | 직접 호출 우회 차단 | 통합 | **`AC-11`~`18` 각각에 대해 따로.** 묶어서 하나로 만들지 않는다 |
| AC-20 | 중복확인 ↔ 가입 결과 일치 | 통합 | |
| AC-21·22·23 | 미인증 401 / `USER` 403 / `ADMIN` 200 | 통합 | 대상은 `GET /api/admin/me` |
| AC-24 | 로그인 후 **원래 목적지 복귀** | **E2E** | 화면 이동이라 브라우저 필요 |
| AC-25 | 인증 없이 문의 목록 조회 | **006으로 이관** | 001에 리소스가 없다 |
| AC-26 | 화면에서 감춘 기능도 서버가 막음 | 통합 | `AC-22`와 같은 요청, **다른 관점**이라 따로 둔다 |
| AC-27 | 모든 오류에 `traceId` | 통합 | **로그에서 같은 값을 찾는 것까지** 검증 |
| AC-28 | 5xx에 내부 정보 비노출 | 통합 | 테이블명·컬럼명·제약조건명 문자열 부재 확인 |
| AC-29 | 점진적 지연 + 429 | 통합 | 지연 시간은 주입 가능한 시계로 검증 |
| AC-30 | 중복확인 빈도 제한 | 통합 | |
| AC-31 | 관리자 별도 진입점 로그인 | 통합 | |
| AC-32 | 관리자 진입점에 `USER` → **AC-2와 동일** | 통합 | **AC-2·3과 3자 비교** |
| AC-33 | 관리자 수명이 더 짧다 | 통합 | 발급된 만료 시각 비교 |
| **AC-34** | **오픈 리다이렉트 차단** (신규) | **E2E** | 브라우저가 이동을 수행하므로 |

**E2E 6개** (5~10개 범위): AC-7 · AC-24 · AC-34 + 가입→로그인→로그아웃 정상 흐름 ·
관리자 로그인→관리자 화면 · **로그인 화면 UI 자체 검증 1개**
(`test-strategy.md` 4.1이 요구한 것 — 전부 API로 우회하면 로그인 버튼이 깨져도 아무 테스트도 실패하지 않는다).

**AC 커버리지: 33/33** (001 범위). `AC-25`는 006.

---

## 구현 순서 — 테스트가 먼저 (원칙 I)

스펙의 우선순위를 따른다. **US1이 관리자 자동 생성 덕에 회원가입 없이 완결 가능**하다.

| 순서 | 무엇 | 왜 이 순서 |
|---|---|---|
| 0 | 6.7 마이그레이션 SQL (사람) · Docker Compose · CI 잡 | 스키마가 전 레인 공유 지점 |
| 1 | 공통 — 전역 예외 핸들러 · `traceId` · 오류 코드 | `AC-27`·`AC-28`이 **모든 응답에 걸린다.** 나중에 넣으면 전부 고쳐야 한다 |
| 2 | **US1** 로그인·로그아웃·재발급 + 관리자 초기 생성 | 관리자 계정이 자동 생성되므로 가입 없이 검증 가능 |
| 3 | **US1** 관리자 진입점 분리 (`AC-31`~`33`) | 2번의 메커니즘을 공유하고 진입점만 분리 |
| 4 | **US2** 회원가입 + 중복확인 | |
| 5 | **US3** 권한 접근제어 | US1·US2가 있어야 검증된다 |
| 6 | 남용 방어 (`AC-29`·`AC-30`) | 로그인·중복확인이 있어야 얹을 수 있다 |
| 7 | 프론트 화면 + E2E | |

**1번을 먼저 하는 이유를 강조한다.** `AC-27`(모든 오류에 추적 식별자)과 `AC-28`(내부 정보 비노출)은
**특정 기능의 AC가 아니라 모든 응답에 걸리는 AC**다. 기능을 다 만든 뒤에 넣으면 이미 만든
모든 오류 경로를 다시 손대야 한다.

---

## 위험과 대응

| 위험 | 왜 위험한가 | 대응 |
|---|---|---|
| **실패 응답 3경로가 갈라진다** | `AC-2`·`AC-3`·`AC-32` 중 하나만 달라도 계정 열거가 뚫린다. 예외 종류가 달라서 핸들러가 다르게 잡기 쉽다 | **세 응답을 서로 비교하는 테스트를 따로 둔다.** 각각 통과해도 서로 다를 수 있다 |
| **함수 유니크 인덱스를 `validate`가 검증하지 않는다** | 엔티티에 `unique=true`만 걸고 실제 인덱스와 어긋나면 대소문자 다른 중복 가입이 통과 | **동시 가입 경합 테스트에 `Kim` vs `kim` 포함**(`ADR-003` 리스크 1) |
| **지연이 스레드를 고갈시킨다** | 방어 장치가 서비스 거부 수단이 된다 | **가상 스레드 활성화.** Java 25를 고른 것이 여기서 값을 한다 |
| 관리자 초기 비밀번호 유출 | 저장소가 공개다. 한 번 커밋되면 히스토리에 영구히 남는다 | 환경변수만. `.env`는 `.gitignore`. **CI의 gitleaks가 이미 방어선** |
| E2E 상태 파일이 커밋된다 | 자격증명이다 | **`auth/*.json`을 `.gitignore`에 추가**(`test-strategy.md` 4.1) |
| JPA를 "SQL 안 써도 되는 도구"로 오해 | N+1 등 사고가 난다 | `show-sql` 상시 + **통합 테스트가 실제 SQL을 돌린다**(`ADR-004` 완화) |

---

## Complexity Tracking

> Constitution Check 위반 0건이므로 비어 있다.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
