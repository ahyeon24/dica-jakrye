# CLAUDE.md

빈티지 디카(2000년대~2010년대 초 똑딱이) 작례 사이트. 기종별로 그 카메라로 찍은 작례를 한국어로 모아 보여준다.
포트폴리오가 아니라 **실제로 배포·운영할 서비스**다. 운영 관점(법적 고지, 개인정보, 비용, 장애)을 기본으로 고려한다.

## 문서가 기준이다

설계는 `docs/`에 있고, 코드는 문서를 따른다. 설계를 바꾸면 문서도 같이 고치고 `docs/pdf/`의 PDF도 다시 만든다.

| 문서 | 내용 |
|---|---|
| `docs/PRD.md` | 기능 범위(F1~F10), MVP 포함·제외 |
| `docs/ARCHITECTURE.md` | 구성, 배치 설계(3.3~3.9), 규모 확장 체크리스트(3.8) |
| `docs/ERD.md` | 테이블, 인덱스, 정규화 규칙(4장), 조회수 방식(5.1) |
| `docs/API.md` | API 명세, 에러 코드(9장) |
| `docs/SECURITY.md` | 보안 체크리스트. 기능을 만들 때 해당 항목을 확인 |
| `docs/CAMERAS.md` | 초기 기종 목록과 Commons 확인 결과 |
| `docs/LATER.md` | MVP에서 뺀 기능(인기 기종 등)의 조사·검증 결과 |

## 폴더와 명령

- `backend/`: Spring Boot 4.1, Java 21, PostgreSQL 17, Flyway, JPA + JdbcClient, Spring Security(구글·카카오 OAuth) + Spring Session JDBC
  - 로컬 로그인 키는 `backend/.env` (Git 제외, `.env.example` 참고)
  - 테스트의 쓰기 요청은 `TestCsrf.csrf()`를 쓴다. spring-security-test의 `csrf()`는 앱의 CSRF 저장소를 세션 방식으로 바꿔 다른 테스트를 깨뜨린다.
- `poc/`: 외부 데이터 검증 스크립트 (Commons, 네이버 데이터랩). `poc/.env`에 키가 있다
  - `poc/maker_model_id/`: 캐논 나라별 이름(IXUS·IXY·PowerShot SD)이 같은 기종인지 내부 번호로 확인. 캐논 기종·별칭을 추가할 때 쓴다
- `frontend/`: Next.js 16.4 (App Router, **Cache Components 켬**), TypeScript, Tailwind CSS 4
  - **이 Next.js는 예전 버전과 API가 다르다. 코드를 쓰기 전에 `frontend/node_modules/next/dist/docs/`의 해당 문서를 읽는다.** (`frontend/AGENTS.md`)
  - 백엔드 데이터를 읽는 서버 컴포넌트는 `<Suspense>` 안에 둔다. `params`·`searchParams`는 Promise다.
  - `usePathname`·`useSearchParams`를 쓰는 클라이언트 컴포넌트도 `<Suspense>` 안에 둔다. (빠뜨리면 개발 화면에 "Blocking Route" 오류)
  - 화면을 고친 뒤에는 curl만 보지 말고 브라우저에서 Next 오류 표시가 없는지 확인한다.
  - 브라우저의 쓰기 요청은 `lib/client.ts`의 `apiSend`를 쓴다. (CSRF 헤더를 붙임)
  - 서버에서는 `src/lib/api.ts`(server-only)로 백엔드를 직접 부르고, 브라우저에서는 `/api/*`(rewrites로 백엔드에 전달)를 부른다.
  - 이미지는 백엔드가 이미 크기를 맞춰 두므로 `next/image` 최적화를 쓰지 않는다. (`components/Thumb.tsx`)
  - 화면은 반응형 하나로, 휴대폰 먼저. 작례 격자는 3·4·6칸 (ARCHITECTURE.md 2.1)

```bash
docker compose up -d                 # 로컬 DB (포트 5433, 이 맥의 5432는 다른 PostgreSQL이 사용 중)
cd backend && ./gradlew test         # Testcontainers로 빈 DB를 띄워 테스트 (Docker 필요)
cd backend && ./gradlew bootRun      # local 프로필, http://localhost:8080
cd frontend && npm run dev            # http://localhost:3000 (백엔드가 켜져 있어야 함)
cd frontend && npx tsc --noEmit && npm run lint
# Commons 수집을 특정 기종만 한 번 돌리기
./gradlew bootRun --args='--app.batch.run-on-startup=commons-collect --app.batch.commons-collect.only-slugs=canon-ixus-30 --app.commons.per-camera=20'
```

## 꼭 지킬 규칙

