#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
mock_selfbench.py — 单独压 mock 自身（TODO #67-③ 前置 ④）

为什么必须有这一步（[[AI并发测试方案]] §二 硬性要求 3 / §六·A · D5）
    mock 是"被测系统的替身"，它自己若是瓶颈，压出来的数字**全是 mock 的吞吐**，与服务端无关。
    ⇒ **先固定并发打 mock，拿到 P50/P99 与吞吐；证明它不是瓶颈之后，再去压 mall-ai。**

判据（三条，脚本自动判定）
    ① mock 的 `peak_active` ≈ 本次并发数  ⇒ 请求**真的并发**（单线程 HTTPServer 会把它压成 1~2）
    ② mock 的吞吐 ≥ mall-ai 阶梯的预期吞吐（一般高出一个数量级）
    ③ 失败数为 0；P99 稳定（不随并发暴涨 → 说明 mock 侧没有排队）

用法（**在新机跑**，mock 也在新机 → 走本机回环，不占内网/公网）
    python3 mock_selfbench.py                                   # 默认 20,50,100 × 20s
    python3 mock_selfbench.py --steps 20,50,100,200 --duration 15
    python3 mock_selfbench.py --tools                            # 带 tools 请求（走工具轮，更接近生产）
    python3 mock_selfbench.py --json mock_bench.json
