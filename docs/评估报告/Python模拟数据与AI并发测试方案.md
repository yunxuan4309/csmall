# Python 模拟数据 + AI 并发测试方案（规范设计版）

> **创建日期**：2026-08-26（初稿）
> **规范设计定稿**：2026-09-11
> **状态**：✅ **规范设计已定稿，待实施**（对应 TODO **#48**）
> **目的**：① 用 Python 脚本模拟真实业务数据积累（浏览/加购/下单/秒杀），让系统看起来像运营中的电商；② 在不调用真实 DeepSeek API 的前提下，测试 AI 导购模块能扛住多少并发
> **定位**：与 JMeter 压测互补——JMeter 测性能尖峰（100 并发秒杀 + Sentinel 限流），本方案造业务数据沉淀 + 测 AI 服务端并发承载
> **关联**：[[TODO文件]]#48、[[TODO已完成]]#29（cron 备份）/ #47（恢复演练）、[[服务器内存优化方案]]（R7 已完成）、[[运维手册--密钥密码轮换]]、[[阿里云ECS服务器情况]]

---

## 〇、本次规范设计改了什么（2026-09-11）

> 初稿（2026-08-26）的设计**没有数据隔离层**，只有"用户名前缀 + 事后 DELETE"；本次逐条读码 + 服务器实测后**重写**。**原始有价值内容（80/15/5 漏斗、mock LLM 思路、指标、面试话术）全部保留**。

| # | 初稿问题 | 证据（2026-09-11 实测） | 本次修正 |
|---|---|---|---|
| ① | **清理 SQL 顺序错误 → 订单删不掉**：先 `DELETE ums_user`，再 `DELETE oms_order WHERE user_id IN (SELECT id FROM ums_user WHERE username LIKE 'test_sim_%')` —— 用户已删，子查询返回**空集** | 初稿 §2.4 原文 | 改为**逆序删除**（子表→主表）+ 登记表驱动（§2.2.5） |
| ② | **只靠前缀删不掉"不可逆污染"** | `pms_spu.sales` 已有 **81 / 1 / 1**…（累加值，**无"谁贡献多少"记录**）；`pms_sku.stock` iPhone 各 30/25/20/15；`seckill_sku.seckill_stock` 25~150（有 1 个已 0）；Redis `mall:seckill:sku:stock:*` **12 个** | 新增**快照回滚兜底**（§2.2.3 / §2.2.7） |
| ③ | **Redis 清理会误伤真实数据**：`--scan --pattern 'mall:seckill:reseckill:*' \| xargs del` 删**全部**用户的购买标记 | 初稿 §2.4 | 改为**按登记的用户 id 精确删**（§2.2.6） |
| ④ | **全库 0 个外键** → 删除顺序无数据库保护，漏表即静默残留 | `information_schema` 外键数 = **0**；含 `user_id` 的表实测 **7 张** | 清理清单**精确到 9 张表**（§2.5） |
| ⑤ | **mock LLM 返回格式不足以压 SSE**：初稿 mock 只返回单个 JSON | `ChatServiceImpl.doStreamDeepSeek` 按 `line.startsWith("data: ")` 解析（SSE 分片）→ 普通 JSON 响应**收不到任何 chunk** | mock 必须**实现 SSE 流式格式**（§3.2） |
| ⑥ | **mock 部署位置错误**：初稿让容器访问 `127.0.0.1:9999` | `csmall-ai` 在容器内，`127.0.0.1` = **容器自身**，不是开发机 | mock 部署在**新机**（内网可达），用 **compose override 注入环境变量**，不改 prod yml（§3.2） |
| ⑦ | **压测脚本"放本机跑"** 与 2026-09-09 新要求**矛盾** | 老机 **5 Mbps 固定带宽** | 脚本**必须跑在新机/内网**（§3.4 / §五） |
| ⑧ | **未覆盖** 2026-09-09 补充需求：~100 req/s + 每日 12 点高峰 + cron | TODO #48 | 新增 §3.4 |
| ⑨ | 内存基线 2.1G / 无 Swap / "先做 R7" | R7（2026-09-07）**已执行**：mem_limit 全容器 + Nacos 降堆 + **Swap 2G**；2026-09-11 实测 **available 3.6G** | §四 更新为实测；执行顺序删掉"先做 R7" |

