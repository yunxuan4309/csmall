# AI 导购 Agent 升级方案

> **状态**: ✅ **已敲定实施（2026-09-10 用户拍板：做）** —— 按 **P0 → P1** 分期推进（P2 可选）；**实施前必读「〇、决策记录 + 实施前代码校正」**（其中 6 条校正来自读码实测，照原稿做会踩空）
> **进度（2026-09-10）**：**P0 代码已完成**（提交 `09d22b1`）**并已部署生产、两阶段验证通过**（提交 `0fe1a1e` 的开关注入 + `work/部署指令-step2-agent-p0.md`）。**⚠️ 生产当前 `AI_AGENT_ENABLED=true`（开关是开的）**。**⚠️ 与原计划的偏差**：P0 的 Agent 只覆盖**同步** `/ai/chat/send`，SSE 仍走旧流水线（原 S5「最后一轮复用 SSE」顺延 P1）。
> **关联**: [[TODO文件]]#32、[[面试准备/09-AI模块]] Q0/Q12、**[[AI模型名停用风险与thinking参数改造方案]]（#58，与本项强相关，见校正⑥）**
> **定位**: 技术演示增强 + 面试素材(业务收益为零——生产 0 调用,简历未投)

---

## 〇、决策记录 + 实施前代码校正（2026-09-10 敲定）

### 1. 决策（用户 2026-09-10 拍板）

| 项 | 结论 |
|---|---|
| **做不做** | ✅ **做**。定位不变：**演示增强 + 面试素材**，业务收益为零（生产 0 调用） |
| **范围** | **P0（最小 Function Calling，首个可见里程碑）→ P1（完整单 Agent）**；P2 框架化仍可选 |
| **入口开关** | 新增 `cooxiao.ai.agent-enabled`（默认 `false`，灰度切换，与 `embedding-enabled` 同套路） |
| **对外接口** | `/ai/ask`、`/ai/chat/send`、`/ai/chat/stream` **全部不变**（前端无感） |
| **本期不做** | 写操作（加购/下单）不给 AI 执行权——只"建议"，或**根本不暴露写工具** |

### 2. 实施前代码校正（读码实测，⚠️ 照原稿做会踩空）

> 原方案（2026-09-02）是**设计稿**，未逐条对代码核实。本次敲定时发现 **6 处需修正**：

| # | 原稿写法 | 代码事实（证据） | 校正 |
|---|---|---|---|
| ① | P1「**动作审计**：AgentActionLog（**先 Redis 后 DB + Flyway**，参考 IoT DecisionLog 表）」 | 🔴 **`mall-ai` 没有任何数据库栈**：`pom.xml` 无 JDBC / MyBatis / MySQL driver / Flyway；无 `db/migration` 目录；`application*.yml` 无 `spring.datasource`。它本来就是**无状态服务**（只用 Redis + ES + Dubbo） | **改为只落 Redis**：key `ai:agent:action:{sessionId}`（List：`LPUSH` + `LTRIM 0 49` + `EXPIRE` 7 天）。**DB 化降为可选 P2**——给 mall-ai 加数据源等于引入一整套持久化栈，与"无状态"定位冲突，收益仅是演示 |
| ② | P0「DeepSeekAiClient 支持 `tools` + 解析 `tool_calls`」 | 消息类型是 `List<Map<String, String>>`（`AiClient` 接口 2 个 `chat` 重载 + `doChat`）。但 tool 场景的 message **不是纯 String**：assistant 消息带 `tool_calls` 数组、`role:"tool"` 消息带 `tool_call_id` | **必须先把消息类型加宽为 `List<Map<String, Object>>`**：涉及 `AiClient`（接口）+ `DeepSeekAiClient`（**保留 String 版兼容重载**，避免动所有调用方）+ `ChatServiceImpl.buildMessages` |
| ③ | P0「ChatServiceImpl 改造：请求带 tools → … → 流式回复」 | **LLM 调用有两条独立路径**：同步走 `AiClient.doChat`（RestTemplate）；**SSE 流式走 `ChatServiceImpl.doStreamDeepSeek`（裸 `HttpURLConnection`、模型硬编码 `deepseek-v4-flash`、`stream=true`）**——两份请求体各写各的 | **Agent 循环只放"非流式"路径**：新增 `DeepSeekAiClient.chatWithTools(...)`（`stream=false`）跑 1~3 轮工具；**最后一轮再复用现有 `streamDeepSeek`** 吐答案给用户。**不要**去 SSE delta 里解析 `tool_calls`（分片增量，拼接解析复杂、收益为零） |
| ④ | P0「工具 `search_products` 复用 `RagServiceImpl.intentSearch` / `SearchServiceImpl`」 | `RagServiceImpl.intentSearch(...)`、`buildContext(...)`、`buildRelatedProducts(...)` 是**包私有方法**（无修饰符，仅 `com.cooxiao.mall.ai.service.impl` 包内可见）；`public` 的只有 `ask(...)` / `structuredSearch(...)` / `fullTextSearchNoPrice(...)` | 工具实现类**放进 `...ai.service.impl` 同包**（最省事），或改用 public 方法。**不要新建包放工具类**（会调不通） |
| ⑤ | 未提 | 预算/闸门/限流设施已齐备：`TokenBudgetService`（2 元/天）、`AiConcurrencyGuard`（并发 20，满即失败不排队）、Sentinel 三组规则（`ai-chat=5` / `ai-reason=10` / `ai-light=30`）、`AiUserRateLimiter`（60s/10 次）；且 `doChat` 内**已自带** `checkBudget()` + `usage` 记账 | **全部复用，不新建**。但要注意：Agent 循环把 LLM 调用数**放大最多 3 倍** → 对 `concurrent-max` 与日预算的压力要能讲清（演示环境 0 调用，可接受） |
| ⑥ | 未提 | 生产 `chat-model: deepseek-chat`，而该模型名**已被官方公告停用**（见 **[[TODO文件]]#58**）；DeepSeek 的正解是"同一模型 + `thinking` 开关"（`{"thinking":{"type":"disabled"}}`） | **与 #58 强耦合**：Agent 的**工具选择必须是稳定 JSON（`tool_calls`）** → 建议 **#58 与 #32 同期落地**（先做 thinking 开关改造，再在其上做 Function Calling），否则"模型名随时失效"的风险会直接压在新功能上 |

