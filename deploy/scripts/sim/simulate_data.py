#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
simulate_data.py — CoolShark 业务数据模拟器（TODO #48 · 第一层「造数」骨架）

设计依据：docs/评估报告/Python模拟数据与AI并发测试方案.md
          （规范设计定稿 09-10 + **实施前复核 09-11 §〇.1 D1~D9**）

核心原则（缺一不可）
  1. **登记驱动**：每创建一个实体 → 往 cs_mall_sim.sim_entity 写一行；清理只认登记表，不靠 LIKE 猜
  2. **逆序清理**：子表 → 主表（全库 0 个外键，漏一张就静默残留）；分批 500；默认 dry-run
  3. **不可逆字段不算**：pms_spu.sales / pms_sku.stock / Redis 秒杀预热键 → 交快照整库还原兜底
  4. **fail-fast 预检**：库在不在 / 前缀是否为 0 / 库存够不够 / 秒杀是否可用 → 不通过直接退出
  5. **凭据不落盘**：DB 密码只从环境变量取（§〇.1 D6）

用法（详见同目录 README.md）
  export SIM_DB_PASSWORD='...'
  python simulate_data.py --preflight --days 2 --per-day 1000     # 只做预检，不写任何数据
  python simulate_data.py --days 2 --per-day 1000                 # 造数（登记每个实体）
  python simulate_data.py --clean --batch sim_20260911_1530       # 清理预览（默认 dry-run）
  python simulate_data.py --clean --batch sim_20260911_1530 --apply

