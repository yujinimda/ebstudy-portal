# 코드 읽는 순서 — 001 인증

> **목적**: 자바·백엔드를 처음 보는 상태에서 이 저장소를 어떤 순서로 열어야 막히지 않는가.
> **원칙**: 층별로 훑지 않고 **회원가입 한 줄기를 끝까지 완주**한 뒤 옆으로 넓힌다.
> 층으로 훑으면(엔티티 전부 → 서비스 전부) 마지막까지 "돌아가는 것"이 안 보인다.
>
> 각 단계 끝의 **확인 질문**에 답할 수 있으면 다음으로 간다.
> 답이 안 나오면 그 지점이 곧 `process.md` 4.2 learning 노트 트리거다.

**작성**: 2026-07-31 · 001 백엔드 + 프론트 구현 완료 시점

---

## 시작하기 전에 — 5분

```bash
docker compose up --build
```

→ http://localhost:3000 을 열고 **회원가입 → 로그인 → 내 정보 → 로그아웃**을 손으로 해본다.

**코드를 읽기 전에 동작을 먼저 본다.** 무엇을 하는 코드인지 모르는 상태로 읽으면
문법에만 시선이 가고 의도가 안 남는다.

브라우저 개발자도구 → Network 를 켜고 같은 일을 한 번 더 한다. `/api/auth/signup`,
`/api/auth/login`, `/api/me` 가 오가는 것과 **응답 본문에 토큰이 없는 것**을 눈으로 본다.

---

## 1단계 — 뼈대 (10분)

| 순서 | 파일 | 무엇을 본다 |
|---|---|---|
| 1 | `backend/src/main/java/com/ebstudy/portal/PortalApplication.java` | 20줄. 앱이 시작되는 유일한 지점 |
| 2 | `backend/src/main/resources/db/migration/V1__create_users.sql` | **코드보다 DB가 먼저다.** 진짜 출발점 |
| 3 | `backend/src/main/java/com/ebstudy/portal/user/Role.java` | 7줄. `enum` 이 뭔지 |

**확인 질문**
- `users` 테이블의 컬럼 6개를 말할 수 있나?
- `CHECK (role IN ('USER','ADMIN'))` 은 무엇을 막는가?

---

## 2단계 — 데이터 (30분)

| 순서 | 파일 | 무엇을 본다 |
|---|---|---|
| 1 | `user/User.java` | 엔티티. **V1 SQL 을 옆에 띄우고 한 줄씩 대조** |
| 2 | `user/UserRepository.java` | 리포지토리. 메서드 이름이 곧 쿼리 |
| 3 | `user/ReservedUsername.java` + `ReservedUsernameRepository.java` | 같은 구조 한 번 더 → 패턴이 굳는다 |

**대조하며 볼 때 짝이 없는 3곳이 볼거리다.**

| 한쪽에만 있는 것 | 어디에 | 왜 |
|---|---|---|
| `CHECK (role IN ...)` | SQL 에만 | Java 는 `Role` enum 이, DB 는 CHECK 가 각자 막는다 |
| `UNIQUE INDEX ... LOWER(username)` | SQL 에만 | 대소문자 무시 유일성은 JPA 로 표현할 수 없다 |
| `updatable = false` | 엔티티에만 | DB 제약이 아니라 **JPA 가 UPDATE 문에서 뺀다**는 지시 |

**확인 질문**
- `User.java` 와 `V1.sql` 은 무엇으로 연결되나? (코드 참조가 아니다)
- 그 일치를 누가 언제 검사하나?
- `existsByRole` 이라고만 적었는데 SQL 이 어떻게 생기나?

---

## 3단계 — 회원가입 완주 ★ 여기가 핵심 (1시간)

| 순서 | 파일 | 무엇을 본다 |
|---|---|---|
| 1 | `user/SignupPolicy.java` | 22줄. `application.yml` 값이 Java 로 오는 방법 |
| 2 | **`user/SignupService.java`** | **가장 중요한 파일.** 검증·트랜잭션·예외가 다 있다 |
| 3 | `auth/AuthController.java` — `signup` · `check-id` 만 | 나머지 메서드는 건너뛴다 |

**`SignupService` 에서 반드시 볼 것**

- `@Transactional` 이 붙은 위치 — 왜 클래스가 아니라 메서드인가
- `validatePassword` 의 **72바이트 검사** — 조용히 자르면 왜 `AC-2` 가 거짓이 되나
- `catch (DataIntegrityViolationException)` — DB 예외를 왜 갈아서 던지나 (`AC-28`)
- 리포지토리 2개 + 인코더를 **순서대로 부르는 것**이 서비스의 일이다

**여기까지가 `Controller → Service → Repository → DB` 한 바퀴다. 4단계 전에 한 번 멈춘다.**