| ⑦ | P0 S3 未提 | 🔴 实测（2026-09-10 实验 F）：**`tools` 与 `response_format: json_object` 不能共存** —— 带上 `response_format` 时模型**直接输出 JSON，不再触发 `tool_calls`**（`finish_reason=stop`） | 工具选择轮**只带 `tools`，不设 `response_format`**；工具参数的稳定性靠 **JSON Schema 约束 + 服务端二次校验**（不是靠 json mode）。⚠️ 现有 `chatWithModel(jsonMode=true)` 与 Function Calling **不兼容**，两者必须分开走 |

### 3. 🔬 2026-09-10 实测复验（可行性已证实）

> 与 [[AI模型名停用风险与thinking参数改造方案]] §3.1 **同一批实验**（老机直连 DeepSeek，最小请求，`max_tokens` 50~300）——**不凭文档引述，直接验证 P0 路线成立**：

| 实验 | 输入 | 结果 | 对本方案的意义 |
|---|---|---|---|
| **E** | `thinking: disabled` + `tools` + "推荐 5000 以内的手机" | **200**，`finish_reason=tool_calls`，**`tool_calls` 1 个**，参数 `{"keywords":"手机","budgetMax":5000}` | ✅ **P0 可行性证实**：模型确实**主动调工具**、参数抽取准确 → S3/S4/S5 路线成立 |
| **C** | `thinking: disabled` + `tools` + 历史消息**不带** `reasoning_content` | **200** | ✅ **工具轮用 `disabled` 可绕开官方 400 规则**（"思考模式 + tools 必须回传 `reasoning_content`"，实验 B 实测 400）→ **这是 P0 的关键设计依据** |
| **F** | `thinking: disabled` + `tools` + `response_format: json_object` | **200 但 `tool_calls` 为空**，模型直接返回 JSON | 🔴 **新坑 → 校正⑦**：工具轮**绝不能设 `response_format`** |

> ⚠️ **附带结论（写进代码注释）**：既然工具轮用 `thinking: disabled`，就**不需要维护 `reasoning_content` 回传链路**（那是 thinking enabled + tools 才有的负担）。这是"工具轮 disabled"设计除稳定性之外的**第二个收益**。

### 4. 建议落地顺序

