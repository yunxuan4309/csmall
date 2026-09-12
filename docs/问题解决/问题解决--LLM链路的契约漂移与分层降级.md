# 问题解决 -- LLM 链路的契约漂移与分层降级

> **日期**: 2026-09-11（素材来自 2026-09-10/11 的 #58 改造与 #32 Agent 上线）
> **一句话**: **在 LLM 参与的链路里，"失败"是常态而不是异常；更隐蔽的是"契约漂移"—— 你一行代码没改，模型名/参数/响应格式已经变了。**
> **本文提炼自（原文档保留不动）**: [[AI模型名停用风险与thinking参数改造方案]]（#58，387 行）、[[AI导购Agent实现详解]]（#32，含排障手册与 4 条生产踩坑）
> **关联（仅互链、不合并）**: [[问题解决--搜索双索引与降级分层]]（该文 §关联 曾预留"未来可并入本类：**#58**、**#32**" —— **本篇就是那次并入**）· [[问题解决--资源治理与流量防护]]（**限流/闸门/预算** 那一半）· [[本地双实例锁验证报告-2026-09-09]]（Mockito 环境限制，与本篇问题 3 同源）· [[问题解决--AI导购模块部署]]（Nacos 双注册 / Dubbo 端口）· [[TODO第三批实现与原理-1]] §二（模型配置可配化）/ §五（13 格实验）已并入本文 §六 / §七

---

## 一、这一类问题的共性（★ 先看这张表，比逐个看个案更重要）

| 维度 | 共性 | 本项目实例 |
|---|---|---|
| **本质** | 调用方与模型之间没有编译期契约，**只有"运行时的口头约定"** | 模型别名、`thinking` 参数、`thinking` 与 `response_format` 互斥、`tool_calls` 分片 |
| **为什么危险** | 漂移**不会立刻报错** —— 官方停用模型名有"兼容期"，兼容层还在就一切正常 | `deepseek-chat` 公告 2026-07-24 停用，**2026-09-10 实测仍 200** |
| **为什么会"静默"** | 设了**不生效的参数**不报错（死参数）；**零引用**的配置不报错（死配置）；**永不执行**的代码不报错（死代码） | `temperature` 在思考模式下是死参数 · `compare-model` 零引用 · `DeepSeekAiClient.embed` 无注入点 |
| **为什么难定位** | 现象是"偶发降级/偶发 500/回答正确但卡片空"，**不是崩溃** | 4 条生产踩坑全部如此 |
| **共性解法** | ① 用**实测**确认契约（而不是读文档/读代码猜）；② 判"生效值"必须 **yml + 代码默认值一起看**；③ 降级**分层**且**在写第一个字节之前决策**；④ 排障看**定量对照**，不看到 ERROR 就改代码 | 见第二~四节 |

> 💡 **一句话记忆**：别的地方怕的是 500；**这一块最怕的是 200 —— 但答案是错的、或者链路其实没生效。**

---

## 二、问题 1：#58 契约漂移 —— 生产依赖一个"已公告停用"的模型名

### 现象

