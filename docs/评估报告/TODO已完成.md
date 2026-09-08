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

**第二批已完成项**（2026-09-08：#8/#36/#23/#14 P0+P1/#33/#5/#2+#34——**第二批全部代码项完成并已全量部署服务器**）：

| 编号 | 事项 | 完成情况 |
|------|------|---------|
| **#33** | 双索引数据不一致（A2：统一索引 + mall-search 只读降级层） | ✅ 完成 + **已部署**（2026-09-08；本地实测 AI 主链路通 + 停 mall-ai 前端秒降级；服务器验证：AI 索引重建后 19 条无下架残留、普通搜索 /search 返回正常、图片 URL 完整）。明细见 [[TODO第二批实现与原理]] §五 |
| **#8** | AI 预算按北京时间结算 | ✅ 完成 + **已部署**（2026-09-07 改码，TokenBudgetService 时区，09-08 随批上线） |
| **#36** | DLX 死信 + OrderQueueConsumer requeue 修复 | ✅ 完成 + **已部署**（x-death 限次重试 + 订单队列 DLX + OrderDlxConsumer；服务器 order_queue_dlx 已建）|
| **#23** | 漏触发接口补 @Validated | ✅ 完成 + **已部署**（DTO 补规则 + 全局异常处理器补全 + admin.js 废弃标注）|
| **#14** | Redis 主从切换防数据 P0+P1 | ✅ 完成 + **已部署**（P0 三层 + order_type 治本(Flyway V6) + 方案Y + P1 对账任务，见 §六）|
| **#5** | Sentinel 能力补齐 P0 | ✅ 完成 + **已部署**（2026-09-08：统一 Nacos 管理 flow+degrade；order/sso 加 datasource；秒杀代码规则改"宕机兜底"；规则 JSON 入库 `deploy/docker/sentinel/`；sso 补 dashboard env；`eager:true` 修复 transport 懒加载。服务器实测 30 并发 adminLogin → 20×429 限流生效，Dashboard 三应用可见规则。P1 热点/P2 集群未做）。明细见 [[Sentinel部署执行清单-2026-09-08]]、[[TODO文件]] #5 |
| **#2+#34** | AI 接口限流 + 并发闸门 | ✅ 完成 + **已部署**（2026-09-08：Sentinel 3 组规则 ai-chat=5/ai-reason=10/ai-light=30 + Semaphore 并发闸门=20 挂 LLM 调用汇聚点 + 每用户频控 60s/10；AiBusyException 降级闭环=繁忙永不 500。服务器实测 30 并发 /ai/search → 10×200+20×429 无 500、频控 15 连打全 429。部署两坑已修：pom 缺 datasource-nacos + blockHandler 签名。问答缓存/多实例未做）。明细见 §七·六、[[AI限流与并发闸门-部署执行清单-2026-09-08]] |
| **#13** | Nacos 开启认证 | ✅ 完成 + **已部署**（2026-09-08：NACOS_AUTH_ENABLE + 随机 TOKEN/IDENTITY + 管理员密码初始化 + 11 服务全客户端同步[discovery 11 + Dubbo registry 8 + Sentinel datasource 4, Seata file 豁免]；实测无 token=403/真 JWT/全服务注册正常/Sentinel 规则热更新。**历史隐患修复**：nacos 无数据卷 → 重建丢 derby 配置 → 补 nacos_data 卷 + 仓库 JSON 重建 6 规则）。明细见 §七·七、[[Nacos认证-部署执行清单-2026-09-08]] |
| **#29** | 数据库定期备份 | ✅ 完成 + **已部署**（2026-09-08：`/data/csmall/backup/backup-db.sh` docker exec mysqldump 6 库 + gzip + 保留 7 天 + cron 每日 02:30；仓库留档 `deploy/backup-db.sh`；实测 49KB/6 库/39 表完整。恢复演练待做）。明细见 [[TODO文件]] #29 |

