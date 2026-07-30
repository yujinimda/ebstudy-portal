# ebstudy-portal

게시판 포털. 현재 구현된 것은 **001 인증**(회원가입 · 로그인 · 로그아웃 · 권한 기반 접근제어)의
**백엔드**다. 프론트(Next.js)는 아직 없다.

- 무엇을 왜 그렇게 만들었는지: [`specs/001-user-auth/spec.md`](specs/001-user-auth/spec.md) ·
  [`contracts/auth-api.md`](specs/001-user-auth/contracts/auth-api.md) ·
  [`research.md`](specs/001-user-auth/research.md) · [`specs/data-model.md`](specs/data-model.md)
- 진행 상황: [`specs/STATUS.md`](specs/STATUS.md)

**스택**: Java 25 LTS · Spring Boot 4.1 · Gradle(Kotlin DSL) · PostgreSQL 17 ·
Spring Data JPA · Flyway · Spring Security(JWT 내장) · bcrypt

---

## 1. 띄우기

### 사전 조건

| | 무엇 | 왜 |
|---|---|---|
| Docker | Docker Desktop 등이 **돌고 있어야 한다** | 로컬 기동과 통합 테스트가 둘 다 Docker 를 쓴다 |
| JDK 25 | 테스트·빌드를 호스트에서 돌릴 때만 필요 | `docker compose` 만 쓸 거면 없어도 된다 |

### 그냥 띄우기 (`.env` 없이도 뜬다)

```bash
docker compose up --build
```

- `postgres` 가 healthy 가 된 뒤 `backend` 가 뜬다
- **Flyway 가 `V1`~`V4` 를 자동으로 적용**한다 (`Successfully applied 4 migrations` 로그)
- `ddl-auto=validate` 가 엔티티와 스키마 일치를 확인한다 — **어긋나면 부팅이 실패한다**
- 관리자 시딩은 `.env` 가 없으면 **건너뛴다**(경고 로그 1줄). 회원가입·로그인은 그대로 된다
- 로그는 stdout **JSON 1줄**(ECS)로 나온다. `traceId` 가 오류 응답과 같은 값이다

정지·초기화:

```bash
docker compose down       # 정지 (데이터 유지)
docker compose down -v    # 볼륨까지 삭제 → 다음 기동에서 마이그레이션이 처음부터 다시 돈다
```

### 관리자 로그인까지 하려면 `.env` 를 만든다

```bash
cp .env.example .env
```

`.env` 에서 최소한 이 4개를 채운다(나머지는 비워두면 기본값을 쓴다):

```
JWT_SECRET=충분히-길고-무작위인-값
ADMIN_INITIAL_ID=bossadmin
ADMIN_INITIAL_PASSWORD=사람이-지어내지-않은-값
BACKEND_PORT=8080          # 호스트 8080 이 이미 쓰이고 있으면 18080 등으로 바꾼다
```

> `.env` 는 커밋되지 않는다(`.gitignore`). **관리자 초기 비밀번호는 저장소에 어떤 형태로도
> 남기지 않는다**(`FR-023` · `SC-008`). `JWT_SECRET` 을 비우면 앱이 임시 키를 만들고 경고하는데,
> 재시작마다 발급된 Access 토큰이 전부 무효가 된다.

기동 후 관리자 시딩은 **없을 때만** 일어난다. 여러 번 재시작해도 계정은 하나이고
비밀번호는 초기화되지 않는다(`AC-37`).

---

## 2. 되는지 확인하기 — `curl`

`BACKEND_PORT` 를 바꿨으면 아래 `8080` 을 그 값으로 바꾼다.

```bash
B=http://localhost:8080

# ── 회원가입 (201)
curl -i -X POST $B/api/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"username":"hong01","password":"Study1234abcd","name":"홍길동"}'

# ── 아이디 중복확인 (분당 10회 제한)
curl "$B/api/auth/check-id?username=hong01"     # {"available":false}
curl "$B/api/auth/check-id?username=Admin"      # {"available":false}  ← 대소문자 무시 금지어
curl "$B/api/auth/check-id?username=freeid01"   # {"available":true}

# ── 로그인 (200) — 자격증명은 httpOnly 쿠키로만 온다. 본문에 토큰이 없다
curl -i -c cookies.txt -X POST $B/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"hong01","password":"Study1234abcd"}'
#   Set-Cookie: ACCESS_TOKEN=...; Path=/;         Max-Age=1800;    HttpOnly; SameSite=Lax
#   Set-Cookie: REFRESH_TOKEN=...; Path=/api/auth; Max-Age=1209600; HttpOnly; SameSite=Lax

# ── 로그인한 사용자 정보
curl -b cookies.txt $B/api/me
#   {"username":"hong01","name":"홍길동","role":"USER"}

# ── 관리자 전용 기능에 USER 로 접근 → 403
curl -b cookies.txt $B/api/admin/me

# ── Access 재발급 (프론트가 401 을 받고 부르는 자리)
curl -i -b cookies.txt -c cookies.txt -X POST $B/api/auth/refresh

# ── 로그아웃 (204) — 이 기기의 티켓만 무효화하고 쿠키를 지운다
curl -i -b cookies.txt -c cookies.txt -X POST $B/api/auth/logout

# ── 로그아웃 뒤 재발급 → 401 AUTH_REFRESH_INVALID
curl -i -b cookies.txt -X POST $B/api/auth/refresh
```

