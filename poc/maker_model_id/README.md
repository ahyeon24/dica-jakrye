# 캐논 내부 기종 번호 확인

같은 카메라가 나라별로 다른 이름(IXUS·IXY·PowerShot SD)으로 팔린 경우, 짐작으로 짝짓지 않고 **캐논 MakerNote의 Model ID**로 확인한다.
결과는 `docs/CAMERAS.md` 2장 "캐논 내부 번호", 적용은 `backend/src/main/resources/db/migration/V5__maker_model_id.sql`.

새 캐논 기종을 추가하거나 별칭(IXY 이름 등)을 넣을 때 이 순서로 확인한다.

## 1. 분류에 어떤 이름이 섞여 있는지 세기

```bash
cd poc/maker_model_id
# 기종slug<TAB>분류 이름 ("Taken with " 뺀 것)
printf 'canon-ixus-30\tCanon Digital IXUS 30\n' > categories.tsv
python3 -I exif_model_survey.py categories.tsv --samples 3 > survey.txt
```

- 파일은 받지 않고 Commons API의 메타데이터만 읽는다. 분류 하나에 최대 3,000장, 요청 사이 1초.
- 맨 아래 `S` 줄이 가장 많은 이름이 아닌 모델명의 표본 파일이다.

## 2. 표본의 내부 번호 읽기

```bash
grep '^S' survey.txt | cut -f2- > samples.tsv
C=~/.gradle/caches/modules-2/files-2.1
CP=$(ls $C/com.drewnoakes/metadata-extractor/2.19.0/*/*.jar | grep -v sources):$(ls $C/com.adobe.xmp/xmpcore/6.1.11/*/*.jar | grep -v sources)
java -cp "$CP" ModelId.java samples.tsv
```

- 백엔드를 한 번 빌드했으면 metadata-extractor가 Gradle 캐시에 있다. (버전은 `backend/build.gradle`과 맞춘다)
- 다운로드는 3초 간격, 파일 앞 256KB만 받는다. 한 번에 50장까지.
- 비교할 기존 기종의 표본도 같이 넣는다. 같은 번호면 같은 기종이다.

## 판단 기준

- 번호가 같으면 같은 기종 → `camera_exif_model`(EXIF 이름)과 `camera_alias`(검색 별칭)에 추가
- 번호가 다르면 다른 기종 → 이름이 비슷해도 넣지 않는다 (예: IXY DIGITAL 110 IS ≠ IXUS 110 IS)
- 번호가 없으면(편집 프로그램을 거친 사진) 판단하지 않는다. 다른 표본을 본다.
- 기종마다 번호를 2장 이상에서 확인한다.

## 다른 제조사

2026-11-19에 니콘·후지필름·소니·카시오·리코·코닥·올림푸스·삼성도 봤는데 기종 번호로 쓸 값이 없었다.
올림푸스·삼성은 모델명 칸에 나라별 이름을 같이 적는다 (`u700,S700`, `<KENOX S630 / Samsung S630>`).
