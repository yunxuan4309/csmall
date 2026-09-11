# AI 导购 Agent 实现详解（Function Calling + ReAct · 手写薄编排）

> **创建日期**: 2026-09-10
> **状态**: ✅ **P0 + P1 已实现并部署生产**（生产开关 `AI_AGENT_ENABLED=true`）；**56 项离线测试全绿**
> **定位**: 本文是**实现说明书**（架构 / 每个类 / 每处取舍 / 逐条证据 / 排障手册 / 面试底稿）。
> 与它分工的三份文档：[[AI导购Agent升级方案]]（**当初的计划**与清单）、[[TODO第三批实现与原理-1]]（**选型过程** + 13 格实测实验 + 小插曲）、[[问题解决--服务注册与网关路由]]（本次抢修复盘）。
> 🧩 **已提炼（2026-09-11）**：本文件的「§六 分层降级 + §七 可观测与三步排障法 + §10.3 生产踩坑 4 条 + §4.3 参数不可信」已提炼为「一类问题」文档 → **[[问题解决--LLM链路的契约漂移与分层降级]]**（**原文件保留不动**，仅互链；本篇仍是完整实现说明书/排障底稿）
> **一句话**: **不引框架，用 OpenAI 兼容的 Function Calling 手写"一个循环 + 一份工具清单"**，把项目已有的会话/预算/闸门/SSE/检索/降级全部复用。为什么不上 Spring AI → 见 [[TODO第三批实现与原理-1]] §一（结论：卡 Boot 代际，本项目 3.2.5 低于其支持下限）。

---

## 〇、一页速览

```
用户 → nginx → 网关(/ai/**) → mall-ai
                                │
        ┌───────────────────────┴────────────────────────┐
        │ ChatServiceImpl（对话中枢）                      │
        │   agent-enabled? ── 否 ──► 固定流水线（旧行为）   │
        │        │ 是                                     │
        │        └─► Agent 循环（同步 / 流式两种）          │
        │              ├ ToolRegistry（工具清单→JSON Schema）│
        │              ├ AiTool.execute（参数收敛 + 取数）   │
        │              └ AgentActionAuditor（Redis 审计）   │
        └───────────────────────┬────────────────────────┘
                                │
                        AiClient（接口）
                                │
                    DeepSeekAiClient（OpenAI 兼容 HTTP）
                     chat / chatJson / streamChat
                     chatWithTools / streamChatWithTools
```

| 关键数字 | 值 |
|---|---|
| 工具 | **3 个**（`search_products` ES / `compare_products` Dubbo / `get_stock` Dubbo），**全部只读** |
| 轮数 | `agent-max-rounds` 默认 **3**（含收敛轮）；用尽则**摘掉工具强制收口** |
| 新增代码 | 客户端 ~180 行 + 工具 3 个 ~380 行 + 循环/流式/降级 ~230 行 + 审计 ~70 行 |
| 测试 | **56 项**离线测试（假 LLM 脚本化多轮 + Proxy 假 Dubbo），零成本可重复 |
| 实测 | **13 格**直连 DeepSeek 契约实验（A~M）+ P0/P1 两轮生产验证 |
| 成本 | 每次对话 ≈ (工具轮数 + 1) 次 LLM 调用 → 约为旧路径的 **2~3 倍**（实测单次几分钱） |
| 事件顺序 | 与旧流水线**完全一致**（`thinking → products → sessionId → chunk* → done`）→ **前端零改动** |

**文件清单（全部在 `mall-ai/mall-ai-webapi`）**

| 文件 | 职责 |
|---|---|
| `client/AiClient.java` | LLM 客户端接口（5 种调用） |
| `client/DeepSeekAiClient.java` | 实现：请求体唯一构造点、SSE 公共管道、工具轮（同步/流式） |
| `client/AiToolCall.java` / `client/AiToolRound.java` | 工具调用元素（`arguments` 二次解析）/ 一轮响应（含可回灌 assistant 消息） |
| `config/AiTask.java` / `config/AiProperties.java` | 任务枚举（含 `AGENT`）/ 档位层 + 任务层 + Agent 开关 |
| `service/impl/AiTool.java` / `AiToolResult.java` / `ToolRegistry.java` | 工具契约 / 工具结果（observation + hits）/ 注册表 |
| `service/impl/SearchProductsTool.java` / `CompareProductsTool.java` / `GetStockTool.java` | 三个工具 |
| `service/AgentActionAuditor.java` | Redis 动作审计 |
| `service/impl/ChatServiceImpl.java` | 双路径 + Agent 循环（同步/流式）+ 分层降级 |

---

## 一、整体架构

### 1.1 入口矩阵：谁受 `agent-enabled` 影响

| 接口 | 做什么 | 受 Agent 开关影响？ |
|---|---|---|
| `POST /ai/chat/send` | 多轮对话（同步） | ✅ 是（Agent 同步循环） |
| `POST /ai/chat/stream` | 多轮对话（SSE 流式） | ✅ 是（流式 Agent，**P1 新增**） |
| `POST /ai/chat/stream-sse` | 兼容旧 SSE（`SseEmitter`） | ❌ 否（仍走旧流水线） |
| `POST /ai/ask` | RAG 问答（问答 + 相关商品） | ❌ 否（一直走 `RagServiceImpl.ask`） |
| `POST /ai/search` | 商品搜索（含 AI 重排） | ❌ 否 |
| `POST /ai/compare` | 商品对比接口 | ❌ 否（`ProductCompareServiceImpl`） |
| `GET /ai/product/{spuId}/related` | 相关推荐 | ❌ 否 |

> ⚠️ **关掉开关 ≠ 删掉 Agent**：代码、3 个工具 Bean、审计组件**全部照常注册**（启动日志仍打印"已注册 AI 工具 3 个"），只是两个对话入口切回旧流水线。这是**生产安全绳**：出问题改一个环境变量 + recreate 容器（~45s）即可回到升级前行为，**无需换 jar**。

