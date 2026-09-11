#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
load_test.py — CoolShark #48 第二层「可观测展示」压测脚本（两档：浏览 pass / 限流 block）

定位（与 simulate_data.py 的区别，**别混着讲**）
  · simulate_data.py = **慢节奏造数**（0.5~3s/次 ≈ 0.5 QPS）→ 数据**沉淀进数据库**
  · load_test.py     = **短时高峰**（并发阶梯）→ 在 **SkyWalking / Sentinel** 上出**曲线**
  见方案 §六「可观测展示与录像 SOP」；§6.4 三段式录像：静默基线 → 阶梯加压 → 撞限流。

两档（方案 §6.2 的"两档展示"，**结论不能混着讲**）
  · `--mode browse`（默认）：压**只读浏览** URL → 看 **pass QPS 曲线**（SkyWalking / Sentinel）
  · `--mode limit`         ：压**有流控规则的写接口** → 看 **block 曲线**（Sentinel 限流生效）

🔴 四条硬事实（2026-09-11 实测，都踩过坑）
  1. **浏览接口也要登录**（`mall-front` 是 `.anyRequest().authenticated()`，见
     `ResourceWebSecurityConfiguration.java:64`）→ 不带 token 返回 `state=401`，
     **而 HTTP 状态码仍是 200** ⇒ 本脚本**只看 body 的 `state`**。
  2. **必须在新机（172.29.193.240）跑**：老机只有 5 Mbps 公网带宽，本机压自己是自压自伤。
     脚本走**私网** `172.29.193.239`，不占公网带宽。
  3. **被 Sentinel 拦截 = `state=429`**（`OrderBlockHandler` 返回
     `ResponseCode.TOO_MANY_REQUESTS`）→ 这是区分"被限流"与"业务失败"的**唯一判据**。
  4. **"能压出 block"取决于有没有别的闸门先把你拦住**（本文件 `LIMIT_TARGETS` 里有实测记录）：
     `支付订单` 有 `@Idempotent(key="pay", expire=10)`，key 含 `userId+argsDigest`
     → 4 个订单 × 20 用户 = 只有 80 个可用 key → **10 秒内最多 8/s，够不到规则里的 20 QPS**。

用什么账号
  · 造数产出的 20 个模拟用户：`testsim0001`..`testsim0020` / `Sim123456`（浏览档 + pay 档）
  · 限流档默认靶子 `adminLogin`（`mall-sso`，QPS 10）：**只需合法格式的假账号**（无需真实管理员）

✅ 数据安全
  · `browse`：零写入、零 AI 调用
  · `limit --target pay`：用**已存在的订单**反复支付 → 被放行的会因"已支付/幂等"失败 → **零新增业务数据**
  · `limit --target adminlogin`：**不产生业务数据**，只会在 `cs_mall_ams.ams_admin_login_log`
    留下"失败的管理员登录尝试"日志行（**已明确披露**，见 README；这不是 `data_source` 9 张表之一）

用法
  python3 load_test.py --check                                   # 自检（两档的前置都验一遍）
  python3 load_test.py --steps 20,50,100 --duration 45           # 浏览档（pass 曲线）
  python3 load_test.py --mode limit --target adminlogin --steps 20,40 --duration 30   # 限流档（block 曲线）
  python3 load_test.py --mode limit --target pay --steps 30 --duration 20             # 实测 pay 能否出 block
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
from typing import Any, Callable, Dict, List, Optional, Tuple

try:
    import requests
except ImportError:  # 允许没装依赖时也能看 --help
    requests = None  # type: ignore[assignment]

DEPS_HINT = "缺少依赖：本脚本只需要 `requests`（系统已自带 2.31.0）；pymysql 只有造数脚本需要"

BASE = os.environ.get("SIM_BASE", "http://172.29.193.239:10087").rstrip("/")
USER_PREFIX = os.environ.get("SIM_USER_PREFIX", "testsim")
USER_PASSWORD = os.environ.get("SIM_USER_PASSWORD", "Sim123456")

