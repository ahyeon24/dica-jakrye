"""초기 기종 시드 SQL 생성기.

이 파일이 초기 기종 데이터의 원본이다. 수정 후 다시 실행해서 마이그레이션을 만든다.
  python3 -I backend/scripts/gen_seed_cameras.py > backend/src/main/resources/db/migration/V2__seed_cameras.sql

- 데이터 출처: docs/CAMERAS.md (2차 확정), EXIF 값은 Commons 표본 (poc/seed_exif_pairs.json, 2026-11-17)
- 정규화 규칙은 docs/ERD.md 4장과 똑같아야 한다. 나중에 만들 Java 정규화 코드도 같은 결과를 내야 하므로,
  아래 NORMALIZE_EXAMPLES를 Java 테스트에도 그대로 쓴다.
- 이미 운영에 배포된 뒤에는 V2를 고치지 않는다. 바꿀 내용은 새 마이그레이션(V3…)으로 추가한다.
"""
import re
import sys


# ---------- 정규화 (ERD.md 4장) ----------

def normalize_exif(s: str) -> str:
    """EXIF 제조사·모델: NUL 제거 → 앞뒤 공백 제거·연속 공백 하나로 → 소문자"""
    s = s.replace("\x00", "")
    s = re.sub(r"\s+", " ", s).strip()
    return s.lower()


def normalize_alias(s: str) -> str:
    """검색 별칭: EXIF 규칙 + 공백·하이픈 모두 제거"""
    return re.sub(r"[\s\-]", "", normalize_exif(s))


NORMALIZE_EXAMPLES = [  # (함수, 입력, 기대값) — Java 테스트에도 같은 예시를 쓴다
    ("exif", "Canon DIGITAL IXUS 30\x00\x00", "canon digital ixus 30"),
    ("exif", "  <KENOX S630  / Samsung S630> ", "<kenox s630 / samsung s630>"),
    ("exif", "OLYMPUS IMAGING CORP.", "olympus imaging corp."),
    ("alias", "PowerShot SD200", "powershotsd200"),
    ("alias", "Cyber-shot DSC-T1", "cybershotdsct1"),
    ("alias", "익서스 30", "익서스30"),
]
for kind, raw, want in NORMALIZE_EXAMPLES:
    got = (normalize_exif if kind == "exif" else normalize_alias)(raw)
    assert got == want, (kind, raw, got, want)


# ---------- 데이터 ----------

BRANDS = [  # (slug, name, name_ko)
    ("canon", "Canon", "캐논"),
    ("kodak", "Kodak", "코닥"),
    ("fujifilm", "Fujifilm", "후지필름"),
    ("ricoh", "Ricoh", "리코"),
    ("nikon", "Nikon", "니콘"),
    ("sony", "Sony", "소니"),
    ("samsung", "Samsung", "삼성"),
    ("olympus", "Olympus", "올림푸스"),
    ("casio", "Casio", "카시오"),
]

