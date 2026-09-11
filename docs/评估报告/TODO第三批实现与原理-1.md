# TODO 第三批实现与原理 · 补册 1（AI 导购 Agent + Python 模拟数据）

> **创建日期**: 2026-09-10
> **状态**: ✅ **#32（AI 导购 Agent：P0 + P1 + 生产加固）全部完成并部署验证**（2026-09-11 收口）—— **#58 已完成并部署生产**、**#32 全阶段已上线**（生产开关 `true`；2026-09-11 复核通过）、**#60 仅评估**；🛠 **#48 待实施**（规范设计定稿 + **09-11 实施前复核 §〇.1 共 9 条** + **第一层造数骨架已落地**）；✅ **#63 已修复并独立复核**（**§九**，验收全绿 + 召回回归无异常）；✅ **#59 已通过充值解决 → #31 前置解除**（实测 200 / 1024 维，**§十**）；✅ **向量链路降级已补**（**§10.4**）+ **Embedding 可替换性加固 P0~P3 已完成**（**§十一 评估 / §十二 实施**）；🧪 **当前 mall-ai 测试：82 项全绿**（56 原有 + `EmbeddingSelfCheckTest` 6 + `RagServiceImplVectorFallbackTest` 6 + `VectorSyncServiceImplDegradeTest` 14）
> **📖 本册同时是"AI 模块升级"的执行记录**：**§三·6/§三·7** 记录 P0/P1 的**真实实现**（与计划骨架的差异、生产挖出的问题与加固均已如实标注）；**§八 小插曲**记录两次**非计划内**的生产事件；**§九/§十**记录 **ES 索引缺陷（#63）的复核与修复** 与 **#31 前置解除（#59）**；**§十一/§十二**记录 **Embedding 可替换性（依赖倒置）的评估与实施**；**逐类实现说明见 [[AI导购Agent实现详解]]**
> **为什么另开一册**: 原 [[TODO第三批实现与原理]] 已 946 行、以跨机集群为主；按用户要求把 **Agent 与模拟数据**这两块"新东西"单独成册，**原册不动**，两册互链
> **用途**: 与三个「实现与原理」正册格式对齐 —— 「**选型/原理 → 本项目设计 → 实测证据 → 疑惑点 → 面试话术**」；**本册的特色是"技术选型过程"被完整记录**（为什么不跟风上框架）
> **关联**: [[TODO文件]] #32/#48/#58/#59/#60/#61/#62/**#63/#64**、[[AI导购Agent升级方案]]（计划与清单）、**[[AI导购Agent实现详解]]（实现说明书：架构/类/取舍/排障/面试底稿）**、[[Python模拟数据与AI并发测试方案]]、**[[商品与秒杀扩容方案]]（#63 修复 + 商品/秒杀扩容的实施方案）**、[[AI模型名停用风险与thinking参数改造方案]]、[[TODO第三批实现与原理]]（正册）、[[TODO已完成]]

---

## 〇、本册全景

| 编号 | 事项 | 本质 | 状态 |
|---|---|---|---|
| **#58** | 模型名停用风险 + `thinking` 开关改造 | **前置**：给 Agent 一个稳定的模型契约 | ✅ **已完成并部署生产**（2026-09-10，见 [[TODO已完成]] §十四）|
| **#32** | AI 导购升级 Agent（Function Calling + ReAct） | 让 LLM 有"动作决策权"，而非只当解析器 | ✅ **全部完成并部署**（P0 `09d22b1`/`0fe1a1e` + P1 `10a4ab0` + 加固 `2fd305d`）；生产开关 `true`、**56 项测试全绿**、2026-09-11 复核通过（同步 200 + 流式 211 事件）；**待办条目已归档** → [[TODO已完成]] §十六/§十七（见 §三·5~§三·7）|
| **#48** | Python 模拟数据 + AI 并发测试 | 造运营数据 + 验证 AI 承载 | 🛠 **规范设计定稿 + 09-11 实施前复核（§〇.1，9 条）+ 第一层造数骨架已落地**（`deploy/scripts/sim/`）；待实施 |
| **#63** 🆕 | **ES 商品索引 mapping 与代码期望不符** | **AI 检索的"地基"是错的**（单字分词 / 品牌过滤恒失效 / 补全恒空 / 无向量字段） | 🔴 **已复核属实，修复中**（2026-09-11 用户要求二次验证）→ **§九** |
| **#64** 🆕 | 秒杀预热两套 `spu_id` 语义冲突 | **同一字段两种含义**（`seckill_spu.id` vs pms `spu_id`） | 🔴 已登记（4/12 秒杀 SKU 从不预热，靠凌晨永久 key 兜住）；**不在本批夹带** |
| **#59/#31** 🆕 | 硅基流动余额 → 向量检索开关 | 外部额度是**功能的前置条件** | ✅ **#59 已解决**（充值 10 元，实测 **200 / 1024 维**）→ **#31 前置解除** → **§十** |
| **#60** | Spring AI 引入评估 | **结论：暂不引入**（前置 = Boot 全站升级） | ✅ 评估完成，仅记录 |
| **插曲** | nginx 静态上游 IP → 全站 502 / 前端 conf 漂移 | **非计划内**：调用方持有"过期地址" | ✅ 已抢修并复盘（见 **§八**）|

**依赖关系**：`#58（模型契约）→ #32-P0 → #32-P1`；**#48 相对独立**（可并行、也可作为给 #32 喂演示数据的下游）。

---

## 一、⭐ 技术选型：Agent 到底怎么写（本册最值钱的一节）

### 1.1 用户的质疑（也是最好的问题）

> "不引入 Spring AI，我们怎么写 agent？**自己造船吗**？"

这个质疑背后是一个常见误解：**把"Agent"等同于"框架"**。

### 1.2 认知前提：Agent 不是框架，是"一个循环 + 一份工具清单"

Function Calling + ReAct 的**本体**只有三十来行：

```java
messages = [system, ...history, user];
for (round = 1; round <= maxRounds; round++) {      // ① 循环（ReAct 的 "Re"）
    r = chatWithTools(messages, TOOLS);             // ② 带 tools 调一次（非流式）
    if (r.toolCalls.isEmpty()) break;               // ③ 模型决定不再调工具 → 收敛
    messages += r.assistantMessage;                 // ④ assistant(含 tool_calls) 原样回灌
    for (tc : r.toolCalls)                          // ⑤ 执行工具（ReAct 的 "Act"）
        messages += { role:"tool", tool_call_id: tc.id, content: tool(tc).run() };
}
answer = streamChat(messages);                      // ⑥ 最后一轮流式吐答案
```

**框架省掉的正是这几十行；框架真正值钱的是周边生态**（多供应商抽象、MCP、向量库、Advisor、评测）—— 而这些我们**要么已有、要么用不到**。

### 1.3 候选方案逐条对比（这才是"选型过程"）

| 方案 | 能否用 | 关键理由 |
|---|---|---|
| **A. 手写薄编排 + OpenAI 兼容 HTTP**（✅ 选定） | ✅ | 零新依赖；与项目"不增加外部依赖"原则一致；**已有基础设施全部复用** |
| **B. Spring AI** | ❌ | 🔴 **卡版本代际**：官方支持 **Boot 3.4.x→1.0.x / 3.5.x→1.1.x / 4.x→2.x**，**Boot 3.3 及更早不在支持范围**；本项目 **Boot 3.2.5 低于下限** → 要接入必须先**全站升级**（见 §1.4） |
| **C. Spring AI Alibaba** | ❌ | 同样卡 Boot 代际，且额外绑定阿里生态 |
| **D. LangChain4j** | 🟡 | 可脱离 Spring Boot starter 使用、**理论上能绕开 Boot 升级**，但（a）仍需版本核实（b）是**新增一个重依赖**（c）本项目场景收益不足以抵消 |

> **结论：没有"既用 Agent 框架、又不升 Boot"的免费午餐。**

### 1.4 "升 Boot 是不是只影响 AI 模块？" —— 影响面实测（决定性证据）

**① 版本声明在根 pom，全体继承**

```
pom.xml（根，artifactId = csmall）
└─ <parent> spring-boot-starter-parent 3.2.5        ← Boot 版本在这里
   <properties>
     spring-cloud.version          = 2023.0.3        ← 必须与 Boot 同代际
     spring-cloud-alibaba.version  = 2023.0.1.2      ← 必须与 Boot 同代际
```

**实测：13 个顶层模块全部 `parent = csmall`**
（mall-ai / ams / common / front / gateway-server / order / pojo / product / resource / search / seckill / sso / ums）
→ **改根 pom 的 Boot 版本 = 整个反应堆一起改，"只改 AI 模块"不成立。**

**② 三件套联动 + 一整套 starter 要跟着动**

| 需同步升级 | 现值 | 目标 |
|---|---|---|
| Spring Boot | **3.2.5** | ≥3.4 |
| Spring Cloud | 2023.0.3 | **2024.0.x** |
| Spring Cloud Alibaba | 2023.0.1.2 | 与目标 Boot 配对的版本 ⚠️ **SCA 发布节奏常滞后 = 首要风险** |
| springdoc / Knife4j | 2.5.0 / 4.5.0 | springdoc 与 Boot **强耦合**，几乎必然要动 |
| MyBatis-Plus / mybatis-spring-boot / PageHelper | 3.5.9 / 3.0.3 / 1.4.7 | 各自换 Boot 3.4+ 兼容版 |
| Dubbo / Seata / Sentinel starter | 3.3.2 / 2.1.0 / BOM | 逐一验证适配 |
| Druid / Redisson / Quartz / 各类 `*-test` | — | 与 Boot 主版本对齐项全要同步 |

**③ 影响面量化**

| 维度 | 数量 |
|---|---|
| 反应堆顶层模块 | **13**（11 个运行服务 + gateway + common/pojo 库） |
| pom.xml | **30+** |
| 需重建部署 | **11 服务 + 1 网关**，跑在**两台机器**（老机 21 容器 / 新机 5 容器） |
| 必做回归 | SSO+网关路由 / 秒杀跨机双实例 / Redis 主从哨兵 / Seata 事务 / Dubbo 全链路 / ES 搜索 / AI 全链路 / Sentinel 规则 / Nacos 注册与配置 |

**④ "只升 AI 模块"技术上能做，但不该做**

理论上可让 `mall-ai` **不继承根 pom**、单独挂一套 Boot 3.4。但：

- `mall-ai` 依赖 **`mall-common` / `mall-pojo` / `mall-product-service`**（均按 Boot 3.2.5 编译）→ 一个反应堆出现**两套 Spring 代际**；fat jar 会用 3.4 的 Spring Framework 覆盖传递依赖，而 `mall-common` 里的 **Security Filter / Redis 配置是按 6.1 编译的** → **运行期 `NoSuchMethodError` 一类风险**
- Nacos/Sentinel/Seata/Dubbo 的 starter 也要为 mall-ai 单独选版本 → **一个项目两套 Spring Cloud Alibaba**，构建与排障复杂度翻倍
- 收益仅是"让一个演示模块用上框架" → **性价比不成立**

### 1.5 决策：**不是造船，是装船舵** 🚢

