# CoolShark 项目待办事项

> 创建日期：2026-05-13
> 这些问题完成度会影响到前后端联调和生产部署，建议按优先级依次处理。

---

## 1. 【前端开发】添加商品图片/品牌Logo/分类图标的管理按钮

### 当前状态
- 数据库已有图片数据（SPU 组图、品牌 Logo、分类图标）
- 当前没有前端管理界面可以上传、修改或删除这些图片
- 如果存在前端管理后台，需要添加对应的操作入口

### 需要做的事情

| 功能 | 说明 | 涉及后端接口 |
|------|------|-------------|
| **SPU 图片管理** | 为每个 SPU 提供「新增图片」「删除图片」「设置封面」功能 | `POST /upload/picture/single` 上传单张图，返回 URL 后更新 SPU 的 `pictures` 字段 |
| **品牌 Logo 管理** | 在品牌编辑页添加 Logo 上传控件 | `POST /upload/brand-logo` 上传后更新 `pms_brand.logo_url` |
| **分类图标管理** | 在分类编辑页添加图标上传控件 | `POST /upload/category-icon` 上传后更新 `pms_category.icon` |
| **SPU 详情富文本** | 提供富文本编辑器，支持插入图片 | 使用 `pms_spu_detail.content` 字段存储 HTML |

### 后端已就绪的资源
| 上传接口 | 已实现 | 配置路径 |
|----------|--------|----------|
| `POST /upload/brand-logo` | ✅ | `mall-resource` → `FileUploadController` |
| `POST /upload/category-icon` | ✅ | 同上 |
| `POST /upload/picture/single` | ✅ | 同上 |

### 实现思路
- 前端管理后台在每个 SPU/品牌/分类的编辑页面添加图片上传组件
- 调用 `mall-resource` 的上传接口 → 返回图片 URL → 将 URL 提交到对应实体的更新接口
- 无需新增后端接口，上传和保存链路已完整

---

## 2. ✅【已完成】硬编码 URL 问题（方案 B：服务层拼接）

### 完成情况
- 数据库中已改为纯相对路径存储 ✅
- `ImageUrlPrefixHelper` 工具类已实现，覆盖 SPU组图/品牌Logo/分类图标 ✅
- 开发环境（`localhost:9060`）和生产环境（`http://8.156.85.160/`）已分别验证通过 ✅
- `pms_spu_detail.content` 富文本中的 `<img>` 相对路径**暂未处理**，属于已知遗留问题

### 问题描述
当前 `init-test-data.sql` 中的图片 URL 使用了占位符 `RESOURCE_HOST`：

```
JSON_ARRAY('RESOURCE_HOST/spu_1_1.jpg', ...)
```

执行 SQL 前需要手动全局替换 `RESOURCE_HOST` → `http://localhost:9060`（开发）或 `http://8.156.85.160`（生产），容易出错且无法自动切换。

### 推荐方案（方式 B）：数据库中存相对路径，服务层拼接前缀

#### 改动思路
1. **数据库中只存相对路径**（如 `spu_1_1.jpg`），不存完整 URL
2. **在 ForFrontSpuServiceImpl 等服务层代码中**，返回前自动拼接 `resource-host` 前缀
3. **开发环境和生产环境使用不同的配置文件**，`resource-host` 值不同

#### 具体改动点

**数据库层** — 数据改为只存文件名：

```sql
-- 改前
JSON_ARRAY('http://localhost:9060/spu_1_1.jpg')
-- 改后
JSON_ARRAY('spu_1_1.jpg')
```

**服务层** — 在返回 VO 前拼接前缀：

文件：`D:\java\csmall\mall-product\mall-product-webapi\src\main\java\com\cooxiao\mall\product\service\impl\ForFrontSpuServiceImpl.java`

在 `listSpuByCategoryId()` 和 `getSpuById()` 中，对 `pictures` 字段做处理：

```java
// 伪代码思路
@Value("${custom.file-upload.resource-host}")
private String resourceHost;

private String formatPictures(String pictures) {
    if (StringUtils.isBlank(pictures)) {
        return null;
    }
    // JSONArray 解析后，每个元素拼接 resourceHost + 相对路径
    JSONArray arr = JSONArray.parseArray(pictures);
    for (int i = 0; i < arr.size(); i++) {
        arr.set(i, resourceHost + arr.getString(i));
    }
    return arr.toJSONString();
}
```

