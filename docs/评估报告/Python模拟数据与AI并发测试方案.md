# Python 模拟数据 + AI 并发测试方案

> **创建日期**：2026-08-26
> **目的**：① 用 Python 脚本模拟真实业务数据积累（浏览/加购/下单/秒杀），
> 让系统看起来像运营中的电商；② 在不调用真实 DeepSeek API 的前提下，
> 测试 AI 导购模块能扛住多少并发
> **定位**：与 JMeter 压测互补——JMeter 测性能尖峰（100 并发秒杀 + Sentinel 限流），
> 本方案造业务数据沉淀 + 测 AI 服务端并发承载
> **说明**：演示视频已录制并上传 B 站，本方案是"数据准备 + AI 承载验证"的补充

---

## 一、方案总览

```
┌─ 模拟数据生成（业务数据积累）─┐
│  Python 脚本 → 登录/浏览/加购/下单/支付/秒杀   │  → 数据库沉淀
└──────────────────────────────┘
┌─ AI 并发测试（不调真实 API）──┐
│  Python 并发 → /ai/chat/stream（SSE）         │  → 测服务端承载
│  用本地 mock LLM 替代 DeepSeek 真实调用        │     （非模型速度）
└──────────────────────────────┘
```

---

## 二、模拟数据生成脚本设计

### 2.1 核心原则：按真实用户行为分布（不是纯随机）

| 行为 | 占比 | 说明 |
|------|------|------|
| 浏览/搜索商品 | 80% | 只 GET 商品列表/详情/搜索，不产生订单 |
| 加入购物车 | 15% | POST 购物车，但不结算 |
| 下单+支付 | 5% | 完整流程：下单 → 支付（模拟支付模式） |
| 秒杀 | 按需 | 对齐时间窗口，限购逻辑 |

> 每天 1000 条"行为"里，只有约 50 单——**这才像真实电商**（订单转化率约 5%）。
> 面试话术："我按真实用户漏斗（浏览→加购→下单 80/15/5）模拟了 N 天数据"

### 2.2 脚本骨架（Python + requests）

```python
"""
simulate_data.py — CoolShark 业务数据模拟器
用法: python simulate_data.py --days 30 --per-day 1000 --clean
"""
import argparse, random, time, requests

BASE = "http://8.156.77.197"   # 生产服务器（经 Gateway）
USER_PREFIX = "test_sim_"      # 模拟用户前缀，可一键清理

def login(username, password="123456"):
    r = requests.post(f"{BASE}/user/sso/login",
                      json={"username": username, "password": password})
    return r.json()["data"]["token"]

def browse(token, spu_ids):
    # 80% 行为：随机浏览商品列表/详情
    ...

def add_cart(token, sku_id, qty):
    ...

def create_order(token, sku_id, qty, contact):
    # 5% 行为：下单 + 模拟支付
    ...

def seckill(token, sku_id, rand_code):
    # 秒杀：需先 GET 秒杀详情拿 randCode，再 POST 提交
    ...

def main(days, per_day, clean):
    if clean:
        cleanup()          # 删 test_sim_% 用户 + 关联数据 + Redis 标记
        return
    users = [f"{USER_PREFIX}{i}" for i in range(20)]  # 预置 20 个模拟用户
    for day in range(days):
        for _ in range(per_day):
            action = random.choices(
                ["browse", "cart", "order", "seckill"],
                weights=[80, 15, 4.5, 0.5])[0]
            ...  # 按分布执行
        print(f"day {day+1}/{days} done")
```

### 2.3 关键设计点（必须遵守）

| 点 | 说明 |
|----|------|
| **可清理** | 用户前缀 `test_sim_`，提供 `--clean` 一键删（用户+订单+秒杀记录+Redis 标记），演示前恢复干净 |
| **秒杀对齐窗口** | 当前 6 场全年有效（2026-01-01~12-31），脚本随机时间即可；跨年后需先更新窗口 |
| **秒杀限购** | 同一用户同一 SKU 支付后**永久不能再买**（reseckill 永久标记 + success 唯一索引）→ 脚本要换 SKU 或换用户 |
| **支付用模拟模式** | 项目已支持模拟支付（`simulated: true`），脚本走模拟支付即可，不碰支付宝沙箱 |
| **节奏控制** | 默认慢节奏（sleep 随机 0.5~3s），模拟真实用户；需要并发时再开多线程 |
| **幂等** | 下单请求 contactName 用随机值，避免幂等 Key 冲突 |

### 2.4 清理清单（cleanup 函数要做的）

```sql
-- MySQL
DELETE FROM cs_mall_ums.ums_user WHERE username LIKE 'test_sim_%';
DELETE FROM cs_mall_oms.oms_order WHERE user_id IN (SELECT id FROM cs_mall_ums.ums_user WHERE username LIKE 'test_sim_%');
-- 秒杀 success / 购物车 / 订单项同理
-- Redis
redis-cli --scan --pattern 'mall:seckill:reseckill:*' | xargs redis-cli del   -- 按需
```

> ⚠️ 演示前**必须清理**：压测卖点（100 并发 0 超卖）依赖干净数据，几万条模拟订单会让列表页变慢、秒杀"已购买"误伤。

---

## 三、AI 并发测试方案（不调真实 API）

### 3.1 为什么不能直接压真实 API