**확인 질문**
- 컨트롤러는 3줄인데 서비스는 130줄이다. 무엇이 어디 있어서 그런가?
- `throw new ApiException(...)` 한 것이 어떻게 400 JSON 이 되나? (아직 모르면 4단계로)

---

## 4단계 — 에러 처리 (30분)

3단계에서 생긴 "이 `throw` 가 어디로 가지?" 가 여기서 풀린다.

| 순서 | 파일 | 무엇을 본다 |
|---|---|---|
| 1 | `common/ErrorCode.java` | 에러 목록. `enum` 의 진짜 활용 예 |
| 2 | `common/ApiException.java` | 예외 만들기 |
| 3 | `common/GlobalExceptionHandler.java` | 컨트롤러 이후의 예외를 잡는 자리 |
| 4 | `common/ProblemDetailFactory.java` | **응답을 만드는 유일한 자리** |
| 5 | `common/ProblemAuthEntryPoints.java` | Security 필터가 만드는 401·403 |

**★ 왜 어댑터가 3개인가**가 이 단계의 핵심이다. `@RestControllerAdvice` 하나로는
Security 필터 체인의 401/403 을 못 잡는다 — 컨트롤러에 닿기 전에 발생하기 때문이다.
셋이 같은 생성기를 공유하지 않으면 `AC-27`(모든 오류에 traceId)이 거기서 갈라진다.

**확인 질문**
- 401 과 403 은 왜 `GlobalExceptionHandler` 가 못 잡나?
- `AC-28` 이 막는 실제 사고는 무엇인가?

---

## 5단계 — 프론트 (1시간)

백엔드 한 바퀴를 돈 상태라 계약이 읽힌다. **프론트가 본업이므로 여기는 빠르게 지나간다.**
대신 **"프론트가 책임지는 것 4개"** 만 정확히 본다(`contracts/auth-api.md` 마지막 절).

| 순서 | 파일 | 무엇을 본다 | AC |
|---|---|---|---|
| 1 | `frontend/next.config.ts` | `/api/*` rewrite. **CORS 가 없는 이유** | — |
| 2 | **`frontend/lib/safe-redirect.ts`** | 오픈 리다이렉트 방어. **`//evil.example` 이 왜 위험한가** | `AC-34` |
| 3 | `frontend/lib/safe-redirect.test.ts` | 변형 26개. 계약이 지목한 목록 그대로다 | |
| 4 | **`frontend/lib/api.ts`** | **자동 재발급.** 규칙 3개가 코드 어디에 있는지 짚는다 | `AC-4` |
| 5 | `frontend/lib/api.test.ts` | 규칙 3개를 호출 횟수로 검증 | |
| 6 | `frontend/lib/useSession.ts` | **왜 서버에 물어봐야만 로그인 상태를 아는가** | `AC-7` |
| 7 | `frontend/components/RequireAuth.tsx` | `?next=` 를 만드는 자리 | `AC-24` |
| 8 | `frontend/app/login/LoginForm.tsx` | 그 값을 **받는 쪽에서 검증**한다 | `AC-34` |
| 9 | `frontend/app/admin/page.tsx` | **화면에서 감춰도 서버가 막는다** | `AC-26` |

**★ 이 단계에서 제일 중요한 한 가지**

`lib/api.ts` 의 자동 재발급 규칙 3개다.

```
1. 재시도는 1회만          → 없으면 무한 루프
2. 동시 재발급을 하나로     → 없으면 로그인 1회에 티켓이 여러 개 생긴다
3. refresh 자신은 재시도 안 함 → 1번과 같은 루프
```

코드에서 각각이 **어느 줄**인지 짚을 수 있어야 한다.

**확인 질문**
- 왜 토큰을 `localStorage` 에 두지 않나?
- `//evil.example` 은 왜 `startsWith("http")` 검사를 통과하나?
- USER 계정으로 `/admin` 을 주소창에 직접 치면 무슨 일이 일어나나?

---

## 6단계 — 로그인 (제일 어렵다, 2시간)

**순서를 지킨다. 뒤엣것이 앞엣것을 쓴다.**

| 순서 | 파일 | 무엇을 본다 |
|---|---|---|
| 1 | `auth/AuthProperties.java` | 설정값 묶음 |
| 2 | `auth/JwtIssuer.java` | Access 토큰 만들기·검증 |
| 3 | `auth/AuthCookies.java` | 쿠키 속성. **만들기와 지우기가 같은 메서드를 통과하는 이유** |
| 4 | `auth/RefreshTicket.java` | 엔티티 하나 더. **`@ManyToOne` 관계 매핑 등장** |
| 5 | `auth/RefreshTokenService.java` | Refresh 는 JWT 가 아니다 |
| 6 | **`auth/AuthService.java`** | **로그인 본체** |