> 📌 **部署回填（2026-09-08 维护窗口执行完毕，服务器实测）**：11 个微服务 jar 全量重建替换（11:25~12:59，旧 jar 备份 `/data/csmall/jars/backup-20260908/`）+ Flyway V6 自动执行成功（oms_order 已加 `order_type` 列默认 0，flyway success=1）+ ES 索引清理（删空索引 `cool_shark_mall_index` + 旧 `cool_shark_mall_index2`，AI 索引 `/ai/syncAll` 重建后 **19 条**）+ 前端 dist 更新 + 21 容器全 Up。**部署中修复 mall-ai 既有 bug**：AI 重排偶发降级/超时，真因 = reasoning 模型过度思考致 content 截断（JSON 任务改用 `deepseek-chat`、SSE 对话保留 `v4-flash`），详见 [[TODO第二批实现与原理]] §5.4.5 边界表 #21。

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

---

## 七、#5 Sentinel 能力补齐 P0（2026-09-08 完成并部署）

> **说明**：#5 P1 热点参数限流 / P2 集群流控未做（原条目保留在 [[TODO文件]]），此处归档 **P0 已完成部分**。

**审计修正（原方案文档与事实不符）**：原记载"秒杀 QPS=10 = Nacos + 代码双保险"，实测 **Nacos SENTINEL_GROUP 规则全空** + seckill 日志 `converter can not convert rules because source is empty` = **本地代码规则被 Nacos 空配置覆盖，秒杀限流当时实际失效**（历史覆盖坑复现：FlowRuleManager 整体替换非叠加，配了 datasource 后权威源 = Nacos）。

**完成内容（2026-09-08）**：
- **统一 Nacos 管理规则**：Nacos 建 5 个 dataId（SENTINEL_GROUP）——`mall-seckill-flow-rules`(QPS=10)/`mall-seckill-degrade-rules`(慢调用熔断)、`mall-order-flow-rules`(新增/支付订单 QPS=20)/`mall-order-degrade-rules`、`mall-sso-flow-rules`(adminLogin QPS=10)；规则 JSON 事实来源入库 `deploy/docker/sentinel/*.json`（git add -f，与 compose/redis-conf 同策略）
- **order/sso 接入 datasource**：pom 加 `sentinel-datasource-nacos`，prod/test yml 配 flow+degrade datasource（sso 仅 flow——登录失败属业务异常，配 degrade 会误伤）
- **秒杀代码规则改兜底语义**：`SentinelFlowRuleConfig` 保留为"启动瞬态 + Nacos 宕机兜底"（Nacos 正常→以 Nacos 为准；Nacos 空→代码也被擦；Nacos 宕机→代码兜底存活），注释写明机制
- **sso 补 dashboard env**：compose 加 `SPRING_CLOUD_SENTINEL_TRANSPORT_DASHBOARD: sentinel:8858`（曾漏同步导致 sso 心跳连 nacos:8858 失败 `Connection refused`）
- **`eager: true` 修复 transport 懒加载**：SCA 默认懒加载，无流量时 CommandCenter 不启动 → Dashboard 查不到规则/监控；实测 sso 打流量后才出现 `Begin listening at port 8880`，加 eager 后启动即初始化
- **清理死文件**：删除 `deploy/docker/sentinel-rules.json`（无代码/compose 引用、GBK 乱码、count=100 与真实 10 不符）

**服务器验证（2026-09-08 实测）**：
- Nacos 5 规则发布成功（curl `content@file` 单次编码——曾踩双重编码坑：python quote + --data-urlencode 各编一次 → 存了 URL 编码串）
- 三容器（order/sso/seckill）重建后 record 日志 flow+degrade 全部 `notify-ok` 从 Nacos 加载；心跳 0 失败
- **限流实测**：adminLogin 30 并发 → 10×400(进业务) + **20×429(被拦)** = QPS=10 精确生效
- transport 端口 8880/8872/8870 均可达（dashboard 视角 `getRules?type=flow` 拉到真实规则）；Dashboard 左侧可见 mall-sso/mall-order/mall-seckill 三应用

