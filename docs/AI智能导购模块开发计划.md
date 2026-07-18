# AI 智能导购模块开发计划

> 创建日期：2026-05-15
> 基于 CoolShark 微服务电商平台（Spring Cloud Alibaba + Java 21 + Spring Boot 3.2.5）

---

## 一、概述

### 1.1 业务目标

在现有 CoolShark 电商平台基础上，新增 **AI 智能导购** 模块，分四个阶段逐步实现从基础的 AI 商品对比到高级语义搜索的能力，为用户提供智能化购物体验。

### 1.2 架构决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 模块结构 | 新建 `mall-ai` 根模块，内含 `mall-ai-webapi` + `mall-ai-service` | 遵循现有模块结构（如 mall-search、mall-order），保持架构一致性 |
| 服务端口 | `10010` | 在现有端口范围（9010~10009）外顺延 |
| AI API | DeepSeek API（国内直连，性价比高）或阿里云通义千问 | 服务器在阿里云成都节点，国内 API 延迟低 |
| 向量存储 | Elasticsearch 7.17 `dense_vector` 类型 | 复用现有 ES 中间件，无需额外部署向量数据库 |
| 会话存储 | Redis（TTL 过期机制） | 复用现有 Redis，天然支持分布式 |
| 服务间调用 | Apache Dubbo | 与项目现有 RPC 方案一致 |
| Gateway 路由 | `Path=/ai/**` | 遵循现有 Gateway 路由配置模式 |

### 1.3 当前项目关键依赖

- **JDK 21**、Spring Boot 3.2.5、Spring Cloud Alibaba 2023.0.1.2
- **Dubbo 3.3.2**（服务间 RPC）、**Nacos**（注册/配置中心）
- **Elasticsearch 7.17.29**（含 IK 分词器，已用于商品搜索）
- **Redis 7.x** + **RabbitMQ 3.13.7** + **MySQL 8.0** + **Seata 2.1.0**
- 部署在阿里云 ECS（4 核 16G），通过 systemd 管理服务

---

## 二、第一阶段：AI 商品对比（预计 1-2 天）

### 2.1 目标

实现零基础设施改造，最快上线。用户选择多个商品后，调用 AI 自动生成结构化的对比结果（规格、价格、卖点、适用场景等维度），展示 AI 的信息整理与归纳能力。

### 2.2 技术方案

```
用户选择商品IDs → 前端调用 POST /ai/compare
  → mall-ai 通过 Dubbo 获取 SPU 详情（name/price/brand/category/description/pictures）
  → 组装 prompt 调用 AI API → AI 返回结构化 JSON → 解析后返回前端
```

### 2.3 接口设计

```
POST /ai/compare
请求体:
{
  "spuIds": [1, 2, 3],
  "dimensions": ["规格参数", "价格", "核心卖点", "适用场景", "优缺点"]
}

响应:
{
  "code": 200,
  "data": {
    "dimensions": ["规格参数", "价格", "核心卖点", "适用场景", "优缺点"],
    "products": [
      {
        "id": 1,
        "name": "商品A",
        "picture": "http://...",
        "dimensionValues": ["...", "...", "...", "...", "..."]
      }
    ],
    "summary": "综合推荐建议..."
  }
}
```

### 2.4 关键实现

1. **AiClient 抽象层**：定义统一的 AI API 调用接口，支持切换供应商
   ```java
   public interface AiClient {
       String chat(String systemPrompt, String userMessage);
   }
   ```

2. **ProductCompareService**：接收商品 ID 列表 → Dubbo 调用获取 SPU 详情 → 调用 AI → 解析 JSON 返回

3. **Prompt 模板**（system message）：
   ```text
   你是一个专业的电商导购助手。请对比以下商品，从"规格参数"、"价格"、
   "核心卖点"、"适用场景"、"优缺点"五个维度进行结构化对比。
   请以JSON格式输出。
   ```

4. **Gateway 路由**：在 `application-dev.yml` 添加：
   ```yaml
   - id: mall-ai
     uri: http://localhost:10010
     predicates:
       - Path=/ai/**
   ```

### 2.5 需要新增的文件