---

## 一、方案总览（两层解耦，可独立执行）

```
┌─ 第一层：模拟数据生成（业务数据积累）────────────────────┐
│  Python 脚本 → 登录/浏览/加购/下单/支付/秒杀               │
│      ├─ 写业务表（生产库，受控）                          │
│      └─ 写「影子登记表」cs_mall_sim.sim_entity（每个实体一行）│  ← 🆕 清理与审计的唯一依据
└───────────────────────────────────────────────────────┘
┌─ 第二层：AI 并发测试（不调真实 API）─────────────────────┐
│  Python 并发 → /ai/chat/stream（SSE）                    │
│  经内网打到服务器上的 mock LLM（替代 DeepSeek）            │
└───────────────────────────────────────────────────────┘
```

> **两层可分别执行**：第一层慢节奏、低风险；第二层（压测）对带宽/内存敏感。**建议先做第一层**（不受压测资源约束）。
> **硬安全网**：第一次真正造数**之前**必须先做 `mysqldump` 全量快照（§2.2.3）。

---

## 二、第一层：模拟数据生成

### 2.1 核心原则：按真实用户行为分布（不是纯随机）

| 行为 | 占比 | 说明 |
|------|------|------|
| 浏览/搜索商品 | 80% | 只 GET 商品列表/详情/搜索，不产生订单 |
| 加入购物车 | 15% | POST 购物车，但不结算 |
| 下单+支付 | 5% | 完整流程：下单 → 支付（模拟支付模式） |
| 秒杀 | 按需 | 对齐时间窗口，限购逻辑 |

> 每天 1000 条"行为"里，只有约 50 单——**这才像真实电商**（订单转化率约 5%）。
> 面试话术："我按真实用户漏斗（浏览→加购→下单 80/15/5）模拟了 N 天数据"

### 2.2 🔴 数据隔离设计（本次新增 · 核心）

#### 2.2.1 为什么必须要有隔离层

造数会**不可逆地**改动三类东西，靠"删掉模拟用户"**救不回来**：

| 类别 | 实测现状（2026-09-11） | 造数后果 | 能否靠删用户还原 |
|---|---|---|---|
| **累加计数器** | `pms_spu.sales`：81 / 1 / 1 … | 下单/秒杀**累加** sales | ❌ **不能**（没有"谁贡献多少"的记录） |
| **真实库存** | `pms_sku.stock`：30/25/20/15；`seckill_sku.seckill_stock`：25~150（1 个已 0） | 下单扣库存 | ❌ 不能（需人工补回，易算错） |
| **Redis 状态** | `mall:seckill:sku:stock:*` **12 个**预热库存键 | 秒杀扣减预热库存 | ❌ 不能（与实际库存偏差累积） |
| 可追踪实体 | `ums_user` 110 / `oms_order` 86 / `success` 58 | 新增用户/订单/购物车/成功记录 | ✅ 能（按登记精确删） |

> **结论**：可追踪实体用"登记表精确删"，**不可逆字段交给快照整体还原**。

#### 2.2.2 方案选型

| 方案 | 做法 | 规范度 | 成本 | 评价 |
|---|---|---|---|---|
| **A. 影子库 / 影子表** | 造数写 `cs_mall_sim` 或 `*_sim` 表，服务切数据源 | ★★★★★ | 🔴 **极高**——11 个服务的表名散在 Mapper XML/注解里，要动态表名或多数据源改造，**远超造数本身** | ❌ 本项目不划算 |
| **B. 快照回滚** | dump → 造数 → 事后**整库还原** | ★★★★☆ | 🟢 低——**项目已有验证过的能力**（#29 cron 备份 + #47 独立临时容器恢复演练） | ✅ **兜底主线** |
| **C. 影子登记表** | 独立 schema `cs_mall_sim` 建 `sim_batch` / `sim_entity`，记录每个被创建实体的主键 | ★★★★☆ | 🟢 低——**不改 6 个业务库的表结构**，只加一个独立库 2 张表 | ✅ **清理主线** |