# 被 Sentinel 拦截的业务码（`OrderBlockHandler` → ResponseCode.TOO_MANY_REQUESTS）
BLOCKED_STATE = 429

# 限流档靶子：resource 名 / 规则 QPS / 端点 / 说明（`deploy/docker/sentinel/*.json`）
# 🔬 2026-09-11 实测（这条是**量出来的**，不是推断）：
#    · adminlogin：20 并发 × 8s → RPS 165.5，**被限流 1259 次（93.26%）** ✅ 完美出 block
#    · pay      ：30 并发 × 8s → RPS  74.4，**被限流只有 4 次（0.66%）** ❌ 压不出来
#      原因：`@Idempotent(key="pay", expire=10)` 的切面**在 Sentinel 切面之外**先拦 →
#      583 次返回 **409（幂等锁）**，只有极少数走到 `@SentinelResource`
#      ⚠️ 判据陷阱：**409 不是 block，只有 429 才是**（混起来会得出完全相反的结论）
LIMIT_TARGETS = {
    "adminlogin": {
        "path": "/admin/sso/login", "resource": "adminLogin", "rule_qps": 10, "who": "mall-sso",
        "note": "无 @Idempotent → 每次请求都计入 Sentinel，**实测 93% 被限流**（推荐靶子）；"
                "不产生业务数据",
    },
    "pay": {
        "path": "/oms/order/pay", "resource": "支付订单", "rule_qps": 20, "who": "mall-order",
        "note": "🔴 **实测压不出 block（仅 0.66%）**：`@Idempotent(key=pay, expire=10)` 切面在 "
                "Sentinel 之外先拦 → 大量 **409（幂等锁）** 而非 429。保留此靶子只为"
                "「可复现实测结论」，演示请用 adminlogin",
    },
}

# 假的、但**格式合法**的管理员账号（只为让 DTO 校验通过、从而触达 @SentinelResource）
FAKE_ADMIN_USER = os.environ.get("SIM_FAKE_ADMIN_USER", "adminfake01")
FAKE_ADMIN_PASS = os.environ.get("SIM_FAKE_ADMIN_PASS", "Sim123456")


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
            adapter = requests.adapters.HTTPAdapter(pool_connections=64, pool_maxsize=64,
                                                    max_retries=0)
            s.mount("http://", adapter)
            self._local.s = s
        return s

    def call(self, method: str, path: str, token_header: Optional[str] = None,
             **kw) -> Tuple[Optional[int], Optional[int], Dict[str, Any], float]:
        """返回 (state, http_code, body, 耗时秒)。**state 才是成败判据**（401 也是 HTTP 200）。"""
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

    # --- 认证 ---
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

    # --- 订单（限流档 · pay 靶子）---
    def order_list(self, page: int, page_size: int, token_header: str):
        return self.call("GET", "/oms/order/list", token_header=token_header,
                         params={"page": page, "pageSize": page_size})

    def order_pay(self, order_id: int, token_header: str):
        # paymentType 必须 2=支付宝（0=银联会 500「暂未实现」，见 PaymentTypeEnum）
        return self.call("POST", "/oms/order/pay", token_header=token_header,
                         json={"id": order_id, "paymentType": 2})

    # --- 管理员登录（限流档 · adminLogin 靶子；假账号 + 合法格式）---
    def admin_login(self, username: str = FAKE_ADMIN_USER, password: str = FAKE_ADMIN_PASS):
        return self.call("POST", "/admin/sso/login",
                         json={"username": username, "password": password})


def acquire_tokens(api: Api, n_users: int) -> List[str]:
    tokens: List[str] = []
    failed: List[str] = []
    for i in range(1, n_users + 1):
        u = f"{USER_PREFIX}{i:04d}"
        tok = api.login(u, USER_PASSWORD)
        (tokens if tok else failed).append(tok or u)
    log(f"登录完成：成功 {len(tokens)}/{n_users}" + (f"，失败 {failed}" if failed else ""))
    return tokens


