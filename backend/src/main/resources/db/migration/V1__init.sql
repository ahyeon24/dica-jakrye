-- 초기 스키마. 설계 문서: docs/ERD.md
-- 규칙: PK는 bigint identity, enum은 varchar + CHECK, 생성·수정 시각은 timestamptz, 삭제는 deleted_at(소프트 삭제)

-- 3.1 브랜드
CREATE TABLE brand (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        varchar(50)  NOT NULL UNIQUE,
    name_ko     varchar(50),
    slug        varchar(60)  NOT NULL UNIQUE,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

-- 3.2 기종
CREATE TABLE camera (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    brand_id              bigint       NOT NULL REFERENCES brand (id),
    name                  varchar(100) NOT NULL,
    name_ko               varchar(100),          -- 화면 표시용 한글 이름 (예: 익서스 30)
    slug                  varchar(120) NOT NULL UNIQUE,
    release_year          smallint,
    sensor_type           varchar(10)  CHECK (sensor_type IN ('CCD', 'CMOS')),
    native_width          integer      CHECK (native_width > 0),
    native_height         integer      CHECK (native_height > 0),
    cover_photo_id        bigint,      -- FK는 photo 테이블을 만든 뒤 추가
    commons_collected_at  timestamptz,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (brand_id, name)
);

-- 3.3 검색 별칭
CREATE TABLE camera_alias (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    camera_id   bigint       NOT NULL REFERENCES camera (id) ON DELETE CASCADE,
    alias       varchar(150) NOT NULL,
    normalized  varchar(150) NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (camera_id, normalized)
);
CREATE INDEX idx_camera_alias_normalized ON camera_alias (normalized);

-- 3.4 EXIF 매칭: EXIF (제조사, 모델) 하나는 정확히 한 기종에만 연결
CREATE TABLE camera_exif_model (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    camera_id   bigint       NOT NULL REFERENCES camera (id) ON DELETE CASCADE,
    exif_make   varchar(100) NOT NULL,
    exif_model  varchar(100) NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (exif_make, exif_model)
);

-- 3.4.1 Commons 분류 연결
CREATE TABLE camera_source_category (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    camera_id      bigint       NOT NULL REFERENCES camera (id) ON DELETE CASCADE,
    category_name  varchar(200) NOT NULL UNIQUE,
    created_at     timestamptz  NOT NULL DEFAULT now()
);

-- 3.5 사용자 (이메일은 저장하지 않는다)
CREATE TABLE users (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider           varchar(10)  NOT NULL CHECK (provider IN ('GOOGLE', 'KAKAO')),
    provider_user_id   varchar(100),          -- 탈퇴하면 null
    nickname           varchar(30)  NOT NULL UNIQUE,
    profile_image_key  varchar(300),
    role               varchar(10)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    deleted_at         timestamptz,
    UNIQUE (provider, provider_user_id)
);

-- 3.10 약관 동의 기록 (추가만 하고 수정·삭제하지 않는다)
CREATE TABLE terms_agreement (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     bigint       NOT NULL REFERENCES users (id),
    terms_type  varchar(30)  NOT NULL CHECK (terms_type IN ('TERMS_OF_SERVICE', 'PRIVACY_COLLECTION')),
    version     varchar(20)  NOT NULL,
    agreed_at   timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (user_id, terms_type, version)
);
CREATE INDEX idx_terms_agreement_latest ON terms_agreement (user_id, terms_type, agreed_at DESC);

-- 3.6 작례 (사용자 업로드 + Commons)
CREATE TABLE photo (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source            varchar(10)   NOT NULL CHECK (source IN ('UPLOAD', 'COMMONS')),
    camera_id         bigint        REFERENCES camera (id),   -- null이면 미분류
    uploader_id       bigint        REFERENCES users (id),    -- Commons면 null
    exif_make_raw     varchar(100),
    exif_model_raw    varchar(100),
    exif_make         varchar(100),
    exif_model        varchar(100),
    flash_fired       boolean,                                -- null = 알 수 없음
    taken_at          timestamp,                              -- EXIF 촬영 시각 (시간대 없음)
    f_number          numeric(4, 1),
    exposure_time     varchar(20),
    iso               integer,
    focal_length      numeric(5, 1),
    width             integer,
    height            integer,
    is_original_size  boolean,                                -- null = 판별 불가
    original_key      varchar(300),                           -- 비공개 버킷, 업로드만
    display_key       varchar(300)  NOT NULL,
    thumbnail_key     varchar(300)  NOT NULL,
    view_count        integer       NOT NULL DEFAULT 0,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),
    deleted_at        timestamptz,
    CONSTRAINT chk_photo_upload  CHECK (source <> 'UPLOAD'  OR (uploader_id IS NOT NULL AND original_key IS NOT NULL)),
    CONSTRAINT chk_photo_commons CHECK (source <> 'COMMONS' OR (camera_id IS NOT NULL AND uploader_id IS NULL AND original_key IS NULL))
);

-- 목록 조회용 (삭제되지 않은 작례만)
CREATE INDEX idx_photo_camera        ON photo (camera_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_photo_camera_flash  ON photo (camera_id, flash_fired, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_photo_camera_orig   ON photo (camera_id, is_original_size, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_photo_uploader      ON photo (uploader_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_photo_recent_upload ON photo (created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND source = 'UPLOAD' AND camera_id IS NOT NULL;
-- 미분류 연결용
CREATE INDEX idx_photo_unclassified  ON photo (exif_make, exif_model) WHERE camera_id IS NULL AND deleted_at IS NULL;
-- 업로드 횟수 제한용 (삭제한 작례도 센다)
CREATE INDEX idx_photo_upload_rate   ON photo (uploader_id, created_at);

ALTER TABLE camera
    ADD CONSTRAINT fk_camera_cover_photo FOREIGN KEY (cover_photo_id) REFERENCES photo (id);

-- 3.7 Commons 작례 정보 (photo와 1:1)
CREATE TABLE commons_meta (
    photo_id         bigint       PRIMARY KEY REFERENCES photo (id),
    commons_page_id  bigint       NOT NULL UNIQUE,
    file_title       varchar(300) NOT NULL,
    page_url         varchar(500) NOT NULL,
    author           varchar(300) NOT NULL,   -- HTML을 지운 글자만
    license_name     varchar(50)  NOT NULL,
    license_url      varchar(300),
    source_category  varchar(200) NOT NULL,
    fetched_at       timestamptz  NOT NULL DEFAULT now(),
    last_checked_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_commons_meta_checked ON commons_meta (last_checked_at);

-- 3.9 신고
CREATE TABLE report (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    photo_id     bigint       NOT NULL REFERENCES photo (id),
    reporter_id  bigint       NOT NULL REFERENCES users (id),
    reason       varchar(20)  NOT NULL CHECK (reason IN ('INAPPROPRIATE', 'STOLEN', 'PORTRAIT_RIGHTS', 'WRONG_CAMERA', 'OTHER')),
    detail       varchar(500),
    status       varchar(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RESOLVED', 'REJECTED')),
    action       varchar(20)  CHECK (action IN ('DELETED', 'CAMERA_CHANGED')),
    handled_by   bigint       REFERENCES users (id),
    handled_at   timestamptz,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (photo_id, reporter_id)
);
CREATE INDEX idx_report_status ON report (status, created_at);
