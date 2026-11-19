# 빈티지 디카 작례 사이트 — API 명세

> 상태: 초안 · 작성일: 2026-11-14 · 관련 문서: [PRD.md](./PRD.md), [ARCHITECTURE.md](./ARCHITECTURE.md), [ERD.md](./ERD.md)
>
> 구현 후에는 Swagger(springdoc-openapi)가 최신 명세가 된다. 이 문서는 구현 전 설계용이다.

## 1. 공통 규칙

### 1.1 주소

| 환경 | 프론트 | API |
|---|---|---|
| 운영 | `https://{도메인}` | `https://api.{도메인}` |
| 로컬 | `http://localhost:3000` | `http://localhost:8080` |

- 모든 API는 `/api`로 시작한다. (OAuth 로그인 주소만 예외, 2장 참고)
- 프론트와 API가 같은 상위 도메인을 써서 쿠키를 공유한다.

### 1.2 인증 방식: 세션 쿠키

로그인 상태는 **서버 세션 + httpOnly 쿠키**로 유지한다. (Spring Session JDBC, 세션은 PostgreSQL에 저장)

- JWT 대신 세션을 고른 이유:
  - 탈퇴, 관리자 권한 회수, 강제 로그아웃이 **즉시** 반영된다. JWT는 만료될 때까지 살아 있다.
  - 토큰 재발급 로직이 필요 없다.
  - 세션을 서버 메모리가 아니라 **DB에 저장**하므로, 재배포해도 로그인이 풀리지 않고 서버를 여러 대로 늘려도 모든 서버가 같은 세션을 본다. (ARCHITECTURE.md 3.9)
  - 사용자가 아주 많아져 요청마다 DB에서 세션을 읽는 게 부담이 되면, 세션 저장소를 Redis로 옮긴다. 코드는 거의 그대로 두고 설정만 바꾸면 된다.
- 쿠키 설정: `HttpOnly`, `Secure`, `SameSite=Lax`, `Domain=.{도메인}`
- 세션 만료: 마지막 요청 후 30일

### 1.3 CSRF

쿠키로 인증하므로 쓰기 요청(POST, PATCH, DELETE)은 CSRF 토큰이 필요하다.

- 서버가 `XSRF-TOKEN` 쿠키를 내려주고(JS에서 읽을 수 있음), 프론트는 쓰기 요청마다 같은 값을 `X-XSRF-TOKEN` 헤더에 넣는다. (Spring Security `CookieCsrfTokenRepository`)
- 쿠키는 API 응답마다 내려온다. 쓰기 요청 전에 쿠키가 없으면 `GET /api/csrf`(204)를 먼저 부른다. (`frontend/src/lib/client.ts`)
- 실패하면 `403 CSRF_TOKEN_INVALID`
- CORS: 프론트 도메인만 허용, `credentials: include`

### 1.4 응답 형식

성공하면 데이터를 그대로 반환한다.

실패하면:

```json
{
  "code": "PHOTO_NOT_FOUND",
  "message": "작례를 찾을 수 없어요."
}
```

### 1.5 페이지네이션 (커서 방식)

목록 API는 `cursor`와 `size`를 받는다.

```
GET /api/cameras/canon-ixus-30/photos?size=24&cursor=eyJ0Ijo...
```

```json
{
  "items": [ ... ],
  "nextCursor": "eyJ0Ijo..."
}
```

- `size` 기본 24, 최대 48 (24는 3·4·6칸 격자로 모두 나누어떨어짐)
- `nextCursor`가 null이면 마지막 페이지
- 커서는 마지막 항목의 `(created_at, id)`를 인코딩한 값. 프론트는 내용을 해석하지 않는다.

### 1.6 권한 표기

| 표기 | 의미 |
|---|---|
| 공개 | 로그인 없이 호출 가능 |
| 로그인 | USER 이상 |
| 관리자 | ADMIN만 |

## 2. 인증·회원

### 로그인 흐름

```
1. 프론트 "구글로 로그인" 클릭
   → GET https://api.{도메인}/oauth2/authorization/google  (Spring Security가 처리)
2. 구글 로그인 후 콜백
   → GET /login/oauth2/code/google  (Spring Security가 처리)
3-a. 이미 가입한 회원 → 세션 생성 → 프론트 원래 페이지로 이동
3-b. 처음 온 사람   → 회원은 아직 만들지 않고 "가입 대기" 정보만 세션에 저장
                    → 프론트 /signup 으로 이동
4. /signup 에서 닉네임 입력 + 약관 동의 → POST /api/auth/signup → 회원 생성, 로그인 완료
```

