# AI 智能导购模块 — 后端接口需求文档

## 1. 接口概述

AI 智能导购模块（`mall-ai`）是为 CoolShark 电商平台提供 AI 驱动的智能化购物助手功能。该模块以 **RAG（检索增强生成）** 架构为核心，通过 Elasticsearch 检索商品后将结果作为上下文注入大语言模型，生成准确、可解释的智能导购体验。

**当前检索模式**：ES IK 分词全文检索（当前默认，无需 embedding API）+ 向量语义检索（预留扩展，一键切换）。

### 功能范围

| 子功能 | 定位 | 范围 |
|--------|------|------|
| **RAG 智能问答** | 核心功能 | 用户用自然语言提问 → ES 检索相关商品 → AI 生成回答（含商品推荐列表） |
| **AI 商品对比** | 辅助决策 | 选择 2~4 个商品 → AI 从多维度结构化对比 → 输出对比表格 + 综合推荐 |
| **多轮对话导购** | 进阶功能 | 创建会话 → 连续对话（带上下文记忆）→ AI 逐步引导筛选 → 偏好自动提取 |
| **商品数据同步** | 基础设施 | 将数据库商品数据同步到 ES，为问答功能提供数据基础 |

### 技术栈

| 组件 | 技术选型 |
|------|----------|
| 框架 | Spring Boot 3.2.5 + Spring Cloud Alibaba |
| RPC | Apache Dubbo 3.3.2（从 mall-product 获取 SPU 数据） |
| 搜索引擎 | Elasticsearch 7.17.29（IK 分词 + dense_vector 两种检索） |
| 检索方式 | 全文检索（IK `multi_match`，当前默认）/ 向量语义检索（`cosineSimilarity`，预留） |
| 对话模型 | DeepSeek V4 Flash（`deepseek-v4-flash`） |
| 统一响应 | `JsonResult<T>` 统一包装 |
| 认证鉴权 | JWT（通过 SSOFilter 统一校验） |
| 成本控制 | Redis 日预算计数器（默认 10 元/天） |

---

## 2. 接口列表

### 2.1 RAG 智能问答

#### 接口标识

> `POST /ai/ask`

#### 功能描述

接收用户自然语言问题，通过以下流程处理：
1. **预算检查**：查询 Redis 中日累计 AI 费用，超限则直接返回"服务繁忙"
2. **ES 检索商品**：根据配置选择检索方式
   - **全文检索（当前默认）**：用户问题通过 ES `multi_match` + IK 分词对商品字段加权匹配（名称^5 > 标题^4 > 语义文本^3 > 描述^2 > 品牌、分类、标签）
   - **向量检索（预留扩展）**：用户问题通过 Embedding API 转为向量，在 ES 中 `cosineSimilarity` 匹配语义向量
3. 将召回商品的名称、标题、描述、品牌、分类、价格、销量、标签等信息拼接为上下文
4. 将上下文 + 用户问题发给 AI 聊天 API（DeepSeek V4 Flash），要求 AI 基于提供的商品信息回答问题
5. 返回 AI 回答 + 相关商品列表

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|------|--------|------|
| `Authorization` | Header | string | 是 | — | JWT Bearer Token |
| `question` | Body | string | 是 | — | 用户自然语言问题 |
| `topK` | Body | int | 否 | 5 | 召回相关商品数量 |

#### 请求体示例

```json
{
  "question": "3000元左右适合学生用的手机推荐",
  "topK": 5
}
```

#### 返回数据结构

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 状态码（200 成功） |
| `message` | string | 状态信息 |
| `data.answer` | string | AI 生成的回答文本（Markdown 格式） |
| `data.relatedProducts[]` | array | 检索到的相关商品列表 |
| `relatedProducts[].spuId` | long | SPU ID，用于前端跳转商品详情页 |
| `relatedProducts[].name` | string | 商品名称 |
| `relatedProducts[].title` | string | 商品标题 |
| `relatedProducts[].listPrice` | number | 价格（元） |
| `relatedProducts[].brandName` | string | 品牌名称 |
| `relatedProducts[].categoryName` | string | 分类名称 |
| `relatedProducts[].picture` | string | 商品图片 URL |
| `relatedProducts[].tags` | string | 标签（逗号分隔） |
| `relatedProducts[].sales` | int | 销量 |
| `relatedProducts[].score` | double | 向量匹配分数（0~2，越高越相关） |