**실패 응답은 전부 같은 얼굴이다** — 아래 셋의 상태코드·`code`·`detail` 이 모두 같다.
다르면 아이디 존재 여부를 알아내 가입자 명단을 뽑을 수 있다(계정 열거).

```bash
# 틀린 비밀번호 (AC-2)
curl -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"hong01","password":"WrongPass9999"}'
# 없는 아이디 (AC-3)
curl -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"nosuchuser","password":"WrongPass9999"}'
# 관리자 진입점에 USER 계정 + 정확한 비밀번호 (AC-32)
curl -X POST $B/api/admin/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"hong01","password":"Study1234abcd"}'
#
# 셋 다: 401 / {"code":"AUTH_INVALID_CREDENTIALS","detail":"아이디 또는 비밀번호를 확인해 주세요", ...}
#        errors 필드 없음 · Set-Cookie 없음
```

관리자(`.env` 에 시딩 값을 넣고 기동했을 때):

```bash
curl -i -c admin.txt -X POST $B/api/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"'"$ADMIN_INITIAL_ID"'","password":"'"$ADMIN_INITIAL_PASSWORD"'"}'
curl -b admin.txt $B/api/admin/me    # 200
```

---

## 3. 테스트

```bash
cd backend
./gradlew test        # 단위 + 통합. Testcontainers 가 실제 PostgreSQL 컨테이너를 띄운다
./gradlew build       # 컴파일 + 테스트 + bootJar
```

- **Docker 가 꺼져 있으면 통합 테스트가 실패한다.** 도구 설정 문제이고 코드 결함이 아니다
- 통합 테스트는 `.env` 의 `DB_URL` 을 쓰지 않는다 — Testcontainers 가 접속 정보를 런타임에 주입한다
- 이미지 태그는 `docker-compose.yml` 과 같은 값으로 고정돼 있다(`postgres:17.10`)

---

## 4. 구현 범위

### 엔드포인트 8개 — 전부 구현

| | 엔드포인트 | 비고 |
|---|---|---|
| 1 | `POST /api/auth/signup` | 검증 8종 · 동시 가입 경합은 DB 유니크 인덱스가 판정 |
| 2 | `GET /api/auth/check-id` | 클라이언트 단위 분당 10회 |
| 3 | `POST /api/auth/login` | `ADMIN` 계정도 통과한다(수명은 역할 기준) |
| 4 | `POST /api/admin/auth/login` | 비밀번호 검증을 끝낸 뒤 권한을 본다 |
| 5 | `POST /api/auth/refresh` | Refresh 를 회전시키지 않고 Access 만 재발급 |
| 6 | `POST /api/auth/logout` | 제시된 해시로 찾은 **한 행만** 무효화 |
| 7 | `GET /api/me` | |
| 8 | `GET /api/admin/me` | `ADMIN` 만 |

### 001 범위에서 **하지 않은 것**

| | 무엇 | 어디 |
|---|---|---|
| 프론트엔드 | 화면 6개 · Playwright E2E · `/api/*` rewrite · 목적지 복귀(`AC-24`)와 오픈 리다이렉트 차단(`AC-34`) | 별도 작업 |
| `AC-25` 문의게시판 목록 | 001 에 리소스가 없다 | **006 으로 이관**(스펙 명시) |
| CI 잡(`build`/`test`) | `.github/workflows/ci.yml` 은 아직 `secret-scan` 만 있다 | 다음 작업 |
| 백엔드 포트 비공개 | `research.md` 5 는 "백엔드 포트를 호스트로 열지 않는다"고 정했다. 프론트가 없어 지금은 유일한 진입점이라 열어 뒀다 | `docker-compose.yml` 주석에 방아쇠 표기 |

---

## 5. 자동 테스트가 있는 것 / 없는 것

**있는 것 (10개 테스트)**

