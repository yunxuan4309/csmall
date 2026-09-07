# CoolShark 已完成任务归档

> **创建日期**: 2026-09-07
> **来源**: 从 [[TODO文件]] 拆分迁移——原 TODO 文件只保留未完成/部分完成/暂缓项，本文件归档**全部已完成**任务。
> **维护**: 新完成任务从 [[TODO文件]] 移入本文件末尾；若本文件过长，按批次切分为 `TODO已完成-2` 等。

---

## 一、第一批：安全 + 资源止血（2026-09-07 已全部完成）

| 顺序 | 编号 | 事项 | 完成情况 |
|------|------|------|---------|
| 1 | **#25** | JWT_SECRET 生产随机化 | ✅ 完成（.env 换 64 字符随机密钥，11 容器 env 一致，认证链路回归通过）|
| 2 | **R7** | 内存优化（mem_limit+Nacos降堆+Swap） | ✅ 完成（mem_limit 全 21 容器 + Nacos 512m + Sentinel 限堆 + Swap 2G；available 2.0G→3.9G）|
| 3 | **#24** | MySQL 强密码 + 端口 127.0.0.1 | ✅ 完成（43 位强密码 ALTER USER 双 host，3306 收窄）|
| 4 | **R1+R2+R3+R4** | Redis 加固（密码/AOF/内存上限/conf） | ✅ 完成（requirepass + AOF + maxmemory 256mb volatile-lru，6379 收窄）|
| 5 | **#38** | Dockerfile 双份清理 | ✅ 完成（11 份残留 Dockerfile 已 git rm）|

> 📌 明细与实战经验（Redis RDB→AOF 迁移丢数据、gateway 启动竞态、conf 属主权限）见 [[TODO第一批实现与原理]] §九；轮换操作见 [[运维手册--密钥密码轮换]]。

**第二批已完成项**（从路线图迁移，#8 / #36 已完成）：

| 编号 | 事项 | 完成情况 |
|------|------|---------|
| **#8** | AI 预算按北京时间结算 | ✅ 已完成（2026-09-07，TokenBudgetService 时区） |
| **#36** | DLX 死信 + OrderQueueConsumer requeue 修复 | ✅ 已完成（x-death 限次重试 + 订单队列 DLX + OrderDlxConsumer）|

---

## 二、正文已完成条目（从 [[TODO文件]] 正文迁移）

### R1. 【Redis】无密码认证 + 密码链路三处脱节 ✅ 已完成（2026-09-07）

> ✅ **2026-09-07 已修复**：requirepass 开启 + 11 微服务注入 `SPRING_DATA_REDIS_PASSWORD`，端口收窄 127.0.0.1。执行明细见 [[TODO第一批实现与原理]]。

**现状（已核实）**：
- Redis 服务端 `requirepass` 为空 —— 容器以裸 `redis-server` 启动，无任何参数、无配置文件
- `docker-compose.yml` 中 redis 服务**无 environment 段**，`.env` 的 `REDIS_PASSWORD`（实测值为空）从未传给 Redis 容器
- 11 个微服务容器只注入 `SPRING_DATA_REDIS_HOST=redis`，**均未注入 `SPRING_DATA_REDIS_PASSWORD`**
- 各模块 `application-prod.yml` 写 `password: ${REDIS_PASSWORD:}` → 永远回退空

**影响**：Docker 网络内任意容器可无密码读写 Redis；若 6379 被安全组误开放则公网裸奔（当前安全组仅开 22/80，暂未暴露，但纵深防御为零）。

**解决方案**（开认证必须"服务端 + 11 个微服务"同步切换，任一中间态都会导致 Redis 不可用）：
1. `.env` 设置强密码：`REDIS_PASSWORD=<强随机值>`
2. compose redis 服务加 `environment: REDIS_PASSWORD: ${REDIS_PASSWORD}`，并用挂载的 redis.conf 或 `command: ["redis-server", "--requirepass", "${REDIS_PASSWORD}"]` 启用认证
3. 11 个微服务 environment 全部补 `SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}`
4. 验证：`docker exec csmall-redis redis-cli -a <密码> ping` → PONG；无密码连接应被拒绝
5. 可选加固：端口映射改 `127.0.0.1:6379:6379`，避免宿主机网卡直接监听

