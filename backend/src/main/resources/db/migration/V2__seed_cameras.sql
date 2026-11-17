-- 초기 기종 시드 데이터 (자동 생성 — 직접 고치지 말 것)
-- 생성기: backend/scripts/gen_seed_cameras.py · 기종 목록: docs/CAMERAS.md
-- 브랜드 9개, 기종 26개

INSERT INTO brand (name, name_ko, slug) VALUES
    ('Canon', '캐논', 'canon'),
    ('Kodak', '코닥', 'kodak'),
    ('Fujifilm', '후지필름', 'fujifilm'),
    ('Ricoh', '리코', 'ricoh'),
    ('Nikon', '니콘', 'nikon'),
    ('Sony', '소니', 'sony'),
    ('Samsung', '삼성', 'samsung'),
    ('Olympus', '올림푸스', 'olympus'),
    ('Casio', '카시오', 'casio');

-- IXUS 30
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'IXUS 30', '익서스 30', 'canon-ixus-30', 'CCD', 2048, 1536 FROM brand WHERE slug = 'canon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('IXUS 30', 'ixus30'), ('익서스 30', '익서스30'), ('PowerShot SD200', 'powershotsd200'), ('Digital IXUS 30', 'digitalixus30')) AS v(alias, normalized) WHERE c.slug = 'canon-ixus-30';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('canon', 'canon digital ixus 30'), ('canon', 'canon powershot sd200')) AS v(mk, md) WHERE c.slug = 'canon-ixus-30';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Canon Digital IXUS 30')) AS v(cat) WHERE c.slug = 'canon-ixus-30';

-- IXUS 40
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'IXUS 40', '익서스 40', 'canon-ixus-40', 'CCD', 2272, 1704 FROM brand WHERE slug = 'canon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('IXUS 40', 'ixus40'), ('익서스 40', '익서스40'), ('PowerShot SD300', 'powershotsd300'), ('Digital IXUS 40', 'digitalixus40')) AS v(alias, normalized) WHERE c.slug = 'canon-ixus-40';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('canon', 'canon digital ixus 40'), ('canon', 'canon powershot sd300')) AS v(mk, md) WHERE c.slug = 'canon-ixus-40';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Canon Digital IXUS 40')) AS v(cat) WHERE c.slug = 'canon-ixus-40';

-- IXUS 55
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'IXUS 55', '익서스 55', 'canon-ixus-55', 'CCD', 2592, 1944 FROM brand WHERE slug = 'canon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('IXUS 55', 'ixus55'), ('익서스 55', '익서스55'), ('PowerShot SD450', 'powershotsd450'), ('IXY DIGITAL 60', 'ixydigital60'), ('Digital IXUS 55', 'digitalixus55')) AS v(alias, normalized) WHERE c.slug = 'canon-ixus-55';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('canon', 'canon digital ixus 55'), ('canon', 'canon powershot sd450'), ('canon', 'canon ixy digital 60')) AS v(mk, md) WHERE c.slug = 'canon-ixus-55';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Canon Digital IXUS 55')) AS v(cat) WHERE c.slug = 'canon-ixus-55';

-- IXUS 60
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'IXUS 60', '익서스 60', 'canon-ixus-60', 'CCD', 2816, 2112 FROM brand WHERE slug = 'canon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('IXUS 60', 'ixus60'), ('익서스 60', '익서스60'), ('PowerShot SD600', 'powershotsd600'), ('IXY DIGITAL 70', 'ixydigital70'), ('Digital IXUS 60', 'digitalixus60')) AS v(alias, normalized) WHERE c.slug = 'canon-ixus-60';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('canon', 'canon digital ixus 60'), ('canon', 'canon powershot sd600'), ('canon', 'canon ixy digital 70')) AS v(mk, md) WHERE c.slug = 'canon-ixus-60';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Canon Digital IXUS 60')) AS v(cat) WHERE c.slug = 'canon-ixus-60';