```
① #58 thinking 开关改造（只动 mall-ai，~几十行 + 1 个容器重建）   ← 先做：给 Agent 一个稳定的模型契约
② #32-P0 最小 Function Calling（~0.5~1 天）                      ← 首个可见里程碑
③ #32-P1 完整单 Agent（~2~3 天）
④ （可选）#32-P2 框架化 / 审计 DB 化
```

> **为什么建议先 #58**：不先做也能上 P0，但 `deepseek-chat` 一旦被撤，`tool_calls` 链路同时失效。两个改动**都只动 `mall-ai`**，合在同一维护窗口更省事。

### 5. 为什么**手写**而不是引入 Spring AI（与 [[AI模型名停用风险与thinking参数改造方案]] §4.6 / §4.7 呼应）

> 用户之问："不引入 Spring AI，我们怎么写 agent？自己造船吗？"
> **答：不用造船 —— 船已经有了，我们只是装一个船舵。**

**① 认知前提：Agent 不是框架，是"一个循环 + 一份工具清单"**

Function Calling + ReAct 的**本体**只有三十来行：

```java
messages = [system, ...history, user]
for (round = 1; round <= maxRounds; round++) {         // ① 循环（ReAct 的"Re"）
    r = chatWithTools(messages, TOOLS)                 // ② 带 tools 调一次（非流式）
    if (r.toolCalls.isEmpty()) break                   // ③ 模型决定不再调工具 → 收敛
    messages += r.assistantMessage                     // ④ assistant(含 tool_calls) 原样回灌
    for (tc : r.toolCalls)                             // ⑤ 执行工具（ReAct 的"Act"）
        messages += { role: "tool", tool_call_id: tc.id, content: tool(tc).run() }
}
answer = streamChat(messages)                          // ⑥ 最后一轮流式吐答案
```

**框架省掉的正是这几十行；框架真正值钱的是它周边的生态。** 而周边生态我们**要么已有、要么用不到**。

**② 要"造"的 vs 已"有"的（本项目的真实账）**

| 部件 | 处置 | 依据 |
|---|---|---|
| 工具契约 `AiTool` + `ToolRegistry` | 🆕 新建（~60 行） | 只需 `name/spec/execute` 三个方法 |
| 带 `tools` 的请求 + 解析 `tool_calls` | 🆕 扩展 `DeepSeekAiClient`（~80 行） | 复用既有 `concurrencyGuard` / `checkBudget` / `usage` 记账 |
| Agent 循环 `agentBranch` | 🆕 新建（~100 行） | 最后一轮**复用现有 `streamDeepSeek`** |
| 工具实现（`search_products` 等） | 🆕 ~40 行/个 | **内部复用 `RagServiceImpl`**（检索已存在，零重复） |
| 会话 / 多轮记忆 | ♻️ **已有** | `SessionManager`（Redis，TTL 24h） |
| 预算控制 | ♻️ **已有** | `TokenBudgetService`（2 元/天，`doChat` 内已自动记账） |
| 并发闸门 | ♻️ **已有** | `AiConcurrencyGuard`（上限 20，满即降级） |
| 限流 / 频控 | ♻️ **已有** | Sentinel 三组规则 + `AiUserRateLimiter` |
| SSE 流式输出 | ♻️ **已有** | `ChatServiceImpl.doStreamDeepSeek` + 前端 thinking 卡片 |
| 检索 / 召回 / 重排 | ♻️ **已有** | `RagServiceImpl` / `SearchPipeline` / ES |
| 降级兜底 | ♻️ **已有** | 既有 RAG 快速路径（双路径设计，见附录 A） |

→ **新增代码 ≈ 300 行**（其中一半是 Schema 与工具），**不是"造船"，是"装船舵"**。
→ 反过来看：Spring AI 自带 `ChatMemory` / `Advisor` / `VectorStore` 体系，进来后反而要**绕过或适配**我们已有的 Redis 会话、ES 检索、预算闸门 —— **框架净收益被自己的既有设施抵消**。

**③ 手写在本场景是"正解"，不是"将就"**

| 理由 | 说明 |
|---|---|
| **规模匹配** | 单 Agent / 3 个只读工具 / 3 轮上限。框架的价值在多供应商抽象 + 生态（MCP、向量库、Advisor、评测），我们要么已有、要么用不到 |
| **可控性=核心诉求** | 我们刚用实验摸清 `reasoning_content` 回传（400）、`tools`×`response_format` 互斥 —— 这些**框架会藏起来**。自己写，每条规则都在手里，出 400 一眼定位 |
| **面试/演示价值** | 手写能把"**agent 到底是什么**"讲透（`tool_calls` 消息结构 / ReAct 轮次 / 边界设在哪）；框架会变成"我调了个注解" |
| **代价不成比例** | 引入 Spring AI 的前置是**全站 Boot 升级**（§4.7：13 个模块、两台机器、12 个服务回归） |