**DB**
- 테이블은 Flyway 마이그레이션(`backend/src/main/resources/db/migration`)으로만 바꾼다. JPA는 검사만 한다(`ddl-auto: validate`).
- **운영에 배포된 마이그레이션 파일은 절대 고치지 않는다.** 바꿀 내용은 새 번호(V3, V4…)로 추가한다. 아직 배포 전이면 고쳐도 되지만, 그때는 로컬 DB를 새로 만들어야 한다(`docker compose down -v`).
- ERD의 규칙은 DB 제약 조건으로도 건다.

**기종 데이터**
- `V2__seed_cameras.sql`은 `backend/scripts/gen_seed_cameras.py`가 만든다. **SQL을 직접 고치지 않는다.** 스크립트를 고치고 다시 생성한다.
- EXIF 모델명은 실제 사진(Commons 표본 등)에서 확인된 값만 넣는다. 짐작으로 나라별 이름을 짝짓지 않는다. (캐논 IXUS↔IXY 대응을 짐작했다가 틀린 적 있음. IXY DIGITAL 110 IS는 IXUS 110 IS와 다른 기종)
- 캐논은 내부 번호(Canon Model ID)로 확인한다: `poc/maker_model_id/README.md`. 확인한 번호는 새 마이그레이션으로 `camera_maker_model`에 넣는다.

**정규화**
- 별칭·EXIF 정규화 규칙은 `TextNormalizer.java`와 `gen_seed_cameras.py`가 **똑같아야** 한다. 한쪽을 바꾸면 다른 쪽도 바꾸고, 두 쪽의 같은 예시 테스트가 모두 통과해야 한다.

**이미지·저장소**
- 저장 키는 `StorageKeys`로 서버가 만든다. 사용자가 보낸 파일명을 쓰지 않는다.
- 공개 파일(썸네일·보기용)은 EXIF를 모두 지운다. 원본(비공개)은 GPS만 지운다. 다운로드 기능·원본 공개 주소는 만들지 않는다.
- 작은 사진은 늘리지 않는다.

**Wikimedia Commons**
- 위키미디어 이용 규칙을 지킨다: User-Agent(연락처는 운영 메일 dicajakrye@gmail.com), 순차 요청, `maxlag`, 다운로드 간격(현재 3초). 간격을 줄이지 않는다. 1초 간격에서 429로 막히고 10분 제한에 걸린 적 있다.
- 분류만 믿지 않고 EXIF 모델이 그 기종인지 확인한다. 작가 HTML은 저장 전에 지운다.
- 작례마다 작가·라이선스·원본 링크·크기 조정 표시를 보여줘야 한다.

**보안·개인정보**
- 비밀 값(.env, 키, 비밀번호)은 Git에 올리지 않는다. 운영 값은 환경변수로만 받는다.
- 에러 응답은 `{code, message}`만. 스택 트레이스·SQL 오류를 내보내지 않는다.
- 사용자 개인 정보(이메일 등)를 외부 서비스 요청에 넣지 않는다.
- 서버 메모리에 "기억해야 하는 값"을 두지 않는다. 서버를 여러 대로 늘려도 맞게 동작해야 한다. (ARCHITECTURE.md 3.9)

## 작업 방식

- **main에 바로 push하지 않는다.** 기능마다 브랜치(`feat/…`, `fix/…`, `chore/…`, `docs/…`)를 만들고 PR로 합친다.
  1. 브랜치에서 작업하고 커밋 (커밋 메시지는 먼저 보여 주고 확인받는다)
  2. push하고 PR을 연다. 본문에 무엇을 왜 바꿨는지, 어떻게 확인했는지 적는다. 관련 이슈가 있으면 `Closes #번호`
  3. PR의 CI(백엔드 테스트, 프론트 검사·빌드)가 **통과한 뒤에만** 합친다. 실패하면 고쳐서 다시 push
  4. 합치는 방식은 squash, 합친 뒤 브랜치는 지운다 (`gh pr merge --squash --delete-branch`)
  - 비공개 저장소 무료 요금제라 GitHub 브랜치 보호 규칙을 쓸 수 없다. 규칙이 막아 주지 않으므로 이 순서를 직접 지킨다.
- 할 일은 GitHub Issues에 둔다. 대화나 문서 체크박스에만 남기지 않는다.
- 조사·검증에 쓴 스크립트는 임시 폴더에 두지 않고 `poc/`에 남긴다. 문서에 "확인했다"고 적으면 다시 돌려 볼 수 있어야 한다.
- 응답과 문서는 한국어로 쓴다.
- 기능을 만들면 테스트를 함께 만들고, 실제로 돌려서 확인한 뒤 결과를 알려 준다.
- 외부 서비스의 정책·요금·API는 기억으로 단정하지 말고 공식 문서나 실제 호출로 확인한다. (Flickr 키 발급 조건, 네이버 데이터랩 이관을 기억으로 안내했다가 틀린 적 있음)
