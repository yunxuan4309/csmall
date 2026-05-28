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

## 五、第四阶段：语义搜索（预计 5-7 天）

### 5.1 目标

实现商品全量向量化，提供高级语义搜索。用户可用模糊的自然语言描述搜索商品（如"适合送女朋友的生日礼物"），系统基于语义匹配返回最相关结果，与竞品形成差异化优势。

### 5.2 技术方案

对 **现有 mall-search 模块** 进行扩展，增加语义搜索能力，与现有关键词搜索共存：

- **全量商品向量化**：为每个 SPU 生成语义向量（名称 + 标题 + 描述 + 分类 + 标签）
- **混合搜索架构**：关键词精确匹配 + 向量语义相似度的加权组合（关键词 0.4 + 向量 0.6）
- **搜索前端统一**：与现有 `/search` 接口保持一致，通过 `mode=semantic` 参数区分

### 5.3 搜索架构

```
GET /search/semantic?query=性价比高的办公笔记本&page=1&pageSize=20
       │
       ▼
┌────────────────┐
│ SearchService   │
└────┬───────────┘
     │ 1. 调用 AI embedding 将用户查询转为向量
     │ 2. ES 混合查询（two-phase）
     │    - 阶段一：关键词检索 + 向量近似检索
     │    - 阶段二：结果融合与重排序
     │ 3. 返回排序结果
     │
     ├───▶ ES multi_match（关键词） weight=0.4
     │
     └───▶ ES script_score（向量余弦） weight=0.6
```

### 5.4 索引设计

在现有 `cool_shark_mall_index2` 索引基础上，对每个文档添加 `semantic_vector` 字段：

```json
PUT /cool_shark_mall_index2/_mapping
{
  "properties": {
    "semantic_vector": {
      "type": "dense_vector",
      "dims": 1024,
      "index": true,
      "similarity": "cosine"
    }
  }
}
```

语义文本拼接规则：
```text
商品名称：{name} | 标题：{title} | 描述：{description}
| 品牌：{brandName} | 分类：{categoryName} | 标签：{tags}
```

### 5.5 接口设计

```
GET /search/semantic?query=性价比高的办公笔记本&page=1&pageSize=20
响应:
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1,
        "name": "商品A",
        "listPrice": 4999,
        "brandName": "品牌X",
        "score": 0.92
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 50,
    "totalPage": 3
  }
}
```

### 5.6 关键实现

1. **ES 混合查询 DSL**：
   ```json
   {
     "size": 20,
     "query": {
       "bool": {
         "should": [
           { "script_score": {
               "query": { "match_all": {} },
               "script": {
                 "source": "cosineSimilarity(params.vector, 'semantic_vector') + 1.0",
                 "params": { "vector": [...] }
               },
               "boost": 0.6
           }},
           { "multi_match": {
               "query": "性价比高的办公笔记本",
               "fields": ["name^3", "title^2", "description", "category_name"],
               "type": "best_fields",
               "boost": 0.4
           }}
         ]
       }
     }
   }
   ```

2. **数据同步**：
   - **全量**：启动时遍历所有已上架 SPU，调用 embedding API 生成向量，batch 写入 ES
   - **增量**：在 mall-product 暴露 Dubbo 接口 `notifySpuUpdate(Long spuId)`，由商品更新时触发
   - **定时**：设置凌晨低峰期定时全量同步（备份保障）

3. **降级策略**：向量检索异常时自动降级为纯关键词搜索，不影响用户体验

### 5.7 需要新增/修改的文件

| 文件路径 | 说明 |
|----------|------|
| `mall-search/.../search/service/impl/SemanticSearchServiceImpl.java` | 语义搜索实现 |
| `mall-search/.../search/controller/SearchController.java` | 新增 `GET /search/semantic` |
| `mall-search/.../search/service/ISearchService.java` | 接口扩展 |
| `mall-pojo/.../search/entity/SpuForElastic.java` | 新增 semantic_vector 字段 |

### 5.8 风险与注意事项

