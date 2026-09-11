# AI 并发测试方案（第二层 · TODO #67 之 ③）

> **创建**：2026-09-11（自《Python 模拟数据 + AI 并发测试方案》**切割**而来）
> **状态**：🔴 **未开始** —— `mock_llm.py` 未写 · compose `AI_API_BASE_URL` 透传未加 · Nacos 规则未放开 · Agent 双链路未压
> **归属**：[[TODO文件]] **#67**（= #48 剩余部分）之 **③ 第二层 AI 并发压测**
> **定位**：**只测「服务端承载」**，不调真实付费 LLM —— 内网 mock + 临时放开限流，量出"第几个并发开始降级"
> **姊妹篇**：[[Python模拟数据与数据隔离方案]]（第一层：造数 + 数据隔离 + 可观测展示）
> **引用关系**：本方案 §二 / §四 / §六 引用姊妹篇的 **§2.2.8**（造数避峰）/ **§四**（服务器资源与内存基线）/ **§2.2.6**（Redis 键清单）
> 🧩 **已归纳**：❌ **尚未提炼** —— AI 侧**没有**对应「一类问题」文档；造数侧已提炼为 [[问题解决--生产造数的数据隔离与复核方法]]
> **⚠️ 未完成约束**：本文档是 #67 该子项的**唯一方案正文**，**做完前不得归档**（[[TODO第三批实现与原理-1]] §三·3.1 只留指针，正文在此）

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
> ⚠️ 本骨架是**设计稿**：`tool_calls` 分片契约以 [[TODO第三批实现与原理-1]] §3.1 **实验 K（实测 19 片）** 为准，实施时先用真实请求对齐一次再压。

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
| 2 | **备份 Nacos 规则原文**（`mall-ai-flow-rules`）到文件 + 记 md5 | `curl ".../configs?dataId=mall-ai-flow-rules&group=SENTINEL_GROUP"` 输出存盘 + `md5sum` |
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

**关联文档**：[[TODO文件]] **#67**（本方案对应其 ③）· [[Python模拟数据与数据隔离方案]]（**姊妹篇**：造数 + 隔离 + 展示）· [[演示录像操作手册]]（#67 之"录像"）· [[问题解决--生产造数的数据隔离与复核方法]]（造数侧已提炼）· [[商品与秒杀扩容方案]]（库存池放大）· [[TODO第三批实现与原理-1]]（§三·3.1 未完成部分）· [[TODO已完成]]（#48 第一层 §十九）

**切割说明（2026-09-11）**：本文档由《Python 模拟数据 + AI 并发测试方案》**按任务拆分**而来（原文件已改为**薄索引**）。原 §三（3.1~3.5）= 本方案 §一~§五；原 §〇 的 ⑤~⑧ 行 = 本方案 §〇；原 §〇.1 的 D1~D5/D8a/D8b + 两项决策 = 本方案 §六；原 §七 的压测侧 10 项 = 本方案 §七；原 §八 的压测/AI 话术 = 本方案 §八。