### 1.2 分层职责

| 层 | 内容 | 设计要点 |
|---|---|---|
| **入口层** | `AiController`（鉴权 + 限流） | 每个 AI 接口都过 `AiUserRateLimiter.checkRate`（默认 60s/10 次） |
| **中枢层** | `ChatServiceImpl` | 唯一决定"走哪条路径"的地方；双路径共用会话、偏好、保存 |
| **客户端层** | `AiClient` / `DeepSeekAiClient` | 模型/思考/温度/max_tokens **只在 `buildBody` 一处**；预算、闸门、记账都在这一层 |
| **配置层** | `cooxiao.ai.models`（档位）+ `cooxiao.ai.tasks`（任务） | Java 里**不出现模型名**；换模型只改 `.env` |
| **设施层** | `TokenBudgetService` / `AiConcurrencyGuard` / `SessionManager` / `PreferenceExtractor` / `AgentActionAuditor` | 预算（2 元/日）、并发闸门（20）、会话（Redis 24h）、偏好、审计 |
| **数据层** | Elasticsearch（商品索引）· Dubbo → mall-product（SPU/SKU 真实数据）· Redis | **检索走 ES（模糊找），事务数据走 Dubbo（准确）** |

---

## 二、LLM 客户端设计

### 2.1 五种调用（`AiClient`）

| 方法 | 用途 | 任务档 | 关键参数 |
|---|---|---|---|
| `chat(system, user)` / `chat(messages)` | 普通对话 | `CHAT` | `thinking: enabled`，**不下发 temperature**（思考模式下不生效） |
| `chatJson(system, user)` | 结构化输出（意图提取/重排/偏好） | `JSON` | `thinking: disabled` + `response_format: json_object` + temperature |
| `chat(AiTask, ...)` | 指定任务的通用入口 | 任意 | 由 `tasks.<task>` 决定 |
| `streamChat(messages, onChunk)` | SSE 流式对话 | `CHAT` | `stream: true` + `stream_options.include_usage`（否则无法记账） |
| `chatWithTools(messages, tools[, toolChoice])` | **工具轮（非流式）** | `AGENT` | `tools` + `tool_choice` + `thinking: disabled`，**绝不带 `response_format`** |
| `streamChatWithTools(messages, tools[, toolChoice], onChunk)` | **工具轮（流式）** | `AGENT` | 同上 + `stream: true`；正文实时回调，`tool_calls` 按 index 拼接 |

### 2.2 请求体唯一构造点 `buildBody(task, messages)`

```
model       ← aiProperties.modelFor(task)        // 档位解析，Java 无模型名
messages    ← 调用方传入
max_tokens  ← tasks.<task>.max-tokens ?? 全局
thinking    ← {"type": "enabled"|"disabled"}     // 官方机制，取代"换模型名"
reasoning_effort ← 仅 thinking=enabled 且配置了才下发
temperature ← ★只有 thinking=disabled 才下发（思考模式下官方不生效，下发会造成"调了温度"的假象）
response_format ← ★只有 AiTask.JSON 才下发
tools / tool_choice ← 只有工具轮由 buildToolBody 追加（"auto" / "required"）
```

**为什么强调"唯一构造点"**：历史上 SSE 路径曾**另写一份请求体**，导致"改模型要改两处"。#58 把它收敛成一处，Agent 的工具轮也直接复用它 —— **所以工具轮天然不会带上 `response_format`**（这正是必需的，见 2.4）。

### 2.3 任务层 + 档位层（模型可配化）

```yaml
cooxiao.ai:
  models: { flash: ${AI_MODEL_FLASH:deepseek-v4-flash}, pro: ${AI_MODEL_PRO:deepseek-v4-pro} }
  tasks:
    chat:    { tier: flash, thinking: true,  max-tokens: 3000 }
    json:    { tier: flash, thinking: false, temperature: 0.3, max-tokens: 3000 }
    expand:  { tier: flash, thinking: false, temperature: 0.3 }     # 查询扩展输出纯文本
    agent:   { tier: flash, thinking: false, max-tokens: 1000 }     # ★ 工具轮：必须关思考
    compare: { tier: flash, thinking: true }
```

- **改名/换代/下架** → 改 `.env` 的模型变量 + 重建 1 个容器，**不重编译**。
- ⚠️ **必须用显式占位符**：`cooxiao.ai.chat-model` 对应的环境变量是 `COOXIAO_AI_CHATMODEL`（去横线），指望 relaxed binding 隐式映射很容易踩空。

### 2.4 ⭐ 三条模型契约（全部来自实测，不是猜的）

| # | 契约 | 实测 | 代码里的落实 |
|---|---|---|---|
| 1 | **工具轮必须关思考** | `thinking: enabled` + `tools` + 不带 `reasoning_content` → **HTTP 400** `The reasoning_content in the thinking mode must be passed back to the API.`；`disabled` → 200 | `tasks.agent.thinking=false`；回灌的 assistant 消息**刻意不带** `reasoning_content` |
| 2 | **`tools` 与 `response_format` 互斥** | 二者同时下发 → 200 但 **`tool_calls` 为空**（模型只输出 JSON，不再调工具） | 工具轮走 `AiTask.AGENT`，`buildBody` 只在 `JSON` 任务加 `response_format` → **天然互斥**；单测锁死 |
| 3 | **思考模式下 `temperature` 不生效** | 设了不报错，但无效果 | `buildBody` 只在 `thinking: disabled` 时下发 |

### 2.5 SSE 公共管道 `openSseStream(body, onData)`

两条流式路径（`streamChat` 与 `streamChatWithTools`）**共用**取流/断连/记账/容错：