> ⚠️ 顺序坑：不能只开服务端 requirepass（微服务会全部连不上）；也不能只注入客户端密码（服务端无密码时 AUTH 报 "no password is set"）。两条变更须在同一维护窗口内完成，否则中间态全站 Redis 挂掉。

📄 **完整方案（含哨兵模式，已定稿未执行）见 [[Redis配置加固与哨兵模式方案]]**

### R2. 【Redis】未启用 AOF，仅 RDB 快照，重启丢数据 ✅ 已完成（2026-09-07）

> ✅ **2026-09-07 已修复**：redis.conf 开启 `appendonly yes` + `appendfsync everysec`（执行中踩到 RDB→AOF 迁移丢数据坑，已解决并记录见 [[TODO第一批实现与原理]] §9.2）。

**现状（已核实）**：`appendonly no`；仅 RDB 快照（`save 3600 1 300 100 60 10000`）。秒杀库存预热、购买标记（reseckill）、随机码、AI 会话上下文全部存在 Redis。

**影响**：进程重启/崩溃最多丢 60s~15min 数据；购买标记（`mall:seckill:reseckill:*`）丢失 → 可能重复秒杀；库存 DECR 丢失靠 DB `seckill_stock >= quantity` 条件兜底不超卖，但预扣库存会与 DB 不一致。

**解决方案**：挂载自定义 redis.conf，开启 `appendonly yes` + `appendfsync everysec`（性能与安全平衡，秒杀场景标准做法），RDB 与 AOF 并存。📄 详见 [[Redis配置加固与哨兵模式方案]] §2.1/§2.2

### R3. 【Redis】无内存上限 + noeviction，内存失控风险 ✅ 已完成（2026-09-07）

> ✅ **2026-09-07 已修复**：`maxmemory 256mb` + `maxmemory-policy volatile-lru`（实测生效）。

**现状（已核实）**：`maxmemory 0`（无上限）、`maxmemory-policy noeviction`。

**影响**：Redis 可吃满服务器 16G 内存（系统内存基线已 71%）；noeviction 在内存写满时**拒绝写入** → 秒杀随机码/库存写入直接失败，功能停摆而非降级。

**解决方案**：redis.conf 设 `maxmemory 256mb` + `maxmemory-policy volatile-lru`（只淘汰带 TTL 的缓存键，保护 `mall:seckill:reseckill:*` 永久购买标记不被误淘汰）。📄 详见 [[Redis配置加固与哨兵模式方案]] §2.1

### R4. 【Redis】无自定义 redis.conf，配置无法持久化 ✅ 已完成（2026-09-07）

> ✅ **2026-09-07 已修复**：`/data/csmall/redis/redis-master.conf` 挂载进容器（不加 :ro），固化 R1~R3 全部配置。

**现状（已核实）**：裸 `redis-server` 启动，无配置文件挂载；所有 `CONFIG SET` 调整重启即失。

**解决方案**：在 `/data/csmall/redis/` 生成 `redis-master.conf` / `redis-replica.conf` 并挂载进容器，`command: ["redis-server", "/usr/local/etc/redis/redis.conf"]`，一次性固化 R1~R3 全部配置。⚠️ 挂载**不加 `:ro`**（Redis 运行时会自动 REWRITE conf 记录角色/replicaof，只读挂载会导致故障切换失败或脑裂）。📄 详见 [[Redis配置加固与哨兵模式方案]] §二/§三

### R7. 【内存】容器无 mem_limit + 无 Swap + Nacos/Sentinel 堆可降 ✅ 已完成（2026-09-07）

> ✅ **2026-09-07 已修复**：① 全 21 容器 mem_limit（docker update + compose 持久化）② Nacos 堆 512m ③ Sentinel -Xmx256m ④ Swap 2G（/swapfile + fstab）。实测 available 2.0G→3.9G。

