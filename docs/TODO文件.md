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

---

## 8. 【秒杀】RabbitMQ 不可用时的降级方案

### 背景

当前秒杀下单流程：Redis 扣库存成功 → 发 RabbitMQ 消息 → 消费者异步落库。如果 RabbitMQ 挂掉，`rabbitTemplate.convertAndSend()` 会抛异常，导致 `@GlobalTransactional` 回滚。但 Redis 库存扣减不在 Seata 事务管理范围内，**无法自动回滚**，导致库存"蒸发"。

### 方案对比

| 方案 | 复杂度 | 优点 | 缺点 |
|------|--------|------|------|
| **方案一 ★（已选中）** 本地表缓冲 | ⭐⭐ | 不依赖额外中间件，MQ 恢复后自动补发 | 引入额外表和定时任务 |
| 方案二：消息确认 + 重试 | ⭐ | 配置简单，Spring Boot 原生支持 | MQ 本身挂了时无法解决 |
| 方案三：RabbitMQ 集群 | ⭐⭐⭐⭐⭐ | 高可用，单节点挂了不影响 | 需要额外服务器资源，当前单机条件不支持 |
| 方案四：Redis 库存回滚 | ⭐ | 实现简单 | 并发下计数不准确 |

### 方案一详细设计（待实现）

**在 `mall-seckill` 中新增 `seckill_message_retry` 表：**

```sql
CREATE TABLE seckill_message_retry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    order_sn VARCHAR(64),
    message_body TEXT COMMENT '序列化的 Success 对象',
    status TINYINT DEFAULT 0 COMMENT '0-待发送 1-发送成功 2-发送失败(已达重试上限)',
    retry_count INT DEFAULT 0,
    create_time DATETIME,
    update_time DATETIME
);
```

**改动点：**

| 位置 | 改动 |
|------|------|
| `SeckillServiceImpl.commitSeckill()` | Redis 扣库存成功后，先 `INSERT` 一条消息记录到 `seckill_message_retry`（status=0），再发 MQ。发送成功后将 status 更新为 1 |
| 新增 `MessageRetryTask` | 定时任务（每 5 秒）扫描 `seckill_message_retry` 中 status=0 且 retry_count < 3 的记录，重新发送 MQ 消息 |
| `SeckillQueueConsumer.process()` | 消费者处理成功后，回调查 `seckill_message_retry` 将 status 更新为 1 |
| 新增 `MessageRetryMapper` | MyBatis Plus 操作 `seckill_message_retry` 表 |

### 执行状态

- [ ] 方案一已选中，面试结束后实施
- [ ] 面试前不做任何代码修改，避免引入不稳定因素

---

## 9. 【支付模块】支付宝沙箱集成 + 模拟支付

> 创建日期：2026-07-18 | 最后更新：2026-07-19
> 已实现：支付策略模式 + 支付宝沙箱集成 + 模拟支付开关 + 支付流水记录
> 沙箱 APPID: 9021000165675647
> 当前状态：**模拟支付模式已跑通**，真实沙箱支付页面有服务端 bug 无法走完

### 9.1 已实现功能

| 功能 | 状态 | 说明 |
|------|------|------|
| 支付策略模式 | ✅ | `PaymentStrategy` 接口 + `PaymentStrategyFactory` 自动路由 |
| 支付宝沙箱策略 | ✅ | `AlipaySandboxStrategy`：签名、验签、查单全链路已实现 |
| 模拟支付开关 | ✅ | `alipay.simulated=true` → 跳过支付宝 API，直接支付成功 |
| 支付流水记录 | ✅ | `oms_payment_record` 表 + MyBatis Mapper |
| 前端查单接口 | ✅ | `POST /oms/order/pay/query` |
| 异步回调接口 | ✅ | `POST /payment/callback/alipay/notify`（需公网可达） |
| PEM 密钥加载 | ✅ | classpath 文件加载，自动剥离 PEM 头尾 |
| 支付流水表 | ✅ | `database/cs_mall_oms/oms_payment_record.sql` 已执行 |

### 9.2 新增/修改文件清单

**新增（12 个）：**
| 文件 | 说明 |
|------|------|
| `mall-order-webapi/.../payment/PaymentStrategy.java` | 支付策略接口 |
| `mall-order-webapi/.../payment/PaymentResult.java` | 发起支付返回值 |
| `mall-order-webapi/.../payment/PaymentCallbackResult.java` | 回调标准化结果 |
| `mall-order-webapi/.../payment/PaymentStrategyFactory.java` | 策略工厂（自动注册） |
| `mall-order-webapi/.../payment/AlipayConfig.java` | 支付宝配置（@ConfigurationProperties） |
| `mall-order-webapi/.../payment/AlipaySandboxStrategy.java` | 支付宝沙箱实现 |
| `mall-order-webapi/.../mapper/OmsPaymentRecordMapper.java` | 支付流水 Mapper |
| `mall-order-webapi/.../controller/PaymentCallbackController.java` | 回调接收 | 
| `mall-pojo/.../model/PaymentRecord.java` | 流水实体 |
| `mall-pojo/.../enums/PaymentTypeEnum.java` | 支付方式枚举 |
| `mall-pojo/.../enums/PaymentStatusEnum.java` | 支付状态枚举 |
| `mall-order-webapi/src/main/resources/alipay_private_key.pem` | 私钥文件 |