> 📄 完整执行清单与验证见 [[Sentinel部署执行清单-2026-09-08]]；原方案与 P1/P2 见 [[Sentinel能力补充计划]]。

---

## 八、#2+#34 AI 限流 + 并发闸门（2026-09-08 完成并部署）

> **说明**：问答 Redis 缓存（#34 层 3）、多实例扩容（层 6，随 #4）未做，属后续可选。

**三层防护**：
- **① Sentinel 入口 QPS 限流（#2）**：3 组资源 ai-chat=5（流式/同步对话）/ ai-reason=10（搜索/问答/对比）/ ai-light=30（补全/推荐/历史）；Nacos mall-ai-flow-rules 统一管理；AiController @SentinelResource + 每接口专属 blockHandler（返回 429 / SSE error）
- **② 并发闸门（#34 核心）**：AiConcurrencyGuard Semaphore=20，挂**所有真实 LLM 调用汇聚点**（DeepSeekAiClient.chat/chatWithModel/doChat/embed + ChatServiceImpl.streamDeepSeek）——按请求限流 ≠ 按 LLM 调用限流（一次 /ai/search 内部调 2 次 LLM），闸门统计外部 API 真实并发占用；满抛 AiBusyException → 服务内既有降级路径（Search 纯 ES/Ask busy VO/SSE error）= **繁忙永不 500**
- **③ 每用户频控**：AiUserRateLimiter Redis INCR+TTL 60s 窗口，10 次/分，防单用户刷爆预算

**服务器实测（2026-09-08）**：
- 30 并发 `/ai/search` → **10×200 + 20×429**（ai-reason QPS=10 精确生效，无 500）
- 单用户连打 15 次 /ai/ask → **全 429** + 日志 `【AI每用户频控】userId=1 已调用 15 次/60s，超限 10 次`
- 单次调用正常（state=200）

**部署踩两坑（已修复提交 bddb590）**：
1. mall-ai pom **缺 `sentinel-datasource-nacos`**（yml 配了 datasource 但漏依赖，order/sso 在 #5 加过）→ 启动 `ClassNotFoundException: NacosDataSource` → 补依赖
2. **blockHandler 签名缺原方法参数**（只写 `(BlockException e)`）→ Sentinel 反射要求 = 原方法全部参数 + BlockException → 找不到匹配 → FlowException 落全局 500 → 改每接口专属签名 + 泛型 busyResult

> 📄 完整设计原理 + 面试话术见 [[TODO第二批实现与原理]] §七·六；执行清单见 [[AI限流与并发闸门-部署执行清单-2026-09-08]]。

---

## 九、#33 搜索双索引统一（A2：统一索引 + mall-search 只读降级层，2026-09-08 完成并部署）

**问题重述**：mall-search 与 mall-ai 各维护一个 ES 索引 + 各一套同步链路 → 数据漂移（实测 AI 20 vs search 18 条，且都残留已下架商品）。

**复核修正**（2026-09-08 三重实证推翻"用户可见 bug"原判断）：① Web 前端早已 100% 走 AI 搜索（search.js 无 /search 调用）；② mall-search 零真实流量（nginx 24h 0 请求）；③ mall-search 是孤立遗留（零反向依赖、无 Dubbo provider）。**真实问题 = 同步模型缺陷**：只 upsert 不 delete、删除/下架/审核不触发同步、写入不过滤业务状态 → 已下架商品残留在索引。

**方案 A2 落地**（三方讨论选定）：统一单一索引 `cool_shark_mall_ai` + mall-search 改造为**只读普通搜索降级层**（纯 ES multi_match，无写链路=无漂移）+ 前端 fallback（AI 确定性故障 → /search）。

**服务器验证**：AI 索引 `/ai/syncAll` 重建后 19 条（剔除下架 18）；删空索引 `cool_shark_mall_index` + 旧 `cool_shark_mall_index2`；普通搜索 /search 返回正常、图片 URL 完整（resource-host）。部署中修复 mall-ai reasoning 模型 JSON 截断 bug（JSON 任务换 deepseek-chat）。

