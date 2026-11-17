"""초기 기종 후보를 Wikimedia Commons로 확인한다.

후보마다
- 추측한 Commons 분류 이름이 실제로 있는지, 파일이 몇 개인지
- 표본 사진의 EXIF 제조사·모델명 (실제 표기)
- 원래 해상도 (표본 중 가장 큰 크기)
를 확인해서 candidates_result.json 에 저장한다.

실행: python3 -I camera_candidates.py
"""
import collections
import json
import os
import time
import urllib.parse
import urllib.request

API = "https://commons.wikimedia.org/w/api.php"
UA = "dica-jakrye-poc/0.1 (vintage digicam sample research; low volume, sequential)"
DELAY = 2.0
SAMPLE = 50

# (id, 브랜드, 국내에서 부르는 대표 이름, 다른 이름들, 추측 Commons 분류 이름들)
CANDIDATES = [
    ("canon-ixy-digital-200", "Canon", "IXY DIGITAL 200", ["IXUS 30", "PowerShot SD200", "익서스 30"], ["Canon Digital IXUS 30", "Canon PowerShot SD200", "Canon IXY Digital 200"]),
    ("canon-ixy-digital-30", "Canon", "IXY DIGITAL 30", ["IXUS 40", "PowerShot SD300", "익서스 40"], ["Canon Digital IXUS 40", "Canon PowerShot SD300", "Canon IXY Digital 30"]),
    ("canon-ixy-digital-55", "Canon", "IXY DIGITAL 55", ["IXUS 55", "PowerShot SD450", "익서스 55"], ["Canon Digital IXUS 55", "Canon PowerShot SD450", "Canon IXY Digital 55"]),
    ("canon-ixy-digital-70", "Canon", "IXY DIGITAL 70", ["IXUS 60", "PowerShot SD600", "익서스 60"], ["Canon Digital IXUS 60", "Canon PowerShot SD600", "Canon IXY Digital 70"]),
    ("canon-ixy-digital-10", "Canon", "IXY DIGITAL 10", ["IXUS 70", "PowerShot SD1000", "익서스 70"], ["Canon Digital IXUS 70", "Canon PowerShot SD1000", "Canon IXY Digital 10"]),
    ("canon-ixy-digital-20-is", "Canon", "IXY DIGITAL 20 IS", ["IXUS 75", "PowerShot SD750", "익서스 75"], ["Canon Digital IXUS 75", "Canon PowerShot SD750", "Canon IXY Digital 20 IS"]),
    ("canon-ixy-digital-600", "Canon", "IXY DIGITAL 600", ["IXUS 750", "PowerShot SD550", "익서스 750"], ["Canon Digital IXUS 750", "Canon PowerShot SD550", "Canon IXY Digital 600"]),
    ("canon-ixy-digital-800-is", "Canon", "IXY DIGITAL 800 IS", ["IXUS 800 IS", "PowerShot SD700 IS", "익서스 800"], ["Canon Digital IXUS 800 IS", "Canon PowerShot SD700 IS", "Canon IXY Digital 800 IS"]),
    ("canon-powershot-a570-is", "Canon", "PowerShot A570 IS", ["파워샷 A570"], ["Canon PowerShot A570 IS"]),
    ("sony-dsc-t1", "Sony", "Cyber-shot DSC-T1", ["사이버샷 T1"], ["Sony DSC-T1"]),
    ("sony-dsc-t10", "Sony", "Cyber-shot DSC-T10", ["사이버샷 T10"], ["Sony DSC-T10"]),
    ("sony-dsc-t100", "Sony", "Cyber-shot DSC-T100", ["사이버샷 T100"], ["Sony DSC-T100"]),
    ("sony-dsc-w1", "Sony", "Cyber-shot DSC-W1", ["사이버샷 W1"], ["Sony DSC-W1"]),
    ("sony-dsc-w55", "Sony", "Cyber-shot DSC-W55", ["사이버샷 W55"], ["Sony DSC-W55"]),
    ("sony-dsc-w80", "Sony", "Cyber-shot DSC-W80", ["사이버샷 W80"], ["Sony DSC-W80"]),
    ("fujifilm-finepix-f31fd", "Fujifilm", "FinePix F31fd", ["파인픽스 F31"], ["Fujifilm FinePix F31fd"]),
    ("fujifilm-finepix-f10", "Fujifilm", "FinePix F10", ["파인픽스 F10"], ["Fujifilm FinePix F10"]),
    ("fujifilm-finepix-z1", "Fujifilm", "FinePix Z1", ["파인픽스 Z1"], ["Fujifilm FinePix Z1"]),
    ("fujifilm-finepix-z5fd", "Fujifilm", "FinePix Z5fd", ["파인픽스 Z5"], ["Fujifilm FinePix Z5fd"]),
    ("nikon-coolpix-s1", "Nikon", "COOLPIX S1", ["쿨픽스 S1"], ["Nikon Coolpix S1"]),
    ("nikon-coolpix-s3", "Nikon", "COOLPIX S3", ["쿨픽스 S3"], ["Nikon Coolpix S3"]),
    ("nikon-coolpix-s200", "Nikon", "COOLPIX S200", ["쿨픽스 S200"], ["Nikon Coolpix S200"]),
    ("nikon-coolpix-3200", "Nikon", "COOLPIX 3200", ["쿨픽스 3200"], ["Nikon Coolpix 3200"]),
    ("olympus-mju-700", "Olympus", "μ 700", ["뮤 700", "Stylus 700", "mju 700"], ["Olympus mju 700", "Olympus µ700", "Olympus Stylus 700", "Olympus mju-700"]),
    ("olympus-mju-400-digital", "Olympus", "μ-40 DIGITAL", ["뮤 40", "Stylus 400", "mju 400 digital"], ["Olympus mju 400 Digital", "Olympus Stylus 400 Digital", "Olympus µ-40 Digital"]),
    ("samsung-vluu-nv3", "Samsung", "VLUU NV3", ["블루 NV3", "NV3"], ["Samsung NV3"]),
    ("samsung-vluu-nv7-ops", "Samsung", "VLUU NV7 OPS", ["블루 NV7", "NV7"], ["Samsung NV7"]),
    ("samsung-vluu-l83t", "Samsung", "VLUU L83T", ["블루 L83T", "L83T"], ["Samsung L83T"]),
    ("samsung-kenox-s630", "Samsung", "KENOX S630", ["케녹스 S630", "S630"], ["Samsung S630", "Samsung Kenox S630"]),
    ("samsung-kenox-v700", "Samsung", "KENOX V700", ["케녹스 V700", "Digimax V700"], ["Samsung Digimax V700", "Samsung V700"]),
    ("casio-exilim-ex-z750", "Casio", "EXILIM EX-Z750", ["엑슬림 Z750"], ["Casio Exilim EX-Z750"]),
    ("casio-exilim-ex-s500", "Casio", "EXILIM EX-S500", ["엑슬림 S500"], ["Casio Exilim EX-S500"]),
    ("casio-exilim-ex-z3", "Casio", "EXILIM EX-Z3", ["엑슬림 Z3"], ["Casio Exilim EX-Z3"]),
    ("panasonic-lumix-dmc-fx01", "Panasonic", "LUMIX DMC-FX01", ["루믹스 FX01", "FX07"], ["Panasonic Lumix DMC-FX01", "Panasonic Lumix DMC-FX07"]),
    ("kodak-easyshare-v570", "Kodak", "EasyShare V570", ["코닥 V570"], ["Kodak EasyShare V570"]),
    ("pentax-optio-wp", "Pentax", "Optio WP", ["옵티오 WP"], ["Pentax Optio WP"]),
]


