#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
load_test.py — CoolShark #48 第二层「可观测展示」压测脚本（浏览档 · 只读）

定位（与 simulate_data.py 的区别，**别混着讲**）
  · simulate_data.py = **慢节奏造数**（0.5~3s/次 ≈ 0.5 QPS）→ 数据**沉淀进数据库**
  · load_test.py     = **短时高峰**（并发阶梯）→ 在 **SkyWalking / Sentinel** 上出**曲线**
  见方案 §六「可观测展示与录像 SOP」；§6.4 三段式录像：静默基线 → 阶梯加压 → 撞限流。

🔴 三条硬事实（2026-09-11 实测，都踩过坑）
  1. **浏览接口也要登录**！mall-front 的安全配置是 `.anyRequest().authenticated()`
     （`ResourceWebSecurityConfiguration.java:64`），不带 token 会返回
     `{"state":401,"message":"您没有登录！"}` —— 而 **HTTP 状态码仍是 200**。
     ⇒ 本脚本**只看 body 里的 `state`**，绝不拿 HTTP 码当成败判据。
  2. **必须在新机（172.29.193.240）跑**：老机只有 5 Mbps 公网带宽，本机压自己 = 自压自伤，
     数据无意义（纪律见 `CLAUDE.md` §五·7）。脚本走**私网** `172.29.193.239`，不占公网带宽。
  3. 用**造数产出的 20 个模拟用户**（`testsim0001`..`testsim0020` / `Sim123456`）登录拿 token。

✅ 安全边界：**纯浏览、零写入、零 AI 调用** —— 不产生业务数据、不消耗 AI 额度、不动库存。
   （限流档要压写接口，属另一档，见方案 §6.2 两档展示）

用法
  # ① 先自检（登录 + 两个端点 + 鉴权判定都验一遍，几秒钟，不压测）
  python3 load_test.py --check

  # ② 三段阶梯（录像主命令；建议先手动录 30~60s 静默基线再执行）
  python3 load_test.py --steps 20,50,100 --duration 45

  # ③ 结果落盘，便于回填到 sim_batch.note / 结果表
  python3 load_test.py --steps 20,50,100 --duration 45 --json load_result.json
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import os
import random
import statistics
import sys
import threading
import time
from collections import Counter
from typing import Any, Dict, List, Optional, Tuple

try:
    import requests
except ImportError:  # 允许没装依赖时也能看 --help
    requests = None  # type: ignore[assignment]

DEPS_HINT = ("缺少依赖：请先 `sudo apt-get install -y python3-pymysql`（pymysql 只有造数脚本需要）；"
             "本脚本只需要 `requests`（系统已自带 2.31.0）")

# 内网 + Gateway（与 simulate_data.py 同源；禁止改成公网 IP）
BASE = os.environ.get("SIM_BASE", "http://172.29.193.239:10087").rstrip("/")
USER_PREFIX = os.environ.get("SIM_USER_PREFIX", "testsim")
USER_PASSWORD = os.environ.get("SIM_USER_PASSWORD", "Sim123456")

# 浏览端点：都是**只读**的（`/front/**` 需登录，见文件头说明）
BROWSE_ENDPOINTS = ("/front/spu/list/all", "/front/spu/{id}")

# 造数脚本产出的 SKU 所属 SPU（实测在售的 SPU id 范围；用不到时随机化也无害）
SPU_ID_CANDIDATES = tuple(range(1, 25))


def discover_spu_ids(api: "Api", token: str, page_size: int = 50) -> List[int]:
    """用列表接口**自动发现真实存在的 SPU id**。

    🔴 为什么不能硬编码：`/front/spu/{id}` 打到不存在的 id 会返回业务错误 →
    会被统计成"失败"，**污染成功率**（压出来的数就不可信了）。
    这里启动时先拉一页列表（只读、1 次请求），把真实 id 收进来。
    """
    state, _, body, _ = api.spu_list_all(1, page_size, token)
    if state not in (200, 20000):
        return []
    data = body.get("data") or {}
    ids: List[int] = []
    for row in (data.get("list") or []):
        try:
            ids.append(int(row["id"]))
        except (KeyError, TypeError, ValueError):
            continue
    return ids