| 文件路径 | 说明 |
|----------|------|
| `mall-ai/pom.xml` | 根 pom，聚合子模块 |
| `mall-ai/mall-ai-webapi/pom.xml` | WebAPI 模块 |
| `mall-ai/mall-ai-service/pom.xml` | 服务 API 模块 |
| `mall-ai/mall-ai-webapi/.../MallAiWebApiApplication.java` | 启动类 |
| `mall-ai/mall-ai-webapi/.../controller/AiController.java` | `POST /ai/compare` |
| `mall-ai/mall-ai-webapi/.../service/impl/ProductCompareServiceImpl.java` | 对比实现 |
| `mall-ai/mall-ai-webapi/.../config/AiClientConfig.java` | AI 客户端配置 |
| `mall-ai/mall-ai-webapi/.../config/AiProperties.java` | API Key、端点等配置 |
| `mall-ai/mall-ai-service/.../service/IAiService.java` | Dubbo 接口 |
| `mall-pojo/.../ai/dto/ProductCompareDTO.java` | 对比请求 DTO |
| `mall-pojo/.../ai/vo/CompareResultVO.java` | 对比结果 VO |

### 2.6 风险与注意事项

| 风险 | 应对 |
|------|------|
| AI API 延迟 | 前端先显示 loading，超时设为 15s；加 Redis 缓存相同商品组合的对比结果 |
| API Key 安全 | 通过 Nacos 配置或环境变量注入，不写入代码仓库 |
| 输出格式不稳定 | 使用 AI 的 JSON 模式输出；后端做 JSON 校验和 fallback |

---

## 三、第二阶段：RAG 智能问答 ✅（已完成）

> **实施时间**：2026-05-15  
> **当前状态**：已完成并可用，使用 ES 全文检索作为默认检索模式

### 3.1 目标

基于已有的商品数据库，构建检索增强生成（RAG）能力。用户可通过自然语言提问，系统检索相关商品后由 AI 生成准确回答，体现语义理解能力。

### 3.2 技术方案

利用 **已有 Elasticsearch 7.17** 实现双重检索，无需额外部署向量数据库：

- **ES IK 分词全文检索（当前默认）**：用户问题直接通过 `multi_match` 对商品字段（名称^5、标题^4、语义文本^3、描述^2、品牌、分类、标签）进行加权匹配。不依赖任何外部 embedding API，零成本运行
- **向量语义检索（预留扩展）**：通过配置开关 `cooxiao.ai.embedding-enabled=true` 切换到向量模式。接入 embedding API 后，将用户问题转为向量，通过 `cosineSimilarity` 做语义匹配。当前代码已完整实现，仅待接入 embedding API 供应商
- **生成回答**：将检索结果作为 context 注入 prompt，由 AI 生成答案

### 3.3 架构

```
POST /ai/ask  "3000元左右适合学生的手机"
       │
       ▼
┌─────────────────┐        ┌────────────────────────────┐
│   RagServiceImpl │───────▶│ ES 检索（二选一，配置开关控制）  │
├─────────────────┤        ├────────────────────────────┤
│ 1. 预算检查       │        │ mode=全文: multi_match 分词  │
│ 2. ES 检索商品    │        │ mode=向量: cosineSimilarity │
│ 3. 构建上下文     │        └────────────────────────────┘
│ 4. AI Chat 回答  │
└────────┬────────┘
         │
         ▼
┌──────────────┐
│ DeepSeek V4  │  Chat API 生成推荐回答
│ Flash        │
└──────────────┘
```

### 3.4 ES 索引结构

**索引名**：`cool_shark_mall_ai`（启动时自动创建，含 IK 分词 text 字段 + dense_vector 向量字段）

```json
{
  "mappings": {
    "dynamic": false,
    "properties": {
      "spuId":          { "type": "long" },
      "name":           { "type": "text", "analyzer": "ik_max_word" },
      "title":          { "type": "text", "analyzer": "ik_max_word" },
      "description":    { "type": "text", "analyzer": "ik_max_word" },
      "categoryName":   { "type": "keyword" },
      "brandName":      { "type": "keyword" },
      "listPrice":      { "type": "double" },
      "pictures":       { "type": "keyword" },
      "tags":           { "type": "keyword" },
      "sales":          { "type": "integer" },
      "semanticText":   { "type": "text", "analyzer": "ik_max_word" },
      "semanticVector": { "type": "dense_vector", "dims": 512, "index": true, "similarity": "cosine" }
    }
  }
}
```

### 3.5 接口设计

```
POST /ai/ask
请求体:
{
  "question": "3000元左右适合学生的手机",
  "topK": 5
}

响应:
{
  "code": 200,
  "data": {
    "answer": "根据您的需求，为您推荐以下手机：\n\n1. **荣耀X50** ...",
    "relatedProducts": [
      { "spuId": 10001, "name": "荣耀X50", "title": "...", "listPrice": 1579.00,
        "brandName": "荣耀", "categoryName": "手机", "picture": "http://...",
        "tags": "5G,大电池", "sales": 5000, "score": 0.92 }
    ]
  }
}
```

