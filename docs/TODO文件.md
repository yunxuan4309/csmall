# CoolShark 项目待办事项

> **创建日期**: 2026-05-13
> **最后更新**: 2026-08-17（新增 Redis 主从+哨兵学习计划）
> **关联文档**: [[服务器巡检与待修复问题清单-2026-08-04]]、[[JVM调优方案]]

---

## 🟡 中优先级

### 1. 【前端开发】商品图片/品牌Logo/分类图标管理界面

当前后台管理缺少：
- ❌ 相册管理页面（`pms_album` 表已有，后端 Controller 已就绪）
- ❌ 图片管理页面（`pms_picture` 表已有，后端 Controller 已就绪）
- ❌ SPU 编辑时从相册选图

| 功能 | 涉及文件 |
|------|---------|
| 相册 CRUD | 新增 `AdminAlbumList.vue` + `album.js` API |
| 图片列表/上传/删除 | 新增 `AdminPictureList.vue` + `picture.js` API |
| SPU 关联相册 | 修改 `SpuList.vue` |

---

### 2. 【AI 安全】`/ai/**` 接口接入 Sentinel 限流

> **2026-08-14 新增**：AI 接口审计发现所有 `/ai/**` 接口均无限流规则（无 `@SentinelResource`、`sentinel-rules.json` 无 ai 规则），单个用户/单 IP 可无限并发调用，即使有 2 元/日预算也存在并发冲超风险。

**目标**：给 AI 调用路径（重点：`/ai/chat/stream`、`/ai/chat/send`、`/ai/ask`、`/ai/compare`、`/ai/search`）加 Sentinel 限流，例如：
- 按用户或按 IP：每 60 秒最多 N 次（N 建议 10~30，需实测）
- 流式接口额外限制并发连接数（防 SSE 长连接占满线程池）

**参考实现**：
- 现有先例：`mall-sso` 的 `AdminSSOController.doLogin` 已用 `@SentinelResource(value = "adminLogin", blockHandler = "loginBlock")` + 自定义 `BlockException` 统一返回 429 JSON（见 `mall-common` 全局异常处理）
- 流控规则可通过 Sentinel 控制台（8090 端口）动态下发，或写入 `deploy/docker/sentinel-rules.json` 持久化

**涉及文件**：`AiController.java`（各端点加注解）、`deploy/docker/sentinel-rules.json`（规则）、`mall-common`（429 响应处理已有）

---

## 🟢 低优先级

### 2. 图片打包进 JAR 的问题

`mall-resource/src/main/resources/static/` 下的图片随 JAR 打包，浪费空间。生产环境已走 `/data/csmall-upload/`，可考虑移除 static 下的重复图片。

### 3. RedisBloom 模块安装

当前 Redis 无 RedisBloom 模块，秒杀用 Redis Set 替代布隆过滤器。当前数据量（6场秒杀、12SKU）影响可忽略。未来数据量大时安装。

> **2026-08-09 服务器核实**：生产 Redis 7.4.10（standalone），`module list` 为空、`BF.EXISTS` 报 unknown command → **确认未装 RedisBloom**。
> 安装方案：改用 RedisStack 镜像，或在 docker-compose 的 Redis 启动命令加 `--loadmodule ./redisbloom.so`（挂载模块文件）；装好后把 `SeckillBloomInitialJob` 的 Set 实现替换为 `bf.madd` / `bf.exists`（代码里已留 TODO）。

### 4. 【支付】微信支付模拟策略

`WechatPaySimulationStrategy implements PaymentStrategy`，遵循微信 API 契约。

### 5. 【支付】真实支付宝支付

当前 `simulated: true`（模拟支付）。待支付宝沙箱修复或商户签约后改为 `false`。

### 6. 【演示】JMeter + Sentinel + SkyWalking 联合压测

面试演示用，文档已写好（`秒杀压测演示指南.md`），待录制。

### 7. HTTPS/SSL 配置 ⏸️

```
阻塞链: HTTPS → SSL证书 → 域名 → ICP备案 → 服务器续费≥3个月
                                                    ↑
                                            卡在预算（8月29日到期）
```

### 8. 【AI 预算】每日预算按北京时间结算

> **2026-08-15 新增**：`TokenBudgetService` 用 JVM 默认时区（服务器容器为 UTC）计算 `ai:daily_cost:<日期>` key 和 TTL，导致**每日预算在北京时间早上 8:00 重置**，而非零点。

**修复方案**：`TokenBudgetService` 中改用 `ZoneId.of("Asia/Shanghai")` 计算日期（`buildKey`）和次日零点 TTL（`getSecondsUntilMidnight`），例如：

```java
private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
// buildKey: LocalDate.now(ZONE)
// getSecondsUntilMidnight: ZonedDateTime.now(ZONE) → 次日 atStartOfDay(ZONE)
```

**涉及文件**：`mall-ai/mall-ai-webapi/src/main/java/com/cooxiao/mall/ai/service/TokenBudgetService.java`

### 9. 【学习】Redis 主从 + 哨兵高可用实验

