"""네이버 데이터랩 검색어 트렌드(NCP NAVER API HUB) 검증용 스크립트 (PoC).

기준 키워드("디카")와 기종 몇 개의 최근 56일 검색 추이를 받아
- 기종별 값이 0만 나오지 않는지
- 기준 키워드가 기종보다 충분히 큰지
- 최근 28일 vs 그 전 28일 증가율이 어떻게 나오는지
를 확인한다.

키는 같은 폴더의 .env 파일에서 읽는다. (.env는 Git에 올리지 않음)
  NCP_API_HUB_CLIENT_ID=...
  NCP_API_HUB_CLIENT_SECRET=...

실행: python3 -I datalab_poc.py
"""
import datetime as dt
import json
import os
import sys
import urllib.error
import urllib.request

URL = "https://naverapihub.apigw.ntruss.com/search-trend/v1/search"
ANCHOR = {"groupName": "기준:디카", "keywords": ["디카"]}

# 기종당 별칭 최대 5개. 실제 서비스에서는 camera_alias.use_for_trend 값으로 만든다.
CAMERAS = [
    {"groupName": "IXY 200", "keywords": ["IXY 200", "익서스 30", "ixus 30", "ixy digital 200"]},
    {"groupName": "DSC-T1", "keywords": ["DSC-T1", "사이버샷 T1", "소니 T1"]},
    {"groupName": "VLUU NV3", "keywords": ["NV3", "블루 NV3", "VLUU NV3"]},
    {"groupName": "FinePix Z1", "keywords": ["파인픽스 Z1", "FinePix Z1", "후지 Z1"]},
    {"groupName": "COOLPIX S1", "keywords": ["쿨픽스 S1", "COOLPIX S1", "니콘 S1"]},
    {"groupName": "IXY 3", "keywords": ["IXY 3", "익서스 3"]},
    {"groupName": "케녹스 S630", "keywords": ["케녹스 S630", "S630"]},
    {"groupName": "EX-Z750", "keywords": ["EX-Z750", "카시오 Z750", "엑슬림 Z750"]},
]
PERIOD = 28


def load_env():
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
    if not os.path.exists(path):
        sys.exit(".env 파일이 없어요. .env.example을 복사해서 .env를 만들고 키를 넣어 주세요.")
    env = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip()
    cid, sec = env.get("NCP_API_HUB_CLIENT_ID"), env.get("NCP_API_HUB_CLIENT_SECRET")
    if not cid or not sec:
        sys.exit(".env에 NCP_API_HUB_CLIENT_ID, NCP_API_HUB_CLIENT_SECRET 값을 넣어 주세요.")
    return cid, sec


def call(cid, sec, groups, start, end):
    body = {"startDate": start, "endDate": end, "timeUnit": "date", "keywordGroups": groups}
    req = urllib.request.Request(
        URL, data=json.dumps(body).encode(), method="POST",
        headers={"X-NCP-APIGW-API-KEY-ID": cid, "X-NCP-APIGW-API-KEY": sec,
                 "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        # 응답 본문만 출력 (키는 출력하지 않음)
        sys.exit(f"API 오류 {e.code}: {e.read().decode()[:300]}")


def main():
    cid, sec = load_env()
    end = dt.date.today() - dt.timedelta(days=1)
    start = end - dt.timedelta(days=PERIOD * 2 - 1)
    mid = end - dt.timedelta(days=PERIOD - 1)
    print(f"기간: {start} ~ {end} (비교 {start}~{mid - dt.timedelta(days=1)} / 최근 {mid}~{end})")

    calls = 0
    rows = []
    for i in range(0, len(CAMERAS), 4):
        chunk = CAMERAS[i:i + 4]
        data = call(cid, sec, [ANCHOR] + chunk, start.isoformat(), end.isoformat())
        calls += 1
        sums = {}
        for res in data.get("results", []):
            prev = recent = 0.0
            zero_days = 0
            for d in res.get("data", []):
                day = dt.date.fromisoformat(d["period"])
                v = float(d["ratio"])
                if v == 0:
                    zero_days += 1
                if day >= mid:
                    recent += v
                else:
                    prev += v
            days_with_data = len(res.get("data", []))
            sums[res["title"]] = (prev, recent, days_with_data)
        a_prev, a_recent, _ = sums.get(ANCHOR["groupName"], (0, 0, 0))
        print(f"\n[요청 {calls}] 기준 '디카' 합계: 그 전 {a_prev:.1f} / 최근 {a_recent:.1f}")
        for cam in chunk:
            prev, recent, n = sums.get(cam["groupName"], (0, 0, 0))
            score = recent / a_recent * 100 if a_recent else 0
            growth = (recent / prev - 1) * 100 if prev else None
            rows.append((cam["groupName"], score, growth, n))
            g = f"{growth:+.0f}%" if growth is not None else "NEW(그 전 0)"
            print(f"  {cam['groupName']:<12} 최근 관심도(디카=100 기준) {score:6.2f} | 증가율 {g:>10} | 데이터 있는 날 {n}/56")

    print(f"\n총 API 호출: {calls}회")
    print("\n관심도 순:")
    for name, score, growth, n in sorted(rows, key=lambda r: -r[1]):
        print(f"  {name:<12} {score:6.2f}")


if __name__ == "__main__":
    main()
