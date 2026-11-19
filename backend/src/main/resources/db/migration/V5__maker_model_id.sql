-- 제조사 내부 기종 번호로 기종 찾기 (ERD 3.4-1, docs/CAMERAS.md 6장)
-- 캐논은 나라마다 이름이 달라도(IXUS·IXY·PowerShot SD) MakerNote의 Canon Model ID가 같다.
-- EXIF 모델명이 등록되지 않은 이름이어도 이 번호가 같으면 그 기종으로 연결한다.
-- 2026-11-19 Commons 표본으로 확인한 값만 넣는다. 다른 제조사는 쓸 만한 번호가 없어 캐논만.

CREATE TABLE camera_maker_model (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    camera_id      bigint      NOT NULL REFERENCES camera (id) ON DELETE CASCADE,
    exif_make      varchar(100) NOT NULL,  -- 정규화한 EXIF 제조사 (ERD 4장). 지금은 'canon'만
    maker_model_id bigint      NOT NULL,   -- 캐논: MakerNote 0x0010 (Canon Model ID)
    created_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (exif_make, maker_model_id)
);
CREATE INDEX idx_camera_maker_model_camera ON camera_maker_model (camera_id);

-- 업로드 사진에서 읽은 내부 번호. 미분류 작례를 연결할 때 이 번호도 함께 등록한다
ALTER TABLE photo ADD COLUMN maker_model_id bigint;

-- 기종별 Canon Model ID (괄호는 같은 번호로 확인된 EXIF 모델명)
INSERT INTO camera_maker_model (camera_id, exif_make, maker_model_id)
SELECT c.id, 'canon', v.mid
  FROM camera c
  JOIN (VALUES
        ('canon-ixus-30',     0x01550000),  -- DIGITAL IXUS 30, PowerShot SD200, IXY DIGITAL 40
        ('canon-ixus-40',     0x01540000),  -- DIGITAL IXUS 40, PowerShot SD300, IXY DIGITAL 50
        ('canon-ixus-55',     0x01810000),  -- DIGITAL IXUS 55, PowerShot SD450, IXY DIGITAL 60
        ('canon-ixus-60',     0x01970000),  -- DIGITAL IXUS 60, PowerShot SD600, IXY DIGITAL 70
        ('canon-ixus-70',     0x02160000),  -- DIGITAL IXUS 70, PowerShot SD1000, IXY DIGITAL 10
        ('canon-ixus-75',     0x02150000),  -- DIGITAL IXUS 75, PowerShot SD750, IXY DIGITAL 90
        ('canon-ixus-110-is', 0x02570000),  -- DIGITAL IXUS 110 IS, PowerShot SD960 IS, IXY DIGITAL 510 IS
        ('canon-powershot-g9',  0x02230000),
        ('canon-powershot-g11', 0x02700000)
       ) AS v (slug, mid) ON v.slug = c.slug;

-- 내부 번호로 확인된 일본판 이름. 전용 영역이 지워진 사진(편집 프로그램)도 이름으로 연결되게 한다.
-- 주의: IXY DIGITAL 110 IS는 IXUS 110 IS와 다른 기종이다(번호 0x02640000). 이름만 보고 짝짓지 않는다.
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, 'canon', v.model
  FROM camera c
  JOIN (VALUES
        ('canon-ixus-30',     'canon ixy digital 40'),
        ('canon-ixus-40',     'canon ixy digital 50'),
        ('canon-ixus-110-is', 'canon ixy digital 510 is')
       ) AS v (slug, model) ON v.slug = c.slug;

INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized
  FROM camera c
  JOIN (VALUES
        ('canon-ixus-30',     'IXY DIGITAL 40',     'ixydigital40'),
        ('canon-ixus-40',     'IXY DIGITAL 50',     'ixydigital50'),
        ('canon-ixus-110-is', 'IXY DIGITAL 510 IS', 'ixydigital510is')
       ) AS v (slug, alias, normalized) ON v.slug = c.slug;
