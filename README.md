# dica-jakrye

빈티지 디카 작례 사이트. 기종별로 그 카메라로 찍은 작례를 모아 보여준다.

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

## 비밀 정보

- `.env`, `application-local.yml`은 Git에 올리지 않는다. (`.gitignore`)
- 운영 DB 접속 정보는 환경변수 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로만 받는다.
- 자세한 규칙은 `docs/SECURITY.md`