#### 返回数据示例

```json
{
  "code": 200,
  "message": "OK",
  "data": {
    "answer": "根据商品信息，为您推荐以下几款手机：\n\n1. **荣耀X50** - 价格仅1579元，配备5800mAh大电池...\n\n**购买建议：** 如果预算在3000元左右，荣耀X50性价比最高...",
    "relatedProducts": [
      {
        "spuId": 10001,
        "name": "荣耀X50",
        "title": "荣耀X50 5G手机 5800mAh大电池 骁龙6",
        "listPrice": 1579.00,
        "brandName": "荣耀",
        "categoryName": "手机",
        "picture": "http://images/image.jpg",
        "tags": "5G,大电池,性价比",
        "sales": 5000,
        "score": 0.92
      }
    ]
  }
}
```


### 2.2 AI 商品对比

#### 接口标识

> `POST /ai/compare`

#### 功能描述

选择多个商品后进行多维度 AI 对比分析：
1. 通过 Dubbo 从 mall-product 获取各 SPU 详情（含名称、标题、价格、品牌、分类、描述、标签、关键词、销量、评价数等）
2. 将商品信息 + 对比维度发给 AI，要求按指定 JSON 格式输出结构化对比结果
3. 解析 AI 返回的 JSON，组装为统一的对比结果返回
4. AI 响应解析失败时降级：返回原始商品列表 + 降级提示，保证前端有数据可展示

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| `Authorization` | Header | string | 是 | JWT Bearer Token |
| `spuIds` | Body | array<long> | 是 | 要对比的 SPU ID 列表，最少 2 个，最多 4 个 |
| `dimensions` | Body | array<string> | 否 | 对比维度列表。不传时使用默认维度：`["规格参数","价格","核心卖点","适用场景","优缺点"]` |

#### 请求体示例

```json
{
  "spuIds": [10001, 10002, 10003],
  "dimensions": ["性能", "拍照", "续航", "性价比"]
}
```

#### 返回数据结构

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 状态码（200 成功，500 AI 服务不可用） |
| `message` | string | 状态信息 |
| `data.dimensions` | array<string> | 对比维度列表 |
| `data.products[]` | array | 参与对比的商品列表 |
| `products[].id` | long | SPU ID |
| `products[].name` | string | 商品名称 |
| `products[].picture` | string | 商品第一张图片 URL |
| `products[].dimensionValues[]` | array<string> | 各维度的 AI 评价，与 dimensions 顺序对应 |
| `data.summary` | string | AI 综合推荐建议 |

#### 返回数据示例

```json
{
  "code": 200,
  "message": "OK",
  "data": {
    "dimensions": ["性能", "拍照", "续航", "性价比"],
    "products": [
      {
        "id": 10001,
        "name": "荣耀X50",
        "picture": "http://images/image1.jpg",
        "dimensionValues": [
          "骁龙6 Gen1，中端处理器，日常使用流畅",
          "1亿像素主摄，夜景模式出色",
          "5800mAh大电池，续航优秀",
          "1579元，性价比极高"
        ]
      }
    ],
    "summary": "综合对比，荣耀X50在续航和性价比方面表现最佳...如果你是重度手机用户，推荐选择荣耀X50。"
  }
}
```


### 2.3 多轮对话导购（Phase 3）

#### 2.3.1 创建会话 — `POST /ai/chat/session`

创建导购对话会话，返回 `sessionId` 和欢迎语。

| 字段 | 类型 | 说明 |
|------|------|------|
| `data.sessionId` | string | 会话 ID，前端保存，后续请求传入 |
| `data.reply` | string | AI 欢迎语 |
| `data.preferences` | object | 用户偏好（初始为空） |
| `data.relatedProducts` | array | 初始为空 |

#### 2.3.2 发送消息 — `POST /ai/chat/send`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `sessionId` | string | 否 | 首次传 null，后端创建 |
| `message` | string | 是 | 用户消息 |

请求体：`{"sessionId":"...", "message":"我想买3000元手机"}`

返回 `{sessionId, reply, preferences: {budget, category...}, relatedProducts: [...]}`

