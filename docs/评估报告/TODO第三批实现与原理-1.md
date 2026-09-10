# TODO 第三批实现与原理 · 补册 1（AI 导购 Agent + Python 模拟数据）

> **创建日期**: 2026-09-10
> **状态**: 📋 **设计定稿 + 决策记录（待实施）** —— 本册覆盖 **#32（AI 导购 Agent）/ #48（Python 模拟数据）**，以及支撑它们的 **#58（模型 `thinking` 开关）/ #60（Spring AI 评估）**
> **为什么另开一册**: 原 [[TODO第三批实现与原理]] 已 946 行、以跨机集群为主；按用户要求把 **Agent 与模拟数据**这两块"新东西"单独成册，**原册不动**，两册互链
> **用途**: 与三个「实现与原理」正册格式对齐 —— 「**选型/原理 → 本项目设计 → 实测证据 → 疑惑点 → 面试话术**」；**本册的特色是"技术选型过程"被完整记录**（为什么不跟风上框架）
> **关联**: [[TODO文件]] #32/#48/#58/#60、[[AI导购Agent升级方案]]、[[Python模拟数据与AI并发测试方案]]、[[AI模型名停用风险与thinking参数改造方案]]、[[TODO第三批实现与原理]]（正册）、[[TODO已完成]]

---

## 〇、本册全景

| 编号 | 事项 | 本质 | 状态 |
|---|---|---|---|
| **#58** | 模型名停用风险 + `thinking` 开关改造 | **前置**：给 Agent 一个稳定的模型契约 | ✅ 方案定稿 + 实测复验通过；待实施 |
| **#32** | AI 导购升级 Agent（Function Calling + ReAct） | 让 LLM 有"动作决策权"，而非只当解析器 | ✅ 方案敲定（P0 → P1）；待实施 |
| **#48** | Python 模拟数据 + AI 并发测试 | 造运营数据 + 验证 AI 承载 | ✅ 规范设计定稿；待实施 |
| **#60** | Spring AI 引入评估 | **结论：暂不引入**（前置 = Boot 全站升级） | ✅ 评估完成，仅记录 |

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
| SSE 流式 + thinking 卡片 | ♻️ **已有** | `doStreamDeepSeek` + 前端已有组件 |
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
| 9 | `DeepSeekAiClient.embed/embedBatch` 死代码 | `DeepSeekAiClient:180-235` | 🟡 可选删除（无人调用；真调用必失败 —— 打的是 DeepSeek 地址 + 硅基流动模型名） |

> 第 6 条是**结构性问题**：改模型要改两个地方（客户端 + SSE），本次一并收敛，以后只改一处。

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
| **循环只放非流式路径**，最后一轮再复用 SSE | LLM 调用有**两条独立路径**；流式下解析 `tool_calls` 分片收益为零 |
| **工具类必须放 `...ai.service.impl` 同包** | `RagServiceImpl.intentSearch/buildContext/buildRelatedProducts` 是**包私有**方法 |
| **`max_tokens` 要留足** | 实测 D/I：思考模式下 `max_tokens=120` 被思考**全部吃光 → content 为空** |

### 3.3 工具集与边界

| 级别 | 示例 | 策略 |
|---|---|---|
| 🔵 只读 | 检索 / 比价 / 查库存 | 登录即可，AI 自动执行 |
| 🟡 写操作 | 加购 / 改订单 | **AI 只"建议"，用户点确认才执行**（human-in-the-loop） |
| 🔴 高敏 | 退款 / 改价 / 发券 | 角色限制 + 双重确认 + 审计，**或干脆不给 AI 这个工具** |

**工具级/行为级边界（代码层强制）**：白名单 / 参数 JSON Schema + **服务端二次校验** / **轮数上限 3** / 预算复用（每轮记账）/ Sentinel 限流 / **结果真实性**（只能基于工具返回）/ 角色映射 / **动作审计**（Redis List：`ai:agent:action:{sessionId}`，`LTRIM 0 49` + TTL 7d —— ⚠️ **mall-ai 无任何数据库栈，落 DB 不可行**）/ 失败降级。

### 3.4 代码骨架（用本项目真实类名）

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

- **P0（~0.5~1 天）**：`chatJson`/档位配置 → 消息类型加宽 `Map<String,Object>` → `chatWithTools` → `ToolRegistry` + `search_products` → `agentBranch` 循环 → 验证 curl 能触发工具调用
- **P1（~2~3 天）**：`compare_products` / `get_stock` → Redis 动作审计 → 每轮 SSE `thinking` 事件（前端已有组件，**无需改前端**）→ 预算/闸门回归 → 工具失败自动降级
- **P2（可选）**：框架化（见 §一）/ 审计 DB 化
- **回滚**：`agent-enabled: false` 重启一个容器即回现状；改动**只落在 `mall-ai`**，无表变更、无 ES mapping 变更

