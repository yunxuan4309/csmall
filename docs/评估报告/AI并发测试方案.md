# AI 并发测试方案（第二层 · TODO #67 之 ③）

> **创建**：2026-09-11（自《Python 模拟数据 + AI 并发测试方案》**切割**而来）
> **状态**：🟢 **首轮实测完成（2026-09-12）** —— mock / D1 透传 / Nacos 5→200 / 800 用户池 / **#62 已修** 全部就绪；**两条链路 × 20/50/100 已压完**（结果见 **§十**）：成功 RPS pipeline **21.4** / agent **~20.4**，20 并发档最健康（p50<1s、TTFT 100~170ms、0 硬失败）；**逐层量出两层墙**（Spring 异步池默认 8 → AI 闸门 20）。⏳ 待办：恢复现场 + 结果回填 + 其余 7 模块的同类潜伏风险
> **归属**：[[TODO文件]] **#67**（= #48 剩余部分）之 **③ 第二层 AI 并发压测**
> **定位**：**只测「服务端承载」**，不调真实付费 LLM —— 内网 mock + 临时放开限流，量出"第几个并发开始降级"
> **姊妹篇**：[[Python模拟数据与数据隔离方案]]（第一层：造数 + 数据隔离 + 可观测展示）
> **引用关系**：本方案 §二 / §四 / §六 引用姊妹篇的 **§2.2.8**（造数避峰）/ **§四**（服务器资源与内存基线）/ **§2.2.6**（Redis 键清单）
> 🧩 **已归纳**：✅ **压测侧已提炼（2026-09-12）** → [[问题解决--压测结论的可信性（先证伪干扰项与工具自身）]]（从 §十 实测的 8 个"会让结论作假"的问题聚合而成）；造数侧已提炼为 [[问题解决--生产造数的数据隔离与复核方法]]
> **⚠️ 未完成约束**：本文档是 #67 该子项的**唯一方案正文**，**做完前不得归档**（[[TODO第三批实现与原理-1]] §三·3.1 只留指针，正文在此）
> 🆕 **2026-09-12 登记**：**第一层造数脚本的改造计划**（否决持续运行 · 提量/提 QPS · 秒杀动作 + 秒杀后恢复）见 **§九** —— 正文归属 A 册，此处先登记防丢

---

## 〇、初稿在 mock / 压测侧的设计问题（原方案 §〇 的 ⑤~⑧ 行）

> 来源：2026-09-10 定稿时对**初稿（2026-08-26）**的逐条修正。造数侧的问题（①②③④⑨）见 [[Python模拟数据与数据隔离方案]] §〇。

| # | 初稿问题 | 证据（2026-09-10 实测） | 本次修正 |
|---|---|---|---|
| ⑤ | **mock LLM 返回格式不足以压 SSE**：初稿 mock 只返回单个 JSON | SSE 解析实测在 **`DeepSeekAiClient.openSseStream:349`**：`line.startsWith("data: ")` 取分片、`data: [DONE]` 结束、带 `usage` 的分片就地记账（`doStream` 与 `streamChatWithTools` 共用）→ 普通 JSON 响应**收不到任何 chunk**〔原稿引用的 `ChatServiceImpl.doStreamDeepSeek` **已不存在**（全仓 grep 0 命中），2026-09-11 复核修正，见 §六·B · D8a〕 | mock 必须**实现 SSE 流式格式**（§二） |
| ⑥ | **mock 部署位置错误**：初稿让容器访问 `127.0.0.1:9999` | `csmall-ai` 在容器内，`127.0.0.1` = **容器自身**，不是开发机 | mock 部署在**新机**（内网可达），用 **compose override 注入环境变量**，不改 prod yml（§二） |
| ⑦ | **压测脚本"放本机跑"** 与 2026-09-09 新要求**矛盾** | 老机 **5 Mbps 固定带宽** | 脚本**必须跑在新机/内网**（§四 / §五） |
| ⑧ | **未覆盖** 2026-09-09 补充需求：~100 req/s + 每日 12 点高峰 + cron | TODO #67（原 #48） | 新增 §四 |

---

## 一、为什么不能直接压真实 API

- **有真实成本**：`TokenBudgetService` 全局 **2 元/天**预算（**生效值 = `application.yml:86` `daily-budget: 2.0`**；代码兜底默认 `AiProperties:104 = 10.0` 被 yml 覆盖 → 2026-09-11 复核澄清，见 §六·B · D8b），压 1000 并发瞬间打满 → 真实使用没额度。**今日实耗佐证**：`ai:daily_cost:2026-09-11` = **0.011296 元**（日常量级）
- **速度瓶颈在 DeepSeek**：SSE 流式接口的响应速度取决于 DeepSeek 处理时间，**不是你的服务能力**——压出来的"慢"是模型慢，不是后端慢

## 二、正确做法：内网 mock LLM，测服务端承载

| 要素 | 正确做法 | 为什么 |
|---|---|---|
| **mock 放哪** | 部署在**新机** `172.29.193.240:9999`（内网，老机可达 —— 2026-09-11 实测新机→老机网关 `10087` **26ms**） | 容器内 `127.0.0.1` = **容器自身**；放开发机则要么走公网（5Mbps）要么访问不到 |
| **怎么接** | **compose override 文件**注入：**`AI_API_BASE_URL=http://172.29.193.240:9999`** + **`AI_API_KEY=sk-mock`**；测完 `docker compose up -d mall-ai` 恢复 | **不改 prod yml、不入库**；一次 `up -d mall-ai` 即可回滚。<br>🔴 **2026-09-11 复核（D1）：初稿的 `COOXIAO_AI_BASEURL` / `COOXIAO_AI_APIKEY` 根本不生效** —— yml 实际读 `${AI_API_BASE_URL}` / `${AI_API_KEY}`（`application.yml:22-23`）；**且 compose 里 `mall-ai` 未透传 `AI_API_BASE_URL`**（`docker-compose.yml:545-571` 实测只有 `AI_API_KEY` / `AI_AGENT_*`）→ **必须先给 compose 加一行** `AI_API_BASE_URL: ${AI_API_BASE_URL:-https://api.deepseek.com}`，否则 override 的值进不了容器。**压测前必查**容器内 base-url 已变（否则这一轮打的是真实付费 API） |
| **mock 必须实现什么** | ① **非流式**：`choices[0].message.content`（供 `doChat`/`chatWithModel`）<br>② 🔴 **流式 SSE**：`data: {...}\n\n` 分片 + 末尾 `data: [DONE]`（供 `/ai/chat/stream`）<br>③ 🔴 **Agent 工具轮（2026-09-11 新增，D2）**：`delta.tool_calls` 分片（首片带 `id`/`name`，后续片只带 `arguments`，**按 `index` 拼接**）<br>④ 带 `usage` 的末尾 chunk（`stream_options.include_usage=true`） | SSE 解析实测在 **`DeepSeekAiClient.openSseStream:349`**（`line.startsWith("data: ")`）—— **返回普通 JSON 会收不到任何 chunk**；生产 `AI_AGENT_ENABLED=true` → 不带 `tool_calls` **测的就不是生产链路**（§六·A · D2） |
| **压什么接口** | `/ai/chat/stream`（SSE 长连接，最主要的承载瓶颈） | 见 §三 |