#### 好处
- 开发、测试、生产用同一套 SQL，无需手动替换
- 切换环境只需改配置文件的 `resource-host` 值
- 未来迁移到 OSS 只需改这个拼接逻辑，不影响数据库

#### 注意事项
- 需要确认是否影响 `StringUtils` 和 `JSONArray` 的导入（项目中已有 FastJSON 依赖）
- 从 `pms_spu_detail.content` HTML 中提取图片路径并拼接比较复杂，难度较高，可以先跳过

---

## 3. 【构建部署】图片打包进 JAR 的问题

### 当前状态
- 图片已上传到服务器 `/data/csmall-upload/` 目录（供 Nginx 直接服务）
- 但 `mall-resource/src/main/resources/static/` 下的原始图片文件**仍保留在项目中**并被打包进 JAR

### 潜在影响

| 影响维度 | 说明 |
|----------|------|
| **JAR 体积** | 目前 ~50 张图片约 5-10MB，尚可接受。如果后续图片增加到几百张，JAR 体积会明显膨胀 |
| **更新便利性** | 图片一旦打包进 JAR，更换图片需要重新打包、上传、重启服务 |
| **存储浪费** | 每次部署都是完整的 JAR + 图片，而图片通常很少变动 |

### 建议方案

**短期**（当前阶段）：
- 图片已上传到服务器 `/data/csmall-upload/`，生产环境由 Nginx 直接服务，不依赖 JAR 内的图片
- 但 `static/` 目录仍保留在项目中随 JAR 打包，确保本地开发环境也能正常工作（`classpath:/static/`）
- 如果后续需要减少 JAR 体积，可从 `static/` 中移除图片文件

**长期**（如果项目持续发展）：
- 将图片从 `static/` 迁移到服务器磁盘 `/data/csmall-upload/` 目录
- 利用已有的 `spring.web.resources.static-locations: file:${custom.file-upload.server-local-base-path}` 配置，Spring Boot 会自动从该目录服务静态文件
- 后续新增的上传图片已经走的是这个路径，只需把初始化图片也复制过去即可
- 移除 `static/` 下的图片，减小 JAR 体积

### 迁移步骤（长期方案）
```bash
# 1. 将图片 scp 到服务器
scp -r mall-resource/src/main/resources/static/*.jpg ecs-user@8.156.85.160:/data/csmall-upload/
scp -r mall-resource/src/main/resources/static/*.png ecs-user@8.156.85.160:/data/csmall-upload/

# 2. 从项目中删除 static/ 下的图片文件（保留代码中的引用）
# 3. 确认 spring.web.resources.static-locations 配置包含 file:${custom.file-upload.server-local-base-path}
# 4. 重新打包部署，确认图片仍可访问
```

ai模块把这个 Key 添加到服务器上的 /data/jars/csmall.env 里。

---

## 5. 【AI 导购】Embedding 向量语义检索扩展点

### 背景
当前 RAG 问答（`POST /ai/ask`）使用 **ES IK 分词全文检索**（`multi_match`），不依赖 embedding API，功能完整可用。DeepSeek V4 当前不提供专用的 embedding API 端点（`/v1/embeddings` 返回 404），因此在代码中保留了向量语义检索的完整实现，通过配置开关控制。

### 扩展点清单

| 位置 | 说明 |
|------|------|
| `AiProperties.embeddingEnabled` | 检索模式开关：`false`=全文检索（当前），`true`=向量语义检索 |
| `application.yml` → `cooxiao.ai.embedding-enabled` | 运行时配置，改后重启即可切换 |
| `RagServiceImpl.vectorSearch()` | 向量检索方法已完整实现，开关翻为 `true` 后直接调用 |
| `VectorSyncServiceImpl.syncAll()/syncSpu()` | 商品数据同步到 ES，当前写入所有 text 字段但略过向量。接入 embedding 后需恢复向量生成逻辑 |

### 接入步骤（待完成）