| 部件 | 处置 | 依据 |
|---|---|---|
| `AiTool` 契约 + `ToolRegistry` | 🆕 新建 ~60 行 | 只需 `name/spec/execute` |
| 带 `tools` 的请求 + 解析 `tool_calls` | 🆕 扩展 `DeepSeekAiClient` ~80 行 | **复用** `concurrencyGuard` / `checkBudget` / `usage` 记账 |
| Agent 循环 `agentBranch` | 🆕 ~100 行 | 最后一轮**复用现有 `streamDeepSeek`** |
| 工具实现 `search_products` 等 | 🆕 ~40 行/个 | **内部复用 `RagServiceImpl`**（检索已存在，零重复） |
| 会话 / 多轮记忆 | ♻️ **已有** | `SessionManager`（Redis TTL 24h） |
| 预算控制 | ♻️ **已有** | `TokenBudgetService`（2 元/天，`doChat` 内已自动记账） |
| 并发闸门 | ♻️ **已有** | `AiConcurrencyGuard`（上限 20，满即降级） |
| 限流 / 频控 | ♻️ **已有** | Sentinel 三组规则 + `AiUserRateLimiter` |
| SSE 流式 + thinking 卡片 | ♻️ **已有** | `DeepSeekAiClient.doStream` / `openSseStream`（原写 `doStreamDeepSeek` 已不存在，09-11 复核修正）+ 前端已有组件 |
| 检索 / 召回 / 重排 | ♻️ **已有** | `RagServiceImpl` / `SearchPipeline` / ES |
| 降级兜底 | ♻️ **已有** | 既有 RAG 快速路径（双路径设计） |

→ **新增 ≈ 300 行**（一半还是 Schema 与工具）。**引擎、油箱、安全阀、驾驶舱、航海日志都已具备。**

> 反向论证：Spring AI 自带 `ChatMemory` / `Advisor` / `VectorStore` 整套，进来后**反而要绕过或适配**我们已有的 Redis 会话、ES 检索、预算闸门 —— **框架净收益被自己的既有设施抵消**。

### 1.6 诚实的代价（不吹）

| 不用框架会失去 | 影响 | 缓解 |
|---|---|---|
| 现成工具编排 | 自己写循环 + 解析 | 就是那 ~150 行，且**已被实验 E/C/H 验证** |
| 多供应商无缝切换 | 换供应商要改请求体 | `base-url` + 模型占位符已可配；DeepSeek/硅基流动均为 **OpenAI 兼容协议** → 配置层可换；只有换**非兼容协议**才需改客户端 |
| 结构化输出框架 | 自己校验参数 | **本来就必须做**：JSON Schema 声明 + **服务端二次校验**（不能只信模型） |
| 可观测 | 自己记 | **已有**：`usage` 记账 + SkyWalking + 日志 |
| 生态（MCP / 向量库 / Advisor / 评测） | 用不到 | 项目不需要 |

### 1.7 不锁死：将来迁 Spring AI 的映射路径

| 本次设计 | Spring AI 等价物 |
|---|---|
| `AiTool` 接口 | `@Tool` 注解 / `FunctionCallback` |
| `tasks.json`（档位 + 思考模式） | `ChatOptions` / `ToolCallingChatOptions` |
| `agentBranch` 循环 | `ChatClient` + `ToolCallingAdvisor`（或**直接删除**） |
| `SearchProductsTool.execute` | **一行都不用改**（业务实现与框架无关） |

→ 迁移是**替换实现**，不是重写业务，**本次投入不浪费**。

### 1.8 面试话术

> "Agent 我用的是 **OpenAI 兼容的 Function Calling 手写编排**，没上 Spring AI —— **不是不会用，是这个项目不该用**。Spring AI 官方支持代际是 Boot 3.4/3.5 对 1.0/1.1、Boot 4 对 2.x，**3.3 及更早不在支持范围**；我这边是 Boot 3.2.5，而 Boot 版本声明在**根 pom 的 parent**里、**13 个模块全体继承**，接它就要连带升 Spring Cloud 2023.0.3→2024.0.x 和 Spring Cloud Alibaba，还得把 springdoc/MyBatis-Plus/Dubbo/Seata 全套跟着换 —— **那是全站框架升级，不是加个功能**。
> 而 Agent 本体其实就是一个循环加一份工具清单，三十来行；我这边**会话、预算、并发闸门、SSE、检索、降级全都是现成的**，真正要写的只有工具契约 + 一次带 tools 的调用 + 循环。所以我做的是**框架无关的薄编排**：`AiTool` 接口、能力档位配置、任务到模型的映射 —— 将来真要迁 Spring AI，工具实现一行都不用改。"

---

## 二、技术选型：模型配置可配化（抗更名 / 下架）

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
| 4 | `max_tokens 2000` | `ChatServiceImpl:370` | → 读配置（**2000 偏紧**，见 §五实验 D/I） |
| 5 | `temperature 0.3` | `DeepSeekAiClient:82` | → 读配置 |
| 6 | **SSE 自建 body + 自建 Authorization** | `ChatServiceImpl:354-373` | 🔴 **与客户端重复一份请求逻辑** → 收敛为 `AiClient.streamChat(...)`，**模型/思考/温度/max_tokens 只有一处** |
| 7 | 模型名 / embedding 地址字面量 | `application.yml` | → 显式占位符 |
| 8 | `compare-model` 死配置 | `AiProperties:22` + yml | → **删除**（全仓库零引用，实测确认） |
| 9 | `DeepSeekAiClient.embed/embedBatch` 死代码 | `DeepSeekAiClient:180-235` | ✅ **已删除**（无人调用；真调用必失败 —— 打的是 DeepSeek 地址 + 硅基流动模型名） |

> 第 6 条是**结构性问题**：改模型要改两个地方（客户端 + SSE），本次一并收敛，以后只改一处。

> ✅ **落地与验证（2026-09-10）**：**#58 已实施并部署生产** —— 生产实测：路由日志**恰好 1 次**（修双 bean 前是 2 次）、`/ai/search` 5s 返回 AI 重排说明、`/ai/ask` 4s 返回完整推荐、`ai:daily_cost` 0 → **0.002492 元**、`content 为空` / `reasoning` / 4xx 告警均 **0**；**9 项硬编码全部清零** + 新增 2 个测试类（yml→Java 绑定 / 请求体规则）。详见 [[TODO已完成]] §十四、[[AI模型名停用风险与thinking参数改造方案]] §十一（实施与部署记录）。

### 2.5 面试话术

> "模型这部分我做了**可配化**：模型名全部走**显式占位符**外置到环境变量，代码里一处都不留；再加一层**能力档位**——代码只说'我要 JSON 任务'或'我要对话任务'，档位到实际模型 id 的映射放在配置里。这样官方**改名或者换代，我只改一个环境变量重启一个容器**，不用重编译。这里有个细节：不能指望 Spring 的 relaxed binding，`chat-model` 对应的环境变量是 `COOXIAO_AI_CHATMODEL` 去横线的形式，写带下划线的反而不映射，所以我坚持用显式占位符。"

---

## 三、Agent 设计（#32）

### 3.1 双路径架构（与现有 RAG 共存，不是推翻重做）

```
对外接口（前端无感）：/ai/ask、/ai/chat/send、/ai/chat/stream ← 全不变
                    ChatServiceImpl
    简单问题 ──→ ① 快速路径（现有）：意图 → RAG → 生成    （不调 LLM 决策 = 便宜）
    复杂问题 ──→ ② Agent 路径（新增）：LLM 决策
                    ├ search_products（内部 = 现有 RAG）
                    ├ compare_products / get_stock …
                    └ 最多 3 轮 ReAct → 最终流式生成
```

- 入口开关 `cooxiao.ai.agent-enabled`（默认 `false`，灰度，与 `embedding-enabled` 同套路）
- **降级链**：Agent 异常 → 回退快速路径 RAG → 再失败纯对话/降级文案（**永远有兜底**）

### 3.2 关键设计约束（来自实测，不是猜的）

| 约束 | 依据 |
|---|---|
| **工具轮 `thinking: disabled`** | 实测 C：`thinking: disabled` + tools + 不带 `reasoning_content` → **200**；而 enabled 时 **400**（实测 B）→ **天然绕开官方的 reasoning 回传规则** |
| **工具轮绝不带 `response_format`** | 实测 F：带 `json_object` 时模型**直接输出 JSON、不再调工具**（`tool_calls` 为空） |
| **循环只放非流式路径**，最后一轮再复用 SSE | LLM 调用有**两条独立路径**；流式下解析 `tool_calls` 分片收益为零（⚠️ **P0 实际做得更保守：连"最后一轮复用 SSE"都没做** —— 见 §3.6 偏差①）|
| **工具类必须放 `...ai.service.impl` 同包** | `RagServiceImpl.intentSearch/buildContext/buildRelatedProducts` 是**包私有**方法 |
| **`max_tokens` 要留足** | 实测 D/I：思考模式下 `max_tokens=120` 被思考**全部吃光 → content 为空** |

### 3.3 工具集与边界

| 级别 | 示例 | 策略 |
|---|---|---|
| 🔵 只读 | 检索 / 比价 / 查库存 | 登录即可，AI 自动执行 |
| 🟡 写操作 | 加购 / 改订单 | **AI 只"建议"，用户点确认才执行**（human-in-the-loop） |
| 🔴 高敏 | 退款 / 改价 / 发券 | 角色限制 + 双重确认 + 审计，**或干脆不给 AI 这个工具** |

**工具级/行为级边界（代码层强制）**：白名单 / 参数 JSON Schema + **服务端二次校验** / **轮数上限 3** / 预算复用（每轮记账）/ Sentinel 限流 / **结果真实性**（只能基于工具返回）/ 角色映射 / **动作审计**（Redis List：`ai:agent:action:{sessionId}`，`LTRIM 0 49` + TTL 7d —— ⚠️ **mall-ai 无任何数据库栈，落 DB 不可行**）/ 失败降级。

### 3.4 代码骨架（**计划稿**，用本项目真实类名 —— 落地实现与它有差异，见 §3.6）

```java
public interface AiTool {                                   // ① 框架无关的工具契约
    String name();
    Map<String,Object> spec();                              // OpenAI function schema
    String execute(Map<String,Object> args) throws Exception;
}

@Component
public class SearchProductsTool implements AiTool {          // ② 复用现有 RAG，零重复
    @Autowired private RagServiceImpl ragService;            // 同包（service.impl）
    public String name() { return "search_products"; }
    public String execute(Map<String,Object> a) { /* SearchIntent → intentSearch → buildContext */ }
}
```

```java
// ③ 一次带工具的非流式调用（关键：tools + thinking:disabled，且绝不加 response_format）
public ToolRound chatWithTools(List<Map<String,Object>> messages) {
    concurrencyGuard.acquire("agent");
    try {
        var body = Map.of("model", tierOf(AiTask.JSON), "messages", messages,
                          "tools", toolRegistry.specs(),
                          "thinking", Map.of("type","disabled"));   // ← 实测可绕开 400
        /* … usage 记账照旧 … */
    } finally { concurrencyGuard.release(); }
}
```

### 3.5 分阶段与执行清单