**★ `AuthService` 는 "실패가 전부 같은 얼굴이 되게 하는 것"이 존재 이유다.**

- 없는 아이디에도 **더미 해시로 검증을 수행**한다 → 건너뛰면 시간차로 존재 여부가 샌다
- 관리자 진입점도 **비밀번호 검증을 끝낸 뒤에** 권한을 본다 → 먼저 보면 `AC-32` 가 빨라진다
- `@Transactional` 이 **없는 것이 의도**다 → 지연이 트랜잭션 안에 있으면 커넥션을 붙잡는다

**확인 질문**
- `AC-2`·`AC-3`·`AC-32` 세 경로가 코드에서 **어느 한 줄**로 합쳐지나?
- Refresh 는 왜 JWT 가 아닌가?
- 왜 Refresh 해시에 bcrypt 를 쓸 수 없나?

---

## 7단계 — 보안·인프라 (1시간)

| 파일 | 무엇을 본다 |
|---|---|
| `auth/SecurityConfig.java` | URL 별 권한. 프론트 미들웨어와 비슷하다 |
| `auth/JwtCookieAuthenticationFilter.java` | 쿠키 → 인증 상태. **몰래 재발급하지 않는 이유** |
| `auth/ratelimit/` 5개 | 로그인 제한. `LoginDelayPolicy` 부터 |
| `admin/` 3개 | 관리자. 앞 내용의 반복이라 쉽다 |
| `common/TraceIdFilter.java` | 요청마다 추적 번호 |
| `auth/ExpiredTicketCleanupJob.java` | **테이블을 만들면 청소 책임이 따라온다** |

---

## 8단계 — 테스트를 정답지로 (30분)

**코드보다 의도가 잘 보인다.** 각 파일이 "무엇을 보장하려는 것인지"를 문장으로 적어놨다.

| 파일 | 무엇을 지키는가 |
|---|---|
| `backend/src/test/.../user/ConcurrentSignupIT.java` | `Kim01` 과 `kim01` 이 동시에 가입하면 하나만 성공 |
| `backend/src/test/.../auth/AuthFailureUniformityIT.java` | 세 실패 응답이 서로 **완전히 같다** |
| `backend/src/test/.../auth/LoginLifecycleIT.java` | 로그인 → 로그아웃 → **다른 기기는 살아 있다** |
| `backend/src/test/.../auth/AuthorizationIT.java` | 401 · 403 · 200 |
| `frontend/lib/*.test.ts` | 프론트 책임 2개 |

```bash
cd backend  && ./gradlew test    # 통합 10개 (실제 Postgres)
cd frontend && npm test          # 단위 34개
```

---

## 곁에 두고 볼 문서

| 문서 | 언제 |
|---|---|
| `specs/001-user-auth/contracts/auth-api.md` | **3~6단계 내내.** 코드의 근거가 전부 여기 있다 |
| `specs/001-user-auth/spec.md` | AC 번호를 만났을 때 |
| `specs/data-model.md` | 2단계에서 인덱스·제약이 궁금할 때 |
| `specs/learning/` | 이미 정리된 노트 7개 (DB 주제) |
| `README.md` 6절 | **스펙과 어긋나는 지점** — 알고 남긴 것들 |

---

## 아직 노트가 없는 주제 (2026-07-31 기준)

`process.md` 4.2 규칙상 "이게 뭐야"를 물은 지점마다 노트가 남아야 하는데,
**2026-07-30 대화에서 나온 8개가 비어 있다.**

| 주제 | 우선순위 | 왜 |
|---|---|---|
| 계층 구조 (Controller/Service/Repository) | **높음** | 002~006 에서 계속 쓴다 |
| 의존성 주입 · 생성자 주입 · `final` | **높음** | 모든 클래스에 나온다 |
| 엔티티 vs DTO | 중 | 002 에서 다시 만난다 |
| Mapper · ORM · JPA · Hibernate 관계 | 중 | `ADR-004` 를 읽으려면 필요 |
| 클래스 · 인스턴스 · 메서드 · 필드 | 중 | 용어가 통해야 나머지가 담긴다 |
| Java 패키지 (`com.ebstudy.portal`) | 낮 | 한 번 알면 끝 |
| Gradle Wrapper | 낮 | 한 번 알면 끝 |
| Lombok | 낮 | 이 프로젝트에서 실제로 쓴다 |

노트 형식은 `process.md` 4.2 — `물은 맥락` / `쉬운 말 요약` / `설명 + 예제` /
**`★ 내 말로 다시`**(사람) / `아직 모르는 것`(사람).

> `내 말로 다시` 칸을 못 쓰면 아직 이해하지 못한 것이다.
> 설명만 저장하면 학습이 아니라 스크랩이다.