"""
from __future__ import annotations

import argparse
import json
import statistics
import sys
import threading
import time
import urllib.request
from typing import Any, Dict, List, Optional, Tuple

MOCK_URL = "http://127.0.0.1:9999/v1/chat/completions"
MOCK_ROOT = "http://127.0.0.1:9999/"

MSGS = [{"role": "user", "content": "推荐一款 3000 元以内的手机"}]
TOOLS = [{"type": "function",
          "function": {"name": "searchProducts", "description": "搜索商品",
                       "parameters": {"type": "object", "properties": {}}}}]


def log(msg: str) -> None:
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def mock_stats() -> Dict[str, Any]:
    try:
        with urllib.request.urlopen(MOCK_ROOT, timeout=5) as r:
            return json.load(r)
    except Exception as e:  # noqa: BLE001
        return {"_error": f"{type(e).__name__}: {e}"}


def pct(vals: List[float], p: float) -> float:
    if not vals:
        return 0.0
    k = max(0, min(len(vals) - 1, int(-(-p / 100.0 * len(vals) // 1)) - 1))
    return vals[k]


def one_request(url: str, tools: bool, timeout: float) -> Tuple[bool, float, float, int]:
    """返回 (ok, 总耗时, 首片耗时, 分片数)。读 SSE 到 EOF —— 与 mall-ai 的 Java 客户端同口径。"""
    body: Dict[str, Any] = {"model": "mock-model", "messages": MSGS, "stream": True,
                            "stream_options": {"include_usage": True}}
    if tools:
        body["tools"] = TOOLS
        body["tool_choice"] = "required"
    req = urllib.request.Request(url, data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json",
                                          "Authorization": "Bearer sk-mock"})
    t0 = time.perf_counter()
    ttft = 0.0
    chunks = 0
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            for raw in r:
                line = raw.decode("utf-8", "replace").strip()
                if not line.startswith("data: "):
                    continue
                if ttft == 0.0:
                    ttft = time.perf_counter() - t0
                if line != "data: [DONE]":
                    chunks += 1
        return True, time.perf_counter() - t0, ttft, chunks
    except Exception:  # noqa: BLE001
        return False, time.perf_counter() - t0, ttft, chunks


def step(url: str, conc: int, duration: float, tools: bool) -> Dict[str, Any]:
    lat: List[float] = []
    ttfts: List[float] = []
    chunks_total = 0
    ok = 0
    fail = 0
    lock = threading.Lock()
    end_at = time.perf_counter() + duration

    def worker() -> None:
        nonlocal ok, fail, chunks_total
        while time.perf_counter() < end_at:
            good, el, ttft, ch = one_request(url, tools, timeout=30.0)
            with lock:
                lat.append(el)
                if ttft:
                    ttfts.append(ttft)
                chunks_total += ch
                if good:
                    ok += 1
                else:
                    fail += 1

    log(f"▶ 并发 {conc} 开始（{duration:.0f}s{tools and ' · 带 tools 工具轮' or ''}）")
    threads = [threading.Thread(target=worker, daemon=True) for _ in range(conc)]
    t0 = time.perf_counter()
    for t in threads:
        t.start()
    while time.perf_counter() < end_at:
        time.sleep(max(1.0, duration / 4))
        with lock:
            done = ok + fail
        log(f"    {time.perf_counter() - t0:5.1f}s  RPS={done / max(0.001, time.perf_counter() - t0):7.1f}"
            f"  成功 {ok:6d}  失败 {fail:4d}")
    for t in threads:
        t.join(timeout=10)
    elapsed = max(0.001, time.perf_counter() - t0)
    lat.sort()
    ttfts.sort()
    return {
        "concurrency": conc,
        "rps": round((ok + fail) / elapsed, 1),
        "ok": ok, "failed": fail,
        "latency_ms": {"p50": round(pct(lat, 50) * 1000, 1), "p95": round(pct(lat, 95) * 1000, 1),
                       "p99": round(pct(lat, 99) * 1000, 1),
                       "avg": round(statistics.mean(lat) * 1000, 1) if lat else 0.0},
        "ttft_ms": {"p50": round(pct(ttfts, 50) * 1000, 1), "p99": round(pct(ttfts, 99) * 1000, 1)},
        "chunks_per_req": round(chunks_total / max(1, ok + fail), 2),
    }


def main() -> int:
    ap = argparse.ArgumentParser(description="单独压 mock（证明 mock 不是瓶颈）")
    ap.add_argument("--url", default=MOCK_URL, help=f"mock 端点（默认 {MOCK_URL}，走本机回环）")
    ap.add_argument("--steps", default="20,50,100", help="并发阶梯")
    ap.add_argument("--duration", type=float, default=20.0, help="每档秒数")
    ap.add_argument("--tools", action="store_true", help="带 tools 请求（走工具轮，更接近生产 Agent 链路）")
    ap.add_argument("--json", help="结果写入 JSON")
    args = ap.parse_args()

    before = mock_stats()
    if before.get("_error"):
        log(f"🔴 连不上 mock（{MOCK_ROOT}）：{before['_error']}")
        log("   → 先在新机起 mock：`cd /tmp && nohup python3 mock_llm.py --port 9999 "
            "--prompt-tokens 0 --completion-tokens 0 > /tmp/mock_llm.log 2>&1 &`")
        return 1
    log("=" * 78)
    log(f"mock 自压基准：{args.url}")
    log(f"mock 启动时累计请求 {before['total']}（本次统计只看增量）")
    log("=" * 78)

    steps = [int(s) for s in str(args.steps).replace(" ", "").split(",") if s]
    results = []
    for c in steps:
        results.append(step(args.url, c, args.duration, args.tools))
        time.sleep(1.5)
    after = mock_stats()

    log("=" * 78)
    log("📊 mock 自压结果")
    log(f"{'并发':>5} {'RPS':>9} {'成功':>8} {'失败':>6} {'p50':>9} {'p95':>9} {'p99':>9} "
        f"{'TTFT p50':>10} {'分片/请求':>10}")
    for r in results:
        lm, tt = r["latency_ms"], r["ttft_ms"]
        log(f"{r['concurrency']:>5} {r['rps']:>9} {r['ok']:>8} {r['failed']:>6} "
            f"{lm['p50']:>8}ms {lm['p95']:>8}ms {lm['p99']:>8}ms {tt['p50']:>9}ms {r['chunks_per_req']:>10}")
    log("-" * 78)

    peak = after.get("peak_active", 0)
    max_conc = max(steps)
    log(f"mock 侧观测：累计请求 {before['total']} → {after['total']}"
        f"（增量 {after['total'] - before['total']}）· **峰值并发 {peak}**")
    verdict = []
    if peak >= max_conc * 0.8:
        verdict.append(f"✅ 峰值并发 {peak} ≈ 阶梯峰值 {max_conc} → 请求真的并发了（mock 已线程化）")
    else:
        verdict.append(f"🔴 峰值并发只有 {peak}（阶梯峰值 {max_conc}）→ mock 被串行化，"
                       f"**压出来的数字是 mock 的吞吐**，先查是否用了 ThreadingHTTPServer")
    total_fail = sum(r["failed"] for r in results)
    if total_fail == 0:
        verdict.append("✅ 全程 0 失败")
    else:
        verdict.append(f"🔴 有 {total_fail} 次失败 → 先修 mock 再压服务端")
    base_rps = results[0]["rps"] if results else 0
    verdict.append(f"ℹ️ 单档吞吐参考：{base_rps} req/s @ {steps[0] if steps else '-'} 并发"
                   f" → 只要 mall-ai 阶梯的预期 RPS **明显低于**它，mock 就不是瓶颈")
    for v in verdict:
        log("   " + v)
    log("=" * 78)

    if args.json:
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump({"url": args.url, "tools": args.tools, "steps": steps,
                       "results": results,
                       "mock_total_before": before.get("total"), "mock_total_after": after.get("total"),
                       "mock_peak_active": peak}, f, ensure_ascii=False, indent=2)
        log(f"结果已写入 {args.json}")
    return 0 if (total_fail == 0 and peak >= max_conc * 0.8) else 1


if __name__ == "__main__":
    sys.exit(main())