- ✅ **P0（~0.5~1 天）已完成并部署（2026-09-10）**：`chatJson`/档位配置 → 消息类型加宽 `Map<String,Object>` → `chatWithTools` → `ToolRegistry` + `search_products` → `sendWithAgent` 循环 → 生产 curl 实测**触发工具调用并两轮收敛**（见 §3.6 末尾的部署验证）
- **P1（~2~3 天）**：`compare_products` / `get_stock` → Redis 动作审计 → 每轮 SSE `thinking` 事件（前端已有组件，**无需改前端**）→ 预算/闸门回归 → 工具失败自动降级
- **P2（可选）**：框架化（见 §一）/ 审计 DB 化
- **回滚**：`agent-enabled: false` 重启一个容器即回现状；改动**只落在 `mall-ai`**，无表变更、无 ES mapping 变更

> 📄 完整方案（差距清单 / 9 条边界 / 文件级清单 / 验证 / 回滚）见 [[AI导购Agent升级方案]]

### 3.6 🛠 实际实现（P0 代码，2026-09-10 完成 · 提交 `09d22b1`）

> §3.4 是**计划骨架**（当时尚未通读全部代码）；下面是**真正落地的形态**。差异一律如实标出 —— 记录的价值在于"实际怎么做的"，而不是"当初打算怎么做"。

**新增 6 个类 / 改 4 个类**（全部在 `mall-ai`）

| 类 | 职责 | 与计划骨架的差异 |
|---|---|---|
| `client/AiToolCall` | `tool_calls` 元素；`arguments` 是**字符串形式的 JSON** → `args()` 二次解析（非法 JSON 返回空 Map，交给工具自身校验）| 计划里没单独建类 |
| `client/AiToolRound` | 一轮工具响应：`content` + `List<AiToolCall>` + **`assistantMessage`（可直接回灌）** | ⭐ **计划缺这一块**：回灌消息必须含 `tool_calls` 原样结构，且**刻意不带 `reasoning_content`**（工具轮 `thinking=disabled` 本就没有；若哪天改成开思考，**必须回传否则接口 400**）|
| `AiClient.chatWithTools(messages, tools)` | 接口方法（`tools` 由调用方传入）| 计划签名是 `chatWithTools(messages)` |
| `service/impl/AiTool` | 契约：`name()` / `description()` / `parameters()`（JSON Schema）/ `execute(Map)` | 计划写的是 `spec()`；**`description` 单独一个方法**，因为它直接决定"模型会不会用、用得对不对" |
| `service/impl/AiToolResult` | `observation`（给模型看）**+ `hits`**（原始 ES 文档，**只给 Java 侧转 VO**）| ⭐ **计划缺这一块**：解决"工具结果既要喂模型、又要出前端商品列表"的双向需求 |
| `service/impl/ToolRegistry` | 构造器注入 `List<AiTool>` **自动收集**（新增工具不必改注册表）；**同名工具启动期直接抛异常** | 计划只是 `Map<String,AiTool>`；**同名 = "模型点了 A、执行的却是 B"的静默故障，必须炸在启动期** |
| `service/impl/SearchProductsTool` | `search_products`：`SearchIntent → intentSearch → observation`，严格条件无命中时去掉价格/品牌兜底一次 | 计划写 `→ buildContext`（散文）；**落地改用紧凑 JSON：同信息 token 约为散文的 1/3，而 Agent 每轮都要带全历史 → 省下来的是复利** |
| `config/AiTask` | 新增 **`AGENT("agent")`** 任务档（`thinking:false`、`max_tokens:1000`）| 计划未提 |
| `DeepSeekAiClient` | `buildToolBody = buildBody(AGENT) + tools + tool_choice:auto` —— 复用"**请求体只在一处定义**"的收敛成果，因此**天然不会带 `response_format`** | 计划是另写一段 body（那样迟早会与主流程漂移）|
| `ChatServiceImpl` | `sendWithAgent(...)` + `sendWithPipeline(...)`（把原 `send()` 主体抽出来当**兜底**）| 见下方偏差①② |

**⭐ 三条关键实现决策（讲这些比讲"我写了 300 行"值钱）**

1. **模型给的参数一律不可信** → 代码层收敛：`sortBy` 白名单（非法**直接忽略、绝不拼进 ES 查询**）、负数预算丢弃、**上下限颠倒自动交换**（模型的常见错误，纠正比报错好）、关键词/品牌长度截断。
2. **严格条件无命中时"放宽兜底 + 如实告知"**：去掉价格/品牌再查一次，并在 observation 里写明"已放宽条件" —— 否则模型会**以为这就是严格匹配的结果**，继续误导用户。
3. **轮数用尽 → 摘掉工具强制收口**：不抛错、也不把"超出轮数"这种内部细节抛给用户，而是追加一句"请立即基于以上信息给出最终推荐，不要再请求调用工具"再问一次。

**🧪 离线测试（计划里没有，落地时补的）**：`ChatServiceImplAgentTest` 用**假 LLM 脚本化多轮**锁死 6 条路径 —— ①一轮工具 + 收敛（**断言 `assistant.tool_calls` 与 `role=tool` 的 observation 真的出现在第 2 轮请求里**）②轮数用尽强制收口 ③正文为空兜底 ④未知工具 ⑤异常降级回固定流水线 ⑥开关关闭完全不碰 Agent。
> **为什么必须离线**：Agent 的正确性主要在**循环控制**，与真实模型无关；而真实调用不稳定、要花钱、跑得慢 → 拿它当回归测试等于没有测试。**mall-ai 模块 26 项测试全绿**（请求体规则 8 / 工具注册 2 / 检索工具边界 7 / 配置绑定 3 / Agent 循环 6）。

**⚠️ 与计划的偏差（如实记录）**

| # | 计划 | 实际 | 原因 |
|---|---|---|---|
| ① | 最后一轮**复用现有 SSE** 输出 | **P0 只覆盖同步 `/ai/chat/send`**；SSE（`/ai/chat/stream`）**仍走旧流水线** | "流式 + 工具轮"要处理"工具调用发生在流里"的复杂情形，放 P1 更稳 |
| ② | `agent-enabled: false` 写死 | 改为 **`${AI_AGENT_ENABLED:false}` + compose 注入** | 让开关"**改 `.env` + recreate 即生效，不用重编译 jar**"；回滚更快（三级：关开关 ~45s / 换回 jar / 连 compose 一起回）|
| ③ | `spec()` 返回 schema，`execute` 返回 String | `parameters()` + `execute → AiToolResult` | 见上表（回灌与出前端是两条数据流，混在 String 里迟早出错）|

> 📋 **P0 部署指令**：`work/部署指令-step2-agent-p0.md`（**阶段 A 只换 jar 验"零回归" → 阶段 B 开开关验 Agent**；jar MD5 `5193b258f0725fd729d261c482659b19`）

#### ✅ P0 生产部署与验证（2026-09-10 当晚完成，两阶段）

| 阶段 | 做了什么 | 结果 |
|---|---|---|
| **A 零回归** | 只换 jar + 注入开关（`AI_AGENT_ENABLED=false`）| jar md5 落位一致；启动日志 `agent-enabled=false`；`/ai/search` 返回商品、`/ai/ask` 返回完整推荐；`content 为空`=0、**真实 4xx=0** |
| **B 开启 Agent** | `.env` 改 `true` + `docker compose up -d mall-ai`（**不用重编译**）| 启动日志 `agent-enabled=true`；问"我想买 5000 以内的手机" → `工具 search_products：keywords=手机, price=null~5000.0（已放宽=false）→ 命中 4 条` → `Agent 第 1 轮：调用工具 1 次` → **`Agent 第 2 轮收敛`**（413 字，带表格的 3 款推荐）|
| **兜底复验** | 独立问一个**库里没有的品类**："有没有 1000 以内的单反相机" | `已放宽=true`（去价格兜底生效，命中 9 条）→ 回答**明确说明"整个商品库没有单反相机这一类目"**并给替代思路（拍照强的手机），**没有编造商品** —— 正是"observation 带 `note: 已放宽`"的设计目的 |

**两个值得写进面试的细节（真实输出，非设想）**
1. **模型会主动剔除误召回**：ES 全文检索把"联想天逸510S（台式机）"召回到"手机"结果里，模型的回答里自己加了一句"**（搜索结果里还有一款联想天逸510S，但它是台式机，不是手机，已帮你排除）**" —— 工具只负责给候选，**判断交给模型**，这正是 Agent 相对"固定流水线"的可见收益。
2. **查不到就说查不到**：兜底放宽后依然没有单反相机，模型**没有顺着"放宽结果里有 9 条"硬推荐**，而是如实说明类目缺失 —— 说明"工具结果 + 明确告知已放宽"这套上下文设计是有效的。

**同时确认（避免误判为本次引入）**：启动日志里有 1 条 Dubbo ERROR（`Failed register interface application mapping for …ISpuSyncService`，error code 5-10 = 配置中心未接）—— 这是**项目既有特征**：**未改动**的 `csmall-product` 里有 **7 次**、`csmall-order` 1 次（09-09 启动即有），且 mall-product 已成功发现 mall-ai 的 provider（`Available Invokers : 172.18.0.9:20880`）、TCP 直连通 → **无功能影响**，只是启动噪声。

**预算**：`ai:daily_cost:2026-09-10` 正常累加（多次往返测试后 0.010946 元，TTL 到次日零点）；**Agent 单次约为旧路径 2~2.5 倍**（工具轮 + 收敛轮）。**回滚**：改 `.env` 为 `false` + `docker compose up -d mall-ai`（~45s，**无需换 jar**）。

### 3.7 🛠 P1 实际实现（2026-09-10 完成并部署 + 生产加固，2026-09-11 复核）

| 部件 | 做法 | 关键取舍 |
|---|---|---|
| **`AiClient.streamChatWithTools(messages, tools, onContentChunk)`** | 抽出 **SSE 公共管道** `openSseStream(body, onData)`（取流/断连/记账/容错只写一处），`doStream` 与它共用；正文实时回调，`tool_calls` **按 index 拼接连缀** `arguments`，最后组装成 `AiToolRound` | 依据**实验 K**：arguments 被切成 19 段；首片才带 `id`/`name` |
| **`compare_products` 工具** | 走 **Dubbo** `IForFrontSpuService.getSpuById`，只返回事实（价格/品牌/分类/销量/库存/标签/标题），并把 SPU 映射成 **ES 文档形状**塞进 `hits` → **对比也能出前端商品卡片** | ⭐ **没有复用 `ProductCompareServiceImpl.compare(...)`**：它内部会再调一次 LLM 生成总结 → 一次工具调用变两次计费 + 多占一个并发闸门槽，且违反"工具只给事实、判断留给收敛轮" |
| **`get_stock` 工具** | 走 **Dubbo** `IForFrontSkuService.getSkusBySpuId`，返回 SKU 级库存 + 总库存；⚠️ 说明与 observation 都写明"**只含常规库存、不含秒杀库存**" | 库存是事务数据，**不能信 ES**（异步同步不准）；不返回 hits（卡片由检索/对比工具负责） |
| **`AgentActionAuditor`（Redis 审计）** | `ai:agent:action:{sessionId}` List：`LPUSH` 一条 `{ts,round,tool,args,hits,costMs,ok}` → `LTRIM 0 49` → `EXPIRE 7d`；**写失败只记 WARN** | mall-ai 无数据库栈 → 落 Redis；审计"尽力而为"，绝不因审计把对话搞挂 |
| **流式 Agent（`sendStreamWithAgent`）** | 事件顺序**与旧流水线完全一致**（thinking → products → sessionId → chunk* → done）→ **前端零改动**；`products` 发出前**先缓冲正文**，之后实时转发；每轮工具调用发一条 `thinking` 进度文案（前端已有卡片） | 依据**实验 K 的 preamble**；常见路径（1 轮工具 + 1 轮作答）里作答正文是**真正逐字流式**的 |
| **降级策略（分层）** | ① 未写出任何内容就失败 → **降级到旧流水线**；② 已写出内容后失败 → **如实报错收尾，绝不降级**（否则两段回答拼接）；③ 工具异常/未知工具 → 转 observation 让模型自己决定 | 单测把两种情况都钉死（见下） |