def discover_spu_ids(api: Api, token: str, page_size: int = 50) -> List[int]:
    """用列表接口**自动发现真实存在的 SPU id**（硬编码 id 打到不存在的商品会被算成失败、污染成功率）"""
    state, _, body, _ = api.spu_list_all(1, page_size, token)
    if state not in (200, 20000):
        return []
    ids: List[int] = []
    for row in ((body.get("data") or {}).get("list") or []):
        try:
            ids.append(int(row["id"]))
        except (KeyError, TypeError, ValueError):
            continue
    return ids


def discover_orders(api: Api, tokens: List[str], page_size: int = 20) -> List[Tuple[str, int]]:
    """给每个用户查他名下的订单 → [(token, order_id)]（纯 API，**不需要数据库**）"""
    pairs: List[Tuple[str, int]] = []
    for tok in tokens:
        state, _, body, _ = api.order_list(1, page_size, tok)
        if state not in (200, 20000):
            continue
        rows = (body.get("data") or {}).get("list") or []
        for row in rows:
            try:
                pairs.append((tok, int(row["id"])))
            except (KeyError, TypeError, ValueError):
                continue
    return pairs


# =============================================================================
# 统计（线程安全）
# =============================================================================

class Stats:
    """`blocked`（被 Sentinel 限流）与 `hard_fail`（异常/5xx）**必须分开记**：
    限流档里 blocked 是**目标**、业务失败是**预期**，都不能当成故障。"""

    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.lat: List[float] = []
        self.ok = 0
        self.blocked = 0
        self.hard_fail = 0
        self.by_state: Counter = Counter()
        self.by_http: Counter = Counter()
        self.err: Counter = Counter()

    def add(self, state: Optional[int], http: Optional[int], elapsed: float) -> None:
        with self.lock:
            self.lat.append(elapsed)
            if http is not None and http >= 500:
                self.hard_fail += 1
            if state in (200, 20000):
                self.ok += 1
            elif state == BLOCKED_STATE:
                self.blocked += 1
            else:
                self.by_state[str(state)] += 1
            self.by_http[str(http)] += 1

    def add_error(self, kind: str) -> None:
        with self.lock:
            self.err[kind] += 1
            self.hard_fail += 1

    def snapshot(self) -> Tuple[int, int, int, List[float]]:
        with self.lock:
            bad = sum(self.by_state.values()) + sum(self.err.values())
            return self.ok, self.blocked, bad, list(self.lat)


def pct(sorted_vals: List[float], p: float) -> float:
    if not sorted_vals:
        return 0.0
    k = max(0, min(len(sorted_vals) - 1, int(math.ceil(p / 100.0 * len(sorted_vals))) - 1))
    return sorted_vals[k]


# =============================================================================
# 通用加载器（两档共用：进度打印 / 安全阈值 / 统计汇总）
# =============================================================================