⚠️ 本脚本只做「第一层：造数」。AI 并发压测（第二层）与 mock LLM 见方案文档 §3.5。
"""

from __future__ import annotations

import argparse
import datetime as dt
import os
import random
import re
import sys
import time
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple

try:
    import requests
except ImportError:  # 延迟到 main() 里报错，保证 `--help` 在没装依赖时也能用
    requests = None  # type: ignore[assignment]
try:
    import pymysql
except ImportError:
    pymysql = None  # type: ignore[assignment]

DEPS_HINT = ("缺少依赖：请先 `python3 -m venv ~/sim-venv && "
             "~/sim-venv/bin/pip install -r requirements.txt`（见 §〇.1 D6）")


# =============================================================================
# 配置（全部可用环境变量覆盖；密码必须来自环境变量 —— §〇.1 D6）
# =============================================================================

BASE = os.environ.get("SIM_BASE", "http://172.29.193.239:10087").rstrip("/")  # ⚠️ 内网 + Gateway

USER_PREFIX = os.environ.get("SIM_USER_PREFIX", "test_sim_")
USER_PASSWORD = os.environ.get("SIM_USER_PASSWORD", "Sim123456")
EMAIL_DOMAIN = os.environ.get("SIM_EMAIL_DOMAIN", "example.com")
# 明显是假号段（139 + 8 位），既能过手机号正则，又不会撞到真实号码
FAKE_PHONE_PREFIX = os.environ.get("SIM_PHONE_PREFIX", "1390000")

# 资源站前缀：pictures 存的是**相对文件名**，需拼成完整 URL（实测既有订单项就是这个形态）
SIM_RESOURCE_HOST = os.environ.get("SIM_RESOURCE_HOST", "http://8.156.77.197/")

SIM_DB = dict(
    host=os.environ.get("SIM_DB_HOST", "172.29.193.239"),
    port=int(os.environ.get("SIM_DB_PORT", "3306")),
    user=os.environ.get("SIM_DB_USER", "root"),
    password=os.environ.get("SIM_DB_PASSWORD", ""),
    database="cs_mall_sim",
    charset="utf8mb4",
    autocommit=False,
)

# 漏斗：浏览 80% / 加购 15% / 下单支付 5%（方案 §2.1）
FUNNEL = (("browse", 0.80), ("cart", 0.15), ("order", 0.05))

# 清理顺序（严格逆序：子 → 父）。mode: pk=按登记主键 / user=按登记用户 / order_child=按登记的 order_id 反查
CLEAN_ORDER: Sequence[Tuple[str, str, str, str]] = (
    ("cs_mall_oms", "oms_order_item", "order_child", "order_id"),
    ("cs_mall_seckill", "success", "pk", "id"),
    ("cs_mall_seckill", "seckill_message_retry", "pk", "id"),
    ("cs_mall_oms", "oms_payment_record", "pk", "id"),
    ("cs_mall_oms", "oms_cart", "pk", "id"),
    ("cs_mall_oms", "oms_order", "pk", "id"),
    ("cs_mall_resource", "res_upload_record", "pk", "id"),
    ("cs_mall_ums", "ums_login_log", "user", "user_id"),
    ("cs_mall_ums", "ums_user", "pk", "id"),
)

CHUNK = 500


# =============================================================================
# 小工具
# =============================================================================

def log(msg: str) -> None:
    print(f"[{dt.datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)


def db(**overrides):
    cfg = dict(SIM_DB)
    cfg.update(overrides)
    if not cfg.get("password"):
        sys.exit("未设置 SIM_DB_PASSWORD 环境变量（凭据不落盘，见 §〇.1 D6）")
    cfg["cursorclass"] = pymysql.cursors.DictCursor
    return pymysql.connect(**cfg)


def first_picture(pictures: Optional[str]) -> str:
    """取商品第一张图。

    实测（2026-09-11）：`pms_sku.pictures` / `pms_spu.pictures` 存的是 **JSON 数组字符串、
    内容是相对文件名**（如 `["spu_1_1.jpg", "spu_1_2.jpg"]`，有的逗号后带空格），
    而 `oms_order_item.picture_url` 里**两种形态都有**：
      * 新单：`http://8.156.77.197/spu_2_1.jpg`（完整 URL ← 采用这个）
      * 旧单：`spu_2_1.jpg`（裸文件名）
    → 本函数统一产出"完整 URL"，与资源站（`RESOURCE_HOST`）拼接，避免前端裂图。
    """
    if not pictures:
        return ""
    m = re.search(r"https?://[^\s\"',\]]+", pictures)
    if m:
        return m.group(0)
    names = re.findall(r"\"([^\"]+)\"", pictures) or [p.strip() for p in pictures.split(",") if p.strip()]
    name = names[0].strip() if names else ""
    if not name:
        return ""
    if name.startswith("http"):
        return name
    return SIM_RESOURCE_HOST.rstrip("/") + "/" + name.lstrip("/")


# =============================================================================
# 影子登记
# =============================================================================

class Registry:
    """sim_entity 的写入口 —— 每个被创建的实体都必须过这里"""

    def __init__(self, batch_id: str, enabled: bool = True):
        self.batch_id = batch_id
        self.enabled = enabled
        self.count = 0

    def register(self, conn, table: str, pk: Any, db_name: str, user_ref: Optional[str] = None) -> None:
        # 关键点：登记与业务写入**共用同一个连接/事务**，要么都成要么都不成
        with conn.cursor() as cur:
            cur.execute(
                "INSERT INTO cs_mall_sim.sim_entity"
                "(batch_id, db_name, table_name, pk_value, user_ref, created_at)"
                " VALUES (%s, %s, %s, %s, %s, NOW())",
                (self.batch_id, db_name, table, str(pk), user_ref),
            )
        self.count += 1


# =============================================================================
# HTTP API（端点已按 2026-09-11 读码核对；见 README「端点事实来源」）
# =============================================================================

class Api:
    def __init__(self, base: str = BASE, timeout: int = 20):
        self.base = base
        self.timeout = timeout
        self.s = requests.Session()

    def _call(self, method: str, path: str, token_header: Optional[str] = None, **kw):
        headers = kw.pop("headers", {})
        if token_header:
            headers["Authorization"] = token_header
        r = self.s.request(method, self.base + path, headers=headers, timeout=self.timeout, **kw)
        try:
            body = r.json()
        except ValueError:
            raise RuntimeError(f"{method} {path} 非 JSON 响应：HTTP {r.status_code} {r.text[:200]}")
        state = body.get("state")
        if state not in (200, 20000):
            raise RuntimeError(f"{method} {path} 业务失败：state={state} message={body.get('message')}")
        return body.get("data")

    # --- 认证 ---
    def register_user(self, username: str, nickname: str, email: str, phone: str, password: str) -> Dict[str, Any]:
        """POST /ums/user/register —— 建**模拟用户**（必须能被清理，所以走登记）"""
        return self._call("POST", "/ums/user/register", json={
            "username": username, "nickname": nickname, "email": email,
            "phone": phone, "password": password, "ackPassword": password,
        })

    def login(self, username: str, password: str) -> str:
        """POST /user/sso/login —— 返回可直接用于 Authorization 头的串"""
        data = self._call("POST", "/user/sso/login", json={"username": username, "password": password})
        return f"{data.get('tokenHeader') or 'Bearer '}{data.get('tokenValue')}"

    # --- 浏览（80%，无需登录）---
    def spu_list_all(self, page: int = 1, page_size: int = 10):
        return self._call("GET", "/front/spu/list/all", params={"page": page, "pageSize": page_size})

    def spu_detail(self, spu_id: int):
        return self._call("GET", f"/front/spu/{spu_id}")

    # --- 加购 / 下单 ---
    def cart_add(self, token_header: str, sku: Dict[str, Any], qty: int):
        return self._call("POST", "/oms/cart/add", token_header=token_header, json={
            "skuId": sku["sku_id"], "title": sku["title"],
            "mainPicture": sku["picture"], "price": float(sku["price"]), "quantity": qty,
        })

    def order_add(self, token_header: str, sku: Dict[str, Any], qty: int, address: Dict[str, str]):
        price = float(sku["price"])
        total = round(price * qty, 2)
        return self._call("POST", "/oms/order/add", token_header=token_header, json={
            "contactName": f"模拟用户{random.randint(1000, 9999)}",   # 随机 → 避免 @Idempotent key 冲突
            "mobilePhone": FAKE_PHONE_PREFIX + f"{random.randint(0, 9999):04d}",
            "provinceCode": address["province_code"], "provinceName": address["province_name"],
            "cityCode": address["city_code"], "cityName": address["city_name"],
            "districtCode": address["district_code"], "districtName": address["district_name"],
            "streetCode": address["street_code"], "streetName": address["street_name"],
            "detailedAddress": f"模拟地址 {random.randint(1, 999)} 号",
            "paymentType": 0, "orderType": 0,          # 0=银联（模拟支付）；orderType 服务端会强制置 0
            "amountOfOriginalPrice": total, "amountOfFreight": 0.0,
            "amountOfDiscount": 0.0, "amountOfActualPay": total,
            "orderItems": [{
                "skuId": sku["sku_id"], "title": sku["title"], "barCode": sku.get("bar_code") or "",
                "data": "{}", "mainPicture": sku["picture"], "price": price, "quantity": qty,
            }],
        })

    def order_pay(self, token_header: str, order_id: int):
        """POST /oms/order/pay —— 当前为**模拟支付**（不碰支付宝沙箱）"""
        return self._call("POST", "/oms/order/pay", token_header=token_header, json={
            "id": order_id, "paymentType": 0,
        })


# =============================================================================
# 目录与地址模板（直接读 MySQL —— 比猜 VO 结构准确，且这正是脚本需要 DB 权限的原因）
# =============================================================================

CATALOG_SQL = """
SELECT s.id AS sku_id, s.spu_id, s.title, s.price, s.stock, s.bar_code, s.pictures,
       p.name AS spu_name, p.category_id, p.sales AS spu_sales