#### 2.2.3 推荐组合 = **C（清理主线）+ B（兜底）**

```
① 快照      造数前：mysqldump 6 库全量（复用 #29 脚本）→  备份号写进 sim_batch 备注
② 登记      造数时：每创建一个实体，往 cs_mall_sim.sim_entity 写一行（批次 + 库 + 表 + 主键）
③ 清理      按登记表**逆序**删（子表→主表）+ 分批 + 幂等 + dry-run 预览
④ Redis     按登记的用户 id **精确删**（禁止 pattern 全删）
⑤ 计数器    sales / stock / 秒杀预热键**不做"减回去"** → 需要绝对干净时走 ① 的**整库还原**
⑥ 校验      删完对比"基线快照"（行数 + 计数器），有差异即报告，不静默
```

> 这样既**避开了方案 A 的天坑**（不改 11 个服务的表名），又拿到了影子表的核心价值：**可审计、可重放、可精确回滚、生产表零结构变更**。
> 计数器这类"累加不可逆"字段交给快照整库还原——这正是"影子"思想在数据层的等价物：**先存真身，再在真身上动作，事后整体还原**。

#### 2.2.4 影子登记表 DDL（独立 schema，与业务库隔离）

```sql
-- 独立库：不碰 6 个业务库的任何表结构（实测 2026-09-11：cs_mall_sim 尚不存在）
CREATE DATABASE IF NOT EXISTS cs_mall_sim DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 批次：一次造数运行
CREATE TABLE IF NOT EXISTS cs_mall_sim.sim_batch (
  batch_id     VARCHAR(40)  NOT NULL COMMENT '批次号，如 sim_20260911_1530',
  started_at   DATETIME     NOT NULL,
  finished_at  DATETIME     NULL,
  days         INT          NOT NULL DEFAULT 0 COMMENT '模拟天数',
  per_day      INT          NOT NULL DEFAULT 0 COMMENT '每天行为数',
  done_actions BIGINT       NOT NULL DEFAULT 0,
  dump_file    VARCHAR(255) NULL COMMENT '造数前全量快照文件名（兜底用）',
  status       VARCHAR(16)  NOT NULL DEFAULT 'running' COMMENT 'running/finished/cleaned',
  note         VARCHAR(500) NULL,
  PRIMARY KEY (batch_id)
) ENGINE=InnoDB COMMENT='模拟数据批次（影子登记）';

-- 实体登记：每个被创建的实体一行 —— 清理的唯一依据
CREATE TABLE IF NOT EXISTS cs_mall_sim.sim_entity (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  batch_id   VARCHAR(40) NOT NULL,
  db_name    VARCHAR(64) NOT NULL COMMENT 'cs_mall_ums / cs_mall_oms / ...',
  table_name VARCHAR(64) NOT NULL,
  pk_value   VARCHAR(64) NOT NULL COMMENT '主键值（字符串存，兼容雪花 ID / 自增）',
  user_ref   VARCHAR(64) NULL     COMMENT '关联的模拟用户名，便于按用户精确清 Redis',
  created_at DATETIME    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_batch (batch_id),
  KEY idx_target (db_name, table_name),
  KEY idx_user (user_ref)
) ENGINE=InnoDB COMMENT='模拟实体登记（影子表）';
```

#### 2.2.5 清理算法（逆序 · 分批 · 幂等 · 可预览）

**表依赖顺序（子 → 父，逆序即删除顺序）**——实测表名（2026-09-11）：