| 风险 | 应对 |
|------|------|
| ES 7.17 向量检索性能 | 设置 `num_candidates` 上限；关键词作为兜底；大数据量考虑升级 ES 8.x |
| Embedding 成本 | 全量导入约 ¥3-5（5000 SPU × 1 次 API/SPU）；增量更新极少 |
| 分数校准 | 两路分数做 min-max 归一化后再加权合并 |
| 搜索延迟增加 | 预期增加 100-300ms；前端做 debounce |

---

## 六、总体实施建议

### 6.1 实施顺序与依赖关系

```
第一阶段 ──→ 第二阶段 ──→ 第三阶段 ──→ 第四阶段
（无依赖）    （依赖一）    （依赖二）    （可独立/依赖二）
```

- 第一阶段可独立开发部署，与后续阶段无代码耦合
- 第二阶段需要在 ES 中新建索引和向量字段，AiClient 可复用第一阶段代码
- 第三阶段依赖第二阶段的 RAG 能力，新增会话管理
- 第四阶段改造现有 `mall-search`，可与前三阶段并行开发

### 6.2 工作量汇总

| 阶段 | 开发 | 测试 | 部署 | 合计 | 复杂度 |
|------|------|------|------|------|--------|
| 一 | ✅ 已完成 | — | — | — | ⭐ |
| 二 | ✅ 已完成 | — | — | — | ⭐⭐⭐ |
| 三 | ✅ 已完成 | — | — | — | ⭐⭐ |
| 四 | 待开发 | 3d | 2d | **~6d** | ⭐⭐⭐⭐ |

### 6.3 依赖的外部服务

| 服务 | 用途 | 推荐供应商 | 备注 |
|------|------|-----------|------|
| Chat API | 生成对话/对比/问答 | DeepSeek / 通义千问 | 第一阶段用 chat，第三、四阶段也用 |
| Embedding API | 生成文本向量 | DeepSeek text-embedding-v3 / 通义千问 embedding | 第二、四阶段使用，维度 1024 |

### 6.4 部署注意事项

1. `mall-ai` 部署在现有 ECS（4C16G），端口 `10010`，通过 systemd 管理
2. 环境变量 `csmall.env` 新增：`AI_API_KEY`、`AI_API_BASE_URL`
3. Gateway 添加 `/ai/**` 路由
4. AI API 调用是外部 HTTPS 请求，需确保 ECS 出方向网络畅通
5. 所有阶段代码均通过 Git 管理，按 `feat(ai): 描述` 格式提交
6. **生产部署后自动同步**：`sync-auto-on-startup=true` 会在启动时自动将商品数据同步到 ES，无需手动操作
7. **生产环境 sync 接口需要鉴权**：`sync-whitelisted=false`，只有通过 SSO 登录的管理员才能调

### 6.5 当前配置项清单（application.yml）

```yaml
cooxiao:
  ai:
    api-key: ${AI_API_KEY:sk-placeholder}
    base-url: ${AI_API_BASE_URL:https://api.deepseek.com}
    chat-model: deepseek-v4-flash
    temperature: 0.7
    max-tokens: 2000
    timeout: 15000
    # 向量检索（预留）
    embedding-enabled: false
    embedding-model: deepseek-v4-flash
    embedding-dimensions: 512
    # 预算控制
    daily-budget: 10.0
    chat-input-price-per-million: 1.0
    chat-output-price-per-million: 2.0
    embedding-price-per-million: 1.0
    # 部署运维
    sync-auto-on-startup: false   # test:false / prod:true
    sync-whitelisted: false       # test:true  / prod:false
```

### 6.6 开发/生产环境差异

| 事项 | test | prod |
|------|------|------|
| ES 初始化 | 手动 Swagger `POST /ai/sync` | 启动时自动 `SyncOnStartupRunner` |
| sync 鉴权 | 白名单免鉴权 | 需 JWT token |
| Knife4j | 可访问 | 关闭 |
| 日志级别 | trace | info |

