#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""分场景延迟拆解：list / search / matches 各 600 请求、20 并发。"""
import http.client, json, statistics, sys, threading, time
from urllib.parse import quote

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api/v1"
TOKEN = sys.argv[2] if len(sys.argv) > 2 else ""
IDS = [int(x) for x in (sys.argv[3] if len(sys.argv) > 3 else "").split(",") if x]
HOST, PORT = "localhost", 8080
PREFIX = "/api/v1"

lat = {"list": [], "search": [], "matches": []}
lock = threading.Lock()

def worker(kind, n_req):
    conn = http.client.HTTPConnection(HOST, PORT, timeout=15)
    local = []
    for i in range(n_req):
        if kind == "list":
            path = f"{PREFIX}/posts?page={1 + i % 500}&pageSize=20"
        elif kind == "search":
            kw = quote(["耳机", "校园卡", "雨伞", "黑色", "钥匙", "水杯"][i % 6])
            t = "LOST" if i % 2 else "FOUND"
            path = f"{PREFIX}/posts/search?keyword={kw}&type={t}&page={1 + i % 20}&pageSize=20"
        else:
            path = f"{PREFIX}/posts/{IDS[i % len(IDS)]}/matches"
        t0 = time.perf_counter()
        try:
            conn.request("GET", path, headers={"Authorization": "Bearer " + TOKEN} if kind == "matches" else {})
            r = conn.getresponse(); body = r.read()
            if r.status == 200 and json.loads(body).get("code") == "OK":
                local.append((time.perf_counter() - t0) * 1000)
        except Exception:
            try: conn.close()
            except Exception: pass
            conn = http.client.HTTPConnection(HOST, PORT, timeout=15)
    with lock:
        lat[kind].extend(local)

threads = []
for kind, n in (("list", 30), ("search", 30), ("matches", 30)):
    for _ in range(20):
        threads.append(threading.Thread(target=worker, args=(kind, n)))
# 预热一轮
w = threading.Thread(target=worker, args=("list", 2)); w.start(); w.join()
for t in threads: t.start()
t0 = time.perf_counter()
for t in threads: t.join()
wall = time.perf_counter() - t0

for kind in ("list", "search", "matches"):
    s = sorted(lat[kind]); n = len(s)
    if not n:
        print(f"{kind:8s}: no data"); continue
    p = lambda q: s[min(n - 1, int(q / 100 * n + 0.5) - 1)]
    print(f"{kind:8s}: n={n}  mean={statistics.mean(s):6.1f}ms  P50={p(50):6.1f}  P95={p(95):6.1f}  P99={p(99):6.1f}  max={s[-1]:6.1f}")
print(f"total wall: {wall:.1f}s")