-- IXUS 70
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'IXUS 70', '익서스 70', 'canon-ixus-70', 'CCD', 3072, 2304 FROM brand WHERE slug = 'canon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('IXUS 70', 'ixus70'), ('익서스 70', '익서스70'), ('PowerShot SD1000', 'powershotsd1000'), ('IXY DIGITAL 10', 'ixydigital10'), ('Digital IXUS 70', 'digitalixus70')) AS v(alias, normalized) WHERE c.slug = 'canon-ixus-70';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('canon', 'canon digital ixus 70'), ('canon', 'canon powershot sd1000'), ('canon', 'canon ixy digital 10')) AS v(mk, md) WHERE c.slug = 'canon-ixus-70';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Canon Digital IXUS 70')) AS v(cat) WHERE c.slug = 'canon-ixus-70';

-- IXUS 75
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'IXUS 75', '익서스 75', 'canon-ixus-75', 'CCD', 3072, 2304 FROM brand WHERE slug = 'canon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('IXUS 75', 'ixus75'), ('익서스 75', '익서스75'), ('PowerShot SD750', 'powershotsd750'), ('IXY DIGITAL 90', 'ixydigital90'), ('Digital IXUS 75', 'digitalixus75')) AS v(alias, normalized) WHERE c.slug = 'canon-ixus-75';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('canon', 'canon digital ixus 75'), ('canon', 'canon powershot sd750'), ('canon', 'canon ixy digital 90')) AS v(mk, md) WHERE c.slug = 'canon-ixus-75';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Canon Digital IXUS 75')) AS v(cat) WHERE c.slug = 'canon-ixus-75';

-- IXUS 110 IS
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'IXUS 110 IS', '익서스 110 IS', 'canon-ixus-110-is', 'CCD', 4000, 3000 FROM brand WHERE slug = 'canon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('IXUS 110 IS', 'ixus110is'), ('익서스 110 IS', '익서스110is'), ('PowerShot SD960 IS', 'powershotsd960is'), ('Digital IXUS 110 IS', 'digitalixus110is'), ('익서스 110', '익서스110')) AS v(alias, normalized) WHERE c.slug = 'canon-ixus-110-is';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('canon', 'canon digital ixus 110 is'), ('canon', 'canon powershot sd960 is')) AS v(mk, md) WHERE c.slug = 'canon-ixus-110-is';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Canon Digital IXUS 110 IS')) AS v(cat) WHERE c.slug = 'canon-ixus-110-is';

-- PowerShot G9
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'PowerShot G9', '파워샷 G9', 'canon-powershot-g9', 'CCD', 4000, 3000 FROM brand WHERE slug = 'canon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('PowerShot G9', 'powershotg9'), ('파워샷 G9', '파워샷g9')) AS v(alias, normalized) WHERE c.slug = 'canon-powershot-g9';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('canon', 'canon powershot g9')) AS v(mk, md) WHERE c.slug = 'canon-powershot-g9';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Canon PowerShot G9')) AS v(cat) WHERE c.slug = 'canon-powershot-g9';

-- PowerShot G11
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'PowerShot G11', '파워샷 G11', 'canon-powershot-g11', 'CCD', 3648, 2736 FROM brand WHERE slug = 'canon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('PowerShot G11', 'powershotg11'), ('파워샷 G11', '파워샷g11')) AS v(alias, normalized) WHERE c.slug = 'canon-powershot-g11';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('canon', 'canon powershot g11')) AS v(mk, md) WHERE c.slug = 'canon-powershot-g11';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Canon PowerShot G11')) AS v(cat) WHERE c.slug = 'canon-powershot-g11';