**④ 诚实的代价（不吹）**

| 不用框架会失去 | 影响 | 缓解 |
|---|---|---|
| 现成的工具编排 | 自己写循环 + 解析 | 就是上面那 ~150 行，且**已被实验验证**（E/C 两格） |
| 多供应商无缝切换 | 换供应商要改请求体 | `base-url` + 模型占位符已可配；DeepSeek/硅基流动均为 **OpenAI 兼容协议** → 配置层可换；只有换**非兼容协议**（如 Anthropic 原生）才需改客户端 |
| 结构化输出框架 | 自己校验参数 | **本来就必须做**：JSON Schema 声明 + **服务端二次校验**（不能只信模型） |
| 可观测 | 自己记 | **已有**：`usage` 记账 + SkyWalking 全链路 + 结构化日志 |
| 生态（MCP / 向量库 / Advisor / 评测） | 用不到 | 项目不需要 |

**⑤ 不锁死：将来迁 Spring AI 的映射路径（本次抽象是框架无关的）**

| 本次设计 | Spring AI 等价物 |
|---|---|
| `AiTool` 接口 | `@Tool` 注解 / `FunctionCallback` |
| `tasks.json`（档位 + 思考模式） | `ChatOptions` / `ToolCallingChatOptions`（`spring.ai.openai.chat.options.model`） |
| `agentBranch` 循环 | `ChatClient` + `ToolCallingAdvisor`（或直接删除） |
| `SearchProductsTool.execute` | **一行都不用改**（业务实现与框架无关） |

→ 迁移是**替换实现**，不是重写业务；**本次投入不浪费**。

**⑥ 第三条路（轻量库）也评估过，仍不选**

| 选项 | 结论 |
|---|---|
| 直接用 **OpenAI 兼容 HTTP**（现状） | ✅ **选它** —— 零新依赖，与项目"不增加外部依赖"原则一致 |
| **LangChain4j**（可脱离 Spring Boot starter 使用） | 🟡 理论上可绕开 Boot 升级，但**仍需版本核实**，且是**新增一个重依赖**；本项目场景收益不足以抵消 |
| **Spring AI / Spring AI Alibaba** | ❌ 卡 Boot 代际（§4.6/§4.7） |

> **一句话**：**没有"既用 Agent 框架、又不升 Boot"的免费午餐**；可行且最省的是**保持 HTTP 直连 + 自己写一层薄编排**。

---

## 一、现状盘点(代码实证)

| 已有能力 | 代码证据 |
|---------|---------|
| LLM 意图解析 | `ChatServiceImpl.extractSearchIntent`:消息 → SearchIntent JSON(预算/品牌/品类) |
| 意图驱动检索 | `RagServiceImpl.intentSearch`:按预算/品牌/品类过滤 + ES 多路召回 |
| 跨轮偏好记忆 | `session.preferences` 存 budget("5000 以内"会记住) |
| SSE 流式 + 思考卡片 | ChatServiceImpl + SearchPipeline(onThinking) |
| 预算控制 | TokenBudgetService(2 元/天,Redis 日计数) |
| 会话管理 | SessionManager(ai:chat:session:* TTL 24h) |

**本质**: "LLM 解析意图 → 代码写死执行路径 → 生成"——LLM 只当**解析器**,没有动作决策权,无工具调用,无循环。

---

## 二、与 Agent 的差距清单

| # | 差距 | 当前 | Agent 应该 |
|---|------|------|-----------|
| 1 | LLM 无动作决策权 | LLM 只输出 SearchIntent,执行由代码决定 | LLM 自己选工具("要查商品/要对比/要看库存") |
| 2 | 无 Function Calling | "检索"是固定代码,LLM 不可见不可选 | 声明工具集,LLM 按需调用 |
| 3 | 无执行循环 | 意图→检索→答,一次到底 | ReAct:查→评估→再查→答 |
| 4 | 无执行验证/反思 | 生成完就完 | 工具结果校验,答非所问重检索 |
| 5 | 记忆 | ✅ 偏好记忆(超纯聊天记忆,亮点) | + 执行轨迹记忆(做了哪些动作) |

