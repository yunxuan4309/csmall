# mall-ai 智能导购模块 — 问题与解决方案总结

> 开发周期：2026-05-14 ~ 2026-05-16  
> 涉及模块：mall-ai（新增）、mall-pojo（扩展）、mall-product-webapi（修改）、mall-gateway-server（修改）

---

## 一、Phase 1 — AI 商品对比

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| `JsonResult.failed(String)` 编译报错 | 没有 `failed(String)` 重载 | 用 `JsonResult.failed(ResponseCode, String)` |
| `JsonResult<Void>` 无法转为 `JsonResult<CompareResultVO>` | Java 泛型不可变 | 新增 `failedResult()` 泛型 helper 方法 |
| Swagger 页面弹出登录框 | `spring-boot-starter-security` 自动配置 | 创建完整的 SSOFilter + SecurityFilterChain |
| AI 模型选型 | 需要支持 JSON 结构化输出的对话模型 | 选择 DeepSeek V4 Flash（chat-completion），预留 DeepSeek V4 Pro |

---

## 二、Phase 2 — RAG 智能问答

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| ES Embedding API 返回 404 | DeepSeek V4 不支持 `/v1/embeddings` 端点 | 改用 ES IK 分词全文检索（`multi_match`），向量检索作为预留扩展 |
| `Script.Builder.source()` 编译报错 | ES Java Client 7.x API：`source()` 需在 `inline()` 内调用 | 改为 `.inline(inline -> inline.source(...))` |
| `JwtTokenUtils` Bean 找不到 | 启动类默认扫描只覆盖 `com.cooxiao.mall.ai` | 添加 `@Import(MallCommonConfiguration.class)` + `@EnableDubbo` |
| Dubbo 调用 Seata 过滤器报 `NoClassDefFoundError` | mall-ai 缺少 Seata 依赖 | 添加 `seata-spring-boot-starter` + `seata-serializer-kryo` |
| Seata 启动报 `service.vgroupMapping` 找不到 | Seata 自动配置尝试连接 Seata Server | 添加 `seata.enabled: false` |
| Swagger 页面 CSS/JS 加载失败 | `/webjars/**` 路径不在白名单 | 白名单添加 `/webjars/**` |
| ES `multi_match.fields()` 报 `number_format_exception` | 整个字符串被当成一个字段名 | 改为 `List.of("name^5", "title^4", ...)` 每个字段独立传入 |
| 商品图片不显示 | ES 存相对路径，返回时没拼 host 前缀 | 添加 `custom.file-upload.resource-host` 配置 + `extractFirstPicture()` 拼前缀 |
| 没有价格过滤 | 纯文字搜索不支持价格范围 | 从用户问题中正则提取价格约束，ES `bool` 添加 `range` filter |

---

## 三、Phase 2.5 — Token 预算控制

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 需要限制 API 调用费用 | 无限制调用可能超出预算 | 新增 `TokenBudgetService`，Redis 日计数器，默认 10 元/天 |
| 定价不匹配 | 默认价格是 OpenAI 的 | 按 DeepSeek V4 Flash 定价配置：输入 1元/百万tokens，输出 2元/百万tokens |

---

## 四、Product Compare 维度重复问题

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| AI 生成的对比维度值全重复 | Flash 模型处理 5 维度结构化 JSON 能力不足 | 不改模型，改为后端从商品字段直接填充维度值，AI 只生成总结 |
| 关键词/标签为"暂无" | 数据库 `keywords`/`tags` 字段为空 | `fallback()` 方法用 `title`/`description` 兜底 |

---

## 五、Phase 3 — 多轮对话

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| AI 说"没有6000以上的"但存在 7000+ 商品 | 旧偏好（budget:3000）在 prompt 中覆盖了检索结果 | 请求时从当前消息正则提取价格，立即更新会话偏好 |
| AI 幻觉（明明有商品却说没有） | System prompt 中"用户偏好"与"检索结果"冲突 | 标注检索结果为"权威数据源"，偏好为"仅供参考" |
| 价格过滤无结果时 AI 无上下文 | ES `range` filter 过滤掉所有商品 | 结果为空时自动降级为无价格过滤的纯文字搜索 |

---

## 六、部署与生产环境

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| Gateway 返回 500 | Nacos 里 mall-ai 注册了两个实例（Dubbo 20882 + HTTP 10010），`lb://` 可能路由到 Dubbo 端口 | 改为直连 `http://127.0.0.1:10010` |
| 缺少 systemd 服务文件 | mall-ai 是新增模块 | 创建 `deploy/systemd/mall-ai.service` |
| 缺少环境变量 | `AI_API_KEY` 未配置 | 在 `csmall.env` 中添加 |
| 生产 Gateway 缺少路由 | 只加了开发环境路由 | 在 `application-prod.yml` 添加 mall-ai 路由 |
| 第一次部署需要同步数据到 ES | ES 索引为空，RAG 无数据 | 添加 `sync-auto-on-startup` 启动自动同步 |

---

## 七、关键技术决策

| 决策 | 说明 |
|------|------|
| ES 全文检索替代向量检索 | DeepSeek V4 无 embedding API，使用 IK 分词 `multi_match` + 价格 `range` filter |
| 向量检索作为预留扩展 | 代码完整保留，`embedding-enabled: false` 控制，待接入其他 embedding 供应商 |
| 商品变更自动同步 | mall-product → Dubbo `ISpuSyncService` → mall-ai → ES upsert |
| 多轮对话预算即时更新 | 正则提取当前消息中的价格，立即更新会话偏好，避免旧偏好误导 AI |
| AI 对比改用数据填充 | 维度值从商品字段直接取（keywords → tags → title/description 降级），AI 只写总结 |
| Gateway 直连避免路由错误 | 生产环境使用 `http://127.0.0.1:10010` 替代 `lb://mall-ai` |

---

## 八、当前模块接口清单（8 个端点）

| 端点 | 阶段 | 说明 |
|------|------|------|
| `POST /ai/compare` | Phase 1 | 商品对比 |
| `POST /ai/ask` | Phase 2 | RAG 问答 |
| `POST /ai/chat/session` | Phase 3 | 创建对话 |
| `POST /ai/chat/send` | Phase 3 | 发送消息（多轮记忆） |
| `GET /ai/chat/history` | Phase 3 | 对话历史 |
| `POST /ai/sync` | 基础设施 | 全量同步 ES |
| `POST /ai/sync/{spuId}` | 基础设施 | 增量同步 ES |