```java
body.put("stream", true);  body.put("stream_options", {"include_usage": true});   // 否则无法记账
HttpURLConnection conn = ...  // connectTimeout 10s / readTimeout = cooxiao.ai.timeout
读行 → 只看 "data: " 前缀 → JSON.parse(line.substring(6))
   ├ usage 分片 → recordUsage()（预算记账）
   └ 其余 → onData.accept(data)     // 单个分片解析失败 catch 掉，不中断整条流
```

**实测依据（实验 J）**：思考模式下 SSE 里 reasoning 分片数是 content 的 **2.3 倍**，但现有解析器只读 `delta.content` 与 `usage` → **reasoning 分片被安全忽略**，改造无破坏。

### 2.6 流式工具轮的解析（P1 的核心难点）

**实测（实验 K）**：流式 + tools 时，模型**先吐一段 preamble 正文**（13 个 content 分片），然后 `tool_calls` 以 **20 个分片**到达，`arguments` 被切成 **19 段**（`""`→`{`→`"`→`keywords`→`"`→…→`}`），**首片才带 `id`/`name`**。

```java
Map<Integer,String> ids, names;  Map<Integer,StringBuilder> args;     // 按 index 累积
delta.content           → 累积 + 实时回调 onContentChunk
delta.tool_calls[i]     → tc.id != null ? ids.put(index, id)          // 只在首片
                          fn.name != null ? names.put(index, name)
                          args.computeIfAbsent(index,...).append(fn.arguments)   // ★ 必须拼接
结束后按 index 升序组装 → AiToolCall(id, name, argsJson) + assistantMessage(content, calls)
```

⚠️ **不拼接 = 拿到半截 JSON**（"无中生有的 bug"），这是本项最容易写错的地方。

---

## 三、Agent 循环设计

### 3.1 同步循环（`/ai/chat/send`）

```java
messages = [system(Agent 提示词 + 用户偏好), ...最近 8 条历史, user]
firstToolChoice = forceFirstTool(message) ??       // 商品类问题 → "required"
for (round = 1; round <= maxRounds; round++) {
    r = chatWithTools(messages, tools, round==1 ? firstToolChoice : null)
    if (!r.hasToolCalls()) {                       // ← 收敛信号
        reply = r.content().isBlank() ? finalAnswerWithoutTools(messages) : r.content()
        return buildResult(reply, hits)
    }
    messages += r.assistantMessage()               // ★ 含 tool_calls，必须原样回灌
    for (tc : r.toolCalls()) {
        result = executeToolCall(session, round, tc)   // 计时 + 审计 + 兜异常
        hits = result.hits().isEmpty() ? hits : result.hits()
        messages += { role:"tool", tool_call_id: tc.id, content: result.observation() }
    }
}
return buildResult(finalAnswerWithoutTools(messages), hits)   // ⛔ 轮数用尽：摘掉工具强制收口
```

### 3.2 流式循环（`/ai/chat/stream`）—— 事件顺序是硬约束

**旧前端契约**：`thinking → products → sessionId → chunk* → done`。Agent 必须**不改前端**也满足它，难点在于 **`tools` 轮会先吐 preamble 正文**（实验 K），而那时商品列表还没拿到。

**解法：products 之前先缓冲正文，之后实时转发**

```java
boolean productsSent = false;
for (round...) {
    List<String> buffered = new ArrayList<>();
    r = streamChatWithTools(messages, tools, choice, chunk -> {
        if (productsSent) { writeSSE("chunk", chunk); reply += chunk; }   // 实时
        else               buffered.add(chunk);                          // 先攒着
    });
    if (!r.hasToolCalls()) { emitProductsIfNeeded(); flush(buffered); finish(); return true; }
    messages += r.assistantMessage();
    for (tc : r.toolCalls()) { result = executeToolCall(...); writeSSE("thinking", describeTool(...)); messages += toolMessage(...); }
    emitProductsAndSession(hits);      // ★ 此刻才发 products / sessionId / "正在生成回答"
    productsSent = true;
    flush(buffered);                   // 补发 products 之前攒下的正文（顺序就对了）
}
// 轮数用尽 → 摘掉工具、流式收口（streamChat）
```

**收益（真实收益，不是理论）**：常见路径是"1 轮工具 + 1 轮作答"，此时**作答正文是真正逐字流式的**，且事件顺序与旧流水线一致。生产实测：**249 个 chunk 分片**、`products`(第 7 行) 早于第一个 `chunk`(第 16 行) ✓。

**额外好处**：缓冲还顺带保证了**降级不拼接** —— 如果正文才吐了一半（还在缓冲里）就断流，缓冲被丢弃、完整走旧流水线；**一个字都没发给前端**，所以安全。

### 3.3 ⭐ 首轮强制调用工具（`tool_choice=required`）

**生产实测发现的问题**：模型遇到与历史相似的问题时，会**直接引用历史对话里的商品**作答 —— 内容也许没错，但**这一轮没有任何工具结果 → 前端商品卡片为空**。

**解法**：命中商品/购买意图关键词时，**首轮**下发 `tool_choice: "required"` 强制检索一次；后续轮次回到 `auto` 让模型自己收敛。

- **实测（实验 M）**：DeepSeek **支持** `tool_choice: "required"` → 返回 `finish_reason=tool_calls`。
- **保守关键词表**：`买 / 推荐 / 找 / 搜 / 有没有 / 有货 / 库存 / 多少钱 / 价格 / 预算 / 便宜 / 贵 / 对比 / 比较 / 哪个 / 选 / 适合`。
- **可关闭**：`cooxiao.ai.agent-force-first-tool`（默认 `true`）。
- 为什么不"每轮都 required"：那样连"你好"都会触发检索（实测 M 里它真的拿"你好"去搜了），既浪费又傻。

### 3.4 消息栈的形状（回灌协议）

