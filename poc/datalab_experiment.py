"""데이터랩 추가 실험: 기종 단위 검색량이 너무 작게 나와서 조건을 바꿔 본다.

A. 기준 키워드 없이 기종끼리만 (주 단위)
B. 시리즈 이름(익서스, 사이버샷 등) + 기준 "디카" (일 단위)
C. 기준을 작은 키워드("빈티지 디카")로 바꾸고 기종 4개 (주 단위)

실행: python3 -I datalab_experiment.py
"""
import datetime as dt
import json
import sys
import urllib.error
import urllib.request

from datalab_poc import URL, load_env

end = dt.date.today() - dt.timedelta(days=1)
start = end - dt.timedelta(days=55)


def call(cid, sec, groups, unit):
    body = {"startDate": start.isoformat(), "endDate": end.isoformat(),
            "timeUnit": unit, "keywordGroups": groups}
    req = urllib.request.Request(
        URL, data=json.dumps(body).encode(), method="POST",
        headers={"X-NCP-APIGW-API-KEY-ID": cid, "X-NCP-APIGW-API-KEY": sec,
                 "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        sys.exit(f"API 오류 {e.code}: {e.read().decode()[:300]}")


def show(title, data):
    print(f"\n=== {title}")
    for res in data.get("results", []):
        vals = [float(d["ratio"]) for d in res.get("data", [])]
        nonzero = sum(1 for v in vals if v > 0)
        print(f"  {res['title']:<14} 합계 {sum(vals):8.2f} | 최대 {max(vals) if vals else 0:6.2f} | 값 있는 구간 {nonzero}개")


cid, sec = load_env()
print(f"기간: {start} ~ {end}")

show("A. 기종끼리만 (주 단위)", call(cid, sec, [
    {"groupName": "IXY 200", "keywords": ["IXY 200", "익서스 30", "ixus 30"]},
    {"groupName": "DSC-T1", "keywords": ["DSC-T1", "사이버샷 T1"]},
    {"groupName": "FinePix Z1", "keywords": ["파인픽스 Z1", "FinePix Z1"]},
    {"groupName": "COOLPIX S1", "keywords": ["쿨픽스 S1", "COOLPIX S1"]},
    {"groupName": "케녹스 S630", "keywords": ["케녹스 S630"]},
], "week"))

show("B. 시리즈 이름 + 기준 '디카' (일 단위)", call(cid, sec, [
    {"groupName": "기준:디카", "keywords": ["디카"]},
    {"groupName": "익서스/IXY", "keywords": ["익서스", "IXY", "ixus"]},
    {"groupName": "사이버샷", "keywords": ["사이버샷", "cybershot"]},
    {"groupName": "케녹스/블루", "keywords": ["케녹스", "블루 디카", "vluu"]},
    {"groupName": "쿨픽스", "keywords": ["쿨픽스", "coolpix"]},
], "date"))

show("C. 기준 '빈티지 디카' + 기종 (주 단위)", call(cid, sec, [
    {"groupName": "기준:빈티지디카", "keywords": ["빈티지 디카", "빈티지디카"]},
    {"groupName": "IXY 200", "keywords": ["IXY 200", "익서스 30", "ixus 30"]},
    {"groupName": "COOLPIX S1", "keywords": ["쿨픽스 S1", "COOLPIX S1"]},
    {"groupName": "케녹스 S630", "keywords": ["케녹스 S630"]},
    {"groupName": "FinePix Z1", "keywords": ["파인픽스 Z1", "FinePix Z1"]},
], "week"))

print("\n총 API 호출: 3회")