FROM cs_mall_pms.pms_sku s
JOIN cs_mall_pms.pms_spu p ON p.id = s.spu_id
WHERE s.stock > 0 AND p.is_deleted = 0 AND p.is_published = 1
ORDER BY s.stock DESC
"""

ADDRESS_SQL = """
SELECT province_code, province_name, city_code, city_name,
       COALESCE(street_code, '') AS street_code, street_name,
       district_code, district_name
FROM cs_mall_oms.oms_order
WHERE province_code IS NOT NULL AND city_code IS NOT NULL AND street_name IS NOT NULL
ORDER BY id DESC LIMIT 1
"""


def load_catalog(conn) -> List[Dict[str, Any]]:
    with conn.cursor() as cur:
        cur.execute(CATALOG_SQL)
        rows = list(cur.fetchall())
    for r in rows:
        r["price"] = float(r["price"])
        r["picture"] = first_picture(r.get("pictures"))
    return rows


def load_address_template(conn) -> Dict[str, str]:
    """从一笔已有订单抄一份**合法**的省市区街道编码，避免自己编造被校验拒绝"""
    with conn.cursor() as cur:
        cur.execute(ADDRESS_SQL)
        row = cur.fetchone()
    if not row:
        raise RuntimeError("库里没有任何可用地址样本 → 无法构造合法下单地址，请先手工下过一单")
    return row


# =============================================================================
# 预检（fail-fast：任一不过直接退出 —— §〇.1 D7 / §六 清单）
# =============================================================================

def preflight(conn, days: int, per_day: int, need_seckill: bool) -> List[Dict[str, Any]]:
    problems: List[str] = []

    with conn.cursor() as cur:
        # 1) 影子库/表就位
        cur.execute("SELECT COUNT(*) AS c FROM information_schema.tables "
                    "WHERE table_schema='cs_mall_sim' AND table_name IN ('sim_batch','sim_entity')")
        if cur.fetchone()["c"] != 2:
            problems.append("cs_mall_sim 或两张登记表不存在 → 先执行 init_sim_db.sql")

        # 2) 基线干净（前缀用户必须为 0）
        cur.execute("SELECT COUNT(*) AS c FROM cs_mall_ums.ums_user WHERE username LIKE %s", (USER_PREFIX + "%",))
        users = cur.fetchone()["c"]
        if users:
            problems.append(f"基线不干净：已有 {users} 个 {USER_PREFIX}* 用户 → 先清理上一批")

        # 3) 库存预算（§〇.1 D7：库存是硬约束）
        cur.execute("SELECT COUNT(*) AS c, COALESCE(SUM(stock),0) AS s FROM cs_mall_pms.pms_sku WHERE stock > 0")
        row = cur.fetchone()
        expected_orders = int(days * per_day * 0.05)
        need_units = expected_orders * 2                      # 平均每单 2 件（保守估计）
        log(f"库存：{row['c']} 个可用 SKU / 合计 {row['s']} 件；本批预计 {expected_orders} 单 ≈ 需 {need_units} 件")
        if row["s"] < need_units:
            problems.append(f"库存不足：可用 {row['s']} 件 < 预计需要 {need_units} 件 → 调小 --per-day/--days")

        # 4) 地址模板（下单必需）
        try:
            load_address_template(conn)
        except Exception as e:                                # noqa: BLE001
            problems.append(f"地址模板不可用：{e}")

        # 5) 目录非空
        if not load_catalog(conn):
            problems.append("没有可用的在售 SKU（stock>0 且已上架）")

        # 6) 秒杀（可选路径）—— 当前 6 场全年有效，但预热键是按需生成的
        if need_seckill:
            cur.execute("SELECT COUNT(*) AS c FROM cs_mall_seckill.seckill_sku WHERE seckill_stock > 0")
            if cur.fetchone()["c"] == 0:
                problems.append("没有可用的秒杀 SKU（seckill_stock > 0）→ 去掉 --with-seckill")

    if problems:
        log("❌ 预检未通过：")
        for p in problems:
            log(f"   - {p}")
        sys.exit(2)

    catalog = load_catalog(conn)
    log(f"✅ 预检通过：{len(catalog)} 个在售 SKU 可用；基线 {USER_PREFIX}* = 0")
    return catalog


# =============================================================================
# 造数
# =============================================================================

def ensure_users(api: Api, conn, registry: Registry, n_users: int) -> List[Dict[str, Any]]:
    """建模拟用户：**先建一个并校验成功**，再批量 —— 用户名/密码正则若不合规要当场暴露"""
    users: List[Dict[str, Any]] = []
    for i in range(1, n_users + 1):
        username = f"{USER_PREFIX}{i:04d}"
        phone = FAKE_PHONE_PREFIX + f"{i:04d}"
        data = api.register_user(username, f"模拟用户{i:04d}", f"{username}@{EMAIL_DOMAIN}", phone, USER_PASSWORD)
        uid = data.get("id") if isinstance(data, dict) else None
        if not uid:
            raise RuntimeError(f"注册接口未返回用户 id，无法登记（返回={data}）→ 先核对 UserRegistryDTO 字段")
        registry.register(conn, "ums_user", uid, "cs_mall_ums", user_ref=str(uid))
        conn.commit()
        token = api.login(username, USER_PASSWORD)
        users.append({"id": uid, "username": username, "token": token})
        if i == 1:
            log(f"✅ 首个模拟用户可用：{username} (id={uid})")
    log(f"已建 {len(users)} 个模拟用户（全部登记）")
    return users


def run_funnel(api: Api, conn, registry: Registry, catalog, users, days: int, per_day: int,
               sleep_min: float, sleep_max: float, with_seckill: bool) -> Dict[str, int]:
    stat = {"browse": 0, "cart": 0, "order": 0, "fail": 0}
    actions = days * per_day
    log(f"开始造数：{days} 天 × {per_day} 行为 = {actions} 次动作（慢节奏 {sleep_min}~{sleep_max}s）")
    for n in range(1, actions + 1):
        r, acc = random.random(), 0.0
        kind = "order"
        for name, p in FUNNEL:
            acc += p
            if r <= acc:
                kind = name
                break
        user = random.choice(users)
        sku = random.choice(catalog)
        try:
            if kind == "browse":                      # 80%：只读，不产生业务数据
                (api.spu_detail if random.random() < 0.5 else
                 (lambda sid: api.spu_list_all(random.randint(1, 3), 10)))(sku["spu_id"])
            elif kind == "cart":                      # 15%：加购但不结算
                api.cart_add(user["token"], sku, random.randint(1, 2))
                with conn.cursor() as cur:            # 取回刚插入的 cartId 并登记
                    cur.execute("SELECT id FROM cs_mall_oms.oms_cart WHERE user_id=%s ORDER BY id DESC LIMIT 1",
                                (user["id"],))
                    row = cur.fetchone()
                if row:
                    registry.register(conn, "oms_cart", row["id"], "cs_mall_oms", user_ref=str(user["id"]))
                    conn.commit()
            else:                                     # 5%：完整下单 + 模拟支付
                vo = api.order_add(user["token"], sku, 1, load_address_template(conn))
                if not isinstance(vo, dict) or "id" not in vo:
                    raise RuntimeError(f"下单响应里没有订单 id，无法登记（返回={vo}）→ 核对 OrderAddVO 字段")
                oid = vo["id"]
                registry.register(conn, "oms_order", oid, "cs_mall_oms", user_ref=str(user["id"]))
                with conn.cursor() as cur:            # 订单项按 order_id 登记（它自己没有 user_id）
                    cur.execute("SELECT id FROM cs_mall_oms.oms_order_item WHERE order_id=%s", (oid,))
                    for it in cur.fetchall():
                        registry.register(conn, "oms_order_item", it["id"], "cs_mall_oms", user_ref=str(user["id"]))
                conn.commit()
                api.order_pay(user["token"], int(oid))
            stat[kind] += 1
        except Exception as e:                        # noqa: BLE001
            stat["fail"] += 1
            if stat["fail"] <= 20:                    # 只打前 20 条错误，避免刷屏
                log(f"   ⚠️ 动作失败（{kind}）：{e}")
        if n % 50 == 0:
            log(f"   进度 {n}/{actions}：浏览 {stat['browse']} / 加购 {stat['cart']} / 下单 {stat['order']} / 失败 {stat['fail']}")
        time.sleep(random.uniform(sleep_min, sleep_max))
    if with_seckill:
        log("⚠️ --with-seckill 未实现（随机码需先从 /seckill/spu/list 取回，见 README「待补」）→ 本次跳过")
    return stat


# =============================================================================
# 清理：逆序 · 分批 · 幂等 · 默认 dry-run
# =============================================================================

def _registered(conn, batch: str, db_name: str, table: str) -> List[str]:
    with conn.cursor() as cur:
        cur.execute("SELECT pk_value FROM cs_mall_sim.sim_entity "
                    "WHERE batch_id=%s AND db_name=%s AND table_name=%s", (batch, db_name, table))
        return [r["pk_value"] for r in cur.fetchall()]


def _registered_users(conn, batch: str) -> List[str]:
    with conn.cursor() as cur:
        cur.execute("SELECT DISTINCT user_ref FROM cs_mall_sim.sim_entity "
                    "WHERE batch_id=%s AND user_ref IS NOT NULL", (batch,))
        return [r["user_ref"] for r in cur.fetchall()]


def _count(cur, table: str, column: str, values: Sequence[str]) -> int:
    cur.execute(f"SELECT COUNT(*) AS c FROM {table} WHERE {column} IN %s", (tuple(values),))
    return cur.fetchone()["c"]


def clean(conn, batch: str, apply_: bool) -> None:
    log(f"{'⚠️ APPLY（真删）' if apply_ else '🔍 DRY-RUN（只统计）'}：批次 {batch}")

    with conn.cursor() as cur:
        cur.execute("SELECT status FROM cs_mall_sim.sim_batch WHERE batch_id=%s", (batch,))
        row = cur.fetchone()
        if not row:
            sys.exit(f"批次不存在：{batch}")
        if row["status"] == "cleaned":
            log("该批次已标记 cleaned（幂等：删不到就跳过）")

    order_ids = _registered(conn, batch, "cs_mall_oms", "oms_order")
    user_ids = _registered_users(conn, batch)
    if not user_ids:
        user_ids = _registered(conn, batch, "cs_mall_ums", "ums_user")
    log(f"登记规模：order_id {len(order_ids)} 个 / user_id {len(user_ids)} 个")

    total = 0
    with conn.cursor() as cur:
        for db_name, table, mode, column in CLEAN_ORDER:
            full = f"{db_name}.{table}"
            if mode == "order_child":
                if not order_ids:
                    log(f"  {full:38s} 跳过（没有登记的 order_id）")
                    continue
                values = order_ids
            elif mode == "user":
                if not user_ids:
                    log(f"  {full:38s} 跳过（没有登记的 user_id）")
                    continue
                values = user_ids
            else:
                values = _registered(conn, batch, db_name, table)
                if not values:
                    log(f"  {full:38s} 无登记 → 跳过")
                    continue
            affected = 0
            for i in range(0, len(values), CHUNK):
                part = values[i:i + CHUNK]
                n = _count(cur, full, column, part)
                affected += n
                if apply_ and n:
                    conn.begin()
                    try:
                        cur.execute(f"DELETE FROM {full} WHERE {column} IN %s", (tuple(part),))
                        conn.commit()
                    except Exception:
                        conn.rollback()
                        raise
            total += affected
            log(f"  {full:38s} {'已删' if apply_ else '将删'} {affected} 行")

    with conn.cursor() as cur:
        if apply_:
            cur.execute("UPDATE cs_mall_sim.sim_batch SET status='cleaned', finished_at=NOW() WHERE batch_id=%s", (batch,))
            conn.commit()
    log(f"{'已删除' if apply_ else '将删除'}合计 {total} 行（分批 {CHUNK}/批，单批事务）")
    log("⚠️ 未处理**不可逆字段**（pms_spu.sales / pms_sku.stock / Redis 秒杀预热键）——"
        "需要绝对干净请走整库还原快照（方案 §2.2.3）；Redis 只按登记 user id 精确删，禁止 pattern 全删")


# =============================================================================
# main
# =============================================================================

def main() -> None:
    ap = argparse.ArgumentParser(description="CoolShark #48 业务数据模拟器（第一层：造数）")
    ap.add_argument("--days", type=int, default=1, help="模拟天数")
    ap.add_argument("--per-day", type=int, default=1000, help="每天行为数（80/15/5 漏斗）")
    ap.add_argument("--users", type=int, default=20, help="预置模拟用户数")
    ap.add_argument("--sleep-min", type=float, default=0.5, help="动作间隔下限（秒）")
    ap.add_argument("--sleep-max", type=float, default=3.0, help="动作间隔上限（秒）")
    ap.add_argument("--with-seckill", action="store_true", help="附加秒杀动作（当前未实现，仅预检）")
    ap.add_argument("--preflight", action="store_true", help="只做预检，不写任何数据")
    ap.add_argument("--clean", action="store_true", help="清理模式（默认 dry-run）")
    ap.add_argument("--batch", help="清理指定批次号")
    ap.add_argument("--apply", action="store_true", help="清理时真正执行 DELETE（默认只统计）")
    ap.add_argument("--base", default=BASE, help=f"网关地址（默认 {BASE}）")
    args = ap.parse_args()

    # 依赖检查放在 parse_args 之后：保证 `--help` 在没装依赖时也能用
    if requests is None or pymysql is None:
        sys.exit(DEPS_HINT)

    conn = db()
    try:
        if args.clean:
            if not args.batch:
                sys.exit("--clean 必须带 --batch sim_YYYYMMDD_HHMM")
            clean(conn, args.batch, args.apply)
            return

        catalog = preflight(conn, args.days, args.per_day, args.with_seckill)
        if args.preflight:
            log("--preflight 结束：未写入任何数据 ✅")
            return

        batch = "sim_" + dt.datetime.now().strftime("%Y%m%d_%H%M")
        registry = Registry(batch)
        with conn.cursor() as cur:
            cur.execute("INSERT INTO cs_mall_sim.sim_batch"
                        "(batch_id, started_at, days, per_day, status, note) VALUES (%s, NOW(), %s, %s, 'running', %s)",
                        (batch, args.days, args.per_day, f"dump_file=待填；base={args.base}"))
        conn.commit()
        log(f"批次 {batch} 已开（记得在造数**前**做 mysqldump 并把文件名写进 sim_batch.dump_file）")

        api = Api(args.base)
        users = ensure_users(api, conn, registry, args.users)
        stat = run_funnel(api, conn, registry, catalog, users, args.days, args.per_day,
                         args.sleep_min, args.sleep_max, args.with_seckill)

        with conn.cursor() as cur:
            cur.execute("UPDATE cs_mall_sim.sim_batch SET status='finished', finished_at=NOW(), "
                        "done_actions=%s, note=CONCAT(COALESCE(note,''), ' | ', %s) WHERE batch_id=%s",
                        (sum(stat.values()) - stat["fail"], json_stat(stat, registry), batch))
        conn.commit()
        log(f"✅ 造数结束（批次 {batch}）：{json_stat(stat, registry)}")
        log(f"清理预览：python {os.path.basename(__file__)} --clean --batch {batch}")
    finally:
        conn.close()


def json_stat(stat: Dict[str, int], registry: Registry) -> str:
    import json
    return json.dumps({"actions": stat, "registered_entities": registry.count}, ensure_ascii=False)


if __name__ == "__main__":
    main()