```sql
-- 删除顺序（严格按此逆序；全库无外键，漏一张就静默残留）
-- 1) cs_mall_oms.oms_order_item            （按 order_id 关联，表本身无 user_id）
-- 2) cs_mall_seckill.success               （有 user_id）
-- 3) cs_mall_seckill.seckill_message_retry （有 user_id）
-- 4) cs_mall_oms.oms_payment_record        （有 user_id）
-- 5) cs_mall_oms.oms_cart                  （有 user_id）
-- 6) cs_mall_oms.oms_order                 （有 user_id）
-- 7) cs_mall_resource.res_upload_record    （有 user_id；本方案若不传图可跳过）
-- 8) cs_mall_ums.ums_login_log             （有 user_id）
-- 9) cs_mall_ums.ums_user                  （按 username LIKE 'test_sim_%'）
```

**算法要点**：

| 点 | 做法 |
|---|---|
| **驱动** | 以 `sim_entity(batch_id, db, table, pk)` 为准，**不靠 LIKE 猜** |
| **逆序** | 按上表顺序 1→9；`oms_order_item` 用登记到的 order_id 反查 |
| **分批** | 每批 `LIMIT 500`，避免长事务锁表 |
| **幂等** | 重复执行不报错（删不到就跳过）；`sim_batch.status` 置 `cleaned` |
| **dry-run** | 默认只**打印**将删除的行数（`SELECT COUNT(*)`），加 `--apply` 才真删 |
| **事务** | 单批内事务；失败即停并报告（不静默继续） |
| **顺序修正** | 🔴 绝不"先删用户再按用户查订单"（初稿的 bug，见 §〇①） |

#### 2.2.6 Redis 清理（精确删，禁止 pattern 全删）

```bash
# ❌ 禁止（会删掉所有用户的购买标记，误伤真实数据）
# redis-cli --scan --pattern 'mall:seckill:reseckill:*' | xargs redis-cli del

# ✅ 正确：先取出本批次的模拟用户 id 列表，再按 id 拼接精确 key 删除
#    键结构以 SeckillCacheUtils 为准：<prefix>:<skuId>:<userId>
#    reseckill / ordered / orderLock 三类均按 batch 登记的用户逐一 DEL
```

> 涉及键（`SeckillCacheUtils`）：`reseckill`、`ordered`、`orderLock`；另有 `mall:seckill:sku:stock:*`（**预热库存，属不可逆**，交快照兜底，见 §2.2.7）。

#### 2.2.7 不可逆字段的处理原则

**不做"减回去"运算**——`sales` 是累加、库存扣减还并发（补偿运算既易错又不安全）。

| 字段 | 处置 |
|---|---|
| `pms_spu.sales` | 接受偏差；演示要求"干净"时走整库还原 |
| `pms_sku.stock` / `seckill_sku.seckill_stock` | 同上 |
| Redis `mall:seckill:sku:stock:*` | 同上（还原后需重新预热，走 `SeckillInitialJob`） |

#### 2.2.8 造数 SOP（三段式）

```
【造数前】
 1. 确认基线干净：SELECT COUNT(*) FROM cs_mall_ums.ums_user WHERE username LIKE 'test_sim_%'  → 0（2026-09-11 实测 = 0 ✅）
 2. mysqldump 6 库全量 → 记录文件名到 sim_batch.dump_file
 3. 记录"基线快照"（行数 + 计数器）到临时表/文件，供清理后比对
 4. 确认低峰时段（避开每日 12 点高峰窗口）

【造数中】
 5. 按 80/15/5 慢节奏执行；每创建一个实体写一行 sim_entity
 6. 实时观察 docker stats / free -h

【造数后】
 7. dry-run 清理预览 → 确认影响行数
 8. 需要干净环境 → 走整库还原（复用 #47 的独立临时容器验证法先验证备份可用）
 9. 记录结果到 sim_batch（finished / cleaned）
```

### 2.3 脚本骨架（含登记 + dry-run）