**处理流程**：加载会话 → ES 商品检索 → 拼接历史+偏好+商品 → AI 回复 → 存储 → 偏好提取

#### 2.3.3 获取历史 — `GET /ai/chat/history?sessionId=xxx`

返回 `{sessionId, messages: [{role, content, timestamp}], preferences}`

**会话管理**：Redis key `ai:chat:session:{id}`，TTL 24h，最多 30 条消息，偏好自动累积。

---

### 2.4 全量同步商品数据到 ES

#### 接口标识

> `POST /ai/sync`

#### 功能描述

将数据库中所有 SPU 全量同步到 ES，为 RAG 问答提供数据基础：
1. 通过 Dubbo 分页拉取所有 SPU（每页 500 条）
2. 遍历每个 SPU，拼接语义文本：`"商品名称：{name} | 标题：{title} | 描述：{description} | 品牌：{brandName} | 分类：{categoryName} | 标签：{tags}"`
3. 逐批写入 ES `cool_shark_mall_ai` 索引
4. 若 `embedding-enabled=true` 则同步生成语义向量

**调用场景：**
- **首次部署**：数据同步完成后，RAG 问答功能才能正常使用
- **数据重建**：如果 ES 数据丢失或索引重建，重新同步
- **日常运行**：商品增删改由 Dubbo `ISpuSyncService` 自动增量同步，无需手动调用此接口

**注意事项：**
- 该接口是同步执行，数据量大时可能耗时较长
- 若某批次失败，跳过该批次继续处理，不做整体回滚
- 已加入 Swagger 白名单，开发阶段无需 token

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| 无需参数 | — | — | — | 该接口无请求体 |

#### 返回数据示例

```json
{
  "code": 200,
  "message": "OK",
  "data": "同步完成，共 156 条商品"
}
```

**数据库无商品时：**

```json
{
  "code": 200,
  "message": "OK",
  "data": "同步完成，共 0 条商品"
}
```


### 2.5 增量同步单个 SPU 到 ES

#### 接口标识

> `POST /ai/sync/{spuId}`

#### 功能描述

同步单个 SPU 到 ES（覆盖更新）。该接口已被 mall-product 模块的 `SpuServiceImpl` 自动触发：

- **自动触发**：mall-product 新增/修改商品后，通过 Dubbo `ISpuSyncService.syncSpu(spuId)` 自动调用，无需手动操作
- **手动触发**：仅在调试或修复数据时手动调用

**处理逻辑：**
1. 通过 Dubbo 获取指定 SPU 详情
2. 拼接语义文本，写入 ES 索引（覆盖更新）
3. SPU 不存在时仅输出 warn 日志，不抛异常

#### 请求参数

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| `spuId` | Path | long | 是 | 要同步的 SPU ID |

#### 返回数据示例

```json
{
  "code": 200,
  "message": "OK",
  "data": "SPU 10001 同步完成"
}
```

---

### 2.6 开发环境 vs 生产环境差异

| 事项 | 开发环境（test） | 生产环境（prod） |
|------|-----------------|-----------------|
| **ES 数据初始化** | 手动：Swagger 调 `POST /ai/sync` | 自动：启动时 `SyncOnStartupRunner` 自动执行全量同步 |
| **sync 接口鉴权** | 已加入白名单，无 token 可调 | 需要 JWT token 鉴权 |
| **Knife4j 文档** | 开启（`doc.html` 可访问） | 关闭（`production: true`） |
| **E 全量同步方式** | 临时需要时手动调 `/ai/sync` | 部署后自动同步，后续通过 Dubbo 增量更新 |

**配置差异：**

| 配置项 | application-test.yml | application-prod.yml |
|--------|---------------------|---------------------|
| `cooxiao.ai.sync-whitelisted` | `true` | `false`（默认） |
| `cooxiao.ai.sync-auto-on-startup` | 无（默认 `false`） | `true` |

**生产部署流程：**

```
1. 部署 mall-ai JAR 包到服务器
2. systemd 启动服务
3. SyncOnStartupRunner 检测到 sync-auto-on-startup=true
4. 自动拉取所有 SPU → 批量写入 ES
5. 日志输出 "启动自动同步完成，共 N 条商品"
6. RAG 问答功能就绪
```

**后续商品更新**（生产与开发一致）：