> 📄 完整评估（A1/A2 决策树）+ 实施细节 + 面试话术见 [[TODO第二批实现与原理]] §五、[[搜索双索引统一与一致性评估]]。

---

## 十、#23 校验漏触发修复（DTO 补规则 + 全局异常补全，2026-09-08 完成并部署）

**审计修正**：原 TODO 称"5 个接口漏触发 @Validated"——逐 DTO 核查发现三类：
| 类 | 接口 | 真相 | 处理 |
|---|---|---|---|
| A 真 bug | doRegister | DTO 有规则但漏 @Valid | ✅ 补 @Valid |
| B 无规则 | add/edit 地址、updateAdmin | DTO 根本没规则（非漏触发） | ✅ 补规则 + 触发 |
| C 注解空转 | addSeckillSpu/Sku | 有触发但 DTO 无规则 | ✅ 补规则 |
| D 正常 | 31 接口 | 触发+规则都有 | ✅ 复核 |

**⭐ 连带关键发现**：全局异常处理器缺 `MethodArgumentNotValidException`/`ConstraintViolationException` handler → 校验失败落 Throwable 返回 **500 而非 400**——补 @Valid 只是第一步，异常处理不补全校验仍"假生效"。已补两个 handler（mall-common）。

**规则设计要点**：编辑类 DTO（updateAdmin/editAddress）只强制 id @NotNull（MyBatis-Plus 动态 SQL 允许部分更新）；"手机/固话二选一"DTO 无法表达 → Service 层兜底。

> 📄 完整审计 + 治理清单（39 接口 5 类）+ 面试话术见 [[TODO第二批实现与原理]] §二。

---

## 十一、#13 Nacos 认证（2026-09-08 完成并部署）

**方案 A 落地**：NACOS_AUTH_ENABLE + 随机 TOKEN（Base64 48字符）/IDENTITY + 管理员密码初始化（2.4+ 无默认密码，`POST /v1/auth/users/admin`）+ **11 服务全客户端同步**（Spring Cloud discovery 11 + Dubbo registry 8 + Sentinel datasource 4；Seata file 模式豁免）。

**服务器验证**：无 token=403、登录拿真 JWT（非 AUTH_DISABLED）、全服务注册正常（HTTP 实例 + *-dubbo 20880 分离）、Sentinel 规则热更新正常。

**历史隐患修复**：nacos 是唯一没挂数据卷的中间件 → 重建容器 derby 数据全丢（6 条规则 dataId 丢失实证）→ compose 补 `nacos_data:/home/nacos/data` 卷 + 仓库 JSON 重建规则（listener 热更新服务无需重启）。**待办 #46**：服务器 nacos 容器卷挂载尚未应用（下次动 nacos 前必做）。

> 📄 完整原理（JWT 三角色/流程/三类客户端透传）+ 两坑 + 面试话术见 [[TODO第二批实现与原理]] §七·七；执行清单见 [[Nacos认证-部署执行清单-2026-09-08]]。

---

## 十二、#29 数据库定期备份（2026-09-08 完成并部署）

**落地**：备份脚本 `/data/csmall/backup/backup-db.sh`（docker exec mysqldump 6 库全量 --single-transaction + gzip + 保留 7 天 + 密码从 .env 读不硬编码）+ cron `30 2 * * *` + 仓库留档 `deploy/backup-db.sh`。

**实测**：49KB / 6 库 / 39 表完整性验证通过（CREATE DATABASE×6 / CREATE TABLE×39）。

**待办**：恢复演练（TODO 铁律"备份没验证过=没有备份"，建议导临时库验证）→ 已记入 [[TODO文件]] 第三批 #47（有空再做）。

> 面试价值：能讲"备份要有恢复演练，没验证过的备份等于没有"的运维底线。