```python
# mock_llm.py（骨架 v2 · 2026-09-11 复核后修订）
#   🔴 D5：必须 ThreadingHTTPServer —— 单线程 HTTPServer 会把并发 SSE 串行化，压到的是 mock 不是服务端
#   🔴 D2：必须实现 tool_calls —— 生产 AI_AGENT_ENABLED=true，Agent 首轮带 tools（商品意图还带 tool_choice=required）
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json, time

CONTENT = "这是一条模拟的 AI 导购回复，用于并发测试。"

def sse(w, obj):
    w.write(f"data: {json.dumps(obj, ensure_ascii=False)}\n\n".encode())
    w.flush()

def tool_call_round(w, name, args):
    """工具轮：按实测契约分片（首片带 id/name，后续片只带 arguments）"""
    sse(w, {"choices": [{"delta": {"tool_calls": [
        {"index": 0, "id": "call_sim_1", "type": "function",
         "function": {"name": name, "arguments": ""}}]}}]})
    for p in [args[i:i+6] for i in range(0, len(args), 6)]:      # 拆片，模拟真实流（补册实验 K：实测 19 片）
        sse(w, {"choices": [{"delta": {"tool_calls": [
            {"index": 0, "function": {"arguments": p}}]}}]})
        time.sleep(0.005)
    sse(w, {"choices": [{"delta": {}, "finish_reason": "tool_calls"}]})
    sse(w, {"choices": [], "usage": {"prompt_tokens": 50, "completion_tokens": 30}})
    w.write(b"data: [DONE]\n\n"); w.flush()

def content_round(w):
    """收敛轮：逐字回 content"""
    for piece in [CONTENT[i:i+6] for i in range(0, len(CONTENT), 6)]:
        sse(w, {"choices": [{"delta": {"content": piece}}]})
        time.sleep(0.05)                                        # 模拟逐字输出
    sse(w, {"choices": [], "usage": {"prompt_tokens": 50, "completion_tokens": 30}})
    w.write(b"data: [DONE]\n\n"); w.flush()

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"        # 长连接：SSE 必须，否则连接复用行为不真实
    def log_message(self, *a):
        pass                             # 压测时不要刷日志（刷日志本身会拖慢 mock）

    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        req = json.loads(self.rfile.read(n) or b"{}")
        tools = req.get("tools") or []
        # 已经调过工具 → 本轮必须收敛（复刻生产"第 1 轮调工具 → 第 2 轮收敛"）
        used = any(m.get("role") == "assistant" and m.get("tool_calls") for m in req.get("messages", []))
        if req.get("stream"):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Connection", "keep-alive")
            self.end_headers()
            if tools and not used:
                tool_call_round(self.wfile, tools[0]["function"]["name"], '{"keyword": "手机"}')
            else:
                content_round(self.wfile)
            return
        time.sleep(0.3)
        body = json.dumps({"choices": [{"message": {"content": CONTENT}, "finish_reason": "stop"}],
                           "usage": {"prompt_tokens": 50, "completion_tokens": 30}}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers(); self.wfile.write(body)

# 🔴 线程化 + 端口：mock 并发能力必须 ≥ 闸门 20（concurrent-max: 20），最好 ≥ 阶梯峰值
ThreadingHTTPServer(("0.0.0.0", 9999), Handler).serve_forever()
```

> 🔴 **mock 的三条硬性要求（2026-09-11 新增，见 §六·A · D5）**
> 1. **必须线程化**（`ThreadingHTTPServer` / `socketserver.ThreadingMixIn`）—— 单线程版本会把 20 条并发 SSE **串行化**，压出来的是 mock 的吞吐；
> 2. **并发能力 ≥ 20（闸门值），最好 ≥ 阶梯峰值 100**；
> 3. **先单独压 mock**（固定并发打 9999）拿到 P50/P99 与吞吐，**证明 mock 不是瓶颈之后**再压 mall-ai —— 否则压测数字不可信。
>
> ⚠️ 本骨架是**设计稿**：`tool_calls` 分片契约以 [[问题解决--LLM链路的契约漂移与分层降级]] §七 **实验 K（实测 19 片）** 为准，实施时先用真实请求对齐一次再压。

**方案 B（备选）**：不走接口，脚本直接写 `ai:chat:session:*` 到 Redis——只测存储层，**价值低**，不推荐作为主路径。

## 三、要测的指标（面试可讲）

| 指标 | 意义 |
|------|------|
| 并发 SSE 连接数上限 | Tomcat 默认 200 线程，SSE 长连接占线程 → 线程池是主要瓶颈 |
| **并发闸门命中率** | `AiConcurrencyGuard` 上限 **20**（实测 `application.yml` `concurrent-max: 20` + `AiProperties:130`），超出走降级（`AiBusyException`）→ 这是**真实保护点**，要量化"多少并发开始降级" |
| 🔴 **每用户频控（干扰项，必须先排除）** | `user-rate-limit: 10` / 60s（`AiProperties:136-139` 实测开启）→ **同一 token 第 11 个请求起全部 429**，测到的是频控不是闸门。压测**必须用 N ≥ 阶梯峰值个不同模拟用户**（§六·A · D3） |
| 🔴 **Sentinel 入口限流（干扰项）** | `ai-chat=5 QPS`（`deploy/docker/sentinel/mall-ai-flow-rules.json`）→ 处理剧本见 **§五** |
| 单请求内存增量 | SSE 流式输出 + 会话 JSON 在内存中累积 |
| Redis 写入 QPS | `SessionManager.save` 每次对话写 Redis（24h TTL） |
| **预算记账正确性（新增，正面结论）** | `TokenBudgetService:60` 用 Redis `incr`（**原子**）→ 高并发下不会少记/多记，可作"AI 治理在高并发下仍正确"的实测结论 |
| 内存峰值 | 见 [[Python模拟数据与数据隔离方案]] §四 |

> ⚠️ **指标顺序原则（2026-09-11 补）**：**先证伪干扰项（Sentinel 5 QPS / 每用户频控 10）→ 再测真实承载**。否则量到的"降级点"是限流器的，不是并发闸门的。

