# AI 模型名停用风险与 `thinking` 参数改造方案

> **状态**: ✅ **已实施 + 已部署生产并验证通过（2026-09-10）** —— 代码改造、隐患修复、上线、回归**全部完成**。
> **生产验证证据（2026-09-10）**: 「AI 模型路由」启动**只打 1 次**（修复前 2 次）；`/ai/search` 5s 返回 AI 重排说明；`/ai/ask` 4s 返回完整推荐；预算记账正常增长；`content 为空` / `reasoning` / 4xx 告警均为 **0**。**详见 §十一 实施与部署记录**。
> **历史决策（2026-09-10）**: ① 10 格实测实验**复现了 400 规则**并**验证了 `thinking: disabled` 生效**；② 用户确认**按推荐执行**（采纳方案 A；`doChat` 走**新增 `chatJson`** 变体；**删除** `compare-model` 死配置）；③ **与 [[AI导购Agent升级方案]]（#32）同窗口分两步做** —— 本方案 Step 1 已完成，下一步 #32-P0
> **用途**: 消除"**生产依赖一个已被官方公告停用的模型别名**"这一隐患，并把现行的"**靠提示词叫模型别思考**"换成**官方 `thinking` 开关**
> **关联**: [[TODO文件]] #58、[[TODO第二批实现与原理]] §5.4.5 #21（当初为何改成 `deepseek-chat`）、[[TODO第三批实现与原理]]、[[跨机集群实施执行清单-2026-09-09]]（`/models` 与 API 实测方法）
> **规则来源**: [DeepSeek 官方「思考模式」文档](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode)、[官方「模型 & 价格」](https://api-docs.deepseek.com/zh-cn/quick_start/pricing)、[官方更新日志](https://api-docs.deepseek.com/zh-cn/updates)

---

## 一、问题

### 1.1 生产实际生效值（2026-09-10 实测，最权威）

解包生产容器 `csmall-ai` 的 `/app/app.jar` → `BOOT-INF/classes/application.yml`：

```yaml
chat-model: deepseek-chat        # 第 26 行 —— 已公告停用的别名
compare-model: deepseek-v4-flash # 第 27 行 —— 零引用（死配置，见 §5.2）
embedding-model: BAAI/bge-m3     # 第 41 行（走硅基流动，非 DeepSeek）
```

`application-prod.yml` **未定义 `cooxiao.ai` 段** → 直接继承 → **生产确实在跑 `deepseek-chat`**。

**改动来源**：commit `c0d7209`（2026-09-08 13:08）
> `fix(ai): AI 重排偶发降级/超时根治 - reasoning 模型分工(JSON任务用 deepseek-chat)`
> diff：`- chat-model: deepseek-v4-flash` → `+ chat-model: deepseek-chat`

### 1.2 风险：这个名字**已被官方公告停用**，只是还在兼容期

[官方更新日志 2026-04-24](https://api-docs.deepseek.com/zh-cn/updates) 原文：
> 旧有的 API 接口的两个模型名 `deepseek-chat` 与 `deepseek-reasoner` 将于三个月后（**2026-07-24**）停止使用。

| 时点 | 事实 |
|---|---|
| 2026-04-24 | 官方公告 `deepseek-chat` / `deepseek-reasoner` **将于 2026-07-24 停用** |
| 2026-07-24 | 名义上的停用日（**实际未执行**，兼容层仍在） |
| 2026-09-08 | 项目**改为依赖** `deepseek-chat`（commit `c0d7209`）|
| 2026-09-10 | 实测仍 HTTP 200（可用），但**这是"欠着的债"，随时可能被撤** |

**一旦兼容层撤销**：`chat-model=deepseek-chat` 的所有调用立即 400/404 → 意图提取、重排、偏好提取、RAG、对比**全线降级**（mall-ai 的降级路径会兜住不 500，但 AI 功能等于失效）。

### 1.3 附带发现：两个别名现在指向同一个底层模型

2026-09-10 实测（向 `api.deepseek.com` 发 `max_tokens=1` 最小请求，只读、成本可忽略）：

| 请求的 model | HTTP | 响应里的 `model` | 是否返回 `reasoning_content` |
|---|---|---|---|
| `deepseek-chat` | 200 | `deepseek-flash` | **无**（非推理）|
| `deepseek-v4-flash` | 200 | `deepseek-flash` | **有**（推理）|
| `deepseek-flash` | 200 | `deepseek-flash` | 有 |
| `deepseek-reasoner` | 200 | `deepseek-flash` | 有 |
| `deepseek-v4-pro` | 200 | `deepseek-v4-pro` | 有 |

**结论**：`deepseek-chat` 与 `deepseek-v4-flash` **底层已是同一个 `deepseek-flash`**，行为差异**只来自"思考模式开/关"** —— 这正好印证了 §三 的官方机制才是正路。

> ⚠️ `GET /models` **不是权威清单**：它只列 `deepseek-flash` 与 `deepseek-v4-pro`，**不含** `deepseek-chat` / `deepseek-v4-flash`（但两者都能调）。**不能据它判断可用性。**

---

## 二、根因：当初为什么改用 `deepseek-chat`（这段历史必须保留）

见 [[TODO第二批实现与原理]] §5.4.5 #21 的完整排查链：

`deepseek-v4-flash` 是 **reasoning（思考）模型**，响应 = `reasoning_content`（思考）+ `content`（答案）。它对**重排 / 意图提取**这类 JSON 小任务**过度思考**，且思考量**随 `max_tokens` 水涨船高**（实测：`max_tokens=1000` → 想满 1000；`=4000` → 想满 4000）→ 思考吃满预算 → `content` **为空或被截断** → JSON 解析失败 → 降级。

当时的修法是**按任务类型换模型名**：JSON 任务用非推理的 `deepseek-chat`。**方向对，但手段过时** —— 它用"换模型名"来间接控制"思考开关"，而这恰恰是官方**已提供显式参数**的能力。

---

## 三、正解：官方「思考模式开关」

[官方「思考模式」文档](https://api-docs.deepseek.com/zh-cn/guides/thinking_mode) 给出的机制是「**同一个模型 + 思考模式开关**」，**不是**"准备两个不同模型"：

| 能力 | 参数（OpenAI 格式） | 参数（Anthropic 格式） |
|---|---|---|
| **思考模式开关** | `{"thinking": {"type": "enabled/disabled"}}` | `{"reasoning": {"effort": "none/low/high/max"}}`（`none` = 关闭）|
| **思考强度控制** | `{"reasoning_effort": "low/high/max"}` | `{"output_config": {"effort": "low/high/max"}}` |

**官方明确说明的约束（都会影响现有代码，见 §五）**：

1. **思考模式默认打开**，`effort` 默认 `high`
2. **思考模式下 `temperature` / `presence_penalty` / `frequency_penalty` 不生效** —— *"为了兼容已有软件，设置参数不会报错，但也不会生效"*
3. 非思考模式下 `top_p` **恒为 1.0**；思考模式下 `top_p` 下限被抬到 `0.95`
4. 用 OpenAI SDK 时 `thinking` 要放进 `extra_body`；**裸 HTTP 请求直接放 body 顶层即可**（本项目是 `RestTemplate` + `Map`，直接 `put` 即可）
5. 官方示例用的 model id 就是 **`deepseek-flash`**（现役 id）

### 3.1 🔬 2026-09-10 实测复验（7 格实验，直接验证开关与 400 规则）

> **方法**：在老机用生产 `AI_API_KEY` 向 `https://api.deepseek.com/chat/completions` 发最小请求（`max_tokens` 50~300，成本可忽略、只读）。
> **目的**：**不凭官方文档引述**，直接证实/证伪"开关生效"与"400 规则"，并顺带验证 #32（Function Calling）的可行性。

| 实验 | 请求 | 结果 | 结论 |
|---|---|---|---|
| **A** | `thinking: disabled` + `response_format: json_object` | **200**，`model=deepseek-flash`，**无 `reasoning_content`**，content = 干净 JSON（len 8） | ✅ **开关从根上生效**（§三 的核心假设成立） |
| **B** | `thinking: enabled` + `tools` + 历史 assistant 消息**不带** `reasoning_content` | 🔴 **HTTP 400**：`The `reasoning_content` in the thinking mode must be passed back to the API.`（`invalid_request_error`） | ✅ **400 规则实测复现**（不是文档传闻） |
| **C** | `thinking: disabled` + `tools` + **不带** `reasoning_content` | **200** | ✅ **"工具轮用 disabled"设计成立** → 天然绕开 400 规则（供 #32 用） |
| **D** | `thinking: enabled` + `tools` + **带** `reasoning_content` | **200**（reasoning len 103，**content len 0**） | ✅ 证明规则**只因缺字段**；⚠️ 同时暴露"思考吃满 `max_tokens` → content 空"的老问题 |
| **E** | `thinking: disabled` + `tools` + 全新提问"推荐 5000 以内的手机" | **200**，`finish_reason=tool_calls`，**`tool_calls` 有 1 个**，`search_products` 参数 `{"keywords":"手机","budgetMax":5000}` | ✅ **#32-P0 可行性证实**（模型确实主动调工具，且参数抽取准确）|
| **F** | `thinking: disabled` + `tools` + **`response_format: json_object`** | **200**，但 **`tool_calls` = 无**，模型直接返回 JSON 内容 | 🔴 **新坑：`tools` 与 `response_format` 互斥**——`response_format` 会"抢走"工具调用。**工具轮绝不能同时设 `response_format`** |
| **G** | `thinking: enabled` + `tools` + 全新提问 | **200**，**`tool_calls` 有 1 个**（但后续必须回传 `reasoning_content`，见 B/D） | 推理模式也能调工具，但代价是**必须维护 reasoning 回传链路** |

**实测带来的 3 个方案修正**：

1. **`tools` 与 `response_format` 二选一**（实验 F）→ **#32 的工具轮不要设 `response_format`**；现有 `chatWithModel(jsonMode=true)` 的写法与 Function Calling **不兼容**，这是 #32-P0 的硬约束（已同步到 [[AI导购Agent升级方案]]）。
2. **SSE 分支 `max_tokens` 偏紧**（实验 D 佐证"思考吃满 → content 空"）：`ChatServiceImpl:370` **硬编码 `max_tokens: 2000`**（配置里是 3000）→ 本次改造**应一并改为读配置**，别让思考把答案挤空。
3. **`DeepSeekAiClient.embed/embedBatch` 是死代码**（顺带发现，非本方案范围）：全项目**没有任何地方**用 `AiClient` 的 embed —— `RagServiceImpl:48/84` 与 `VectorSyncServiceImpl:36/77/148` 都**直接注入 `SiliconFlowEmbeddingClient`**。而 `DeepSeekAiClient.embed` 打的是 DeepSeek baseUrl + 硅基流动的模型名（真被调用必失败）→ 属遗留死代码，可**顺手删除或标注**。

---

## 四、方案

### 4.1 目标形态

**统一用官方现役 model id + 显式 `thinking` 开关**，按"是否需要思考"而不是"模型名"来分流：

```java
// ① JSON 结构化任务（重排 / 意图提取 / 偏好提取 / 查询扩展）
requestBody.put("model", "deepseek-v4-flash");                        // 官方现役 id
requestBody.put("thinking", Map.of("type", "disabled"));              // ← 根上关掉思考
requestBody.put("response_format", Map.of("type", "json_object"));
// 无需再靠提示词"不要思考"，也不可能出现 reasoning 挤空 content

// ② 需要深度推理的对话（SSE 流式 / RAG / 对比）
requestBody.put("model", "deepseek-v4-flash");
requestBody.put("thinking", Map.of("type", "enabled"));
requestBody.put("reasoning_effort", "high");                          // 或 low / max
```

**为什么这是长期稳定的解**：

| # | 现在（脆弱） | 改后（稳定） |
|---|---|---|
| 1 | 依赖 `deepseek-chat` —— 官方已公告停用，靠兼容层活着 | 用**官方现役 id**（`deepseek-v4-flash` / `deepseek-flash`，官方文档与 `/models` 都用它）|
| 2 | 靠**提示词**"叫模型别思考"（打补丁；实测仍偶发截断）| `thinking: disabled` **从根上关掉思考**，不可能有 `reasoning_content` 挤占 `max_tokens` |
| 3 | 用"**换不同模型名**"区分任务类型 | 官方设计的**单模型双模式**，语义清晰 |
| 4 | 同一模型名被**两类任务共用**（见 §4.2 洞察）| 每个调用点**显式声明**自己要哪种模式 |

### 4.2 调用点分类（✅ 2026-09-10 逐处 grep 核实；🔧 2026-09-10 实施期修正 1 处）

**A. JSON 结构化任务 → `thinking: disabled` + `response_format`（实施期修正为 **3 处**）**

| 调用点 | 任务 | 当前模型来源 | 现状 |
|---|---|---|---|
| `ChatServiceImpl:316` | 意图提取 | **硬编码** `"deepseek-chat"` | 传 `jsonMode=true` |
| `SearchServiceImpl:227-232` | 重排 | `aiProperties.getChatModel()` | 传 `jsonMode=true` |
| `PreferenceExtractor:54` | 偏好提取 | `chat()` → `doChat` → `chatModel` | ⚠️ **走 `doChat`，没有 `jsonMode` 标志**（靠提示词，返回后 `JSON.parseObject`）|

> 🔧 **2026-09-10 实施期修正（读码发现：原稿把第 4 处归错类了）**
>
> `SearchPipeline:120` 是 **`expandQuery` 查询扩展**，输出的是**空格分隔的关键词纯文本，不是 JSON**。
> 它同样需要"关思考"（短任务、要稳定输出），但**绝不能下发 `response_format`** ——
> DeepSeek 要求开启 `json_object` 时**提示词必须含 "json"**，而该方法的提示词并没有 → 强行归类会直接 **400**。
> **因此实现里单列了任务类型 `AiTask.EXPAND`**（关思考、不带 `response_format`），见 §4.5。

**A′. 纯文本结构化短任务 → `thinking: disabled`，但**不带** `response_format`（1 处）**

| 调用点 | 任务 | 说明 |
|---|---|---|
| `SearchPipeline:120` | 查询扩展（`expandQuery`） | 输出空格分隔关键词；关思考 + 不下发 `response_format` |

**B. 自然语言 / 需推理 → `thinking: enabled`（4 处）**

| 调用点 | 任务 | 当前模型来源 |
|---|---|---|
| `ChatServiceImpl:103` | 对话（RAG 前置）| `chat()` → `doChat` |
| `RagServiceImpl:460` | RAG 回答 | `chat()` → `doChat` |
| `ProductCompareServiceImpl:79` | 对比摘要 | `chat()` → `doChat` |
| `ChatServiceImpl:354-373` | **SSE 流式对话** | **硬编码** `"deepseek-v4-flash"` |

> ⭐ **关键洞察（本方案的第二个理由）**：`doChat()` 同时被 **A 类（偏好提取）与 B 类（对话、RAG、对比）** 共用 —— 也就是说"**用模型名区分任务类型**"这个做法**本身就不严谨**：同一个 `chat-model` 服务了两类完全不同的需求。改用 `thinking` 显式参数后，这个歧义消失。
> **副作用提醒**：A 类里的 `PreferenceExtractor` 当前**没有 `jsonMode` 参数** → 实现时改为直接调用 `chatJson(...)`（新增的 JSON 任务入口），不再需要扩签名。

### 4.3 改动清单（文件级，供实施时对照）

| # | 文件 | 改动 | 说明 |
|---|---|---|---|
| 1 | `AiProperties.java` | `chatModel` 默认值 `"deepseek-chat"` → `"deepseek-v4-flash"`；新增 `reasoningModel` / `thinkingEnabled` 之类字段 | 保留配置项，但值换成现役 id |
| 2 | `application.yml` | `chat-model: deepseek-chat` → `deepseek-v4-flash`；删掉死配置 `compare-model`（§5.2）| prod 未覆盖，改这一处即生效 |
| 3 | `application-test.yml`（如涉及）| 同步检查 | 本地/测试环境一致性 |
| 4 | `DeepSeekAiClient.chatWithModel()` | body 增加 `thinking`（**按 `jsonMode` 推导**：`jsonMode=true → disabled`）；`jsonMode=false → enabled` + `reasoning_effort` | 一处改动覆盖 A/B 两类中的 2 个调用点 |
| 5 | `DeepSeekAiClient.doChat()` | 增加 `thinking: enabled` + `reasoning_effort`；**或在接口上补模式参数**以区分 A 类（偏好提取/查询扩展）与 B 类（对话/RAG/对比）| ⚠️ **这是本方案唯一需要"设计决策"的地方**（见下方二选一）|
| 6 | `ChatServiceImpl:316` | 去掉硬编码 `"deepseek-chat"`，改用 `aiProperties.getChatModel()` | 消除硬编码漂移 |
| 7 | `ChatServiceImpl:354-373`（SSE）| 去掉硬编码 `"deepseek-v4-flash"` → 用配置；body 增加 `thinking: enabled` + `reasoning_effort`；🔴 **`max_tokens: 2000` 硬编码 → 改为读配置（3000+）**（实测 D：思考吃满会把 content 挤空）| 顺带修掉 `temperature` 死参数（§5.1）|
| 8 | `SearchServiceImpl:227-232` | 传入的 model 由硬编码/配置统一；确保 `thinking: disabled` | — |

**`doChat` 的处理 —— ✅ 2026-09-10 已定（"甲"的精简变体：新增 `chatJson`，不动现有签名）**：

- ✅ **采纳做法**：**新增** `String chatJson(String systemPrompt, String userMessage)`（内部 `thinking: disabled`），只把 **2 个 JSON 调用点**改过去：
  - `PreferenceExtractor:54`
  - `SearchPipeline:120`
- ✅ **现有 `chat(...)` 保持"思考模式"语义不变** → `ChatServiceImpl:103`（多轮对话）、`RagServiceImpl:460`（RAG）、`ProductCompareServiceImpl:79`（对比）**一行都不用改** → **回归面最小**。
- 🚫 不采纳乙（`doChat` 一律 disabled，会让对话质量下降）、丙（不解决"靠提示词"的问题）。
- **影响面已实测确认**：`AiClient` **只有 `DeepSeekAiClient` 一个实现**（`SiliconFlowEmbeddingClient` 不实现该接口，见 §3.1 修正 3）→ 加方法零风险。

> 📌 **本次一并纳入的「模型配置可配化」（第 1+2 层）见 §4.5** —— 它复用**同一批文件**（`AiProperties` / `application.yml` / `DeepSeekAiClient` / `ChatServiceImpl`），**边际成本 ≈ 0**；并已评估 **不引入 Spring AI**（§4.6）。

### 4.4 为什么不是"找一个不会过度思考的模型"

这是本方案最想澄清的一点：**不需要换模型**。

- `thinking` 开关**就是**"会不会思考"的开关 —— 关掉它，模型**根本不会产出 `reasoning_content`**，过度思考问题从**机制层面**消失（不是缓解）
- 换成"别的非推理模型"反而会引入**新依赖**（又要担心那个名字停不停用、能力够不够）
- 官方现役 id 只有 `deepseek-flash` / `deepseek-v4-pro` 两个（`/models` 实测），它们的区别是**能力档位**，不是"会不会思考" —— 后者靠 `thinking` 控制

### 4.5 模型配置可配化设计（第 1+2 层，2026-09-10 并入本次改造）

> **诉求（用户）**：模型相关配置要能适应**未来模型更名 / 下架** —— 即"**改名不用改代码、不用重编译**"。
> **现状实测：半可配**。Key / API 地址已是占位符 ✅；但**模型名是字面量，且有 2 处硬编码在代码里**；而 prod 又**未覆盖 `cooxiao.ai` 段** → 生产模型名来自 **jar 内的 `application.yml`** → **想换模型必须重编译**。

**第 1 层：消灭硬编码 + 模型名外置为环境变量（必做）**

| 动作 | 位置 |
|---|---|
| 2 处**硬编码模型名** → 改读配置 | `ChatServiceImpl:316`（`"deepseek-chat"`）、`:367`（SSE 的 `"deepseek-v4-flash"`） |
| 模型名/地址改**显式占位符** | `application.yml`：`chat-model: ${AI_MODEL_CHAT:…}`、`embedding-model: ${AI_MODEL_EMBEDDING:…}`、`embedding-base-url: ${AI_EMBEDDING_BASE_URL:…}` |
| 顺带收敛**硬编码参数** | `ChatServiceImpl:369` 的 `temperature`、`:370` 的 `max_tokens`（→ 读配置）、`DeepSeekAiClient:82` 的 `temperature 0.3` |
| 顺带消除**重复的请求体构造** | SSE 那段自建 body + 自建 Authorization（`:354-373`）与 `DeepSeekAiClient` **重复了一份** → 收敛为 `AiClient.streamChat(...)`，让**模型 / 思考 / 温度 / max_tokens 只有一处** |

> ⚠️ **必须用显式占位符，不要指望 Spring relaxed binding**：`cooxiao.ai.chat-model` 对应的环境变量是 **`COOXIAO_AI_CHATMODEL`**（去横线），写 `COOXIAO_AI_CHAT_MODEL` **并不直接映射** —— 隐式且易错。

**第 2 层：能力档位层（把"任务语义"与"模型 id"解耦）**

```yaml
cooxiao:
  ai:
    models:                                  # 逻辑档位 → 实际 id（易变，走环境变量）
      flash:     ${AI_MODEL_FLASH:deepseek-v4-flash}
      pro:       ${AI_MODEL_PRO:deepseek-v4-pro}
      embedding: ${AI_MODEL_EMBEDDING:BAAI/bge-m3}
    tasks:                                   # 任务 → 档位 + 思考模式
      chat:    { tier: flash, thinking: enabled }
      json:    { tier: flash, thinking: disabled }
      compare: { tier: flash, thinking: enabled }
```

- 代码只出现 `TaskType.CHAT` / `TaskType.JSON`，**永不出现模型名**
- **官方改名 → 改一个环境变量 + 重建 `mall-ai` 容器**；官方出新档位 → 加一行 yml，**代码零改动**
- 本次要加的 `chatJson(...)` 就是 `tasks.json` 的落地形态

**补充：换 embedding 模型会牵动 ES 索引（本次不做，仅记录）**
维度变了 → ES mapping 的 `dims` **不可修改** → 必须走"**删索引 → 改 `embedding-dimensions` → 重启 → `/ai/syncAll`**"。
（好消息：`dims` **已经是读配置**的 —— `EsIndexInitializer` 用 `.formatted(aiProperties.getEmbeddingDimensions())` ✅）

### 4.6 为什么**暂不引入 Spring AI**（2026-09-10 评估结论）

| 判据 | 事实 | 结论 |
|---|---|---|
| **版本代际** | 官方支持矩阵：**Boot 3.4.x → Spring AI 1.0.x**、**Boot 3.5.x → 1.1.x**、**Boot 4.x → 2.x**；**Boot 3.3 及更早不在官方支持代际内**（[官方入门文档](https://docs.springframework.org.cn/spring-ai/reference/getting-started.html)写明支持 3.4/3.5；其 2.0 页标注"2.0.x 支持 Boot 4.0.x/4.1.x"） | 🔴 **本项目 Boot 3.2.5 低于支持下限** |
| **引入代价** | 要接 Spring AI 必须先升 Boot 3.2.5 → **≥3.4**，连带 **Spring Cloud 2023.0.3 → 2024.0.x**、**Spring Cloud Alibaba 2023.0.1.2 → 新一代**、Dubbo/Seata/Sentinel/Gateway…**11 个服务 + 跨机集群全量回归** | 🔴 这是**全站框架升级项目**，不是 AI 功能项目 |
| **本次诉求能否达成** | "模型可配化"本质是**请求体参数级**的事（model / thinking / temperature）→ **手写收敛即可**（第 1+2 层，零框架依赖） | ✅ 不需要框架 |
| **#32 工具循环** | 手写约 100~150 行；且本次实验已把 `tool_calls` / `reasoning_content` / `tools`×`response_format` 三条规则摸清 | ✅ 可手写 |
| **演示 / 面试** | 手写能把 **`tool_calls` 消息结构**、**`reasoning_content` 回传规则**、**`tools` 与 `response_format` 互斥**这些细节讲透；用框架会把这些藏起来 | ✅ 手写反而更有料 |
| **未来口子** | 第 2 层的"**档位 + 任务映射**"是**框架无关抽象**，将来迁到 Spring AI 的 `ChatClient` / `ChatOptions`（`spring.ai.openai.chat.options.model`）**可直接映射** | ✅ 本次投入不浪费、不锁死 |

> ✅ **结论**：**#58 与 #32 本次都不引入 Spring AI**，按"**手写 + 模型配置可配化**"落地；Spring AI 保留为 **#32 原方案的 P2（框架化）**，其**前置 = Boot 全站升级**，已另行登记 **TODO #60** 跟踪。
> （历史补充：Spring AI `0.8.x` 里程碑版曾支持更早的 Boot 代际，但那是 1.0 GA **之前**、早已停止维护的版本，2026 年回头采用维护性极差，**不作为选项**。）

### 4.7 若为了 Spring AI 强行升级 Spring Boot：影响面实测（2026-09-10）

> 回答"升级 Boot 是不是**只影响 AI 模块**"—— **不是**。

**① 版本声明在根 pom，全体继承（实测证据）**

```
pom.xml（根，artifactId = csmall）
└─ <parent> spring-boot-starter-parent 3.2.5        ← Boot 版本在这里
   <properties>
     spring-cloud.version          = 2023.0.3        ← 必须与 Boot 同代际
     spring-cloud-alibaba.version  = 2023.0.1.2      ← 必须与 Boot 同代际
```

实测：**13 个顶层模块全部 `parent = csmall`**（mall-ai / ams / common / front / gateway-server / order / pojo / product / resource / search / seckill / sso / ums）
→ **改根 pom 的 Boot 版本 = 整个反应堆一起改**，不存在"只改 AI 模块"的选项。

**② 三件套必须联动，且牵动一整套 starter**

| 需同步升级 | 现值 | 说明 |
|---|---|---|
| Spring Boot | **3.2.5** | → **≥3.4**（Spring AI 支持下限） |
| Spring Cloud | 2023.0.3 | → **2024.0.x**（Moorgate，配 Boot 3.4） |
| Spring Cloud Alibaba | 2023.0.1.2 | → 与目标 Boot 代际配对的版本 ⚠️ **SCA 发布节奏常滞后于 Boot，是首要风险项** |
| springdoc / Knife4j | 2.5.0 / 4.5.0 | springdoc 与 Boot 版本**强耦合**，几乎必然要动 |
| MyBatis-Plus / mybatis-spring-boot / PageHelper | 3.5.9 / 3.0.3 / 1.4.7 | 各自需换到 Boot 3.4+ 兼容版本 |
| Dubbo / Seata / Sentinel 相关 starter | 3.3.2 / 2.1.0 / BOM 管理 | 逐一验证与目标 Boot 的适配 |
| Druid / Redisson / Quartz / spring-security-test / spring-rabbit-test | — | 与 Boot 主版本对齐的项需同步 |

**③ 影响面量化**

| 维度 | 数量 |
|---|---|
| 反应堆顶层模块 | **13**（11 个运行服务 + gateway + common/pojo 两个库） |
| pom.xml 文件 | 30+ |
| 需重建部署 | **11 个服务 + 1 个网关**，跑在**两台机器**（老机 21 容器 / 新机 5 容器） |
| 必做回归 | SSO+网关路由 / 秒杀跨机双实例 / Redis 主从哨兵 / Seata 分布式事务 / Dubbo 全链路 / ES 搜索 / AI 全链路 / Sentinel 规则 / Nacos 注册与配置 |
| 相对当年 SB2→SB3 的变化 | 现在多了**跨机集群 + 安全加固基线（JWT/强密码/端口/认证）** → **回归面比当年更大** |

**④ "只升 AI 模块"技术上也能做，但不该做**

理论上可让 `mall-ai` **不继承根 pom**、单独挂一套 Boot 3.4 + Spring Cloud 2024 的 `dependencyManagement`。但：

- `mall-ai` **依赖 `mall-common` / `mall-pojo` / `mall-product-service`**（均按 Boot 3.2.5 编译的二方库）→ 一个反应堆里出现**两套 Spring 代际**；fat jar 会用 3.4 的 Spring Framework 覆盖传递依赖，而 `mall-common` 里的 Security Filter / Redis 配置是按 6.1 编译的 → **运行期 `NoSuchMethodError` 一类风险**
- Nacos / Sentinel / Seata / Dubbo 的 starter 也必须为 mall-ai 单独选版本 → **一个项目两套 Spring Cloud Alibaba**，构建与排障复杂度翻倍
- 收益仅是"让一个演示模块用上框架" → **性价比不成立**

**⑤ 结论与建议路线**

| 选择 | 评价 |
|---|---|
| **为 Spring AI 升级 Boot** | ❌ **不建议** —— 用"全站框架升级 + 跨机集群回归"的风险，换一个"锦上添花"的框架 |
| **先手写实现（本次路线）** | ✅ **推荐** —— 模型可配化 / `thinking` 开关 / 工具循环都能达成，且留下框架无关抽象 |
| **独立立项做 Boot 升级** | 🟡 **值得，但理由应是 EOL 与安全**（Boot 3.2.x 的 OSS 支持窗口约 13 个月、早已结束），**而不是 Spring AI**；升级时**顺带**解锁 Spring AI 才合理 |

> ✅ 因此 **#60** 的结论是"**暂不引入 Spring AI**"，触发条件 = **将来做 Boot 全站升级时顺带评估**（届时仍需逐一核实 SCA / springdoc / MyBatis-Plus / Dubbo 与目标 Boot 代际的配对）。

---

## 五、顺带修掉的三个"死参数 / 死配置 / 死代码"

### 5.1 `temperature` 在思考模式下是死参数

| 位置 | 代码 | 问题 |
|---|---|---|
| `DeepSeekAiClient:82` | `requestBody.put("temperature", 0.3)` | `chatWithModel` 硬编码 |
| `DeepSeekAiClient:131` | `requestBody.put("temperature", aiProperties.getTemperature())`（默认 0.7）| `doChat` |
| `ChatServiceImpl:369` | `body.put("temperature", 0.7)` | **SSE 分支（思考模式）** |

按官方文档，**思考模式下 `temperature` 不生效**（设了不报错）。所以 SSE 分支的 `temperature: 0.7` **一直是死参数**；改造后（显式 `thinking: enabled`）依然是死参数 —— 应**删除或注释说明**，避免误导后来人以为"调了温度"。

### 5.2 `compare-model` 是死配置（零引用）

全仓库 grep `compareModel` / `compare-model`，**只命中定义处与 yml 各一次**：

```
AiProperties.java:22      private String compareModel = "deepseek-v4-flash";
application.yml:27        compare-model: deepseek-v4-flash
```

**没有任何代码读取它** → `ProductCompareServiceImpl` 用的是 `aiClient.chat(...)`（即 `chat-model`）。
**处置建议**：✅ **删除**（2026-09-10 用户确认；全仓库零引用，实测确认）。

### 5.3 `DeepSeekAiClient.embed/embedBatch` 是死代码（2026-09-10 实测发现）

全项目**没有任何注入点使用 `AiClient` 的 embed**：

- `RagServiceImpl:48/84` → 直接注入 `SiliconFlowEmbeddingClient`
- `VectorSyncServiceImpl:36/77/148` → 同样直接注入 `SiliconFlowEmbeddingClient`

而 `DeepSeekAiClient.embed` 打的是 **DeepSeek `baseUrl` + 硅基流动的 `embeddingModel`（BAAI/bge-m3）+ DeepSeek 的 apiKey** —— 三样对不上，**真被调用必然失败**。

**处置建议**：🟡 顺手清理（可延后）——删除 `DeepSeekAiClient` 的 embed/embedBatch 与 `AiClient` 的对应声明（连带 `doEmbed` 共约 55 行），或至少标注 `@Deprecated` + "embedding 请用 SiliconFlowEmbeddingClient"。**与 #58 主目标无关**，若担心回归面可留到之后再动。

---

## 六、风险、成本与回滚

| 项 | 评估 |
|---|---|
| **成本** | 极低。生产 AI 模块 **0 调用**（业务收益为零），改动只影响潜在调用路径 |
| **Token 成本** | `thinking: disabled` 会让 JSON 任务的 completion token **大幅下降**（不再产出思考 token）→ 反而**省钱** |
| **风险 ①** | **忘记加 `reasoning_content` 回传规则**：官方规定**携带 `tools` 的请求**必须回传历史 `reasoning_content`，否则 400。⚠️ 本项目 `chatWithModel` **未使用 `tools`**（无 Function Calling），故**不受影响**；但**若将来做 [[AI导购Agent升级方案]]（#32）引入 tools，必须遵守此规则** |
| **风险 ②** | 参数名/取值写错（如 `thinking` 放错层级）→ 走 OpenAI SDK 时要放 `extra_body`；**裸 HTTP 直接放 body 顶层**（本项目属后者，直接用 `Map.put`）|
| **风险 ③** | 响应结构不变（`content` / `reasoning_content` 同级），现有解析代码**无需改**；但改成 `thinking: disabled` 后**不应再期待 `reasoning_content`** |
| **回滚** | 单文件配置回滚：`application.yml` 的 `chat-model` 改回 `deepseek-chat` + 重建 mall-ai 容器（**不改数据结构、无数据风险**）|
| **部署** | 需重新构建 mall-ai 镜像 + 重建容器（约 1 个容器，非全站）|

---

## 七、验证清单（实施后逐项跑）

```bash
# ① JSON 任务不再产出 reasoning（意图提取/重排）
docker logs --tail 200 csmall-ai | grep -i 'reasoning\|content 为空'   # 期望：无
# ② 调 API 直连验证两种模式的行为差异（不依赖应用）
curl -s https://api.deepseek.com/chat/completions \
  -H "Content-Type: application/json" -H "Authorization: Bearer $KEY" \
  -d '{"model":"deepseek-v4-flash","thinking":{"type":"disabled"},
       "messages":[{"role":"user","content":"hi"}],"max_tokens":20}'
#   期望：message 里【没有】reasoning_content，content 直接是答案
# ③ 同参数把 thinking 改成 enabled → 期望出现 reasoning_content
# ④ 业务回归：前端搜一个词 → AI 主链路通（意图解析→召回→重排）且无降级
# ⑤ 预算记账：确认 usage 仍返回（reasoning token 不计入 completion? 以实测 usage 为准）
# ⑥ 死参数清理确认：SSE 分支不再发送 temperature
```

---

## 八、面试话术

**主线**："我发现 AI 模块的生产配置依赖 `deepseek-chat` —— 而官方早在 4 月就公告这个模型名 **7 月 24 日停用**，只是兼容层还在。这属于典型的'**技术债靠别人的宽容在跑**'。我顺着查清了当初为什么用它：`deepseek-v4-flash` 是**思考模型**，对重排/意图提取这类 JSON 小任务会**思考到把 `max_tokens` 吃满**，导致 `content` 被挤空、解析失败、降级——当时的解法是**按任务类型换模型名**（JSON 任务用非推理的 `deepseek-chat`）。

**但这是过时的手段**：DeepSeek 官方提供的机制是「**同一个模型 + `thinking` 思考模式开关**」——`{"thinking":{"type":"disabled"}}` 从根上关掉思考，模型根本不会产出 `reasoning_content`，问题从机制层面消失，而不是靠提示词'求它别想'。所以我把它改成**官方现役模型 id + 显式 thinking 开关**：JSON 任务 disabled、深度对话 enabled + `reasoning_effort`。顺带发现两件事：① SSE 分支的 `temperature` 在思考模式下**是死参数**（官方明确说不生效）；② `compare-model` 配置项**零引用**，是死配置。

排查过程中我还做了两个实验：向 `/chat/completions` 发 `max_tokens=1` 的最小请求逐个试探 5 个模型名，发现 `deepseek-chat` 和 `deepseek-v4-flash` **底层已经指向同一个 `deepseek-flash`**，行为差异只来自思考模式——这恰好证明了'该用模式开关而不是换模型名'。另外确认 `GET /models` **不是权威清单**（它不列 `deepseek-chat`，但那个名字其实还能调）。"

**被追问"你怎么保证改完不影响线上"**："生产 AI 模块 0 调用（业务收益为零），改动是配置 + 客户端请求体；不加 `tools` 所以不触发官方那条'必须回传 reasoning_content'的 400 规则；响应结构不变，上层解析代码不用动；回滚就是改回一个模型名 + 重建一个容器。"

**被追问"为什么不干脆换个不会过度思考的模型"**："因为**不需要换模型** —— `thinking` 开关本身就是'会不会思考'的开关，关掉它模型根本不产出思考内容，是机制层面的解决。换模型反而引入新的名字依赖，而且官方现役只有 `deepseek-flash` / `deepseek-v4-pro` 两个档位，它们的区别是能力而不是'想不想'。"

---

## 九、备选方案

| 方案 | 内容 | 评价 |
|---|---|---|
| **A（本方案，推荐）** | 官方现役 id + 显式 `thinking` 开关（按调用点分流）| ✅ 长期稳定、语义清晰、顺带修掉 2 个死参数 |
| **B** | 只把 `chat-model` 换成 `deepseek-v4-flash`，**暂不动 thinking** | ⚠️ 会**退回** reasoning 挤空 content 的老问题（§二）→ 不可行，除非同时禁用思考 |
| **C** | 维持 `deepseek-chat`，**加监控 + 写好降级** | ⚠️ 治标不治本；兼容层一撤就失效（但可作为"暂不改"的兜底：至少**在 TODO 里留明确触发条件**）|
| **D** | 换用第三方非推理模型（如其他厂商）| ❌ 引入新依赖 + 成本模型变化，与本项目"不增加外部依赖"原则冲突 |

---

## 十、决策记录（2026-09-10 用户确认：按推荐执行）

| # | 决策点 | 结论 |
|---|---|---|
| 1 | 是否采纳**方案 A**（现役 id + 显式 `thinking` 开关） | ✅ **采纳** |
| 2 | `doChat` 改造方式 | ✅ **新增 `chatJson` 变体**（只改 2 个 JSON 调用点，B 类调用点零改动）→ §4.3 |
| 3 | `compare-model` 死配置 | ✅ **删除**（全仓库零引用，实测确认） |
| 4 | `DeepSeekAiClient.embed` 死代码 | 🟡 顺手清理（可延后，见 §5.3） |
| 5 | 实施时机 | ✅ **与 [[AI导购Agent升级方案]]（#32）同一维护窗口、分两步**：先本方案（Step 1，验证后部署），再上 #32-P0（Step 2） |

> 📌 触发条件提醒：**只要官方哪天真正撤掉 `deepseek-chat`**（实验 E 已确认现役 `deepseek-v4-flash` 可正常调工具），本方案就从"优化"变成"必须立刻做"——建议在兼容期内完成。

---

## 十一、实施记录（Step 1 代码完成，2026-09-10 · ⏳ **未部署**）

> **状态**：代码改造完成 + 本地编译/打包/测试全绿；**尚未部署到服务器**（按约定，部署需单独走维护窗口）。
> **范围**：§十 决策 1~4 + **模型配置可配化**（§4.5 第 1+2 层）。

### 11.1 改动清单（文件级）

| 文件 | 改动 |
|---|---|
| `AiTask.java` 🆕 | 任务枚举：`CHAT` / `JSON` / `EXPAND` / `COMPARE` —— **替代"用模型名区分任务"** |
| `AiProperties.java` | 重构为「**档位层 `models` + 任务层 `tasks`**」；删 `chatModel` / `compareModel`；新增 `agentEnabled` / `agentMaxRounds`（#32 预留）；新增 `taskOptions()` / `modelFor()` 解析器 + 启动打印模型路由 |
| `AiClient.java` | 消息类型加宽为 `List<Map<String,Object>>`（#32 校正②）；新增 `chatJson` / `chat(AiTask,…)` / `streamChat`；**删除死代码 `embed` / `embedBatch`**（§5.3） |
| `DeepSeekAiClient.java` | **请求体只在一处构造**（`buildBody`）：模型来自档位、`thinking` 显式下发、温度仅非思考模式下发、`response_format` 仅 JSON 任务；**SSE 收敛进来**（`streamChat` 复用同一套档位/预算/闸门） |
| `ChatServiceImpl.java` | 删 2 处硬编码模型名 + `temperature 0.7` + `max_tokens 2000`；**删除本地 SSE 实现（~75 行）**与不再需要的字段；`extractSearchIntent` → `chatJson` |
| `PreferenceExtractor.java` | 偏好提取 → `chatJson`（JSON 任务：关思考 + `response_format`） |
| `SearchServiceImpl.java` | 重排 → `chatJson`；删除 `aiProperties` 依赖 |
| `SearchPipeline.java` | 查询扩展 → `chat(AiTask.EXPAND, …)`（**关思考但不带 `response_format`**，见 §4.2 修正） |
| `ProductCompareServiceImpl.java` | 对比摘要 → `chat(AiTask.COMPARE, …)`（让 COMPARE 可单独调档） |
| `application.yml` | `models`（档位→id，**显式环境变量占位符**）+ `tasks`（任务→档位/思考/温度/max_tokens）；删 `chat-model` / `compare-model`；embedding 模型与地址占位符化 |
| `AiConcurrencyGuard.java` | 更新挂点注释（原注释指向已删除的 `chatWithModel` / `doChat`） |
| 测试 🆕 ×2 | `AiPropertiesBindingTest`（yml→Java 绑定）/ `DeepSeekAiClientRequestBodyTest`（请求体规则） |

### 11.2 硬编码清理（§4.5 的 9 项 → 全部清零）

| # | 原硬编码 | 现状 |
|---|---|---|
| 1 | `ChatServiceImpl:316` `"deepseek-chat"` | ✅ 删（走 `chatJson` → 档位解析） |
| 2 | `ChatServiceImpl:367` `"deepseek-v4-flash"` | ✅ 删（SSE 已收敛进客户端） |
| 3 | `ChatServiceImpl:369` `temperature 0.7` | ✅ 删（由任务配置决定） |
| 4 | `ChatServiceImpl:370` `max_tokens 2000` | ✅ 删（读任务配置） |
| 5 | `DeepSeekAiClient:82` `temperature 0.3` | ✅ 删（`tasks.json.temperature`） |
| 6 | SSE 自建 body + Authorization（与客户端重复一份） | ✅ **收敛进 `DeepSeekAiClient.streamChat`** |
| 7 | yml 模型名 / embedding 地址字面量 | ✅ 全部改**显式占位符** |
| 8 | `compare-model` 死配置 | ✅ 删（零引用） |
| 9 | `DeepSeekAiClient.embed/embedBatch` 死代码 | ✅ 删（连带 `AiClient` 声明） |

> **验收**：对 `mall-ai-webapi/src/main` 全量检索模型名 → **唯一命中是 `AiTask.java` 的历史注释**（无代码级模型名）。
> 代码里保留的默认值只剩**服务端点**（`baseUrl` / `embeddingBaseUrl`）—— 那是"连到哪"、不是"用哪个模型"，且都能被环境变量覆盖。

### 11.3 与方案的偏差（实施期发现，均已记录）

| # | 偏差 | 原因 |
|---|---|---|
| 1 | 新增任务类型 **`EXPAND`** | §4.2 原稿把"查询扩展"误归为 JSON 类；它实际输出纯文本，带 `response_format` 会 **400**（见 §4.2 修正） |
| 2 | `AiClient` 新增 `chat(AiTask, system, user)` 重载 | 避免调用方为"任务 + 简单消息"手工拼 `List<Map>` |
| 3 | 提前落地 `agent-enabled` / `agent-max-rounds` | #32 的 S1 本就要加；Step 1 同批改 `AiProperties`/yml 的**边际成本≈0**（默认 `false`，行为不变） |
| 4 | ⚠️ **chat/SSE 的 `max_tokens` 由 2000 → 3000** | 原 SSE 硬编码 2000；思考模式下预算偏紧（实验 D/I：`max_tokens=120` 时 content 必为空）。**这是本次唯一的对外行为变化**：思考预算更宽，代价是可能略增 token 消耗与响应耗时 |

### 11.4 本地验证证据

```
mvn -o compile（全反应堆 13 个模块）              → BUILD SUCCESS
mvn -o -pl mall-ai/mall-ai-webapi -am package     → mall-ai-webapi-0.0.1-SNAPSHOT.jar（121.5 MB，含 BOOT-INF）
mvn -o -pl mall-ai/mall-ai-webapi -am test        → Tests run: 7, Failures: 0, Errors: 0
```

**启动日志（绑定测试实测输出，证明 yml→Java 路由正确）**：

```
AI 模型路由（cooxiao.ai）：chat[flash→deepseek-v4-flash,thinking=on]
                          json[flash→deepseek-v4-flash,thinking=off]
                          expand[flash→deepseek-v4-flash,thinking=off]
                          compare[flash→deepseek-v4-flash,thinking=on]
```

**测试覆盖的关键规则**：
- 档位绑定（嵌套 `Map<String,TaskOptions>` + kebab 键）与**缺失档位 fail-fast**
- `thinking: enabled` 的任务**不下发** `temperature`；`disabled` 的任务**才下发**
- **只有 JSON 任务**带 `response_format`（EXPAND 不带）
- 所有任务的 `model` 均来自档位表（非代码硬编码）

### 11.5 部署记录（2026-09-10 完成）

| 步 | 动作 | 结果 |
|---|---|---|
| 1 | 本地 `mvn package` | `mall-ai-webapi-0.0.1-SNAPSHOT.jar`，127,368,066 字节，MD5 **`ff85810b187cc59030c25aa42e5943c8`** |
| 2 | 上传 + **MD5 校验** | 落位后 `/data/csmall/jars/mall-ai.jar` 的 MD5 与本地**完全一致** |
| 3 | 备份 / 回滚就位 | 旧 jar → `/data/csmall/jars/backup-20260911-step1/`（目录名沿用当时命名）；镜像 tag `csmall-mall-ai:before-step1`（= `8af9d33be09f`） |
| 4 | 重建重启 | `docker compose build mall-ai && docker compose up -d mall-ai` —— **只动 mall-ai 一个容器**；新镜像 `734aaf72…`，启动 45s，内存 600MiB/1GiB |
| 5 | 验证 | 见 §11.6 |

### 11.6 生产验证证据（2026-09-10）

| 验证项 | 实测结果 |
|---|---|
| **🐛 隐患修复** | 「AI 模型路由」启动日志 **恰好 1 次**（修复前 2 次）—— 双 bean 重复注册已消除 |
| 模型路由值 | `chat[flash→deepseek-v4-flash,thinking=on] json[off] expand[off] compare[on]` —— **生产模型名来自配置，非硬编码** |
| **JSON 任务（重排）** | `POST /ai/search`（关键词「手机」）→ **200 / 5s / 9 条命中**，`aiExplanation: "按热度、价格与手机匹配度排序，华为小米优先"` |
| **CHAT 任务（思考 ON）** | `POST /ai/ask`（「推荐一款适合拍照的手机」）→ **200 / 4s**，返回完整中文推荐（含「徕卡影像」等卖点分析） |
| 预算记账 | `ai:daily_cost:2026-09-10`：0 → **0.002492 元**（`usage` 记账链路正常） |
| **#58 目标告警** | `content 为空` **0** 次、`reasoning` 相关 **0** 次、`invalid_request`/4xx **0** 次 |
| Nacos | `172.18.0.9:10010` enabled + healthy |
| 容器 env | `SPRING_PROFILES_ACTIVE=prod`、`AI_API_KEY=sk-****…`、`EMBEDDING_API_KEY=sk-****…`（#44 轮换后的新 key） |

> ⚠️ **冒烟测试在生产留下的痕迹（透明说明）**：1 条管理员登录日志（`ams_login_log`）、`ai:daily_cost` 记了 **0.0025 元**；未创建 AI 会话键（`/ai/ask` 不建会话）。其余无副作用。

### 11.7 后续（不属于本方案）

- **`#32-P0` Function Calling** —— 本方案已为其铺好地基（档位层 + 任务层 + 消息类型加宽为 `Map<String,Object>`）
- 可选：`.env` 注入 `AI_MODEL_FLASH` / `AI_MODEL_EMBEDDING`（不注入即用 yml 默认值）
- 可选：清理 `.idea/workspace.xml` 里遗留的旧 `AI_API_KEY`（#44 已建议，属卫生项）