**测试（离线，零成本）**：`ChatServiceImplAgentTest` 扩到 **15 项**（新增流式：products 早于 chunk、无工具也发空 products、缓冲未写出可安全降级、已写出后失败不降级、轮数用尽流式收口、thinking 进度文案）；新增 `CompareProductsToolTest` 8 项、`GetStockToolTest` 7 项（含**参数不可信**：非法/负数/字符串 id、去重封顶、provider down 不炸链路）。**mall-ai 模块 50 项测试全绿**。

> 📋 **P1 部署**：work/部署指令-step3-agent-p1.md；**部署后验证与加固记录见下**；**逐类实现说明见 [[AI导购Agent实现详解]]**（架构/每个类/取舍/排障/面试底稿）。

#### ✅ P1 生产验证 + 3 处加固（2026-09-10 当晚）

| 验证项 | 结果 |
|---|---|
| 流式 Agent | **249 个 chunk** 逐字流式；`event: products`（行 7）**早于**第一个 `event: chunk`（行 16）✓ 事件顺序与旧契约一致 |
| `compare_products` | `请求 2 个，取到 2 个（缺失 0）`；重跑 2/2 均 200 ✓ |
| `get_stock` | `spuId=3（小米 14 Pro）→ 2 个 SKU，总库存 100` ✓ |
| Redis 审计 | `LRANGE ai:agent:action:{sid} 0 -1` 有 `{ts,round,tool,args,hits,costMs,ok}`，TTL 7 天量级 ✓ |
| 预算 | `ai:daily_cost` 正常累加（多次往返后 ≈0.03 元）✓ |

**本轮挖出的 3 个真问题（已修并复验，56 项测试全绿）**

| # | 现象 | 根因 | 修复 | 复验证据 |
|---|---|---|---|---|
| 1 | 部署后立刻打接口 **503** `No servers available for service: mall-ai` | `sleep 45` 早于实际启动（44.7s 启动 + 注册）→ 落在"反注册 ↔ 重注册"空窗 | 部署手册改为**轮询 `Started MallAiWebApiApplication`**（不是 Agent 代码问题） | 第 4 轮部署按新写法执行，全程无 503 ✓ |
| 2 | 某轮回答正确但 **`products: []`**（前端没卡片） | 模型遇到与历史相似的问题**直接引用历史商品作答**，本轮没调工具 → 没有 hits | ①system 提示词加"即使与历史相似也要重新核对 / 没有工具数据不要给具体商品"；②**首轮 `tool_choice=required`**（商品意图关键词命中时，实验 M） | 日志 `Agent 首轮强制调用工具（命中商品意图关键词「买」）`；`products` **4 件、首项"小米 14 Pro"**（不再为空）✓；反向验证 `你好，你能做什么` **不触发** ✓ |
| 3 | 库存回答存在**张冠李戴风险** | `get_stock` 只回 SKU 数据、**不带商品名** → 模型先猜 spuId 再按提问里的商品名作答 | 工具先 `getSpuById` 确认，observation 增加 **`spuName`**；SPU 不存在直接报错 | 日志 `工具 get_stock：spuId=3（小米 14 Pro）→ 2 个 SKU，总库存 100` ✓；模型编造的 `spuId=1001` 被优雅拒绝（审计 `ok=false`，循环继续）✓ |

> **另有偶发 500**（`AccessDeniedException` + "响应已提交"）—— 经 3 次观测 + 3 组定量实验**已定性**：**不是 Agent 逻辑问题**（经网关 8 次 + 直连 4 次 + 流式并发 6 次 = **18/18 客户端全 200**；且并发实验里客户端全成功、日志仍出现 2 次 AccessDenied → 它是**内部 ERROR 派发的次生现象**）。
> 完整证据与两条候选修法见 **[[TODO文件]] #62**，逐类说明见 [[AI导购Agent实现详解]] §10.3。

---

## 四、Python 模拟数据设计（#48）

> 🔄 **2026-09-11 实施前复核已更新 → 本章为 09-10 的"设计过程记录"，实施请以方案文档为准**
> 实施前逐条实测 + 读码又查出 **9 条**（mock 注入变量名不生效 / Agent 已上生产但 mock 无 `tool_calls` / 每用户频控 10·60s 污染并发结论 / Sentinel `ai-chat=5` 冲突 / mock 单线程 `HTTPServer` 自己是瓶颈 / 新机无 pymysql 且被 PEP 668 拦 …）→ 见 **[[Python模拟数据与AI并发测试方案]] §〇.1**，含两项决策：**生产 + 临时放开限流**、**Agent 双链路各压一遍**。

### 4.1 为什么必须有"隔离层"（三类**不可逆**污染，实测）

| 类别 | 实测现状（2026-09-10；库存口径 09-11 复核修正） | 造数后果 | 删用户能还原吗 |
|---|---|---|---|
| **累加计数器** | `pms_spu.sales`：81 / 1 / 1 … | 下单/秒杀**累加** | ❌ 没有"谁贡献多少"的记录 |
| **真实库存** | `pms_sku` **38 SKU / 合计 stock 1456 / max 100（1 个已 0）**；`seckill_sku` **12 条 / 合计 seckill_stock 822 / max 150（1 个已 0）** | 下单扣减 | ❌ |
| **Redis 状态** | `mall:seckill:sku:stock:*` **12 个** | 秒杀扣预热库存 | ❌ |
| 可追踪实体 | `ums_user` 110 / `oms_order` 86 / `success` 58 | 新增记录 | ✅ 可按登记精确删 |

**初稿的 5 个缺陷**（逐条实测确认）：
1. 🔴 **清理 SQL 顺序错误** —— 先 `DELETE ums_user`，再按 `username LIKE 'test_sim_%'` 查 user_id 删订单 → 子查询已空 → **订单删不掉**
2. 🔴 **前缀删不掉不可逆污染**（上表三类）
3. 🟠 **Redis 清理想按 pattern 全删** → 会**误伤真实用户**的购买标记
4. 🟠 **全库 0 个外键**（实测）→ 删除顺序无数据库保护，漏表即静默残留；含 `user_id` 的表实测 **7 张**
5. 🟠 **mock LLM 未实现 SSE 格式** → SSE 解析在 **`DeepSeekAiClient.openSseStream:349`**（`line.startsWith("data: ")`；原写的 `ChatServiceImpl.doStreamDeepSeek` **已不存在**，09-11 复核修正），普通 JSON **收不到任何 chunk**

### 4.2 选型（本轮"选型过程"的第二处记录）

| 方案 | 做法 | 规范度 | 成本 | 评价 |
|---|---|---|---|---|
| **A. 影子库 / 影子表** | 造数写独立 schema，服务切数据源 | ★★★★★ | 🔴 极高（11 个服务表名散在 Mapper） | ❌ 不划算 |
| **B. 快照回滚** | dump → 造数 → 事后整库还原 | ★★★★☆ | 🟢 低（**项目已有 #29 备份 + #47 恢复演练**） | ✅ 兜底主线 |
| **C. 影子登记表** | 独立 schema 记每个被创建实体 | ★★★★☆ | 🟢 低（**不改业务库表结构**） | ✅ 清理主线 |

**决策 = C + B**：登记表驱动**精确清理**（逆序 / 分批 / 幂等 / dry-run），**不可逆字段交给快照整库还原**（**不做"减回去"的补偿运算**——累加值易算错且并发不安全）。

> 这正是"影子表"思想在数据层的等价物：**先存真身，再在真身上动作，事后整体还原**。

### 4.3 影子登记表 + 清理算法

```sql
CREATE DATABASE IF NOT EXISTS cs_mall_sim DEFAULT CHARACTER SET utf8mb4;   -- 实测：尚不存在
CREATE TABLE cs_mall_sim.sim_batch  ( batch_id PK, started_at, dump_file, status, … );
CREATE TABLE cs_mall_sim.sim_entity ( id PK, batch_id, db_name, table_name, pk_value, user_ref, … );
```

**删除顺序（子 → 父，实测真实表名）**：
`oms_order_item` → `seckill.success` → `seckill_message_retry` → `oms_payment_record` → `oms_cart` → `oms_order` → `res_upload_record` → `ums_login_log` → `ums_user`

**Redis**：按登记的用户 id **精确拼 key 删除**（`reseckill` / `ordered` / `orderLock`），**禁止 `--scan --pattern` 全删**。

### 4.4 mock LLM 与内网压测

| 要素 | 正确做法 |
|---|---|
| mock 放哪 | **新机内网** `172.29.193.240:9999`（容器内 `127.0.0.1` = 容器自身，**不可达**） |
| 怎么接 | **compose override** 注入 **`AI_API_BASE_URL`** 环境变量（⚠️ 2026-09-11 复核修正：本节原写的 `COOXIAO_AI_BASEURL` **不生效**，yml 实际读 `${AI_API_BASE_URL}`，且 compose 的 `mall-ai` 当时**未透传**该变量）；**不改 prod yml**，测完 `up -d mall-ai` 恢复 |
| mock 必须支持 | **SSE 分片**（`data: {…}\n\n` + `data: [DONE]`）+ 非流式 JSON + usage + 🔴 **`tool_calls` 分片**（2026-09-11 复核补：生产 `AI_AGENT_ENABLED=true`，不带工具轮测的就不是生产链路）；且**必须线程化**（`ThreadingHTTPServer`，单线程会自己成为瓶颈） |
| 压测三约束 | ① 脚本**跑新机/内网**（老机 5 Mbps，本机压自己=自压自伤）② **先证伪干扰项再测承载**：入口 Sentinel（秒杀 QPS=10、ai-chat=5）+ **每用户频控 10/60s** → 需临时放开阈值 + 用多用户 token ③ **20→50→100 阶梯** + 盯 `docker stats`/`free -h` + **中止阈值**（available<1.5G / 5xx+429>1% / p99>10s） |

> 📄 完整方案（DDL / 清理 SQL / mock 代码 / 纪律清单 / 面试话术）见 [[Python模拟数据与AI并发测试方案]]
> 🆕 **2026-09-11 补强（按用户"最初出发点"）**：该方案的初衷是「**模拟客户行为 → 在 Sentinel / SkyWalking 上看数据量与变化 → 录像展示**」，原稿只写了造数与压测取数、**漏了"可观测展示"这一层** → 已补 **§六 可观测展示与录像 SOP**（面板清单 / SSH 隧道命令 / **§6.2 实证：压浏览 URL 在 Sentinel 也可见** / "平地起峰"三段式录制脚本 / 话术），并把 **§五 执行顺序按"可录像展示"重排**（可观测展示 → 第 ② 步；AI 并发 → 第 ⑤ 步）。

---

## 五、实测实验总表（13 格，全部在服务器直连 DeepSeek 跑，成本可忽略）

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

## 六、依赖关系与执行顺序