## 四、真实并发模拟（2026-09-09 用户补充需求）

**目标**：真实并发 ~100 req/s + **每日 12 点高峰窗口**（cron 定时触发）。

**三条硬约束（必须遵守）**：

| 约束 | 原因 | 做法 |
|---|---|---|
| 🔴 **脚本跑在新机/内网** | 老机 **5 Mbps 固定带宽**，本机压自己 = 自压自伤，数据无意义 | 脚本部署 `172.29.193.240`，打老机**内网 IP** `172.29.193.239`（不限速不计费）；**禁止走公网 IP**。〔2026-09-11 实测该通路可用：3306/6379/10087 全通、网关 26ms〕 |
| 🔴 **负载画像要避开限流** | Sentinel 已拦：秒杀 QPS=**10**、`ai-chat`=**5** / `ai-reason`=**10** / `ai-light`=**30**（规则实测见 `deploy/docker/sentinel/mall-ai-flow-rules.json`）→ 压这些接口只会得到 429，测不到服务能力 | **承载指标**：主压**浏览 / 加购 / 普通下单**（无限流规则）；**限流指标**：秒杀/AI 单独验证（期望 429），不作为承载数字 |
| 🔴 **阶梯加压 + 盯资源** | 老机 4C16G、available **3.4G**（2026-09-11 实测） | **20 → 50 → 100** 阶梯；每档盯 `docker stats` + `free -h`；**命中中止阈值立即停**（阈值见 §五） |

**与"造数"的关系**：造数是**慢节奏、长时间**沉淀数据；高峰压测是**短时高压**验证承载。**两者用同一套脚本的不同模式**，但**不要同时跑**。
> ⚠️ **一处内部矛盾已澄清（2026-09-11）**：[[Python模拟数据与数据隔离方案]] §2.2.8 要求造数"避开 12 点高峰"，而本节要求 cron 在 12 点触发高峰 —— 二者**不是同一件事**：**造数是长时间低频**（必须避峰），**高峰压测是受控短时演练**（刻意选高峰，且只压无业务意义的浏览接口）。规则是：**造数避峰、压测选峰、两者不同时**。

## 五、生产压测执行剧本（2026-09-11 决策：生产 + 临时放开限流 + 双链路各压一遍）

> **为什么选"生产"而不是"本地起 mall-ai"**：竞品价值恰恰在**真实的 4C16G / 容器编排 / 真实中间件 / 内网拓扑**；本地压本地拿不到这些结论。
> **为什么放开限流是安全的**：**mock 生效后 LLM 流量根本不进真实 API**（base-url 已指向新机 mock）→ 放开 `ai-chat` 阈值**几乎没有成本风险**，这是本决策成立的关键前提。**D1 没做对，这条就不成立。**

**前置（缺一不可，全部有验证方式）**

| # | 动作 | 验证方式 |
|---|---|---|
| 1 | compose 给 `mall-ai` 加 `AI_API_BASE_URL` 透传 + override 指向 mock（`172.29.193.240:9999`） | `docker inspect csmall-ai` 看到 `AI_API_BASE_URL=http://172.29.193.240:9999`；**且 mock 侧计数真的涨了** |
| 2 | **备份 Nacos 规则原文**（`mall-ai-flow-rules`）到文件 + 记 md5 | `curl ".../configs?dataId=mall-ai-flow-rules&group=SENTINEL_GROUP"` 输出存盘 + `md5sum` | ⚠️ **写后立刻 GET 可能读到旧快照**（2026-09-12 实测：紧接热改的 GET 仍显示旧值，一度被误判为"写入未生效"）⇒ **等 1~2 秒再复核**，并与落盘的 md5 比对确认 |
| 3 | 构造 **N 个模拟用户 token**（N ≥ 阶梯峰值，建议 120） | 脚本预登录 → token 列表落文件 + 抽查 3 个能 200 |
| 4 | mock 已按 §二 v2（`ThreadingHTTPServer` + 工具轮） | **单独压 mock** 拿 P50/P99 与吞吐，证明 mock 不是瓶颈 |
| 5 | 记录基线 `free -h` / `docker stats --no-stream` | 落文件（对比用） |

**执行（每档停 3~5 分钟，不要一跳到底）**

```
① Nacos 热改 ai-chat 阈值 5 → 200（只改这一条）→ 立即 GET 复核已生效（热更新，零重建）
② 阶梯 20 → 50 → 100：
   a. 先 AI_AGENT_ENABLED=false（固定流水线，单轮 LLM）
   b. 再 AI_AGENT_ENABLED=true （Agent，工具轮 + 收敛轮 = 双轮）
   每档盯：docker stats / free -h / mock 侧并发计数 / mall-ai 日志的 429 与异常
   中止阈值（任一命中立即停）：available < 1.5G ｜ 5xx+429 占比 > 1% ｜ 单档 p99 > 10s
③ 恢复：Nacos 改回 count=5 → GET 复核 → 抽查一次真实请求确认 429 行为回归
④ 恢复 mall-ai 环境变量（移除 override）→ recreate → 验证 base-url 已回到 api.deepseek.com
```

**回滚点（两个，互相独立）**：① Nacos 规则原文（第 2 步落盘）；② `AI_API_BASE_URL` / `AI_AGENT_ENABLED` 改回 + `docker compose up -d mall-ai`（~45s）。
**顺序的意义**：先压**流水线（单轮）**再压 **Agent（双轮）**，两组的吞吐比值就是"**Agent 这条双轮链路额外付出的承载代价**"——这是 #32 上线后才可能拿到的素材。
**成果回填**：每档结果写进 `sim_batch.note` 或独立的"压测结果表"（指标 → 数字 → 结论），否则一个月后只剩"压过"两个字。

### 5.1 执行步骤（#67 之 ③ 的第 11~15 步 · 自原 §五「执行顺序」迁入）

```
【⑤ AI 并发压测（~1 天 · 投入最大，放最后）】
11. mock LLM 落文件并测通（ThreadingHTTPServer + tool_calls 工具轮）
12. compose 加 AI_API_BASE_URL 透传（§六·A · D1）+ override 指向 mock
13. 备份 Nacos 规则（落盘 + md5）→ 热改 ai-chat 阈值 → 构造 N ≥ 120 个模拟用户 token
14. 阶梯 20 → 50 → 100：先 AI_AGENT_ENABLED=false（流水线单轮），再 true（Agent 双轮）
    每档盯 docker stats / free -h；命中中止阈值（available<1.5G / 5xx+429>1% / p99>10s）立即停
15. 测完：Nacos 改回 + GET 复核 → 移除 override → recreate → 验证 base-url 已回真实地址
 ★ 产出：Agent（双轮）vs 固定流水线（单轮）的承载对比
```

