# 디카 작례 (dica-jakrye)

**디카 사기 전에, 그 기종으로 진짜 찍은 작례를 한국어로 모아 보는 사이트.**
2000년대~2010년대 초 똑딱이 디카를 기종별로 정리하고, 그 카메라로 찍은 사진을 모아 보여준다.

## 왜 만들었나

- 빈티지 디카 작례는 인스타·블로그에 흩어져 있고, EXIF가 지워져 진짜 그 기종으로 찍었는지 알 수 없다.
- 같은 카메라도 나라마다 이름이 달라(IXUS / IXY / PowerShot SD) 검색이 잘 안 된다.

## 주요 기능

- 기종 검색: 별칭과 나라별 이름으로도 찾는다. 캐논은 내부 모델 번호로 같은 기종인지 확인한다.
- 기종 페이지와 작례 상세: 촬영 정보, 올린 사람 또는 출처·라이선스를 함께 보여준다.
- 작례 올리기: 구글·카카오 로그인 후 업로드하면 EXIF로 기종을 자동 인식하고, 위치 정보(GPS)는 지운 뒤 저장한다.
- 초기 작례 수집: Wikimedia Commons에서 기종별 작례를 이용 규칙(요청 간격, User-Agent)을 지키며 모은다.
- 신고와 관리자 처리: 부적절한 작례 신고, 관리자 삭제, 미분류 작례 연결.

## 만든 방식

코드보다 문서를 먼저 썼다. 기능 범위, 구조, DB, API, 보안 체크리스트를 `docs/`에 확정하고,
외부 데이터(Commons, 네이버 데이터랩)는 `poc/`의 스크립트로 먼저 검증한 뒤 구현했다.
수요가 확인되지 않은 기능(인기 기종 순위)은 조사 결과와 함께 [LATER.md](docs/LATER.md)로 미뤘다.

| 문서 | 내용 |
|---|---|
| [PRD](docs/PRD.md) | 문제 정의, 기능 범위, MVP 포함·제외 |
| [ARCHITECTURE](docs/ARCHITECTURE.md) | 구성, 배치 설계, 확장 체크리스트 |
| [ERD](docs/ERD.md) · [API](docs/API.md) | 테이블과 API 명세 |
| [SECURITY](docs/SECURITY.md) | 기능별 보안 체크리스트 |

**기술**: Next.js 16 · TypeScript · Tailwind CSS 4 / Spring Boot 4.1 · Java 21 · PostgreSQL 17 · Flyway · Spring Security(OAuth2) / Cloudflare R2 / GitHub Actions CI · Testcontainers

## 폴더

| 폴더 | 내용 |
|---|---|
| `docs/` | 기획·설계 문서 (PRD, ARCHITECTURE, ERD, API, SECURITY, CAMERAS, LATER), 와이어프레임, PDF |
| `backend/` | Spring Boot 4.1 · Java 21 · PostgreSQL 17 · Flyway |
| `frontend/` | Next.js 16 (App Router, Cache Components) · TypeScript · Tailwind CSS 4 |
| `poc/` | 외부 데이터 검증 스크립트 (Wikimedia Commons, 네이버 데이터랩) |

## 로컬 실행 (백엔드)

필요한 것: Docker, JDK 21

```bash
# 1. 로컬 DB (PostgreSQL 17, 포트 5433)
docker compose up -d

# 2. 백엔드 실행 (local 프로필, http://localhost:8080)
cd backend
./gradlew bootRun

# 상태 확인
curl localhost:8080/actuator/health
```

## 로컬 실행 (프론트)

필요한 것: Node.js 24, 백엔드 실행 중

```bash
cd frontend
npm install
npm run dev   # http://localhost:3000
```

- 서버 컴포넌트는 `BACKEND_URL`(기본 `http://localhost:8080`)로 백엔드를 직접 부르고, 브라우저는 같은 출처의 `/api/*`를 부르면 Next.js가 백엔드로 넘겨 준다. (`next.config.ts` rewrites)
- 검사: `npx tsc --noEmit`, `npm run lint`

## 참고

- 테이블은 앱이 시작할 때 Flyway가 `backend/src/main/resources/db/migration`의 SQL로 만든다. JPA가 테이블을 바꾸지 않는다. (`ddl-auto: validate`)
- 테스트는 Testcontainers로 빈 PostgreSQL을 따로 띄워서 돌린다: `./gradlew test` (Docker 필요)
- DB를 처음부터 다시 만들려면: `docker compose down -v` 후 다시 `up`

## CI (자동 검사)

`main`에 push하거나 PR을 열면 GitHub Actions가 자동으로 돌린다. (`.github/workflows/ci.yml`)

- 백엔드: `./gradlew test` (Testcontainers로 PostgreSQL을 띄워서)
- 프론트: `npx next typegen`(라우트 타입 생성) → `npx tsc --noEmit`, `npm run lint`, `npm run build`

결과는 GitHub 저장소의 Actions 탭과 커밋 옆 체크 표시로 본다. 비밀 값은 쓰지 않는다.

## 운영 메모

- **관리자 지정**: 가입한 뒤 DB에서 권한을 바꾼다. 권한은 요청마다 DB에서 확인하므로 바로 반영된다.
  ```sql
  UPDATE users SET role = 'ADMIN' WHERE nickname = '닉네임';
  ```
  관리자는 헤더의 **관리** 링크(`/admin/reports`)에서 신고를 처리한다.
- **신고 알림 메일**: `MAIL_USERNAME`(Gmail 주소), `MAIL_PASSWORD`(Gmail 앱 비밀번호)를 넣으면 신고가 들어올 때 `ADMIN_NOTIFY_EMAIL`(기본: 운영 메일)로 알림을 보낸다. 비우면 로그만 남긴다.

## 비밀 정보

- `.env`, `application-local.yml`은 Git에 올리지 않는다. (`.gitignore`)
- 로컬 로그인 키: `backend/.env.example`을 `backend/.env`로 복사해 구글·카카오 키를 채운다. 백엔드가 시작할 때 읽는다. 없으면 로그인만 안 되고 나머지는 동작한다.
- 운영 값은 환경변수로만 받는다: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `FRONT_URL`, `CORS_ALLOWED_ORIGINS`, `COOKIE_DOMAIN`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `ADMIN_NOTIFY_EMAIL`
- 자세한 규칙은 `docs/SECURITY.md`