약관 동의 전에는 회원을 만들지 않는다. 동의 기록 없이 회원 정보가 저장되는 일을 막기 위해서다.

**로그인 후 돌아갈 페이지**

- 프론트는 로그인 버튼을 누를 때 돌아갈 경로를 넘긴다. 예: `/oauth2/authorization/kakao?redirect=/cameras/canon-ixus-30`
- 서버는 이 값을 세션에 저장해 두고, 로그인(또는 가입) 완료 후 그 경로로 보낸다.
- **우리 사이트 내부 경로만 허용한다.** `/`로 시작해야 하고 `//`나 `/\`로 시작하면 안 된다. 조건에 맞지 않으면 홈(`/`)으로 보낸다. 외부 사이트로 보내는 공격(Open Redirect)을 막기 위해서다. ([SECURITY.md](./SECURITY.md) 6장)

### `GET /api/auth/signup` — 가입 대기 정보 · 공개 (가입 대기 세션 필요)

가입 화면에 보여줄 정보를 준다.

```json
{
  "provider": "KAKAO",
  "terms": [
    { "type": "TERMS_OF_SERVICE", "version": "2026-11-01", "url": "/terms/2026-11-01" },
    { "type": "PRIVACY_COLLECTION", "version": "2026-11-01", "url": "/privacy-consent/2026-11-01" }
  ],
  "redirect": "/cameras/canon-ixus-30"
}
```

- `terms`: 지금 시행 중인 약관 목록. 모두 필수 동의다.
- `redirect`: 가입을 마친 뒤 돌아갈 내부 경로 (로그인 시작 때 저장한 값)
- 제공자에게서 닉네임을 받지 않으므로 미리 채워 줄 닉네임은 없다. 카카오 닉네임은 실명인 경우가 많아, 그대로 공개 닉네임이 되는 일을 막기 위해서다.

- 가입 대기 세션이 없으면 `401 SIGNUP_SESSION_NOT_FOUND`

### `POST /api/auth/signup` — 가입 완료 · 공개 (가입 대기 세션 필요)

```json
{
  "nickname": "디카러버",
  "agreedTerms": [
    { "type": "TERMS_OF_SERVICE", "version": "2026-11-01" },
    { "type": "PRIVACY_COLLECTION", "version": "2026-11-01" }
  ]
}
```

- 성공: `201`, 내 정보(`GET /api/me`와 같은 형식) 반환, 로그인 상태가 된다.
- 서버는 `users` 1행과 `terms_agreement`를 약관 종류별로 1행씩 한 트랜잭션으로 저장한다. (ERD.md 3.10)
- 실패:
  - `400 TERMS_NOT_AGREED`: 필수 약관 중 빠진 게 있음
  - `400 TERMS_VERSION_MISMATCH`: 그사이 약관이 바뀜. 화면을 새로고침하게 한다.
  - `409 NICKNAME_DUPLICATED`
  - `400 INVALID_NICKNAME`: 2~20자, 한글·영문·숫자·밑줄만

### `GET /api/users/nickname-availability?nickname=디카러버` — 닉네임 중복 확인 · 공개

```json
{ "available": true }
```

### `POST /api/auth/logout` — 로그아웃 · 로그인

- 성공: `204`

### `GET /api/me` — 내 정보 · 로그인

```json
{
  "id": 12,
  "nickname": "디카러버",
  "profileImageUrl": null,
  "role": "USER",
  "provider": "KAKAO",
  "photoCount": 37
}
```

- 로그인 안 했으면 `401 UNAUTHORIZED`. 프론트는 이걸로 로그인 여부를 판단한다.

### `PATCH /api/me` — 닉네임 변경 · 로그인

```json
{ "nickname": "새닉네임" }
```

- 실패: `409 NICKNAME_DUPLICATED`, `400 INVALID_NICKNAME`

### `DELETE /api/me` — 회원 탈퇴 · 로그인

```json
{ "confirm": true }
```

- 개인정보 삭제, 올린 작례 전부 삭제(R2 파일 포함), 세션 종료
- 성공: `204`
- `confirm`이 true가 아니면 `400 CONFIRM_REQUIRED`
- 작례가 많으면 R2 파일 삭제는 응답 후 비동기로 처리한다. DB 처리(작례 숨김)는 응답 전에 끝낸다.

## 3. 홈

### `GET /api/photos/recent` — 최근 올라온 작례 · 공개

홈의 "최근 올라온 작례" 격자. 사용자 업로드만 보여준다. (Commons 작례는 기종 페이지에서)

| 파라미터 | 설명 |
|---|---|
| cursor, size | 1.5 참고. 홈에서는 첫 페이지(24장)만 쓰고 "더 보기"로 다음 페이지 |

```json
{
  "items": [ /* 작례 요약 (7.1) */ ],
  "nextCursor": "…"
}
```

- 미분류 작례는 빼고, 기종이 정해진 작례만 보여준다.
- 사용자 업로드가 아직 적은 출시 직후에는 이 영역을 숨기거나, 24장이 안 되면 있는 만큼만 보여준다.
- "요즘 뜨는 디카"(`/api/trends`)는 추후 기능으로 뺐다. ([LATER.md](./LATER.md))

## 4. 브랜드·기종

### `GET /api/brands` — 브랜드 목록 · 공개

```json
[
  { "slug": "canon", "name": "Canon", "nameKo": "캐논", "cameraCount": 8 }
]
```

### `GET /api/brands/{slug}` — 브랜드 페이지 · 공개

```json
{
  "slug": "canon",
  "name": "Canon",
  "nameKo": "캐논",
  "cameras": [
    {
      "slug": "canon-ixus-30",
      "name": "IXUS 30",
      "nameKo": "익서스 30",
      "releaseYear": 2003,
      "photoCount": 52,
      "coverThumbnailUrl": "…"
    }
  ]
}
```

- 없으면 `404 BRAND_NOT_FOUND`
- 기종 수가 적어서(MVP 30개) 페이지네이션 없이 전부 준다.

### `GET /api/cameras/search?q=익서스` — 기종 검색 · 공개

| 파라미터 | 설명 |
|---|---|
| q | 검색어. 1~50자. ERD 4장의 정규화 규칙을 똑같이 적용 |
| limit | 기본 20 |

```json
{
  "items": [
    {
      "slug": "canon-ixus-30",
      "name": "IXUS 30",
      "nameKo": "익서스 30",
      "brandName": "Canon",
      "matchedAlias": "익서스 30",
      "photoCount": 52,
      "coverThumbnailUrl": "…"
    }
  ]
}
```

- 기종 대표 이름·한글 이름·별칭을 같은 정규화 규칙(ERD.md 4장)으로 비교한다. 띄어쓰기·하이픈·대소문자는 무시한다. ("sd 200" → PowerShot SD200)
- 정렬: 정확히 일치 > 앞부분 일치 > 포함, 같으면 작례 많은 순
- 브랜드 이름(영문·한글)과 정확히 같으면 그 브랜드 기종을 모두 보여준다. ("캐논" → 캐논 기종 전부)
- `%`, `_` 같은 문자는 글자 그대로 검색한다.
- 기종 대표 이름과 별칭을 함께 검색한다. `matchedAlias`는 어떤 별칭으로 걸렸는지 보여주는 용도 (대표 이름으로 걸렸으면 null)
- 결과가 없으면 빈 배열

### `GET /api/cameras/{slug}` — 기종 정보 · 공개

```json
{
  "slug": "canon-ixus-30",
  "name": "IXUS 30",
  "nameKo": "익서스 30",
  "brand": { "slug": "canon", "name": "Canon" },
  "releaseYear": 2003,
  "sensorType": "CCD",
  "aliases": ["익서스 30", "PowerShot SD200"],
  "photoCount": 52,
  "flashPhotoCount": 18,
  "originalSizePhotoCount": 31,
  "nativeResolution": "2048×1536"
}
```

- `nativeResolution`: 카메라 원래 해상도. 모르면 null (이때 원본 크기 필터를 숨긴다)

- 없으면 `404 CAMERA_NOT_FOUND`

### `GET /api/cameras/{slug}/photos` — 기종 작례 목록 · 공개

| 파라미터 | 설명 |
|---|---|
| flash | 생략: 전체 / `true`: 플래시 사용 / `false`: 미사용. 플래시 정보가 없는 작례는 `true`·`false`일 때 빠진다 |
| originalOnly | 생략 또는 `false`: 작게 줄인 사진까지 전부 / `true`: 원본 크기 사진만 (`is_original_size = true`) |
| cursor, size | 1.5 참고 |

- 두 필터는 같이 쓸 수 있다. 예) `?flash=true&originalOnly=true`

```json
{
  "items": [ /* 작례 요약 (7.1) */ ],
  "nextCursor": "…"
}
```

## 5. 작례

### `GET /api/photos/{id}` — 작례 상세 · 공개

```json
{
  "id": 1043,
  "source": "UPLOAD",
  "displayUrl": "https://img.{도메인}/display/…jpg",
  "thumbnailUrl": "https://img.{도메인}/thumbs/…jpg",
  "width": 2048,
  "height": 1536,
  "camera": { "slug": "canon-ixus-30", "name": "IXUS 30", "nameKo": "익서스 30", "brandName": "Canon" },
  "exif": {
    "takenAt": "2026-10-03T18:22:10",
    "flashFired": true,
    "fNumber": 2.8,
    "exposureTime": "1/60",
    "iso": 100,
    "focalLength": 5.4
  },
  "uploader": { "nickname": "디카러버", "profileImageUrl": null },
  "commons": null,
  "createdAt": "2026-11-14T10:00:00+09:00",
  "isMine": false
}
```

Commons 작례면 `uploader`가 null이고 `commons`가 채워진다. 이미지 주소는 업로드 작례와 똑같이 `img.{도메인}`이다.

```json
"commons": {
  "author": "John Doe",
  "licenseName": "CC BY-SA 4.0",
  "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
  "pageUrl": "https://commons.wikimedia.org/wiki/File:…jpg",
  "modified": true
}
```

- 화면에 반드시 함께 표시한다: 작가, 라이선스(링크), 원본 파일 페이지 링크, 크기 조정 여부. (예: "사진: John Doe · CC BY-SA 4.0 · Wikimedia Commons · 크기 조정됨")
- `modified`: 크기를 줄였으므로 항상 true. 라이선스 표시 의무 때문에 둔다.
- `licenseUrl`은 퍼블릭 도메인이면 null일 수 있다.
- `author`는 서버가 HTML을 지운 글자만 내려준다.

- 미분류 작례는 `camera`가 null
- `isOriginalSize`(작례 상세·요약 공통): true면 원본 크기, false면 줄인 사진, null이면 알 수 없음. "원본 크기만" 필터에 쓰고, 화면에 따로 표시하지는 않는다.
- 1600px보다 작은 사진은 `displayUrl`도 원래 크기 그대로다. 화면에서 늘리지 않는다.
- 삭제됐거나 없으면 `404 PHOTO_NOT_FOUND`
- **신고로 가려진 작례**는 올린 사람과 관리자에게만 보이고, 그 외에는 `404 PHOTO_NOT_FOUND`. 올린 사람에게는 `hiddenByReport: true`가 와서 안내를 보여준다.
- `isMine`: 로그인한 사용자가 올린 작례면 true. 삭제 버튼 표시용
  - 프론트의 서버 렌더링은 로그인 쿠키 없이 부르므로 항상 false다. (쿠키를 넘기면 페이지를 사람마다 따로 만들어야 해서 캐시를 못 쓴다) 프론트가 브라우저에서 한 번 더 불러 확인한다.

### `GET /api/photos/{id}/adjacent?flash=true&originalOnly=true` — 이전·다음 작례 · 공개

작례 상세에서 옆으로 넘길 때 쓴다. 순서와 필터는 기종 작례 목록(`/api/cameras/{slug}/photos`)과 같다.

```json
{
  "prev": { "id": 1044, "...": "작례 상세와 같은 형식" },
  "next": { "id": 1042, "...": "작례 상세와 같은 형식" }
}
```

- `prev`: 격자에서 바로 앞 작례(더 최신), `next`: 바로 뒤 작례(더 오래됨). 처음·끝이면 null
- 넘기자마자 정보까지 바꿔 보여주려고 상세 형식 전체를 준다.
- 미분류 작례는 기종 순서가 없으므로 둘 다 null
- 작례가 없거나 삭제됐으면 `404 PHOTO_NOT_FOUND`

### `POST /api/photos/{id}/views` — 조회수 증가 · 공개

- 상세 페이지가 브라우저에서 열릴 때 프론트가 호출한다. 서버 렌더링(검색봇 포함)에서는 호출하지 않는다.
- 성공: `204`
- 서버는 DB에서 `view_count = view_count + 1`로 바로 올린다. (ERD.md 5.1)
- MVP에서는 같은 사람의 반복 조회(새로고침)를 거르지 않는다.
- **사용자가 많아지면 반드시 바꾼다**: DB 부담이 커지면 모아서 반영하는 방식으로, 조회수를 인기 순위에 쓰기 시작하면 반복 조회를 거르도록. API 주소와 응답은 그대로 두고 서버 내부만 바꾼다. (ARCHITECTURE.md 3.8)

### 업로드 흐름

```
1. 사용자가 파일 선택
2. 프론트가 브라우저에서 EXIF를 읽는다 (exifr 라이브러리)
   → EXIF 없음: 원본 파일 안내, 업로드 막음
   → GET /api/cameras/exif-match 로 인식 결과를 미리 보여줌