> **2026-08-21 确认**：服务器 available 仅 2.1G、无 Swap、**全部 21 个容器 `mem_limit=0`**（任何进程失控可直接吃满宿主 → OOM 杀服务，历史杀过 ES）。8-04 已做过一轮 JVM 调优（`docs/评估报告/JVM调优方案.md`），本轮为增量优化。

**四项优化**（按收益排序）：
1. **全容器加 mem_limit**（安全网）：11 微服务 768m~1g、es/oap 1.5g、其余 256m~512m；`docker update --memory` **零重启即时生效** + compose 持久化
2. **Nacos 堆 1g→512m**：省 ~480M（最大单点收益，standalone 注册量小，需重启 nacos）
3. **Sentinel 加 -Xmx256m**：省 ~50-100M 并消除默认堆 ~3.5G 隐患（需重启）
4. **Swap 2G**：防瞬时峰值 OOM 缓冲垫（需 ecs-user sudo 执行）

**明确不做**：再压 product/order/seckill 堆（秒杀突发 Full GC 风险）、降 ES 堆 / MySQL buffer pool（影响性能收益小）。

📄 **完整方案（含执行步骤/验证/回滚）见 [[服务器内存优化方案]]**

---

## 三、正文：第二批已完成条目

### 8. 【AI 预算】每日预算按北京时间结算 ✅ 已完成（2026-09-07）

> **2026-08-15 新增**：`TokenBudgetService` 用 JVM 默认时区（服务器容器为 UTC）计算 `ai:daily_cost:<日期>` key 和 TTL，导致**每日预算在北京时间早上 8:00 重置**，而非零点。

**修复方案**：`TokenBudgetService` 中改用 `ZoneId.of("Asia/Shanghai")` 计算日期（`buildKey`）和次日零点 TTL（`getSecondsUntilMidnight`），例如：

```java
private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
// buildKey: LocalDate.now(ZONE)
// getSecondsUntilMidnight: ZonedDateTime.now(ZONE) → 次日 atStartOfDay(ZONE)
```

**涉及文件**：`mall-ai/mall-ai-webapi/src/main/java/com/cooxiao/mall/ai/service/TokenBudgetService.java`

> ✅ **2026-09-07 已修复**：TokenBudgetService 改用 `ZoneId.of("Asia/Shanghai")`（buildKey + getSecondsUntilMidnight 两处），已编译验证。预算现于北京时间零点重置。

---

## 四、正文：第三批/其它已完成条目（#24/#25/#36/#38）

### 24. 【安全】MySQL 密码强化 + 端口 127.0.0.1 绑定 + 容器非 root ✅ 已完成（2026-09-07，前两项）

> ✅ **2026-09-07**：root 强密码 + 3306 收窄 127.0.0.1 已完成（详见顶部第一批表）。第 3 项"容器非 root"（redis/nacos 用户态）暂缓，未做。

> **2026-08-28 新增（06 Q10 全量安全审计实测）**：① **MySQL root 密码仅 4 位**（.env 长度实测）——太弱，内网可爆破；② **中间件端口全映射宿主机 0.0.0.0**（Redis 6379/MySQL 3306/Nacos 8848/ES 9200/Seata 8091/RabbitMQ 5672/SkyWalking 11800...）——安全组挡了公网，但宿主机网卡全监听，纵深防御为零；③ 中间件容器以 root 运行（redis/nacos/gateway，mysql 已 500 非 root）。

**方案（P1）**：
1. .env 改 MySQL root 强密码（≥16 位随机）+ compose 同步 + 应用连接串验证
2. compose 端口映射改 `127.0.0.1:6379:6379` 等（宿主机仅本机监听，Docker 内网不受影响——服务间走容器网络）——**零功能影响、纵深防御大幅提升**
3. 中间件容器加非 root 用户（redis/nacos 镜像支持 -u）

**面试价值**：能讲"安全审计实测出 MySQL 密码太短、端口映射过宽、容器 root"的审计深度

### 25. 【安全】JWT_SECRET 生产随机化 ✅ 已完成（2026-09-07）

> **2026-08-28 新增（06 Q10 审计实测）**：生产 .env 的 `JWT_SECRET` **和代码默认值一样**（CooxiaoMall2026Jwt...开头）——"生产用环境变量"形同虚设，**攻击者 clone 公开仓库就能伪造任意用户 JWT**。这是当前最优先的安全项。

