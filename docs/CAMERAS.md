# 빈티지 디카 작례 사이트 — 초기 기종 목록

> 상태: **확정 (2차)** · 남은 확인 사항은 6장 · 작성일: 2026-11-17 · 관련 문서: [PRD.md](./PRD.md), [ERD.md](./ERD.md)
>
> 확인 스크립트: `poc/camera_candidates.py` (1차 결과 원본: `poc/candidates_result.json`)

## 1. 정하는 방법

1. **1차 후보**: 2000년대 CCD 똑딱이 위주로 36개를 뽑아 Commons로 확인했다.
2. **2차 조정**: 국내 인기 조사(브랜드 선호 순위, 셀럽·방송 화제 기종)를 반영해 기종을 추가·교체했다.
   - 브랜드 선호: 캐논 > 코닥·후지필름 > 산요 > 니콘·리코
   - 화제 기종: 코닥 C713(카리나), 캐논 익서스 310·110(방송·인플루언서), 리코 R10(뉴진스 "Ditto" 감성), 산요 작티
3. **Commons 확인**: 기종마다 Commons 분류 이름, 사진 수, EXIF 모델명, 사진 크기를 스크립트로 확인했다.
4. **최종 30개**: 국내 인기를 우선하고, Commons에 사진이 없는 인기 기종은 지인·커뮤니티 작례로 채운다.

## 2. 확인하면서 알게 된 것

**국내에서 화제인 기종 일부는 Commons에 사진이 없다**
- 캐논 익서스 230 HS(0장), 310 HS(1장), 산요 작티 J1·DMX-C40(0장)
- 국내 유행 기종과 해외 자유 라이선스 사진 저장소의 사진 분포가 다르다. 이 기종들은 **출시 전에 지인·커뮤니티에서 작례를 직접 모아야** 한다. 모으지 못하면 기종 페이지는 "첫 작례를 올려주세요" 상태로 출시한다. (와이어프레임 2쪽)

**국내 인기 기종에 2010년대 CMOS 기종도 들어 있다**
- 익서스 230 HS·310 HS(2011)는 CMOS 센서다. ("HS" = CMOS) PRD는 "2000년대 CCD 똑딱이"를 대상으로 적었는데, 실제 국내 수요는 2010년대 초반 슬림 똑딱이까지 포함한다.
- 그래서 대상 범위를 **"2000년대~2010년대 초 똑딱이 (CCD 위주, CMOS 포함)"** 로 넓혔다. (PRD 2장) 센서 종류는 `camera.sensor_type`에 적어 기종 페이지에 보여준다.
- 후지필름 X100은 APS-C 대형 센서의 고급 카메라라서 "똑딱이"와 시장·가격대가 달라 **넣지 않는다.**

**캐논은 나라별 이름이 제각각이고, 내가 알던 대응이 일부 틀렸다**
- 같은 카메라가 유럽은 IXUS, 미국은 PowerShot SD·ELPH, 일본은 IXY로 팔렸다.
- Commons 사진의 EXIF로 확인된 대응: IXUS 55 = IXY 60, IXUS 60 = IXY 70, IXUS 70 = IXY 10, IXUS 75 = IXY 90, IXUS 750 = IXY 700, IXUS 800 IS = IXY 800 IS, IXUS 110 IS = PowerShot SD960 IS
- 처음엔 IXUS 55 = IXY 55처럼 잘못 짝지었는데 EXIF가 바로잡아 줬다. **사람 기억보다 EXIF가 정확하다.**
- 한국에서는 "익서스(IXUS)"로 많이 부르므로 **캐논 똑딱이는 IXUS 이름을 대표 이름**으로 쓰고, IXY·PowerShot SD는 별칭으로 둔다. EXIF로 확인되지 않은 IXY 이름은 넣지 않는다.

**Commons 분류에 다른 카메라 사진이 섞여 있다**
- 예) 앞선 검증에서 IXUS 30 분류에 IXY DIGITAL 40(다른 카메라로 추정)이 섞여 있었다. 루믹스 FX01 분류에는 FX07이 함께 집계됐다.
- 그래서 분류만 믿지 않고 **EXIF 모델명이 그 기종 목록에 있는 사진만** 가져온다. (ARCHITECTURE.md 3.3)