> ℹ️ 原 §五「执行顺序」的**造数 / 展示侧（①~④ + 收尾）** 见 [[Python模拟数据与数据隔离方案]] §五；该册对应位置已改为指针（"⑤ AI 并发压测 → 已迁出"）。

---

## 六、实施前复核（AI 侧：D1~D5 · D8a · D8b · 两项决策）

> **来源**：2026-09-11 实施前逐条实测 + 读码（9 条复核 D1~D9 中的**压测侧**部分）。
> **为什么单列**：这 9 条里 **D1~D5 直接影响压测结论正确性**（不修就会得到"不可信数字"，最坏压到**真实付费 API**）。
> 造数侧条目（D6 / D7 / D8c / D9）见 [[Python模拟数据与数据隔离方案]] §〇.1。

### A. 🔴 必须修（直接影响压测结论正确性）

| # | 缺陷 | 证据（2026-09-11 实测 / 读码） | 修法 |
|---|---|---|---|
| **D1** | **mock 注入的环境变量名错误 → override 静默失效，压测打真实付费 API** | 原稿（现 **§二**）写 `COOXIAO_AI_BASEURL` / `COOXIAO_AI_APIKEY`；实际 yml 用 **`${AI_API_KEY}` / `${AI_API_BASE_URL:https://api.deepseek.com}`**（`mall-ai/mall-ai-webapi/src/main/resources/application.yml:22-23`）；生产容器 env **实测只有 `AI_API_KEY`，没有 `AI_API_BASE_URL`**（当前 base-url 来自 jar 内 yml 默认值）；`deploy/docker/docker-compose.yml:545-571` 的 `mall-ai` **未透传该变量** | ① compose `mall-ai.environment` **新增一行** `AI_API_BASE_URL: ${AI_API_BASE_URL:-https://api.deepseek.com}`（否则 `.env` 里有值也进不了容器）；② override 注入 `AI_API_BASE_URL=http://172.29.193.240:9999` + `AI_API_KEY=sk-mock`；③ **压测前必查**：容器内 base-url 确实已变（`docker inspect` 或启动日志），否则这一轮全部作废 |
| **D2** | **Agent 已上生产，mock 未实现 `tool_calls` → 测的不是生产链路** | 生产容器 env 实测 `AI_AGENT_ENABLED=true` → `/ai/chat/stream` 走 `streamChatWithTools`，请求体带 `tools`，商品意图首轮还带 `tool_choice=required`；本文档定稿（09-10）在 Agent 上线（09-11）**之前**，§〇⑤ 只要求 mock 回 `delta.content` | **已决策：两条链路各压一遍**（§六·C / §五）——mock 必须实现**工具轮**（按 `tools[0].function.name` 回 `tool_calls` 分片，参数按 `index` 分片拼接；第二轮回 `content`），再分别压 `AI_AGENT_ENABLED=false/true` |
| **D3** | **每用户频控 10 次/60s → 同一 token 第 11 个请求起全 429** | `AiProperties:136-139` `user-rate-limit-enabled: true` / `user-rate-limit: 10`；`AiUserRateLimiter` 60 秒窗口（同 yml `application.yml:96-97`） | 并发压测**必须用 N ≥ 阶梯峰值个不同模拟用户**（建议 120 个 token）；或该轮临时放开 `user-rate-limit` 并在文档写明"特意放开以测闸门"。§三 指标表已补"干扰项"列 |
| **D4** | **Sentinel `ai-chat=5 QPS` 与"测 AI 承载"直接冲突** | `deploy/docker/sentinel/mall-ai-flow-rules.json:2-10` `count=5`（Nacos `mall-ai-flow-rules` 同源，热更新） | **已决策：生产 + 临时放开限流**——Nacos 热改阈值 → 压测 → 改回，**零重建**；执行剧本见 **§五**（含备份/中止阈值/回滚点）。放开限流**几乎无成本风险**，因为 mock 生效后 LLM 流量根本不进真实 API |
| **D5** | **mock 用单线程 `HTTPServer` → mock 自己就是瓶颈** | 原稿（现 **§二**）代码 `HTTPServer(("0.0.0.0", 9999), Handler).serve_forever()` 逐个处理请求，且每片 `time.sleep(0.05)` → 20 条并发 SSE 被**串行化**，压出来的是 mock 的吞吐 | 改 `ThreadingHTTPServer`；**mock 并发能力必须 ≥ 闸门 20**（`concurrent-max: 20`），最好 ≥ 阶梯峰值；并**先单独压 mock 拿 P50/P99**，证明它不是瓶颈 |

### B. 🟡 事实与引用漂移（压测侧；不影响方案成立，但会误导实施者）

| # | 漂移 | 证据 | 修法 |
|---|---|---|---|
| **D8a** | §〇⑤ 引用 `ChatServiceImpl.doStreamDeepSeek` | **全仓 grep 0 命中**（该方法已不存在） | 改为 `DeepSeekAiClient.openSseStream`（L325-368）：`line.startsWith("data: ")` 取分片、`data: [DONE]` 结束、带 `usage` 的分片就地记账；`doStream` 与 `streamChatWithTools` 共用。**结论（mock 必须回 SSE 分片）不变** |
| **D8b** | §一 "全局 **2 元/天**预算" —— ⚠️ **复核澄清：原稿是对的，我第一版判错了** | **生效值 = `mall-ai/mall-ai-webapi/src/main/resources/application.yml:86` `daily-budget: 2.0`**（yml 才是生效源）。`AiProperties:104` 的 `dailyBudget = 10.0` 只是**代码兜底默认值**、被 yml 覆盖 → 二者不一致属"有默认值但被配置覆盖"的正常形态，**不是文档错误**。<br>💡 **教训（写给以后的自己）**：判"某个配置的生效值"**必须同时查 yml 与代码默认值** —— 只看 `AiProperties` 会得出"10 元"的错误结论（本次我第一版就判错了，靠 grep `daily-budget` 才发现）。<br>今日实耗 `ai:daily_cost:2026-09-11` = **0.011296 元** | §一 **保持"2 元/天"不变**，仅补 yml 行号与实耗佐证 |

### C. 两项决策（2026-09-11 用户拍板）

| 决策 | 内容 | 影响 / 落点 |
|---|---|---|
| **① 压测环境** | **生产 + 临时放开限流**（Nacos 热改 → 压 → 改回，零重建） | 保留"内网真压生产"的卖点（带宽/内存/容器编排都是真的）；执行剧本 **§五** |
| **② Agent 链路** | **两条链路各压一遍**：`AI_AGENT_ENABLED=false`（固定流水线，单轮 LLM）vs `true`（Agent，工具轮 + 收敛轮双轮） | 拿到"**Agent 双轮链路多付出的承载代价**"这组数字——**#32 上线后才可能获得的新素材**；前置是 mock 先支持工具轮（D2） |