| 테스트 | 검증 AC |
|---|---|
| `AuthFailureUniformityIT` | **`AC-2`·`AC-3`·`AC-32` 3자 비교** · `AC-27`(응답의 traceId) |
| `LoginLifecycleIT` | `AC-1` · `AC-9` · `AC-5` · `AC-35` · `AC-4`(백엔드 몫) |
| `AuthorizationIT` | `AC-21` · `AC-22` · `AC-23` · `AC-31` · `AC-33` |
| `ConcurrentSignupIT` | `AC-10` + 동시 가입 경합(`Kim01` vs `kim01` — `LOWER()` 유니크 인덱스 확인) |
| `LoginDelayPolicyTest` | `AC-29` 의 **지연 계산** 부분 |

**테스트가 없는 AC** — 001 분모 36개 중 아래 20개다(`AC-25` 는 006 이관이라 분모 밖).

| AC | 무엇 | 메모 |
|---|---|---|
| `AC-6` | Refresh 만료 401 `AUTH_REFRESH_EXPIRED` | 구현은 있다. 수명을 짧게 오버라이드하는 테스트가 없다 |
| `AC-7` | 스크립트로 쿠키 못 읽음 | **E2E 몫**(브라우저 필요) |
| `AC-8` | 가입 성공 201 | 전용 테스트는 없다. 다른 테스트의 전제로 201 을 확인한다 |
| `AC-11`~`AC-18` | 입력 검증 8종 경계값 | 구현·수동 확인 완료. 경계값 테스트가 없다 |
| `AC-19` | 직접 호출 우회 차단 | `AC-11`~`18` 각각에 따로 필요하다 |
| `AC-20` | 중복확인 ↔ 가입 결과 일치 | |
| `AC-24` | 로그인 후 원래 목적지 복귀 | **프론트·E2E 몫** |
| `AC-26` | 화면에서 감춘 기능도 서버가 막음 | `AC-22` 와 같은 요청, 다른 관점이라 따로 필요하다 |
| `AC-28` | 5xx 내부 정보 비노출(화이트리스트) | 5xx 를 일으키는 테스트가 없다. 409 경로에서 제약조건명 비노출만 확인했다 |
| `AC-29` | 429 · 차단 해제 후 정상 로그인(통합) | 계산 부분만 단위 테스트가 있다 |
| `AC-30` | 중복확인 빈도 제한 | `curl` 로 수동 확인(10회 후 429). 자동 테스트 없음 |
| `AC-34` | 오픈 리다이렉트 차단 | **프론트 단위 + E2E 몫** |
| `AC-36` | 로그에 비밀번호·자격증명 없음 | 로그 캡처 테스트가 없다 |
| `AC-37` | 관리자 시딩 멱등성 | `docker compose restart` 로 수동 확인(계정 1개 · 비밀번호 유지 · 부팅 성공). 자동 테스트 없음 |

---

## 6. 스펙과 어긋나 보이는 지점 — **고치지 않고 보고한다**

> `specs/` 의 문서는 한 줄도 수정하지 않았다. 아래는 구현 지시와 스펙 문서가 부딪힌 곳이다.
> 판단이 필요한 것은 **1·2번**이다.

### 1. 비밀번호 해싱 — 구현은 **bcrypt**, `ADR-005`·`research.md` 13·`process.md` 6.1 은 **Argon2id**

- 5번 게이트가 bcrypt 를 **기각**한 근거: bcrypt 는 입력을 **72바이트에서 자르는데** 스펙은
  비밀번호 **64자 + 한글·이모지**를 허용한다(한글 64자 = 192바이트). 앞 72바이트가 같은 두
  비밀번호가 같은 입력이 되어 *"틀린 비밀번호로 로그인이 성공"* 하고 **`AC-2` 가 거짓이 된다**
- 이 구현은 사용자 지시대로 bcrypt 를 쓰고, **72바이트를 넘는 비밀번호를 거부**해서 절단을 막았다
  (`SIGNUP_PASSWORD_MAX_BYTES=72`)
- **남는 어긋남**: 스펙이 허용하는 값 중 **거부되는 것이 생겼다** — 한글 25자 이상(75바이트) 비밀번호는
  `USER_PASSWORD_LENGTH_INVALID` 로 400 이다. `plan.md`의 AC-14·15 배정
  (*"한글 64자(192바이트)가 전부 가입되고 그 비밀번호로만 로그인된다"*)과 **정면으로 부딪힌다**
- → **Argon2id 로 돌릴지, 스펙의 비밀번호 문자 집합/길이를 조정할지 사람이 결정해야 한다**

