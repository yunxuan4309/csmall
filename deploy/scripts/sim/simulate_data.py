#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
simulate_data.py — CoolShark 业务数据模拟器（TODO #48 · 第一层「造数」骨架）

设计依据：docs/评估报告/Python模拟数据与数据隔离方案.md
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

⚠️ 本脚本只做「第一层：造数」。AI 并发压测（第二层）与 mock LLM 见 docs/评估报告/AI并发测试方案.md §五（原 §3.5）。
"""

from __future__ import annotations

import argparse
import datetime as dt
import os
import random
import re
import socket
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

DEPS_HINT = ("缺少依赖：请先 `sudo apt-get install -y python3-pymysql`"
             "（🔴 免 venv —— 实测 ensurepip 缺失且无外网，venv 路径跑不通；"
             "见方案 §〇.1 D6「2026-09-11 晚修订」）")


# =============================================================================
# 配置（全部可用环境变量覆盖；密码必须来自环境变量 —— §〇.1 D6）
# =============================================================================

BASE = os.environ.get("SIM_BASE", "http://172.29.193.239:10087").rstrip("/")  # ⚠️ 内网 + Gateway

USER_PREFIX = os.environ.get("SIM_USER_PREFIX", "testsim")
# 🔴 2026-09-11 校准实测教训：服务端用户名校验是 `^[a-zA-Z]{1}[0-9a-zA-Z]{3,15}$`
#    （mall-common/src/main/java/com/cooxiao/mall/common/validation/RegExpressions.java:14）
#    → **只允许字母与数字**。原来的 `test_sim_` 含下划线 → 注册直接 400：
#      "用户名必须是由字母、数字组成的4~16字符，且第1个字符必须是字母！"
#    → 改为 `testsim`：生成 `testsim0001`（11 字符，全字母数字，首字符为字母）✅
USER_PASSWORD = os.environ.get("SIM_USER_PASSWORD", "Sim123456")
EMAIL_DOMAIN = os.environ.get("SIM_EMAIL_DOMAIN", "example.com")
# 假号段（**7 位前缀** + 4 位序号 = 11 位）—— 必须同时过服务端两条手机号正则：
#   注册口 `^1[34589][0-9]{9}$`   下单口 `^1(?:3\d|4[4-9]|…)\d{8}$`（都要求 11 位）
# 🔴 2026-09-11 实测踩坑：原默认 `1390000` 已被既有 `benchuser01..100` **占满 100 个**
#    （13900000001~13900000100）→ 注册直接 409「注册手机号已存在」。
#    实测空闲段：**1390009** / 1390090 / 1390100 / 1391111 / 1380000 / 1890000 / 1990000
#    → 默认改为 `1390009`（生成 13900090001…），并由 preflight 第 9 项做碰撞预检兜底。
FAKE_PHONE_PREFIX = os.environ.get("SIM_PHONE_PREFIX", "1390009")

# ===== 🏷️ 模拟数据标识：专用列 `data_source`（2026-09-11 定稿）=========================
# 目的：让模拟数据**自证身份** —— 单表等值查询即可看出"这行是造的"，不必 JOIN。
# 承载：**9 张业务表统一新增专用列** `data_source`（`NULL`=常规/真实 · `SIM`=模拟造数）。
#       ⚠️ 早先"借用 `oms_order.tag` / `oms_order_item.data`"的写法**已撤回**：
#          属语义借用（`tag` 本是展示标签、`data` 本是商品全属性 json），
#          且 `tag` 会在订单页显示成标签 → 同一行挂两种标识，反而更易混。
# 迁移：4 个 Flyway 迁移文件（ums V3 / oms V7 / seckill V6 / resource V2），见方案 §2.2.9。
#       🔴 只走迁移文件、**禁止手工 ALTER** —— 手工加列后再跑迁移会
#          `Duplicate column name` → Flyway 失败 → **应用启动失败**。
# 谁写：**服务端零改动** —— 值由本脚本**按影子登记表回填**（见下方 backfill()）。
# 追批：`data_source` 只表达"是不是造的"；**精确批次仍由 `cs_mall_sim.sim_entity` 反查**。
# ⚠️ 边界：**新增的商品数据视为真实商品、不打 SIM 标记**（见《商品与秒杀扩容方案》）。
DATA_SOURCE_SIM = os.environ.get("SIM_DATA_SOURCE", "SIM")

# 🔄 回填口径（2026-09-11 只读实测 `information_schema` 后确定；G7 后补 ③）——
#    ① 脚本直接 INSERT 的 4 张表 → 按**登记主键** `id` 回填；
#    ② 服务端在业务链路中写的 5 张表（登录日志 / 支付记录 / 秒杀成功 / 秒杀重试 / 上传记录）
#       脚本**没有插入点**，只能按 **`user_id`** 兜底回填 —— 实测这 5 张**全部有 `user_id`**，
#       所以键可靠、**不需要 JOIN 订单**；
#    ③ 🆕 **子表按父表主键兜底**：`oms_order_item` 没有 `user_id`，且它**可能因事务快照问题漏登记**
#       （2026-09-11 G7 实测：地址模板的 SELECT 提前定格了 REPEATABLE READ 快照 →
#        3 条订单项全部漏登记，导致"库里有行、SIM=0"）。按父订单 `order_id` 回填最可靠。
BACKFILL_BY_PK: Sequence[Tuple[str, str, str]] = (
    ("cs_mall_ums", "ums_user", "id"),
    ("cs_mall_oms", "oms_cart", "id"),
    ("cs_mall_oms", "oms_order", "id"),
    ("cs_mall_oms", "oms_order_item", "id"),
)
BACKFILL_BY_USER: Sequence[Tuple[str, str]] = (
    ("cs_mall_ums", "ums_login_log"),
    ("cs_mall_oms", "oms_payment_record"),
    ("cs_mall_seckill", "success"),
    ("cs_mall_seckill", "seckill_message_retry"),
    ("cs_mall_resource", "res_upload_record"),
)
BACKFILL_BY_ORDER: Sequence[Tuple[str, str, str]] = (
    ("cs_mall_oms", "oms_order_item", "order_id"),   # 子表：按父订单 id 兜底
)
# 迁移应加 `data_source` 的 9 张表（预检用：列不在 = 迁移没跑，直接 fail-fast）
# ⚠️ `oms_order_item` 同时出现在 ① 与 ③ → 这里**必须去重**，否则预检会以为缺列
DATA_SOURCE_TABLES: Sequence[Tuple[str, str]] = tuple(dict.fromkeys(
    [(d, t) for d, t, _ in BACKFILL_BY_PK]
    + list(BACKFILL_BY_USER)
    + [(d, t) for d, t, _ in BACKFILL_BY_ORDER]
))

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
# 🔴 2026-09-11 修正：`oms_payment_record` 原为 `pk`（按登记主键）模式，但**脚本从不登记支付记录**
#    （它是支付时服务端写的）→ 清理会走"无登记 → 跳过" → **支付记录静默残留**（全库 0 外键，删漏不报错）。
#    实测该表有 `user_id` → 改为 `user` 模式，与 data_source 回填口径统一。
#    同理：`success` / `seckill_message_retry` / `res_upload_record` 也都是服务端写的，一并改为 `user`。
CLEAN_ORDER: Sequence[Tuple[str, str, str, str]] = (
    ("cs_mall_oms", "oms_order_item", "order_child", "order_id"),
    ("cs_mall_seckill", "success", "user", "user_id"),
    ("cs_mall_seckill", "seckill_message_retry", "user", "user_id"),
    ("cs_mall_oms", "oms_payment_record", "user", "user_id"),
    ("cs_mall_oms", "oms_cart", "pk", "id"),
    ("cs_mall_oms", "oms_order", "pk", "id"),
    ("cs_mall_resource", "res_upload_record", "user", "user_id"),
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
    pwd = cfg.get("password") or ""
    if not pwd:
        sys.exit("未设置 SIM_DB_PASSWORD 环境变量（凭据不落盘，见 §〇.1 D6）")
    # 🔴 2026-09-11 实测踩到：PyMySQL 用 **latin1** 编码密码，若密码含非 ASCII 字符会抛
    #    `UnicodeEncodeError: 'latin-1' codec can't encode characters in position N-M`
    #    —— 那个报错完全看不出"其实是占位符没替换"。这里提前拦下并给可执行的提示。
    try:
        pwd.encode("latin1")
    except UnicodeEncodeError:
        sys.exit(
            "❌ SIM_DB_PASSWORD 含非 ASCII 字符（如中文）→ 你很可能**没有把占位符替换成真实密码**。\n"
            "   错误示例：export SIM_DB_PASSWORD='<老机 MySQL root 密码>'   ← 中文占位符会被原样当密码\n"
            "   正确做法：在老机跑 `grep '^MYSQL_ROOT_PASSWORD=' /data/csmall/.env` 取真实值，然后\n"
            "     · 方式一：export SIM_DB_PASSWORD='真实密码'\n"
            "     · 方式二（更稳，不进历史、免引号）："
            "read -rsp 'MySQL root 密码: ' SIM_DB_PASSWORD; export SIM_DB_PASSWORD; echo"
        )
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
# 🏷️ 回填专用列 `data_source='SIM'`（**服务端零改动** —— 值由脚本按登记表补写）
# =============================================================================

def has_column(conn, db_name: str, table: str, column: str) -> bool:
    """列是否存在 —— **实测而不是猜**（迁移没跑就不能回填，也不该静默跳过）"""
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) AS c FROM information_schema.columns "
                    "WHERE table_schema=%s AND table_name=%s AND column_name=%s",
                    (db_name, table, column))
        return cur.fetchone()["c"] > 0


def _chunks(values: Sequence[Any], size: int = CHUNK) -> Iterable[Sequence[Any]]:
    for i in range(0, len(values), size):
        yield values[i:i + size]


def _count_where(cur, full: str, where: str, params: Sequence[Any]) -> int:
    cur.execute(f"SELECT COUNT(*) AS c FROM {full} WHERE {where}", tuple(params))
    return cur.fetchone()["c"]


def backfill(conn, batch: str) -> Dict[str, int]:
    """把本批次登记到的实体标成 `data_source='SIM'`，返回 `{库.表: 标记行数}`。

    **服务端零改动**：不要求服务端写标识，而是造完数按影子登记表回填 ——
      ① 脚本直接 INSERT 的 4 张表 → 按**登记主键**；
      ② 服务端在业务链路中写的 5 张表（登录日志/支付记录/秒杀成功/秒杀重试/上传记录）
         → 脚本**没有插入点**，按**登记 user_id** 兜底（实测这 5 张都有 user_id）。
    幂等：WHERE 已排除 `data_source='SIM'` 的行，可重复执行（重复跑得 0）。
    """
    with conn.cursor() as cur:
        cur.execute("SELECT db_name, table_name, pk_value, user_ref FROM cs_mall_sim.sim_entity "
                    "WHERE batch_id=%s", (batch,))
        rows = list(cur.fetchall())

    by_pk: Dict[Tuple[str, str], List[Any]] = {}
    user_ids: List[Any] = []
    for r in rows:
        by_pk.setdefault((r["db_name"], r["table_name"]), []).append(r["pk_value"])
        if r["user_ref"]:
            user_ids.append(r["user_ref"])
    user_ids = sorted({str(u) for u in user_ids})

    marked: Dict[str, int] = {}
    with conn.cursor() as cur:
        def _mark(db_name: str, table: str, key_col: str, values: Sequence[Any]) -> int:
            full, total = f"{db_name}.{table}", 0
            for part in _chunks(values):
                ph = ",".join(["%s"] * len(part))
                cur.execute(
                    f"UPDATE {full} SET data_source=%s "
                    f"WHERE {key_col} IN ({ph}) AND (data_source IS NULL OR data_source<>%s)",
                    (DATA_SOURCE_SIM, *part, DATA_SOURCE_SIM))
                total += cur.rowcount
            return total

        for db_name, table, key_col in BACKFILL_BY_PK:
            values = by_pk.get((db_name, table))
            if values:
                marked[f"{db_name}.{table}"] = _mark(db_name, table, key_col, values)

        if user_ids:
            for db_name, table in BACKFILL_BY_USER:
                if not has_column(conn, db_name, table, "data_source"):
                    log(f"   ⚠️ 跳过 {db_name}.{table}：无 data_source 列（迁移未执行？）")
                    continue
                marked[f"{db_name}.{table}"] = _mark(db_name, table, "user_id", user_ids)

        # ③ 子表按**父订单 id** 兜底（父行主键即 BACKFILL_BY_PK 里的 oms_order.id）
        #    这是 G7 的修复：即便订单项漏登记，也能按父订单把它们标上
        order_ids = by_pk.get(("cs_mall_oms", "oms_order"))
        if order_ids:
            for db_name, table, key_col in BACKFILL_BY_ORDER:
                if not has_column(conn, db_name, table, "data_source"):
                    log(f"   ⚠️ 跳过 {db_name}.{table}：无 data_source 列（迁移未执行？）")
                    continue
                key = f"{db_name}.{table}"
                marked[key] = marked.get(key, 0) + _mark(db_name, table, key_col, order_ids)

    conn.commit()
    return marked


def verify_backfill(conn, batch: str) -> List[str]:
    """回填校验（**方案的可行性边界之一：回填必须真的发生**）。

    逐表比对"**本批应标的行数**"与"**本批实际标了的行数**"，不一致即报（返回问题列表）：
      · 按主键回填的表：应标 = 登记条数（1:1）
      · 按 user_id 回填的表：应标 = **属于本批模拟用户的行数**（可能一单多行，故不能拿登记条数比）
      · 子表按父订单：应标 = **本批订单下的子行数**（G7 的校验盲点修复）

    🔴 **G11 修正（2026-09-12 正式造数实测）—— 多批次共存必误报**：
      原实现把"实标行数"取成**全局** `data_source='SIM'` 计数，再与"本批应标"比 —— 库里一旦
      并存 ≥2 个批次（本次实测：旧演示 20 用户 + AI 压测池 800 + 本批 125 = **945**），
      **每张表都会报"标识数不符 / 误标"**：那一跑共报 **11 条，逐条都能用别的批次解释**，
      而**真漏标是 0**（经独立按批次复核确认）。⇒ 现一律**按批次圈定范围**再计数
      （主键表 `key IN 本批登记 pk` / user 表 `user_id IN 本批 user_ref` / 子表 `key IN 本批订单 id`），
      跨批次的行只作 `ℹ️` **提示**、不再算问题。
    """
    with conn.cursor() as cur:
        cur.execute("SELECT db_name, table_name, pk_value, user_ref FROM cs_mall_sim.sim_entity "
                    "WHERE batch_id=%s", (batch,))
        rows = list(cur.fetchall())

        by_pk: Dict[Tuple[str, str], List[Any]] = {}
        user_ids: List[str] = []
        for r in rows:
            by_pk.setdefault((r["db_name"], r["table_name"]), []).append(r["pk_value"])
            if r["user_ref"]:
                user_ids.append(str(r["user_ref"]))
        user_ids = sorted(set(user_ids))

        problems: List[str] = []

        def _count_scoped(db_name: str, table: str, key_col: str,
                          values: Sequence[Any], sim_only: bool) -> int:
            """**本批范围内**（`key_col IN 本批登记值`）的计数；`sim_only=True` 只数已标 SIM 的。"""
            n = 0
            for part in _chunks(values):
                ph = ",".join(["%s"] * len(part))
                where = f"{key_col} IN ({ph})"
                params: Tuple[Any, ...] = tuple(part)
                if sim_only:
                    where += " AND data_source=%s"
                    # 🔴 顺序即语义：SQL 里 `IN (...)` 在前、`data_source=%s` 在后
                    params = (*part, DATA_SOURCE_SIM)
                n += _count_where(cur, f"{db_name}.{table}", where, params)
            return n

        def _report(full: str, label: str, should: int, got: int, extra: str = "") -> None:
            if got < should:
                problems.append(f"{full} 漏标：{label} {should} 行，只标了 {got} 行{extra}")
            elif got > should:
                problems.append(f"{full} 误标：本批范围内标了 {got} 行，但{label}只有 {should} 行{extra}")
            else:
                log(f"   ✅ {full:38s} {label} {should:5d} → 已标 {got:5d}")

        # ① 按主键回填：本批**现存**行必须全部被标记
        #    ⚠️ 不能拿"登记条数"当应标数：登记后行可能被**业务正常删除**（2026-09-12 实测：
        #    下单会清掉该 SKU 的购物车行 → 139 条登记 / 136 条现存）→ 那 3 条按 pk 删不到
        #    = 无残留，**不算漏标**；只作 ℹ️ 提示，免得把"业务删除"误判成"回填没做"。
        for db_name, table, key_col in BACKFILL_BY_PK:
            values = by_pk.get((db_name, table))
            if not values:
                continue
            full = f"{db_name}.{table}"
            if not has_column(conn, db_name, table, "data_source"):
                problems.append(f"{full} 缺 data_source 列 → 迁移没执行？")
                continue
            exists = _count_scoped(db_name, table, key_col, values, False)
            if exists != len(values):
                log(f"   ℹ️ {full:38s} 登记 {len(values):5d} / 现存 {exists:5d}"
                    f"（{len(values) - exists} 行已被业务删除 → 清理按 pk 删不到 = 无残留）")
            _report(full, "本批现存", exists,
                    _count_scoped(db_name, table, key_col, values, True))

        # ② 按 user_id 回填：属于本批模拟用户的行**应全部**被标记
        if user_ids:
            for db_name, table in BACKFILL_BY_USER:
                full = f"{db_name}.{table}"
                if not has_column(conn, db_name, table, "data_source"):
                    problems.append(f"{full} 缺 data_source 列 → 迁移没执行？")
                    continue
                _report(full, "本批用户",
                        _count_scoped(db_name, table, "user_id", user_ids, False),
                        _count_scoped(db_name, table, "user_id", user_ids, True))

        # ③ 子表按父订单：属于本批订单的所有子行都应被标记
        #    （G7 的校验盲点修复：以前只校验"登记表里出现过的表"，
        #      `oms_order_item` 漏登记 → 根本不在校验范围内 → 漏标却报"通过"）
        order_ids = by_pk.get(("cs_mall_oms", "oms_order"))
        if order_ids:
            for db_name, table, key_col in BACKFILL_BY_ORDER:
                full = f"{db_name}.{table}"
                if not has_column(conn, db_name, table, "data_source"):
                    problems.append(f"{full} 缺 data_source 列 → 迁移没执行？")
                    continue
                _report(full, "本批订单下",
                        _count_scoped(db_name, table, key_col, order_ids, False),
                        _count_scoped(db_name, table, key_col, order_ids, True),
                        extra="（按父订单兜底）")

        # ④ ℹ️ 跨批次提示（**不再算问题**）：库里标了 SIM 但不属于本批的行 → 属于**别的批次**
        #    口径：全局已标 SIM 数 − 本批范围内已标数（**不能用分块 NOT IN**，那会把同一条行重复计）
        hints: List[str] = []
        for db_name, table, key_col in BACKFILL_BY_PK:
            values = by_pk.get((db_name, table))
            if not values:
                continue
            other = (_count_where(cur, f"{db_name}.{table}", "data_source=%s", (DATA_SOURCE_SIM,))
                     - _count_scoped(db_name, table, key_col, values, True))
            if other > 0:
                hints.append(f"{db_name}.{table} +{other}")
        if user_ids:
            for db_name, table in BACKFILL_BY_USER:
                other = (_count_where(cur, f"{db_name}.{table}", "data_source=%s", (DATA_SOURCE_SIM,))
                         - _count_scoped(db_name, table, "user_id", user_ids, True))
                if other > 0:
                    hints.append(f"{db_name}.{table} +{other}")
        if hints:
            log(f"   ℹ️ 另有已标 SIM 的行属于**别的批次**（不是本批问题）：{', '.join(hints)}")

    return problems


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

    # --- 浏览（80%：**也需要登录**）---
    # 🔴 2026-09-11 实测踩坑：浏览接口**不是公开接口**！
    #    mall-front 的安全配置是 `.anyRequest().authenticated()`
    #    （mall-front/.../security/config/ResourceWebSecurityConfiguration.java:64），
    #    permitAll 白名单只有 `/` `/favicon.ico` `/error` `/swagger-resources/**`
    #    `/v2|v3/api-docs/**` `/doc.html`。
    #    不带 token → `{"state":401,"message":"您没有登录！"}`，**而 HTTP 状态码仍是 200**
    #    （本项目鉴权失败不改 HTTP 码）→ **只看 HTTP 码会把 401 误判成成功**（我上一轮就这么误判过）。
    #    实测：带 token 后 `/front/spu/list/all` 返回 state=200 + 真实商品数据；
    #          `Authorization: Bearer<token>` 与 `Bearer <token>` **两种都能过**
    #          （服务端用 `startsWith(tokenHead)` + `substring(...).trim()`）。
    def spu_list_all(self, page: int = 1, page_size: int = 10, token_header: Optional[str] = None):
        return self._call("GET", "/front/spu/list/all", token_header=token_header,
                          params={"page": page, "pageSize": page_size})

    def spu_detail(self, spu_id: int, token_header: Optional[str] = None):
        return self._call("GET", f"/front/spu/{spu_id}", token_header=token_header)

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
            # 🔴 2026-09-11 校准实测教训：`REGEXP_CONTACT_NAME = ".{2,4}"`
            #    （mall-pojo/.../valid/order/OrderRegExpression.java:6）—— **联系人只能 2~4 字符**！
            #    原来传 `模拟用户1234`（8 字符）→ 下单会 400。这里随机取 2 位数字：
            #    既合规，又保持参数可变（@Idempotent 的 key = idempotent:<key>:<userId>:<argsDigest>，
            #    args 一变 key 就不同，不会互相顶掉）
            "contactName": f"模拟{random.randint(0, 99):02d}",         # 4 字符，合规且随机
            # 🏷️ 不再借用 `tag` 传标识（已撤回）：标识统一走专用列 `data_source`，由 backfill() 回填
            "mobilePhone": FAKE_PHONE_PREFIX + f"{random.randint(0, 9999):04d}",
            "provinceCode": address["province_code"], "provinceName": address["province_name"],
            "cityCode": address["city_code"], "cityName": address["city_name"],
            "districtCode": address["district_code"], "districtName": address["district_name"],
            "streetCode": address["street_code"], "streetName": address["street_name"],
            "detailedAddress": f"模拟地址 {random.randint(1, 999)} 号",
            # 🔴 2026-09-11 实测踩坑：`PaymentTypeEnum` = 0=银联 / 1=微信 / **2=支付宝**
            #    （mall-pojo/.../order/enums/PaymentTypeEnum.java:8-10），而 `PaymentStrategyFactory`
            #    **只注册了支付宝策略**（AlipaySandboxStrategy），其余渠道会
            #    `throw IllegalArgumentException("支付渠道 [银联] 暂未实现")`（PaymentStrategyFactory.java:36）
            #    → 原来传 0 必然 500。支付宝走的是**模拟模式**：未配置 AppId/私钥时
            #    **跳过真实支付宝 API 直接返回成功**（AlipaySandboxStrategy.java:110-112）。
            "paymentType": 2, "orderType": 0,          # 2=支付宝（当前唯一可用渠道；底层仍是模拟支付）
            "amountOfOriginalPrice": total, "amountOfFreight": 0.0,
            "amountOfDiscount": 0.0, "amountOfActualPay": total,
            "orderItems": [{
                "skuId": sku["sku_id"], "title": sku["title"], "barCode": sku.get("bar_code") or "",
                "data": "{}", "mainPicture": sku["picture"], "price": price, "quantity": qty,
            }],
        })

    def order_pay(self, token_header: str, order_id: int):
        """POST /oms/order/pay —— **支付宝渠道 + 模拟支付**（不碰支付宝沙箱真实 API）

        🔴 必须传 `paymentType=2`（支付宝）：只有它注册了策略，0/1 会 500「暂未实现」。
        """
        return self._call("POST", "/oms/order/pay", token_header=token_header, json={
            "id": order_id, "paymentType": 2,
        })

    # --- 秒杀（#67 之 ④ · 2026-09-12）---
    def seckill_spu_list(self, token_header: str, page: int = 1, page_size: int = 50):
        """`/seckill/spu/list` —— 行里 `id` 是 **PMS spu 主键**，`url` 只在**秒杀时间窗内**才有值"""
        return self._call("GET", "/seckill/spu/list", token_header=token_header,
                          params={"page": page, "pageSize": page_size})

    def seckill_sku_list(self, token_header: str, spu_id: int):
        """`/seckill/sku/list/{spuId}`（同样以 PMS spu 主键为参）"""
        return self._call("GET", f"/seckill/sku/list/{spu_id}", token_header=token_header)

    def seckill_commit(self, token_header: str, rand_code: str, dto: Dict[str, Any]):
        """`POST /seckill/{randCode}`（body = SeckillOrderAddDTO；服务端**不校验金额**）"""
        return self._call("POST", f"/seckill/{rand_code}", token_header=token_header, json=dto)

    def seckill_spu_detail(self, token_header: str, spu_id: int):
        """`GET /seckill/spu/{spuId}`（PMS spu 主键）—— 🔴 **randCode 只能从这里拿**：
        设置 `url` 的代码在 `SeckillSpuServiceImpl.getSeckillSpuVO()`（:135-156），
        **列表接口不设 url**（2026-09-12 干跑实测：list 的 url 全为 None）。
        ⚠️ 路由是 `/seckill/spu/{id}`（控制器 `@RequestMapping("/seckill/spu")` + `@GetMapping("/{spuId}")`）；
        写成 `/seckill/{id}` 会命中 `POST /seckill/{randCode}` ⇒ `state=500 Request method 'GET' is not supported`"""
        return self._call("GET", f"/seckill/spu/{spu_id}", token_header=token_header)


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
# 🔴 服务端 DTO 校验规则（**照抄代码**，用于本地预校验 —— 2026-09-11 校准踩坑后新增）
# =============================================================================
# 为什么要有这一段：校准第一次真跑时，`test_sim_0001` 的下划线被服务端拒绝
# （state=400），而脚本直到"写下第一个实体"那一刻才暴露 → **跑一次撞一个**。
# 现在把服务端的真实正则抄进脚本，**在预检阶段一次性把所有字段验完**。
#
# 来源（`文件:行`；**服务端改校验后必须同步这里**）：
#   username    mall-common/src/main/java/com/cooxiao/mall/common/validation/RegExpressions.java:14
#   password    同文件:17
#   nickname    mall-pojo/src/main/java/com/cooxiao/mall/pojo/valid/ums/UserRegistryRegExpression.java:6
#   phone       同文件:9
#   email       同文件:12
#   contactName mall-pojo/src/main/java/com/cooxiao/mall/pojo/valid/order/OrderRegExpression.java:6
#   mobilePhone 同文件:9
#
# ⚠️ **本表已与 Java 源逐条机器比对（7/7 一致）**，因为 2026-09-11 我**手抄**时把 phone 的
#    `[0-9]` 多抄了一组（写成 12 位），差点让预检报出假故障。**改完请重跑比对**：
#      PowerShell：从上述文件抽 `String REGEXP_X = "..."` 的字面量，与本节的正则逐条 diff
SERVER_REGEX: Dict[str, Tuple[str, str]] = {
    "username": (r"^[a-zA-Z]{1}[0-9a-zA-Z]{3,15}$",
                 "用户名必须由字母、数字组成、4~16 字符、首字符必须是字母"),
    "password": (r"^[\u0020-\u007e]{4,16}$", "密码长度 4~16"),
    "nickname": (r".{2,16}", "昵称 2~16 字符"),
    "phone": (r"^1[34589][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]$",
              "中国大陆手机号（注册口，**11 位**：1 + [34589] + 9 位）"),
    "email": (r"^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$", "邮箱格式"),
    "contactName": (r".{2,4}", "联系人姓名必须 2~4 字符"),
    "mobilePhone": (r"^1(?:3\d|4[4-9]|5[0-35-9]|6[67]|7[013-8]|8\d|9\d)\d{8}$",
                    "中国大陆有效手机号（下单用的这条更严）"),
}


def validate_local() -> List[str]:
    """用**服务端 DTO 的真实正则**在本地预校验"将要提交的字段值"。

    返回问题列表（空 = 全过）。目的是把"跑一次撞一个"变成"预检一次全暴露"。
    """
    problems: List[str] = []
    u = f"{USER_PREFIX}{1:04d}"                       # 第 1 个模拟用户（与 ensure_users 同构）
    samples = {
        "username": u,
        "password": USER_PASSWORD,
        "nickname": f"模拟用户{1:04d}",
        "phone": FAKE_PHONE_PREFIX + f"{1:04d}",
        "email": f"{u}@{EMAIL_DOMAIN}",
        "contactName": f"模拟{0:02d}",                # 与 order_add 同构（最短形态）
        "mobilePhone": FAKE_PHONE_PREFIX + f"{1:04d}",
    }
    for field, value in samples.items():
        pattern, desc = SERVER_REGEX[field]
        if re.fullmatch(pattern, value) is None:
            problems.append(
                f"字段 {field} 过不了服务端校验：本次会提交 {value!r}，"
                f"但它不匹配 {pattern}（{desc}）→ 服务端将返回 400")
    return problems


# =============================================================================
# 预检（fail-fast：任一不过直接退出 —— §〇.1 D7 / §六 清单）
# =============================================================================

def preflight(conn, days: int, per_day: int, need_seckill: bool,
              n_users: int = 20) -> List[Dict[str, Any]]:
    # 0) 🔴 先用服务端真实正则把"要提交的字段值"全验一遍（不碰网络、不碰数据）
    problems: List[str] = validate_local()

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

    # 7) 🔴 专用列 `data_source` 已就位（= 4 个 Flyway 迁移已执行）
    #    列不在 → 回填无处可写 → 直接 fail-fast；**不静默跳过**，否则"自证身份"形同虚设
    missing = [f"{d}.{t}" for d, t in DATA_SOURCE_TABLES
               if not has_column(conn, d, t, "data_source")]
    if missing:
        problems.append(
            f"data_source 列缺失 {len(missing)}/{len(DATA_SOURCE_TABLES)} 张表："
            f"{', '.join(missing)} → 先重启对应服务让 Flyway 迁移生效（方案 §2.2.9）")

    # 8) 🔴 计划使用的**手机号段 / 用户名段**未被占用
    #    2026-09-11 实测踩坑：`1390000` 段已被既有 benchuser01..100 占满 100 个
    #    （13900000001~13900000100）→ 注册直接 409「注册手机号已存在」。
    #    原来只查了 `testsim%` 的用户名计数、**没查手机号**，所以撞上了才知道 → 现在预先拦。
    with conn.cursor() as cur:
        planned = (
            ("手机号", "phone", [FAKE_PHONE_PREFIX + f"{i:04d}" for i in range(1, n_users + 1)],
             f"换 `SIM_PHONE_PREFIX`（当前 {FAKE_PHONE_PREFIX}；"
             f"实测空闲段 1390009 / 1390090 / 1390100 / 1391111 / 1380000 / 1890000 / 1990000）"),
            ("用户名", "username", [f"{USER_PREFIX}{i:04d}" for i in range(1, n_users + 1)],
             f"换 `SIM_USER_PREFIX`（当前 {USER_PREFIX}）"),
        )
        for label, col, values, hint in planned:
            clash: List[str] = []
            for part in _chunks(values):
                ph = ",".join(["%s"] * len(part))
                cur.execute(f"SELECT {col} AS v FROM cs_mall_ums.ums_user WHERE {col} IN ({ph})",
                            tuple(part))
                clash.extend(str(r["v"]) for r in cur.fetchall())
            if clash:
                problems.append(
                    f"{label}段被占用：{len(clash)}/{len(values)} 个已存在（如 {clash[0]}）→ {hint}")

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
               sleep_min: float, sleep_max: float, with_seckill: bool,
               qps: float = 0.0, jitter: float = 0.3) -> Dict[str, int]:
    stat = {"browse": 0, "cart": 0, "order": 0, "paid": 0, "pay_fail": 0, "fail": 0}
    actions = days * per_day

    # 🔴 2026-09-11 实测踩坑（G7）：地址模板**必须挪到循环外读一次**。
    #    原来每个下单动作都调 `load_address_template(conn)`，它是 SELECT →
    #    会**开启脚本自己的事务并在那一刻定格快照**（MySQL 默认 REPEATABLE READ），
    #    而该参数在 `api.order_add(...)` **之前**求值 → 快照定格在"App 还没建订单"时，
    #    于是紧随其后的 `SELECT id FROM oms_order_item WHERE order_id=?`
    #    **永远看不到 App 刚提交的订单项** → 订单项全部漏登记（实测 3/3 漏）。
    #    （加购没出问题，是因为 `api.cart_add()` 之前没有任何 DB 读，快照是新的。）
    address = load_address_template(conn)
    conn.commit()                       # 结束"读地址"开启的事务，确保后续 SELECT 拿新快照

    log(f"开始造数：{days} 天 × {per_day} 行为 = {actions} 次动作"
        + (f"（目标 **{qps:.1f} QPS**，含 ±{jitter * 100:.0f}% 抖动）" if qps > 0
           else f"（慢节奏 {sleep_min}~{sleep_max}s）"))
    for n in range(1, actions + 1):
        t_action = time.perf_counter()          # 本动作起点（--qps 配速要用）
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
            if kind == "browse":                      # 80%：只读（**需登录**，见 Api 注释），不产生业务数据
                if random.random() < 0.5:
                    api.spu_detail(sku["spu_id"], user["token"])
                else:
                    api.spu_list_all(random.randint(1, 3), 10, user["token"])
                stat["browse"] += 1
            elif kind == "cart":                      # 15%：加购但不结算
                api.cart_add(user["token"], sku, random.randint(1, 2))
                with conn.cursor() as cur:            # 取回刚插入的 cartId 并登记
                    cur.execute("SELECT id FROM cs_mall_oms.oms_cart WHERE user_id=%s ORDER BY id DESC LIMIT 1",
                                (user["id"],))
                    row = cur.fetchone()
                if row:
                    registry.register(conn, "oms_cart", row["id"], "cs_mall_oms", user_ref=str(user["id"]))
                    conn.commit()
                stat["cart"] += 1
            else:                                     # 5%：完整下单 + 模拟支付
                vo = api.order_add(user["token"], sku, 1, address)
                if not isinstance(vo, dict) or "id" not in vo:
                    raise RuntimeError(f"下单响应里没有订单 id，无法登记（返回={vo}）→ 核对 OrderAddVO 字段")
                oid = vo["id"]
                # 🔴 防御性刷新快照：确保下面的 SELECT 能看到 App 刚提交的订单项（见函数开头说明）
                conn.commit()
                registry.register(conn, "oms_order", oid, "cs_mall_oms", user_ref=str(user["id"]))
                with conn.cursor() as cur:            # 订单项按 order_id 登记（它自己没有 user_id）
                    cur.execute("SELECT id FROM cs_mall_oms.oms_order_item WHERE order_id=%s", (oid,))
                    items = list(cur.fetchall())
                for it in items:
                    registry.register(conn, "oms_order_item", it["id"], "cs_mall_oms",
                                      user_ref=str(user["id"]))
                conn.commit()
                if not items:
                    log(f"   ⚠️ 订单 {oid} 查不到订单项 → 已改由 backfill() 按 order_id 兜底标记")
                stat["order"] += 1                    # 订单已建即计入（与支付结果分开统计）
                try:
                    api.order_pay(user["token"], int(oid))
                    stat["paid"] += 1
                except Exception as pe:               # noqa: BLE001
                    # 支付失败**不算动作失败**：订单已建成（只是保持未支付）
                    stat["pay_fail"] += 1
                    if stat["pay_fail"] <= 20:
                        log(f"   ⚠️ 支付失败（订单 {oid} 已建、保持未支付）：{pe}")
        except Exception as e:                        # noqa: BLE001
            stat["fail"] += 1
            if stat["fail"] <= 20:                    # 只打前 20 条错误，避免刷屏
                log(f"   ⚠️ 动作失败（{kind}）：{e}")
        if n % 50 == 0:
            log(f"   进度 {n}/{actions}：浏览 {stat['browse']} / 加购 {stat['cart']} / "
                f"下单 {stat['order']}（已支付 {stat['paid']}，支付失败 {stat['pay_fail']}）/ 失败 {stat['fail']}")
        # 🔴 节奏（2026-09-12 新增 `--qps`）：把"提量"与"提 QPS"解耦 ——
        #    qps > 0 时按**目标速率**配速（用"本动作实际耗时"回补等待时间，含抖动），
        #    否则沿用 sleep-min/max 的随机慢节奏（模拟真人）。
        #    ⚠️ 写动作有 Sentinel 天花板（秒杀提交 10 QPS / 新增订单 20 / 支付订单 20），
        #      浏览类无规则 ⇒ 需要更高 QPS 时请把动作结构调成"多浏览 + 少写"。
        if qps > 0:
            target = 1.0 / qps
            spent = time.perf_counter() - t_action
            wait = target - spent + target * jitter * (random.random() * 2 - 1)
            if wait > 0:
                time.sleep(wait)
        else:
            time.sleep(random.uniform(sleep_min, sleep_max))
    if with_seckill:
        log("ℹ️ 秒杀**不在漏斗里跑**（漏斗只做浏览/加购/下单）→ 由漏斗结束后的**独立秒杀阶段**执行，见下方 🐝")
    return stat


# =============================================================================
# 秒杀动作 + 「秒杀后恢复」（#67 之 ④ · 2026-09-12 新增）
# =============================================================================
# 【契约 · 读码实测 —— 实施前请按此对齐】
#   · 列表  GET /seckill/spu/list?page&pageSize（需 token）
#       行里 `id` = **PMS spu 主键**（`SeckillSpuServiceImpl:119-124` 用 `standardVO` 复制构造 VO，
#       `id` 来自 pms_spu 且未被覆盖）；`url` = "/seckill/<randCode>"
#       🔴 **只有当前时间落在秒杀时间窗内才会赋值**（`:142-156`）⇒ **取不到 url = 不在窗口内**
#       `purchased` = 该 spu 下是否已购买（读 `reseckill` 永久标记）
#   · SKU   GET /seckill/sku/list/{spuId}（同样以 PMS spu 主键为参）→ skuId / seckillPrice / stock
#   · 提交  POST /seckill/{randCode}，body = `SeckillOrderAddDTO`（必填字段与普通下单同构，见 Api.order_add）
#   · 服务端**不校验金额**：只用 `item.price` 记 `success.seckillPrice`（`SeckillServiceImpl:141-142`）
#   · Redis 时序（决定"要恢复什么"）：
#       提交时 → `order:lock`(1min, setIfAbsent)；提交成功 → `ordered`=sn(2h)
#       支付成功 → **由 order 模块**写 `reseckill`（**永久**，`OmsOrderServiceImpl:235-243`）并删 `ordered`
#       ⇒ 未支付：删 order:lock + ordered；已支付：**还要删 reseckill**
#   · 库存：Redis `mall:seckill:sku:stock:<skuId>` 在提交时 `decrement`；**DB 侧由 MQ 消费者异步回写**
#       ⇒ 恢复必须 Redis 立即回补 + DB 按快照回填，并**等一拍再重读校验**（异步写入可能比我们晚到）
SECKILL_RE_SECKILL = "mall:seckill:reseckill:"
SECKILL_ORDER_LOCK = "mall:seckill:order:lock:"
SECKILL_ORDERED = "mall:seckill:ordered:"
SECKILL_SKU_STOCK = "mall:seckill:sku:stock:"


class Redis:
    """极简 RESP 客户端（**纯标准库**，避免为"秒杀后恢复"引入 redis 依赖）。

    ⚠️ 生产 Redis 是**哨兵模式**：本客户端必须**直连主库**（默认 `172.29.193.239:6379`）——
       连到从库时写命令会收 `READONLY`；哨兵地址只在故障转移后才需要。
    """

    def __init__(self, host: str, port: int = 6379, password: Optional[str] = None,
                 timeout: float = 5.0):
        self.sock = socket.create_connection((host, int(port)), timeout)
        self.f = self.sock.makefile("rb")
        if password:
            self.cmd("AUTH", password)

    def cmd(self, *args: Any) -> Any:
        """RESP 数组协议（任意参数都安全，不依赖 inline 命令的转义规则）"""
        buf = b"*%d\r\n" % len(args)
        for a in args:
            b = str(a).encode()
            buf += b"$%d\r\n%s\r\n" % (len(b), b)
        self.sock.sendall(buf)
        return self._read()

    def _read(self) -> Any:
        line = self.f.readline().rstrip(b"\r\n")
        if not line:
            raise RuntimeError("Redis 连接被关闭")
        t, rest = line[:1], line[1:]
        if t == b"+":
            return rest.decode()
        if t == b"-":
            raise RuntimeError("Redis 错误：" + rest.decode())
        if t == b":":
            return int(rest)
        if t == b"$":
            n = int(rest)
            if n < 0:
                return None
            return self.f.read(n + 2)[:-2].decode()
        if t == b"*":
            n = int(rest)
            return None if n < 0 else [self._read() for _ in range(n)]
        raise RuntimeError(f"未知 RESP 类型：{t!r}")

    def exists(self, key: str) -> bool:
        return int(self.cmd("EXISTS", key)) == 1

    def close(self) -> None:
        try:
            self.f.close()
            self.sock.close()
        except Exception:      # noqa: BLE001
            pass


def open_redis(args) -> Optional[Redis]:
    """连**主库**；连不上只告警、返回 None（不阻断造数主流程）"""
    pw = os.environ.get("SIM_REDIS_PASSWORD")
    if not pw:
        log("⚠️ 未设 SIM_REDIS_PASSWORD → **秒杀后恢复的 Redis 部分将跳过**"
            "（限购标记与预热库存会残留）")
        return None
    try:
        r = Redis(args.redis_host, args.redis_port, pw)
        r.cmd("PING")
        log(f"✅ Redis 主库已连接（{args.redis_host}:{args.redis_port}）")
        return r
    except Exception as e:      # noqa: BLE001
        log(f"⚠️ Redis 连接失败（{e}）→ 恢复的 Redis 部分将跳过")
        return None


def seckill_targets(api: "Api", token: str) -> List[Dict[str, Any]]:
    """取**在秒杀时间窗内、未购买**的目标。

    🔴 **randCode 只能从详情接口拿**（2026-09-12 干跑实测）：`url` 由
    `SeckillSpuServiceImpl.getSeckillSpuVO()` 赋值（:135-156），**列表接口不设 url**。
    所以流程是：列表拿候选 `id`（= PMS spu 主键）→ 逐个详情接口读 `url`。
    """
    page = api.seckill_spu_list(token, 1, 50) or {}
    out: List[Dict[str, Any]] = []
    for row in (page.get("list") or []):
        if row.get("purchased"):
            continue                                   # 已购买（reseckill 永久标记）
        spu_id = row.get("id")
        try:
            detail = api.seckill_spu_detail(token, spu_id) or {}
        except Exception as e:      # noqa: BLE001
            # 不在窗口内时服务端可能直接报错（"随机码不存在"）→ 属正常，跳过
            log(f"   · spu {spu_id} 详情不可用（{e}）→ 跳过")
            continue
        url = str(detail.get("url") or "").strip()
        if not url.startswith("/seckill/"):
            continue                                   # 不在时间窗内 / 拿不到随机码
        rc = url.rsplit("/", 1)[-1]
        if rc:
            out.append({"spu_id": spu_id, "name": detail.get("name") or row.get("name"),
                        "rand_code": rc, "url": url})
    return out


def seckill_snapshot(conn, r: Optional[Redis], sku_id: int) -> Dict[str, Any]:
    """秒杀前**记账**：DB 三值 + Redis 预热库存（恢复的唯一依据）"""
    snap: Dict[str, Any] = {"sku_id": sku_id}
    with conn.cursor() as cur:
        cur.execute("SELECT seckill_stock FROM cs_mall_seckill.seckill_sku WHERE sku_id=%s", (sku_id,))
        row = cur.fetchone()
        snap["seckill_stock"] = row["seckill_stock"] if row else None
        cur.execute("SELECT stock FROM cs_mall_pms.pms_sku WHERE id=%s", (sku_id,))
        row = cur.fetchone()
        snap["stock"] = row["stock"] if row else None
        cur.execute("SELECT p.sales FROM cs_mall_pms.pms_spu p "
                    "JOIN cs_mall_pms.pms_sku s ON s.spu_id=p.id WHERE s.id=%s", (sku_id,))
        row = cur.fetchone()
        snap["sales"] = row["sales"] if row else None
    # 🆕 2026-09-12：**全量** sales 快照 —— 实测秒杀会给"把 `seckill_spu.id` 当 pms spu 用"的**另一行**加 sales
    #   （`SeckillQueueConsumer:98` → `incrementSales(sku.getSpuId())`，而该列存的是**秒杀表内部 id**）⇒
    #   只快照"目标 spu"根本恢复不掉，必须按**实际变化**回补。
    with conn.cursor() as cur:
        cur.execute("SELECT id, sales FROM cs_mall_pms.pms_spu")
        snap["all_sales"] = {int(x["id"]): x["sales"] for x in cur.fetchall()}
    snap["redis_stock"] = None
    if r is not None:
        try:
            snap["redis_stock"] = r.cmd("GET", SECKILL_SKU_STOCK + str(sku_id))
        except Exception as e:      # noqa: BLE001
            log(f"   ⚠️ 读预热库存失败：{e}")
    return snap


def seckill_restore(conn, r: Optional[Redis], snap: Dict[str, Any], user_id: int) -> List[str]:
    """**秒杀后恢复**（用户 2026-09-12 明确要求"每次秒杀之后就恢复数据"）。

    ① Redis：删三类秒杀标记（`order:lock` / `ordered` / **`reseckill`**）+ 预热库存回补
    ② DB  ：按快照回填 `seckill_stock` / `pms_sku.stock` / `pms_spu.sales`
            —— 这是**记账回补**（秒杀前读到的确值），不是"减回去"的补偿运算
    ③ 返回值 = 恢复动作清单（**不静默**）
    """
    acts: List[str] = []
    sku_id = snap["sku_id"]
    if r is not None:
        keys = [SECKILL_ORDER_LOCK + f"{sku_id}:{user_id}",
                SECKILL_ORDERED + f"{sku_id}:{user_id}",
                SECKILL_RE_SECKILL + f"{sku_id}:{user_id}"]
        try:
            existed = [k for k in keys if r.exists(k)]
            if existed:
                r.cmd("DEL", *existed)
            names = "/".join(k.split(":")[-3] for k in existed)
            acts.append(f"Redis: DEL {len(existed)} 个秒杀标记键" + (f"（{names}）" if existed else ""))
            if snap.get("redis_stock") is not None:
                r.cmd("SET", SECKILL_SKU_STOCK + str(sku_id), snap["redis_stock"])
                acts.append(f"Redis: SET 预热库存 ← {snap['redis_stock']}")
        except Exception as e:      # noqa: BLE001
            acts.append(f"🔴 Redis 恢复失败：{e}")
    else:
        acts.append("⚠️ Redis 未连接 → 三类标记键**未清理**")
    with conn.cursor() as cur:
        if snap.get("seckill_stock") is not None:
            cur.execute("UPDATE cs_mall_seckill.seckill_sku SET seckill_stock=%s WHERE sku_id=%s",
                        (snap["seckill_stock"], sku_id))
            acts.append(f"DB: seckill_stock ← {snap['seckill_stock']}")
        if snap.get("stock") is not None:
            cur.execute("UPDATE cs_mall_pms.pms_sku SET stock=%s WHERE id=%s", (snap["stock"], sku_id))
            acts.append(f"DB: pms_sku.stock ← {snap['stock']}")
        if snap.get("sales") is not None:
            cur.execute("UPDATE cs_mall_pms.pms_spu p JOIN cs_mall_pms.pms_sku s ON s.spu_id=p.id "
                        "SET p.sales=%s WHERE s.id=%s", (snap["sales"], sku_id))
            acts.append(f"DB: pms_spu.sales ← {snap['sales']}")
        # 🆕 2026-09-12：再按**全量 sales 快照**回补"实际被改动过的行"（跨命名空间写入的兜底）
        for spu_id, before in (snap.get("all_sales") or {}).items():
            cur.execute("SELECT sales FROM cs_mall_pms.pms_spu WHERE id=%s", (spu_id,))
            row = cur.fetchone()
            if row and str(row["sales"]) != str(before):
                cur.execute("UPDATE cs_mall_pms.pms_spu SET sales=%s WHERE id=%s", (before, spu_id))
                acts.append(f"DB: ⚠️ pms_spu.sales[{spu_id}] {row['sales']} → {before}"
                            "（**非目标 spu**：秒杀内部 id 与 pms id 串了）")
    conn.commit()
    return acts


def seckill_verify(conn, r: Optional[Redis], snap: Dict[str, Any], user_id: int) -> Tuple[bool, str]:
    """重读校验：DB 三值是否回到快照；Redis 三类标记是否已清、预热库存是否回补"""
    sku_id = snap["sku_id"]
    bad: List[str] = []
    with conn.cursor() as cur:
        cur.execute("SELECT seckill_stock FROM cs_mall_seckill.seckill_sku WHERE sku_id=%s", (sku_id,))
        row = cur.fetchone()
        if snap.get("seckill_stock") is not None and row and row["seckill_stock"] != snap["seckill_stock"]:
            bad.append(f"seckill_stock={row['seckill_stock']}≠{snap['seckill_stock']}")
        cur.execute("SELECT stock FROM cs_mall_pms.pms_sku WHERE id=%s", (sku_id,))
        row = cur.fetchone()
        if snap.get("stock") is not None and row and row["stock"] != snap["stock"]:
            bad.append(f"stock={row['stock']}≠{snap['stock']}")
        # 🆕 2026-09-12：**全量 sales** 也要校验（原先只校验 seckill_stock / stock ⇒ 跨命名空间的
        #   sales 改动**校验不到** → 会打印"✅ 全部回位"的假通过）
        for spu_id, before in (snap.get("all_sales") or {}).items():
            cur.execute("SELECT sales FROM cs_mall_pms.pms_spu WHERE id=%s", (spu_id,))
            row = cur.fetchone()
            if row and str(row["sales"]) != str(before):
                bad.append(f"pms_spu.sales[{spu_id}]={row['sales']}≠{before}")
    if r is not None:
        for k in (SECKILL_ORDER_LOCK, SECKILL_ORDERED, SECKILL_RE_SECKILL):
            try:
                if r.exists(k + f"{sku_id}:{user_id}"):
                    bad.append(f"残留键 {k}…")
            except Exception as e:      # noqa: BLE001
                bad.append(f"校验 Redis 失败：{e}")
        if snap.get("redis_stock") is not None:
            try:
                cur_v = r.cmd("GET", SECKILL_SKU_STOCK + str(sku_id))
                if str(cur_v) != str(snap["redis_stock"]):
                    bad.append(f"预热库存={cur_v}≠{snap['redis_stock']}")
            except Exception as e:      # noqa: BLE001
                bad.append(f"校验预热库存失败：{e}")
    return (not bad), ("；".join(bad) if bad else "全部回位")


def run_seckill_phase(api: "Api", conn, registry: Registry, users: List[Dict[str, Any]],
                      address: Dict[str, str], args, r: Optional[Redis]) -> Dict[str, int]:
    """秒杀阶段：每次动作都"快照 → 提交 → 登记 → **恢复** → 校验"（可用 `--no-seckill-restore` 关掉）"""
    stat = {"seckill_ok": 0, "seckill_fail": 0, "seckill_restored": 0, "seckill_verify_bad": 0}
    targets = seckill_targets(api, users[0]["token"])
    if not targets:
        log("⚠️ 没有**在秒杀时间窗内且未购买**的目标（`url` 为空 = 不在窗口内）→ 本次跳过秒杀动作")
        return stat
    log(f"🐝 秒杀目标 {len(targets)} 个（第一个：{targets[0]['name']} · randCode={targets[0]['rand_code']}）")

    # 🆕 2026-09-12：秒杀链路**会创建 oms_order / oms_order_item**（实测每次 +1/+1、`data_source=NULL`
    #   ⇒ 既不登记也不回填 ⇒ 清理必漏）。用"订单 id > 动作前最大值"增量反查，避开 DB 时区与异步时序。
    order_marks: List[Tuple[Any, int]] = []
    registered_orders: set = set()

    def _max_order_id(uid: Any) -> int:
        with conn.cursor() as c2:
            c2.execute("SELECT COALESCE(MAX(id),0) AS m FROM cs_mall_oms.oms_order WHERE user_id=%s", (uid,))
            return int(c2.fetchone()["m"] or 0)
    for i in range(1, args.seckill_count + 1):
        user = random.choice(users)
        target = random.choice(targets)
        try:
            skus = api.seckill_sku_list(user["token"], target["spu_id"]) or []
        except Exception as e:      # noqa: BLE001
            log(f"   ⚠️ 取秒杀 SKU 失败：{e}")
            stat["seckill_fail"] += 1
            continue
        skus = [s for s in skus if int(s.get("stock") or 0) > 0]
        if not skus:
            log("   ⚠️ 该 SPU 下没有库存 > 0 的秒杀 SKU → 跳过")
            stat["seckill_fail"] += 1
            continue
        sku = random.choice(skus)
        sku_id = int(sku["skuId"] if sku.get("skuId") is not None else sku.get("id"))
        price = float(sku.get("seckillPrice") or sku.get("price") or 0)
        snap = seckill_snapshot(conn, r, sku_id)
        order_marks.append((user["id"], _max_order_id(user["id"])))
        # 🆕 ⑤（2026-09-12）登记"秒杀标记键"的坐标 `sku:user` → clean() 才能**按 id 精确删** Redis 三类键
        #    ⚠️ 放在**提交之前**：即便提交失败，也可能已写下 `order:lock`（宁可多删一个不存在的键，也不漏删）
        try:
            registry.register(conn, "seckill_marks", f"{sku_id}:{user['id']}", "redis", str(user["id"]))
            conn.commit()
        except Exception as e:      # noqa: BLE001
            log(f"   ⚠️ 登记 seckill_marks 失败：{e}")
        dto = {
            "spuId": target["spu_id"],
            "contactName": f"模拟{random.randint(0, 99):02d}",
            "mobilePhone": FAKE_PHONE_PREFIX + f"{random.randint(0, 9999):04d}",
            "provinceCode": address["province_code"], "provinceName": address["province_name"],
            "cityCode": address["city_code"], "cityName": address["city_name"],
            "districtCode": address["district_code"], "districtName": address["district_name"],
            "streetCode": address["street_code"], "streetName": address["street_name"],
            "detailedAddress": f"模拟地址 {random.randint(1, 999)} 号",
            "paymentType": 2,
            "amountOfOriginalPrice": price, "amountOfFreight": 0.0,
            "amountOfDiscount": 0.0, "amountOfActualPay": price,
            "seckillOrderItemAddDTO": {
                "skuId": sku_id, "title": sku.get("title"), "barCode": sku.get("barCode"),
                "data": "{}", "mainPicture": first_picture(sku.get("pictures")) or "",
                "price": price, "quantity": 1,
            },
        }
        try:
            vo = api.seckill_commit(user["token"], target["rand_code"], dto)
            stat["seckill_ok"] += 1
        except Exception as e:      # noqa: BLE001
            log(f"   ⚠️ 秒杀 {i} 失败：{e}")
            stat["seckill_fail"] += 1
            vo = None
        # 登记 `success`（G7 式防御：**按 user_id+sku_id 反查**，不依赖响应字段）
        def _register_success() -> bool:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM cs_mall_seckill.success WHERE user_id=%s AND sku_id=%s "
                            "ORDER BY id DESC LIMIT 1", (user["id"], sku_id))
                row = cur.fetchone()
            if row:
                registry.register(conn, "success", row["id"], "cs_mall_seckill", user_ref=str(user["id"]))
                conn.commit()
                return True
            return False
        try:
            # 🔴 2026-09-12 修正：原写法 `if vo or _register_success(): _register_success()` 在
            #    "响应给了 vo、而行也已存在"时会**重复登记同一行**（clean 会重复删同一 pk，且计数虚高）
            if not _register_success():
                log("   ℹ️ success 行尚未落库（MQ 消费者**异步**写）→ 交给回填矩阵按 user_id 兜底（实测有效）")
        except Exception as e:      # noqa: BLE001
            log(f"   ⚠️ 登记 success 失败：{e}")
        if args.seckill_restore:
            # ⚠️ DB 侧由 MQ 消费者**异步**回写 ⇒ 等一拍再恢复/校验，避免"我们回填后它又改回来"
            time.sleep(args.seckill_restore_wait)
            acts = seckill_restore(conn, r, snap, user["id"])
            ok, why = seckill_verify(conn, r, snap, user["id"])
            if not ok:              # 异步写入后到 → 再补一次（只补一次，避免无限循环）
                acts += seckill_restore(conn, r, snap, user["id"])
                ok, why = seckill_verify(conn, r, snap, user["id"])
            stat["seckill_restored"] += 1
            if not ok:
                stat["seckill_verify_bad"] += 1
            log(f"   🧹 秒杀 {i} 恢复：{'；'.join(acts)} → "
                + ("✅ 校验通过" if ok else f"🔴 校验未通过（{why}）"))
    # 🧾 兜底：把本阶段**异步建出来的秒杀订单**登记上（否则 clean 会漏删、回填也标不到）
    for uid, max_before in order_marks:
        try:
            with conn.cursor() as cur:
                cur.execute("SELECT id FROM cs_mall_oms.oms_order WHERE user_id=%s AND id > %s", (uid, max_before))
                new_orders = [x["id"] for x in cur.fetchall()]
            for oid in new_orders:
                if oid in registered_orders:
                    continue
                registry.register(conn, "oms_order", oid, "cs_mall_oms", user_ref=str(uid))
                registered_orders.add(oid)
                with conn.cursor() as cur:
                    cur.execute("SELECT id FROM cs_mall_oms.oms_order_item WHERE order_id=%s", (oid,))
                    for it in cur.fetchall():
                        if it["id"] not in registered_orders:
                            registry.register(conn, "oms_order_item", it["id"], "cs_mall_oms", user_ref=str(uid))
                            registered_orders.add(it["id"])
                conn.commit()
        except Exception as e:      # noqa: BLE001
            log(f"   ⚠️ 兜底登记秒杀订单失败：{e}")
    if registered_orders:
        log(f"   🧾 兜底登记秒杀订单：新增 {len(registered_orders)} 条主键"
            "（秒杀会真建 oms_order/oms_order_item —— 不登记就会漏删、漏标）")
    return stat



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

    # 🆕 ⑤ Redis 精确清理（2026-09-12）：按**登记坐标** `sku:user` 删三类标记键
    #    🔴 禁止 `--scan --pattern` 全删 —— 会误伤真实数据的同名键
    marks = _registered(conn, batch, "redis", "seckill_marks")
    if not marks:
        log("  redis.seckill_marks                  无登记 → 跳过（不碰任何 Redis 键）")
    else:
        r = open_redis_for_clean()
        if r is None:
            log(f"  redis.seckill_marks                  ⚠️ 未设 SIM_REDIS_PASSWORD / 连不上 → 跳过（{len(marks)} 个坐标未清）")
        else:
            keys = [pre + pair for pair in marks
                    for pre in (SECKILL_ORDER_LOCK, SECKILL_ORDERED, SECKILL_RE_SECKILL)]
            existing = [k for k in keys if r.exists(k)]
            if apply_ and existing:
                r.cmd("DEL", *existing)
            r.close()
            log(f"  redis.seckill_marks                  {'已删' if apply_ else '将删'} {len(existing)} 个键"
                f"（登记 {len(marks)} 个坐标 × 3 类 = {len(keys)} 个候选）"
                + (f"：{existing}" if existing else " → 当前无残留（幂等）"))

    with conn.cursor() as cur:
        if apply_:
            cur.execute("UPDATE cs_mall_sim.sim_batch SET status='cleaned', finished_at=NOW() WHERE batch_id=%s", (batch,))
            conn.commit()
    log(f"{'已删除' if apply_ else '将删除'}合计 {total} 行（分批 {CHUNK}/批，单批事务）")

    # 🔴 清理后自检（全库 0 个外键 → 漏删**不会报错**，只能主动查）
    if apply_:
        # 🔴 2026-09-12：自检改**按批次口径**（原先查全局 SIM 计数 → 多批次共存必误报，见方案 §G G11）
        left: List[str] = []

        def _scoped(db_name: str, table: str, key_col: str, values: Sequence[Any]) -> int:
            """本批范围内**已标 SIM** 的行数"""
            n = 0
            for part in _chunks(values):
                ph = ",".join(["%s"] * len(part))
                n += _count_where(cur, f"{db_name}.{table}",
                                  f"data_source=%s AND {key_col} IN ({ph})",
                                  (DATA_SOURCE_SIM, *part))
            return n

        with conn.cursor() as cur:
            for db_name, table, key_col in BACKFILL_BY_PK:
                values = _registered(conn, batch, db_name, table)
                if not values:
                    continue
                n = _scoped(db_name, table, key_col, values)
                if n:
                    left.append(f"{db_name}.{table}={n}")
            for db_name, table in BACKFILL_BY_USER:
                if not user_ids or not has_column(conn, db_name, table, "data_source"):
                    continue
                n = _scoped(db_name, table, "user_id", user_ids)
                if n:
                    left.append(f"{db_name}.{table}={n}")
        if left:
            log(f"⚠️ **本批范围内**仍残留 SIM 标识行：{', '.join(left)} → 清理清单漏表或删除失败，请人工核对")
        else:
            log("✅ 清理后自检通过（**按批次口径**）：本批登记的 9 张表范围内已无 data_source='SIM' 残留")
            log("   ℹ️ 若库里还有其他批次，它们标 SIM 的行**不属于本批**，本自检不计入（避免 G11 式误报）")
    log("⚠️ 未处理**不可逆字段的值**（`pms_spu.sales` / `pms_sku.stock` / 秒杀预热键）——"
        "删行删不回累加值：需要绝对干净请走**整库还原快照**（方案 §2.2.3），或用 `--compare-baseline` 核差异；"
        "Redis 已按**登记坐标**精确删（禁止 pattern 全删）")


# =============================================================================
# ⑤ Redis 精确清理（clean 侧）：按登记坐标 `sku:user` 删，**禁止 pattern 全删**
# =============================================================================

def open_redis_for_clean() -> Optional[Redis]:
    """clean() 用的 Redis 连接：host/port 走环境变量（与造数侧同一套），连不上返回 None。"""
    pw = os.environ.get("SIM_REDIS_PASSWORD")
    if not pw:
        return None
    try:
        r = Redis(os.environ.get("SIM_REDIS_HOST", "172.29.193.239"),
                  int(os.environ.get("SIM_REDIS_PORT", "6379")), pw)
        r.cmd("PING")
        return r
    except Exception as e:      # noqa: BLE001
        log(f"  ⚠️ Redis 连接失败（{e}）→ Redis 部分跳过")
        return None


# =============================================================================
# ⑥ sim_baseline 基线比对（2026-09-12）：造数**前**记基线 → 清理**后**比对
# =============================================================================

# 🔴 2026-09-12 修正：`DATA_SOURCE_TABLES` 是 **(库, 表) 元组对**，不能直接当 SQL 表名用
#   （首版写成 `DATA_SOURCE_TABLES + (...)` → `FROM ('cs_mall_ums', 'ums_user')` → SQL 1064 语法错）
BASELINE_TABLES: Sequence[str] = tuple(f"{d}.{t}" for d, t in DATA_SOURCE_TABLES) + (
    "cs_mall_pms.pms_sku", "cs_mall_pms.pms_spu")

# metric → (表, 聚合列)；`row_count` 走 COUNT(*)，其余走 SUM(列)
BASELINE_AGG: Dict[str, Tuple[str, str]] = {
    "sum_stock": ("cs_mall_pms.pms_sku", "stock"),
    "sum_sales": ("cs_mall_pms.pms_spu", "sales"),
}


def _baseline_now(conn, target: str, metric: str) -> str:
    with conn.cursor() as cur:
        if metric == "row_count":
            cur.execute(f"SELECT COUNT(*) AS v FROM {target}")
        else:
            table, col = BASELINE_AGG[metric]
            cur.execute(f"SELECT COALESCE(SUM({col}),0) AS v FROM {table}")
        return str(cur.fetchone()["v"])


def baseline_save(conn, batch: str) -> Dict[str, str]:
    """造数**前**写基线：9 张标识表 + `pms_sku`/`pms_spu` 的行数，以及 **stock / sales 合计**。

    为什么要记 `stock`/`sales` 合计：它们是**不可逆的累加字段**（方案 §2.2.1），
    清理只能删行、删不回累加值 ⇒ 基线是判断"是否被改过、改了多少"的唯一对照。
    """
    out: Dict[str, str] = {}
    for target in BASELINE_TABLES:
        out[f"{target}|row_count"] = _baseline_now(conn, target, "row_count")
    for metric in BASELINE_AGG:
        table = BASELINE_AGG[metric][0]
        out[f"{table}|{metric}"] = _baseline_now(conn, table, metric)
    with conn.cursor() as cur:
        for key, value in out.items():
            target, metric = key.rsplit("|", 1)
            cur.execute("INSERT INTO cs_mall_sim.sim_baseline(batch_id,target,metric,value,created_at) "
                        "VALUES (%s,%s,%s,%s,NOW())", (batch, target, metric, value))
    conn.commit()
    return out


def baseline_compare(conn, batch: str) -> List[str]:
    """清理**后**与基线比对；返回差异清单（空 = 完全回到基线）。"""
    with conn.cursor() as cur:
        cur.execute("SELECT target, metric, value FROM cs_mall_sim.sim_baseline WHERE batch_id=%s", (batch,))
        base = {(r["target"], r["metric"]): r["value"] for r in cur.fetchall()}
    if not base:
        return [f"批次 {batch} 没有基线记录（造数时未带 --baseline）"]
    diffs: List[str] = []
    for (target, metric), value in sorted(base.items()):
        now = _baseline_now(conn, target, metric)
        if now != str(value):
            diffs.append(f"{target} {metric}：基线 {value} → 现在 {now}")
    return diffs

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
    ap.add_argument("--qps", type=float, default=0.0,
                    help="目标速率（>0 时**按它配速**，覆盖 sleep-min/max；例：--qps 5）。"
                         "⚠️ 写动作受 Sentinel 天花板（秒杀 10 / 订单 20 / 支付 20 QPS），超了会 429")
    ap.add_argument("--jitter", type=float, default=0.3,
                    help="--qps 配速的抖动比例（默认 0.3 = ±30%%，让流量不至于完全均匀）")
    ap.add_argument("--with-seckill", action="store_true",
                    help="附加秒杀阶段（2026-09-12 已实现：列表取 randCode → 提交 → 登记 → **秒杀后恢复**）")
    ap.add_argument("--seckill-count", type=int, default=5, help="秒杀动作次数（默认 5）")
    ap.add_argument("--no-seckill-restore", dest="seckill_restore", action="store_false",
                    help="🔴 **不恢复**秒杀后的数据（默认**恢复**：Redis 三类标记 + 预热库存 + DB 三值）")
    ap.add_argument("--seckill-restore-wait", type=float, default=1.5,
                    help="提交后等待多久再恢复（秒，默认 1.5）—— 给 MQ 消费者异步回写 DB 留出时间")
    ap.add_argument("--redis-host", default=os.environ.get("SIM_REDIS_HOST", "172.29.193.239"),
                    help="Redis **主库**地址（哨兵模式下必须直连主库；默认 172.29.193.239）")
    ap.add_argument("--redis-port", type=int, default=int(os.environ.get("SIM_REDIS_PORT", "6379")),
                    help="Redis 主库端口（默认 6379）")
    ap.add_argument("--preflight", action="store_true", help="只做预检，不写任何数据")
    ap.add_argument("--clean", action="store_true", help="清理模式（默认 dry-run）")
    ap.add_argument("--batch", help="清理指定批次号")
    ap.add_argument("--apply", action="store_true", help="清理时真正执行 DELETE（默认只统计）")
    ap.add_argument("--verify", action="store_true",
                    help="只校验指定批次的回填结果（需 --batch）：按批次口径，不造数、不写库；有问题退出码 1")
    ap.add_argument("--baseline", action="store_true",
                    help="⑥ 造数**前**记录基线（9 表行数 + stock/sales 合计）→ 清理后用 --compare-baseline 比对")
    ap.add_argument("--compare-baseline", action="store_true",
                    help="⑥ 与 --batch 的基线比对（清理后跑）：有差异退出码 1")
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

        if args.verify:
            if not args.batch:
                sys.exit("--verify 必须带 --batch sim_YYYYMMDD_HHMM")
            problems = verify_backfill(conn, args.batch)
            if problems:
                log(f"⚠️ 批次 {args.batch} 回填校验未通过（{len(problems)} 条）：")
                for p in problems:
                    log(f"   - {p}")
                sys.exit(1)
            log(f"✅ 批次 {args.batch} 回填校验通过：登记 ⇄ data_source 标识 一致（按批次口径）")
            return

        if args.compare_baseline:
            if not args.batch:
                sys.exit("--compare-baseline 必须带 --batch sim_YYYYMMDD_HHMM")
            diffs = baseline_compare(conn, args.batch)
            if diffs:
                log(f"⚠️ 批次 {args.batch} 与基线不一致（{len(diffs)} 项）：")
                for d in diffs:
                    log(f"   - {d}")
                sys.exit(1)
            log(f"✅ 批次 {args.batch} 已完全回到基线（行数 + stock/sales 合计一致）")
            return

        catalog = preflight(conn, args.days, args.per_day, args.with_seckill, args.users)
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
        if args.baseline:
            # ⑥ 基线必须在**任何写入之前**采（下面 ensure_users 就开始写了）
            base = baseline_save(conn, batch)
            log(f"📐 基线已记录（{len(base)} 项 = {len(BASELINE_TABLES)} 张表行数 + stock/sales 合计）"
                f" → 清理后跑 `--compare-baseline --batch {batch}` 比对")

        api = Api(args.base)
        # 🆕 2026-09-12：用户池真实感告警（§九：20 人 × 每天 1000 行为 = 每人 50 次动作，真人远低于此）
        if args.per_day > 0 and args.users < max(4, args.per_day // 4):
            log(f"⚠️ 用户池偏小：{args.users} 人 × 每天 {args.per_day} 行为 = 每人每天 "
                f"{args.per_day / max(1, args.users):.0f} 次动作（真人一天十几次量级）→ "
                f"建议 `--users` ≈ per_day/8 = {max(4, args.per_day // 8)}")
        users = ensure_users(api, conn, registry, args.users)
        stat = run_funnel(api, conn, registry, catalog, users, args.days, args.per_day,
                         args.sleep_min, args.sleep_max, args.with_seckill,
                         qps=args.qps, jitter=args.jitter)

        # 🐝 秒杀阶段（#67 之 ④）：每次动作都"快照 → 提交 → 登记 → **恢复** → 校验"
        if args.with_seckill:
            # G7 教训：地址模板**只读一次**且读后 commit（否则 REPEATABLE READ 会把快照定格）
            address = load_address_template(conn)
            conn.commit()
            r = open_redis(args)
            seck = run_seckill_phase(api, conn, registry, users, address, args, r)
            stat.update(seck)
            if r is not None:
                r.close()
            if args.seckill_restore:
                log(f"🧹 秒杀后恢复：{seck['seckill_restored']} 次；"
                    f"校验未通过 {seck['seckill_verify_bad']} 次"
                    + ("（✅ 全部回位）" if seck["seckill_verify_bad"] == 0 else "（🔴 需人工核对）"))

        # 🏷️ 回填专用列标识（**服务端零改动**）：造完数按影子登记表补写 data_source='SIM'
        log("🏷️ 回填 data_source='SIM'（按登记表；服务端零改动）…")
        marked = backfill(conn, batch)
        for k, v in sorted(marked.items()):
            log(f"   {k:38s} 已标 {v} 行")
        log(f"   合计标记 {sum(marked.values())} 行")

        # 校验"回填是否真的发生"（方案可行性边界①）—— 有问题只报不阻断，但必须处理
        problems = verify_backfill(conn, batch)
        if problems:
            log("⚠️ 回填校验**未通过**（数据已造出，请先处理再进入清理/展示）：")
            for p in problems:
                log(f"   - {p}")
        else:
            log("✅ 回填校验通过：登记 ⇄ data_source 标识 一致")

        note = (f"{json_stat(stat, registry)}; data_source_marked={sum(marked.values())}; "
                f"backfill_problems={len(problems)}")
        with conn.cursor() as cur:
            cur.execute("UPDATE cs_mall_sim.sim_batch SET status='finished', finished_at=NOW(), "
                        "done_actions=%s, note=CONCAT(COALESCE(note,''), ' | ', %s) WHERE batch_id=%s",
                        # done_actions = 真正成功的业务动作数（浏览+加购+下单；
                        # 支付失败不算动作失败，订单已建成 → 不计入 done 也不计入 fail）
                        (stat["browse"] + stat["cart"] + stat["order"], note, batch))
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