-- EasyShare C713
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'EasyShare C713', '이지쉐어 C713', 'kodak-easyshare-c713', 'CCD', 3072, 2304 FROM brand WHERE slug = 'kodak';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('EasyShare C713', 'easysharec713'), ('이지쉐어 C713', '이지쉐어c713'), ('C713', 'c713'), ('코닥 C713', '코닥c713')) AS v(alias, normalized) WHERE c.slug = 'kodak-easyshare-c713';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('eastman kodak company', 'kodak easyshare c713 zoom digital camera')) AS v(mk, md) WHERE c.slug = 'kodak-easyshare-c713';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Kodak EasyShare C713')) AS v(cat) WHERE c.slug = 'kodak-easyshare-c713';

-- EasyShare V570
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'EasyShare V570', '이지쉐어 V570', 'kodak-easyshare-v570', 'CCD', 2576, 1932 FROM brand WHERE slug = 'kodak';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('EasyShare V570', 'easysharev570'), ('이지쉐어 V570', '이지쉐어v570'), ('V570', 'v570'), ('코닥 V570', '코닥v570')) AS v(alias, normalized) WHERE c.slug = 'kodak-easyshare-v570';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('eastman kodak company', 'kodak v570 dual lens digital camera')) AS v(mk, md) WHERE c.slug = 'kodak-easyshare-v570';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Kodak EasyShare V570')) AS v(cat) WHERE c.slug = 'kodak-easyshare-v570';

-- FinePix F470
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'FinePix F470', '파인픽스 F470', 'fujifilm-finepix-f470', 'CCD', 2816, 2112 FROM brand WHERE slug = 'fujifilm';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('FinePix F470', 'finepixf470'), ('파인픽스 F470', '파인픽스f470'), ('F470', 'f470')) AS v(alias, normalized) WHERE c.slug = 'fujifilm-finepix-f470';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('fujifilm', 'finepix f470')) AS v(mk, md) WHERE c.slug = 'fujifilm-finepix-f470';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Fujifilm FinePix F470')) AS v(cat) WHERE c.slug = 'fujifilm-finepix-f470';

-- FinePix F31fd
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'FinePix F31fd', '파인픽스 F31fd', 'fujifilm-finepix-f31fd', 'CCD', 2848, 2136 FROM brand WHERE slug = 'fujifilm';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('FinePix F31fd', 'finepixf31fd'), ('파인픽스 F31fd', '파인픽스f31fd'), ('F31fd', 'f31fd'), ('파인픽스 F31', '파인픽스f31')) AS v(alias, normalized) WHERE c.slug = 'fujifilm-finepix-f31fd';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('fujifilm', 'finepix f31fd')) AS v(mk, md) WHERE c.slug = 'fujifilm-finepix-f31fd';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Fujifilm FinePix F31fd')) AS v(cat) WHERE c.slug = 'fujifilm-finepix-f31fd';

-- FinePix F10
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'FinePix F10', '파인픽스 F10', 'fujifilm-finepix-f10', 'CCD', 2848, 2136 FROM brand WHERE slug = 'fujifilm';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('FinePix F10', 'finepixf10'), ('파인픽스 F10', '파인픽스f10'), ('F10', 'f10')) AS v(alias, normalized) WHERE c.slug = 'fujifilm-finepix-f10';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('fujifilm', 'finepix f10')) AS v(mk, md) WHERE c.slug = 'fujifilm-finepix-f10';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Fujifilm FinePix F10')) AS v(cat) WHERE c.slug = 'fujifilm-finepix-f10';

-- FinePix Z1
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'FinePix Z1', '파인픽스 Z1', 'fujifilm-finepix-z1', 'CCD', 2592, 1944 FROM brand WHERE slug = 'fujifilm';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('FinePix Z1', 'finepixz1'), ('파인픽스 Z1', '파인픽스z1'), ('Z1', 'z1')) AS v(alias, normalized) WHERE c.slug = 'fujifilm-finepix-z1';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('fujifilm', 'finepix z1')) AS v(mk, md) WHERE c.slug = 'fujifilm-finepix-z1';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Fujifilm FinePix Z1')) AS v(cat) WHERE c.slug = 'fujifilm-finepix-z1';