---

## 七、纪律与安全清单（压测侧，实施时逐条打勾）
- [ ] **压测走内网**：脚本在新机；禁止公网 IP；禁止老机自压
- [ ] 🆕 **mock 先自测**：单独压 mock 拿 P50/P99 与吞吐，证明 mock 不是瓶颈后再压服务端
- [ ] 🆕 **mock 地址必验证**：压测前确认容器内 `AI_API_BASE_URL` 已是 mock（**否则打的是真实付费 API**）
- [ ] 🆕 **mock 必须线程化**：`ThreadingHTTPServer`，并发能力 ≥ 闸门 20（最好 ≥ 阶梯峰值）
- [ ] 🆕 **并发用多用户**：≥ 峰值并发个不同模拟用户，绕开每用户频控 10/60s
- [ ] 🆕 **Nacos 规则先备份**：`mall-ai-flow-rules` 原文落盘 + 记 md5；改完立即 GET 复核；测完改回
- [ ] 🆕 **中止阈值**：available < 1.5G ｜ 5xx+429 占比 > 1% ｜ 单档 p99 > 10s → 立即停
- [ ] **阶梯加压**：20 → 50 → 100；每档看 `docker stats` + `free -h`
- [ ] 🆕 **结果回填**：每档指标 → 数字 → 结论写进 `sim_batch.note` 或结果表（不然只剩"压过"两个字）
- [ ] **测完恢复**：移除 override、重建 mall-ai、恢复 SkyWalking（若停）
---

## 八、面试话术（AI 并发 / 压测）
> "压测我也是把脚本放在**内网另一台机器**上打的——生产带宽只有 5Mbps，本机压自己是自压自伤，数据没意义。另外我**没有把"限流触发"当成承载指标**：承载测试要回答的是"系统自己什么时候扛不住"，而入口那道 Sentinel 阈值会先把请求挡掉、把结论污染成"限流很有效"——所以我把阈值**临时放开**（Nacos 热改、测完改回、零重建）先排除这道干扰；限流本身我另外单独做了一组"故意撞闸门"的验证，用来展示保护确实生效。**这两件事结论不同，不能混着说。**"

> "AI 并发这条路我踩过坑才想明白：**同一台服务上其实叠了三道保护，不先把干扰项证伪，压出来的数字是假的**——入口 Sentinel `ai-chat` 只有 5 QPS，还有每用户 60 秒 10 次的频控，再往里才是并发闸门 20。所以我先临时把入口阈值放开（**Nacos 热改，测完改回，零重建**），再用**一批不同的模拟用户**绕开频控，才真正量到"第几个并发开始降级"。压的时候也没打真实模型——内网放了个 mock LLM，用 compose override 临时把 base-url 指过去，**不改生产配置、一次 recreate 就能回滚**；而正因为走了 mock，放开限流几乎没有成本风险。最后还有个意外收获：Agent 那条链路每题要调两次模型（先调工具、再收敛），我对着固定流水线又各压了一遍，量出了**这条双轮链路多付出的那部分承载代价**。"
---

## 九、⏳ 待实施：第一层造数脚本的改造（**2026-09-12 登记防丢**）

> **为什么记在 B 册**：这些改造属于**第一层（造数）**，正文归属 [[Python模拟数据与数据隔离方案]]（A 册）；
> 但它们是 2026-09-12 做 ③ AI 并发压测时**当场发现并拍板**的，先登记在此**防止丢失**。
> 🔴 **正式实施时**：正文写进 A 册（新增「提量造数」与「秒杀动作 + 秒杀后恢复」两节），并在 [[TODO文件]] 登记状态；
> 本节届时改为指针（与三册「实现与原理」的薄索引做法一致）。
> **来源**：用户 2026-09-12 决策 —— ① **否决"持续运行"**（依据见 9.1）② 改为**给脚本提量 + 提 QPS** ③ **把秒杀动作加进脚本，且每次秒杀后恢复数据**。

### 9.1 为什么否决"持续运行"（留证据，避免将来重复讨论）

| 冲突 | 证据（`deploy/scripts/sim/simulate_data.py`，2026-09-12 读码 + 实测） |
|---|---|
| 🔴 脚本**自己禁止**连续运行 | `:641-644` 预检要求 `testsim%` **必须为 0**（"基线不干净 → 先清理上一批"）→ **第二轮就会被自己的预检拒绝** |
| 🔴 "造数前快照"安全网失效 | A 册 §2.2.3 的快照兜底 = **整库还原**；持续跑意味着还原点越来越远 → 兜底代价随时长线性增长 |
| 🔴 清理是**按 batch 全清**的原子语义 | `--clean --batch X`（CLI `:933-935` + `CLEAN_ORDER`）；持续跑只有"一个 batch 越滚越大"（失去"演示前清干净"的原子性）或"缺'只清旧的'能力"两种坏选择 |
| ⚠️ **慢节奏在面板上几乎看不见** | `time.sleep(random.uniform(0.5, 3.0))` 在**循环内**（`:814`）→ ≈**0.57 QPS** → SkyWalking 上是贴地平线（§六 开头已述）；要好看得改**时段权重 + 会话化** |

> **结论**：持续运行要付的代价（改预检语义 / 改快照策略 / 新增滚动保留 / 会话化 + 时段权重）**高于收益** → **用户 2026-09-12 否决**。

### 9.2 改造一：**提量 + 提 QPS**

| 项 | 现状 | 目标 / 硬约束 |
|---|---|---|
| 数量 | `--days N --per-day M`（`:926-927`），默认 1 × 1000 | 提到**数万级**（放大 `--per-day` 即可） |
| 节拍 | 循环内 `sleep(0.5~3.0s)`（`:814`）→ **≈0.57 QPS** | 加 `--qps` / `--sleep 0` / 可选多线程 —— **这是当前最大的提量瓶颈** |
| 🔴 **写动作的 QPS 天花板（实测 Sentinel 规则）** | — | **秒杀订单提交 = 10 QPS** · **新增订单 / 支付订单 = 各 20 QPS** · `adminLogin` = 10 → **超了必 429**；**浏览类（`/front/**`）无规则 → 可任意提** |
| 用户池真实感 | `--users 20` 固定池 + `random.choice(users)`（`:762`）→ **每人每天 50 次动作**（不像真人） | 用户数应随行为量走（≈ `per_day/8`）；号段容量：`1390009` + 4 位 = **1 万**个 ✅ |
| 行为形态 | 固定 `FUNNEL = 80/15/5`（`:133`）**动作级**抽样（`:754-762`） | 可选：**会话化**（一次上线连看 N 个 → 再决定加购/下单）更贴近真实；A 册 §2.1 的 80/15/5 可保留为**动作级**口径 |