def log(msg: str) -> None:
    print(f"[{dt.datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)


# =============================================================================
# HTTP 客户端（每线程一个 Session —— requests.Session 不是线程安全的）
# =============================================================================

class Api:
    def __init__(self, base: str = BASE, timeout: float = 10.0):
        self.base = base
        self.timeout = timeout
        self._local = threading.local()

    @property
    def session(self) -> "requests.Session":
        s = getattr(self._local, "s", None)
        if s is None:
            s = requests.Session()
            # 连接池放大，避免压测时被 urllib3 默认 10 连接卡住（压出来的是客户端瓶颈）
            adapter = requests.adapters.HTTPAdapter(pool_connections=64, pool_maxsize=64,
                                                    max_retries=0)
            s.mount("http://", adapter)
            self._local.s = s
        return s

    def call(self, method: str, path: str, token_header: Optional[str] = None,
             **kw) -> Tuple[Optional[str], Optional[int], Dict[str, Any], float]:
        """返回 (state, http_code, body, 耗时秒)。**state 才是成败判据**（本项目 401 也返回 HTTP 200）。"""
        headers = kw.pop("headers", {})
        if token_header:
            headers["Authorization"] = token_header
        t0 = time.perf_counter()
        try:
            r = self.session.request(method, self.base + path, headers=headers,
                                     timeout=self.timeout, **kw)
            elapsed = time.perf_counter() - t0
            try:
                body = r.json()
            except ValueError:
                return None, r.status_code, {"_raw": r.text[:200]}, elapsed
            return body.get("state"), r.status_code, body, elapsed
        except Exception as e:  # noqa: BLE001 —— 网络异常也要计入统计，不能中断压测
            return None, None, {"_error": f"{type(e).__name__}: {e}"}, time.perf_counter() - t0

    # --- 认证（POST /user/sso/login，在 mall-sso 白名单里，无需 token）---
    def login(self, username: str, password: str) -> Optional[str]:
        state, _, body, _ = self.call("POST", "/user/sso/login",
                                      json={"username": username, "password": password})
        if state not in (200, 20000):
            return None
        data = body.get("data") or {}
        tok = f"{data.get('tokenHeader') or 'Bearer '}{data.get('tokenValue') or ''}"
        return tok if data.get("tokenValue") else None

    # --- 浏览（**必须带 token**）---
    def spu_list_all(self, page: int, page_size: int, token_header: str):
        return self.call("GET", "/front/spu/list/all", token_header=token_header,
                         params={"page": page, "pageSize": page_size})

    def spu_detail(self, spu_id: int, token_header: str):
        return self.call("GET", f"/front/spu/{spu_id}", token_header=token_header)


def acquire_tokens(api: Api, n_users: int) -> List[str]:
    """登录 testsim0001..N 拿 token（造数脚本已建好这些用户）"""
    tokens: List[str] = []
    failed: List[str] = []
    for i in range(1, n_users + 1):
        u = f"{USER_PREFIX}{i:04d}"
        tok = api.login(u, USER_PASSWORD)
        if tok:
            tokens.append(tok)
        else:
            failed.append(u)
    log(f"登录完成：成功 {len(tokens)}/{n_users}" + (f"，失败 {failed}" if failed else ""))
    return tokens


# =============================================================================
# 统计（线程安全）
# =============================================================================

class Stats:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.lat: List[float] = []
        self.ok = 0
        self.by_state: Counter = Counter()      # 业务 state 计数（含 401/429/5xx 的业务码）
        self.by_http: Counter = Counter()       # HTTP 状态码计数
        self.err: Counter = Counter()           # 异常类型计数

    def add(self, state: Optional[str], http: Optional[int], elapsed: float) -> None:
        with self.lock:
            self.lat.append(elapsed)
            if state in (200, 20000):
                self.ok += 1
            else:
                self.by_state[str(state)] += 1
                if state is None and http is None:
                    pass
            self.by_http[str(http)] += 1

    def add_error(self, kind: str) -> None:
        with self.lock:
            self.err[kind] += 1

    def snapshot(self) -> Tuple[int, int, List[float]]:
        with self.lock:
            return self.ok, sum(self.by_state.values()) + sum(self.err.values()), list(self.lat)


def pct(sorted_vals: List[float], p: float) -> float:
    if not sorted_vals:
        return 0.0
    k = max(0, min(len(sorted_vals) - 1, int(math.ceil(p / 100.0 * len(sorted_vals))) - 1))
    return sorted_vals[k]


# =============================================================================
# 单个阶梯：N 并发打 duration 秒
# =============================================================================

def run_step(api: Api, concurrency: int, duration: float, tokens: List[str],
             think: float, stop_flag: threading.Event, spu_ids: List[int]) -> Dict[str, Any]:
    stats = Stats()
    end_at = time.perf_counter() + duration

    def worker(wid: int) -> None:
        rnd = random.Random(1000 + wid)          # 每线程独立随机源，避免同步
        while time.perf_counter() < end_at and not stop_flag.is_set():
            tok = tokens[(wid + rnd.randint(0, len(tokens) - 1)) % len(tokens)]
            try:
                if rnd.random() < 0.5:
                    state, http, _b, el = api.spu_list_all(rnd.randint(1, 3), 10, tok)
                else:
                    state, http, _b, el = api.spu_detail(rnd.choice(spu_ids), tok)
                stats.add(state, http, el)
            except Exception as e:                # noqa: BLE001
                stats.add_error(type(e).__name__)
                log(f"   ⚠️ 线程 {wid} 异常：{e}")
            if think > 0:
                time.sleep(rnd.uniform(0, think))

    log(f"▶ 并发 {concurrency} 开始（持续 {duration:.0f}s）" +
        (f"（think 0~{think}s）" if think > 0 else "（无间隔，压满）"))
    threads = [threading.Thread(target=worker, args=(i,), daemon=True) for i in range(concurrency)]
    t0 = time.perf_counter()
    for t in threads:
        t.start()

    # 每秒打一行进度 —— 录屏时让观众看到 RPS/成功率在动
    while time.perf_counter() < end_at and not stop_flag.is_set():
        time.sleep(1.0)
        ok, bad, lat = stats.snapshot()
        done = ok + bad
        secs = max(0.001, time.perf_counter() - t0)
        rate = done / secs
        rate_ok = ok / secs
        fail_pct = (bad / done * 100.0) if done else 0.0
        print(f"    [并发 {concurrency:3d}] {secs:5.1f}s  RPS={rate:7.1f}（成功 {rate_ok:7.1f}）"
              f"  成功 {ok:6d}  失败 {bad:5d}（{fail_pct:4.1f}%）", flush=True)
        # 中止阈值（方案 §七 压测纪律）：失败率过高立即停，避免把服务打挂
        if done >= 50 and fail_pct > 20.0:
            log(f"   🔴 失败率 {fail_pct:.1f}% > 20% → **本阶梯立即中止**（安全阈值）")
            stop_flag.set()
            break

    for t in threads:
        t.join(timeout=5)
    elapsed = max(0.001, time.perf_counter() - t0)

    ok, bad, lat = stats.snapshot()
    lat_sorted = sorted(lat)
    done = ok + bad
    result = {
        "concurrency": concurrency,
        "duration_s": round(elapsed, 1),
        "requests": done,
        "ok": ok,
        "failed": bad,
        "success_rate_pct": round(ok / done * 100, 2) if done else 0.0,
        "rps": round(done / elapsed, 1),
        "rps_ok": round(ok / elapsed, 1),
        "latency_ms": {
            "avg": round(statistics.mean(lat_sorted) * 1000, 1) if lat_sorted else 0.0,
            "p50": round(pct(lat_sorted, 50) * 1000, 1),
            "p90": round(pct(lat_sorted, 90) * 1000, 1),
            "p95": round(pct(lat_sorted, 95) * 1000, 1),
            "p99": round(pct(lat_sorted, 99) * 1000, 1),
            "max": round((lat_sorted[-1] if lat_sorted else 0) * 1000, 1),
        },
        "by_state": dict(stats.by_state),
        "by_http": dict(stats.by_http),
        "exceptions": dict(stats.err),
    }
    log(f"■ 并发 {concurrency} 结束：RPS={result['rps']}  成功率={result['success_rate_pct']}%  "
        f"p50={result['latency_ms']['p50']}ms  p95={result['latency_ms']['p95']}ms  "
        f"p99={result['latency_ms']['p99']}ms")
    if result["by_state"]:
        log(f"   非成功 state 分布：{result['by_state']}")
    if result["exceptions"]:
        log(f"   异常分布：{result['exceptions']}")
    return result


# =============================================================================
# 自检（--check）：几秒钟，不压测，只验"登录 + 两个端点 + 鉴权判定"都对
# =============================================================================

def do_check(api: Api, n_users: int) -> bool:
    log("=" * 78)
    log("自检模式（--check）：只发少量只读请求，不做压测")
    log("=" * 78)
    ok_all = True

    # 1) 不带 token —— 应被判为失败（本项目会返回 HTTP 200 + state 401，正是要防的坑）
    state, http, body, _ = api.spu_list_all(1, 2, "")
    verdict = "按预期被判为未登录" if state not in (200, 20000) else "🔴 异常：不带 token 竟然成功"
    if state in (200, 20000):
        ok_all = False
    log(f"1) 不带 token 访问 /front/spu/list/all → HTTP {http} · state={state} · {verdict}")
    log(f"   body: {json.dumps(body, ensure_ascii=False)[:120]}")

    # 2) 登录
    tokens = acquire_tokens(api, n_users)
    if not tokens:
        log("🔴 一个 token 都没拿到 → 检查：造数是否已建 testsim* 用户 / 密码是否正确 / mall-sso 是否在跑")
        return False

    # 3) 发现真实 SPU id（压测的详情请求只打真实存在的 id，否则会被算成失败）
    spu_ids = discover_spu_ids(api, tokens[0])
    log(f"2) 自动发现真实 SPU id：{len(spu_ids)} 个"
        + (f" → {spu_ids[:8]}{' …' if len(spu_ids) > 8 else ''}" if spu_ids else ""))
    if not spu_ids:
        log("🔴 列表接口没返回任何商品 → 商品数据异常")
        ok_all = False

    # 4) 带 token 逐个端点验一遍
    checks: List[Tuple[str, Any]] = [("/front/spu/list/all", lambda t: api.spu_list_all(1, 2, t))]
    if spu_ids:
        checks.append((f"/front/spu/{spu_ids[0]}", lambda t: api.spu_detail(spu_ids[0], t)))
    for n, (name, call) in enumerate(checks, start=3):
        state, http, body, el = call(tokens[0])
        good = state in (200, 20000)
        ok_all = ok_all and good
        log(f"{n}) {name} → HTTP {http} · state={state} · {el * 1000:.0f}ms · {'✅' if good else '🔴'}")
        if good:
            data = body.get("data")
            if isinstance(data, dict) and "list" in data:
                rows = data.get("list") or []
                log(f"   total={data.get('total')}  首条={rows[0].get('name') if rows else '<空>'}")
            elif isinstance(data, dict):
                log(f"   商品名={data.get('name')}  库存={data.get('stock')}")

    log("=" * 78)
    log("自检结论：" + ("✅ 全部通过 —— 可以录像（记得先录 30~60s 静默基线，见方案 §6.4）"
                        if ok_all else "🔴 有项目未通过，先看上面日志"))
    log("提示：录像机位 = SkyWalking Load 曲线 + Sentinel 实时监控 + 本脚本滚动日志（三个窗口并排）")
    log("=" * 78)
    return ok_all


# =============================================================================
# main
# =============================================================================

def main() -> None:
    ap = argparse.ArgumentParser(description="CoolShark #48 可观测展示压测脚本（浏览档 · 只读）")
    ap.add_argument("--steps", default="20,50,100",
                    help="并发阶梯，逗号分隔（默认 20,50,100 —— 见方案 §6.4）")
    ap.add_argument("--duration", type=float, default=45.0, help="每个阶梯持续秒数（默认 45）")
    ap.add_argument("--users", type=int, default=20, help="用前 N 个模拟用户登录拿 token（默认 20）")
    ap.add_argument("--think", type=float, default=0.0,
                    help="每请求后随机 sleep 0~think 秒（默认 0=压满；想模拟真实用户可给 0.3）")
    ap.add_argument("--warmup", type=float, default=5.0, help="正式阶梯前的预热秒数（默认 5）")
    ap.add_argument("--gap", type=float, default=2.0, help="阶梯之间的停顿秒数（默认 2）")
    ap.add_argument("--wait", type=float, default=0.0,
                    help="开跑前等待秒数（用它对齐录像：先按回车再开始录静默基线）")
    ap.add_argument("--base", default=BASE, help=f"网关地址（默认 {BASE}，必须走内网）")
    ap.add_argument("--json", help="把结果写成 JSON 文件（便于回填 sim_batch.note / 结果表）")
    ap.add_argument("--check", action="store_true", help="只自检（登录+端点+鉴权判定），不压测")
    args = ap.parse_args()

    if requests is None:
        sys.exit(DEPS_HINT)

    api = Api(args.base)
    if args.check:
        sys.exit(0 if do_check(api, args.users) else 1)

    steps = [int(s) for s in str(args.steps).replace(" ", "").split(",") if s]
    if not steps:
        sys.exit("--steps 解析为空")

    log("=" * 78)
    log(f"CoolShark #48 可观测展示压测（浏览档 · 只读）  base={args.base}")
    log(f"阶梯={steps}  每档 {args.duration:.0f}s  预热 {args.warmup:.0f}s  用户 {args.users}")
    log("⚠️ 两档流量别混着讲：本脚本是**短时高峰**（出面板曲线）；造数是**慢节奏沉淀**（进数据库）")
    log("⚠️ 必须在新机跑（老机 5Mbps 自压自伤）；本脚本走私网、不占公网带宽")
    log("=" * 78)

    if args.wait > 0:
        log(f"⏳ {args.wait:.0f}s 后开始 —— **现在就开录**（先录 30~60s 静默基线，方案 §6.4 ①）")
        time.sleep(args.wait)
    else:
        log("提示：建议先把三个窗口并排就位、录 30~60s **静默基线**，再执行本脚本（§6.4 ①）")

    tokens = acquire_tokens(api, args.users)
    if not tokens:
        sys.exit("🔴 没有可用 token → 先跑 `python3 simulate_data.py --preflight` 确认造数已建用户")

    spu_ids = discover_spu_ids(api, tokens[0])
    if not spu_ids:
        sys.exit("🔴 没发现任何真实 SPU id → 商品数据异常，先跑 `--check`")
    log(f"已发现 {len(spu_ids)} 个真实 SPU id（详情请求只打这些，避免把不存在的 id 算成失败）")

    stop_flag = threading.Event()
    if args.warmup > 0:
        log(f"预热 {args.warmup:.0f}s（结果不计入统计）…")
        run_step(api, max(2, min(steps)), args.warmup, tokens, args.think, stop_flag, spu_ids)
        time.sleep(1)

    results: List[Dict[str, Any]] = []
    t_start = time.perf_counter()
    for idx, c in enumerate(steps, 1):
        if stop_flag.is_set():
            log("已中止，跳过后续阶梯")
            break
        log(f"—— 阶梯 {idx}/{len(steps)}：{c} 并发 ——")
        results.append(run_step(api, c, args.duration, tokens, args.think, stop_flag, spu_ids))
        if idx != len(steps):
            time.sleep(args.gap)
    total_s = time.perf_counter() - t_start

    log("=" * 78)
    log("📊 阶梯汇总（可直接贴进 sim_batch.note 或结果表）")
    log("-" * 78)
    log(f"{'并发':>5} {'RPS':>9} {'成功RPS':>9} {'成功率':>8} {'p50':>8} {'p95':>8} {'p99':>8} {'失败':>7}")
    for r in results:
        lm = r["latency_ms"]
        log(f"{r['concurrency']:>5} {r['rps']:>9} {r['rps_ok']:>9} "
            f"{r['success_rate_pct']:>7.2f}% {lm['p50']:>7}ms {lm['p95']:>7}ms {lm['p99']:>7}ms "
            f"{r['failed']:>7}")
    log("-" * 78)
    log(f"总耗时 {total_s:.0f}s")
    log("🎬 录像收尾（§6.4 ④）：停压 → 录曲线回落 → 打开 SkyWalking Trace 看那一批请求的真实链路")
    log("=" * 78)

    if args.json:
        payload = {
            "generated_at": dt.datetime.now().isoformat(timespec="seconds"),
            "base": args.base, "steps": steps, "duration_per_step": args.duration,
            "users": len(tokens), "think": args.think,
            "results": results, "total_seconds": round(total_s, 1),
            "note": "浏览档（只读、需 token）；与 simulate_data.py 的慢节奏造数是两套流量",
        }
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        log(f"结果已写入 {args.json}")


if __name__ == "__main__":
    main()