- **有真实成本**：`TokenBudgetService` 全局 2 元/天预算，压 1000 并发瞬间打满 → 真实使用没额度
- **速度瓶颈在 DeepSeek**：SSE 流式接口的响应速度取决于 DeepSeek 处理时间，**不是你的服务能力**——压出来的"慢"是模型慢，不是后端慢（用户已确认这一点）

### 3.2 正确做法：本地 mock LLM，测服务端承载

**方案 A：改配置指向本地 mock（推荐，最干净）**

```yaml
# mall-ai application-prod.yml 临时改（测完改回）
cooxiao:
  ai:
    base-url: http://127.0.0.1:9999   # 本地 mock server
    api-key: sk-mock
```

本地起一个简易 mock（Python）：

```python
# mock_llm.py — 模拟 DeepSeek 流式响应，返回固定内容
from http.server import BaseHTTPRequestHandler, HTTPServer
import json, time

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        time.sleep(0.3)                      # 模拟 300ms 响应延迟
        body = json.dumps({"choices": [{
            "message": {"content": "这是一条模拟的 AI 导购回复，用于并发测试。"},
            "finish_reason": "stop"}]}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

HTTPServer(("127.0.0.1", 9999), Handler).serve_forever()
```

**然后**：Python 并发脚本开 N 线程打 `/ai/chat/stream`（SSE），测：
- 服务端能维持多少并发 SSE 连接（Tomcat 线程池上限）
- 内存/CPU 峰值
- Redis 会话写入是否成为瓶颈

**方案 B：直接写会话记录（只测 Redis/DB 承载）**
不走 `/ai/chat` 接口，脚本直接生成会话数据写 Redis（`ai:chat:session:*`），测的是存储层。

### 3.3 要测的指标（面试可讲）

| 指标 | 意义 |
|------|------|
| 并发 SSE 连接数上限 | Tomcat 默认 200 线程，SSE 长连接占线程 → 线程池是主要瓶颈 |
| 单请求内存增量 | SSE 流式输出 + 会话 JSON 在内存中累积 |
| Redis 写入 QPS | `SessionManager.save` 每次对话写 Redis（24h TTL） |
| 内存峰值 | 见第四节评估 |

---

## 四、服务器内存评估（2026-08-26 实测）

### 4.1 当前状态（实测数据）

| 指标 | 值 | 状态 |
|------|-----|------|
| 总内存 | 14 GiB | - |
| 已用 | 12 GiB | ⚠️ 偏高 |
| **可用（available）** | **2.1 GiB** | 🔴 紧张 |
| Swap | **0 B（无）** | 🔴 无缓冲 |
| 容器 mem_limit | **全部 0（无限制）** | 🔴 任何进程失控可吃满宿主 |

### 4.2 内存大头（docker stats 实测）

| 容器 | 内存 |
|------|------|
| Nacos | ~980 MiB |
| product | ~945 MiB |
| seckill | ~798 MiB |
| order | ~786 MiB |
| SkyWalking OAP | ~1.1 GiB |
| ES | ~1.1 GiB |
| 其余 ~15 容器 | 合计 ~5 GiB |

### 4.3 结论：**当前内存扛不住大规模并发测试** ⚠️

| 场景 | 可行性 | 原因 |
|------|--------|------|
| 模拟数据生成（慢节奏，1 请求/秒级） | ✅ 可行 | 负载低，内存压力小 |
| AI 并发 50 以下 | ⚠️ 勉强 | mall-ai 现用 ~571 MiB，SSE 连接每个占线程+缓冲 |
| AI 并发 100+ | 🔴 有 OOM 风险 | 可用仅 2.1G + 无 Swap + 容器无 mem_limit → OOM Killer 可能杀服务（历史杀过 ES） |

### 4.4 建议（与 `docs/评估报告/TODO文件.md` R7 内存优化对齐）

1. **先做内存优化再压测**（R7 方案已定稿）：Nacos 堆 1g→512m（省 ~480M）+ 全容器加 mem_limit + Swap 2G → 可用内存从 2.1G → ~2.7G
2. **压测前确认**：`free -h` 可用内存 ≥ 3G；SkyWalking 可临时停（省 ~1.5G，测完恢复）
3. **从低并发起步**：AI 并发先 20 → 50 → 100 逐步加，边压边看 `docker stats csmall-ai` 和 `free -h`
4. **压测脚本放本地跑**：Python 脚本在本机执行，不占服务器内存（服务器只承受请求负载）

---

## 五、执行顺序建议

```
1. 先写脚本 + mock LLM（本地验证通）
2. 服务器内存优化（R7 方案，找 ecs-user 执行）→ free -h 确认 ≥3G
3. 慢节奏跑模拟数据（1~2 天，每天 1000 行为）→ 检查数据沉淀
4. AI 并发测试：20 → 50 → 100 逐步加压，盯 docker stats + free -h
5. 演示前：--clean 清理模拟数据，恢复干净环境
6. 面试演示：JMeter 压测截图 + 模拟数据统计 + AI 并发测试结果
```

---

**关联文档**：`docs/评估报告/TODO文件.md`（R7 内存优化 / 秒杀管理 TODO）、
`docs/归档/秒杀压测演示指南.md`（JMeter 压测，已归档）、`docs/阿里云ECS服务器情况.md`