def get(params):
    params = {**params, "format": "json", "maxlag": "5"}
    url = API + "?" + urllib.parse.urlencode(params)
    for attempt in range(4):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                data = json.loads(r.read().decode())
            if "error" in data and data["error"].get("code") == "maxlag":
                raise RuntimeError("maxlag")
            return data
        except Exception as e:
            time.sleep(5 * (attempt + 1))
    raise RuntimeError("요청 실패")


def category_sizes(names):
    """분류 이름 목록 → {이름: 파일 수}. 없는 분류는 빠진다. 50개씩 한 번에 확인."""
    out = {}
    names = list(dict.fromkeys(names))
    for i in range(0, len(names), 50):
        chunk = names[i:i + 50]
        data = get({"action": "query", "prop": "categoryinfo",
                    "titles": "|".join("Category:Taken with " + n for n in chunk)})
        for p in data.get("query", {}).get("pages", {}).values():
            if "missing" in p:
                continue
            title = p["title"].removeprefix("Category:Taken with ")
            out[title] = p.get("categoryinfo", {}).get("files", 0)
        time.sleep(DELAY)
    return out


def meta(items, name):
    for it in items or []:
        if it.get("name") == name and isinstance(it.get("value"), (str, int, float)):
            return str(it["value"]).strip().strip("\x00").strip()
    return None


def sample(cat):
    data = get({"action": "query", "generator": "categorymembers",
                "gcmtitle": "Category:Taken with " + cat, "gcmtype": "file",
                "gcmlimit": str(SAMPLE), "prop": "imageinfo",
                "iiprop": "size|mime|commonmetadata"})
    makes, models, sizes = collections.Counter(), collections.Counter(), collections.Counter()
    for p in data.get("query", {}).get("pages", {}).values():
        info = (p.get("imageinfo") or [{}])[0]
        if info.get("mime") != "image/jpeg":
            continue
        cm = info.get("commonmetadata")
        mk, md = meta(cm, "Make"), meta(cm, "Model")
        if mk:
            makes[mk] += 1
        if md:
            models[md] += 1
        w, h = info.get("width") or 0, info.get("height") or 0
        if w and h:
            sizes[(max(w, h), min(w, h))] += 1
    return makes, models, sizes


def main():
    all_names = [c for cand in CANDIDATES for c in cand[4]]
    print(f"후보 {len(CANDIDATES)}개, 추측 분류 {len(all_names)}개 확인 중…", flush=True)
    sizes = category_sizes(all_names)

    results = []
    for cid, brand, name, aliases, guesses in CANDIDATES:
        found = {g: sizes[g] for g in guesses if g in sizes and sizes[g] > 0}
        total = sum(found.values())
        makes, models, dims = collections.Counter(), collections.Counter(), collections.Counter()
        for g in found:
            mk, md, sz = sample(g)
            makes.update(mk); models.update(md); dims.update(sz)
            time.sleep(DELAY)
        native = max(dims) if dims else None
        results.append({
            "id": cid, "brand": brand, "name": name, "aliases": aliases,
            "commons_categories": found, "commons_total": total,
            "exif_makes": dict(makes.most_common(3)), "exif_models": dict(models.most_common(4)),
            "native_long_short": native,
        })
        nat = f"{native[0]}x{native[1]}" if native else "-"
        print(f"{name:<22} Commons {total:>6}장 | 분류 {len(found)}/{len(guesses)} | 최대 해상도 {nat:<10} | EXIF 모델 {', '.join(models.keys()) or '-'}", flush=True)

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "candidates_result.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n저장: {out}")


if __name__ == "__main__":
    main()