def _run_load(label: str, concurrency: int, duration: float, stop_flag: threading.Event,
              hit: Callable[[Stats, random.Random, int], None],
              abort_on_hard_only: bool = False, abort_pct: float = 20.0) -> Dict[str, Any]:
    stats = Stats()
    end_at = time.perf_counter() + duration

    def worker(wid: int) -> None:
        rnd = random.Random(1000 + wid)
        while time.perf_counter() < end_at and not stop_flag.is_set():
            try:
                hit(stats, rnd, wid)
            except Exception as e:                     # noqa: BLE001
                stats.add_error(type(e).__name__)
                log(f"   ⚠️ 线程 {wid} 异常：{e}")

    log(f"▶ {label}：并发 {concurrency} 开始（持续 {duration:.0f}s）")
    threads = [threading.Thread(target=worker, args=(i,), daemon=True) for i in range(concurrency)]
    t0 = time.perf_counter()
    for t in threads:
        t.start()

    while time.perf_counter() < end_at and not stop_flag.is_set():
        time.sleep(1.0)
        ok, blocked, bad, _ = stats.snapshot()
        secs = max(0.001, time.perf_counter() - t0)
        done = ok + blocked + bad
        pct_bad = (bad / done * 100.0) if done else 0.0
        extra = f"  被限流 {blocked:6d}" if blocked else ""
        print(f"    [{label} {concurrency:3d}] {secs:5.1f}s  RPS={done / secs:7.1f}"
              f"  成功 {ok:6d}  失败 {bad:5d}（{pct_bad:4.1f}%）{extra}", flush=True)
        # 安全阈值：浏览档看"失败率"，限流档只看"硬失败"（blocked 与业务失败都是预期）
        if done >= 50:
            ratio = (stats.hard_fail / done * 100.0) if abort_on_hard_only else pct_bad
            if ratio > abort_pct:
                log(f"   🔴 {'硬失败' if abort_on_hard_only else '失败'}率 {ratio:.1f}% > {abort_pct}%"
                    f" → **本阶梯立即中止**（安全阈值）")
                stop_flag.set()
                break

    for t in threads:
        t.join(timeout=5)
    elapsed = max(0.001, time.perf_counter() - t0)

    ok, blocked, bad, lat = stats.snapshot()
    lat_sorted = sorted(lat)
    done = ok + blocked + bad
    series = [v for v in lat_sorted if v]           # 延迟只看已完成请求（异常无耗时）
    result = {
        "label": label,
        "concurrency": concurrency,
        "duration_s": round(elapsed, 1),
        "requests": done,
        "ok": ok,
        "blocked": blocked,                  # ← Sentinel 拦截数（block 曲线就是它）
        "blocked_pct": round(blocked / done * 100, 2) if done else 0.0,
        "failed": bad,
        "hard_fail": stats.hard_fail,
        "rps": round(done / elapsed, 1),
        "latency_ms": {
            "avg": round(statistics.mean(series) * 1000, 1) if series else 0.0,
            "p50": round(pct(series, 50) * 1000, 1),
            "p90": round(pct(series, 90) * 1000, 1),
            "p95": round(pct(series, 95) * 1000, 1),
            "p99": round(pct(series, 99) * 1000, 1),
            "max": round((series[-1] if series else 0) * 1000, 1),
        },
        "by_state": dict(stats.by_state),
        "by_http": dict(stats.by_http),
        "exceptions": dict(stats.err),
    }
    lm = result["latency_ms"]
    ok_pct = round(ok / done * 100, 2) if done else 0.0
    log(f"■ {label} {concurrency} 并发结束：RPS={result['rps']}  成功={ok_pct}%  "
        f"被限流={blocked}（{result['blocked_pct']}%）  p50={lm['p50']}ms  p99={lm['p99']}ms")
    if result["by_state"]:
        log(f"   非成功/非限流的 state 分布：{result['by_state']}")
    if result["exceptions"]:
        log(f"   异常分布：{result['exceptions']}")
    return result


def step_browse(api: Api, concurrency: int, duration: float, tokens: List[str],
                think: float, stop_flag: threading.Event, spu_ids: List[int]) -> Dict[str, Any]:
    def hit(s: Stats, rnd: random.Random, wid: int) -> None:
        tok = tokens[(wid + rnd.randint(0, len(tokens) - 1)) % len(tokens)]
        if rnd.random() < 0.5:
            state, http, _b, el = api.spu_list_all(rnd.randint(1, 3), 10, tok)
        else:
            state, http, _b, el = api.spu_detail(rnd.choice(spu_ids), tok)
        s.add(state, http, el)
        if think > 0:
            time.sleep(rnd.uniform(0, think))

    return _run_load("浏览档 pass", concurrency, duration, stop_flag, hit)


def step_limit(api: Api, target: str, concurrency: int, duration: float,
               targets: List[Tuple[str, Any]], stop_flag: threading.Event) -> Dict[str, Any]:
    """限流档：打有流控规则的接口，**blocked（state=429）就是我们要的曲线**"""
    cfg = LIMIT_TARGETS[target]

    def hit(s: Stats, rnd: random.Random, wid: int) -> None:
        tok, arg = targets[(wid + rnd.randint(0, len(targets) - 1)) % len(targets)]
        if target == "pay":
            state, http, _b, el = api.order_pay(int(arg), tok)
        elif target == "adminlogin":
            state, http, _b, el = api.admin_login()
        else:
            raise RuntimeError(f"未支持的 target: {target}")
        s.add(state, http, el)

    label = f"限流档 {cfg['resource']}(QPS {cfg['rule_qps']})"
    return _run_load(label, concurrency, duration, stop_flag, hit,
                     abort_on_hard_only=True, abort_pct=5.0)