**修改（7 个）：**
| 文件 | 变更 |
|------|------|
| `PayOrderVO.java` | 新增 `paymentForm`、`paymentUrl` 字段 |
| `OmsOrderServiceImpl.java` | 接入策略模式 + 流水记录 + 模拟模式直接完成支付 |
| `OmsOrderMapper.java/xml` | 新增 `selectOrderBySn()` |
| `ResourceWebSecurityConfiguration.java` | `/payment/callback/**` 加入白名单 |
| 根 `pom.xml` | 新增 `alipay-sdk-java` v4.38.200.ALL |
| `mall-order-webapi/pom.xml` | 引入支付宝 SDK |
| `application-test.yml` | 支付宝沙箱 + 模拟模式配置 |

**前端修改：**
| 文件 | 变更 |
|------|------|
| `src/api/order.js` | 新增 `queryPayment()` |
| `src/views/front/order/OrderPay.vue` | 处理 `paymentForm` / `paymentUrl` / 查单按钮 |
| `src/views/front/order/PayResult.vue` | 新建，支付结果落地页 |
| `src/router/index.js` | 新增 `/pay-result` 路由 |

### 9.3 支付宝新沙箱踩坑记录（重要！）

**沙箱环境**：`openapi-sandbox.dl.alipaydev.com`（新沙箱，与旧 `openapi.alipaydev.com` 不同）

**后端 API 层面**：调用全部成功，签名验签正确。遇到的坑：

| 问题 | 原因 | 解决 |
|------|------|------|
| YAML 多行字符串 PEM 密钥解码失败 | Java `InvalidKeyException: Unable to decode key` — YAML `\|` 可能引入不可见字符 | 私钥改为 classpath 文件加载 (`alipay_private_key.pem`)，代码剥离 PEM 头后传裸 base64 给 SDK |
| 支付宝公钥验签失败 | 同上 — SDK 的 `getPublicKeyFromX509` 不处理 PEM 头 | `cleanPemKey()` 统一剥离公钥/私钥 PEM 头尾 |
| 旧沙箱网关 SSL 证书过期 | `openapi.alipaydev.com` 被弃用 | 只能使用新沙箱 |
| `precreate`（当面付扫码）504 | 沙箱不支持当面付产品 | 放弃，用 `page.pay` |
| `wap.pay`（手机网站）500 | 沙箱支付页面挂了 | 放弃，用 `page.pay` |

**支付宝沙箱支付页面前端 bug**（支付宝服务端问题，非我们代码问题）：

| 问题 | 具体表现 | 影响 |
|------|---------|------|
| `document.domain` 不匹配 | `Failed to set the 'domain' property: 'alipay.com' is not a suffix of 'cashier-sandbox.dl.alipaydev.com'` | 支付页面 JS 逻辑断裂，无法正常交互 |
| 内网域名泄露 | 页面引用 `stable.alipay.net`、`seccliprod.stable.alipay.net`、`umidprod.stable.alipay.net` 等内网资源 | 公网 DNS 不解析 → 风控/安全校验 JS 加载失败 → 支付被拦截 |
| 混合内容 | HTTPS 页面加载 HTTP 脚本/图片 | 被 Chrome/Edge 拦截 → 关键 JS 缺失 |
| 支付提交 502 | `POST expressFastPayFrom.json` 返回 502 | 支付核心服务不可用 |

**结论**：API 集成正确（5 次不同方式调用，后端均返回成功），问题出在支付宝沙箱**前端支付页面**有 JS 兼容性 bug + 内网资源泄露。这不是我们能修复的。

### 9.4 妥协方案：模拟支付模式

在 `application-test.yml` 中配置：

```yaml
alipay:
  simulated: true   # true=模拟支付（开发测试），false=真实支付宝（生产）
```

**模拟模式行为**：
- `AlipaySandboxStrategy.initiatePayment()` → 跳过 API，返回模拟交易号
- `OmsOrderServiceImpl.payOrder()` → 检测无 `paymentForm`/`paymentUrl` → 直接更新订单状态为"已支付"
- 支付流水记录中 `paymentStatus=SUCCESS`、`tradeNo=SIMxxxxxx`

**当前配置**：`simulated: true`（test profile）

### 9.5 开启真实支付的计划

当以下任一条件满足时，将 `simulated` 改为 `false` 即可启用真实支付宝：

**方案 A：支付宝修复沙箱（无需改代码）**
1. 等待支付宝修复新沙箱支付页面的 JS/网络问题
2. `simulated: false` + 重启 → 真实支付即可工作