**EXIF 모델명 표기가 브랜드마다 이상하다**
- 삼성: `<Samsung NV3, Samsung VLUU NV3>`, `<KENOX S630  / Samsung S630>`
- 올림푸스: `u700,S700`
- 니콘: COOLPIX 3200은 `E3200`, COOLPIX S1은 `S1`
- 코닥: `KODAK EASYSHARE C713 ZOOM DIGITAL CAMERA`
- 실제 표본에서 나온 값을 정규화(ERD.md 4장)해서 `camera_exif_model`에 넣는다. 사진이 없는 기종은 실제 사진을 받은 뒤 확인해서 넣는다.

**Commons 분류 이름 규칙도 브랜드마다 다르다**
- 소니: `Sony DSC-T1` ("Cyber-shot"을 붙이면 없음)
- 올림푸스: `Olympus u700 / S-700` (μ, mju, Stylus로는 없음)
- 캐논 2000년대: `Canon Digital IXUS 30`, 캐논 2010년대: `Canon IXUS 230 HS` ("Digital" 없음)

**원래 해상도는 스펙 값을 쓴다**
- Commons 표본의 최대 크기는 파노라마·이어 붙인 사진·확대한 사진 때문에 틀린 경우가 있었다. (DSC-W55 6773×2643, G11 7234×3501 등)
- 아래 표의 원래 해상도는 **카메라 스펙(4:3 최대 크기)** 기준이다. 스펙 값은 제조사 자료로 한 번 더 확인한다. "확인 필요"는 스펙을 확실히 모르는 것.

## 3. 추천 30개

- **Commons**: 분류에 있는 파일 수. 작례로 실제 쓸 수 있는 건 이보다 적다. (검증에서 조건 통과율 약 90%)
- **⚠ 직접 수집**: Commons에 사진이 없어 지인·커뮤니티 작례로 채워야 하는 기종

### 캐논 (11)

| 대표 이름 (국내) | 다른 이름 | Commons 분류 | Commons | EXIF 모델 (표본) | 원래 해상도 | 센서 | 비고 |
|---|---|---|---|---|---|---|---|
| 익서스 30 (IXUS 30) | PowerShot SD200 | Canon Digital IXUS 30 | 3,485 | Canon DIGITAL IXUS 30, Canon PowerShot SD200 | 2048×1536 | CCD | IXY 이름 미확인 |
| 익서스 40 (IXUS 40) | PowerShot SD300 | Canon Digital IXUS 40 | 12,712 | Canon DIGITAL IXUS 40, Canon PowerShot SD300 | 2272×1704 | CCD | 국내 대표 인기 모델. IXY 이름 미확인 |
| 익서스 55 (IXUS 55) | PowerShot SD450, IXY DIGITAL 60 | Canon Digital IXUS 55 | 11,788 | + Canon IXY DIGITAL 60 | 2592×1944 | CCD | |
| 익서스 60 (IXUS 60) | PowerShot SD600, IXY DIGITAL 70 | Canon Digital IXUS 60 | 38,920 | + Canon IXY DIGITAL 70 | 2816×2112 | CCD | |
| 익서스 70 (IXUS 70) | PowerShot SD1000, IXY DIGITAL 10 | Canon Digital IXUS 70 | 16,992 | + Canon IXY DIGITAL 10 | 3072×2304 | CCD | |
| 익서스 75 (IXUS 75) | PowerShot SD750, IXY DIGITAL 90 | Canon Digital IXUS 75 | 13,496 | + Canon IXY DIGITAL 90 | 3072×2304 | CCD | |
| 익서스 110 IS (IXUS 110) | PowerShot SD960 IS | Canon Digital IXUS 110 IS | 1,280 | Canon DIGITAL IXUS 110 IS, Canon PowerShot SD960 IS | 4000×3000 | CCD | 방송·인플루언서 화제 |
| 익서스 230 HS (IXUS 230) | PowerShot ELPH 310 HS | — | **0** | — | 4000×3000 | CMOS | ⚠ 직접 수집. 국내 대표 인기 모델 |
| 익서스 310 HS (IXUS 310) | PowerShot ELPH 500 HS | Canon IXUS 310 HS | **1** | — | 4000×3000 | CMOS | ⚠ 직접 수집. 방송·인플루언서 화제 |
| 파워샷 G9 | PowerShot G9 | Canon PowerShot G9 | 22,477 | Canon PowerShot G9 | 4000×3000 | CCD | 고급 똑딱이 |
| 파워샷 G11 | PowerShot G11 | Canon PowerShot G11 | 25,012 | Canon PowerShot G11 | 3648×2736 | CCD | 고급 똑딱이. 표본 최대 7234는 파노라마 |