# =============================================================================
# 自检（--check）
# =============================================================================

def do_check(api: Api, n_users: int, mode: str, target: str) -> bool:
    log("=" * 78)
    log(f"自检模式（--check）：只发少量请求，不做压测（mode={mode}）")
    log("=" * 78)
    ok_all = True

    state, http, body, _ = api.spu_list_all(1, 2, "")
    good = state not in (200, 20000)
    ok_all = ok_all and good
    log(f"1) 不带 token 访问 /front/spu/list/all → HTTP {http} · state={state} · "
        f"{'✅ 按预期判为未登录' if good else '🔴 异常：竟然成功'}")
    log(f"   body: {json.dumps(body, ensure_ascii=False)[:110]}")

    tokens = acquire_tokens(api, n_users)
    if not tokens:
        log("🔴 没拿到 token → 检查造数是否已建 testsim* 用户 / 密码 / mall-sso 是否在跑")
        return False

    spu_ids = discover_spu_ids(api, tokens[0])
    log(f"2) 自动发现真实 SPU id：{len(spu_ids)} 个" + (f" → {spu_ids[:6]}…" if spu_ids else " 🔴"))
    ok_all = ok_all and bool(spu_ids)

    if mode == "browse":
        for n, (name, call) in enumerate(
                [("/front/spu/list/all", lambda t: api.spu_list_all(1, 2, t))]
                + ([ (f"/front/spu/{spu_ids[0]}", lambda t: api.spu_detail(spu_ids[0], t)) ] if spu_ids else []),
                start=3):
            state, http, body, el = call(tokens[0])
            g = state in (200, 20000)
            ok_all = ok_all and g
            log(f"{n}) {name} → HTTP {http} · state={state} · {el * 1000:.0f}ms · {'✅' if g else '🔴'}")
    else:
        cfg = LIMIT_TARGETS[target]
        log(f"3) 限流档靶子：resource=**{cfg['resource']}** 规则 QPS={cfg['rule_qps']}（{cfg['who']}）")
        log(f"   端点 {cfg['path']} —— {cfg['note']}")
        if target == "pay":
            orders = discover_orders(api, tokens)
            log(f"   发现可压订单：**{len(orders)} 个**（token+orderId 组合）")
            if not orders:
                log("   🔴 没有订单可压 → 先跑一次造数（simulate_data.py）产出订单")
                ok_all = False
            else:
                # 试打一发，看真实返回（应失败于"已支付/幂等"，但 **state 会被如实记录**）
                state, http, body, el = api.order_pay(orders[0][1], orders[0][0])
                log(f"   试打一发 POST /oms/order/pay → state={state} · {el * 1000:.0f}ms · "
                    f"msg={str(body.get('message'))[:60]}")
                est = len(orders) / 10.0
                log(f"   ⚠️ 可用 key 数 {len(orders)}（@Idempotent expire=10s）→ 理论上限 ≈ **{est:.1f} req/s**，"
                    f"规则要求 **{cfg['rule_qps']} QPS** → "
                    f"{'🔴 压不出 block（key 不够）' if est < cfg['rule_qps'] else '✅ 有可能压出 block'}")
        elif target == "adminlogin":
            state, http, body, el = api.admin_login()
            log(f"   试打一发 POST /admin/sso/login（假账号 {FAKE_ADMIN_USER}）→ state={state} · "
                f"{el * 1000:.0f}ms · msg={str(body.get('message'))[:60]}")
            log("   ℹ️ 该账号必然登录失败（这是设计），但**请求已计入 Sentinel 的 `adminLogin` 资源** → "
                "只要 RPS 超过 10 就会出现 state=429")

    log("=" * 78)
    log("自检结论：" + ("✅ 通过 —— 可以录像（§6.4：先录 30~60s 静默基线再执行压测命令）"
                        if ok_all else "🔴 有项目未通过，先看上面日志"))
    log("📌 面板可见性提醒（#66）：只有 **mall-order / mall-seckill / mall-sso** 配了 Sentinel dashboard 地址，")
    log("   `mall-front` **不上报** → 浏览档的 pass 曲线请看 **SkyWalking**；限流档请用 order/seckill/sso 的靶子。")
    log("=" * 78)
    return ok_all


