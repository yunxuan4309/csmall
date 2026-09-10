# AI 模型名停用风险与 `thinking` 参数改造方案

> **状态**: 📋 **技术方案已出（2026-09-10），待决策 / 待实施** —— 对应 TODO **#58**（第三批）
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

### 4.2 调用点分类（✅ 2026-09-10 逐处 grep 核实，共 8 处）

**A. JSON 结构化任务 → 必须 `thinking: disabled`（4 处）**

| 调用点 | 任务 | 当前模型来源 | 现状 |
|---|---|---|---|
| `ChatServiceImpl:316` | 意图提取 | **硬编码** `"deepseek-chat"` | 传 `jsonMode=true` |
| `SearchServiceImpl:227-232` | 重排 | `aiProperties.getChatModel()` | 传 `jsonMode=true` |
| `PreferenceExtractor:54` | 偏好提取 | `chat()` → `doChat` → `chatModel` | ⚠️ **走 `doChat`，没有 `jsonMode` 标志**（靠提示词，返回后 `JSON.parseObject`）|
| `SearchPipeline:120` | 意图解析 / 查询扩展 | `chat()` → `doChat` → `chatModel` | ⚠️ 同上 |

**B. 自然语言 / 需推理 → `thinking: enabled`（4 处）**

| 调用点 | 任务 | 当前模型来源 |
|---|---|---|
| `ChatServiceImpl:103` | 对话（RAG 前置）| `chat()` → `doChat` |
| `RagServiceImpl:460` | RAG 回答 | `chat()` → `doChat` |
| `ProductCompareServiceImpl:79` | 对比摘要 | `chat()` → `doChat` |
| `ChatServiceImpl:354-373` | **SSE 流式对话** | **硬编码** `"deepseek-v4-flash"` |

> ⭐ **关键洞察（本方案的第二个理由）**：`doChat()` 同时被 **A 类（偏好提取、查询扩展）和 B 类（对话、RAG、对比）** 共用 —— 也就是说"**用模型名区分任务类型**"这个做法**本身就不严谨**：同一个 `chat-model` 服务了两类完全不同的需求。改用 `thinking` 显式参数后，这个歧义消失。
> **副作用提醒**：A 类里两处（`PreferenceExtractor` / `SearchPipeline`）当前**没有 `jsonMode` 参数**，改造时要么扩签名、要么按调用点显式传 `thinking`。

### 4.3 改动清单（文件级，供实施时对照）

| # | 文件 | 改动 | 说明 |
|---|---|---|---|
| 1 | `AiProperties.java` | `chatModel` 默认值 `"deepseek-chat"` → `"deepseek-v4-flash"`；新增 `reasoningModel` / `thinkingEnabled` 之类字段 | 保留配置项，但值换成现役 id |
| 2 | `application.yml` | `chat-model: deepseek-chat` → `deepseek-v4-flash`；删掉死配置 `compare-model`（§5.2）| prod 未覆盖，改这一处即生效 |
| 3 | `application-test.yml`（如涉及）| 同步检查 | 本地/测试环境一致性 |
| 4 | `DeepSeekAiClient.chatWithModel()` | body 增加 `thinking`（**按 `jsonMode` 推导**：`jsonMode=true → disabled`）；`jsonMode=false → enabled` + `reasoning_effort` | 一处改动覆盖 A/B 两类中的 2 个调用点 |
| 5 | `DeepSeekAiClient.doChat()` | 增加 `thinking: enabled` + `reasoning_effort`；**或在接口上补模式参数**以区分 A 类（偏好提取/查询扩展）与 B 类（对话/RAG/对比）| ⚠️ **这是本方案唯一需要"设计决策"的地方**（见下方二选一）|
| 6 | `ChatServiceImpl:316` | 去掉硬编码 `"deepseek-chat"`，改用 `aiProperties.getChatModel()` | 消除硬编码漂移 |
| 7 | `ChatServiceImpl:354-373`（SSE）| 去掉硬编码 `"deepseek-v4-flash"` → 用配置；body 增加 `thinking: enabled` + `reasoning_effort` | 顺带修掉 `temperature` 死参数（§5.1）|
| 8 | `SearchServiceImpl:227-232` | 传入的 model 由硬编码/配置统一；确保 `thinking: disabled` | — |

**`doChat` 的二选一（需你定）**：

- **方案 甲（小改，推荐）**：给 `AiClient.chat(...)` 增加一个"是否 JSON"的重载/参数，让 `PreferenceExtractor` / `SearchPipeline` 显式声明为 JSON 任务 → `doChat` 据参数决定 `thinking`。**语义最干净，改动可控。**
- **方案 乙（最小改）**：`doChat` 一律 `thinking: disabled`（因为它服务的大多是短任务），只有 SSE/RAG/对比显式开思考。**改动最小，但会让"对话"走非思考模式（回答质量可能下降）。**
- **方案 丙（不动 `doChat`）**：只把 `chat-model` 换成现役 id，`thinking` 只在 `chatWithModel(jsonMode=true)` 和 SSE 两处加。**风险最低，但没解决"靠提示词"的问题。**

### 4.4 为什么不是"找一个不会过度思考的模型"

这是本方案最想澄清的一点：**不需要换模型**。

- `thinking` 开关**就是**"会不会思考"的开关 —— 关掉它，模型**根本不会产出 `reasoning_content`**，过度思考问题从**机制层面**消失（不是缓解）
- 换成"别的非推理模型"反而会引入**新依赖**（又要担心那个名字停不停用、能力够不够）
- 官方现役 id 只有 `deepseek-flash` / `deepseek-v4-pro` 两个（`/models` 实测），它们的区别是**能力档位**，不是"会不会思考" —— 后者靠 `thinking` 控制

---

## 五、顺带修掉的两个"死参数 / 死配置"

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
**处置建议**：要么删除，要么在改造时真正用起来（见 §4.3 #5 的方案甲）。

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

## 十、待你决策

1. **是否采纳方案 A**？（含 4.3 里 `doChat` 的甲/乙/丙三选一）
2. 采纳后是否**这次就实施**（改代码 + 重建 mall-ai 容器，约 1 个容器、低风险）？
3. `compare-model` 死配置：**删除**还是**顺手用起来**？

> 📌 触发条件提醒：**只要官方哪天真正撤掉 `deepseek-chat`**，本方案就从"优化"变成"必须立刻做"——建议至少在兼容期内完成。