```python
"""
simulate_data.py — CoolShark 业务数据模拟器（规范设计版）
用法:
  python simulate_data.py --days 30 --per-day 1000            # 造数（登记每个实体）
  python simulate_data.py --clean --batch sim_20260911_1530    # 预览将清理什么（dry-run）
  python simulate_data.py --clean --batch ... --apply          # 真正删除
"""
import argparse, random, time, uuid, datetime, requests, pymysql

BASE       = "http://172.29.193.239:10087"   # ⚠️ 内网 + Gateway；脚本必须在内网跑（5Mbps 约束，见 §3.4）
USER_PREFIX = "test_sim_"
BATCH      = "sim_" + datetime.datetime.now().strftime("%Y%m%d_%H%M")

SIM_DB = dict(host="172.29.193.239", port=3306, user="root", password="***",
              database="cs_mall_sim", charset="utf8mb4")

def register(db, table, pk, user_ref=None):
    """影子登记：每个被创建的实体写一行 —— 清理的唯一依据"""
    with pymysql.connect(**SIM_DB) as c, c.cursor() as cur:
        cur.execute(
            "INSERT INTO sim_entity(batch_id,db_name,table_name,pk_value,user_ref,created_at)"
            " VALUES(%s,%s,%s,%s,%s,NOW())", (BATCH, db, table, str(pk), user_ref))
        c.commit()

def login(username, password="123456"):
    r = requests.post(f"{BASE}/user/sso/login", json={"username": username, "password": password})
    return r.json()["data"]["token"]

def browse(token, spu_ids):  ...        # 80%：只读，不产生业务数据
def add_cart(token, sku_id, qty):  ...  # 15%：登记 oms_cart.id
def create_order(token, sku_id, qty, contact):  # 5%：登记 oms_order.id / oms_order_item.id
    ...
def seckill(token, sku_id, rand_code):  ...     # 登记 success.id

def main(days, per_day, clean, batch, apply_):
    if clean:
        preview_or_delete(batch, apply_)   # 逆序 + 分批 + 幂等；不 apply 则只打印行数
        return
    # 建批次行 → 预置 test_sim_ 用户（登记 ums_user.id）→ 按 80/15/5 循环
    ...
```

### 2.4 关键设计点（必须遵守）

| 点 | 说明 |
|----|------|
| **可清理** | 三层保障：`test_sim_` 前缀（人眼可辨）+ **影子登记表**（机器精确）+ **快照**（兜底不可逆字段） |
| **秒杀对齐窗口** | 当前 6 场全年有效（2026-01-01~12-31），脚本随机时间即可；跨年后需先更新窗口 |
| **秒杀限购** | 同一用户同一 SKU 支付后**永久不能再买**（`reseckill` 永久标记 + `success` 唯一索引）→ 脚本要换 SKU 或换用户。⚠️ 已知局限见 TODO **#3**（场次维度购买标记） |
| **支付用模拟模式** | 项目已支持模拟支付（`simulated: true`），脚本走模拟支付即可，不碰支付宝沙箱 |
| **节奏控制** | 默认慢节奏（sleep 随机 0.5~3s），模拟真实用户；需要并发时再开多线程（§3.4） |
| **幂等** | 下单请求 contactName 用随机值，避免 `@Idempotent` Key 冲突 |
| **跑哪台** | 🔴 脚本+数据库客户端都在**新机/内网**（§3.4） |

### 2.5 清理清单（精确到表）

```sql
-- 以批次驱动（示意；实际由 sim_entity 生成 IN 列表并分批）
-- 1
DELETE FROM cs_mall_oms.oms_order_item  WHERE order_id IN (<本批 order_id 列表>);
-- 2
DELETE FROM cs_mall_seckill.success     WHERE user_id IN (<本批 user_id 列表>);
-- 3
DELETE FROM cs_mall_seckill.seckill_message_retry WHERE user_id IN (<本批 user_id 列表>);
-- 4
DELETE FROM cs_mall_oms.oms_payment_record WHERE user_id IN (<本批 user_id 列表>);
-- 5
DELETE FROM cs_mall_oms.oms_cart        WHERE user_id IN (<本批 user_id 列表>);
-- 6
DELETE FROM cs_mall_oms.oms_order       WHERE user_id IN (<本批 user_id 列表>);
-- 7
DELETE FROM cs_mall_resource.res_upload_record WHERE user_id IN (<本批 user_id 列表>);
-- 8
DELETE FROM cs_mall_ums.ums_login_log   WHERE user_id IN (<本批 user_id 列表>);
-- 9（最后）
DELETE FROM cs_mall_ums.ums_user        WHERE username LIKE 'test_sim_%';
```