-- R10
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'R10', NULL, 'ricoh-r10', 'CCD', 3648, 2736 FROM brand WHERE slug = 'ricoh';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('R10', 'r10'), ('Ricoh R10', 'ricohr10'), ('리코 R10', '리코r10')) AS v(alias, normalized) WHERE c.slug = 'ricoh-r10';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('ricoh', 'ricoh r10')) AS v(mk, md) WHERE c.slug = 'ricoh-r10';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Ricoh R10')) AS v(cat) WHERE c.slug = 'ricoh-r10';

-- COOLPIX S4100
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'COOLPIX S4100', '쿨픽스 S4100', 'nikon-coolpix-s4100', 'CCD', 4320, 3240 FROM brand WHERE slug = 'nikon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('COOLPIX S4100', 'coolpixs4100'), ('쿨픽스 S4100', '쿨픽스s4100'), ('S4100', 's4100')) AS v(alias, normalized) WHERE c.slug = 'nikon-coolpix-s4100';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('nikon', 'coolpix s4100')) AS v(mk, md) WHERE c.slug = 'nikon-coolpix-s4100';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Nikon Coolpix S4100')) AS v(cat) WHERE c.slug = 'nikon-coolpix-s4100';

-- COOLPIX S1
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'COOLPIX S1', '쿨픽스 S1', 'nikon-coolpix-s1', 'CCD', 2592, 1944 FROM brand WHERE slug = 'nikon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('COOLPIX S1', 'coolpixs1'), ('쿨픽스 S1', '쿨픽스s1')) AS v(alias, normalized) WHERE c.slug = 'nikon-coolpix-s1';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('nikon', 's1')) AS v(mk, md) WHERE c.slug = 'nikon-coolpix-s1';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Nikon Coolpix S1')) AS v(cat) WHERE c.slug = 'nikon-coolpix-s1';

-- COOLPIX 3200
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'COOLPIX 3200', '쿨픽스 3200', 'nikon-coolpix-3200', 'CCD', 2048, 1536 FROM brand WHERE slug = 'nikon';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('COOLPIX 3200', 'coolpix3200'), ('쿨픽스 3200', '쿨픽스3200'), ('E3200', 'e3200')) AS v(alias, normalized) WHERE c.slug = 'nikon-coolpix-3200';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('nikon', 'e3200'), ('nikon corporation', 'e3200')) AS v(mk, md) WHERE c.slug = 'nikon-coolpix-3200';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Nikon Coolpix 3200')) AS v(cat) WHERE c.slug = 'nikon-coolpix-3200';

-- Cyber-shot DSC-T1
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'Cyber-shot DSC-T1', '사이버샷 T1', 'sony-dsc-t1', 'CCD', 2592, 1944 FROM brand WHERE slug = 'sony';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('Cyber-shot DSC-T1', 'cybershotdsct1'), ('사이버샷 T1', '사이버샷t1'), ('DSC-T1', 'dsct1')) AS v(alias, normalized) WHERE c.slug = 'sony-dsc-t1';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('sony', 'dsc-t1')) AS v(mk, md) WHERE c.slug = 'sony-dsc-t1';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Sony DSC-T1')) AS v(cat) WHERE c.slug = 'sony-dsc-t1';

-- Cyber-shot DSC-T10
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'Cyber-shot DSC-T10', '사이버샷 T10', 'sony-dsc-t10', 'CCD', 3072, 2304 FROM brand WHERE slug = 'sony';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('Cyber-shot DSC-T10', 'cybershotdsct10'), ('사이버샷 T10', '사이버샷t10'), ('DSC-T10', 'dsct10')) AS v(alias, normalized) WHERE c.slug = 'sony-dsc-t10';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('sony', 'dsc-t10')) AS v(mk, md) WHERE c.slug = 'sony-dsc-t10';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Sony DSC-T10')) AS v(cat) WHERE c.slug = 'sony-dsc-t10';