1. **选择一个 embedding 供应商**（阿里通义千问、智谱 GLM、百度千帆等均有中文 embedding API）
2. **实现 embedding 客户端**：在 `client` 包下新建 `AliYunEmbeddingClient.java`（或其它供应商），实现 `AiClient` 接口的 `embed()` / `embedBatch()` 方法
3. **配置切换**：将 `application.yml` 中 `embedding-enabled` 改为 `true`，配置供应商的 `api-key`
4. **恢复向量同步**：`VectorSyncServiceImpl` 中取消 embedding 调用代码的注释，调用 `/ai/sync` 重新生成全量商品向量
5. **验证**：发起 RAG 问答请求，日志中出现「使用向量语义检索模式」即表示切换成功

### 注意事项
- 全量向量同步会产生少量 API 费用（商品数 × 百万 tokens 单价，约几元）
- ES 索引 `cool_shark_mall_ai` 中 `semanticVector` 字段已定义好（dense_vector, dims=512），同步后即可使用
- 切换后预算控制（`TokenBudgetService`）会自动计入 embedding API 的费用

---

## 6. 【AI 导购】商品变更自动同步（场景二）✅ 已实现

### 实现方式

mall-product 新增/修改商品后，通过 **Dubbo RPC** 自动触发 mall-ai 同步到 ES：

| 组件 | 文件 | 说明 |
|------|------|------|
| Dubbo 接口 | `mall-ai-service/.../ISpuSyncService.java` | `syncSpu(Long spuId)` |
| Dubbo 提供者 | `mall-ai-webapi/.../SpuSyncDubboServiceImpl.java` | `@DubboService` |
| Dubbo 消费者 | `mall-product-webapi/.../SpuServiceImpl.java` | `@DubboReference`，在 `addNew()` 和 `updateById()` 末尾调用 |

### 注意事项
- `@DubboReference(check = false)`：mall-ai 不可用时不影响商品增删改主流程
- 同步失败仅记录 warn 日志，不抛异常

---

## 7. 【AI 导购】第四阶段：智能搜索增强（TODO）

> 原计划 Phase 4 为向量语义搜索，因 DeepSeek V4 不提供 embedding API，改用以下三个子功能替代。

### 7.1 AI 语义重排序 `POST /ai/search`

**功能**：用户搜索关键词 → ES 全文检索 Top-15 → AI 按用户意图重排序 → 返回 Top-5

**原理**：
```
"学生党高性价比手机"
  → ES multi_match Top-15（IK 分词，快但不理解"学生"语义）
  → AI: "你是导购，按学生预算有限且性能够用重新排序以下 15 个商品"
  → 返回 AI 重排后的 Top-5
```

**不依赖 embedding**，纯靠 AI Chat 做语义理解。

**新增文件**：
- `mall-ai-webapi/.../service/impl/SearchServiceImpl.java` — 核心逻辑
- `AiController.java` — 新增 `POST /ai/search`

### 7.2 搜索自动补全 `GET /ai/search/suggest`

**功能**：用户输入部分文字 → ES Completion Suggester + IK → 返回补全候选

**原理**：ES `completion` suggester 字段，IK 分词实时匹配

**新增文件**：
- EsIndexInitializer 中添加 completion 字段 mapping
- SearchServiceImpl 中 suggest 方法

### 7.3 相关商品推荐 `GET /ai/product/{id}/related`

**功能**：商品详情页 → 基于当前商品推荐相似商品

**原理**：ES `more_like_this` 查询，按名称+标题+描述+标签相似度排序

### 预估工作量

| 子功能 | 难度 | 耗时 |
|--------|------|------|
| AI 语义重排序 | ⭐⭐ | 1.5h |
| 搜索自动补全 | ⭐ | 0.5h |
| 相关商品推荐 | ⭐ | 0.5h |
| **合计** | — | **2.5h** |

### 风险

| 风险 | 应对 |
|------|------|
| AI 重排 token 消耗 | 每次约 500 tokens ≈ 0.001 元，TokenBudgetService 已覆盖 |
| 重排延迟 | ES 200ms + AI ~2s，总计 <3s，可接受 |
| AI 排序不稳定 | temperature=0，固定 prompt |