> ⚠️ **提 QPS 前必须确认目标动作没有流控规则**（或像压测那样 Nacos 临时放开），否则量到的是**限流器**而不是造数能力。

### 9.3 改造二：实现 `--with-seckill`（当前只预检、直接跳过）

证据：`simulate_data.py:815-816` —— `⚠️ --with-seckill 未实现（随机码需先从 /seckill/spu/list 取回）→ 本次跳过`。
要补齐：① 从 `/seckill/spu/list` 取 **randCode**；② 在秒杀场次窗口内提交 `秒杀订单提交`；③ 登记 `success` 行（清理矩阵里它已是 `user` 模式）。

### 9.4 改造三：🔴 **秒杀后恢复数据**（用户明确要求）

秒杀是**唯一会动不可逆资源**的动作（`incrementSales` 的**唯一调用方**就是秒杀消费者 `SeckillQueueConsumer:98`），
所以必须"**用完就还**"。

**要恢复的东西（Redis 四类 + DB 四个值）—— 全部实证到代码行：**

| 目标 | 真实 key / 列 | TTL（实测） | 恢复方式 |
|---|---|---|---|
| 秒杀**下单锁** | `mall:seckill:order:lock:<skuId>:<userId>` | **1 分钟**（`SeckillServiceImpl:75` `setIfAbsent("1",1,MINUTES)`）⚠️ `PrefixConfiguration:33` 注释写"10分钟"是**过期注释**，以代码为准 | `DEL`（会自动过期，但删掉更干净） |
| 秒杀**已下单标记** | `mall:seckill:ordered:<skuId>:<userId>` | **2 小时**（`SeckillServiceImpl:125`，值 = 订单号 `sn`） | `DEL`（⚠️ **支付成功时服务端自己会删**：`OmsOrderServiceImpl:242`） |
| 🔴 秒杀**已购买标记** | `mall:seckill:reseckill:<skuId>:<userId>` | **永久**（`OmsOrderServiceImpl:239` → `boundValueOps(...).set("1")` **不带 TTL**） | 🔴 **必须 `DEL`** —— 它就是"同一用户同一 SKU 永久不能再买"（`SeckillSpuServiceImpl:199 isPurchased` 读它） |
| 预热库存 | `mall:seckill:sku:stock:<skuId>` | 预热时写入（`SeckillInitialJob:86`） | 按差值 `SET` 回（**不可逆**，见 A 册 §2.2.7） |
| DB | `seckill_sku.seckill_stock` · `pms_sku.stock` · `pms_spu.sales` · `seckill.success` 行 | — | **记账回补**（做法见下） |

> ℹ️ **幂等锁不需要"恢复"**：`idempotent:{seckill\|order\|pay}:<userId>:<argsDigest>` 的 TTL 只有
> **3 / 5 / 10 秒**（`SeckillController:44` / `OmsOrderController:48,79`），且 `IdempotentAspect:61`
> **调用结束就 `delete`** → 除非进程被杀，否则自动释放。
> ⇒ 用户最初提到的"订单锁 / 下单锁 / 支付锁"其实**分属两类**（上表的秒杀业务标记 + 本注的幂等锁），
> 需要脚本主动删的是**上表前三行**。

> 🔴 **`reseckill` 由 mall-order 写、mall-seckill 读** —— 恢复脚本必须跨模块理解这个键（写 = 支付成功、读 = 购买校验）。
> ⚠️ **实施前需确认 `sku_id` 两侧同源**：写侧用订单项的 `item.getSkuId()`（`OmsOrderServiceImpl:238`），
> 读侧用 `SeckillSku.getSkuId()`（`SeckillSpuServiceImpl:208`）。若两侧不同源，删错键 = **限购清不掉**（需实测一次）。

> 🔴 **与既有原则的冲突，必须写清**：A 册 §2.2.7 明令"**不做减回去的补偿运算**"—— 那是针对**普通订单**
> （累加值没有"谁贡献多少"的记录）。**秒杀动作可安全例外**：单 SKU、单次、数量已知（qty = 1）→
> 做法是「**秒杀前读 3 个 DB 值 + Redis 库存键 → 秒杀后按"差值/回原值"回补 → 再读一遍校验**」——
> 这是**记账**，不是猜。实施时须在 A 册 §2.2.7 补一段"秒杀动作的例外与理由"。

> ⚠️ **限购会"烧掉"用户×SKU 组合**：支付后**永久**不能再买 → 用户池固定时 **20 用户 × 12 秒杀 SKU = 240 次上限**；
> 持续用同一对会一直失败 → 需**轮换用户或扩 SKU**。前置：[[商品与秒杀扩容方案]]（#63/#64），
> 且 **#64 的"两套 `spu_id` 语义冲突 → 4/12 个秒杀 SKU 从不被预热"** 会直接影响秒杀造数的成功率。

### 9.5 与既有条目的关系（**不新增编号**）

| 本次改造 | 归属 |
|---|---|
| 提量 / 提 QPS | [[TODO文件]] **#67 之 ②「正式造数」** 的增强 |
| 秒杀动作 | **#67 之 ④ `--with-seckill`** |
| 秒杀后恢复 | 🆕 新需求 → 并入 **#67** 的说明（**不新开 `#N`**，避免牵连全仓 `#N` 引用） |

---

## 十、📊 实测结果（2026-09-12 · 第二层 AI 并发压测**首轮完成**）

> **前置（全部就绪并逐项验证）**：内网 mock（`deploy/scripts/ai/mock_llm.py`，0 token 模式，自测 14/14 + 自压基准 0 失败/峰值并发 150/306 req/s）· compose 透传（D1）+ mock override · Nacos `ai-chat` **5→200** · 800 个 AI 专用用户池（`testsimai*`，独立批次可整批清理）· **#62 已修**（未修时并发下 ~50% 假 500，见 §六·附）
> **链路开关**：`AI_AGENT_ENABLED=false`（pipeline，2 次 LLM 调用/请求）· `=true`（agent，工具轮+收敛轮+JSON，3 次调用/请求）
> **命令**：`SIM_USER_PREFIX=testsimai python3 load_test.py --mode ai --link <agent|pipeline> --users 800 --steps 20,50,100 --duration 45 --json ~/ai_<link>.json`（新机内网，不占公网带宽）

### 10.1 结果表（**判承载只看「成功 RPS」**，理由见 10.3①）