### 코닥 (2)

| 대표 이름 (국내) | 다른 이름 | Commons 분류 | Commons | EXIF 모델 (표본) | 원래 해상도 | 센서 | 비고 |
|---|---|---|---|---|---|---|---|
| 코닥 이지쉐어 C713 | EasyShare C713 | Kodak EasyShare C713 | 1,600 | KODAK EASYSHARE C713 ZOOM DIGITAL CAMERA | 3072×2304 (확인 필요, 표본 최대 3056×2292) | CCD | 셀럽(카리나) 화제 |
| 코닥 이지쉐어 V570 | EasyShare V570 | Kodak EasyShare V570 | 1,621 | KODAK V570 DUAL LENS DIGITAL CAMERA | 2576×1932 | CCD | 렌즈 2개 특이 기종. 코닥 수요가 높아 1차 보류에서 살림 |

### 후지필름 (4)

| 대표 이름 (국내) | 다른 이름 | Commons 분류 | Commons | EXIF 모델 (표본) | 원래 해상도 | 센서 | 비고 |
|---|---|---|---|---|---|---|---|
| 파인픽스 F470 | FinePix F470 | Fujifilm FinePix F470 | 701 | FinePix F470 | 2816×2112 | CCD | 국내 대표 인기 모델 |
| 파인픽스 F31fd | FinePix F31fd | Fujifilm FinePix F31fd | 4,040 | FinePix F31fd | 2848×2136 | CCD | |
| 파인픽스 F10 | FinePix F10 | Fujifilm FinePix F10 | 7,517 | FinePix F10 | 2848×2136 | CCD | |
| 파인픽스 Z1 | FinePix Z1 | Fujifilm FinePix Z1 | 1,829 | FinePix Z1 | 2592×1944 | CCD | |

### 산요 (2)

| 대표 이름 (국내) | 다른 이름 | Commons 분류 | Commons | EXIF 모델 (표본) | 원래 해상도 | 센서 | 비고 |
|---|---|---|---|---|---|---|---|
| 작티 J1 | Xacti DMX-J1 | — | **0** | — | 확인 필요 | 확인 필요 | ⚠ 직접 수집. 세로형 캠코더 디자인 |
| 작티 DMX-C40 | Xacti C40 | — | **0** | — | 확인 필요 | 확인 필요 | ⚠ 직접 수집. 모델명 정확한지 확인 필요 |

### 리코 (1)

| 대표 이름 (국내) | 다른 이름 | Commons 분류 | Commons | EXIF 모델 (표본) | 원래 해상도 | 센서 | 비고 |
|---|---|---|---|---|---|---|---|
| 리코 R10 | Ricoh R10 | Ricoh R10 | 9,761 | RICOH R10 | 3648×2736 | CCD | 뉴진스 "Ditto" 감성으로 화제 |

### 니콘 (3)

| 대표 이름 (국내) | 다른 이름 | Commons 분류 | Commons | EXIF 모델 (표본) | 원래 해상도 | 센서 | 비고 |
|---|---|---|---|---|---|---|---|
| 쿨픽스 S4100 | COOLPIX S4100 | Nikon Coolpix S4100 | 2,341 | COOLPIX S4100 | 4320×3240 | CCD | 국내 대표 인기 모델 |
| 쿨픽스 S1 | COOLPIX S1 | Nikon Coolpix S1 | 116 | S1 | 2592×1944 | CCD | 사진 적음 (검증에서 67장 확보) |
| 쿨픽스 3200 | COOLPIX 3200 | Nikon Coolpix 3200 | 23,881 | E3200 | 2048×1536 | CCD | EXIF 모델이 `E3200` |