3. 사용자가 확인 + 동의 체크 후 업로드
   → POST https://api.{도메인}/api/photos (파일 1번만 전송)
4. 서버가 EXIF를 다시 읽어서 최종 판단 (프론트 결과는 미리보기용일 뿐)
```

- **업로드만은 브라우저가 API 서버로 바로 보낸다.** 다른 API는 프론트의 `/api/*`를 거치지만, 최대 20MB 파일이 프론트 서버(Vercel)를 거치면 요청 크기 제한에 걸릴 수 있다. 쿠키(세션·CSRF)를 함께 보내야 해서 CORS `credentials: include`를 쓰고, 운영 쿠키는 `Domain=.{도메인}`이다. (1.2, 1.3)

### `GET /api/cameras/exif-match?make=Canon&model=Canon%20DIGITAL%20IXUS%2030` — EXIF 인식 미리보기 · 로그인

```json
{
  "matched": true,
  "camera": { "slug": "canon-ixus-30", "name": "IXUS 30", "nameKo": "익서스 30", "brandName": "Canon" }
}
```

- 등록 안 된 기종이면 `{ "matched": false, "camera": null }`. 화면에 "아직 등록되지 않은 기종이에요. 올리면 확인 후 기종 페이지에 추가돼요" 안내

### `POST /api/photos` — 작례 올리기 · 로그인

`multipart/form-data`

| 필드 | 설명 |
|---|---|
| file | 원본 사진. **JPEG만**, 최대 20MB |
| agreedToUploadPolicy | `true` 필수 ("직접 찍은 사진, 인물은 당사자 동의") |

서버 처리:
1. 파일 내용으로 JPEG인지 확인 (확장자·Content-Type은 믿지 않음). 헤더에서 가로·세로를 먼저 읽어 4,000만 픽셀을 넘으면 거부
2. EXIF 읽기 → 제조사·모델 없으면 거부
3. 기종 매칭: EXIF 모델명(`camera_exif_model`) → 못 찾으면 캐논 내부 번호(`camera_maker_model`, ERD 3.4.2) → 그래도 없으면 미분류
4. 썸네일(640px)·보기용(1600px)은 EXIF를 모두 지워 공개 버킷에, 원본은 GPS만 지워 비공개 버킷에 저장 (ARCHITECTURE.md 3.1)
5. `photo` 저장

- 응답과 모든 API에 원본 주소는 내려주지 않는다. **다운로드 기능은 없다.**

- 성공: `201`, 작례 상세(5장 첫 API와 같은 형식) 반환. 미분류면 `camera`가 null
- 실패:
  - `400 UPLOAD_POLICY_NOT_AGREED`
  - `400 INVALID_IMAGE`: JPEG가 아니거나 깨진 파일
  - `400 EXIF_NOT_FOUND`: 촬영 정보 없음 (인스타 등에서 받은 파일)
  - `400 IMAGE_DIMENSIONS_TOO_LARGE`: 픽셀 수가 4,000만을 넘음 (이미지 폭탄 방지)
  - `413 FILE_TOO_LARGE`
  - `429 UPLOAD_RATE_LIMITED`: 1시간에 30장 초과 (수치는 운영하며 조정). 최근 1시간 업로드 수를 DB에서 센다. 삭제한 작례도 포함한다.
  - `503 UPLOAD_BUSY`: 서버 한 대가 동시에 처리하는 업로드는 2장까지다 (`app.upload.max-concurrent`). 30초를 기다려도 차례가 오지 않으면 거절한다.

### `DELETE /api/photos/{id}` — 내 작례 삭제 · 로그인

- 본인이 올린 작례만 삭제할 수 있다. Commons 작례는 올린 사람이 없으므로 관리자만 지운다.
- DB 행은 남기고 `deleted_at`을 채운다(신고 기록이 참조). 보기용·썸네일·원본 파일은 실제로 지운다.
- DB를 먼저 바꾸고 파일을 지운다. 파일 삭제가 실패해도 작례는 이미 보이지 않고, 남은 파일은 나중에 정리 배치가 지운다.
- 성공: `204`
- 실패: `403 NOT_PHOTO_OWNER`, `404 PHOTO_NOT_FOUND`

### `POST /api/photos/{id}/reports` — 신고 · 로그인

```json
{
  "reason": "PORTRAIT_RIGHTS",
  "detail": "제가 나온 사진인데 동의한 적 없어요."
}
```

- `reason`: INAPPROPRIATE / STOLEN / PORTRAIT_RIGHTS / WRONG_CAMERA / OTHER
- `detail`: 최대 500자. `OTHER`면 필수
- **STOLEN·PORTRAIT_RIGHTS는 접수하는 즉시 작례를 가린다** (임시조치, PRD F8). 응답 `{ "hidden": true }`. 다른 사유는 `{ "hidden": false }`
- 접수되면 운영 메일로 알림을 보낸다. (메일 실패는 신고 접수에 영향 없음)
- 성공: `201`
- 실패: `409 ALREADY_REPORTED`, `404 PHOTO_NOT_FOUND`, `400 CANNOT_REPORT_OWN_PHOTO`, `400 INVALID_REQUEST`(사유·설명 형식), `429 REPORT_RATE_LIMITED`(하루 10건 초과)

### `GET /api/me/hidden-photos` — 신고로 가려진 내 작례 · 로그인

올린 사람에게 알리는 용도. 본인 프로필 화면에서 부른다.

```json
{ "items": [ { "id": 1043, "thumbnailUrl": "…", "hiddenAt": "2026-11-18T10:00:00+09:00" } ] }
```

## 6. 프로필

### `GET /api/users/{nickname}` — 프로필 · 공개

```json
{
  "nickname": "디카러버",
  "profileImageUrl": null,
  "photoCount": 37,
  "joinedAt": "2026-11-14T10:00:00+09:00",
  "isMe": false
}
```

- 탈퇴했거나 없으면 `404 USER_NOT_FOUND`
- `isMe`: 프론트의 서버 렌더링은 로그인 쿠키 없이 부르므로 항상 false다. 내 프로필 전용 버튼(작례 올리기·설정)은 프론트가 브라우저에서 `/api/me`로 확인해 보여준다.

### `GET /api/users/{nickname}/photos` — 프로필 피드 · 공개

- 파라미터: `cursor`, `size` (1.5 참고)
- 응답: 작례 요약 목록 (7.1). **미분류 작례도 포함**한다. (`camera`가 null, 화면에 "기종 확인 중" 표시)

## 7. 공통 응답 모델

### 7.1 작례 요약 (목록용)

```json
{
  "id": 1043,
  "source": "UPLOAD",
  "thumbnailUrl": "…",
  "width": 2048,
  "height": 1536,
  "flashFired": true,
  "isOriginalSize": true,
  "camera": { "slug": "canon-ixus-30", "name": "IXUS 30", "nameKo": "익서스 30" },
  "credit": "디카러버"
}
```

- `source`: UPLOAD / COMMONS
- `credit`: 업로드면 닉네임, Commons면 작가 이름. 격자에서 출처 표시용
- `width`, `height`: 격자에서 비율을 맞추기 위해 준다.

## 8. 관리자 API

모두 **관리자** 권한. Swagger UI에서 호출한다.

### 신고

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/admin/reports` | 미처리 신고 목록. 작례별로 묶어 오래된 것부터 (작례 썸네일·가림 여부·사유별 건수·설명) |
| POST | `/api/admin/reports/photos/{photoId}/delete` | 처리: 작례 삭제 |
| POST | `/api/admin/reports/photos/{photoId}/change-camera` | 처리: 기종 변경. 본문 `{ "cameraSlug": "canon-ixus-30" }`. 가렸으면 다시 공개 |
| POST | `/api/admin/reports/photos/{photoId}/reject` | 기각: 침해 아님. 가렸으면 다시 공개 |

- 그 작례에 미처리 신고가 없으면 `404 REPORT_NOT_FOUND`

- 처리는 작례 단위다. 그 작례의 PENDING 신고를 모두 함께 처리한다.
- 관리자 화면 `/admin/reports`가 이 API를 부른다. (PRD F8)
- 권한은 요청마다 DB의 `users.role`로 확인한다. 관리자가 아니면 `403 FORBIDDEN`

### 미분류 작례

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/admin/unclassified` | EXIF (제조사, 모델)별 미분류 작례 묶음. 많은 것부터 (최대 200묶음) |
| POST | `/api/admin/exif-models` | 연결. 기존 기종에 붙이거나 새 기종을 만들어 붙인다 |

`GET /api/admin/unclassified` 응답 (배열):

```json
[{
  "exifMake": "Canon", "exifModel": "Canon IXY DIGITAL 999",
  "count": 3, "lastUploadedAt": "2026-11-19T12:00:00+09:00",
  "makerModelId": "0x01550000",
  "maxWidth": 2048, "maxHeight": 1536,
  "brandSlug": "canon",
  "samples": [{ "id": 2332, "thumbnailUrl": "..." }],
  "candidates": [{ "slug": "canon-ixus-30", "name": "IXUS 30", "nameKo": "익서스 30", "brandName": "Canon", "reason": "MAKER_MODEL_ID" }]
}]
```

- `makerModelId`: 묶음의 사진이 캐논 내부 번호 하나만 가지고 있을 때만. 없거나 여러 개면 null
- `brandSlug`: EXIF 제조사에 브랜드 이름이 들어 있으면 그 브랜드 (`eastman kodak company` → `kodak`). 새 기종 만들 때 기본값
- `samples`: 최근 작례 4장까지
- `candidates`: 연결할 기종 후보. 판단은 관리자가 한다.
  - `MAKER_MODEL_ID`: 같은 캐논 내부 번호로 등록된 기종 (가장 믿을 만함)
  - `SIMILAR_NAME`: 같은 브랜드에서 숫자가 들어간 이름 조각("145", "s630", "t10")이 겹치는 기종, 최대 3개. 숫자 없이 "ixus"만 겹치는 건 후보로 보지 않는다.

`POST /api/admin/exif-models` 본문 — `cameraSlug`와 `newCamera` 중 하나만:

```json
{ "exifMake": "Canon", "exifModel": "Canon IXY DIGITAL 999", "cameraSlug": "canon-ixus-30" }
{ "exifMake": "Canon", "exifModel": "Canon IXUS 145",
  "newCamera": { "brandSlug": "canon", "name": "IXUS 145", "nameKo": "익서스 145", "sensorType": "CCD",
                 "nativeWidth": 4608, "nativeHeight": 3456, "releaseYear": 2014 } }
```

- 응답: `{ "cameraSlug": "canon-ixus-145", "moved": 1 }` (옮긴 작례 수)
- 한 트랜잭션에서: (새 기종이면 기종·검색 별칭 추가) → EXIF 모델명 등록 → 묶음의 캐논 내부 번호가 하나면 그 번호도 등록 → 같은 EXIF 모델명 또는 같은 내부 번호의 미분류 작례를 옮기고 원본 크기 여부를 새로 계산
- 다음부터 같은 모델명·내부 번호로 올라오는 사진은 자동으로 그 기종에 붙는다.
- 새 기종: `name`(브랜드 빼고, 100자) 필수. 주소 이름은 `브랜드-이름`으로 만든다 (`canon-ixus-145`). `nameKo`, `sensorType`(CCD·CMOS), 원래 해상도(가로·세로 같이), `releaseYear`는 선택
- 서버가 정규화해서 저장하므로 EXIF 원문을 그대로 보내면 된다.
- 실패: 이미 다른 기종에 연결된 EXIF 값 `409 EXIF_MODEL_ALREADY_MAPPED`, 같은 이름·주소의 기종이 있음 `409 CAMERA_ALREADY_EXISTS`, 없는 기종·브랜드 `404`, 둘 다 보내거나 둘 다 안 보냄·잘못된 값 `400 INVALID_REQUEST`
- 관리자 화면 `/admin/unclassified`가 이 API를 부른다.

### 브랜드·기종

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/admin/brands` | 브랜드 추가 |
| POST | `/api/admin/cameras` | 기종 추가 |
| PATCH | `/api/admin/cameras/{id}` | 기종 수정 (대표 작례 지정 포함) |
| POST | `/api/admin/cameras/{id}/aliases` | 별칭 추가 |
| DELETE | `/api/admin/aliases/{id}` | 별칭 삭제 |
| POST | `/api/admin/cameras/{id}/source-categories` | 작례를 가져올 Commons 분류 추가. 본문 `{ "categoryName": "Taken with Sony DSC-T1" }` |
| DELETE | `/api/admin/source-categories/{id}` | Commons 분류 연결 삭제 (이미 가져온 작례는 남음) |

### 작례

| 메서드 | 경로 | 설명 |
|---|---|---|
| DELETE | `/api/admin/photos/{id}` | 작례 강제 삭제. Commons 작례의 저작권자 삭제 요청, 작례로 쓰기 애매한 Commons 사진 정리에도 사용 |

### 배치

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/admin/batches/{name}/run` | 배치 수동 실행. `commons-collect` / `commons-cleanup` |

- 이미 실행 중이면 `409 BATCH_ALREADY_RUNNING`

## 9. 에러 코드 모음

| HTTP | 코드 | 상황 |
|---|---|---|
| 400 | INVALID_REQUEST | 형식이 잘못된 요청 (공통) |
| 400 | INVALID_NICKNAME | 닉네임 규칙 위반 |
| 400 | TERMS_NOT_AGREED | 약관 미동의 |
| 400 | TERMS_VERSION_MISMATCH | 약관 버전이 바뀜 |
| 400 | CONFIRM_REQUIRED | 탈퇴 확인 누락 |
| 400 | UPLOAD_POLICY_NOT_AGREED | 업로드 동의 누락 |
| 400 | INVALID_IMAGE | JPEG가 아니거나 깨진 파일 |
| 400 | EXIF_NOT_FOUND | 촬영 정보 없음 |
| 400 | IMAGE_DIMENSIONS_TOO_LARGE | 픽셀 수 초과 (4,000만) |
| 400 | CANNOT_REPORT_OWN_PHOTO | 본인 작례 신고 |
| 401 | UNAUTHORIZED | 로그인 필요 |
| 401 | SIGNUP_SESSION_NOT_FOUND | 가입 대기 정보 없음 (만료) |
| 403 | FORBIDDEN | 권한 없음 (관리자 API 등) |
| 403 | NOT_PHOTO_OWNER | 남의 작례 삭제 시도 |
| 403 | CSRF_TOKEN_INVALID | CSRF 토큰 누락·불일치 |
| 404 | BRAND_NOT_FOUND / CAMERA_NOT_FOUND / PHOTO_NOT_FOUND / USER_NOT_FOUND | |
| 404 | REPORT_NOT_FOUND | 관리자 처리: 그 작례에 미처리 신고가 없음 |
| 409 | NICKNAME_DUPLICATED | 닉네임 중복 |
| 409 | ALREADY_REPORTED | 이미 신고함 |
| 429 | REPORT_RATE_LIMITED | 하루 신고 10건 초과 |
| 409 | EXIF_MODEL_ALREADY_MAPPED | EXIF 값이 이미 다른 기종에 연결됨 |
| 409 | CAMERA_ALREADY_EXISTS | 관리자 기종 추가: 같은 브랜드에 같은 이름, 또는 같은 주소 이름의 기종이 있음 |
| 409 | BATCH_ALREADY_RUNNING | 배치 실행 중 |
| 413 | FILE_TOO_LARGE | 20MB 초과 |
| 429 | UPLOAD_RATE_LIMITED | 업로드 횟수 초과 |
| 500 | INTERNAL_ERROR | 서버 오류 (Sentry에 기록) |
| 503 | UPLOAD_BUSY | 동시에 올리는 사람이 많아 30초 안에 처리를 시작하지 못함 |

## 10. 미정 사항

- [x] 업로드 횟수 제한 → 1시간 30장으로 시작, 운영하며 조정
- [x] 이미지 도메인 → `img.{도메인}` 커스텀 도메인 연결
- [x] 프로필 이미지 업로드 → MVP에서 제외. 닉네임 첫 글자로 기본 아바타 표시 (`profileImageUrl`은 null)