生产 `csmall-ai` 容器里跑的 chat 模型是 **`deepseek-chat`** —— 一个官方**已公告停用**的别名（[官方更新日志 2026-04-24](https://api-docs.deepseek.com/zh-cn/updates)：`deepseek-chat` / `deepseek-reasoner` 将于 **2026-07-24** 停止使用）。名义停用日已过，**兼容层还没撤，所以一切"正常"**。

### 排查（怎么把"生效值"钉死）

| 步骤 | 做法 | 结果 |
|---|---|---|
| ① 看**真实**生效值 | 解包**生产容器**的 `/app/app.jar` → `BOOT-INF/classes/application.yml` | `chat-model: deepseek-chat`（第 26 行）|
| ② 排除"prod 覆盖" | 确认 `application-prod.yml` **未定义 `cooxiao.ai` 段** | 直接继承 jar 内默认值 → **生产确实在跑它** |
| ③ 找**改动来源** | `git log` 定位 | commit `c0d7209`（2026-09-08）：`- chat-model: deepseek-v4-flash` → `+ chat-model: deepseek-chat` |
| ④ 探测兼容层还剩多少 | 向 `api.deepseek.com` 发 `max_tokens=1` 最小请求（只读、成本可忽略） | 5 个模型名**全部 200**；且响应里的 `model` 暴露了真相 |

**④ 的附带发现（决定性的）**：

| 请求的 model | HTTP | 响应里的 `model` | 返回 `reasoning_content`？ |
|---|---|---|---|
| `deepseek-chat` | 200 | `deepseek-flash` | **无**（非推理）|
| `deepseek-v4-flash` | 200 | `deepseek-flash` | **有** |
| `deepseek-reasoner` | 200 | `deepseek-flash` | 有 |
| `deepseek-v4-pro` | 200 | `deepseek-v4-pro` | 有 |

⇒ **两个别名底层已是同一个 `deepseek-flash`，行为差异只来自"思考模式开/关"** —— 这直接指向"应该用官方显式参数，而不是换模型名"。

> ⚠️ **`GET /models` 不是权威清单**：它只列 `deepseek-flash` 与 `deepseek-v4-pro`，**不含** `deepseek-chat` / `deepseek-v4-flash`，但两者都能调。**不能据它判断可用性**（这一条如果不实测，很容易得出反向结论）。

### 根因（历史上为什么这么改 —— 方向对、手段过时）

`deepseek-v4-flash` 是 **reasoning（思考）模型**，响应 = `reasoning_content`（思考）+ `content`（答案）。它对**重排 / 意图提取**这类 JSON 小任务**过度思考**，且思考量**随 `max_tokens` 水涨船高**（实测：`max_tokens=1000` → 想满 1000；`=4000` → 想满 4000）→ 思考吃满预算 → `content` **为空或被截断** → JSON 解析失败 → 降级。

当时的修法是**按任务类型换模型名**（JSON 任务用非推理的 `deepseek-chat`）。**方向对，但手段过时** —— 它用"换模型名"间接控制"思考开关"，而这正是官方**已提供显式参数**的能力。

### 处置与验证

- 换成官方 **`thinking` 开关**；`tools`（工具轮）一律 `thinking: disabled`（工具选择是结构化决策，不需要长思考）；
- 实测硬约束（13 格契约实验的 A~J）：**`tools` 与 `response_format` 互斥**；**思考模式不带回传 `reasoning_content` 直接 400**；
- 每次改完对照 `AI 模型路由` 日志确认"路由恰好 1 次"（防双 bean/双调用）。

---

## 三、问题 2：#32 Agent 上线后的 4 条生产踩坑（**全部是"不崩但不对"**）

| # | 现象 | 排查 | 根因 | 处置 |
|---|---|---|---|---|
| **1** | 部署后立刻打接口 **503 `No servers available for service: mall-ai`** | 打印请求时刻与启动日志时刻 | `sleep 45` 早于实际启动：本次 **44.7s 启动 + 注册**，请求在 13:13:02 发出，实例 13:13:09 才注册完 → 正好卡在**"已反注册、还没重注册"的空窗** | **改为轮询日志判据 `Started MallAiWebApiApplication`**（或 grep 到再等 5s）；**不要靠固定睡眠** |
| **2** | 偶发 **500**（`AccessDeniedException` + "响应已提交"） | ⭐ **3 次观测 + 3 组定量实验**：① 失败请求到 mall-ai 时**没有 JWT 解析日志**（匿名）；② **经网关 8 次 + 直连 4 次 + 流式并发 6 次 = 18/18 客户端全 200**；③ 并发实验里**客户端 6/6 全成功，日志却仍出现 2 次 `AccessDenied`** | **它是内部 ERROR 派发的次生现象**：某请求先失败（响应已提交，**典型是 SSE 流**）→ Tomcat 转 `/error` → Spring Security **在 ERROR 派发上又跑一遍过滤器链**，而 JWT 过滤器是 `OncePerRequestFilter`（**默认跳过 ERROR 派发**）→ 匿名 → `AuthorizationFilter` 拒绝 `/error` → 噪声 | 登记 **#62**：候选修法 = **放行 `DispatcherType.ERROR`**（属公共 Security 配置，需单独窗口 + 全服务回归）⇒ ✅ **已收口（2026-09-12）**：真实修法是 `.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()` —— **只放行 ERROR 不够**（见 [[问题解决--压测结论的可信性（先证伪干扰项与工具自身）]] §二）+ SSE 收尾容错；**与 #53 可能同源，建议同窗口** |
| **3** | Agent 回答正确但 **`products: []`**（前端没商品卡片） | 对照"回答用了哪些商品"与工具轨迹 | 模型遇到**与历史相似的问题**时**直接引用历史商品作答**，本轮**没调工具** → 没有 hits | ① system 提示词加规则；② **首轮 `tool_choice=required`**（实验 M 证明支持；但**只对"商品意图"用** —— 永远 required 会让"你好"也去检索） |
| **4** | 库存回答有**张冠李戴**风险（模型先猜 `spuId` 去查） | 看工具返回的 observation 里有什么 | `get_stock` 的 observation **没带商品名** → 模型只能靠猜的 id 自圆其说 | 工具先 `getSpuById` 确认，observation 增加 **`spuName`**；SPU 不存在**直接报错**（实测：模型编造的 `spuId=1001` 被工具优雅拒绝 → 审计 `ok=false` → **循环继续**，不炸） |

> **踩坑 2 的方法论价值最高**：**"日志里出现异常" ≠ "客户端失败"** —— 必须先拿**客户端成功率**与**派发类型（ERROR dispatch）**定性，否则会去改完全无关的代码（本例真正的病灶在"SSE 失败后转 `/error`"）。

---

## 四、问题 3：失败面设计 —— **分层降级** + **流式特有的纪律**

### 4.1 六层降级（触发条件 → 行为 → 用户可见）

| 层 | 触发 | 行为 | 用户看到 |
|---|---|---|---|
| **① 工具级** | 工具抛异常 / 未知工具名 / 参数非法 | 转成**可读 observation 回灌**，**循环继续** | 正常回答（模型换个方式或如实说） |
| **② 未写出内容就失败** | 首轮 LLM 调用异常（流式：**尚未写出任何 chunk**） | **降级到旧固定流水线** | 与升级前一致的答案 + 商品卡片 |
| **③ 已写出内容后失败** | 流式回合**中途**断流（已经发过 chunk） | **如实报错收尾，绝不降级** | `event: error` + `event: done`（**不会出现两段回答拼接**） |
| **④ 轮数用尽** | 模型一直在调工具 | 摘掉 tools，追加"请立即作答"再问一次 | 正常回答（基于已有工具结果） |
| **⑤ 预算超限** | `ai:daily_cost` 超 2 元/日 | 入口直接拒绝 | "服务繁忙，请稍后再试。" |
| **⑥ 空答复** | 收敛轮正文为空 | 再问一次；仍空 → 降级话术 | 降级文案（**不返回空**） |

> 💡 **②③ 的分界是整篇设计的核心**：**"已经写给用户的东西不能装没发生"** —— 所以必须在**写第一个字节之前**决定"这条链路还能不能降级"。为此流式路径**缓冲正文**、`products` 事件**早于第一个 `chunk`**（实测行序：`products` 第 7 行 < 第一个 `chunk` 第 16 行）。

### 4.2 把"模型当不可信输入"（不是当函数）

| 边界 | 做法 | 依据 |
|---|---|---|
| **工具权限** | 本期**只暴露 3 个只读工具**，写操作（加购/下单/退款）**一个都不给** | "**不给 AI 犯错的机会，比事后审计便宜**" |
| **参数越界** | 全部在**工具内**收敛（白名单/截断/类型），不信任模型 | 模型输出是自然语言 |
| **轮数/成本** | `agent-max-rounds=3`；每次调用前 `checkBudget()`、每次响应后按 `usage` 记账 | Agent 是"多次调用"→ 记账必须**每轮**都做（成本 ≈ 旧流水线 2×） |
| **结果真实性** | system 提示词 + **只把工具结果拼进上下文** | 让"编造"**没有素材** |

### 4.3 测试策略：必须离线（这条最容易被跳过）

Agent 的正确性主要在 **循环控制 / 事件顺序 / 降级策略** —— 这些**与真实模型无关，只与我们自己的代码有关**；而真实模型**不稳定、要花钱、跑得慢** → 拿它当回归测试等于没有测试。真实模型只用来做**契约实验（13 格）**与**上线验证**。

两个"假件"技巧：
1. **脚本化假 LLM**：按脚本逐轮返回工具轮；可控制"**吐一片再抛异常**"（测 ③ 已写出不降级）、拆两片正文（测分片拼接）、记录每轮 `messages`/`tool_choice`（测回灌与强制首轮）。
2. **动态代理假 Dubbo**：`Proxy.newProxyInstance(接口, ...)` —— 接口有 5 个方法时**只需实现被测的那一个**，接口新增方法也不会让测试莫名编译不过。

> ⚠️ **没有用 Mockito**：`mockito-inline` 的 MockMaker 需要动态 attach（Windows 走命名管道），在本机受限环境**无法初始化** → 手写假件反而更直观。**这是同一类环境限制**，见 [[本地双实例锁验证报告-2026-09-09]]。

### 4.4 排障入口（三步）

1. **看启动日志**：`Agent 双路径开关` + `已注册 AI 工具 N 个` → 开关与工具是否就位；
2. **看工具轨迹**：`docker logs csmall-ai | grep "工具 "` → 模型到底调了没、命中多少条、走没走"放宽兜底"；
3. **看审计明细**：`LRANGE ai:agent:action:{sid} 0 -1` → 每轮**参数 / 耗时 / `ok`**（`ok=false` = 工具失败被兜住了）→ **可完整回放"AI 到底查了什么"**。

---

## 五、问题 4：契约漂移的"另一面" —— 死参数 / 死配置 / 死代码

改这一类问题时，**顺手一定会挖出"设了但没用"的东西**。它们不会报错，但会**误导后来人**（以为调了、以为生效了）：

| 类型 | 实例 | 为什么是"死的" | 处置 |
|---|---|---|---|
| **死参数** | `temperature`（`DeepSeekAiClient:82` / `:131`、`ChatServiceImpl:369` 的 SSE 分支 `0.7`） | 官方：**思考模式下 `temperature` 不生效**（设了不报错）→ 一直是死参数 | 删除或注释说明，避免后人以为"调了温度" |
| **死配置** | `compare-model`（`AiProperties.java:22` + `application.yml:27`） | 全仓 grep `compareModel`/`compare-model` **只命中定义处与 yml 各一次** → **零引用**（`ProductCompareServiceImpl` 实际用 `chat-model`） | ✅ 删除（用户 2026-09-10 确认） |
| **死代码**（✅ **已删除**，见 §六·2.4 第 9 项） | `DeepSeekAiClient.embed / embedBatch`（约 55 行，含 `doEmbed`） | **无任何注入点使用 `AiClient` 的 embed**（`RagServiceImpl:48/84`、`VectorSyncServiceImpl:36/77/148` 都直接注入 `SiliconFlowEmbeddingClient`）；而 `DeepSeekAiClient.embed` 打的是 **DeepSeek baseUrl + 硅基流动的 model + DeepSeek 的 apiKey** → **三样对不上，真被调用必然失败** | 🟡 顺手清理（可延后），或至少 `@Deprecated` + 注明"embedding 请用 `SiliconFlowEmbeddingClient`" |

---

## 六、附：模型配置可配化（原补册 §二，逐字迁入）—— ⚠️ 一个容易踩的隐式坑

### 2.1 问题与现状实测

**诉求**：模型相关配置要能适应**未来模型更名 / 下架** —— 改名不改代码、不重编译。

**现状：半可配。**

| 配置项 | 现状 | 改名能只改配置吗 |
|---|---|---|
| API Key | `${AI_API_KEY:…}` ✅ | ✅ |
| API 地址（可换供应商） | `${AI_API_BASE_URL:…}` ✅ | ✅ |
| **chat 模型** | `chat-model: deepseek-chat` 🔴 **字面量** | ❌ |
| **embedding 模型 / 地址** | 字面量 🔴 | ❌ |
| 向量维度 | `embedding-dimensions: 1024` ✅ 且 `EsIndexInitializer` 真读了它 | ⚠️ 可改，但需重建 ES 索引 |
| 价格 / max-tokens / 温度 / 超时 / 开关 | ✅ | ✅ |

**两个结构性缺口**：
1. **2 处模型名硬编码在代码里**：`ChatServiceImpl:316`（`"deepseek-chat"`）、`:367`（SSE 的 `"deepseek-v4-flash"`）
2. **prod 未覆盖 `cooxiao.ai` 段** → 生产模型名来自 **jar 内的 `application.yml`** → **想换模型必须重编译**（除非靠隐式 relaxed binding）

### 2.2 两层设计

**第 1 层：消灭硬编码 + 模型名外置为环境变量**

```yaml
chat-model:         ${AI_MODEL_CHAT:deepseek-v4-flash}
embedding-model:    ${AI_MODEL_EMBEDDING:BAAI/bge-m3}
embedding-base-url: ${AI_EMBEDDING_BASE_URL:https://api.siliconflow.cn}
```

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

→ 代码只出现 `TaskType.CHAT` / `TaskType.JSON`，**永不出现模型名**
→ **官方改名 = 改一个环境变量 + 重建 `mall-ai` 一个容器**；官方出新档位 = 加一行 yml，代码零改动

> 📌 **落地时的实际形态（与上面草图的差异，2026-09-10）**：
> - 档位层只保留 **`flash` / `pro` 两档**（embedding 不放进档位表，仍是独立标量 `embedding-model` —— 它属于另一个供应商、也不是"思考/非思考"的档位概念）
> - 任务层由 **`AiTask` 枚举**（`CHAT` / `JSON` / `EXPAND` / `AGENT` / `COMPARE`）承载，yml 里 `tasks.<task>` 配 `tier + thinking + temperature + max-tokens`
> - 缺失档位 **启动不报错、调用时报明确错误**（`modelFor()` 抛异常并提示检查 `cooxiao.ai.models`），且启动打印一次"任务→档位→模型 id/思考模式"路由表
> - 细节见 [[AI模型名停用风险与thinking参数改造方案]] §4.5 / §11

### 2.3 ⚠️ 为什么必须用显式占位符（一个容易踩的隐式坑）

`cooxiao.ai.chat-model` 对应的环境变量是 **`COOXIAO_AI_CHATMODEL`**（**去横线**），写 `COOXIAO_AI_CHAT_MODEL`（带下划线）**并不直接映射**。
→ **不能指望 Spring relaxed binding 兜底，必须写显式占位符**（与项目既有 `api-key` / `base-url` 同风格）。

### 2.4 顺带清掉的硬编码清单（9 项）

| # | 项 | 位置 | 处置 |
|---|---|---|---|
| 1 | 模型名 `"deepseek-chat"` | `ChatServiceImpl:316` | → 读配置（档位） |
| 2 | 模型名 `"deepseek-v4-flash"` | `ChatServiceImpl:367` | → 读配置（档位） |
| 3 | `temperature 0.7` | `ChatServiceImpl:369` | → 读配置 |
| 4 | `max_tokens 2000` | `ChatServiceImpl:370` | → 读配置（**2000 偏紧**，见本文 §七实验 D/I） |
| 5 | `temperature 0.3` | `DeepSeekAiClient:82` | → 读配置 |
| 6 | **SSE 自建 body + 自建 Authorization** | `ChatServiceImpl:354-373` | 🔴 **与客户端重复一份请求逻辑** → 收敛为 `AiClient.streamChat(...)`，**模型/思考/温度/max_tokens 只有一处** |
| 7 | 模型名 / embedding 地址字面量 | `application.yml` | → 显式占位符 |
| 8 | `compare-model` 死配置 | `AiProperties:22` + yml | → **删除**（全仓库零引用，实测确认） |
| 9 | `DeepSeekAiClient.embed/embedBatch` 死代码 | `DeepSeekAiClient:180-235` | ✅ **已删除**（无人调用；真调用必失败 —— 打的是 DeepSeek 地址 + 硅基流动模型名） |

> 第 6 条是**结构性问题**：改模型要改两个地方（客户端 + SSE），本次一并收敛，以后只改一处。

> ✅ **落地与验证（2026-09-10）**：**#58 已实施并部署生产** —— 生产实测：路由日志**恰好 1 次**（修双 bean 前是 2 次）、`/ai/search` 5s 返回 AI 重排说明、`/ai/ask` 4s 返回完整推荐、`ai:daily_cost` 0 → **0.002492 元**、`content 为空` / `reasoning` / 4xx 告警均 **0**；**9 项硬编码全部清零** + 新增 2 个测试类（yml→Java 绑定 / 请求体规则）。详见 [[TODO已完成-明细-2026-09-12]] §十四、[[AI模型名停用风险与thinking参数改造方案]] §十一（实施与部署记录）。

### 2.5 面试话术

> "模型这部分我做了**可配化**：模型名全部走**显式占位符**外置到环境变量，代码里一处都不留；再加一层**能力档位**——代码只说'我要 JSON 任务'或'我要对话任务'，档位到实际模型 id 的映射放在配置里。这样官方**改名或者换代，我只改一个环境变量重启一个容器**，不用重编译。这里有个细节：不能指望 Spring 的 relaxed binding，`chat-model` 对应的环境变量是 `COOXIAO_AI_CHATMODEL` 去横线的形式，写带下划线的反而不映射，所以我坚持用显式占位符。"

---

## 七、附：13 格契约实验总表（原补册 §五，逐字迁入）

> 前 10 格是 **#58 / P0** 阶段做的（模型契约、tools 可行性、互斥规则）；**K/L 两格是 P1 阶段补的**（流式 + tools 契约）；**M 是 P1 加固阶段补的**（`tool_choice=required`）。**写代码前先把契约测出来**，是本项最省时间的习惯。

| 实验 | 输入 | 结果 | 结论 |
|---|---|---|---|
| **A** | `thinking: disabled` + json mode | 200，**无 `reasoning_content`**，content 干净 JSON | ✅ 开关从根上生效 |
| **B** | `thinking: enabled` + `tools` + 不带 `reasoning_content` | 🔴 **400**：`The reasoning_content in the thinking mode must be passed back to the API.` | ✅ **400 规则实测复现** |
| **C** | `thinking: disabled` + `tools` + 不带 `reasoning_content` | **200** | ✅ **"工具轮用 disabled"设计成立** |
| **D** | `thinking: enabled` + `tools` + 带 `reasoning_content` | 200（reasoning 103，**content 0**） | ✅ 规则只因缺字段；⚠️ 顺带暴露"思考吃满" |
| **E** | `thinking: disabled` + `tools` + "推荐5000以内的手机" | 200，`finish=tool_calls`，参数 `{"keywords":"手机","budgetMax":5000}` | ✅ **P0 可行性证实** |
| **F** | `tools` + **`response_format: json_object`** | 200 但 **`tool_calls` 为空** | 🔴 **新坑：两者互斥** |
| **G** | `thinking: enabled` + `tools` + 全新提问 | 200，`tool_calls` 1 个 | 推理模式也能调工具，但需维护 reasoning 回传 |
| **H** | **完整两轮循环**（工具轮 disabled）：round1 → 追加工具结果 → round2 | round1 `finish=tool_calls`；**round2 `finish=stop`、`content_len=272`、输出真实推荐表格** | ✅ **P0 的循环端到端会收敛、会出最终答案** |
| **I** | `thinking: enabled` + `reasoning_effort=low/high` | 均 **200**（接受该参数）；但 `max_tokens=120` 时 **reasoning 183/174、content 均为 0** | ⚠️ **参数可用，但思考极耗 token → `max_tokens` 必须留足** |
| **J** | **SSE 流式 + `thinking: enabled`** + `include_usage` | 129 行：**content 分片 38 / reasoning 分片 88** / usage chunk **有** / `[DONE]` **有** | ✅ **现有解析器只读 `delta.content` 与 `usage` → reasoning 分片被安全忽略，SSE 改造无破坏** |
| **K** | **SSE 流式 + `tools` + `thinking: disabled`**（P1 前夜补测） | `finish_reason=tool_calls`；`tool_calls` **20 行分片**，`arguments` 被切成 **19 段**（`""`→`{`→`"`→`keywords`→`"`→…→`}`）；首片带 `id`+`name`，后续只有 `arguments`；**同一响应里先有 13 个 `content` 分片（英文 preamble）再出现 tool_calls** | ✅ 流式工具调用可行的**唯一正确姿势**是"按 `index` 拼接连缀 arguments"（实现依据）；⭐ preamble 的存在证明"**products 之前先缓冲正文**"是必需的，否则前端会先收正文、后收商品 |
| **L** | **K 之后回灌 `role:"tool"` 结果，继续流式** | `content` **112 个分片**（真正逐字）；`tool_calls`=**0**；`finish_reason=stop`；`usage` 1 行 | ✅ 收敛轮天然流式、不再调工具；✅ usage 可记账 |
| **M** | **`tool_choice: "required"`**（P1 加固阶段） | **200**，`finish_reason=tool_calls`，即使输入是"你好"也被强制去搜（`{"keywords":"你好"}`） | ✅ 官方兼容该参数 → 可用于"商品类问题首轮强制检索"；⚠️ 但不能对所有消息都强制（"你好"也会白搜一次） |

**实验带来的 3 个方案修正**：① 工具轮不能与 `response_format` 共存；② SSE 的 `max_tokens: 2000` 应改读配置（思考占大头，实测 J 中 reasoning 分片是 content 的 2.3 倍）；③ `DeepSeekAiClient.embed` 是死代码。
**P1 阶段（实验 K/L）又带来 2 条修正**：④ 流式下 `tool_calls` 的 `arguments` 是**按 index 分段到达**的，必须拼接（不能假设一条分片就是完整 JSON）；⑤ 工具轮会**先吐 preamble 正文**再吐 tool_calls → 流式 Agent 必须**缓冲正文**直到商品列表发出，否则前端事件顺序错乱。

---

## 八、跨问题的共性认知与踩坑清单

1. **按"网络不可靠 + 对方随时会变"来设计 LLM 链路**（超时/重试/预算/降级四件套），不要当成本地函数调用。
2. **契约漂移比崩溃危险**：停用模型名在兼容期内**一切正常** ⇒ 靠"**调用点清单 + 定期实测核对**"，不要等 400 才发现（本项目是**读 jar 内配置**才钉死生效值的）。
3. ⭐ **判"某配置的生效值"必须同时查 yml 与代码默认值** —— 只看 `AiProperties` 会得出"日预算 10 元"的错误结论，实际 yml `daily-budget: 2.0` 覆盖了它（**写这份文档时我自己第一版就判错了，靠 grep `daily-budget` 才发现**）。
4. **判"服务起来了"要用日志判据 + 外部探活，不要固定 sleep**（44.7s 启动会踩 503 空窗）；而且**判据要按服务自适应**（网关主类叫 `MallGatewayWebApi`，不是 `…Application`）。
5. **看到 ERROR 先别改代码**：用"客户端成功率 + 派发类型"定性；本例 18/18 客户端成功却仍有 `AccessDenied`，真病灶在 **ERROR dispatch 重跑过滤器链**。
6. **降级必须在"写第一个字节之前"决策**：流式一旦写出就**不能收回**，所以缓冲 + 事件顺序（`products` 早于首个 `chunk`）是**降级能力的前提**，不是优化。
7. **死参数/死配置/死代码 = 漂移的另一面**：设了不生效**最误导**，零引用**最隐蔽**。改这一块时**顺手 grep 一遍"只有定义没有引用"的东西**。
8. **测试要"可重复、零成本、能测失败路径"**：模型不能进回归测试；用脚本化假件**主动制造**"写一半就失败"。
9. ⭐ **配置可配化必须用显式占位符**：`chat-model` 对应的环境变量是 `COOXIAO_AI_CHATMODEL`（**去横线**），写带下划线的 `COOXIAO_AI_CHAT_MODEL` **并不映射** —— 一旦指望 Spring relaxed binding 兜底，"可配"就会**静默失效**（看着改了配置、实际没生效），所以模型名/地址一律写成 yml 里的**显式占位符**。
10. ⭐ **契约实验要留总表**：13 格（A~M）是"花钱极少、省时间极多"的资产，**必须留在文档里**；否则下次改模型 / 换供应商要**重跑一遍才知道边界**（例如 `tools` 与 `response_format` 互斥、流式 `tool_calls.arguments` 按 index 分片 —— **读官方文档看不出来**）。

---

## 九、面试综述话术（贯穿本类，可直接用）

> "AI 这块我最想讲的不是功能，而是**失败面**。
> 第一层是**契约**：我们不掌握模型侧，所以我把'模型名、thinking 开关、tools 与 response_format 互斥、tool_calls 分片'这些都**实测**了一遍定成契约 —— 其中一条是生产当时依赖的 `deepseek-chat` **已被官方公告停用**，只是兼容期还没撤；我是**解包生产容器的 jar 配置**才把这个'生效值'钉死的，顺便发现两个别名底层已经是同一个模型，真正该用的是官方的 `thinking` 开关。改的过程中还挖出三个'**死的**'东西：思考模式下的 `temperature` 是死参数、`compare-model` 零引用、`DeepSeekAiClient.embed` 没有注入点且三样配置对不上。
> 第二层是**降级**：我做了六层，关键的分界是'**是否已经写给用户**'—— 没写出去就降级到旧流水线，写出去了就**如实报错收尾、绝不降级**，因为已经给用户的东西不能装没发生；为此流式路径专门缓冲正文、把 `products` 事件排在第一个 `chunk` 之前。
> 第三层是**把模型当不可信输入**：只给 3 个只读工具、参数在工具内收敛、轮数用尽就摘掉 tools 强制收口；上线后 4 个坑全都是'不崩但不对'——尤其是那个偶发 500，日志里一直有 `AccessDeniedException`，但我用客户端成功率 18/18 和 ERROR 派发把这个'噪声'定性了，根因是**SSE 失败后转 `/error` 时过滤器链又跑了一遍**，而不是鉴权真的坏了。"

---

## 十、关联文档

- **源文档（保留不动）**：[[AI模型名停用风险与thinking参数改造方案]]（#58 全过程 + 13 格实验 + 话术）· [[AI导购Agent实现详解]]（实现说明书：排障手册 / 4 条踩坑 / 决策清单）· [[AI导购Agent升级方案]]（计划与清单）
- **原理与归档**：[[TODO第三批实现与原理-1]]（§一 选型 / §三 实现 / §五 13 格实验）· [[TODO已完成]]（§十四 #58 · §十六/§十七 #32）
- **同族问题解决文档**：[[问题解决--搜索双索引与降级分层]]（**降级分层**的另一半：检索侧）· [[问题解决--资源治理与流量防护]]（#2+#34 限流 + 并发闸门 + 每用户频控）· [[问题解决--请求校验与静默失效]]（"设了但不生效"的另一种形态）· [[问题解决--AI导购模块部署]]（Nacos 双注册 / Dubbo 端口）· [[问题解决--本地双实例分布式锁验证方法]]（**同一类环境限制**：Mockito inline 无法 attach → 改手写假件；以及"验证方法本身要经得起推敲"）
- **待办**：~~[[TODO文件]] **#62**（`DispatcherType.ERROR` 放行，P2）· **#65**（库存扣减 MQ 链路失效）~~ ✅ **两项均已收口（2026-09-12）**：#62 已修复验收、#65（含 #70 A~E）已部署验收 —— 与本篇无关但同属"不崩但不对"）
