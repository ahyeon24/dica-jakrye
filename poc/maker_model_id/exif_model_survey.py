"""Commons 'Taken with …' 분류의 모든 파일에서 EXIF 제조사·모델명이 몇 장씩인지 센다.

같은 기종이 나라별로 다른 이름(IXUS·IXY·PowerShot SD 등)으로 찍혀 있는지 찾는 데 쓴다.
파일은 받지 않고 API의 메타데이터만 읽는다. 위키미디어 규칙: 순차 요청, maxlag, 요청 사이 1초.

입력: 탭으로 나눈 "기종slug<TAB>분류 이름" 목록 ("Taken with "는 빼고)
  예) canon-ixus-30<TAB>Canon Digital IXUS 30
  DB에서 뽑기: SELECT c.slug, s.category_name FROM camera_source_category s JOIN camera c ON c.id = s.camera_id;

실행: python3 -I exif_model_survey.py categories.tsv [--samples 3]
  --samples N: 분류에서 가장 많은 이름이 아닌 모델명마다 파일 이름을 N개씩 함께 출력한다.
               출력의 "S<TAB>…" 줄을 모아 ModelId.java의 입력으로 쓰면 내부 번호를 확인할 수 있다.

결과 (2026-11-19): docs/CAMERAS.md 2장 "캐논 내부 번호"
"""
import json
import sys
import time
import urllib.parse
import urllib.request
from collections import Counter

UA = "dica-jakrye/0.1 (dicajakrye@gmail.com) python-urllib"
API = "https://commons.wikimedia.org/w/api.php"
MAX_FILES = 3000  # 분류 하나에서 볼 최대 파일 수 (큰 분류는 일부만)


def call(params):
    url = API + "?" + urllib.parse.urlencode(params)
    for _ in range(5):
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                data = json.load(r)
        except OSError:  # 네트워크가 잠깐 끊기면 기다렸다 다시
            time.sleep(15)
            continue
        if data.get("error", {}).get("code") == "maxlag":
            time.sleep(10)
            continue
        return data
    raise RuntimeError("요청을 계속 실패함")


def survey(category):
    params = {
        "action": "query", "format": "json", "formatversion": "2", "maxlag": "5",
        "generator": "categorymembers", "gcmtitle": "Category:Taken with " + category,
        "gcmtype": "file", "gcmlimit": "50",
        "prop": "imageinfo", "iiprop": "metadata", "iimetadataversion": "latest",
    }
    counts, titles, files = Counter(), {}, 0
    while True:
        data = call(params)
        for page in data.get("query", {}).get("pages", []):
            files += 1
            meta = {}
            for ii in page.get("imageinfo", []):
                for m in ii.get("metadata") or []:
                    meta[m["name"]] = m["value"]
            make = str(meta.get("Make", "")).strip()
            model = str(meta.get("Model", "")).strip()
            key = (make, model) if model else ("", "(EXIF 없음)")
            counts[key] += 1
            titles.setdefault(key, []).append(page["title"])
        time.sleep(1)
        if "continue" not in data or files >= MAX_FILES:
            return counts, titles, files, "continue" in data
        params.update(data["continue"])


def main():
    args = sys.argv[1:]
    samples = 0
    if "--samples" in args:
        i = args.index("--samples")
        samples = int(args[i + 1])
        del args[i:i + 2]
    sample_lines = []
    for line in open(args[0], encoding="utf-8"):
        if not line.strip():
            continue
        slug, cat = line.rstrip("\n").split("\t")
        counts, titles, files, capped = survey(cat)
        print(f"\n## {slug}  (분류: Taken with {cat}, 파일 {files}{'+ 일부만' if capped else ''})")
        for (make, model), n in counts.most_common():
            print(f"  {n:5d}  {make} / {model}")
        if samples:
            for key, _ in counts.most_common()[1:]:
                if key[0]:
                    for t in titles[key][:samples]:
                        sample_lines.append(f"S\t{slug}\t{key[1]}\t{t}")
        sys.stdout.flush()
    if sample_lines:
        print("\n# 표본 (ModelId.java 입력: 앞의 S 열을 빼고 쓴다)")
        print("\n".join(sample_lines))


if __name__ == "__main__":
    main()
