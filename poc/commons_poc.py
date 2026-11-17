"""Wikimedia Commons 작례 데이터 검증용 스크립트 (PoC).

기종별 "Taken with ○○" 분류에서 사진 정보를 받아,
실제로 작례로 쓸 수 있는 사진이 얼마나 되는지 센다.
사진 파일은 받지 않고 정보(메타데이터)만 본다.

실행: python3 -I commons_poc.py
"""
import collections
import html
import json
import os
import re
import time
import urllib.parse
import urllib.request

API = "https://commons.wikimedia.org/w/api.php"
# 위키미디어 규칙: 서비스를 알 수 있는 User-Agent. 출시 전에는 서비스 주소나 운영용 이메일을 넣는다.
UA = "dica-jakrye-poc/0.1 (vintage digicam sample research; low volume, sequential)"
POOL = 500          # 분류마다 후보로 받아 볼 최대 파일 수
PER_CAMERA = 100    # 기종(분류)마다 최종으로 고를 장수
PER_AUTHOR = 10     # 한 작가에게서 고를 최대 장수
MIN_LONG_SIDE = 400   # 이보다 작으면 썸네일도 안 나와서 버림 (px)
ORIGINAL_RATIO = 0.95  # 긴 변이 카메라 원래 해상도의 95% 이상이면 원본 크기

# 분류별 카메라 원래 해상도(긴 변). 실제 서비스에서는 camera.native_width/height
NATIVE_LONG_SIDE = {
    "Taken with Sony DSC-T1": 2592,
    "Taken with Fujifilm FinePix Z1": 2592,
    "Taken with Nikon Coolpix S1": 2592,
    "Taken with Samsung NV3": 3072,
    "Taken with Canon Digital IXUS 30": 2048,
    "Taken with Casio Exilim EX-FH20": 3456,
}
DELAY = 2.0         # 요청 사이 대기 (초)

CATEGORIES = [
    "Taken with Sony DSC-T1",
    "Taken with Fujifilm FinePix Z1",
    "Taken with Nikon Coolpix S1",
    "Taken with Samsung NV3",
    "Taken with Canon Digital IXUS 30",
    "Taken with Casio Exilim EX-FH20",
]

ALLOWED_LICENSE = re.compile(r"^(public domain|pd|cc0|cc by(-sa)? [0-9.]+)", re.I)


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
            wait = 5 * (attempt + 1)
            print(f"    (재시도 {attempt + 1}: {type(e).__name__} {e}, {wait}초 대기)", flush=True)
            time.sleep(wait)
    raise RuntimeError("요청 실패")


def meta_value(items, name):
    """commonmetadata 목록에서 값 꺼내기"""
    for it in items or []:
        if it.get("name") == name:
            v = it.get("value")
            return v if isinstance(v, (str, int, float)) else None
    return None


def strip_html(s):
    return html.unescape(re.sub(r"<[^>]+>", "", s or "")).strip()


def flash_fired(v):
    # EXIF Flash 값: 홀수(비트0=1)면 발광
    try:
        return int(v) & 1 == 1
    except (TypeError, ValueError):
        return None


def check_category(cat):
    files = []
    params = {
        "action": "query",
        "generator": "categorymembers",
        "gcmtitle": "Category:" + cat,
        "gcmtype": "file",
        "gcmlimit": "50",
        "prop": "imageinfo",
        "iiprop": "url|size|mime|extmetadata|commonmetadata",
        "iiurlwidth": "320",  # 갤러리 미리보기용 축소본 주소
        "iiextmetadatafilter": "Artist|LicenseShortName|LicenseUrl",
    }
    while len(files) < POOL:
        data = get(params)
        pages = data.get("query", {}).get("pages", {})
        files.extend(pages.values())
        cont = data.get("continue")
        if not cont:
            break
        params.update(cont)
        time.sleep(DELAY)
    return files[:POOL]


def pick(eligible):
    """작가별로 돌아가며 한 장씩, 작가당 최대 PER_AUTHOR장까지 PER_CAMERA장을 고른다."""
    by_author = collections.OrderedDict()
    for d in eligible:
        by_author.setdefault(d["artist"], []).append(d)
    chosen = []
    round_no = 0
    while len(chosen) < PER_CAMERA and round_no < PER_AUTHOR:
        added = False
        for items in by_author.values():
            if round_no < len(items) and len(chosen) < PER_CAMERA:
                chosen.append(items[round_no])
                added = True
        if not added:
            break
        round_no += 1
    return chosen