| 角色 | 内容 | 注意 |
|---|---|---|
| `system` | Agent 提示词 + 用户偏好 | **7 条规则**：必须用工具、一次一组条件、`count=0` 可放宽、不得编造、够了就答、**即使与历史相似也要重新核对**、**没有工具数据不要给具体商品/价格** |
| `user` | 本轮问题 | — |
| `assistant` | `{content, tool_calls:[{id,type,function:{name,arguments}}]}` | **必须原样入栈**；⚠️ **不带 `reasoning_content`**（工具轮关思考，本就没有；若开思考则必须回传否则 400） |
| `tool` | `{tool_call_id, content: observation}` | `tool_call_id` 必须与 assistant 的 id 一致 |

**历史只回放 `role` + `content`**（最近 8 条）：工具过程不跨轮持久化 —— 上一步的 observation 没有必要在下一步重放（那会让上下文线性膨胀）。

---

## 四、工具层设计

### 4.1 契约与注册表

```java
public interface AiTool {
    String name();                               // 模型按名字调用
    String description();                        // ★ 决定"模型会不会用、用得对不对"
    Map<String,Object> parameters();             // JSON Schema
    AiToolResult execute(Map<String,Object> args);   // 失败请返回 error(...)，别抛异常
}
```

`ToolRegistry`：构造器注入 `List<AiTool>` **自动收集**（新增工具**不必改注册表**）；**同名工具在启动期直接抛异常** —— 否则会变成"模型点了 A、执行的却是 B"的静默故障。

`AiToolResult(observation, hits)` 把两条数据流分开：`observation` 给模型看；`hits` 是原始商品文档，**只给 Java 侧转 `RelatedProductVO` 出前端卡片**。

### 4.2 三个工具

| 工具 | 数据源 | 参数 | observation 关键点 | hits |
|---|---|---|---|---|
| `search_products` | **ES**（`intentSearch`，IK 分词 + 价格/品牌过滤 + 排序） | `keywords*` `budgetMin` `budgetMax` `brand` `category` `sortBy` | 紧凑 JSON（同信息 token 约为散文 1/3）；严格条件无命中时**去掉价格/品牌兜一次**并标注 `note: 已放宽` | ✅ ES 原始文档 |
| `compare_products` | **Dubbo** `IForFrontSpuService.getSpuById` | `spuIds*`（2~3 个） | 并排事实：价格/品牌/分类/销量/库存/标签/标题；缺失的 id 记进 `missingSpuIds` | ✅ SPU 映射成 ES 文档形状（**对比也能出卡片**） |
| `get_stock` | **Dubbo** `IForFrontSkuService.getSkusBySpuId` | `spuId*` | SKU 级库存 + 总库存 + **`spuName`（自报家门）** + `note: 只含常规库存` | ❌ 不出卡片 |

**⭐ 为什么 `compare_products` 不复用现成的 `ProductCompareServiceImpl.compare(...)`**：那个方法内部会**再调一次 LLM** 生成对比总结 → ① 一次工具调用变成两次计费；② Agent 循环本就持有并发闸门，嵌套调用多占一个槽；③ **工具应该只提供事实，"判断"留给 Agent 的收敛轮**。这是刻意的取舍，不是漏用。

**⭐ 为什么 `get_stock` 必须带 `spuName`（生产踩坑后的修复）**：原先只回 SKU 数据，模型**先猜了个 spuId 去查，拿到数据后仍按提问里的商品名作答** —— 幸好它随后检索修正了，但**风险真实存在**。现在工具先 `getSpuById` 确认并回传 `spuName`，模型就无从张冠李戴。

### 4.3 ⭐ 参数一律不可信（模型给的参数是"自由文本"，不是可信输入）

| 参数 | 模型可能的写法 | 收敛策略 |
|---|---|---|
| `budgetMin/budgetMax` | 上下限颠倒；负数；字符串 `"5000"` | 颠倒→**自动交换**；负数→丢弃；字符串→解析 |
| `sortBy` | `"price_asc"` / `"DROP TABLE"` | **白名单**（`sales`/`price_asc`/`price_desc`），白名单外**直接忽略、绝不拼进 ES 查询** |
| `keywords/category/brand` | 超长文本（几百字） | **截断**（100 / 50 / 50） |
| `spuId` / `spuIds` | 猜的 id、负数、重复、超过 3 个、字符串 | 正整数校验 / **去重** / **封顶 3** / SPU 不存在→明确报错 |
| 未知工具名 | 幻觉出来的工具 | 回灌"未知工具 X"，**循环继续**（不 500） |

### 4.4 observation 设计原则

1. **紧凑 JSON**（不是散文）：Agent 每一轮都要把历史全带上，省 token 是复利。
2. **自报家门**：结果里必须包含"这是什么"（商品名/spuId），否则模型会把 A 的数据说成 B。
3. **如实标注加工**：放宽了条件就写 `note: 已放宽` —— 生产实测证明这能让模型**如实说"库里没有这类目"，而不是顺着放宽结果硬推荐**。
4. **给模型的 ≠ 给前端的**：observation 给模型，hits 给前端（`AiToolResult` 两个字段）。

---

## 五、边界与安全（Agent 最值钱的考点）

| 边界 | 做法 | 依据/理由 |
|---|---|---|
| **工具权限分级** | 本期**只暴露 3 个只读工具**；写操作（加购/下单/退款）**一个都不给** | 不给 AI 犯错的机会，比事后审计便宜 |
| **参数越界** | 全部在工具内收敛（见 4.3），**不信任模型** | 模型输出是自然语言，不是可信输入 |
| **轮数上限** | `agent-max-rounds=3`；用尽**摘掉 tools 强制收口** | 防循环失控 + 成本失控；收口时不把"超出轮数"抛给用户 |
| **预算** | 每次调用前 `checkBudget()`（2 元/日），每次响应后按 `usage` 记账 | 复用既有设施；Agent 是"多次调用"，记账必须每轮都做 |
| **并发闸门** | `AiConcurrencyGuard`（上限 20，满即失败/降级） | ⚠️ 闸门 acquire/release 在**客户端方法内成对**，**执行工具期间不持有闸门** → 不存在嵌套占用 |
| **限流** | Sentinel 三组规则 + `AiUserRateLimiter`（60s/10 次/用户） | 复用既有设施 |
| **结果真实性** | system 提示词 + "只把工具结果拼进上下文" | 让"编造"没有素材 |
| **动作审计** | 每次工具调用落 Redis（见 §七） | ⚠️ mall-ai **无数据库栈** → 只能 Redis；审计"尽力而为" |
| **失败降级** | 三层（见 §六） | 永远有兜底 |