# 기종: brand, slug, name, name_ko, sensor, (native_w, native_h), 추가 별칭, EXIF (make, model), Commons 분류
# - 대표 이름·한글 이름은 자동으로 별칭에 들어간다.
# - EXIF는 Commons 표본에서 실제로 나온 값만. 확인 안 된 값(예: 다른 카메라일 수 있는 IXY 이름)은 넣지 않는다.
#   (2026-11-19 캐논 내부 번호로 확인한 IXY 40·50·510 IS와 기종별 번호는 V5__maker_model_id.sql에 있다. V2는 그대로 둔다)
# - 출시연도는 아직 조사하지 않아 비워 둔다.
CAMERAS = [
    # 캐논
    dict(brand="canon", slug="canon-ixus-30", name="IXUS 30", ko="익서스 30", sensor="CCD", native=(2048, 1536),
         aliases=["PowerShot SD200", "Digital IXUS 30"],
         exif=[("Canon", "Canon DIGITAL IXUS 30"), ("Canon", "Canon PowerShot SD200")],
         categories=["Canon Digital IXUS 30"]),
    dict(brand="canon", slug="canon-ixus-40", name="IXUS 40", ko="익서스 40", sensor="CCD", native=(2272, 1704),
         aliases=["PowerShot SD300", "Digital IXUS 40"],
         exif=[("Canon", "Canon DIGITAL IXUS 40"), ("Canon", "Canon PowerShot SD300")],
         categories=["Canon Digital IXUS 40"]),
    dict(brand="canon", slug="canon-ixus-55", name="IXUS 55", ko="익서스 55", sensor="CCD", native=(2592, 1944),
         aliases=["PowerShot SD450", "IXY DIGITAL 60", "Digital IXUS 55"],
         exif=[("Canon", "Canon DIGITAL IXUS 55"), ("Canon", "Canon PowerShot SD450"), ("Canon", "Canon IXY DIGITAL 60")],
         categories=["Canon Digital IXUS 55"]),
    dict(brand="canon", slug="canon-ixus-60", name="IXUS 60", ko="익서스 60", sensor="CCD", native=(2816, 2112),
         aliases=["PowerShot SD600", "IXY DIGITAL 70", "Digital IXUS 60"],
         exif=[("Canon", "Canon DIGITAL IXUS 60"), ("Canon", "Canon PowerShot SD600"), ("Canon", "Canon IXY DIGITAL 70")],
         categories=["Canon Digital IXUS 60"]),
    dict(brand="canon", slug="canon-ixus-70", name="IXUS 70", ko="익서스 70", sensor="CCD", native=(3072, 2304),
         aliases=["PowerShot SD1000", "IXY DIGITAL 10", "Digital IXUS 70"],
         exif=[("Canon", "Canon DIGITAL IXUS 70"), ("Canon", "Canon PowerShot SD1000"), ("Canon", "Canon IXY DIGITAL 10")],
         categories=["Canon Digital IXUS 70"]),
    dict(brand="canon", slug="canon-ixus-75", name="IXUS 75", ko="익서스 75", sensor="CCD", native=(3072, 2304),
         aliases=["PowerShot SD750", "IXY DIGITAL 90", "Digital IXUS 75"],
         exif=[("Canon", "Canon DIGITAL IXUS 75"), ("Canon", "Canon PowerShot SD750"), ("Canon", "Canon IXY DIGITAL 90")],
         categories=["Canon Digital IXUS 75"]),
    dict(brand="canon", slug="canon-ixus-110-is", name="IXUS 110 IS", ko="익서스 110 IS", sensor="CCD", native=(4000, 3000),
         aliases=["PowerShot SD960 IS", "Digital IXUS 110 IS", "익서스 110"],
         exif=[("Canon", "Canon DIGITAL IXUS 110 IS"), ("Canon", "Canon PowerShot SD960 IS")],
         categories=["Canon Digital IXUS 110 IS"]),
    dict(brand="canon", slug="canon-powershot-g9", name="PowerShot G9", ko="파워샷 G9", sensor="CCD", native=(4000, 3000),
         aliases=[], exif=[("Canon", "Canon PowerShot G9")], categories=["Canon PowerShot G9"]),
    dict(brand="canon", slug="canon-powershot-g11", name="PowerShot G11", ko="파워샷 G11", sensor="CCD", native=(3648, 2736),
         aliases=[], exif=[("Canon", "Canon PowerShot G11")], categories=["Canon PowerShot G11"]),
    # 코닥
    dict(brand="kodak", slug="kodak-easyshare-c713", name="EasyShare C713", ko="이지쉐어 C713", sensor="CCD",
         native=(3072, 2304),  # 확인 필요: Commons 표본 최대는 3056×2292
         aliases=["C713", "코닥 C713"],
         exif=[("EASTMAN KODAK COMPANY", "KODAK EASYSHARE C713 ZOOM DIGITAL CAMERA")],
         categories=["Kodak EasyShare C713"]),
    dict(brand="kodak", slug="kodak-easyshare-v570", name="EasyShare V570", ko="이지쉐어 V570", sensor="CCD", native=(2576, 1932),
         aliases=["V570", "코닥 V570"],
         exif=[("EASTMAN KODAK COMPANY", "KODAK V570 DUAL LENS DIGITAL CAMERA")],
         categories=["Kodak EasyShare V570"]),
    # 후지필름
    dict(brand="fujifilm", slug="fujifilm-finepix-f470", name="FinePix F470", ko="파인픽스 F470", sensor="CCD", native=(2816, 2112),
         aliases=["F470"], exif=[("FUJIFILM", "FinePix F470")], categories=["Fujifilm FinePix F470"]),
    dict(brand="fujifilm", slug="fujifilm-finepix-f31fd", name="FinePix F31fd", ko="파인픽스 F31fd", sensor="CCD", native=(2848, 2136),
         aliases=["F31fd", "파인픽스 F31"], exif=[("FUJIFILM", "FinePix F31fd")], categories=["Fujifilm FinePix F31fd"]),
    dict(brand="fujifilm", slug="fujifilm-finepix-f10", name="FinePix F10", ko="파인픽스 F10", sensor="CCD", native=(2848, 2136),
         aliases=["F10"], exif=[("FUJIFILM", "FinePix F10")], categories=["Fujifilm FinePix F10"]),
    dict(brand="fujifilm", slug="fujifilm-finepix-z1", name="FinePix Z1", ko="파인픽스 Z1", sensor="CCD", native=(2592, 1944),
         aliases=["Z1"], exif=[("FUJIFILM", "FinePix Z1")], categories=["Fujifilm FinePix Z1"]),
    # 리코
    dict(brand="ricoh", slug="ricoh-r10", name="R10", ko=None, sensor="CCD", native=(3648, 2736),
         aliases=["Ricoh R10", "리코 R10"], exif=[("RICOH", "RICOH R10")], categories=["Ricoh R10"]),
    # 니콘
    dict(brand="nikon", slug="nikon-coolpix-s4100", name="COOLPIX S4100", ko="쿨픽스 S4100", sensor="CCD", native=(4320, 3240),
         aliases=["S4100"], exif=[("NIKON", "COOLPIX S4100")], categories=["Nikon Coolpix S4100"]),
    dict(brand="nikon", slug="nikon-coolpix-s1", name="COOLPIX S1", ko="쿨픽스 S1", sensor="CCD", native=(2592, 1944),
         aliases=[], exif=[("NIKON", "S1")], categories=["Nikon Coolpix S1"]),
    dict(brand="nikon", slug="nikon-coolpix-3200", name="COOLPIX 3200", ko="쿨픽스 3200", sensor="CCD", native=(2048, 1536),
         aliases=["E3200"], exif=[("NIKON", "E3200"), ("NIKON CORPORATION", "E3200")], categories=["Nikon Coolpix 3200"]),
    # 소니
    dict(brand="sony", slug="sony-dsc-t1", name="Cyber-shot DSC-T1", ko="사이버샷 T1", sensor="CCD", native=(2592, 1944),
         aliases=["DSC-T1"], exif=[("SONY", "DSC-T1")], categories=["Sony DSC-T1"]),
    dict(brand="sony", slug="sony-dsc-t10", name="Cyber-shot DSC-T10", ko="사이버샷 T10", sensor="CCD", native=(3072, 2304),
         aliases=["DSC-T10"], exif=[("SONY", "DSC-T10")], categories=["Sony DSC-T10"]),
    dict(brand="sony", slug="sony-dsc-w55", name="Cyber-shot DSC-W55", ko="사이버샷 W55", sensor="CCD", native=(3072, 2304),
         aliases=["DSC-W55"], exif=[("SONY", "DSC-W55")], categories=["Sony DSC-W55"]),
    # 삼성
    dict(brand="samsung", slug="samsung-vluu-nv3", name="VLUU NV3", ko="블루 NV3", sensor="CCD", native=(3072, 2304),
         aliases=["NV3", "Samsung NV3"], exif=[("Samsung Techwin", "<Samsung NV3, Samsung VLUU NV3>")],
         categories=["Samsung NV3"]),
    dict(brand="samsung", slug="samsung-kenox-s630", name="KENOX S630", ko="케녹스 S630", sensor="CCD", native=(2816, 2112),
         aliases=["S630", "Samsung S630"], exif=[("Samsung Techwin", "<KENOX S630  / Samsung S630>")],
         categories=["Samsung S630"]),
    # 올림푸스
    dict(brand="olympus", slug="olympus-mju-700", name="μ 700", ko="뮤 700", sensor="CCD", native=(3072, 2304),
         aliases=["mju 700", "Stylus 700", "u700"], exif=[("OLYMPUS IMAGING CORP.", "u700,S700")],
         categories=["Olympus u700 / S-700"]),
    # 카시오
    dict(brand="casio", slug="casio-exilim-ex-z750", name="EXILIM EX-Z750", ko="엑슬림 EX-Z750", sensor="CCD", native=(3072, 2304),
         aliases=["EX-Z750", "엑슬림 Z750"], exif=[("CASIO COMPUTER CO.,LTD", "EX-Z750")],
         categories=["Casio Exilim EX-Z750"]),
]