```
mall-product 运营后台新增/编辑商品
  → Dubbo ISpuSyncService.syncSpu(spuId)
  → mall-ai ES 增量更新
  → 无需人工干预
```

---

## 3. 数据来源说明

### 3.1 数据源总览

| 接口 | 数据源 | 获取方式 |
|------|--------|----------|
| `POST /ai/ask` | ES 索引 `cool_shark_mall_ai` | `ElasticsearchClient` 全文检索 / 向量检索 |
| `POST /ai/ask` | DeepSeek Chat API | `RestTemplate` POST 调用 |
| `POST /ai/compare` | mall-product 数据库表 | Dubbo RPC `IForFrontSpuService.getSpuById()` |
| `POST /ai/sync` | mall-product 数据库表 | Dubbo RPC `IForFrontSpuService.getSpuByPage()` |
| `POST /ai/sync/{spuId}` | mall-product 数据库表 | Dubbo RPC `IForFrontSpuService.getSpuById()` |
| 预算控制 | Redis | `StringRedisTemplate` 日累计计数器 |

### 3.2 ES 索引结构

**索引名**: `cool_shark_mall_ai`（启动时通过 `EsIndexInitializer` 自动创建）

| 字段 | ES 类型 | analyzer | 说明 |
|------|---------|----------|------|
| `spuId` | long | — | SPU 主键 ID |
| `name` | text | ik_max_word | 商品名称 |
| `title` | text | ik_max_word | 商品标题 |
| `description` | text | ik_max_word | 商品描述 |
| `categoryName` | keyword | — | 分类名称 |
| `brandName` | keyword | — | 品牌名称 |
| `listPrice` | double | — | 价格 |
| `pictures` | keyword | — | 商品图片（多个 URL 逗号分隔） |
| `tags` | keyword | — | 标签 |
| `sales` | integer | — | 销量 |
| `semanticText` | text | ik_max_word | 语义拼接文本 |
| `semanticVector` | dense_vector | — | 语义向量（维度 1024，cosine 相似度） |

**当前检索方式**：`multi_match` 全文检索（IK 分词，字段权重：name^5, title^4, semanticText^3, description^2, brandName, categoryName, tags）

**向量检索（预留）**：`script_score` 查询 + `cosineSimilarity(params.queryVector, 'semanticVector') + 1.0`（通过 `embedding-enabled: true` 切换）

### 3.3 Dubbo 接口依赖

Dubbo 服务接口定义在 `mall-product-service` 模块中：

```java
// com.cooxiao.mall.product.service.front.IForFrontSpuService
public interface IForFrontSpuService {
    SpuStandardVO getSpuById(Long id);                          // 根据 ID 获取 SPU 详情
    JsonPage<Spu> getSpuByPage(int page, int pageSize);         // 分页获取 SPU
}
```

### 3.4 AI API 依赖

| 功能 | API 地址 | 模型 | 配置项 |
|------|----------|------|--------|
| 聊天对话 | `POST {baseUrl}/chat/completions` | `deepseek-v4-flash` | `cooxiao.ai.chat-model` |
| 文本向量化 | `POST {baseUrl}/embeddings` | `deepseek-v4-flash`（预留） | `cooxiao.ai.embedding-model` |

AI 供应商可切换（当前为 DeepSeek V4），通过 `AiClient` 接口隔离具体实现。必须配置：

```yaml
cooxiao:
  ai:
    api-key: sk-xxxxxxxxxxxxx
    base-url: https://api.deepseek.com
    chat-model: deepseek-v4-flash
    temperature: 0.7
    max-tokens: 2000
    timeout: 15000
    # 向量检索开关（当前关闭）
    embedding-enabled: false
    embedding-model: deepseek-v4-flash
    embedding-dimensions: 512
    # 预算控制
    daily-budget: 10.0
    chat-input-price-per-million: 1.0
    chat-output-price-per-million: 2.0
    embedding-price-per-million: 1.0
```

---

## 4. 业务逻辑要求

### 4.1 RAG 智能问答（`POST /ai/ask`）

#### 核心流程

```
用户问题 → 预算检查 → ES 检索（全文/向量二选一） → 拼接检索结果 →
  AI Chat API(RAG Prompt) → 返回 AI 回答 + 相关商品列表
```