### 3.6 已实现功能清单

| 功能 | 状态 | 文件 |
|------|------|------|
| RAG 问答接口 `POST /ai/ask` | ✅ | `AiController.java` |
| RAG 核心流程（检索→上下文→AI 回答） | ✅ | `RagServiceImpl.java` |
| ES 全文检索（IK 分词 multi_match） | ✅ | `RagServiceImpl.fullTextSearch()` |
| ES 向量检索（cosineSimilarity，预留） | ✅ | `RagServiceImpl.vectorSearch()` |
| ES 索引自动初始化 | ✅ | `EsIndexInitializer.java` |
| 商品数据全量/增量同步到 ES | ✅ | `VectorSyncServiceImpl.java` |
| Token 预算控制（Redis 日预算上限） | ✅ | `TokenBudgetService.java` |
| 检索模式配置开关 | ✅ | `AiProperties.embeddingEnabled` |

### 3.7 Embedding 向量语义检索扩展点

由于 DeepSeek V4 当前不提供 embedding API，项目中已预留完整的向量检索扩展点。要切换到向量语义检索模式，只需：

1. 选择一个 embedding API 供应商（阿里通义千问 / 智谱 GLM / 百度千帆等）
2. 实现该供应商的 embedding 客户端（实现 `AiClient.embed()` / `embedBatch()` 接口）
3. 配置文件 `application.yml` 中将 `cooxiao.ai.embedding-enabled` 改为 `true`
4. 调用 `POST /ai/sync` 重新同步全量商品（生成语义向量）
5. 无需修改任何检索逻辑代码

详见 `TODO文件.md` → §5 Embedding 向量语义检索扩展点。

### 3.8 商品变更自动同步（场景二）

mall-product 模块新增/修改商品后，通过 **Dubbo RPC** 自动触发 mall-ai 的 ES 同步，无需手动调用 `/ai/sync/{spuId}`：

```
mall-product SpuServiceImpl.addNew() / updateById()
  → @DubboReference ISpuSyncService.syncSpu(spuId)
  → mall-ai SpuSyncDubboServiceImpl（@DubboService）
  → VectorSyncServiceImpl.syncSpu(spuId)
  → ES upsert
```

| 组件 | 文件 |
|------|------|
| Dubbo 接口 | `mall-ai-service/.../ISpuSyncService.java` |
| Dubbo 提供者 | `mall-ai-webapi/.../SpuSyncDubboServiceImpl.java` |
| Dubbo 消费者 | `mall-product-webapi/.../SpuServiceImpl.java`（@DubboReference） |

同步失败仅记录日志，不阻断商品增删改主流程。

### 3.9 风险与注意事项

| 风险 | 应对 |
|------|------|
| ES 7.17 dense_vector 无 HNSW | 全文检索作为首选方案，语义检索为可选升级 |
| AI 幻觉 | Prompt 严格约束基于检索结果回答；不做信息编造 |
| API 调用成本 | Redis 实现日预算上限（默认 10 元），超限自动拒绝服务 |
| Embedding API 404 | DeepSeek V4 无 embedding，当前使用 ES 全文检索兜底，长期可接其他供应商 |

---

## 四、第三阶段：多轮对话导购 ✅（已完成）

> **实施时间**：2026-05-15  
> **当前状态**：已完成

### 4.1 目标

在 RAG 基础上扩展多轮对话能力，支持上下文记忆。AI 能逐步引导用户明确需求、缩小选择范围，模拟真人导购体验。

### 4.2 技术方案（已实现）

- **会话管理**：`SessionManager` → Redis JSON 序列化存储（24h TTL）
- **上下文记忆**：每轮对话完整追加到 messages 列表，AI 调用时传入完整历史
- **偏好提取**：`PreferenceExtractor` → 每轮对话后让 AI 从最近 3 轮提取偏好 JSON（预算、类别、品牌、用途、额外需求）
- **消息裁剪**：超过 30 条消息后自动裁剪最早的消息
- **商品检索复用**：直接复用 `RagServiceImpl.fullTextSearch()` + `buildContext()` + `buildRelatedProducts()`，含价格范围过滤
- **预算控制复用**：直接复用 `TokenBudgetService.isBudgetExceeded()`

### 4.3 架构