**方案 B：切换到生产环境（需 APPID + 商户签约）**
1. 申请正式支付宝商户账号，完成签约
2. 获取正式 APPID 和密钥
3. 修改 `application-prod.yml` 中的 `alipay.*` 配置
4. `gateway` 改为 `https://openapi.alipay.com/gateway.do`
5. `simulated: false`
6. 验证生产支付 → 上线

**方案 C：使用旧沙箱（如果支付宝重新启用）**
1. 如果旧沙箱恢复：`gateway` 改回 `https://openapi.alipaydev.com/gateway.do`
2. `simulated: false` + 重启

### 9.6 待办

**高优先级：**
- [ ] 微信支付模拟策略（`WechatPaySimulationStrategy implements PaymentStrategy`，遵循微信 API 契约）
- [ ] 当沙箱修复/商户签约后，`simulated: false` 开启真实支付

**中优先级：**
- [ ] 前端：支付结果页面完善（扫码支付后的查单体验优化）
- [ ] 接入 ngrok 回调，实现实时异步通知
- [ ] 支付流水表加 `refund_*` 退款支持

**低优先级：**
- [ ] 将支付宝密钥移到 `csmall.env` 环境变量（生产部署时）
- [x] ~~执行建表 SQL~~（已完成 2026-07-19）
- [x] ~~PEM 密钥加载问题~~（已解决：classpath 文件 + cleanPemKey）
- [x] ~~SDK 签名验签问题~~（已解决：PEM 头剥离）

---

## 10. 【可观测性】SkyWalking 链路追踪接入记录

> 接入日期：2026-07-20
> 当前已接入：mall-gateway、mall-order、mall-product

### 10.1 已接入模块

| 模块 | Agent 名称 | 状态 |
|------|-----------|------|
| mall-gateway-server | mall-gateway | ✅ 已接入 |
| mall-order | mall-order | ✅ 已接入 |
| mall-product | mall-product | ✅ 已接入 |

### 10.2 其余模块接入方法

每个模块只需要在 IDEA Run Configuration 的 VM options 中加一行：

```
-javaagent:D:/java/csmall/deploy/skywalking/skywalking-agent/skywalking-agent.jar
-DSW_AGENT_NAME=模块名
-DSW_AGENT_COLLECTOR_BACKEND_SERVICES=localhost:11800
```

**其余模块接入清单**：

| 模块 | SW_AGENT_NAME | 优先级 | 建议 |
|------|--------------|--------|------|
| mall-front | mall-front | 中 | 前台展示，加不加都行 |
| mall-search | mall-search | 中 | ES 搜索，加不加都行 |
| mall-seckill | mall-seckill | 高 | 秒杀核心链路，建议加 |
| mall-ai | mall-ai | 低 | AI 对话，链比较简单 |
| mall-ums | mall-ums | 低 | 用户管理，无外部调用 |
| mall-ams | mall-ams | 低 | 后台管理，无外部调用 |
| mall-sso | mall-sso | 中 | 登录入口，可加 |
| mall-resource | mall-resource | 低 | 文件服务，链简单 |

**操作**：打开 IDEA → Run → Edit Configurations → 选对应模块 → Modify options → Add VM options → 粘贴上面三行（改 SW_AGENT_NAME 为对应名称）→ 重启。

### 10.3 启动顺序（重要！）

必须先启动 SkyWalking OAP + UI，再启动微服务。否则 Agent 连接 OAP 超时会拖慢启动。

```
1. start-skywalking.bat（或 IDEA Shell Script 启动）
2. 等待 OAP（11800）和 UI（8088）端口就绪
3. 启动各微服务
```

### 10.4 Docker 部署方案

见 `部署注意文档.md` 第三章，已包含 SkyWalking OAP + UI 的 Docker compose 配置和微服务 Agent 挂载方式。

---

## 11. 【演示压测】JMeter + Sentinel + SkyWalking 联合演示

> 创建日期：2026-07-20
> 状态：待实施

### 目的

面试演示场景：用 JMeter 压秒杀接口 → Sentinel 限流触发 → SkyWalking 实时展示流量尖峰和调用链。

### 步骤

1. **安装 JMeter**
   - 下载: https://jmeter.apache.org/download_jmeter.cgi (~50MB)
   - 解压到本地任意目录

2. **编写压测脚本**
   - 创建 Thread Group（1000 线程，循环 10 次）
   - HTTP Request 指向 `POST /seckill/commit/{randCode}`
   - 携带 JWT Token 和 SeckillOrderAddDTO 参数

3. **启动 Sentinel Dashboard** → 观察限流规则触发

4. **启动 SkyWalking** → 观察秒杀链路和 QPS 变化

5. **录制演示视频**（30 秒）
   - 启动 JMeter → Sentinel Dashboard 显示限流 → SkyWalking 显示流量分析

### 面试话术

> "这是用 JMeter 压秒杀接口的演示。1000 并发进来，Sentinel 限流只放 500 QPS，超出的返回'系统繁忙'。SkyWalking 实时看到 mall-seckill 的 QPS 从 0 飙升到 500，响应时间从 50ms 降到 30ms——因为限流保护了后端，反而更快了。"