**方案（P0，改动小）**：
1. 生成随机强密钥（≥64 字节，如 `openssl rand -base64 48`）
2. 更新服务器 /data/csmall/.env 的 JWT_SECRET
3. **同步更新全部 11 个服务的配置引用**（JWT_SECRET 环境变量名不变，只换值）
4. **全站重启 + 所有用户重新登录**（旧 token 全部失效，可接受）
5. 验证：登录/秒杀/支付全链路 + 旧 token 被拒

**注意**：换密钥 = 所有已签发 token 立即失效（用户要重新登录）——选低峰期执行；顺带把密钥版本化（kid）提上 TODO #17 P1。

**面试价值**：能讲"我发现生产 JWT 密钥=代码默认值（伪造 token 风险），主动换随机密钥"——最有力的安全审计案例

> ✅ **第一批 #1 已于 2026-09-07 完成**：生产密钥=代码默认值 → 已换 64 字符随机密钥

### 36. 【MQ】死信队列 DLX 评估 + 订单消费者 requeue 修复 ✅ 已完成（2026-09-07）

> ✅ **2026-09-07 完成（requeue 修复 + 订单 DLX）**：① OrderQueueConsumer 失败处理从 `basicNack(requeue=true)` 无限重试改为 **x-death 限次重试（最多 3 次）**，达上限 requeue=false；② **订单队列 DLX 已实现**——order_queue 声明 dead-letter → order_ex_dlx → order_queue_dlx，新增 `OrderDlxConsumer` 死信消费者（记录原因 + ERROR 告警）。秒杀消费者"静默丢弃"（basicAck）属 #14 P0，待修后秒杀队列再决定是否接 DLX。

> **2026-09-03 新增（源自 10 Q8 讨论）**：项目 MQ 可靠性"有重试没死信"——企业级五环里缺死信队列 + 可观测。

**现状（代码实证）**：
- acknowledge-mode: manual + Spring listener retry（max-attempts=3 间隔 1s，prod yml）
- 秒杀"先落库 seckill_message_retry + MessageRetryTask 每 5s 重发（retryCount<3）"（TODO #11 低配延迟队列）
- **全项目无 x-dead-letter 配置 = 没有死信队列**——重试耗尽仍失败的消息无归宿（丢或卡）
- ⚠️ **OrderQueueConsumer 仍是 basicNack(requeue=true)** = 毒消息无限重试挂单（与 TODO #19 关联）

**面试价值**：能讲"项目有重试没死信（TODO #36）+ 订单消费者 requeue 还在（TODO #19）——企业级五环：发送确认/消费重试/DLX/可观测/幂等，缺哪环我清楚"

> 🟡 **第二批 #3**（遗留：秒杀消费者静默丢弃见 TODO #14）

### 38. 【部署】Dockerfile 双份不一致：模块目录残留 Alpine 旧版 ✅ 已完成（2026-09-07）

> **2026-09-03 新增（源自容器化部署企业级评估）**：项目存在**两份 Dockerfile**——compose 实际构建用 `deploy/docker/dockerfiles/mall-*.Dockerfile`（11 份，`FROM eclipse-temurin:21-jre` Debian 系 + 完整 JVM 参数 + SW agent，**已修复版**）；但**各模块目录下残留 11 份过期版**（`mall-sso/Dockerfile`、`mall-seckill/Dockerfile` 等，全部 `FROM eclipse-temurin:21-jre-alpine` + 无 JVM 参数 + 无 agent，**Alpine 事故旧版未删**）。

**风险**：任何人（包括未来的自己）照着模块目录 Dockerfile 手动 build → **重新踩 Q12 的坑**（Alpine musl 与 MD5 不兼容，全服务崩溃）；且缺 MaxMetaspaceSize/SW agent 参数 = 运行时行为与生产不一致。

**方案（低成本，建议尽快）**：
1. **删除模块目录 11 份过期 Dockerfile**（推荐——单一事实源 = `deploy/docker/dockerfiles/`，compose 已指向它）
2. 或保留但加注释头"⚠️ 生产勿用，以 deploy/docker/dockerfiles/ 为准"（防误用）
3. 验证：`docker compose config` 确认全部服务 dockerfile 指向 deploy 版