### 소니 (3)

| 대표 이름 (국내) | 다른 이름 | Commons 분류 | Commons | EXIF 모델 (표본) | 원래 해상도 | 센서 | 비고 |
|---|---|---|---|---|---|---|---|
| 사이버샷 DSC-T1 | Cyber-shot T1 | Sony DSC-T1 | 3,716 | DSC-T1 | 2592×1944 | CCD | |
| 사이버샷 DSC-T10 | Cyber-shot T10 | Sony DSC-T10 | 6,882 | DSC-T10 | 3072×2304 | CCD | |
| 사이버샷 DSC-W55 | Cyber-shot W55 | Sony DSC-W55 | 16,695 | DSC-W55 | 3072×2304 | CCD | 표본 최대 6773은 파노라마 |

### 삼성 (2)

| 대표 이름 (국내) | 다른 이름 | Commons 분류 | Commons | EXIF 모델 (표본) | 원래 해상도 | 센서 | 비고 |
|---|---|---|---|---|---|---|---|
| 블루 NV3 (VLUU NV3) | Samsung NV3 | Samsung NV3 | 114 | <Samsung NV3, Samsung VLUU NV3> | 3072×2304 | CCD | 국내 기종. 사진 적음 (검증에서 73장 확보) |
| 케녹스 S630 (KENOX S630) | Samsung S630 | Samsung S630 | 124 | <KENOX S630  / Samsung S630> | 2816×2112 | CCD | 국내 기종 |

### 올림푸스·카시오 (2)

| 대표 이름 (국내) | 다른 이름 | Commons 분류 | Commons | EXIF 모델 (표본) | 원래 해상도 | 센서 | 비고 |
|---|---|---|---|---|---|---|---|
| 뮤 700 (μ 700) | Stylus 700, mju 700 | Olympus u700 / S-700 | 5,116 | u700,S700 | 3072×2304 | CCD | |
| 엑슬림 EX-Z750 | EXILIM Z750 | Casio Exilim EX-Z750 | 437 | EX-Z750 | 3072×2304 | CCD | |

## 4. 보류 (후보였지만 30개에서 뺀 기종)

국내 인기 조사에서 언급이 적어 뺐다. Commons 사진은 충분하므로, 나중에 기종을 늘릴 때 먼저 넣는다.