```
【Step 1】#58 模型契约 + 可配化（同一批文件，边际成本≈0）        ✅ 已完成并部署（2026-09-10）
   ├ 现役模型 id + thinking 开关 + 删 compare-model / temperature 死参数
   ├ 第 1 层：占位符外置 + 清掉 2 处硬编码 + temperature/max_tokens 收敛
   ├ 第 2 层：models（档位）+ tasks（任务→档位+thinking）
   └ 结构收敛：SSE 请求体收敛进 AiClient.streamChat
        ↓ 验证：JSON 任务无 reasoning / SSE 正常 / 主链路无降级 / 预算记账正常   ✅ 全部实测通过
【Step 2】#32-P0 Function Calling（在 Step 1 的模型契约之上）     ✅ 已完成并部署生产（09d22b1 + 0fe1a1e）｜⚠️ 开关当前 true
        ↓ 验证：curl 触达工具调用 + 两轮收敛（实验 H 已验证可行）    ✅ 生产实测通过（工具命中 4 条 → 第 2 轮收敛；另验兜底不编造）
【Step 3】#32-P1 完整单 Agent（单独排期，不塞进同一窗口）           🚧 代码已完成（含流式 Agent/两工具/Redis 审计，50 项测试全绿）｜⏳ 部署待执行
【并行/下游】#48 模拟数据（独立；造的商品数据可反哺 #32 演示）        📋 设计定稿，待实施
【不做】#60 Spring AI（前置 = Boot 全站升级）                      — 仅评估记录
【插曲】nginx 全站 502 抢修 + 前端 conf 漂移（非计划内）            ✅ 已完成（见 §八）
```

---

## 七、常见疑问 FAQ

**Q1：手写会不会"重复造轮子"？**
A：不会。轮子（会话/预算/闸门/SSE/检索/降级）**都在项目里了**；新增的只有"工具契约 + 一次带 tools 的调用 + 循环"约 300 行，其中一半是 Schema 与工具。

**Q2：不上框架，将来要换模型供应商怎么办？**
A：`base-url` 已可配；DeepSeek 与硅基流动都是 **OpenAI 兼容协议**，换供应商是配置层的事。只有换**非兼容协议**才需要改客户端——而那时"档位 + 任务映射"的抽象仍在，改的是一处适配层。

**Q3：不用框架，参数校验/幻觉怎么办？**
A：**这两件事框架也不替你做**。参数靠 JSON Schema + **服务端二次校验**；幻觉靠"只把工具返回结果拼进上下文 + system 明确'只基于提供信息回答'"（项目 system prompt 已有此规则）。

**Q4：为什么工具轮要关掉思考？会不会变笨？**
A：工具选择是**结构化决策**，不需要长思考；关掉思考换来**稳定 JSON + 绕开 400 回传规则 + 省 token**。深度推理放在**最终生成轮**（`thinking: enabled`）。

**Q5：`mall-ai` 到底能不能落库做审计？**
A：**不能（现状）**——它没有任何数据库栈（无 JDBC/MyBatis/Flyway/`db/migration`）。所以审计落 **Redis List**；DB 化要给它引入整套持久化，与"无状态服务"定位冲突。

---

## 八、小插曲（非计划内，2026-09-10）

> 这两件事**都不是本册计划的实施项**，是当晚突发处理生产问题时顺带发生/发现的。记在这里只为"原理与踩坑不断线" —— **详细排查链、证据与面试话术都在问题解决文档里**，本节只做索引与一句话本质。

### 插曲 1：生产全站 502 —— nginx"静态解析上游 IP"（约 24.5 小时无人发现）

**现象**：用户报登不上，浏览器满屏 `502 Bad Gateway`（`/user/sso/login`、`/front/category/all` 全挂）。

**一句话根因**：`proxy_pass http://mall-gateway:10087` 里的**主机名，nginx 只在加载配置那一刻解析一次并永久缓存**（等价写死 IP）；网关容器被重建后 IP 由 `172.18.0.9` 变成 `172.18.0.7`，而 `.9` 被后来重建的 `mall-ai` 占用 → nginx 一直把请求打到**错误的容器**（10087 无人监听）→ `connect() failed (111)` → 502。

**为什么"坏了 24 小时没人知道"**：**21 个容器全 Up、网关自身 `/actuator/health` 还是 200** —— 内部健康检查天然抓不到"路由层地址漂移"（这直接催生了 [[TODO文件]] **#61 外部端到端探活**）。

**处置（先恢复、再根治、不夹带）**：`restart frontend` 秒级恢复 → **隔离网络 A/B 实验**（容器重建后：动态解析 200 / 静态解析 502）→ 改 `resolver 127.0.0.11 valid=10s` + `upstream mall_gateway { zone …; server mall-gateway:10087 resolve; }` → `nginx -t` + `nginx -s reload` **零停机** → `docker commit` 烘进镜像（回滚点 `pre-dynamic-20260910`）→ 逐条路由验证 + 真登录通过。
> ⚠️ 刻意**没用** `set $gw …; proxy_pass http://$gw;` 变量法：用变量会改变 nginx 的 URI 处理语义（与 `rewrite … break` 的配合不同），而本项目有 `/api/… → 剥前缀` 的 rewrite location —— **只有 `upstream` 块能保证语义零变化**。

📄 **完整排查链（7 步逐层排除）+ A/B 实验 + 验证矩阵 + 面试话术** → [[问题解决--服务注册与网关路由]] **问题 2**；归档 → [[TODO已完成]] §十五
🔗 **同类问题**：本册读者可把它与 [[问题解决--服务注册与网关路由]] **问题 1**（Dubbo 应用名撞名 → 网关把 HTTP 请求打到 20880）对照看 —— 两者同族（**调用方持有错误身份/过期地址**），一个"身份错"、一个"地址过期"。

### 插曲 2：前端 nginx conf 的**双向漂移**（两块生产缺失的配置）

**怎么发现的**：排查插曲 1 时顺手比对"容器内 conf / 服务器源文件 / 仓库副本"，发现**仓库那份（`.gitignore` 忽略、未入库、8/1 16:55）比服务器现行版（8/1 23:15）旧，却多了两块生产没有的内容** —— 典型的"双向漂移"，谁都不是谁的超集：

| 块 | 内容 | 生产现状（当时）| 处置 |
|---|---|---|---|
| ① **SSE 超时** | `proxy_read_timeout/proxy_send_timeout 180s` | 缺失 → 走默认 60s | ✅ **已合并上线**（预防性：思考模式下 reasoning 分片被后端忽略 → 模型"闷头思考"期间 nginx 收不到任何字节 → 60s 无数据即被掐断；当时 **0 次**实际发生）|
| ② **`/seckill/:id` SPA 保护** | `location ~ ^/seckill/?\d*$ { if GET → rewrite /index.html last; }` | 缺失 → **真 bug**：浏览器刷新秒杀详情页拿到的是 `{"state":401,"message":"您没有登录！"}` JSON，不是页面 | ✅ **已合并上线**（验证：`/seckill/123` → `text/html`；`/seckill/spu/list`、`/seckill/sku/list/{id}`、`POST /seckill/{code}` 仍走网关 JSON，**真 API 未被吃掉**）|

**根因（比配置本身更值得记）**：`.gitignore` 里有 `deploy/`，这份 conf **从来没进过版本控制** → "服务器改了、仓库不知道"必然发生。**已 `git add -f deploy/docker/frontend/nginx.conf` 纳入跟踪**，并同步了服务器源文件（md5 `1382897d…`）。

**处置纪律**：两块**先只在隔离容器校验、再 `docker cp` + `nginx -t` + `reload` 零停机上线**，且**与插曲 1 分成两次提交/两个回滚点**（`pre-dynamic-20260910` → `dynamic-only-20260910` → `latest`）—— **抢修不夹带"顺手发现的改进"，每次变更都能独立回滚**。

📄 **完整记录** → [[问题解决--服务注册与网关路由]] 问题 2「附带发现」+ [[问题解决--容器构建与编排卫生]]（"单一事实源/配置治理"这一类问题）
🔗 **状态源** → [[TODO文件]]（#61 / conf 已入 git）；归档 → [[TODO已完成]] §十五

---

## 九、ES 商品索引 mapping 复核与修复（#63 · 2026-09-11）

> **为什么记在本册**：这条缺陷**直接决定 AI 导购的检索质量** —— `search_products` 工具、`/ai/search`、建议补全**全都读这一个索引**，它是 Agent 效果的**上游**。
> **完整实施方案（备份/停机/删索引/重建/验收/回滚）见 [[商品与秒杀扩容方案]] §一**；本节只记「怎么发现的 / 证据 / 对 Agent 的影响」。

### 9.1 发现路径：一次"顺带发现"

起因**不是排查故障**，而是要评估「**要不要把商品从 20 个扩到 60 个**」。一评估就发现：**光扩商品没用** —— 搜索体验上不去的根因在索引本身是错的。这也是一次很好的"选型/评估顺带挖出存量缺陷"的实例。

### 9.2 证据（两轮实测；第二轮是用户明确要求"这个问题很重要，再上服务器验证"）

| 复核项 | 实测 | 意义 |
|---|---|---|
| 索引唯一性 | `cool_shark*` 只有 `cool_shark_mall_ai`（19 docs，71.8kb）、**无别名** | ✅ 排除"查错索引" |
| **IK 插件是否可用** | `_cat/plugins` → **`analysis-ik 8.6.0` 已加载**；显式 `{"analyzer":"ik_max_word"}` → **`小米`/`手机` 正确分词（`CN_WORD`）** | ✅ **排除"最坏情况（插件不可用）"** → 重建后 IK 一定生效 |
| 字段实际分词 | `{"field":"name","text":"小米手机"}` → **4 个 `<IDEOGRAPHIC>` 单字** | 索引确实没用 IK |
| 品牌过滤 | `term brandName` = **0** ／ `term brandName.keyword` = **4** ／ `match` = **4** | ✅ **最强证据形态**：数据没问题，是**查询打错了字段形态** |
| 补全数据形状 | 文档里 `suggestField` 是**字符串数组**（`["小米 RedmiBook Pro","小米 小米 RedmiBook Pro",…]`） | ✅ 正是 `completion` 期望的 `inputs` 形状 → 重建后补全立刻可用 |
| `semanticVector` | mapping 无、文档也无（`embedding-enabled=false`） | 🔴 与 #31 直接相关 |
| 创建时间 | `creation_date` = 1785520267975 ≈ **2026-07-31** | 早于当前初始化代码上线 |
| 启动日志 | `ES索引 [cool_shark_mall_ai] 已存在` | ✅ **永不自愈**（初始化代码只在索引不存在时创建） |
| 反推（**推断，非实测**） | 线上 `shards=1 / replicas=0` **与代码期望一致**但 mapping 全空 | 那次创建**带了 settings、没带 mappings**（历史版本），**不是 ES 自动建的**（自动建 replicas 会是 1）；**是哪个版本无法确定** |

### 9.3 对 Agent 的具体影响（本册关心的部分）

1. **`search_products` 的召回质量被"单字分词"拖累** —— 写入与查询用同一套分词器，所以**能匹配上**（这也正是它一直没被发现的原因），但 `name^5 / title^4 / semanticText^3 / description^2` 这套**权重设计在单字粒度上基本失效**。
2. **品牌维度（`intentSearch` 的 `term brandName` 过滤）恒不生效** → 只能靠 `fullTextSearchNoPrice` 兜底。
3. **`/ai/search/suggest` 补全恒返回空**（completion suggester 打 `text` 字段 → 异常被吞）。
4. **`semanticVector` 字段不存在 → 向量检索的"地"是空的**（#31 的前置之一）。