```
POST /ai/chat/send  { sessionId, message }
       │
       ▼
┌─────────────────────┐
│   ChatServiceImpl    │
├─────────────────────┤
│ 1. SessionManager   │
│    .load(sessionId) │
│ 2. TokenBudgetService│
│    .isBudgetExceeded│
│ 3. RagServiceImpl   │
│    .fullTextSearch  │
│    .buildContext    │
│    .buildRelatedProd│
│ 4. AiClient.chat    │
│    (历史+偏好+商品)  │
│ 5. PreferenceExtract│
│ 6. SessionManager   │
│    .save(session)   │
└──────┬──────────────┘
       │
┌──────┴──────┐   ┌────────┐
│ Redis       │   │ ES     │
│ 会话历史缓存  │   │ 商品检索 │
└─────────────┘   └────────┘
```

### 4.4 接口设计（已实现）

- `POST /ai/chat/session` — 创建会话，返回 sessionId + 欢迎语
- `POST /ai/chat/send` {sessionId, message} — 发送消息，返回 {reply, preferences, relatedProducts}
- `GET /ai/chat/history?sessionId=xxx` — 获取历史 {messages, preferences}

### 4.5 已实现文件清单

| 文件 | 说明 |
|------|------|
| `mall-pojo/.../ai/dto/ChatSendDTO.java` | 发送消息 DTO |
| `mall-pojo/.../ai/vo/ChatResultVO.java` | 聊天结果 VO |
| `mall-pojo/.../ai/vo/ChatHistoryVO.java` | 历史记录 VO |
| `mall-pojo/.../ai/model/ChatSession.java` | 会话模型 |
| `mall-pojo/.../ai/model/ChatMessage.java` | 消息模型 |
| `mall-ai/.../service/SessionManager.java` | Redis 会话管理 |
| `mall-ai/.../service/PreferenceExtractor.java` | 偏好提取 |
| `mall-ai/.../service/impl/ChatServiceImpl.java` | 对话核心编排 |
| `mall-ai/.../controller/AiController.java` | 新增 3 个对话接口 |

### 4.6 风险应对

| 风险 | 已采取的应对 |
|------|-------------|
| 长对话 Token 消耗 | 最多 30 条消息自动裁剪；偏好提取只取最近 3 轮；复用 `TokenBudgetService` 预算控制 |
| AI 偏好提取不稳定 | `PreferenceExtractor` 用 JSON parse + fallback 空 Map 兜底（与 Phase 1 JSON 解析一致） |
| Redis 内存 | 24h TTL 自动过期；单会话最多 30 条消息 |
| sessionId 泄露 | 由后端 UUID 生成；前端只透传 |

---

## 五、第四阶段：语义搜索 ✅（已完成） + 企业级升级

> **完成时间**：2026-07-18  
> **实际实现远超原计划**：不仅完成了向量语义检索，还追加了 SSE 流式输出、搜索流水线、查询扩展等企业级能力

### 5.1 实际实现 vs 原计划

| 原计划 | 实际实现 |
|--------|---------|
| 待开发，预计 6 天 | ✅ 已完成 |
| 计划用 DeepSeek embedding | 实际接入**硅基流动 BGE-M3**（免费，1024维） |
| 关键词 + 向量混合搜索 (0.4/0.6) | 实际实现**搜索流水线**：意图提取 → AI 查询扩展 → 多路召回 → 融合排序 |
| 计划改 `mall-search` 模块 | 实际在 `mall-ai` 内实现，不侵入现有搜索模块 |
| 无流式输出 | ✅ SSE 逐字渲染 |
| 无思考可视化 | ✅ 前端 thinking 卡片动画 |
| 无查询扩展 | ✅ AI 驱动查询扩展（"衣服"→"男装 女装 服装 鞋类"） |
| 无 IK 同义词 | ✅ 已创建 synonym.dic（衣服/男装/女装/鞋子等） |

### 5.2 搜索流水线架构

```
用户输入 "1000以内的衣服鞋子推荐"
  │
  ├─ Stage 0: 🤖 AI 意图提取
  │     └─ 输出 JSON → { budgetMax:1000, category:"衣服", keywords:"衣服 鞋子" }
  │
  ├─ Stage 1: 🔍 预处理 + AI 查询扩展
  │     └─ "衣服" → DeepSeek → "男装 女装 服装 服饰 鞋类 运动鞋"
  │
  ├─ Stage 2: 🔎 多路召回
  │     ├─ 关键词路: multi_match(IK) + 价格 range filter → Top-10
  │     └─ 全文路: multi_match 无价格过滤 → Top-10（兜底）
  │
  ├─ Stage 3: 🎯 融合排序
  │     └─ 两路去重合并 → 按相关性排序
  │
  └─ Stage 4: 💬 AI 生成回答
        └─ 上下文 + 商品列表 → DeepSeek → SSE 流式输出
```