| 链路 | 异步池 | 并发 | 成功 | **成功 RPS** | 总 RPS | 其它降级 | 硬失败 | p50 | p99 | TTFT p50 |
|---|---|---|---|---|---|---|---|---|---|---|
| pipeline | **8**（默认） | 20 | 506 | 10.9 | 10.9 | 0 | 0 | 1730ms | 2324ms | 1089ms |
| pipeline | 8 | 50 | 554 | 11.2 | 11.2 | 0 | 0 | 4313ms | 4927ms | 3669ms |
| pipeline | 8 | 100 | 605 | 11.2 | 11.2 | 0 | 0 | 8618ms | 9264ms | 7989ms |
| **pipeline** | **50** | **20** | **972** | **21.2** | 21.2 | **0** | 0 | **890ms** | 1535ms | **169ms** |
| pipeline | 50 | 50 | 988 | **21.4** | 50.5 | 1348 | 0 | 945ms | 1922ms | 320ms |
| pipeline | 50 | 100 | 870 | 18.6 | 55.4 | 1722 | 3 | 1697ms | 3088ms | 1034ms |
| **agent** | **50** | **20** | 936 | ~20.4 | 20.7 | 12 | 0 | **943ms** | 1313ms | **109ms** |
| agent | 50 | 50 | 867 | ~19.3 | 50.6 | 1468 | 0 | 953ms | 1931ms | 246ms |
| agent | 50 | 100 | 787 | ~17.9 | 53.6 | 1701 | 7 | 1786ms | 3111ms | 1032ms |

> `agent` 三行是**补算**的（那一轮跑在 `ok_rps` 字段加入之前，用 `成功/总耗时` 折算）；其余由脚本直接输出。

### 10.2 🎯 逐层"找墙"（本轮最值钱的产出）

```
第 1 层墙：Spring MVC 异步执行器默认并发 8
          证据：pool=8 时 mock 侧「峰值并发 LLM 调用 = 8」、RPS 恒定 11 且延迟随并发线性增长；
                放开到 50 后 → mock 峰值 20、总 RPS 53.6（+379%）、p50 8618→1786ms
          ⚠️ 它一开始被误判为「Spring 的 SSE_EXECUTOR」—— 实际 `SSE_EXECUTOR`（虚拟线程，无上限）
             **只被旧接口 `sendStreamLegacy` 用**；主链路 `/ai/chat/stream` 走的是 Spring 默认
             `applicationTaskExecutor`，而全仓没有配置 `spring.task.execution` ⇒ 默认 core-size=8。
          证据补强：应用日志出现 `task-46`/`task-41` 等编号 ≥8 的线程（这一点与"恰好 8"不完全自洽，
             已如实记录；但"并发上限 ~8"由 mock 侧计数**直接测得**，不依赖配置推断）。
第 2 层墙：AI 并发闸门 20（设计值 `concurrent-max: 20`，全局单一 Semaphore）
          证据：pool=50 后立刻现身 —— 服务端日志 `【AI并发闸门】chat:agent 场景繁忙：当前并发 20/20，
                已拒绝本次调用`（每线程上百条）；mock 峰值并发恰好 20
第 3 层墙：入口 Sentinel `ai-chat`（本轮临时放开 5→200，因此没撞到）
第 4 层墙：2 元/天预算（本轮让 mock 回 0 token 规避，因此也没撞到；`budgetExceeded` 与闸门满**同一句文案**）
```

**⇒ 结论**：agent 链路可达 **≈20 成功请求/秒**、pipeline **≈21**，两者**都在 50 并发开始被闸门拒绝**；
**20 并发档最健康**（p50 < 1s、TTFT 100~170ms、0 硬失败）。
**⇒ 一个与预期相反的事实**：Agent（3 次 LLM 调用/请求）与 pipeline（2 次）的**成功 RPS 几乎相同**（20.4 vs 21.4）。
按"闸门 20 ÷ (调用次数 × 单次占槽)"的简单模型，pipeline 本应快约 1.5 倍 ⇒ **该模型与实测不吻合**，
单次调用的实际占槽时长/次数仍需再测（**未深挖，如实记录**，不编圆）。

### 10.3 ⚠️ 三个读数陷阱（本轮实测踩到，已写进脚本）

| # | 陷阱 | 证据与处置 |
|---|---|---|
| ① | **总 RPS 会被"快速失败"抬高** | agent@100 总 RPS **53.6**，但成功仅 ~18/s（65%+ 是闸门毫秒级拒绝）⇒ 脚本已新增 **`成功 RPS`** 并排打印，**判承载只看它** |
| ② | **"闸门满"在流式路径上对客户端不可区分** | 闸门满时服务端 `catch` 写的是通用文案 `AI 服务暂时不可用，请稍后重试。`，与真异常**同一句** ⇒ 脚本原 `busy` 列报了 **0**（实际 65% 被拒）。现脚本会**打印提示 + 给出判据**：`docker logs csmall-ai \| grep '【AI并发闸门】'`；**不把不确定的归到 busy** |
| ③ | **#62 未修时数字全是假的** | 未修时 20 并发硬失败 **50%**、RPS 12.9、p50 1787ms；这些"更好看"的数字来自**一半请求在安全过滤器被秒拒（~130ms）**。修复后同条件：硬失败 **0**、成功 163、RPS 7.5、p50 2210ms ⇒ **RPS 下降反而是修复生效的标志** |

### 10.4 与 #62 的关系

`/ai/chat/stream` 并发下 ~50% HTTP 500（根因 = ASYNC/ERROR 二次派发被授权规则拒绝）**是本次压测的拦路虎**：
不修的话，20 并发档就会因"硬失败率 47.5% > 中止阈值 5%"被**脚本自动中止**（本轮实测确实被中止过）。
修复与验收见 [[TODO文件]] **#62**（已收口）；未修的其余 7 个模块属潜伏风险，见同条"影响面"。

### 10.5 可讲的点（面试/复盘）

1. **"系统扛不住"要逐层归因**：同一台机器上叠了**四层**保护/限额（异步池 8 → AI 闸门 20 → 入口限流 → 预算），
   不逐层证伪就会把"限流器很有效"当成"承载上限"讲。
2. **先证伪干扰项**：入口限流临时放开（Nacos 热改、零重建）、预算用 0 token 规避、频控用 800 用户池绕开。
3. **压测工具本身也要证伪**：mock 必须线程化 + 放大 accept 队列（`request_queue_size` 默认 5，100 并发实测丢 17 个连接）；
   同时要证明客户端不是瓶颈（新机 load 0.27/2 核、脚本仅 4% CPU）。