def summarize(cat, files, gallery):
    c = collections.Counter()
    models = collections.Counter()
    eligible = []
    for p in files:
        info = (p.get("imageinfo") or [{}])[0]
        c["후보"] += 1
        ext = info.get("extmetadata", {})
        lic = (ext.get("LicenseShortName") or {}).get("value", "")
        artist = strip_html((ext.get("Artist") or {}).get("value", ""))
        cm = info.get("commonmetadata")
        model = meta_value(cm, "Model")
        w, h = info.get("width") or 0, info.get("height") or 0
        if info.get("mime") != "image/jpeg":
            continue
        if not ALLOWED_LICENSE.match(lic or "") or not artist:
            continue
        if not model:
            continue
        c["라이선스·작가·EXIF 통과"] += 1
        if max(w, h) < MIN_LONG_SIDE:
            c[f"너무 작아 제외(<{MIN_LONG_SIDE}px)"] += 1
            continue
        c["모든 조건 통과"] += 1
        models[str(model).strip()] += 1
        native = NATIVE_LONG_SIDE.get(cat)
        original = (max(w, h) >= native * ORIGINAL_RATIO) if native else None
        eligible.append({
            "category": cat, "title": p.get("title", ""), "thumb": info.get("thumburl"),
            "page": info.get("descriptionurl"), "w": w, "h": h,
            "artist": artist, "license": lic, "model": str(model).strip(),
            "flash": flash_fired(meta_value(cm, "Flash")), "ok": True, "original": original,
        })

    chosen = pick(eligible)
    gallery.extend(chosen)
    authors_all = collections.Counter(d["artist"] for d in eligible)
    authors_chosen = collections.Counter(d["artist"] for d in chosen)

    print(f"\n=== {cat}")
    for k in ["후보", "라이선스·작가·EXIF 통과", f"너무 작아 제외(<{MIN_LONG_SIDE}px)", "모든 조건 통과"]:
        print(f"  {k}: {c[k]}")
    print(f"  선택 중 원본 크기: {sum(1 for d in chosen if d['original'])}장 / 줄인 사진: {sum(1 for d in chosen if d['original'] is False)}장")
    print(f"  최종 선택: {len(chosen)}장 / 작가 {len(authors_chosen)}명 (조건 통과 사진의 작가는 {len(authors_all)}명)")
    top = authors_chosen.most_common(1)
    if top:
        print(f"  가장 많은 작가: {top[0][0][:30]} {top[0][1]}장")
    print(f"  플래시 사용: {sum(1 for d in chosen if d['flash'])}장")
    print("  EXIF 모델 상위:", ", ".join(f"{m}({n})" for m, n in models.most_common(3)) or "-")


def write_gallery(gallery):
    """결과를 눈으로 볼 수 있게 HTML 갤러리로 저장한다. (미리보기는 Commons 축소본을 그대로 띄움)"""
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "commons_result.html")
    data = json.dumps(gallery, ensure_ascii=False).replace("</", "<\\/")
    page = GALLERY_TEMPLATE.replace("__DATA__", data)
    with open(out, "w", encoding="utf-8") as f:
        f.write(page)
    return out