---

## 六、降级与容错（分层，触发条件 → 用户可见行为）

| 层 | 触发 | 行为 | 用户看到 |
|---|---|---|---|
| **① 工具级** | 工具抛异常 / 未知工具名 / 参数非法 | 转成可读 observation 回灌，**循环继续** | 正常回答（模型换个方式或如实说） |
| **② 未写出内容就失败** | 首轮 LLM 调用就异常（同步：`chatWithTools`；流式：尚未写出任何 chunk） | **降级到旧固定流水线** | 与升级前一致的答案 + 商品卡片 |
| **③ 已写出内容后失败** | 流式回合中途断流（已经发过 chunk） | **如实报错收尾，绝不降级** | `event: error` + `event: done`（**不会出现两段回答拼接**） |
| **④ 轮数用尽** | 模型一直在调工具 | 摘掉 tools，追加"请立即作答"再问一次 | 正常回答（模型基于已有工具结果） |
| **⑤ 预算超限** | `ai:daily_cost` 超 2 元 | 入口直接拒绝 | "服务繁忙，请稍后再试。" |
| **⑥ 空答复** | 收敛轮正文为空 | 再问一次；仍空 → 降级话术 | 降级文案（不返回空） |

> 💡 流式缓冲（§3.2）与"③ 不降级"是配套设计：**"已经写给用户的东西不能装没发生"**，这是流式场景特有的纪律。

---

## 七、可观测性

### 7.1 日志关键行（排障就靠这几条）

```
AI 模型路由（cooxiao.ai）：chat[flash→deepseek-v4-flash,thinking=on] json[...] agent[flash→…,thinking=off] compare[...]
Agent 双路径开关：agent-enabled=true（false=固定流水线 / true=Function Calling），agent-max-rounds=3
已注册 AI 工具 3 个: [compare_products, get_stock, search_products]
Agent 首轮强制调用工具（命中商品意图关键词「买」）
工具 search_products：keywords=手机, brand=null, price=null~5000.0（已放宽=false）→ 命中 4 条
工具 compare_products：请求 2 个，取到 2 个（缺失 0）
工具 get_stock：spuId=3（小米 14 Pro）→ 2 个 SKU，总库存 100
Agent 第 1 轮：调用工具 1 次，累计命中 4 条
Agent 第 2 轮收敛：命中商品 4 条，回答 413 字
Agent(流式) 第 1 轮：调用工具 1 次，累计命中 4 条
流式 Agent 失败：xxx        ← 后面会跟降级或报错收尾
Agent 流程异常，降级为固定流水线：xxx
```

### 7.2 SSE 事件契约（前端依赖，**不可随意改顺序**）

| 事件 | 数据 | 时机 |
|---|---|---|
| `thinking` | 文案（`🤖 正在理解…` / `🔧 第 N 轮…` / `🔎 商品检索完成：拿到 N 条数据` / `💬 AI 正在生成回答…`） | 全程 |
| `products` | `RelatedProductVO` JSON 数组（**可能是空数组**） | **第一个 `chunk` 之前** |
| `sessionId` | 会话 id | `products` 之后、`chunk` 之前 |
| `chunk` | 正文分片 | 收敛轮逐字 |
| `done` | 空 | 最后 |
| `error` | 文案 | 出错时 |

> ⚠️ **Agent 路径不发 `categories` 事件**（旧流水线在"0 结果"时会推可用分类引导）。Agent 由模型自己决定是否换条件重查，引导方式不同，前端对缺省事件已能容忍。

### 7.3 Redis 键

| 键 | 类型 | 内容 | TTL |
|---|---|---|---|
| `ai:chat:session:{sid}` | String(JSON) | 会话（消息 + 偏好，最多 30 条） | 24h |
| `ai:chat:user:{uid}` | String | 用户最后活跃会话（前端不传 sid 时自动恢复） | 24h |
| `ai:daily_cost:{yyyy-MM-dd}` | String | 当日累计成本（元） | 到次日零点 |
| `ai:user:rate:{uid}:{window}` | String | 用户频控计数 | 窗口级 |
| **`ai:agent:action:{sid}`** | **List** | **动作审计，最新在前，最多 50 条** | **7d** |

**审计记录字段**：`{ts, round, tool, args(截断 200), hits, costMs, ok}` —— 用 `LRANGE ai:agent:action:{sid} 0 -1` 即可回放"AI 到底查了什么"。

### 7.4 三步排障法

1. **看启动日志**：`Agent 双路径开关` + `已注册 AI 工具 N 个` → 确认开关与工具是否就位。
2. **看工具轨迹**：`docker logs csmall-ai | grep "工具 "` → 模型到底调了没、命中多少条、有没有走放宽兜底。
3. **看审计明细**：`LRANGE ai:agent:action:{sid} 0 -1` → 每轮的参数、耗时、成功与否（`ok=false` 说明工具失败被兜住了）。

---

## 八、运维：开关、部署、回滚、成本

### 8.1 开关语义（重要）

| 项 | 说明 |
|---|---|
| 变量 | `AI_AGENT_ENABLED`（yml `cooxiao.ai.agent-enabled`，默认 `false`） |
| 读取时机 | **Spring 启动时**（`@ConfigurationProperties`）→ **不是请求级热切换** |
| 切换方式 | 改 `/data/csmall/.env` → `docker compose up -d mall-ai`（**不用重编译**，约 45s） |
| 关掉之后 | Agent 代码/工具/审计**仍在**，只是两个对话入口走旧流水线 |
| 想秒级热切换 | 需改造为"读 Redis 标志位（+本地短 TTL 缓存）"，或等项目 TODO #4 的 Nacos 配置中心迁移 |