> 📄 完整方案（差距清单 / 9 条边界 / 文件级清单 / 验证 / 回滚）见 [[AI导购Agent升级方案]]

---

## 四、Python 模拟数据设计（#48）

### 4.1 为什么必须有"隔离层"（三类**不可逆**污染，实测）

| 类别 | 实测现状（2026-09-10） | 造数后果 | 删用户能还原吗 |
|---|---|---|---|
| **累加计数器** | `pms_spu.sales`：81 / 1 / 1 … | 下单/秒杀**累加** | ❌ 没有"谁贡献多少"的记录 |
| **真实库存** | `pms_sku.stock` 30/25/20/15；`seckill_sku.seckill_stock` 25~150（1 个已 0） | 下单扣减 | ❌ |
| **Redis 状态** | `mall:seckill:sku:stock:*` **12 个** | 秒杀扣预热库存 | ❌ |
| 可追踪实体 | `ums_user` 110 / `oms_order` 86 / `success` 58 | 新增记录 | ✅ 可按登记精确删 |

**初稿的 5 个缺陷**（逐条实测确认）：
1. 🔴 **清理 SQL 顺序错误** —— 先 `DELETE ums_user`，再按 `username LIKE 'test_sim_%'` 查 user_id 删订单 → 子查询已空 → **订单删不掉**
2. 🔴 **前缀删不掉不可逆污染**（上表三类）
3. 🟠 **Redis 清理想按 pattern 全删** → 会**误伤真实用户**的购买标记
4. 🟠 **全库 0 个外键**（实测）→ 删除顺序无数据库保护，漏表即静默残留；含 `user_id` 的表实测 **7 张**
5. 🟠 **mock LLM 未实现 SSE 格式** → `doStreamDeepSeek` 按 `data: ` 解析，普通 JSON **收不到任何 chunk**

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
| 怎么接 | **compose override** 注入 `COOXIAO_AI_BASEURL` 环境变量；**不改 prod yml**，测完 `up -d mall-ai` 恢复 |
| mock 必须支持 | **SSE 分片**（`data: {…}\n\n` + `data: [DONE]`）+ 非流式 JSON + 可选 usage |
| 压测三约束 | ① 脚本**跑新机/内网**（老机 5 Mbps，本机压自己=自压自伤）② **避开 Sentinel 限流接口**（秒杀 QPS=10、ai-chat=5）→ 主压浏览/加购/普通下单 ③ **20→50→100 阶梯** + 盯 `docker stats`/`free -h` |

> 📄 完整方案（DDL / 清理 SQL / mock 代码 / 纪律清单 12 项 / 面试话术）见 [[Python模拟数据与AI并发测试方案]]

---

## 五、实测实验总表（10 格，全部在服务器直连 DeepSeek 跑，成本可忽略）

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

**实验带来的 3 个方案修正**：① 工具轮不能与 `response_format` 共存；② SSE 的 `max_tokens: 2000` 应改读配置（思考占大头，实测 J 中 reasoning 分片是 content 的 2.3 倍）；③ `DeepSeekAiClient.embed` 是死代码。

---

## 六、依赖关系与执行顺序

```
【Step 1】#58 模型契约 + 可配化（同一批文件，边际成本≈0）
   ├ 现役模型 id + thinking 开关 + 删 compare-model / temperature 死参数
   ├ 第 1 层：占位符外置 + 清掉 2 处硬编码 + temperature/max_tokens 收敛
   ├ 第 2 层：models（档位）+ tasks（任务→档位+thinking）
   └ 结构收敛：SSE 请求体收敛进 AiClient.streamChat
        ↓ 验证：JSON 任务无 reasoning / SSE 正常 / 主链路无降级 / 预算记账正常
【Step 2】#32-P0 Function Calling（在 Step 1 的模型契约之上）
        ↓ 验证：curl 触达工具调用 + 两轮收敛（实验 H 已验证可行）
【Step 3】#32-P1 完整单 Agent（单独排期，不塞进同一窗口）
【并行/下游】#48 模拟数据（独立；造的商品数据可反哺 #32 演示）
【不做】#60 Spring AI（前置 = Boot 全站升级）
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

**关联文档**：[[TODO文件]]（状态源 #32/#48/#58/#60）、[[AI导购Agent升级方案]]（Agent 完整方案）、[[Python模拟数据与AI并发测试方案]]（模拟数据完整方案）、[[AI模型名停用风险与thinking参数改造方案]]（§3.1 实验 / §4.5 可配化 / §4.6-4.7 Spring AI 与 Boot 升级评估）、[[TODO第三批实现与原理]]（正册：跨机集群等）、[[TODO已完成]]、[[TODO第二批实现与原理]]（§5.4.5 #21 当初为何改用 `deepseek-chat`）