#### 处理规则

1. **预算控制**（最先执行）：
   - 查询 Redis key `ai:daily_cost:YYYY-MM-DD` 获取当日累计费用
   - 超过 `daily-budget`（默认 10 元）则直接拒绝，返回"服务繁忙"，不消耗任何 API 费用
   - 每次 API 调用后从响应中提取 `usage`（prompt_tokens + completion_tokens），按配置单价计算费用并累加

2. **检索方式**（配置项 `embedding-enabled` 控制）：
   - **全文检索（`false`，当前默认）**：ES `multi_match` 查询，字段权重 `name^5,title^4,semanticText^3,description^2,brandName,categoryName,tags`。IK 分词自动处理中文分词
   - **向量检索（`true`，预留）**：调用 embedding API → `cosineSimilarity + 1.0`（分数 0~2），按相似度降序
   - topK 默认 5，前端可调，最大建议不超过 20
   - ES 检索失败时返回空列表，回答提示"未检索到相关商品"

3. **上下文构建**：
   - 将每个召回的商品字段（名称、标题、描述、品牌、分类、价格、销量、标签）拼接为可读文本
   - 字段值为 null 时替换为空字符串

4. **AI 生成约束（通过 system prompt 实现）**：
   - 只基于提供的商品信息回答，不编造不存在的商品或信息
   - 检索到的商品与问题不相关时，如实告知用户
   - 回答简洁清晰，推荐商品时要说明理由
   - 可以给出购买建议和注意事项

5. **降级处理**：
   - AI Chat API 调用失败时，返回兜底提示："很抱歉，AI 服务暂时不可用，请稍后重试"
   - 即使回答失败，相关商品列表仍正常返回

### 4.2 AI 商品对比（`POST /ai/compare`）

#### 核心流程

```
接收 spuIds → Dubbo 获取各 SPU 详情 → 去除不存在的 SPU →
  拼接商品信息 + 对比维度 → AI Chat API(JSON 输出约束) →
  解析 AI 返回的 JSON → 返回结构化对比结果
```

#### 处理规则

1. **参数校验**：
   - spuIds 最少 2 个，最多 4 个（由 `@Size(min=2, max=4)` 校验）
   - 通过 Dubbo 获取 SPU 时忽略不存在的 ID，如果一个都没找到则返回 404

2. **对比维度**：
   - 用户可自定义维度（如 "性能"、"拍照"、"续航"、"性价比"）
   - 未传入时使用默认维度：`["规格参数","价格","核心卖点","适用场景","优缺点"]`

3. **商品图片处理**：
   - 数据库中 `pictures` 字段可能是 JSON 数组格式的字符串
   - 需要解析 JSON 数组取第一张图片 URL；解析失败则直接使用原字符串

4. **AI 响应解析**：
   - AI 可能输出 Markdown 代码块包裹（以 ` ```json ` 开头或以 ` ``` ` 包裹），需要清理
   - 解析为 JSON 后提取 `products` 数组和 `summary`
   - 每个商品的 `dimensionValues` 顺序必须与 `dimensions` 顺序一致

5. **降级处理**：
   - AI 返回的 JSON 解析失败时，不抛异常，而是降低返回原始商品列表 + 降级提示"AI 对比服务暂时不可用，请稍后重试"
   - AI 调用超时或网络异常时，返回 500 + "AI 服务暂时不可用"

### 4.3 数据同步（`POST /ai/sync` 和 `POST /ai/sync/{spuId}`）

#### 处理规则

1. **全量同步（/sync）**：
   - 分页获取所有 SPU，每页 500 条
   - 每 20 条一批处理（BATCH_SIZE = 20）
   - 若某一批次 embedding API 或 ES 写入失败，记录日志并跳过该批次，不影响全域

2. **语义文本拼接格式**（必须是此格式，保证向量语义一致性）：
   ```
   商品名称：{name} | 标题：{title} | 描述：{description} | 品牌：{brandName} | 分类：{categoryName} | 标签：{tags}
   ```

3. **增量同步（/sync/{spuId}）**：
   - SPU 不存在时仅记录日志，不抛异常

---

## 5. 非功能性需求

### 5.1 性能要求