### 8.2 部署（每次只动 mall-ai 一个容器）

```bash
scp 新 jar → /tmp/mall-ai-stepN.jar
md5sum 校验 → 备份 jar + `docker tag csmall-mall-ai:latest csmall-mall-ai:before-stepN`
cp 落位 → 改 .env 开关（阶段 A 关 / 阶段 B 开）
cd /data/csmall && docker compose build mall-ai && docker compose up -d mall-ai
# ⚠️ 等"启动完成"，不要只等固定秒数（见下方坑）
docker logs --tail 300 csmall-ai | grep "Started MallAiWebApiApplication"
```

> ⚠️ **实测踩坑（2026-09-10）**：`sleep 45` 后打请求 **503 `No servers available for service: mall-ai`** —— 因为这次启动耗时 **44.7s 启动 + 注册**（到 13:13:09 才完成），而请求在 13:13:02 就发出去了，正好卡在"实例已反注册、还没重新注册"的空窗。**正确做法：轮询日志里的 `Started MallAiWebApiApplication`，或 `grep` 到再等 5s**，不要靠固定睡眠。

### 8.3 三级回滚

| 级别 | 操作 | 生效 |
|---|---|---|
| ① 关开关（最快） | `.env` 改 `false` + `docker compose up -d mall-ai` | ~45s |
| ② 换回 jar + 镜像 | 恢复 backup jar + `docker tag ...before-stepN latest` + `up -d --no-build` | ~45s |
| ③ compose 也回滚 | 再恢复 compose 备份后执行 ② | ~45s |

### 8.4 成本模型

| 路径 | LLM 调用数 | 相对成本 |
|---|---|---|
| 旧固定流水线（`/ai/chat/send`） | 意图提取(JSON) + 1 次生成 = 2 次 | 1× |
| Agent（1 轮工具 + 收敛） | 工具轮 + 收敛轮 = 2 次 | ~2× |
| Agent（2 轮工具 + 收敛） | 3 次 | ~2.5~3× |
| 流式 Agent（1 轮工具 + 流式作答） | 2 次 | ~2× |

实测：多次往返测试后 `ai:daily_cost` ≈ **0.03 元**（单次几分钱）；预算上限 2 元/日 → 够用。

---

## 九、测试策略（离线、可重复、零成本）

### 9.1 为什么必须离线

Agent 的正确性主要在 **循环控制 / 事件顺序 / 降级策略**，这些**与真实模型无关**而只与我们自己的代码有关；而真实模型调用**不稳定、要花钱、跑得慢** → 拿它当回归测试等于没有测试。真实模型只用来做**契约实验**（13 格）与**上线验证**。

### 9.2 两个"假件"技巧

1. **假 LLM（脚本化多轮）**：`FakeAiClient` 按脚本逐轮返回 `AiToolRound`；可控制"吐一片再抛异常"（测降级边界）、拆两片正文（测分片拼接）、记录每轮的 `messages` 与 `tool_choice`（测回灌与强制首轮）。
2. **动态代理假 Dubbo**：`Proxy.newProxyInstance(IForFrontSpuService.class, ...)` —— 接口有 5 个方法时**只需实现被测的那一个**，接口新增方法也不会让测试莫名其妙编译不过。

> ⚠️ **没有用 Mockito**：其 `mockito-inline` 的 MockMaker 需要动态 attach（Windows 走命名管道），在本机受限环境下无法初始化。手写假件反而更直观 —— 这个"环境限制"本身也是 [[本地双实例锁验证报告-2026-09-09]] 里记录过的同一类问题。

### 9.3 56 项分布

| 测试类 | 项数 | 覆盖 |
|---|---|---|
| `ChatServiceImplAgentTest` | 19 | 同步：一轮工具+收敛 / 轮数用尽收口 / 空正文兜底 / 未知工具 / 异常降级 / 开关关闭 / **审计记录** / 工具异常仍继续<br>流式：**products 早于 chunk** / 分片拼接 / 无工具也发空 products / 缓冲未写出可安全降级 / 已写出后失败不降级 / 轮数用尽流式收口 / thinking 文案 / **首轮强制 required** |
| `DeepSeekAiClientRequestBodyTest` | 9 | 请求体规则：thinking 显式开关 / 温度只在非思考下发 / `response_format` 只给 JSON 任务 / **工具轮不带 `response_format`** / `tool_choice=required` 透传 / tools 为 null 下发空数组 / 模型 id 来自档位 / 解析 `tool_calls` / 解析收敛轮 |
| `CompareProductsToolTest` | 8 | 对比事实 / 去重封顶 3 / <2 个拒绝 / 非法 id 丢弃 / 字符串 id / 缺失 id 不致命 / 全缺失报错 / provider down 不炸 |
| `GetStockToolTest` | 8 | 汇总库存 / **spuName 自报家门** / SPU 不存在报错 / 字符串 id / 非法参数不落下游 / provider down / 无 SKU 提示 / 封顶 10 个但总量算全 |
| `SearchProductsToolTest` | 7 | 参数收敛（颠倒/负数/白名单/截断）/ 放宽兜底 + 告知 / 空关键词 / null 参数 |
| `ToolRegistryTest` | 2 | schema 形状 + 顺序 / **同名工具启动期报错** |
| `AiPropertiesBindingTest` | 3 | yml→Java 绑定 |

### 9.4 怎么加一个新工具（3 步）

1. 写一个 `@Component implements AiTool`（放 `...ai.service.impl` 包，方便复用包级私有的 `RagServiceImpl` 方法）。
2. 跑测试 —— `Test` 里加一个边界测试（参数非法/provider down/正常）。
3. **不用改注册表**：`ToolRegistry` 构造器注入 `List<AiTool>` 自动收集。