GALLERY_TEMPLATE = """<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Commons 작례 확인</title>
<style>
:root{--bg:#fafafa;--fg:#222;--mute:#777;--card:#fff;--line:#ddd;--bad:#c92a2a;--warn:#e67700}
@media (prefers-color-scheme: dark){:root:not([data-theme=light]){--bg:#161616;--fg:#eee;--mute:#999;--card:#222;--line:#333}}
body{margin:0;padding:16px;background:var(--bg);color:var(--fg);font-family:-apple-system,"Apple SD Gothic Neo",sans-serif;font-size:13px}
h1{font-size:20px;margin:0 0 4px} .sub{color:var(--mute);margin:0 0 12px}
.bar{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin:0 0 16px;position:sticky;top:0;background:var(--bg);padding:8px 0;z-index:2}
select,label{font-size:13px} select{padding:4px 6px}
h2{font-size:16px;margin:24px 0 8px;border-top:2px solid var(--fg);padding-top:6px}
.stats{color:var(--mute);margin-bottom:8px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:10px}
.card{background:var(--card);border:1px solid var(--line);border-radius:8px;overflow:hidden}
.card img{width:100%;aspect-ratio:1;object-fit:cover;display:block;background:#ccc}
.meta{padding:6px 8px;line-height:1.4}
.meta div{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.tag{display:inline-block;border:1px solid var(--line);border-radius:4px;padding:0 4px;margin:2px 2px 0 0;font-size:11px}
.small{color:var(--warn);border-color:var(--warn)} .no{color:var(--bad);border-color:var(--bad)}
a{color:inherit}
</style></head><body>
<h1>Commons 작례 확인</h1>
<p class="sub">기종 분류마다 후보 최대 500장 중 조건(허용 라이선스·작가·EXIF·JPEG·긴 변 400px 이상)을 통과한 사진에서, 작가별로 돌아가며 작가당 최대 10장씩 골라 100장. <b>줄인 사진</b> = 긴 변이 카메라 원래 해상도의 95% 미만. 사진을 누르면 Commons 원본 페이지가 열려요.</p>
<div class="bar">
<label>분류 <select id="cat"><option value="">전체</option></select></label>
<label>플래시 <select id="flash"><option value="">전체</option><option value="1">사용</option><option value="0">미사용</option></select></label>
<label><input type="checkbox" id="hideSmall"> 원본 크기만 보기</label>
<label style="display:none"><input type="checkbox" id="hideBad"></label>
<span id="count" class="stats"></span>
</div>
<div id="out"></div>
<script>
const DATA = __DATA__;
const $ = id => document.getElementById(id);
const cats = [...new Set(DATA.map(d => d.category))];
cats.forEach(c => { const o = document.createElement('option'); o.value = o.textContent = c; $('cat').appendChild(o); });
const esc = s => String(s ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
function render() {
  const cat = $('cat').value, fl = $('flash').value, hs = $('hideSmall').checked, hb = $('hideBad').checked;
  let html = '', shown = 0;
  for (const c of cats) {
    if (cat && c !== cat) continue;
    const items = DATA.filter(d => d.category === c).filter(d => {
      if (hs && d.original !== true) return false;
      if (hb && !d.ok) return false;
      if (fl === '1' && d.flash !== true) return false;
      if (fl === '0' && d.flash !== false) return false;
      return true;
    });
    const authors = new Set(items.map(d => d.artist)).size;
    shown += items.length;
    html += `<h2>${esc(c)}</h2><div class="stats">${items.length}장 · 작가 ${authors}명</div><div class="grid">`;
    for (const d of items) {
      const small = d.original === false;
      html += `<div class="card"><a href="${esc(d.page)}" target="_blank" rel="noopener noreferrer"><img loading="lazy" src="${esc(d.thumb)}" alt=""></a>
<div class="meta"><div title="${esc(d.artist)}">${esc(d.artist) || '(작가 없음)'}</div>
<div>${esc(d.license) || '(라이선스 없음)'} · ${d.w}×${d.h}</div>
<div>${esc(d.model) || '(EXIF 모델 없음)'}</div>
<span class="tag">플래시 ${d.flash === true ? 'O' : d.flash === false ? 'X' : '?'}</span>${small ? '<span class="tag small">줄인 사진</span>' : ''}${d.ok ? '' : '<span class="tag no">제외</span>'}</div></div>`;
    }
    html += '</div>';
  }
  $('out').innerHTML = html;
  $('count').textContent = `표시 ${shown}장`;
}
['cat','flash','hideSmall','hideBad'].forEach(id => $(id).addEventListener('change', render));
render();
</script></body></html>"""


def main():
    print(f"분류마다 후보 최대 {POOL}개 확인 → 조건 통과 사진 중 작가당 {PER_AUTHOR}장, 최대 {PER_CAMERA}장 선택 (사진 파일은 받지 않음)")
    gallery = []
    for cat in CATEGORIES:
        try:
            files = check_category(cat)
        except RuntimeError as e:
            print(f"\n=== {cat}\n  실패: {e}")
            continue
        if not files:
            print(f"\n=== {cat}\n  (분류 없음 또는 파일 0개)")
        else:
            summarize(cat, files, gallery)
        time.sleep(DELAY)
    out = write_gallery(gallery)
    print(f"\n갤러리 저장: {out}")


if __name__ == "__main__":
    main()