| 指标 | 要求 | 说明 |
|------|------|------|
| RAG 问答响应时间 | < 5s（不含 AI 生成） | 向量检索应在 200ms 内完成，主要耗时在 AI API 调用 |
| 商品同步 | — | 同步接口为同步阻塞，大数据量时几十秒~几分钟，未来可考虑异步 |
| AI 对比响应时间 | < 10s | 主要耗时在 AI API 调用 |
| 并发支持 | 满足日常运营 | 各服务无状态，可通过水平扩展 |

### 5.2 错误处理规范

| 异常场景 | HTTP 状态码 | code | 错误信息 |
|----------|------------|------|----------|
| 参数校验失败 | 400 | — | 具体校验错误（如"问题不能为空"） |
| 未登录/Token 过期 | 401 | — | 由 SSOFilter 统一处理 |
| 无权限 | 403 | — | 由 SSOFilter 统一处理 |
| SPU 不存在（对比） | 200 | 404 | "未找到对应的商品信息" |
| AI 服务不可用（对比） | 200 | 500 | "AI 服务暂时不可用，请稍后重试" |
| AI 服务不可用（问答） | 200 | 200 | "很抱歉，AI 服务暂时不可用，请稍后重试" + 相关商品列表 |
| ES 检索失败 | 200 | 200 | 回答提示未检索到商品，商品列表为空 |

**设计原则**：所有接口都返回 200 + `JsonResult` 结构。非 200 的 HTTP 状态码仅用于网关/SSO 层面的认证鉴权拒绝。

### 5.3 认证与鉴权

| 项目 | 说明 |
|------|------|
| 认证方式 | JWT Bearer Token（通过 Spring Security + SSOFilter 统一校验） |
| Token 来源 | SSO 登录后获取（从 Header 或 Cookie 中提取） |
| 鉴权策略 | `.anyRequest().authenticated()` — 所有 `/ai/**` 接口都需要登录 |
| 放行白名单 | `/doc.html`, `/v3/api-docs/**`, `/favicon.ico` 等（Knife4j 文档页面） |

### 5.4 配置项

| 配置键 | 默认值 | 说明 |
|--------|--------|------|
| `cooxiao.ai.api-key` | — | AI API 密钥（必填） |
| `cooxiao.ai.base-url` | `https://api.deepseek.com` | AI API 基础地址 |
| `cooxiao.ai.chat-model` | `deepseek-chat` | 对话模型名称（当前 `deepseek-v4-flash`） |
| `cooxiao.ai.embedding-enabled` | `false` | 向量语义检索开关 |
| `cooxiao.ai.embedding-model` | — | Embedding 模型名称 |
| `cooxiao.ai.embedding-dimensions` | `1024` | 向量维度（需与模型匹配） |
| `cooxiao.ai.temperature` | `0.7` | 生成温度 |
| `cooxiao.ai.max-tokens` | `2000` | 最大输出 token 数 |
| `cooxiao.ai.timeout` | `15000` | API 超时时间（ms） |
| `cooxiao.ai.daily-budget` | `10.0` | 每日 AI 调用预算上限（元） |
| `cooxiao.ai.chat-input-price-per-million` | `1.0` | Chat 输入价格（元/百万 tokens） |
| `cooxiao.ai.chat-output-price-per-million` | `2.0` | Chat 输出价格（元/百万 tokens） |
| `spring.elasticsearch.uris` | — | ES 服务地址 |

### 5.5 当前状态与规划

| 项目 | 状态 | 说明 |
|------|------|------|
| RAG 问答（全文检索） | ✅ 已完成 | ES IK 分词 + DeepSeek V4 Chat，含价格过滤 |
| 多轮对话导购 | ✅ 已完成 | Redis 存储会话，偏好自动提取，上下文记忆 |
| RAG 问答（向量检索） | 🔧 预留 | 代码已完整实现，待接入 embedding API |
| 商品对比 | ✅ 已完成 | DeepSeek V4 Chat 结构化输出 |
| 预算控制 | ✅ 已完成 | Redis 日计数器，默认 10 元/天 |
| ES 索引初始化 | ✅ 已完成 | 启动自动创建，含 dense_vector 预留 |
| Sync 接口异步化 | TODO | 当前同步接口为同步阻塞 |
| 语义搜索 | Phase 4 | 通过向量相似度直接搜索商品 |