-- Cyber-shot DSC-W55
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'Cyber-shot DSC-W55', '사이버샷 W55', 'sony-dsc-w55', 'CCD', 3072, 2304 FROM brand WHERE slug = 'sony';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('Cyber-shot DSC-W55', 'cybershotdscw55'), ('사이버샷 W55', '사이버샷w55'), ('DSC-W55', 'dscw55')) AS v(alias, normalized) WHERE c.slug = 'sony-dsc-w55';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('sony', 'dsc-w55')) AS v(mk, md) WHERE c.slug = 'sony-dsc-w55';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Sony DSC-W55')) AS v(cat) WHERE c.slug = 'sony-dsc-w55';

-- VLUU NV3
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'VLUU NV3', '블루 NV3', 'samsung-vluu-nv3', 'CCD', 3072, 2304 FROM brand WHERE slug = 'samsung';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('VLUU NV3', 'vluunv3'), ('블루 NV3', '블루nv3'), ('NV3', 'nv3'), ('Samsung NV3', 'samsungnv3')) AS v(alias, normalized) WHERE c.slug = 'samsung-vluu-nv3';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('samsung techwin', '<samsung nv3, samsung vluu nv3>')) AS v(mk, md) WHERE c.slug = 'samsung-vluu-nv3';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Samsung NV3')) AS v(cat) WHERE c.slug = 'samsung-vluu-nv3';

-- KENOX S630
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'KENOX S630', '케녹스 S630', 'samsung-kenox-s630', 'CCD', 2816, 2112 FROM brand WHERE slug = 'samsung';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('KENOX S630', 'kenoxs630'), ('케녹스 S630', '케녹스s630'), ('S630', 's630'), ('Samsung S630', 'samsungs630')) AS v(alias, normalized) WHERE c.slug = 'samsung-kenox-s630';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('samsung techwin', '<kenox s630 / samsung s630>')) AS v(mk, md) WHERE c.slug = 'samsung-kenox-s630';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Samsung S630')) AS v(cat) WHERE c.slug = 'samsung-kenox-s630';

-- μ 700
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'μ 700', '뮤 700', 'olympus-mju-700', 'CCD', 3072, 2304 FROM brand WHERE slug = 'olympus';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('μ 700', 'μ700'), ('뮤 700', '뮤700'), ('mju 700', 'mju700'), ('Stylus 700', 'stylus700'), ('u700', 'u700')) AS v(alias, normalized) WHERE c.slug = 'olympus-mju-700';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('olympus imaging corp.', 'u700,s700')) AS v(mk, md) WHERE c.slug = 'olympus-mju-700';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Olympus u700 / S-700')) AS v(cat) WHERE c.slug = 'olympus-mju-700';

-- EXILIM EX-Z750
INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)
SELECT id, 'EXILIM EX-Z750', '엑슬림 EX-Z750', 'casio-exilim-ex-z750', 'CCD', 3072, 2304 FROM brand WHERE slug = 'casio';
INSERT INTO camera_alias (camera_id, alias, normalized)
SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES ('EXILIM EX-Z750', 'exilimexz750'), ('엑슬림 EX-Z750', '엑슬림exz750'), ('EX-Z750', 'exz750'), ('엑슬림 Z750', '엑슬림z750')) AS v(alias, normalized) WHERE c.slug = 'casio-exilim-ex-z750';
INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)
SELECT c.id, v.mk, v.md FROM camera c, (VALUES ('casio computer co.,ltd', 'ex-z750')) AS v(mk, md) WHERE c.slug = 'casio-exilim-ex-z750';
INSERT INTO camera_source_category (camera_id, category_name)
SELECT c.id, v.cat FROM camera c, (VALUES ('Casio Exilim EX-Z750')) AS v(cat) WHERE c.slug = 'casio-exilim-ex-z750';