4. **一次真实压测能挖出生产缺陷**：#62（并发下 50% 5xx）与"闸门满文案不可区分"都是这轮挖出来的。

---

## 十一、🆕 可选深化：补 75/150 档 + 量「单次调用占槽」（2026-09-12 预备 · 待窗口）

> **为什么要做**：§十 的首轮实测留了**一处没能解释的反常** —— pipeline（单轮）与 agent（双轮）的
> **成功 RPS 几乎相同**（21.4 vs ~20.4）。按"双轮更占资源"的直觉，agent 的承载应当明显更低（我在 §十 里
> 也只写了"这处不吻合、没有编圆"）。同时阶梯只到 100，**"过载后是优雅拒绝还是雪崩"还没看到**。

**两个目标**

1. **量「单次调用占槽时长」**（同一并发下：吞吐 × 平均占槽 ≈ 并发上限）⇒ 用它**定量**解释"两条链路成功 RPS 持平"：
   若 agent 的首轮只做工具选择（不吐 token）、或两轮并未叠加占槽，就能给出机制解释，而不是"看起来反常"。
2. **补 75 / 150 两档**：看**拐点之后**的 p99 与失败率（闸门 20 的拒绝占比、是否出现延迟雪崩）。

**可执行清单（每步都有回滚点；⚠️ 会临时改生产：mall-ai 出口 + Nacos 阈值）**

| 步 | 动作 | 命令要点 | 回滚 / 验收 |
|---|---|---|---|
| 0 | 记录现场 | `docker inspect csmall-ai` 存下 `AI_API_BASE_URL`；Nacos 规则原文落盘 + 记 md5 | 结束时逐条比对回位 |
| 1 | 起 mock（新机） | `python3 /tmp/mock_llm.py --port 9999 --prompt-tokens 0 --completion-tokens 0` | 🔴 **0 token** 是为了**不污染日预算**（§十 问题 5） |
| 2 | 挂 override（老机） | `docker compose -f docker-compose.yml -f docker-compose.mock-llm.yml up -d mall-ai` | 先核对 override 里 base-url = 新机内网 |
| 3 | 放开入口限流 | Nacos `ai-chat` 阈值 5 → 200 | ⚠️ **热改后立刻 GET 可能读到旧快照，等 1~2 秒再复核**（§十 的 3 个读数陷阱之一）<br>🔴🔴 **dataId 必须逐字对齐**：应用订阅的是 **`mall-ai-flow-rules`（无扩展名）**，而仓库文件叫 **`mall-ai-flow-rules.json`** —— 2026-09-12 我把配置 PUT 到带 `.json` 的 dataId：Nacos 返回 `true`、GET 也读到 200，**但应用毫无变化**（其 `notify-ok` 的 md5 仍是旧规则的）。判据：**改完必须看应用侧 `notify-ok` 的新 md5**（`docker logs csmall-ai \| grep notify-ok`），只看 Nacos 返回值会被骗。 |
| 3b | **先验证限流真的放开了**（别急着压） | 小档探针：`--steps 50 --duration 20`，看**成功 RPS 是否 > 5** | 若成功 RPS 恒定 ~5 ⇒ 规则**没生效**（回去查 dataId + 应用日志 md5），此时压全阶梯纯属浪费 |

> 🟡 **2026-09-12 首次尝试的状态（如实记录）**：mock 起停、compose override、Nacos 改/还原**均已跑通并核对**；但**阶梯未跑成** —— 我的编排脚本把函数命名为 `R`，而 PowerShell 里 **`r`/`R` 是 `Invoke-History` 的别名** ⇒ 5 段远程命令**全部空跑**（既没压测，**也没执行内嵌的还原**）。发现后**第一时间手工还原并逐项核对**（Nacos 回 5、影子配置删除、mall-ai 回真实出口、mock 停）。
> ⇒ **两条纪律**：① 编排脚本**不用单字母/易撞别名的函数名**（用 `Ssh-Run`）；② **还原动作别内嵌在长作业里** —— 独立一步、执行后立刻核对（否则编排一坏，生产会一直指向 mock）。
> ⏭️ **待重跑**：本项两个目标（75/150 档 + 单次占槽）**未受影响**，随时可开窗口重做。
| 4 | 压测（新机） | `SIM_USER_PREFIX=testsimai python3 /tmp/load_test.py --mode ai --link pipeline --users 800 --steps 20,50,75,100,150 --duration 45 --json ~/ai2.json`；`--link agent` 再跑一遍（两条链路**不混着讲**） | 只看**成功 RPS**（§十 问题 7：总 RPS 会被毫秒级失败抬高） |
| 5 | 量占槽 | 每档记录「成功数 / 峰值并发」+ p50/p99 ⇒ 反推**单次调用占槽** | 与 20/50 两档交叉验证；解释不了就如实写"仍未解释" |
| 6 | 恢复 | Nacos 改回 5 → 移除 override → `docker compose -f docker-compose.yml up -d mall-ai` → **核对 base-url 已回真实地址** | 与步 0 现场逐条比对（`docker inspect` + Nacos md5） |
| 7 | 收尾 | 停 mock（新机 **9999 空闲**）· 删凭据文件 · 结果回填 `sim_batch.note` | 录一次 `docker ps` 与探活 |

> 📌 **归属**：这是 **#67 之 ③ 的可选深化** —— 首轮已完成，本项**不影响任何功能**，只提升"承载曲线 + 拐点"的说服力与面试/录像素材。
> 🧪 **预备状态（2026-09-12 实测）**：`mock_llm.py` 在新机可拉起并应答（见下）；`docker-compose.mock-llm.yml` 已在仓库并被跟踪 ✅。

---

**关联文档**：[[TODO文件]] **#67**（本方案对应其 ③）· [[Python模拟数据与数据隔离方案]]（**姊妹篇**：造数 + 隔离 + 展示）· [[演示录像操作手册]]（#67 之"录像"）· [[问题解决--生产造数的数据隔离与复核方法]]（造数侧已提炼）· [[商品与秒杀扩容方案]]（库存池放大）· [[TODO第三批实现与原理-1]]（§三·3.1 未完成部分）· [[TODO已完成]]（#48 第一层 §十九）

**切割说明（2026-09-11）**：本文档由《Python 模拟数据 + AI 并发测试方案》**按任务拆分**而来（原文件已改为**薄索引**）。原 §三（3.1~3.5）= 本方案 §一~§五；原 §〇 的 ⑤~⑧ 行 = 本方案 §〇；原 §〇.1 的 D1~D5/D8a/D8b + 两项决策 = 本方案 §六；原 §七 的压测侧 10 项 = 本方案 §七；原 §八 的压测/AI 话术 = 本方案 §八。