### 9.4 验收的两条硬指标

- `_analyze {"field":"name","text":"小米手机"}` → **`小米`/`手机` 两个词**（不再是 4 个单字）
- `term brandName="小米"` → **4**（原来 **0**）

### 9.5 ⚠️ 必须盯的一个回归

重建后 `brandName / categoryName / tags` 从 `text` 变 **`keyword`**，而 `multiMatch` **包含这三个字段** → 它们从"部分匹配"变"**整值匹配**"：
**好处**是 `term` 过滤开始生效；**风险**是查询串"小米手机"不再命中 keyword 值"小米" → **召回可能变少**。
### 9.6 修复执行与复核结果（2026-09-11 07:37 · 用户执行 + AI 独立复核）

| 验收项 | 修前 | 修后（实测） |
|---|---|---|
| `dynamic` | 未设置（= `true`） | **`false`** ✅ |
| `name/title/description/semanticText` 分词 | 4 个单字 `<IDEOGRAPHIC>` | **`小米`/`手机`（`CN_WORD`）** ✅（4 个字段全过） |
| `brandName/categoryName/pictures/tags` | `text` | **`keyword`** ✅ |
| `listPrice` / `sales` | `float` / `long` | **`double` / `integer`** ✅ |
| `semanticVector` | **不存在** | **`dense_vector` `dims=1024` `index=true` `similarity=cosine`** ✅ |
| `suggestField` | `text` | **`completion` + `analyzer=ik_max_word`** ✅ |
| `term brandName="小米"` | **0** | **4** ✅ |
| **补全（`/ai/search/suggest`）** | **恒返回空** | **实测 `prefix=小米` → 返回「小米 14」** ✅ |
| 文档总数 | 19 | **19** ✅ |
| 带向量文档数 | 0 | **0**（`embedding-enabled=false`，符合预期） |
| 索引本体 | 旧的 dynamic 索引 | 日志 `ES索引 [cool_shark_mall_ai] 创建成功`（初始化器**真正执行了**）+ 新 uuid |

> ⭐ **一条方法论教训（AI 自己犯的）**：我给的验收命令写的是 `grep -c ik_max_word` 期望 **4** —— **错了两层**：
> ① `grep -c` 数的是**行数**，而 mapping 是**单行 JSON** → 永远只返回 0 或 1；
> ② 实际出现次数是 **5**（4 个 text 字段 + `suggestField` 也用 `ik_max_word`）。
> 正确写法：`grep -o ... | wc -l`。**"验收命令本身也要被验收"** —— 幸好关键那一条 `_analyze` 是确定性的，它一出词就证明了修复成立（否则用户会看到"1 ≠ 4"而误判失败）。

### 9.7 召回回归（修复后实测：担心的"keyword 化导致召回塌"**没有发生**）

| 查询（模拟 `SEARCH_FIELDS` multiMatch） | 命中 | 样例 |
|---|---|---|
| 小米手机 | **8** | 小米 14 / 小米 14 Pro / 小米 RedmiBook Pro |
| 小米 | **4** | — |
| 手机 | **7** | 小米 14 / iPhone 15 / iPhone 15 Pro |
| 华为 | **3** | 华为 MatePad Pro / Mate 60 Pro / P60 Pro |
| 笔记本 | **5** | MacBook Pro 14 / 戴尔 XPS 13 / MacBook Air 15 |
| 旗舰 | **3** | 小米 14 / Redmi K70 Pro / 华为 P60 Pro |
| 价格 ≤5000 + 升序 | ✅ | `listPrice` 现为 `double`，区间过滤与排序正常 |

**为什么没塌（关键论据）**：`brandName/categoryName/tags` 变 `keyword` 后，`multiMatch` 打它们确实退化成"整值匹配"（实测：只打 `brandName` 时 query `"小米手机"` = **0**）；**但 `semanticText` 里本身就写着「品牌：小米 | 分类：手机 | 标签：…」** → 品牌/分类召回由 **`semanticText`（权重 3）兜住**（实测只打 `semanticText` 的 `"小米"` = **4** 命中）。

> ✅ **结论：#63 修复完成、验收全绿、无召回回归**（`grep -c` 那一条是我命令写错，不是修复失败）。

---

## 十、#31 前置解除：#59 充值验证通过（2026-09-11）

| 项 | 结果 |
|---|---|
| **探针** | `POST https://api.siliconflow.cn/v1/embeddings`，生产 key + `BAAI/bge-m3` |
| **结果** | **HTTP 200**，`dims = 1024`，`usage{prompt_tokens:7,completion_tokens:0}`，`model=BAAI/bge-m3` |
| **意义 ①** | **#59 关闭**（原为 **402** `Sorry, your account balance is insufficient`） |
| **意义 ②** | **维度 1024 与 mapping 的 `dense_vector dims=%d`（=`embedding-dimensions: 1024`）完全吻合** → #63 重建出的向量字段与后端要写的向量**同维，不会错配** |

### 10.1 顺序决策（**仍分两步做**）

| 步骤 | 动作 | 为什么这样切 |
|---|---|---|
| **先** | **#63**：删索引 → 重建（`EsIndexInitializer` 的 mapping 常量**始终包含** `semanticVector`，所以这一步就把向量的"地基"打好了） | 它是**唯一需要停机 + 删索引**的动作，风险集中在这里 |
| **后** | **#31**：改 `embedding-enabled=true` + recreate + 同步写入向量 | 只改配置 + 一次同步，**不需要再删索引**，随时可回滚 |

**为什么不合并成一个窗口**（虽然合并能省约 1 分钟停机）：#63 与 #31 **都会改变召回行为**（一个换分词器、一个换检索路径）→ 合在一起**分不清是谁的影响**；且违反「**一次只动一个组件**」（CLAUDE.md §3.5）与「**不夹带**」（§6.1-5）。

### 10.2 #31 实施时的验证清单（预置）

- [ ] 同步后 **带 `semanticVector` 的文档数 = 有效 SPU 数**（19）：`{"query":{"exists":{"field":"semanticVector"}}}`
- [ ] `vectorSearch` 真能返回结果（不是 catch 后返回 `List.of()`）
- [ ] 相似度检索的**结果集变化**符合预期（与纯文本检索对比）
- [ ] 日志里"商品同步完成，共 N 条"的 N 等于有效 SPU 数（**防止 402 式静默失败**）
- [ ] embedding 成本核对：`embedding-price-per-million: 0.0`（当前配置不记预算），确认不会异常消耗 `ai:daily_cost`

### 10.3 🔴 读码发现：**目前没有真正的降级链路**（开 #31 前建议先补）

| 位置 | 代码事实 | 后果 |
|---|---|---|
| `RagServiceImpl.ask()` L82-89 | `embeddingEnabled=true` 时**直接** `embed()` → `vectorSearch()`，**向量分支外没有 try-catch** | **硅基流动一挂，`ask()` 直接抛错 → 搜索报错**（不是降级） |
| `RagServiceImpl.vectorSearch()` L352-355 | catch 里日志写 **"ES 向量检索失败，降级到全文检索"**，但 **`return List.of()`** —— **实际没有降级** | **日志与行为不符**：检索变空 → 回答变成"未检索到相关商品信息"；**排查时会被这条日志误导** |

> ⇒ **建议：开 #31 之前先补这两处**（向量路径加 try-catch → 失败回落 `fullTextSearch()`；并修正那条日志），否则 #31 会把"外部 API 依赖"变成**搜索可用性的单点**。
> ⚠️ 这是**改代码**，按 [[TODO文件]] #31 与纪律要求**单独窗口、不夹带**。

### 10.4 ✅ 降级链路已补（2026-09-11，用户授权"你顺便处理了"）

| # | 位置 | 修改前 | 修改后 |
|---|---|---|---|
| ① | `RagServiceImpl.ask()` | 向量模式下直接 `embed()` → `vectorSearch()`，**无 try-catch** → 外部 API 挂了**搜索直接报错** | 改为调 **`vectorSearchWithFallback(question, topK)`** |
| ② | `RagServiceImpl` 新增 `vectorSearchWithFallback()` | — | **三种失败都回落 `fullTextSearch()`**：embedding 调用失败 / ES 向量检索失败 / 向量结果为空 → **语义能力降级，但搜索仍可用**；并 WARN/ERROR 说明原因 |
| ③ | `RagServiceImpl.vectorSearch()` catch | 日志写 **"降级到全文检索"**，实际 `return List.of()`（**日志与行为不符**） | 日志改为 **"ES 向量检索失败（是否回落全文检索由调用方决定）"**，与实际行为一致，不再误导排查 |
| ④ | `VectorSyncServiceImpl.syncAll()` | `embedBatch()` 抛错 → 整批 `catch` 跳过 → **该批商品完全进不了索引** | **降级为"仅全文索引"**（商品照旧写入 ES，只是不带向量）+ 新增 `vectorDegraded` 计数 → 汇总 **WARN 显式暴露"其中 N 条向量化失败"** |
| ⑤ | `VectorSyncServiceImpl.syncSpu()` | `embed()` 在 try **之外** → 抛错会让**单个商品更新完全不同步** | 包 try-catch → **降级为仅全文索引**（该商品仍能被 BM25 搜到） |

**设计原则（为什么这么改）**：**外部依赖故障不应放大成"功能不可用"** —— 语义检索是**增强**、全文检索是**基线**；embedding 挂了就掉到基线，而不是让搜索/同步整体失败。
同时**降级必须是可见的**（日志显式写"降级/失败条数"）—— 否则就变成**静默降级**，那是比报错更难查的故障形态。

### 10.5 测试结果（2026-09-11，**已跑**）

```
mvn -B -o -pl mall-ai/mall-ai-webapi -am test
[INFO] mall-common ......... Tests run: 5,  Failures: 0, Errors: 0
[INFO] mall-ai-webapi ...... Tests run: 56, Failures: 0, Errors: 0
[INFO] BUILD SUCCESS     （8 个反应堆模块全 SUCCESS）
```

- ✅ **56 项原单测全绿 → 降级改动无回归**（`DeepSeekAiClientRequestBodyTest` 9 / `AiPropertiesBindingTest` 3 / `ChatServiceImplAgentTest` 19 / `CompareProductsToolTest` 8 / `GetStockToolTest` 8 / `SearchProductsToolTest` 7 / `ToolRegistryTest` 2）
- ⚠️ **已知缺口（如实记录）**：本次**没有为新的降级逻辑补单测**（`vectorSearchWithFallback` 需要伪造 `embeddingClient` 的失败路径）。**待补**：给"embedding 抛错 → 回落全文"与"向量结果为空 → 回落全文"各写一条用例。
- 🐞 **踩坑记录（环境类）**：`mvn install` 在本项目 AI 沙箱里**必然失败** —— 它要写 `C:\Users\<用户>\.m2\repository`（**工作区之外**，被文件策略拒绝），报错形态是 `Failed to install artifact …csmall-0.0.1-SNAPSHOT.pom.<随机>.tmp`（**看起来像 Maven 故障，其实是沙箱**）。
  **正确姿势**：用**反应堆内构建** `-pl mall-ai/mall-ai-webapi -am test` —— 模块间依赖走反应堆的 `target/classes`，**不需要 install**，只写工作区内 `target/`。
  （另：不带 `-am` 的离线跑会拿到 `.m2` 里的**旧 `mall-pojo`**，报一堆"找不到符号 SearchDTO/SearchResultVO/SuggestVO" —— 也不是代码问题。）