# =============================================================================
# main
# =============================================================================

def main() -> None:
    ap = argparse.ArgumentParser(description="CoolShark #48 可观测展示压测脚本（浏览 pass / 限流 block 两档）")
    ap.add_argument("--mode", choices=("browse", "limit"), default="browse",
                    help="browse=只读浏览（pass 曲线）；limit=打有流控规则的接口（block 曲线）")
    ap.add_argument("--target", choices=tuple(LIMIT_TARGETS), default="adminlogin",
                    help="limit 档的靶子（默认 adminlogin：能真正压出 block 且零业务数据）")
    ap.add_argument("--steps", default="20,50,100", help="并发阶梯（browse 默认 20,50,100）")
    ap.add_argument("--duration", type=float, default=45.0, help="每档持续秒数")
    ap.add_argument("--users", type=int, default=20, help="用前 N 个模拟用户登录拿 token")
    ap.add_argument("--think", type=float, default=0.0, help="每请求后随机 sleep 0~think 秒")
    ap.add_argument("--warmup", type=float, default=5.0, help="正式阶梯前的预热秒数")
    ap.add_argument("--gap", type=float, default=2.0, help="阶梯之间的停顿秒数")
    ap.add_argument("--wait", type=float, default=0.0, help="开跑前等待秒数（对齐录像）")
    ap.add_argument("--base", default=BASE, help=f"网关地址（默认 {BASE}，必须走内网）")
    ap.add_argument("--json", help="结果写入 JSON（便于回填 sim_batch.note / 结果表）")
    ap.add_argument("--check", action="store_true", help="只自检，不压测")
    args = ap.parse_args()

    if requests is None:
        sys.exit(DEPS_HINT)

    api = Api(args.base)
    if args.check:
        sys.exit(0 if do_check(api, args.users, args.mode, args.target) else 1)

    steps = [int(s) for s in str(args.steps).replace(" ", "").split(",") if s]
    if not steps:
        sys.exit("--steps 解析为空")

    cfg = LIMIT_TARGETS.get(args.target, {})
    log("=" * 78)
    if args.mode == "browse":
        log(f"CoolShark #48 压测 · **浏览档（pass 曲线）** · 只读 · base={args.base}")
    else:
        log(f"CoolShark #48 压测 · **限流档（block 曲线）** · 靶子={args.target}"
            f"（resource={cfg.get('resource')} · 规则 QPS={cfg.get('rule_qps')}）")
    log(f"阶梯={steps}  每档 {args.duration:.0f}s  预热 {args.warmup:.0f}s  用户 {args.users}")
    log("⚠️ 两档结论不能混着讲：浏览档 = 流量变化（pass）；限流档 = 限流生效（block）")
    log("⚠️ 必须在新机跑（老机 5Mbps 自压自伤）；脚本走私网、不占公网带宽")
    log("=" * 78)

    if args.wait > 0:
        log(f"⏳ {args.wait:.0f}s 后开始 —— **现在就开录**（先录 30~60s 静默基线，§6.4 ①）")
        time.sleep(args.wait)
    else:
        log("提示：先把三个窗口并排就位、录 30~60s **静默基线**，再执行本脚本（§6.4 ①）")

    tokens = acquire_tokens(api, args.users)
    if not tokens:
        sys.exit("🔴 没有可用 token → 先跑 `python3 simulate_data.py --preflight` 确认造数已建用户")

    stop_flag = threading.Event()
    if args.mode == "browse":
        spu_ids = discover_spu_ids(api, tokens[0])
        if not spu_ids:
            sys.exit("🔴 没发现任何真实 SPU id → 商品数据异常，先跑 `--check`")
        log(f"已发现 {len(spu_ids)} 个真实 SPU id（详情只打这些，避免把不存在的 id 算成失败）")

        def one(c: int, d: float) -> Dict[str, Any]:
            return step_browse(api, c, d, tokens, args.think, stop_flag, spu_ids)
    else:
        if args.target == "pay":
            targets: List[Tuple[str, Any]] = discover_orders(api, tokens)
            if not targets:
                sys.exit("🔴 没有订单可压 → 先跑一次造数产出订单")
            est = len(targets) / 10.0
            log(f"已发现 {len(targets)} 个可压订单（token+orderId）；@Idempotent 理论上限 ≈ {est:.1f} req/s，"
                f"规则要求 {cfg['rule_qps']} QPS → "
                + ("🔴 可能压不出 block（key 不够）" if est < cfg["rule_qps"] else "✅ 有可能压出 block"))
        else:
            targets = [("", None)]          # adminlogin 不需要 token
            log(f"限流档靶子 {cfg['resource']}：用假账号 {FAKE_ADMIN_USER} 打 {cfg['path']}"
                f"（必然登录失败，但请求会计入 Sentinel）")

        def one(c: int, d: float) -> Dict[str, Any]:
            return step_limit(api, args.target, c, d, targets, stop_flag)

    if args.warmup > 0:
        log(f"预热 {args.warmup:.0f}s（结果不计入统计）…")
        one(max(2, min(steps)), args.warmup)
        time.sleep(1)

    results: List[Dict[str, Any]] = []
    t_start = time.perf_counter()
    for idx, c in enumerate(steps, 1):
        if stop_flag.is_set():
            log("已中止，跳过后续阶梯")
            break
        log(f"—— 阶梯 {idx}/{len(steps)}：{c} 并发 ——")
        results.append(one(c, args.duration))
        if idx != len(steps):
            time.sleep(args.gap)
    total_s = time.perf_counter() - t_start

    log("=" * 78)
    log("📊 阶梯汇总（可直接贴进 sim_batch.note 或结果表）")
    log("-" * 78)
    log(f"{'并发':>5} {'RPS':>9} {'成功':>7} {'被限流':>8} {'限流%':>8} {'其他失败':>9} "
        f"{'p50':>9} {'p95':>9} {'p99':>9}")
    for r in results:
        lm = r["latency_ms"]
        log(f"{r['concurrency']:>5} {r['rps']:>9} {r['ok']:>7} {r['blocked']:>8} "
            f"{r['blocked_pct']:>7.2f}% {r['failed']:>9} "
            f"{lm['p50']:>8}ms {lm['p95']:>8}ms {lm['p99']:>8}ms")
    log("-" * 78)
    log(f"总耗时 {total_s:.0f}s")
    if args.mode == "limit":
        tot_blocked = sum(r["blocked"] for r in results)
        log(f"🎯 限流档要点：**被限流合计 {tot_blocked} 次** —— 这就是 Sentinel 的 block 曲线来源")
        if tot_blocked == 0:
            log("   ⚠️ 一次都没被限流 → RPS 没超过规则阈值，或该靶子被其它闸门（如 @Idempotent）先拦住了")
    else:
        log("🎬 录像收尾（§6.4 ④）：停压 → 录曲线回落 → 打开 SkyWalking Trace 看那一批请求的真实链路")
    log("=" * 78)

    if args.json:
        payload = {
            "generated_at": dt.datetime.now().isoformat(timespec="seconds"),
            "mode": args.mode, "target": args.target if args.mode == "limit" else None,
            "base": args.base, "steps": steps, "duration_per_step": args.duration,
            "users": len(tokens), "think": args.think,
            "results": results, "total_seconds": round(total_s, 1),
            "note": "browse=只读浏览(需 token)；limit 默认 adminlogin=打有流控规则的接口看 block",
        }
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        log(f"结果已写入 {args.json}")


if __name__ == "__main__":
    main()