---

## 十、实测证据与踩坑记录

### 10.1 13 格契约实验（服务器直连 DeepSeek）

| # | 验证 | 结论 |
|---|---|---|
| A~J | 见 [[TODO第三批实现与原理-1]] §五 | 思考开关生效 / **400 规则复现** / tools 可触发 / **tools 与 response_format 互斥** / 两轮循环收敛 / SSE + 思考安全 |
| **K** | **流式 + tools** | `finish_reason=tool_calls`；**`arguments` 分 19 段**（必须拼接）；首片带 `id`/`name`；**同响应先 13 个 content 分片（preamble）再 tool_calls** |
| **L** | K 之后回灌 `role:"tool"` 继续流式 | `content` **112 分片**（逐字）；`tool_calls`=0；`finish_reason=stop`；usage 有 |
| **M** | `tool_choice:"required"` | **支持**：返回 `finish_reason=tool_calls`（即使输入是"你好"也会强制去搜 → 所以要按意图选择性使用） |

### 10.2 生产验证（两轮，两阶段）

| 阶段 | 证据 |
|---|---|
| **P0-A（开关关）** | jar md5 落位一致；`agent-enabled=false`；`/ai/search`、`/ai/ask` 正常；`content 为空`=0；真实 4xx=0 |
| **P0-B（开关开）** | 问"5000 以内的手机" → `工具 search_products…命中 4 条` → `Agent 第 1 轮` → **`Agent 第 2 轮收敛`**（413 字带表格推荐）|
| **P0 兜底** | 问"1000 以内的单反相机" → `已放宽=true` → **如实回答"库里没有这一类目"，不编造** |
| **P1 流式** | **249 个 chunk** 逐字流式；`products`(行 7) **早于**第一个 `chunk`(行 16) ✓；thinking 文案含"🔧 第 1 轮 / 💬 正在生成回答" |
| **P1 新工具** | `compare_products：请求 2 个，取到 2 个（缺失 0）` ✓（重跑 2/2 均 200）；`get_stock：spuId=3（小米 14 Pro）→ 2 个 SKU，总库存 100` ✓ |
| **P1 审计** | `LRANGE ai:agent:action:{sid} 0 -1` 有记录、TTL 7 天量级 ✓ |
| **生产细节** | 模型**主动剔除误召回**（ES 把"联想天逸510S 台式机"召回到手机结果，模型自己注明"它是台式机，已帮你排除"）—— 工具只给候选、判断交给模型 |
| **加固后复测** | ① 流式：`products`(行 10) 早于第一个 `chunk`(行 22)，**241 个 chunk**，**products 4 件、首项"小米 14 Pro"（不再是空数组）** ✓；② 日志 `Agent 首轮强制调用工具（命中商品意图关键词「买」）` ✓；③ `工具 get_stock：spuId=3（小米 14 Pro）→ 2 个 SKU，总库存 100`（**自报家门生效**）✓；④ 模型编造的 `spuId=1001` 被工具优雅拒绝（`查询SPU详情失败…数据不存在` → 审计 `ok=false`，循环继续）✓；⑤ **客户端连续 18 次请求（含流式并发）全部 200** ✓；⑥ 反向验证：`你好，你能做什么` **不触发**强制工具 ✓ |

### 10.3 生产踩坑（4 条，全部已定位/已修）

| # | 现象 | 根因 | 处置 |
|---|---|---|---|
| 1 | 部署后立刻打接口 **503 `No servers available`** | `sleep 45` 早于实际启动（44.7s 启动 + 注册）→ 落在"反注册↔重注册"空窗 | **改为轮询 `Started MallAiWebApiApplication`**；写进部署手册 |
| 2 | 偶发 **500**（`AccessDeniedException` + "响应已提交"） | ⭐ **已定性（3 次观测 + 3 组定量实验）**：① 失败请求到 mall-ai 时**没有 JWT 解析日志**（匿名）；② **经网关连打 8 次 + 直连 4 次 + 流式并发 6 次 = 18/18 客户端全 200**；③ **并发实验里客户端 6/6 全成功，日志却仍出现 2 次 `AccessDenied`** → 它是**内部 ERROR 派发的次生现象**：某请求先失败（响应已提交，**典型是 SSE 流**）→ Tomcat 转 `/error` → Spring Security 在 **ERROR 派发**上再跑一遍过滤器链，而 **JWT 过滤器是 `OncePerRequestFilter`（默认跳过 ERROR 派发）** → 匿名 → `AuthorizationFilter` 拒绝 `/error` → "响应已提交"噪声 | 登记 **#62**（P2）：候选修法 = **放行 `DispatcherType.ERROR`**（属 mall-common/Security 公共配置，需单独窗口 + 全服务回归）+ SSE 收尾容错；**与 #53 可能同源，建议同窗口** |
| 3 | Agent 回答正确但 **`products: []`**（前端没卡片） | 模型遇到与历史相似的问题时**直接引用历史商品作答**，本轮没调工具 → 没有 hits | ①system 提示词加规则 6/7；②**首轮 `tool_choice=required`**（实验 M） |
| 4 | 库存回答有**张冠李戴风险**（模型先猜 spuId 去查） | `get_stock` 的 observation **没带商品名** | 工具先 `getSpuById` 确认，observation 增加 **`spuName`**；SPU 不存在直接报错 |

---

## 十一、关键决策清单（含替代方案与依据）