**一句话**: 现在是"会听指令的检索器";Agent 是"会自己决定怎么干活的助手"。

---

## 三、电商 Agent 场景与工具集设计

### 候选场景

| 场景 | 说明 | 工具 |
|------|------|------|
| 深度导购(升级现有) | 多轮追问预算/品牌/需求 → 推荐+理由 | search_products |
| 主动比价 | "这个比 X 便宜吗?" → 对比表 | compare_products(复用 ProductCompareServiceImpl) |
| 库存/秒杀助手 | "Y 有货吗?" → 实时库存、场次提醒 | get_stock(Dubbo 调 product/seckill) |
| 订单/售后客服 | "订单到哪了?" → 查订单/物流 | query_order(Dubbo 调 order) |
| 优惠/凑单 | 查优惠券、凑满减建议 | get_coupon |
| 写操作(需谨慎) | 加购/下单 | add_to_cart / create_order(建议期不做) |

### 工具声明格式(OpenAI 兼容 tools 参数)

```json
{
  "type": "function",
  "function": {
    "name": "search_products",
    "description": "按条件检索商品,返回商品列表",
    "parameters": {
      "type": "object",
      "properties": {
        "keywords": {"type": "string"},
        "budgetMax": {"type": "number"},
        "brand": {"type": "string"}
      },
      "required": ["keywords"]
    }
  }
}
```

---

## 四、边界设计(重点——Agent 面试最值钱考点)

### 1. 操作权限分级

| 级别 | 示例 | 策略 |
|------|------|------|
| 🔵 只读 | 检索/比价/查库存/查订单 | 登录即可,AI 自动执行 |
| 🟡 写操作 | 加购/改订单 | **AI 只"建议",用户点确认才执行**(human-in-the-loop) |
| 🔴 高敏 | 退款/改价/发券 | 角色限制 + 双重确认 + 审计,或**不给 AI 该工具** |

> 电商涉及钱,与 IoT 不同:IoT 调参可自动(有 AuditLog);电商写操作必须"AI 提议 → 用户拍板"。

### 2. 工具级边界(代码层强制)

```
① 工具白名单:暴露哪些是设计决策,最小权限
② 参数 JSON Schema 约束 + 服务端二次校验(复用 IoT 7 参数白名单思路)
③ 轮数上限:ReAct 最多 3 轮,防循环失控
④ 预算复用:每轮 record 进 TokenBudgetService(2 元/天)
⑤ Sentinel 限流:工具调用接口防刷
```

### 3. 行为级边界

```
⑥ 结果真实性:生成只能基于工具返回,禁止编造(幻觉防护)
⑦ 角色映射:user/admin 工具集不同
⑧ 审计:每个动作落库(谁/何时/调了什么/结果)——IoT 已有 DecisionLog/AuditLog 先例可复制
⑨ 降级兜底:工具失败 → 回退纯 RAG 回答(与 TODO #31 向量降级同一思想)
```

---

## 五、分阶段实施计划

### 🔴 P0 最小可行 Function Calling —— 约 0.5~1 天

目标:让 LLM 拥有**第一个工具选择权**("要不要检索"由 AI 决定)。

1. **DeepSeekAiClient 改造**:chatCompletion 支持 `tools` 参数 + 解析响应 `tool_calls`(手写,无需新依赖——OpenAI 兼容协议)
2. **定义 ToolSpec**:工具声明(name/description/parameters JSON Schema)
3. **实现工具 1 `search_products`**:内部复用 `RagServiceImpl.intentSearch` / `SearchServiceImpl`
4. **ChatServiceImpl 改造**:请求带 tools → 响应含 tool_calls → 执行工具 → 结果拼进 messages → 再调一次 → 流式回复
5. **验证**:curl/Postman 测"推荐 5000 以内的手机"→ 观察是否触发工具调用 + 结果回填正确

改动文件:DeepSeekAiClient.java、ChatServiceImpl.java、新增 ToolRegistry.java(或 AiTool 接口)

### 🟠 P1 完整单 Agent —— 约 2~3 天