> **2026-08-17 新增**：源自面试准备 `02-秒杀高并发.md` Q10（Redis 挂了怎么办）。当前生产为单机 Redis（standalone），秒杀链路强依赖 Redis（随机码/下单锁/库存 DECR），挂了功能直接停摆；`seckill_message_retry` 重试表只兜 MQ 可靠性。计划深入学习"主从复制 + 哨兵"高可用机制并实操实验。

**学习目标**：
- 主从复制：主节点写、从节点异步同步数据（数据冗余）
- 哨兵：监视主节点 → 挂了自动把从提升为主 → 客户端自动重连（故障转移）
- 对照同一思想：Nacos Raft 选主、ES 主分片提升、Redis 哨兵切换

**实验方案**（本地 Docker 或服务器，学习用最小拓扑即可）：
- 最小拓扑：1 主 + 1 从 + 1 哨兵；标准拓扑：1 主 + 2 从 + 3 哨兵（防脑裂需多数派）
- 端口规划：6379（主）/ 6380（从）/ 26379（哨兵），全部仅内网，不暴露公网
- Spring Boot 改造：`spring.redis.sentinel.master/nodes` 指向哨兵；sso/order/seckill 等连 Redis 的服务需全量回归

**边界认知（重要）**：
- 单机搭建 = 演示级 HA：只防 Redis 进程崩溃（如被 OOM 杀），**不防整机宕机**；真实 HA 需 ≥2 台机器，或直接买阿里云托管 Redis（自带主从+哨兵）
- 主从异步复制：主挂瞬间最后几笔 DECR 可能未同步到从，靠数据库 `seckill_stock >= quantity` 条件扣减兜底，不会真超卖
- 当前服务器可用内存约 2.2G，加副本前先评估；服务器 8-29 到期，可结合续费/升级一起规划

**涉及文件**：`/data/csmall/docker-compose.yml`（新增 redis 副本/哨兵服务）、各服务 `application-prod.yml`（sentinel 连接配置）

---

## 已完成（2026-08-04 归档）

| # | 事项 | 完成日期 | 备注 |
|----|------|---------|------|
| 1 | **ES 集群 Red 修复** | 2026-08-04 | 根因: IK 分词器丢失。安装 IK → 重试分片 → 副本清零 → cluster green |
| 2 | **JVM 内存调优** | 2026-08-04 | Seata 2G→512M, OAP 1G→512M, 11微服务加 DirectMemory/CodeCache 限制。系统内存 93%→71% |
| 3 | **硬编码 URL → 服务层拼接** | 2026-05 | `ImageUrlPrefixHelper` 实现，DB 存相对路径 |
| 4 | **AI 导购商品变更自动同步** | 2026-07 | Dubbo `ISpuSyncService` → mall-product 增改商品自动同步 ES |
| 5 | **AI 导购第四阶段：智能搜索增强** | 2026-07 | `/ai/search`(AI重排序) + `/ai/search/suggest`(自动补全) + `/ai/product/{id}/related`(相关推荐) 三个接口全部实现 |
| 6 | **SkyWalking 全链路追踪** | 2026-07-31 | Docker 部署: 所有 11 个微服务 + OAP + UI 全部接入 |
| 7 | **支付模块：支付宝沙箱 + 模拟支付** | 2026-07 | 策略模式 + 签名验签 + 流水记录 + 模拟开关 |
| 8 | **调试接口清理** | — | `/admin/sso/debug` 和 `/admin/sso/hash` 已在代码中移除 |
| 9 | **企业级升级 P0-P2** | 2026-07 | 代码规范 + 幂等 + Docker + Sentinel + MQ + 日志 + SkyWalking + CI/CD |
| 10 | **企业级商品管理基础 CRUD** | 2026-08-04 | 属性模板 + SKU管理(含图片) + 换模板保护 + 多mapper修复 |
| 11 | **docs/ 文档整理** | 2026-08-04 | 25个"问题解决--"移入子文件夹; 3份覆盖文档删除; init-database.bat移到根目录 |
| 12 | **服务器权限收紧** | 2026-08-03 | ai-claude 独立账号 + sudoers 白名单 |
| 13 | **IK 分词器持久化** | 2026-08-05 | es-plugins 目录挂载到容器, config 字典已持久化 |
| 14 | **RabbitMQ 健康检查超时** | 2026-08-05 | timeout 5s→10s, 当前 healthy |
| 15 | **ES 搜索数据同步** | 2026-08-05 | 新增 /search/sync 端点, 18 条商品入库 |
| 16 | **AI 导购 Embedding 向量检索** | 2026-07 | `SiliconFlowEmbeddingClient` + `vectorSearch()` cosineSimilarity 完整实现, test 环境已启用 |
| 17 | **秒杀 RabbitMQ 降级方案** | 2026-08 | `seckill_message_retry` 表 + `MessageRetryTask` 每5秒重试 + SeckillServiceImpl 写入, Flyway V5 |

---

**维护提示**: 新增待办时按优先级放入对应区块（🔴高/🟡中/🟢绿），完成后移入本表末尾。