# ---------- SQL 생성 ----------

def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, int):
        return str(v)
    return "'" + str(v).replace("'", "''") + "'"


def main():
    slugs = [c["slug"] for c in CAMERAS]
    assert len(slugs) == len(set(slugs)), "중복 slug"
    exif_seen = {}
    for c in CAMERAS:
        for mk, md in c["exif"]:
            key = (normalize_exif(mk), normalize_exif(md))
            assert key not in exif_seen, f"EXIF {key}가 {exif_seen.get(key)}와 {c['slug']}에 중복"
            exif_seen[key] = c["slug"]

    out = sys.stdout
    out.write("-- 초기 기종 시드 데이터 (자동 생성 — 직접 고치지 말 것)\n")
    out.write("-- 생성기: backend/scripts/gen_seed_cameras.py · 기종 목록: docs/CAMERAS.md\n")
    out.write(f"-- 브랜드 {len(BRANDS)}개, 기종 {len(CAMERAS)}개\n\n")

    out.write("INSERT INTO brand (name, name_ko, slug) VALUES\n")
    out.write(",\n".join(f"    ({q(n)}, {q(ko)}, {q(s)})" for s, n, ko in BRANDS) + ";\n\n")

    for c in CAMERAS:
        w, h = c["native"]
        out.write(f"-- {c['name']}\n")
        out.write("INSERT INTO camera (brand_id, name, name_ko, slug, sensor_type, native_width, native_height)\n")
        out.write(f"SELECT id, {q(c['name'])}, {q(c['ko'])}, {q(c['slug'])}, {q(c['sensor'])}, {w}, {h} FROM brand WHERE slug = {q(c['brand'])};\n")

        names = [c["name"]] + ([c["ko"]] if c["ko"] else []) + c["aliases"]
        seen, rows = set(), []
        for a in names:
            n = normalize_alias(a)
            if n not in seen:
                seen.add(n)
                rows.append(f"({q(a)}, {q(n)})")
        out.write("INSERT INTO camera_alias (camera_id, alias, normalized)\n")
        out.write(f"SELECT c.id, v.alias, v.normalized FROM camera c, (VALUES {', '.join(rows)}) AS v(alias, normalized) WHERE c.slug = {q(c['slug'])};\n")

        rows = [f"({q(normalize_exif(mk))}, {q(normalize_exif(md))})" for mk, md in c["exif"]]
        out.write("INSERT INTO camera_exif_model (camera_id, exif_make, exif_model)\n")
        out.write(f"SELECT c.id, v.mk, v.md FROM camera c, (VALUES {', '.join(rows)}) AS v(mk, md) WHERE c.slug = {q(c['slug'])};\n")

        rows = [f"({q(cat)})" for cat in c["categories"]]
        out.write("INSERT INTO camera_source_category (camera_id, category_name)\n")
        out.write(f"SELECT c.id, v.cat FROM camera c, (VALUES {', '.join(rows)}) AS v(cat) WHERE c.slug = {q(c['slug'])};\n\n")


if __name__ == "__main__":
    main()