| 决策 | 选了什么 | 替代方案 | 依据 |
|---|---|---|---|
| Agent 框架 | **手写薄编排** | Spring AI / LangChain4j | Spring AI 支持下限 Boot 3.4，本项目 3.2.5（全反应堆 13 模块继承根 pom）→ 见 [[TODO第三批实现与原理-1]] §一 |
| 工具轮思考模式 | **`thinking: disabled`** | 开思考 + 维护 `reasoning_content` 回传 | 实测 B：不带回传直接 **400**；工具选择是结构化决策，不需要长思考 |
| 流式工具解析 | **按 index 拼接连缀 `arguments`** | 假设一条分片即完整 JSON | 实测 K：arguments 被切 19 段 |
| 流式事件顺序 | **保持与旧流水线一致 + products 前缓冲正文** | 改前端适配新顺序 | 前端零改动；实测 K 的 preamble 会让正文先于商品到达 |
| 首轮工具 | **商品意图才 `required`** | 永远 auto / 永远 required | 永远 auto → 会凭历史作答（踩坑 3）；永远 required → "你好"也触发检索（实验 M） |
| `compare_products` 实现 | **只查库摆事实** | 复用 `ProductCompareServiceImpl.compare` | 后者内部再调 LLM → 双倍计费 + 多占闸门槽 + 违反"工具只给事实" |
| 审计存储 | **Redis List（LTRIM 50 / TTL 7d）** | MySQL 表 + Flyway | mall-ai **无数据库栈**；审计是短周期数据，写失败只记 WARN |
| 降级策略 | **分层**（未写出→流水线；已写出→报错不降级） | 统一降级 | 已写出的内容不能"装没发生"，否则两段回答拼接 |
| 工具数量 | **3 个只读** | 加写工具（加购/下单） | 不给 AI 犯错的机会；写操作需要 human-in-the-loop，本期不做 |

---

## 十二、面试话术

**3 分钟主线**

> "AI 导购这块我做了一次 Agent 化升级：从'LLM 解析意图 + 代码写死执行'，变成**模型自己决定调哪个工具、调几次、够了就作答**。技术上我用的是 **OpenAI 兼容的 Function Calling 手写薄编排**，没有上 Spring AI —— 不是不会用，是这个项目不该用：它支持下限是 Boot 3.4，我这个项目是 3.2.5，而 Boot 版本声明在**根 pom 的 parent**里、13 个模块全体继承，接框架就要连带升 Spring Cloud 和整套 starter，那是全站框架升级。而 Agent 的本体其实就是一个循环加一份工具清单，我这边**会话、预算、并发闸门、SSE、检索、降级全是现成的**。
>
> 落地分两期：P0 做同步 Agent，P1 把**流式也接进 Agent**、补了两个新工具（商品对比、真实库存，都走 Dubbo 取事务数据，检索走 ES）、加了 Redis 动作审计。**默认开关关闭，随时可以回滚**——出问题改一个环境变量重建一个容器就回到旧行为。
>
> 工程上有三件事我觉得最值钱：**一是契约靠实测**——我做了 13 格直连模型的实验，比如'工具轮必须关思考'（开思考不回传 reasoning 会 400）、'tools 与 response_format 互斥'、'流式下 `arguments` 是分段到达的必须拼接'；**二是边界不信任模型**——它给的预算、排序、id 全部在代码层收敛（白名单/去重/封顶/SPU 不存在就报错），写操作一个工具都不给；**三是降级分层**——没写出去的内容可以安全降级回旧流水线，已经流式写出去的就如实报错、绝不降级，避免两段回答拼接。
>
> 上线后我还抓到两个只有真实流量才会暴露的问题：模型遇到与历史相似的问题会**直接引用历史商品作答**（内容对但**没有工具结果 → 前端没有商品卡片**），我用'商品意图识别 + 首轮 `tool_choice=required`'强制它先查一次；还有库存工具原先不带商品名，模型会**先猜 spuId 去查、再按提问里的商品名作答**，有张冠李戴风险，我在 observation 里加了 `spuName` 自报家门。"

**追问应对**

| 追问 | 回答要点 |
|---|---|
| 为什么工具轮要关思考？ | 实测：开思考时不回传 `reasoning_content` 直接 400；而工具选择是结构化决策，不需要长思考；省 token 还稳定 |
| Agent 怎么防幻觉？ | 四道：system 规则（只能基于工具结果、没数据不给具体商品）、只把 observation 拼进上下文、参数服务端二次校验、**入库前不做写操作** |
| 怎么防无限循环？ | 轮数上限 + 用尽摘掉 tools 强制收口；并发闸门与预算每轮都拦 |
| 流式下工具调用怎么处理？ | 实测 `arguments` 按 index 分段到达 → 拼接；正文实时转发给前端，但**products 之前先缓冲**，保证事件顺序与旧前端契约一致 |
| 审计为什么用 Redis？ | mall-ai 无任何数据库栈（无 JDBC/MyBatis/Flyway），引持久化与"无状态服务"定位冲突；审计是"尽力而为"，写失败只记 WARN |
| 成本怎么控？ | 每次对话 = 工具轮数 + 1 次生成，约为旧路径 2~3 倍；日预算 2 元 + 每轮记账 + 闸门 20 + 用户频控 60s/10 次 |

---

## 十三、关联文档

- **计划与清单**：[[AI导购Agent升级方案]]（P0/P1 清单逐条勾选 + 偏差记录）
- **选型过程 + 13 格实验 + 小插曲**：[[TODO第三批实现与原理-1]]（§一 为何手写 / §二 模型可配化 / §三 Agent 设计 / §五 实验 / §八 小插曲）
- **模型契约与可配化**：[[AI模型名停用风险与thinking参数改造方案]]（#58：thinking 开关 + 档位/任务两层）
- **状态源**：[[TODO文件]] #32 / #60 / #61 / #62；已完成归档 [[TODO已完成]] §十六
- **本次抢修复盘**：[[问题解决--服务注册与网关路由]] 问题 2（nginx 静态上游 IP → 全站 502）
- **环境限制类踩坑**：[[本地双实例锁验证报告-2026-09-09]]（Mockito inline mock maker 无法初始化，与本项目"手写假件"的测试策略同源）