> ⚠️ **演示前是否清理要想清楚**：压测卖点（100 并发 0 超卖）依赖干净数据；几万条模拟订单会让列表页变慢、秒杀"已购买"误伤。
> ⚠️ 但**"造了数据"本身就是面试素材**（"我按真实漏斗造了 N 天运营数据"）——建议**保留一份"演示数据快照"**，需要干净环境时再还原。

---

## 三、第二层：AI 并发测试

### 3.1 为什么不能直接压真实 API

- **有真实成本**：`TokenBudgetService` 全局 2 元/天预算，压 1000 并发瞬间打满 → 真实使用没额度
- **速度瓶颈在 DeepSeek**：SSE 流式接口的响应速度取决于 DeepSeek 处理时间，**不是你的服务能力**——压出来的"慢"是模型慢，不是后端慢

### 3.2 正确做法：内网 mock LLM，测服务端承载

| 要素 | 正确做法 | 为什么 |
|---|---|---|
| **mock 放哪** | 部署在**新机** `172.29.193.240:9999`（内网，老机可达） | 容器内 `127.0.0.1` = **容器自身**；放开发机则要么走公网（5Mbps）要么访问不到 |
| **怎么接** | **compose override 文件**注入环境变量：`COOXIAO_AI_BASEURL=http://172.29.193.240:9999` + `COOXIAO_AI_APIKEY=sk-mock`；测完 `docker compose up -d mall-ai` 恢复 | **不改 prod yml、不入库**；一次 `up -d mall-ai` 即可回滚 |
| **mock 必须实现什么** | ① **非流式**：`choices[0].message.content`（供 `doChat`/`chatWithModel`）<br>② 🔴 **流式 SSE**：`data: {...}\n\n` 分片 + 末尾 `data: [DONE]`（供 `/ai/chat/stream`）<br>③ 可选：带 `usage` 的末尾 chunk（供预算记账） | `doStreamDeepSeek` 按 `line.startsWith("data: ")` 解析——**返回普通 JSON 会收不到任何 chunk** |
| **压什么接口** | `/ai/chat/stream`（SSE 长连接，最主要的承载瓶颈） | 见 §3.3 |

```python
# mock_llm.py（骨架）—— 注意：必须支持 stream=true 的 SSE 分片响应
from http.server import BaseHTTPRequestHandler, HTTPServer
import json, time

CONTENT = "这是一条模拟的 AI 导购回复，用于并发测试。"

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        req = json.loads(self.rfile.read(n) or b"{}")
        if req.get("stream"):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            for piece in [CONTENT[i:i+6] for i in range(0, len(CONTENT), 6)]:
                chunk = {"choices": [{"delta": {"content": piece}}]}
                self.wfile.write(f"data: {json.dumps(chunk, ensure_ascii=False)}\n\n".encode())
                self.wfile.flush(); time.sleep(0.05)          # 模拟逐字输出
            usage = {"choices": [], "usage": {"prompt_tokens": 50, "completion_tokens": 30}}
            self.wfile.write(f"data: {json.dumps(usage)}\n\n".encode())
            self.wfile.write(b"data: [DONE]\n\n"); self.wfile.flush()
        else:
            time.sleep(0.3)
            body = json.dumps({"choices": [{"message": {"content": CONTENT},
                                            "finish_reason": "stop"}],
                               "usage": {"prompt_tokens": 50, "completion_tokens": 30}}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers(); self.wfile.write(body)

HTTPServer(("0.0.0.0", 9999), Handler).serve_forever()
```