1. **工具集扩展**:`compare_products`(复用 ProductCompareServiceImpl)、`get_stock`(Dubbo 调 mall-product/mall-seckill)
2. **ReAct 循环**:max 3 轮,循环执行 tool_calls,记录每轮动作
3. **动作审计**:⚠️ 原写法（先 Redis 后 DB + Flyway）**已作废** —— `mall-ai` 无数据库栈，见校正① → 改为 **Redis List**：`ai:agent:action:{sessionId}` + `LTRIM 0 49` + `EXPIRE 7d`（DB 化降为可选 P2）
4. **降级兜底**:工具失败 → 回退纯 RAG
5. **边界落地**:只读工具登录可用;写操作本期**不做**或只"建议不执行"
6. **预算联动**:每轮调用 record 进 TokenBudgetService

### 🟡 P2 框架化(可选,更久)

- Spring AI Function Calling 重构(呼应 09 Q0:切换只动 mall-ai 内部,对外接口不变)
- 多 Agent 分工(导购/售后/比价)
- human-in-the-loop 确认流(写操作前端弹确认)

---

## 六、成本与风险

| 项 | 评估 |
|----|------|
| Token 成本 | 循环 = 多轮调用,费用翻倍;2 元/天预算下限轮数(max 3) |
| DeepSeek 支持 | ✅ 支持 tools 参数(function calling),OpenAI 兼容 |
| 风险 | 工具调用失败/超时 → 必须降级(否则搜索挂) |
| 演示风险 | 生产 0 调用,升级纯为面试演示,无真实流量验证 |

---

## 七、面试价值

> "我评估过把 AI 导购升级成 Agent:当前是'LLM 解析意图 + 代码写死执行',差距在工具声明、动作决策、多步循环。DeepSeek 支持 function calling,现有 intentSearch 直接声明成第一个工具——P0 半天让 AI 自己决定'要不要检索';P1 加对比/库存工具 + 3 轮 ReAct 循环 = 完整单 Agent。**边界是重点**:只读自动、写操作用户确认、参数 schema 约束、轮数上限、预算复用、动作审计、失败降级——AI 越自由越要设边界。升级定位是演示增强,业务收益为零(无真实用户)"

---

## 八、执行清单（2026-09-10 敲定版 · 分 P0 / P1）

### P0 最小 Function Calling（~0.5~1 天）—— ✅ **代码已完成（2026-09-10），待部署**