---

## 十一、⭐ 架构评估：Embedding「换平台 / 换模型」的可替换性（2026-09-11 用户提问）

> **用户的原始问题**：
> 「当前这个代码是否支持未来如果更换平台或者模型来计算向量？有没有做适配？我的意思是**上游业务不依赖下游实现**这种。」

### 11.1 结论一句话

> **"换供应商/换模型"这一层做到了（改配置即可，不动代码）；但"依赖倒置"这一层没做到（上游直接依赖具体类，没有抽象）。**
> 即：**能换"同一协议的另一家"，换不了"另一种协议/另一种部署形态"。**

### 11.2 ✅ 已经做到的（配置层解耦 · 这部分是加分项）

| # | 事实 | 证据 |
|---|---|---|
| 1 | **供应商的"三个坐标"全部外部化**：base-url / api-key / model 都能用环境变量注入 | `AiProperties:85-91`；yml `embedding-api-key: ${EMBEDDING_API_KEY}`、`embedding-base-url: ${AI_EMBEDDING_BASE_URL:…}`、**`embedding-model: ${AI_MODEL_EMBEDDING:BAAI/bge-m3}`（代码里没有默认模型名）** |
| 2 | **内部契约是供应商无关的**：`float[]` / `List<float[]>` | `SiliconFlowEmbeddingClient:40,49` |
| 3 | **协议是通用 OpenAI 兼容格式**：`POST {base}/v1/embeddings` + `{model,input,encoding_format}`；解析 `data[i].embedding` 与 `usage.total_tokens` | `SiliconFlowEmbeddingClient:59-91` → **任何 OpenAI 兼容平台（通义/智谱/OpenAI/本地 vLLM/Ollama 的兼容层）换 base-url + model 即通** |
| 4 | **超时可配**（不会因外部平台慢而永久挂住） | `RestTemplateConfig:14-17` 用 `aiProperties.getTimeout()` 设 connect/read timeout |
| 5 | **并发闸门覆盖 embedding** | `AiConcurrencyGuard`（`embed`/`embedBatch` 都过闸门） |
| 6 | **成本记账可配**（`embedding-price-per-million`，当前 `0.0`） | `SiliconFlowEmbeddingClient:82-88` → `TokenBudgetService.record` |
| 7 | **维度由单一配置驱动**（`embedding-dimensions` → ES mapping 的 `dims`） | `AiProperties:93-94` + `EsIndexInitializer` 的 `dims=%d` |

### 11.3 ❌ 没做到的（抽象层 · 真正的"依赖倒置"缺口）

| # | 缺口 | 证据 | 后果 |
|---|---|---|---|
| **1** | **没有接口**：上游直接依赖**具体类** | `RagServiceImpl:9,48` 与 `VectorSyncServiceImpl:5,36` 都是 `import …SiliconFlowEmbeddingClient;` + 按具体类型注入 | 换**协议**（非 OpenAI 兼容）、加**多供应商并存**、加**本地模型**、加**缓存/降级装饰器** 都得改上游源码 |
| **2** | **命名与文案泄漏厂商** | 类名 `SiliconFlowEmbeddingClient`；日志 `"Calling SiliconFlow Embedding API"`；javadoc 写 **"使用 BGE-M3 模型，兼容 OpenAI API 格式，免费调用"** | 用词会误导维护者（"免费"这条**已经过期**——正是 #59 误判的来源）；将来换平台会出现"名字叫 SiliconFlow、实际打的是别家" |
| **3** | 🔴 **维度没有运行时自检** | 全仓 `embeddingDimensions` 只出现在 `AiProperties` 与 `EsIndexInitializer`；**没有任何地方校验"返回向量长度 == 配置维度"** | 换成 **768/1536 维**的模型时：① 忘改配置 → 写 ES 报维度不匹配；② 改了配置但**没删索引重建** → 同样失败（`dims` **建成后不可改**）。**两种错都只在运行时暴露** |
| **4** | `embeddingDimensions` 注释没写"改维度必须删索引重建" | `AiProperties:93` 只说"ES mapping 的 dims 由它决定" | 配置变更的**操作要求**没写进代码，靠人记 |
| **5** | `encoding_format: "float"` 是 OpenAI **扩展**字段 | `SiliconFlowEmbeddingClient:67` | 少数平台不认该字段会 400（影响小，可配置化） |

### 11.4 修复建议（分档，均未实施 —— 按纪律"不夹带"）

| 档 | 动作 | 成本 | 收益 |
|---|---|---|---|
| **P0** ⭐ | **启动自检**：`embeddingEnabled=true` 时启动探针 `embed("probe")`，校验 `length == embeddingDimensions`，不一致**启动即失败并给出可操作报错**（"模型返回 N 维，配置为 M 维；若已换模型请同步改 `embedding-dimensions` 并**删索引重建**"）；顺手删掉 javadoc 里的"免费调用" | ~30 分钟 | 把"运行时才炸"变成"启动就报"，这是本次 #59/#63 两次踩坑的**共同教训** |
| **P1** | **抽接口 `EmbeddingClient`**（`float[] embed(String)` / `List<float[]> embedBatch(List<String>)`）；`SiliconFlowEmbeddingClient` → 改名 `OpenAiCompatEmbeddingClient implements EmbeddingClient`；两处注入改**接口** | 1~2 小时 | 真正做到**依赖倒置**：换协议只加实现、加缓存/降级只加装饰器、上游零改动；**面试可直接讲"我依赖抽象而非厂商 SDK"** |
| **P2** | 注释补齐"改维度 → 必须删索引重建"；把"重启 + 重建索引"写进配置变更 SOP | ~30 分钟 | 防止 #63 这类"配置与索引不一致"再次发生 |
| **P3** | `encoding_format` 可配化或去掉 | ~15 分钟 | 兼容更多平台 |

### 11.5 面试话术（本节的复用价值）

> "我的业务代码**不应该**知道向量是谁算的。现在的情况是：**配置层面已经解耦**——供应商地址、Key、模型名、维度全在配置里，换一家 OpenAI 兼容的平台**只改环境变量、不动一行代码**；内部契约是 `float[]`，不绑任何 SDK 类型。
> 但**抽象层面还差一步**：上游是直接注入具体类的，没有 `EmbeddingClient` 接口，所以'换协议、加多供应商、加本地模型、加缓存'这几件事还得改上游——这就是**依赖倒置还没做透**。
> 另外我踩过一个真实的坑：**向量维度在建索引时是不可变的**，而代码里**没有校验'模型实际返回的维度'和'配置维度'是否一致**，所以我给它的加固是 **P0 加启动自检** —— 让配置错误在**启动时**就暴露，而不是等到检索时才炸。"

## 十二、Embedding 可替换性加固：P0~P3 实施记录（2026-09-11）

> **起因**：**§十一的评估结论**（配置层已解耦、抽象层未做透）→ 用户当日要求**直接实施 P0~P3**（不再停留在评估）。

### 12.1 改动清单（7 处）

| 档 | 改了什么 | 文件 |
|---|---|---|
| **P1** ⭐ | **新增接口 `EmbeddingClient`**（`float[] embed(String)` / `List<float[]> embedBatch(List<String>)`，出参**不暴露任何 SDK/HTTP 类型**）；原类 **`git mv` 改名 `OpenAiCompatEmbeddingClient` 并 `implements EmbeddingClient`**（git 历史保留，状态 `R`）；两处注入**由具体类改为接口** | 新增 `client/EmbeddingClient.java`；`client/OpenAiCompatEmbeddingClient.java`（重命名）；`RagServiceImpl`（import + 字段）；`VectorSyncServiceImpl`（同） |
| **P0** ⭐ | **新增 `EmbeddingSelfCheck` 启动自检**（策略见 12.2） | 新增 `config/EmbeddingSelfCheck.java` |
| **P0** | `EsIndexInitializer` 加 **`@DependsOn("embeddingSelfCheck")`** → **自检先跑、建索引后跑** | `init/EsIndexInitializer.java` |
| **P0** | 删掉 javadoc 里**已过期的"免费调用"**（#59 误判来源），并把厂商名从**类名 / 日志 / 注释**里清掉 | `OpenAiCompatEmbeddingClient` |
| **P2** | `embeddingDimensions` 注释补全 **"改维度 = 改配置 + 删索引重建"** 的完整操作步骤；yml 同步加注释 | `config/AiProperties.java`；`resources/application.yml` |
| **P3** | **`encoding_format` 可配**：新增 `embedding-send-encoding-format`（默认 true；个别平台不认该字段时置 false） | `AiProperties` + `OpenAiCompatEmbeddingClient` + yml |
| 附带 | 同步更新被重构影响的注释引用 | `AiClient`（§5.3 说明）、`AiConcurrencyGuard`（闸门挂点清单） |
| **测试** ⭐ | **检索侧测试接缝**：`vectorSearchWithFallback` / `vectorSearch` 由 `private` → **包级可见（只改可见性、不改行为）**；新增 `RagServiceImplVectorFallbackTest` **6 条** | `RagServiceImpl`；新增 `src/test/.../impl/RagServiceImplVectorFallbackTest.java` |
| **测试** ⭐ | **同步侧测试接缝**：`getAllSpus` / `listIndexedDocIds` / `cleanupInvalidDocs` 放开为**包级可见**，并把 ES 的 **bulk 写入 / 单条写入**各抽成一个方法（`bulkUpsert` / `indexDoc`，**行为与异常语义均不变**）；新增 `VectorSyncServiceImplDegradeTest` **14 条** | `VectorSyncServiceImpl`；新增 `src/test/.../impl/VectorSyncServiceImplDegradeTest.java` |
| 🛡️ **边界 / 加固** ⭐ | 复核共修 **3 处真实问题**（见 **§12.6**）：① `syncAll` 的 `vectors.get(i)` **越界保护**；② `vectorSearchWithFallback` 的 **null/空向量**显式回落；③ **`getAllSpus()` 两处 NPE**（`getList()` 可空 + **`getTotalPage()` 是可空 `Integer`**）→ 改为"**空页即到底**"主终止条件 + `MAX_PAGES` 防御上限 + 触顶告警 | `VectorSyncServiceImpl`；`RagServiceImpl` |

### 12.2 P0 的两条设计决策（为什么这么做）

**① 只有"维度不一致"才让启动失败，外部调用失败只 WARN** —— 延续 §10.4 那条原则

| 情形 | 处置 | 理由 |
|---|---|---|
| 返回维度 ≠ 配置 | **抛异常 → 启动失败**，报错含"实际维度 / 配置维度 / 删索引重建步骤" | **确定性配置错误**；带着它启动只会往 ES 写维度不符的向量 |
| 网络 / 余额 / 401 / 超时 | **WARN，启动继续** | **瞬态外部故障**；若阻断启动，就把"外部 API 挂了"放大成"**服务起不来**"—— 比降级更严重 |
| 返回空向量 / null | WARN，启动继续 | 不是维度问题；保持"**只有维度不一致才致命**"这条规则足够简单 |
| `embedding-enabled=false` | **完全不调用外部接口** | 没开向量检索就该零副作用、零 token 消耗 |