**方案 B（备选）**：不走接口，脚本直接写 `ai:chat:session:*` 到 Redis——只测存储层，**价值低**，不推荐作为主路径。

### 3.3 要测的指标（面试可讲）

| 指标 | 意义 |
|------|------|
| 并发 SSE 连接数上限 | Tomcat 默认 200 线程，SSE 长连接占线程 → 线程池是主要瓶颈 |
| **并发闸门命中率** | `AiConcurrencyGuard` 上限 **20**，超出走降级（`AiBusyException`）→ 这是**真实保护点**，要量化"多少并发开始降级" |
| 单请求内存增量 | SSE 流式输出 + 会话 JSON 在内存中累积 |
| Redis 写入 QPS | `SessionManager.save` 每次对话写 Redis（24h TTL） |
| 内存峰值 | 见 §四 |

### 3.4 🆕 真实并发模拟（2026-09-09 用户补充需求）

**目标**：真实并发 ~100 req/s + **每日 12 点高峰窗口**（cron 定时触发）。

**三条硬约束（必须遵守）**：

| 约束 | 原因 | 做法 |
|---|---|---|
| 🔴 **脚本跑在新机/内网** | 老机 **5 Mbps 固定带宽**，本机压自己 = 自压自伤，数据无意义 | 脚本部署 `172.29.193.240`，打老机**内网 IP** `172.29.193.239`（不限速不计费）；**禁止走公网 IP** |
| 🔴 **负载画像要避开限流** | Sentinel 已拦：秒杀 QPS=**10**、`ai-chat`=**5** / `ai-reason`=**10** / `ai-light`=**30** → 压这些接口只会得到 429，测不到服务能力 | 主压**浏览 / 加购 / 普通下单**（无限流规则）；秒杀/AI 另做**限流验证**（期望 429），不作为承载指标 |
| 🔴 **阶梯加压 + 盯资源** | 老机 4C16G、available 3.6G | **20 → 50 → 100 req/s** 阶梯；每档盯 `docker stats` + `free -h`，出现 OOM 迹象立即停 |

**与"造数"的关系**：造数是**慢节奏、长时间**沉淀数据；高峰压测是**短时高压**验证承载。**两者用同一套脚本的不同模式**，但**不要同时跑**。

---

## 四、服务器资源评估（2026-09-11 实测更新）

| 指标 | 实测值 | 状态 |
|---|---|---|
| 总内存 | 14 GiB | — |
| available | **3.6 GiB**（R7 前为 2.1 GiB） | 🟢 可用（R7 已生效） |
| Swap | **2.0 GiB**（R7 前为 0） | 🟢 有缓冲垫 |
| 容器 mem_limit | **全 21 容器已设**（R7） | 🟢 单容器失控不会吃满宿主 |
| 磁盘 | 99G / 已用 49G（52%） | 🟢 充裕（造数前先看增长） |
| 带宽 | **5 Mbps 固定** | 🔴 压测必须走内网 |

**结论（对照初稿评估）**：

| 场景 | 可行性 | 说明 |
|---|---|---|
| 模拟数据生成（慢节奏，1 请求/秒级） | ✅ 可行 | 负载低；造数时关注磁盘与表增长 |
| AI 并发 20（闸门上限） | ✅ 可行 | 闸门=20，超出即降级——**这本身就是可讲的指标** |
| AI 并发 50~100 | ⚠️ 需阶梯试探 | 先确认 `free -h` ≥ 3G；必要时临时停 SkyWalking（省 ~1.5G），测完恢复 |

> 初稿的"内存大头表"（Nacos 980M / product 945M / OAP 1.1G / ES 1.1G）为 **2026-08-26 快照**；R7 已对 Nacos 降堆并给全部容器加 mem_limit，实际占用已变化，**以现场 `docker stats` 为准**。

---

## 五、执行顺序（2026-09-11 更新）

