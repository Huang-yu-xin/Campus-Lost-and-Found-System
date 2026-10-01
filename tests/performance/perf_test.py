#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
校园失物招领系统 —— 性能测试（任务书 NFR-PERF-01 场景）
目标：1 万条发布、20 并发查询，列表 P95 ≤ 2 秒。

场景混合（模拟真实用户浏览行为）：
  A 55%  公开列表分页      GET /posts?page={1..500}&pageSize=20
  B 30%  组合筛选搜索      GET /posts/search?keyword=..&type=..&category=..&page={1..20}
  C 15%  匹配候选（带token）GET /posts/{id}/matches

用法：python perf_test.py [BASE] [TOKEN]
默认 BASE=http://localhost:8080/api/v1；TOKEN 用于场景 C（dev 环境 mock 登录获取）。
"""
import http.client
import json
import random
import statistics
import sys
import threading
import time
from urllib.parse import urlparse, quote

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api/v1"
TOKEN = sys.argv[2] if len(sys.argv) > 2 else ""
WORKERS = 20
PER_WORKER = 100          # 每线程测量请求数
WARMUP = 200              # 预热请求（不计入统计）
SEED = 20260929

u = urlparse(BASE)
HOST, PORT = u.hostname, u.port or 80
PREFIX = u.path.rstrip("/")

KEYWORDS = ["耳机", "校园卡", "雨伞", "黑色", "钥匙", "钱包", "水杯", "充电宝", "手机", "保温杯"]
TYPES = ["LOST", "FOUND", ""]
CATEGORIES = ["雨伞", "校园卡", "钥匙", "耳机", "水杯", ""]

# 场景 C 用的 ACTIVE LOST 帖 id（由外部传入或脚本内从列表 API 抽取）
MATCH_IDS = []
if len(sys.argv) > 3:
    MATCH_IDS = [int(x) for x in sys.argv[3].split(",") if x]

random.seed(SEED)

class Client:
    """线程本地 keep-alive 连接"""
    def __init__(self):
        self.conn = None
    def get(self, path):
        if self.conn is None:
            self.conn = http.client.HTTPConnection(HOST, PORT, timeout=15)
        for attempt in (0, 1):
            try:
                self.conn.request("GET", PREFIX + path, headers=_headers(path))
                resp = self.conn.getresponse()
                body = resp.read()
                return resp.status, body
            except Exception:
                try: self.conn.close()
                except Exception: pass
                self.conn = http.client.HTTPConnection(HOST, PORT, timeout=15)
                if attempt == 1:
                    raise
        raise RuntimeError("unreachable")

def _headers(path):
    h = {"Accept": "application/json"}
    if TOKEN and "/matches" in path:
        h["Authorization"] = "Bearer " + TOKEN
    return h

KEYWORD_BY_CAT = {
    "雨伞": ["雨伞", "长柄伞", "晴雨伞", "自动伞"],
    "校园卡": ["校园卡", "卡"],
    "钥匙": ["钥匙", "钥匙串"],
    "耳机": ["耳机", "蓝牙耳机", "充电盒"],
    "水杯": ["水杯", "保温杯", "吸管杯"],
    "": ["黑色", "白色", "蓝色", "粉色", "捡到", "丢失"],
}

def build_request(i):
    """按混合比例构造请求路径"""
    r = random.random()
    if r < 0.55 or not MATCH_IDS:
        page = random.randint(1, 500)
        t = random.choice(["", "&type=LOST", "&type=FOUND"])
        return f"/posts?page={page}&pageSize=20{t}"
    elif r < 0.85:
        cat = random.choice(CATEGORIES)
        kw = random.choice(KEYWORD_BY_CAT[cat])
        t = random.choice(TYPES)
        page = random.randint(1, 20)
        path = f"/posts/search?keyword={quote(kw)}&page={page}&pageSize=20"
        if t: path += f"&type={t}"
        if cat: path += f"&category={quote(cat)}"
        return path
    else:
        pid = random.choice(MATCH_IDS)
        return f"/posts/{pid}/matches"

latencies = []
errors = []
lock = threading.Lock()
counter = [0]

def worker(wid):
    c = Client()
    # 预热
    for _ in range(WARMUP // WORKERS):
        try: c.get(build_request(0))
        except Exception as e: pass
    local = []
    err = 0
    for _ in range(PER_WORKER):
        path = build_request(wid * PER_WORKER + _)
        t0 = time.perf_counter()
        try:
            status, body = c.get(path)
            dt_ms = (time.perf_counter() - t0) * 1000
            if status != 200:
                err += 1
                if len(errors) < 10:
                    errors.append((path, status, body[:120]))
            else:
                d = json.loads(body)
                if d.get("code") not in ("OK",):
                    err += 1
                    if len(errors) < 10:
                        errors.append((path, status, d.get("message", "")[:120]))
                else:
                    local.append(dt_ms)
        except Exception as e:
            err += 1
            if len(errors) < 10:
                errors.append((path, 0, str(e)[:120]))
    with lock:
        latencies.extend(local)
        counter[0] += err

def pct(sorted_list, p):
    if not sorted_list: return float("nan")
    k = max(0, min(len(sorted_list) - 1, int(round(p / 100.0 * len(sorted_list) + 0.5)) - 1))
    return sorted_list[k]

if __name__ == "__main__":
    print(f"target={BASE} workers={WORKERS} measured={WORKERS*PER_WORKER} warmup={WARMUP}")
    print(f"match_ids({len(MATCH_IDS)})={MATCH_IDS[:8]}...")
    t0 = time.perf_counter()
    threads = [threading.Thread(target=worker, args=(i,)) for i in range(WORKERS)]
    for t in threads: t.start()
    for t in threads: t.join()
    wall = time.perf_counter() - t0

    s = sorted(latencies)
    n = len(s)
    print("\n===== 结果（不含预热与非 200/非 OK 响应）=====")
    print(f"successful: {n}   failed: {counter[0]}   wall: {wall:.1f}s   RPS: {n/wall:.1f}")
    if n:
        print(f"mean={statistics.mean(s):.1f}ms  P50={pct(s,50):.1f}ms  P90={pct(s,90):.1f}ms  "
              f"P95={pct(s,95):.1f}ms  P99={pct(s,99):.1f}ms  max={s[-1]:.1f}ms")
        target = 2000.0
        verdict = "PASS" if pct(s, 95) <= target else "FAIL"
        print(f"NFR-PERF-01 目标 P95 ≤ {target:.0f}ms -> {verdict} (P95={pct(s,95):.1f}ms)")
    if errors:
        print("\nerror samples:")
        for e in errors:
            print("  ", e)
    sys.exit(0 if (n and pct(s, 95) <= 2000.0) else 1)