### 2. 비밀번호·이름 길이 — 구현 기본값은 **스펙 값**, 지시는 **원 기획서 값**

| | 원 기획서(지시) | `spec.md`(구현 기본값) |
|---|---|---|
| 비밀번호 | 4자 이상 **12자 미만** | **8자 이상 64자 이하** (`FR-006`·`AC-14`·`AC-15`) |
| 이름 | 2자 이상 **5자 미만** | **2자 이상 50자 이하** (`FR-029`·`AC-18`) |

**스펙 값을 기본으로 골랐다.** 근거는 기획서 값을 넣으면 **스펙의 AC 가 즉시 깨지기 때문**이다:

- `AC-8`(가입 성공)의 예시 비밀번호가 **`Study1234abcd` 13자**다 — 12자 미만이면 **핵심 정상 경로가 400** 이 된다.
  같은 값이 `contracts/auth-api.md` 1번의 요청 예시이기도 하다
- `AC-18` 은 **`Maria Consuelo Rodriguez`(24자)가 정상 가입되어야 한다**고 못박았다 — 5자 미만이면 거부된다
- `process.md` 8.2 가 *"비밀번호 4자리 이상 12자리 미만 → 실무 최소 8자, 스펙 단계에서 상향"* 으로
  이미 **의도적으로 올린 값**이라고 기록하고 있다

**되돌리는 방법은 한 줄이다** — `.env` 에 `SIGNUP_PASSWORD_MIN_LENGTH=4` ·
`SIGNUP_PASSWORD_MAX_LENGTH=11` · `SIGNUP_NAME_MAX_LENGTH=4` 를 넣으면 코드 수정 없이 바뀐다.
다만 위 AC 들이 그때 깨진다.

### 3. `AC-20` 과 사용불가 아이디의 관계 — 계약 문구가 성립하지 않는 조합이 있다

- 계약 2번은 *"여기서 `available: false` 인 아이디는 **가입에서도 반드시 409**"* 라고 적었다
- 그런데 **사용불가 아이디**(`admin`)의 가입 실패는 **400 `USER_ID_NOT_ALLOWED`** 다(`AC-13`)
- 구현은 **금지어에도 `available: false`** 를 준다(사용자가 쓸 수 없는 아이디이므로).
  즉 *"false 인데 409 가 아닌"* 조합이 존재한다
- 반대로 금지어에 `available: true` 를 주면 중복확인을 통과한 아이디가 가입에서 400 이 되어
  `AC-20` 의 취지(*"통과했는데 실패하면 사용자가 원인을 알 수 없다"*)를 더 크게 깬다
- → **계약의 "반드시 409" 를 "가입이 거부된다"로 완화**하는 것이 맞아 보인다. 문서를 고치지 않았다

### 4. 마이그레이션 SQL 작성 주체

`process.md` 6.7 과 constitution 원칙 III 는 **"마이그레이션 SQL 은 사람 단독"** 으로 정했다.
이 숙제의 전제가 바이브 코딩이라 **사용자 지시로 AI 가 작성**했다. 각 `V*.sql` 헤더와
커밋 메시지에 그 사실을 적었다.

### 5. Refresh 쿠키 `Path` 의 정확도

계약은 *"Refresh 는 **재발급·로그아웃 경로에만**"* 이라고 적었지만, 쿠키의 `Path` 는 접두사
하나만 지정할 수 있어 **`/api/auth`** 로 두었다. 결과적으로 `/api/auth/login`·`/signup`·
`/check-id` 에도 함께 실린다. 정확히 두 경로로 좁히려면 엔드포인트 경로 구조를 바꿔야 한다
(예: `/api/auth/session/*`). 지금은 표면이 `/api/auth` 하위로 제한된 상태다.

### 6. 그 밖의 구현 메모

- **CSRF 토큰은 도입하지 않았다** — 계약대로 `SameSite=Lax` + 상태 변경이 전부 `POST` 로 덮는다.
  방아쇠: `GET` 으로 상태를 바꾸는 엔드포인트가 생기거나 크로스 오리진이 필요해지는 시점
- **`TRUSTED_PROXY_CIDRS` 를 비워두면 `X-Forwarded-For` 를 신뢰하지 않는다**(위조 방지).
  프론트 rewrite 를 붙이는 시점에 프록시 대역을 채워야 `AC-30` 이 "한 사람이 전체를 막는" 장애가 되지 않는다
- **Spring Boot 4 는 자동설정이 모듈로 쪼개졌다** — `flyway-core` 만 넣으면 마이그레이션이
  **조용히 실행되지 않는다**. `org.springframework.boot:spring-boot-flyway` 가 필요하다(실제로 겪었다)