**面试价值**：能主动讲"我发现项目 Dockerfile 双份不一致（模块目录残留 Alpine 旧版），清理统一到单一事实源"——运维洁癖 + 防坑意识

> ✅ **第一批 #5 已于 2026-09-07 完成**：低成本防踩坑

---

## 五、已完成表（2026-09-07 第一批，从 [[TODO文件]] 文末迁移）

| # | 事项 | 完成日期 | 备注 |
|----|------|---------|------|
| 18 | **#25 JWT_SECRET 生产随机化** | 2026-09-07 | .env 换 64 字符随机密钥，11 容器 env 一致，admin 登录/验签/伪造拒绝全链路回归通过（执行前=代码默认值=最严重安全洞）|
| 19 | **R7 内存优化** | 2026-09-07 | mem_limit 全 21 容器 + Nacos 堆 512m + Sentinel -Xmx256m + Swap 2G；available 2.0G→3.9G |
| 20 | **#24 MySQL 强密码 + 端口收窄** | 2026-09-07 | root 43 位强密码（ALTER USER 双 host：localhost + %）；3306 收窄 127.0.0.1 |
| 21 | **R1~R4 Redis 加固** | 2026-09-07 | requirepass + AOF everysec + maxmemory 256mb volatile-lru + 自定义 conf 挂载；6379 收窄 127.0.0.1。踩坑记录见 [[TODO第一批实现与原理]] §9.2/9.3/9.4 |
| 22 | **#38 Dockerfile 双份清理** | 2026-09-07 | 11 份模块目录残留 Dockerfile 已 git rm，单一事实源 = deploy/docker/dockerfiles/ |

> 📄 第一批完整原理/执行/踩坑文档：[[TODO第一批实现与原理]]；轮换操作手册：[[运维手册--密钥密码轮换]]

### 第一批之前（2026-08-04 归档）

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

## 六、#14 已完成部分（P0 三层 + order_type 治本 + 方案Y + P1 对账）

> **说明**：#14 尚未全部完成（P2 配置层归第三批），故**原条目仍保留在 [[TODO文件]]**。此处归档 **#14 已完成的 P0/P1 部分**，供查询。

**已完成（2026-09-07）**：
- **P0 落库失败不静默（第3层）**：SeckillQueueConsumer 库存不足从 basicAck 静默丢弃改为三兜底——失败留痕 + 已付款告警（新增 IOmsOrderService.getOrderStateBySn Dubbo 查询）+ x-death 限次重试（与 #36 同款）
- **订单 order_type 标识（治本前置）**：oms_order 加 order_type 列（Flyway V6），秒杀入口置 1、普通入口强制 0（防伪造）；markSeckillPurchased/clearSeckillOrdered 仅秒杀单执行（修复"普通订单被误当秒杀单写 reseckill 标记"的潜在 bug）；本地普通购买实测验证守卫生效
- **P0 方案Y 支付前校验本单成交状态（success 落库）**：P0"付款前查库存"原方案放弃（语义缺陷见下）后，用查询本单是否已写入 success 表 替代实现——秒杀单（orderType=1）支付前经新 Dubbo `IForOrderSeckillRecordService.isSeckillSuccessRecorded(orderSn)` 校验本单是否已成功落库，未落库则拦截支付（防"Redis 预扣放行但 DB 扣减失败 rows==0"的用户付了钱没货）；查本单而非剩余库存，不误拦已成交最后一件；Dubbo 异常保守放行。本地秒杀→支付全链路实测通过
- **P1 对账任务**：运行期 5 分钟轻量纠偏（\|diff\|≥2 直接修、\|diff\|=1 连续 3 次才修）+ 凌晨全量校准（\|diff\|≥1 即修 + 补建缺失 key），以 DB 为唯一基准修正 Redis，本地实测修掉 sku26/sku35 漂移、12 sku 零误改

> 📄 方案与原理详见 [[秒杀对账任务实现方案]]、[[TODO第二批实现与原理]] §四。