```
【准备】
 1. 写模拟脚本 + 影子登记表 DDL + mock LLM（本地验证通，含 dry-run 清理预览）
 2. 在新机建 cs_mall_sim + 两张登记表；部署 mock LLM（内网 9999）
【第一层：造数】
 3. 确认基线干净（test_sim_% = 0）→ mysqldump 全量快照 → 记录基线快照
 4. 慢节奏造数（1~2 天，每天 1000 行为）→ 检查数据沉淀 + 登记表行数对得上
 5. dry-run 清理预览 → 演练一次"清理 → 比对照基线"
【第二层：AI 并发】
 6. override 注入 mock base-url → 重建 mall-ai → 并发 20 → 50 → 100 阶梯压测（盯 docker stats / free -h）
 7. 测完移除 override → 重建 mall-ai 恢复真实 API
【收尾】
 8. 决定：保留"演示数据" / 或整库还原到快照（复用 #47 独立容器先验证备份）
 9. 面试演示素材：模拟数据统计 + AI 并发/闸门降级结果 + 限流 429 验证
```

> ~~初稿的"步骤 2：服务器内存优化 R7，找 ecs-user 执行"~~ —— **R7 已于 2026-09-07 执行完毕，该步删除**。

---

## 六、纪律与安全清单（实施时逐条打勾）

- [ ] **默认 dry-run**：清理必须 `--apply` 才真删；先看行数预览
- [ ] **快照先行**：第一次造数前必须有当日/即时 `mysqldump`（6 库全量）
- [ ] **低峰执行**：避开每日 12 点高峰窗口与维护窗口
- [ ] **基线确认**：跑前 `test_sim_%` = 0；跑后登记表行数 = 实际新增实体数
- [ ] **禁止 pattern 删 Redis**：只按登记用户 id 精确删
- [ ] **清理顺序**：严格 9 张表逆序；分批 500；单批事务
- [ ] **不可逆字段不硬算**：`sales`/`stock`/预热键 交给快照还原，不做补偿运算
- [ ] **清理后校验**：行数对比基线；有差异**报告而非静默**
- [ ] **压测走内网**：脚本在新机；禁止公网 IP；禁止老机自压
- [ ] **阶梯加压**：20 → 50 → 100；每档看 `docker stats` + `free -h`
- [ ] **测完恢复**：移除 override、重建 mall-ai、恢复 SkyWalking（若停）
- [ ] **`cs_mall_sim` 不入业务库迁移**：它是**运维/测试辅助库**，不进任何服务的数据源

---

## 七、面试话术（可直接用）

> "造演示数据这事儿，我没有直接往生产库里灌——那会污染三个**不可逆**的东西：商品销量、真实库存、还有 Redis 的秒杀预热库存，事后删用户根本救不回来。我做了一套**规范隔离**：造数前先全量快照；造数时每创建一个实体就往一个**独立影子登记库** `cs_mall_sim` 写一行，清理完全由登记表驱动——**逆序删、分批、幂等、默认 dry-run**；Redis 也是按登记的模拟用户 id **精确删**，不是按 pattern 全删（那会误伤真实用户的购买标记）。至于销量/库存这种累加值我不做'减回去'的补偿运算，容易算错还不安全，直接走快照整库还原兜底。
> 压测我也是把脚本放在**内网另一台机器**上打的——生产带宽只有 5Mbps，本机压自己是自压自伤，数据没意义。而且我**刻意避开**已有 Sentinel 限流的秒杀和 AI 接口，压的是浏览/加购/普通下单；秒杀和 AI 的限流我单独验证 429，不作为承载指标。"

---

**关联文档**：[[TODO文件]]#48 / #3（场次维度购买标记）/ #31（向量检索）、[[TODO已完成]]#29（cron 备份）/ #47（恢复演练）、[[AI导购Agent升级方案]]（#32，Agent 可消费本方案造的丰富商品数据）、[[服务器内存优化方案]]（R7，已归档）、[[阿里云ECS服务器情况]]、[[TODO第三批实现与原理]]（两台服务器与内网拓扑）