- [x] **S1 配置**：`AiProperties` 加 `agentEnabled`（默认 `false`）+ `agentMaxRounds`（默认 3）；yml 加 `agent-enabled` / `agent-max-rounds`。**P0 补强**：改成**显式占位符** `${AI_AGENT_ENABLED:false}` + compose 注入 → **改 `.env` 即可开关，不用重编译 jar**；启动日志新增一行 `Agent 双路径开关：agent-enabled=…`（部署验证与回滚一眼可确认）
- [x] **S2 消息类型加宽**（校正②）：`AiClient` 加 `chat(List<Map<String,Object>>)`（Step 1 已落地，6 处调用点同步）
- [x] **S3 tools 支持**：`AiClient.chatWithTools(messages, tools)` —— 请求体 = `buildBody(AiTask.AGENT)` + `tools` + `tool_choice:auto`，**刻意不发 `response_format`**（实测二者互斥，同时下发模型就不再触发 `tool_calls`）；解析 `choices[0].message.tool_calls`，返回 **`AiToolRound(content, List<AiToolCall>, assistantMessage)`**（`assistantMessage` 为可直接回灌的消息：含 `tool_calls` 原样结构、**不含 `reasoning_content`**）；**复用** `checkBudget()` + `usage` 记账 + 并发闸门
- [x] **S4 工具注册**：`ToolRegistry`（`Map<String,AiTool>`，构造器注入 `List<AiTool>` 自动收集，**同名工具启动期直接抛异常**）+ `AiTool`（`name()` / `description()` / `parameters()` / `execute(Map)`）+ `AiToolResult(observation, hits)`；`search_products` 放 **`...ai.service.impl` 同包**（校正④）复用 `RagServiceImpl`；**模型给的参数一律不可信**：sortBy 白名单、负数预算丢弃、上下限颠倒自动交换、关键词/品牌截断；严格条件无命中时去掉价格/品牌兜底，并在 observation 里写明"已放宽"（免得模型以为这就是严格结果、继续误导用户）
- [x] **S5 Agent 分支**：`ChatServiceImpl.sendWithAgent(...)` —— 非流式跑 1~`agent-max-rounds` 轮 `tool_calls`，模型不再请求工具时那一轮正文即最终答案。**⚠️ 与原计划的偏差**：原写"最后一轮复用现有 `streamDeepSeek`"，实际 P0 **只覆盖同步 `/ai/chat/send`**，SSE（`/ai/chat/stream`）仍走旧流水线（流式 + 工具顺延 P1）；`agent-enabled=false` 时**完全不进**该分支（双路径、零行为变化）
- [x] **S6 边界**：轮数用尽 → **摘掉 tools 强制收口**（绝不把"超出轮数"抛给用户）；未知工具名 → 回灌一条可读观察后**继续循环**（不中断、不 500）；工具参数在代码层收敛（见 S4）
- [x] **S7 降级**：`chatWithTools` 异常 → catch 后**回退固定流水线 RAG**；工具自身失败返回 `AiToolResult.error(...)`（不抛异常打断循环，让模型决定改参数重试还是如实告知）
- [x] **离线测试（P0 补强，原计划未含）**：`ChatServiceImplAgentTest` 用**假 LLM 脚本化多轮**锁死 6 条路径 —— ①一轮工具+收敛（含**回灌内容断言**：assistant 的 `tool_calls` 与 `role=tool` 的 observation 必须真的出现在第 2 轮请求里）②轮数用尽强制收口 ③正文空兜底 ④未知工具 ⑤异常降级回流水线 ⑥开关关闭完全不碰 Agent。**为什么必须离线**：Agent 的正确性主要在"循环控制"，与真实模型无关；而真实调用不稳定、要花钱、跑得慢 → 拿它当回归测试等于没有测试
- [x] **P0 验证（2026-09-10 已完成，两阶段）**：
  **阶段 A（开关=false，验零回归）**：jar md5 `5193b258…` 落位、启动日志 `agent-enabled=false`、`/ai/search` 返回商品、`/ai/ask` 返回完整推荐、`content 为空`=0、真实 4xx=0
  **阶段 B（开 `AI_AGENT_ENABLED=true`）**：问"我想买 5000 以内的手机" →
  `工具 search_products：keywords=手机, price=null~5000.0（已放宽=false）→ 命中 4 条` → `Agent 第 1 轮：调用工具 1 次` → `Agent 第 2 轮收敛：命中商品 4 条，回答 413 字`，答案是带表格的 3 款推荐 + **主动排除误召回的台式机**（"联想天逸510S 不是手机，已帮你排除"）
  **兜底路径复验（AI 独立提问）**：问"有没有 1000 以内的单反相机" → `已放宽=true`（去掉价格过滤兜底生效）→ 回答**诚实说明"库里没有单反相机这一类目"**并给替代思路（拍照强的手机），**没有编造商品** ✓ 这正是 observation 里带 `note: 已放宽` 的设计目的
  预算 `ai:daily_cost:2026-09-10` 正常累加；21 容器全 Up；开关可随时改 `.env` 回滚

> 📋 **P0 实施记录（2026-09-10）**：提交 `09d22b1`；新增 6 个类（`AiToolCall` / `AiToolRound` / `AiTool` / `AiToolResult` / `ToolRegistry` / `SearchProductsTool`）+ 改 4 个类（`AiClient` / `DeepSeekAiClient` / `ChatServiceImpl` / `AiTask`）+ 3 个测试类；**mall-ai 模块 26 项测试全绿**；`agent-enabled` 默认 `false` → 开启前对生产零影响。部署指令：`work/部署指令-step2-agent-p0.md`（jar MD5 `5193b258…`，两阶段 A/B + 三级回滚）。

### P1 完整单 Agent（~2~3 天）

- [ ] **S8 工具扩展**：`compare_products`（复用 `ProductCompareServiceImpl`）、`get_stock`（Dubbo 调 `mall-product` / `mall-seckill` 既有接口）
- [ ] **S9 动作审计（Redis 版，校正①）**：`ai:agent:action:{sessionId}` List + `LTRIM 0 49` + `EXPIRE 7d`，记 `{ts, tool, args 摘要, 结果条数, 耗时, 轮次}`；**不做 DB / Flyway**
- [ ] **S10 思考可视化**：每轮工具调用发 SSE `thinking` 事件（前端 thinking 卡片已有，**无需改前端**）
- [ ] **S11 预算/闸门回归**：确认 3 轮循环下 `ai:daily_cost` 正常累加；闸门满时降级为 RAG 而非 500
- [ ] **P1 验证**：多轮对话触发多次工具调用；`redis-cli LRANGE ai:agent:action:<sid> 0 -1` 看到动作轨迹；**停 `mall-product` 后 Agent 工具失败 → 自动降级 RAG 仍能回答**
- [ ] **边界验收**：写操作不自动执行（本期不暴露写工具）/ 参数越界被拦 / 轮数超限停止 / 预算超限拒绝