| 기종 | Commons 분류 | Commons | EXIF 모델 | 원래 해상도 | 뺀 이유 |
|---|---|---|---|---|---|
| 캐논 익서스 750 | Canon Digital IXUS 750 | 17,538 | + Canon IXY DIGITAL 700 | 3072×2304 | 캐논이 많아 인기 모델 우선 |
| 캐논 익서스 800 IS | Canon Digital IXUS 800 IS | 20,393 | + Canon IXY DIGITAL 800 IS | 2816×2112 | 〃 |
| 캐논 파워샷 A570 IS | Canon PowerShot A570 IS | 12,068 | Canon PowerShot A570 IS | 3072×2304 | AA 건전지형, 감성 똑딱이 수요와 거리 |
| 소니 DSC-T100 | Sony DSC-T100 | 2,915 | DSC-T100 | 3264×2448 | 소니 언급 적음 |
| 소니 DSC-W1 | Sony DSC-W1 | 10,245 | DSC-W1 | 2592×1944 | 〃 |
| 소니 DSC-W80 | Sony DSC-W80 | 7,237 | DSC-W80 | 3072×2304 | 〃 |
| 후지 파인픽스 Z5fd | Fujifilm FinePix Z5fd | 1,561 | FinePix Z5fd | 2848×2136 | 후지 인기 모델 우선 |
| 니콘 쿨픽스 S3 | Nikon Coolpix S3 | 860 | COOLPIX S3 | 2816×2112 | 니콘 인기 모델 우선 |
| 니콘 쿨픽스 S200 | Nikon Coolpix S200 | 2,142 | COOLPIX S200 | 3072×2304 | 〃 |
| 올림푸스 μ-40 DIGITAL | Olympus u40D / S-500 / uD500 | 108 | u40D,S500,uD500 | 2560×1920 | 올림푸스 언급 적음 |
| 삼성 블루 NV7 OPS | Samsung NV7 | 107 | VLUU NV 7, NV 7 | 3072×2304 | 삼성 언급 적음 |
| 삼성 블루 L83T | Samsung L83T | 100 | VLUU L83T/ Samsung L83T | 3264×2448 | 〃 |
| 삼성 케녹스 V700 | Samsung Digimax V700 | 101 | <Digimax V700 / Kenox V10 / Digimax V10 > | 3072×2304 | 〃 |
| 카시오 EX-S500 | Casio Exilim EX-S500 | 101 | EX-S500 | 2560×1920 | 카시오 언급 적음 |
| 카시오 EX-Z3 | Casio Exilim EX-Z3 | 111 | EX-Z3 | 2048×1536 | 〃 |
| 루믹스 DMC-FX01 | Panasonic Lumix DMC-FX01 | 약 17,700 (FX07 합산) | DMC-FX01 (+ DMC-FX07) | 2816×2112 | 분류에 다른 카메라 섞임, 국내 수요 미확인 |
| 펜탁스 옵티오 WP | Pentax Optio WP | 913 | PENTAX Optio WP | 2560×1920 | 방수형, 감성 똑딱이 수요와 거리 |
| 후지필름 X100 | Fujifilm FinePix X100 | 1,899 | FinePix X100 | 4288×2848 | 고급 대형 센서 카메라라 대상 범위 밖 |

## 5. 요약

- **추천 30개**: 캐논 11, 코닥 2, 후지필름 4, 산요 2, 리코 1, 니콘 3, 소니 3, 삼성 2, 올림푸스 1, 카시오 1
- **Commons로 채울 수 있는 기종 26개**, **직접 수집이 필요한 기종 4개** (익서스 230 HS, 310 HS, 작티 J1, DMX-C40)
- 출시 전 목표는 **Commons로 채울 수 있는 기종 26개만 20장 이상**으로 정했다. 직접 수집 기종 4개는 모은 만큼 넣는다. (PRD 13장)

## 6. 할 일

- [x] 대상 범위 → "2000년대~2010년대 초 똑딱이 (CCD 위주, CMOS 포함)", X100 같은 대형 센서 고급 카메라 제외
- [x] 성공 기준 → Commons 기종 26개만 20장 이상
- [ ] 산요 작티 모델명 확인 (J1, DMX-C40이 국내에서 부르는 정확한 모델인지)
- [ ] 원래 해상도 "확인 필요" 기종을 제조사 자료로 확인
- [ ] 캐논 익서스 30·40의 일본 이름(IXY) 확인. 확인 안 되면 별칭에 넣지 않음
- [ ] 직접 수집 기종 4개의 작례 모을 방법 정하기 (지인, 디카 커뮤니티, 인스타 DM 요청 등)
- [x] 시드 데이터 (2026-11-17): Commons로 채울 수 있는 **26개**를 `V2__seed_cameras.sql`로 넣음
  - 원본: `backend/scripts/gen_seed_cameras.py` (데이터 수정은 이 파일에서 하고 SQL을 다시 생성)
  - EXIF 값은 Commons 표본 100장에서 실제로 나온 값만 넣음 (`poc/seed_exif_pairs.json`). 확인 안 된 값은 제외: IXUS 110 분류의 `CANON IXY DIGITAL 510 IS`, 제조사가 덮어써진 `OLYMPUS CORPORATION` + G11, 깨진 NV3 값
  - 출시연도는 비워 둠 (아직 조사 안 함)
- [ ] 나머지 4개(익서스 230 HS·310 HS, 작티 2개)는 모델명·EXIF 확인 후 다음 마이그레이션으로 추가. 산요 브랜드도 그때 추가
- [ ] 출시연도 조사 후 채우기