### 5.3 新增核心文件

| 文件 | 说明 |
|------|------|
| `mall-ai/.../client/SiliconFlowEmbeddingClient.java` | 硅基流动 BGE-M3 embedding 客户端 |
| `mall-ai/.../model/SearchIntent.java` | AI 搜索意图结构化参数 |
| `mall-ai/.../service/SearchPipeline.java` | 5 阶段搜索流水线 + thinking 回调 |

### 5.4 新增接口

| 端点 | 说明 |
|------|------|
| `POST /ai/chat/stream` | SSE 流式聊天（逐字渲染 + 思考过程） |

### 5.5 Embedding 服务切换

| 对比 | 原计划 (DeepSeek) | 实际 (硅基流动) |
|------|-------------------|----------------|
| 模型 | text-embedding-v3 | BAAI/bge-m3 |
| 维度 | 512 | 1024 |
| 费用 | 付费 | **免费**（2000万 tokens） |
| 中文效果 | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| 状态 | /v1/embeddings 返回 404 | ✅ 已接入并用

---

## 六、实施总结

### 6.1 全部阶段完成状态

```
第一阶段 ──→ 第二阶段 ──→ 第三阶段 ──→ 第四阶段 + 企业级升级
（✅ 完成）   （✅ 完成）    （✅ 完成）    （✅ 全部完成）
```

### 6.2 工作量汇总

| 阶段 | 计划 | 实际 | 状态 |
|------|------|------|------|
| 一：商品对比 | 1-2d | ~1d | ✅ |
| 二：RAG 问答 | 3-5d | ~3d | ✅ |
| 三：多轮对话 | 2-3d | ~2d | ✅ |
| 四：语义搜索 | 5-7d | — | ✅ |
| 企业级升级 | 未计划 | ~3d | ✅ (SSE + 流水线 + 查询扩展) |
| **合计** | **~16d** | **~9d** | ✅ 100% 完成 |

### 6.3 依赖的外部服务

| 服务 | 用途 | 供应商 | 费用 |
|------|------|--------|------|
| Chat API | 对话/对比/问答/意图提取 | DeepSeek V4 Flash | ¥1-2/百万 tokens |
| Embedding API | 向量语义检索 | 硅基流动 BGE-M3 | **免费** |

### 6.4 当前配置项清单（application.yml）

```yaml
cooxiao:
  ai:
    api-key: ${AI_API_KEY:sk-placeholder}
    base-url: ${AI_API_BASE_URL:https://api.deepseek.com}
    chat-model: deepseek-v4-flash
    compare-model: deepseek-v4-flash
    temperature: 0.7
    max-tokens: 2000
    timeout: 15000
    # 向量检索（硅基流动 BGE-M3，免费）
    embedding-enabled: true               # test:true / prod:false
    embedding-api-key: ${EMBEDDING_API_KEY}
    embedding-base-url: https://api.siliconflow.cn
    embedding-model: BAAI/bge-m3
    embedding-dimensions: 1024
    embedding-price-per-million: 0.0      # BGE-M3 免费
    # 预算控制
    daily-budget: 2.0                     # 全局 2 元/天
    chat-input-price-per-million: 1.0
    chat-output-price-per-million: 2.0
    # 部署运维
    sync-auto-on-startup: false
    sync-whitelisted: true
```

### 6.5 全部接口清单

| 端点 | 方法 | 阶段 | 说明 |
|------|------|------|------|
| `/ai/compare` | POST | 一 | AI 商品对比 |
| `/ai/ask` | POST | 二 | RAG 智能问答 |
| `/ai/chat/session` | POST | 三 | 创建多轮会话 |
| `/ai/chat/send` | POST | 三 | 发送消息（同步） |
| `/ai/chat/stream` | POST | 升级 | **SSE 流式消息**（逐字渲染 + 思考过程） |
| `/ai/chat/history` | GET | 三 | 对话历史 |
| `/ai/sync` | POST | 二 | 全量商品同步到 ES |
| `/ai/sync/{spuId}` | POST | 二 | 增量同步单个 SPU |

---

> **更新日期**: 2026-07-18  
> **文档状态**: 四阶段全部完成，企业级升级已交付