### 部署与回滚

| 项 | 说明 |
|---|---|
| **改动面** | **只落在 `mall-ai`**：重建 jar → `docker compose up -d mall-ai`（1 个容器）；**不改表、不改 ES mapping、不动其他 10 个服务** |
| **开关回滚** | `agent-enabled: false` + 重建 `mall-ai` → 立刻回到现状（秒级生效，可先于代码回滚） |
| **代码回滚** | 本项改动集中，`git revert` 单次提交即可；**无数据迁移、无 schema 变更** |
| **监控要点** | Agent 开启后关注：`ai:daily_cost` 增速（循环放大最多 3 倍）、`AiBusyException`（闸门满）出现频率、工具执行异常日志 |

---

## 附录 A:与现有 RAG 的共存设计(双路径,2026-09-02 补充)

**不是两套系统,是"RAG 变成 Agent 的工具之一"——分层共存**:

```
对外接口(前端无感):/ai/ask、/ai/chat/send、/ai/chat/stream ← 全不变

                    ChatServiceImpl(对话中枢)
                    ┌───────────────────────────────┐
                    │ 双路径(关键:省 token)          │
    简单问题 ──→ ① 快速路径(现有):意图→RAG→生成      │
                    │   不调 LLM 决策 = 便宜           │
    复杂问题 ──→ ② Agent 路径(新增):LLM 决策          │
                    │   ├ search_products(内部=RAG)   │
                    │   ├ compare_products            │
                    │   ├ get_stock ...               │
                    │   └ 3 轮 ReAct → 生成           │
                    └───────────────────────────────┘
```

| 要点 | 说明 |
|------|------|
| ① RAG 降级为工具 | search_products 内部就是现有 RAG pipeline,不重复实现 |
| ② 双路径分流 | 简单问题走快速路径(不调 LLM 决策 = 省 token);复杂问题进 Agent——呼应 IoT 三段式"先便宜后智能" |
| ③ 配置开关 | agent-enabled(像 embedding-enabled),灰度切换 |
| ④ 降级链 | 工具失败 → 回退快速路径 RAG → 再失败纯 LLM 对话,永远有兜底 |

**代码形态**:ChatServiceImpl 保留现有快速路径 + 新增 agent 分支;Agent 的 search_products 工具直接注入现有 RagServiceImpl/SearchServiceImpl(复用,零重复)。

---

## 附录 B:商品数据补充方案(更贴近真实电商,2026-09-02 补充)

**现状**:ES 20 条真实品牌商品(小米/苹果/耐克/阿迪等),upload 目录 57 张图;同步机制已自动化(/ai/syncAll 接口 + sync-auto-on-startup: true 启动全量同步)——**缺的不是同步,是数据源**。

**结论**:非必需(20 条已够演示);想做时 30-50 条封顶,重点造"真实型号 + 高质量文本描述"(AI 检索的是文本,图片只影响前端观感)。

**批量方案(按推荐排序)**:

| 方案 | 做法 | 优点 | 缺点 |
|------|------|------|------|
| ① 调后台接口(推荐) | 脚本循环调 AdminSpuController 添加接口(addNew) | 走完整业务链路(自动同步 ES/缓存/校验) | 要写脚本 |
| ② SQL 批量插 | INSERT pms_spu/pms_sku | 快 | 绕过业务,要手动触发同步 |
| ③ 生成脚本 | Python 生成真实型号 JSON(手机/笔记本/鞋服)→ 喂给方案① | 一次 50 条真实感 | 要整理型号清单 |
| ④ 爬虫抓电商 | ❌ 不建议 | — | 版权/法律风险 |

**图片来源**:复用 /data/csmall-upload/(57 张)/ 品牌官网素材(少量自用)/ 免费占位图(开发用);检索主要吃文本,图片次要。

**工作量**:脚本方案 0.5~1 天(含型号清单);同步零成本(syncAll 已有)。