**② 用 `@DependsOn` 而不是各自 `@PostConstruct`**
两个 `@PostConstruct` 之间**没有顺序保证**；若 `EsIndexInitializer` 先跑，就会**按错误维度把索引建出来**（`dims` 建成后不可改 → 还得再删一次）。显式声明依赖，比"靠巧合"可靠。

### 12.3 收益对照（§11.4 的预期 → 实际落地）

| 预期收益 | 落地 |
|---|---|
| 换平台 / 换模型 = 改配置 | ✅ 原有能力保留 |
| 换**协议** / 加**本地模型** / 加**缓存装饰器** = 只加实现，上游零改动 | ✅ **接口已就位** |
| 配置错误**启动即暴露** | ✅ `EmbeddingSelfCheck` |
| 配置变更的**操作步骤写进代码** | ✅ `AiProperties` + yml 注释 |
| 兼容更多平台 | ✅ `encoding_format` 可关 |
| **可测性**（意外收获） | ✅ 抽接口后 fake 只需实现 2 个方法 → 新增 `EmbeddingSelfCheckTest` **6 条用例**（**手写 fake、不用 Mockito**，顺带绕开本项目 Mockito inline MockMaker 加载失败的环境限制） |

### 12.4 测试结果（2026-09-11 · 改完**立即自检**，并补上降级分支的用例）

```
mvn -B -o -pl mall-ai/mall-ai-webapi -am test
mall-common ......... Tests run: 5
mall-ai-webapi ...... Tests run: 82     ← 56 原有 + EmbeddingSelfCheckTest 6 + VectorFallbackTest 6 + SyncDegradeTest 14
Failures: 0, Errors: 0, Skipped: 0  →  BUILD SUCCESS（8 个反应堆模块全 SUCCESS）
```

| 用例集 | 条数 | 覆盖 |
|---|---|---|
| `EmbeddingSelfCheckTest` | **6** | 没开就不调用 · 维度一致放行 · **维度不一致 → 启动失败且报错可操作（含 `DELETE` 步骤）** · 瞬态失败不阻断启动 · 空向量按瞬态 · `null` 向量按瞬态 |
| `RagServiceImplVectorFallbackTest` | **6** | **三条降级分支**（embedding 失败 / 向量检索失败 / 向量结果为空 → 全部回落全文检索）+ **反向保护**（向量有结果时**不得**再多打一次全文，防止被改成"永远回落"）+ **边界**（null 向量 / 空向量） |
| `VectorSyncServiceImplDegradeTest` | **14** | **同步侧降级**（embedding 失败仍写入全部文档 / 少返向量不越界 / 一条都没回 / 关闭时不调用 / **空 SPU 列表**）+ **`syncSpu` 单条容错**（写入失败不向上抛 / 可搜索写文档 / 不可搜索走删除）+ **`cleanupInvalidDocs` 差集逻辑**（只删 stale / 无 stale 返回 0 / 跳过非数字 id / 跳过 null id / ES 抛错兜底返回 0） |

#### 🔧 补这两组用例时用到的手法：**测试接缝（test seam）**

`vectorSearchWithFallback()` 原是 `private` 且依赖 `ElasticsearchClient`（具体类、难伪造）—— 这就是它一开始"**想测就得先动生产代码**"的原因，当时我选择**先记录而不是擅自扩大改动面**。
现在的做法是**最小侵入**：把 `vectorSearchWithFallback` / `vectorSearch` 由 `private` 放开为**包级可见**（**不改任何行为**，`fullTextSearch` 本来就是包级可见），同包测试**只重写两个检索入口**：

- 被替换掉的：`vectorSearch`（伪造"ES 挂了 / 返回空"）、`fullTextSearch`（返回可识别的标记结果）
- **仍然走真实代码的**：`embeddingClient.embed()` 调用、**三条降级判断**、日志 —— 所以测到的是真实降级逻辑，而不是"把整个类 mock 掉"的空壳

> **可讲的经验**：遇到"private + 依赖具体类"导致不可测时，不必上重型 mock 框架，**把方法放开一级可见性**就够；前提是**改动只涉及可见性、不涉及行为**。本项目沙箱里 Mockito 的 inline MockMaker 本就加载失败（见 [[本地双实例锁验证报告-2026-09-09]]），这套"手写 fake + 测试接缝"反而是更稳的路子。

> ✅ **同步侧也已覆盖（2026-09-11 追问后补上）**：`VectorSyncServiceImplDegradeTest` 用同一套手法（放开 `getAllSpus` / `cleanupInvalidDocs` 可见性 + 把一行 `esClient.bulk(...)` 抽成 `bulkUpsert(...)`）测到了"**embedding 失败 → 仍写仅全文文档**"这条核心降级 —— **原实现下这种情况这批商品一条都进不了 ES（`synced=0`），现在能全量写入**。

### 12.6 🔍 复核检查记录（逻辑 / 越界 / 注释 —— 2026-09-11 用户要求"最后再检查一下"）

#### A. 真实问题（**发现即修 + 补用例**）

| # | 问题 | 为什么会出问题 | 处置 |
|---|---|---|---|
| **1** | 🛡️ **`syncAll` 的 `vectors.get(i)` 越界** | `embedBatch` 的返回**不保证**"条数 == 入参条数"（少返、部分失败都可能）→ `get(i)` 抛 `IndexOutOfBoundsException`，被外层 catch 成"整批同步失败" → **该批商品又是全部进不了 ES**，等于把刚修好的降级又绕回去了 | 改为 `i < vectors.size()` 才取，取不到按"仅全文"处理并**计入 `vectorDegraded`**；补 2 条用例（**入参 3 条只回 1 条** / **一条都不回**） |
| **2** | 🛡️ **`vectorSearchWithFallback` 的 null / 空向量** | `embed()` 若返回 `null` → 进 `vectorSearch` 后以 **NPE** 形式被 catch（行为对、但日志难读）；返回**空数组**则白跑一次 ES 向量检索 | 显式判断 `null || length == 0` → 直接回落全文并打清楚日志；补 2 条用例 |

#### B. ✅ 原"发现但未改"的一项 —— 已在你确认后一并修复

| # | 位置 | 隐患 | 处置（本次已改） |
|---|---|---|---|
| **3** | `getAllSpus()`（分页循环） | ① `spuPage.getList()` 为 `null` → **NPE**；② 🔴 **`getTotalPage()` 声明是 `Integer`（可空）**，原来直接 `totalPage <= page` 会**自动拆箱 NPE**；③ totalPage 异常大 → 持续翻空页 | 改为：`getList()` 为 null/空即 `break`（**"空页 = 到底"作为主终止条件，不再依赖 totalPage 可信**）+ `getTotalPage()` 显式判 `null` + 新增 `MAX_PAGES = 200` 防御上限 + 触顶打 **WARN**（既不会无限循环，也不会静默截断） |

#### C. 注释完整性检查（逐项）

| 检查点 | 结论 |
|---|---|
| 新增/改动的方法是否解释**"为什么"**（而非只说 what） | ✅ `vectorSearchWithFallback`（修的是什么 / 现在的语义）、`bulkUpsert`（为什么抽这一行）、`EmbeddingSelfCheck`（两类失败为何区别对待）、`EmbeddingClient`（为什么要有这个接口） |
| **测试接缝**是否标注清楚（避免后人误以为是漏写 `private`） | ✅ 4 处都写了"**包级可见（不是 private）：单测接缝**" |
| 每个测试类是否有**覆盖矩阵** | ✅ 3 个测试类都有表格说明"覆盖什么、为什么" |
| "未来维护注意"是否写明 | ✅ 补了两处：① `EmbeddingClient` 加**第二个实现**时上游按类型注入会歧义 → 需 `@Primary`/`@Qualifier`；② `@DependsOn("embeddingSelfCheck")` 与**类名隐式耦合**，改名不同步会**启动即报错（不静默）** |
| 配置项的操作要求是否写进代码 | ✅ `AiProperties.embeddingDimensions` 与 `application.yml` 都写明"**改维度 = 改配置 + 删索引重建**"两步 |
| 历史误导是否清掉 | ✅ 删掉"**免费调用**"（#59 误判来源）；厂商名从类名/日志/注释移除，只保留"原名是什么、为什么改"的说明 |

#### D. 原"未覆盖"两项 —— 已全部补上

| 项 | 现状 |
|---|---|
| `syncSpu()` 单条的**写入失败容错**（含"向量维度不对被 ES 拒绝"） | ✅ 已测：把 ES 单条写入抽成 `indexDoc` 接缝 → **断言不向上抛异常**（只记 error）；另加两条："可搜索 → 写文档"、"不可搜索 → 从 ES 删除" |
| `cleanupInvalidDocs` 的**差集逻辑** | ✅ 已测：把 ES 拉取抽成 `listIndexedDocIds` 接缝 → **5 条用例**覆盖：只删 stale / 无 stale 返回 0 / 跳过非数字 id / 跳过 `null` id / **ES 抛错兜底返回 0 不影响主流程** |

### 12.5 面试话术（P0~P3 完成后的版本）

> "向量化这块我做过一次'可替换性'加固。**配置层**早就解耦了：平台地址、Key、模型名、维度全在配置里，换一家 OpenAI 兼容的平台**只改环境变量**。
> 但我发现**抽象层还差一步** —— 上游直接注入的是具体类，于是我抽了 `EmbeddingClient` 接口，实现改名 `OpenAiCompatEmbeddingClient`（原名把**厂商写进了类名**，注释里甚至写着'免费调用'，那条早就过期了，还害我们误判过一次）。现在换协议、加本地模型、加缓存装饰器都**只加实现，上游一行不改**。
> 更关键的是我加了**启动自检**：向量维度**建索引时不可变**，而代码原来**不校验'模型实际维度 vs 配置维度'**，这种错只会在检索时才炸。我的策略是**区分两类失败** —— **维度不一致是确定性配置错误，直接让启动失败**并给出'删索引重建'的步骤；**网络/余额这类瞬态故障只告警、不阻断启动**，因为把'外部 API 挂了'放大成'服务起不来'是更严重的故障；运行期我已经有降级兜底（向量失败 → 回落全文检索）。"

---

**关联文档**：[[TODO文件]]（状态源 #32/#48/#58/#59/#60/#61/#63/#64）、[[商品与秒杀扩容方案]]（#63 修复 + 商品/秒杀扩容实施方案）、[[AI导购Agent升级方案]]（Agent 完整方案 + P0 清单与实施记录）、[[Python模拟数据与AI并发测试方案]]（模拟数据完整方案 + §〇.1 复核）、[[AI模型名停用风险与thinking参数改造方案]]（§3.1 实验 / §4.5 可配化 / §4.6-4.7 Spring AI 与 Boot 升级评估 / §十一 实施与部署记录）、**[[问题解决--服务注册与网关路由]]（§八 插曲 1/2 的完整排查与话术）**、[[问题解决--容器构建与编排卫生]]（配置不在版本控制这一类问题）、[[TODO第三批实现与原理]]（正册：跨机集群等）、[[TODO已完成]]、[[TODO第二批实现与原理]]（§5.4.5 #21 当初为何改用 `deepseek-chat`）
