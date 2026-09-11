# CoolShark 已完成任务归档

> **创建日期**: 2026-09-07
> **来源**: 从 [[TODO文件]] 拆分迁移——原 TODO 文件只保留未完成/部分完成/暂缓项，本文件归档**全部已完成**任务。
> **维护**: 新完成任务从 [[TODO文件]] 移入本文件末尾；若本文件过长，按批次切分为 `TODO已完成-2` 等。

---

## 〇、第三批已完成（2026-09-09 起）

### #46. 【运维】nacos 数据卷挂载重启 ✅ 已完成（2026-09-09）

> **说明**：#46 是第三批 P0 运维项——nacos 此前是 21 容器里唯一无数据卷的中间件，derby（配置/用户/规则）裸存容器可写层，重建即丢（#13 部署实测丢过 6 条 Sentinel 规则）。

**完成内容（2026-09-09 维护窗口）**：
- **compose 同步**：本地已含 `nacos_data:/home/nacos/data` 卷定义 → scp 覆盖服务器（先 diff 确认仅差 9 行）→ md5 校验
- **数据迁移**：停容器 → `docker cp` 备份 derby（8.5M = protocol 5.8M + derby-data 2.8M，JRaft 停机 compact 后为持久状态）→ 灌入命名卷 → 重建挂卷
- **验证全通过**：无 token 403 ✅ / 管理员登录 accessToken ✅ / **6 条 Sentinel 规则**完整 ✅ / 挂载 `csmall_nacos_data` ✅ / **27 服务重新注册** ✅

**⭐ 踩坑（面试最值钱的素材）——compose 卷名前缀**：
- 手动 `docker volume create nacos_data`（无前缀），但 compose 卷名 = `项目名_卷名` = **`csmall_nacos_data`** → compose 不认识手动卷，重建时**自动创建空卷**挂给 nacos → nacos 在空库全新初始化 → 登录报 `User nacos not found`
- 排坑三证据：卷列表出现两个卷 / `docker inspect` 看挂载名 / `docker compose config` 显示 `name: csmall_nacos_data`
- 修复：删空卷 → `docker volume create csmall_nacos_data`（正确卷名）→ 从宿主机备份灌数据 → compose 重建 → 全验证通过
- **教训**：① compose 卷名带项目前缀，手动建卷须用全名或先 `docker compose config` 核对；② 容器数据备份以停机后为准（运行中 du 看到的是 JRaft 日志膨胀态）；③ 宿主机备份 + 卷双保险让排坑零损失

📄 **完整原理/疑惑点/面试话术见 [[TODO第三批实现与原理]] §一**

### #47. 【运维】数据库备份恢复演练 ✅ 已完成（2026-09-09）

> **说明**：#47 是第三批 P0 运维项——#29 备份已上线（cron 每日 02:30），但 TODO 铁律"备份没验证过 = 没有备份"，从未做过恢复演练。

**完成内容（2026-09-09，独立临时容器方案）**：
- **方案演进**：第一版"生产容器内 sed 改库名导临时库"被否——备份含 `DROP TABLE IF EXISTS`，sed 改写依赖"正则覆盖完备性"，漏一处即清空生产表 → 改用**独立临时 mysql 容器**（同版本 8.0、限 512m、匿名卷、无端口映射），备份**原名直接导入**（临时库无生产数据，无需 sed），验完 `rm -f` 删容器
- **验证结果**：cron 备份 `cs_mall_20260909_0230.sql.gz` 在全新实例完整还原 —— **6 库 39 表** + 抽样 5 表（ums_user 110/pms_spu 20/ams_admin 3/oms_order 86/seckill_spu 6）与生产**逐行一致** → 备份验证可用
- **生产零接触**：独立容器 + 无端口映射 + 匿名卷随删随清；`csmall-mysql` Up healthy 无损

**诚实边界（面试可讲）**：只验证了单日全量备份可还原，未做单表定点恢复（`--databases` 全库模式不支持）与跨机恢复（备份在服务器本地，灾难场景需异机/对象存储）

📄 **完整方案对比（sed vs 独立容器）/原理/面试话术见 [[TODO第三批实现与原理]] §三**

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
| 12 | **服务器权限收紧** | 2026-08-03 | <AI账号> 独立账号 + sudoers 白名单 |
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

---

## 十三、跨机集群（第三批主线：#4 秒杀双实例 + #9 Redis 主从哨兵，2026-09-09 完成）

**总览**：老机 `8.156.77.197`/`172.29.193.239`（21 容器）+ 新机 `csmall-node2` `47.109.70.197`/`172.29.193.240`（5 容器），**同 VPC 同安全组**，组成演示级跨机集群（**不防地域灾难**）。

**目标拓扑（已全部就位）**

| 角色 | 老机 | 新机 |
|---|---|---|
| Redis | **主** 6379 | **从** 6380 + **3 哨兵** 26379-26381（quorum=2） |
| 秒杀 | **实例1** 10007 | **实例2** 10017 |
| Dubbo | product `172.29.193.239:20880` / order `:20881` | seckill-2 `172.29.193.240:20880` |

**阶段与实测数据**

| 阶段 | 内容 | 关键实测 |
|---|---|---|
| 0 | 定时任务分布式锁（代码） | `RedisLockUtils`（`SET NX EX` + Lua CAS）；9/9 单测 + 本地双实例联调 4 次漂移修正 |
| A | 新机铺路 | compose 5 服务 + 4 个 Redis 配置 + `.env` + skywalking-agent 39M |
| 2a/2b | 老机端口放行 | 3306/6379 绑私网 IP；product/order 发布 20880/20881 + 注册宿主 IP；跨机 4 端口全通 |
| 3 | 新机 Redis 从 + 3 哨兵 | 主从 `link up`、21 键一致、副本 `READONLY` 拒写、3 哨兵视角一致、配置持久化修通 |
| 3.5 | 11 服务迁哨兵客户端 | 4 批灰度全 `Started` + health 200；seckill 对端由容器 IP `172.18.0.5` → 宿主 IP `172.29.193.239` |
| **3.6** | **故障转移演练** | **选主 6.1s、客户端 Lettuce 9.1s 自愈、数据零丢失（DBSIZE 21 全程不变）、切回 1.1s**；发现**脑裂窗口 10.9s** |
| **4** | **秒杀双实例** | 副本 `Started in 78s`；**Nacos 2 实例**（HTTP + Dubbo）；**锁互斥：受控争用 10 次 `SET NX` 全失败、0 次误删**；**负载分布 60 请求精确 30:30**；停实例剔除通过（存活实例承接、0 个 5xx） |

**关键原理与踩坑**（深挖见 [[TODO第三批实现与原理]] §五/§六，命令级见 [[跨机集群实施执行清单-2026-09-09]]）

1. **跨机 Dubbo 根因**：Nacos 只做注册发现、**不做流量代理** → 容器注册 `172.18.0.x:20880` 跨机不可达 → `DUBBO_IP_TO_REGISTRY` + `DUBBO_PORT_TO_REGISTRY` + 端口发布（Path 1）
2. **客户端必须迁哨兵模式**：standalone 客户端在主从切换后会打到只读从库报 `READONLY`
3. **哨兵配置持久化三层坑**：文件权限（uid 999）/ 目录挂载 / **单文件挂载无法 rename**（`Resource busy`）
4. **🔴 哨兵 conf 是运行时状态**（`config-epoch`/`myid`/known-*），仓库模板**不得反向覆盖**
5. **脑裂窗口 10.9s**（老主库重启后自认为主）→ `min-replicas-to-write 1` 的必要性（Redis 官方文档同一场景佐证）
6. **停实例 16s 失败窗口**：Nacos 即时摘除（优雅停机主动注销）vs 客户端 LB 缓存延迟 → 需**优雅下线**或网关重试
7. **镜像获取**：两台都拉不到 Docker Hub（`i/o timeout`）→ **镜像名前加镜像站前缀** `docker.1ms.run/<image>` + `docker tag` 标准名

**遗留项**：#14-P2（`min-replicas-to-write`，待做）、#51（容器 restart 策略，P2）、#53（网关重试/优雅下线，P2）、#50（哨兵认证，观察项）、#52（镜像路径，已记录事实）

---

## 十四、#58 AI 模型名停用风险 + `thinking` 开关 + 模型配置可配化（2026-09-10 完成并部署）

> **问题**：生产 `chat-model: deepseek-chat` —— 该模型名**已被官方公告停用**（2026-04-24 公告、2026-07-24 名义停用，靠兼容层在跑）；根因是当初为绕开"`deepseek-v4-flash` 是思考模型、对 JSON 小任务思考到吃满 `max_tokens` 把 `content` 挤空"，**改用"换模型名"这个过时手段**。

**正解**：官方机制是「**同一模型 + `thinking` 开关**」（`{"thinking":{"type":"disabled"}}`）—— 从机制上关掉思考，而非靠提示词"求它别想"。

**本次交付（Step 1）**

1. **模型配置可配化**：新增「**档位层 `models`**（逻辑档位 → 实际 model id）+ **任务层 `tasks`**（任务 → 档位 / 思考 / 温度 / max_tokens）」；**Java 代码里不再出现任何模型名**；yml 模型名全部改**显式环境变量占位符** → **官方改名 = 改 `.env` + 重建一个容器，不重编译**
2. **新增 `AiTask`**（`CHAT` / `JSON` / `EXPAND` / `COMPARE`）—— 取代"用模型名区分任务能力"
3. **9 项硬编码全部清零**：2 处模型名 / 2 处温度 / 1 处 max_tokens / SSE 自建请求体（与客户端重复）/ yml 字面量 / `compare-model` 死配置 / `embed` 死代码
4. **SSE 收敛**进 `AiClient.streamChat`（与同步调用共用同一套档位 / 预算 / 闸门逻辑）
5. **🐛 修复隐患**：主类 `@EnableConfigurationProperties(AiProperties.class)` 与 `@Component` **重复注册了两个同类型 bean** → 类型注入歧义 + `@PostConstruct` 执行两遍（原先仅靠注入字段名恰好等于 bean 名才没炸）→ 已移除重复注册并加防复发注释
6. **新增 2 个测试类**（yml→Java 绑定 / 请求体规则），7 项全绿；全反应堆 13 模块编译通过

**10 格实测实验**（服务器直连 DeepSeek，最小请求）：复现 400 规则（思考模式 + tools 不带 `reasoning_content`）/ 证实 `thinking: disabled` 生效 / 证实 tools 可触发 / **发现 `tools` 与 `response_format` 互斥** / 两轮循环收敛 / SSE + 思考模式对现有解析器安全。

**生产验证（2026-09-10）**：路由日志**恰好 1 次**（修复前 2 次）；`/ai/search` 5s 返回 AI 重排说明；`/ai/ask` 4s 返回完整推荐；预算记账 0 → **0.002492 元**；`content 为空` / `reasoning` / 4xx 告警均 **0**；jar MD5 落位校验一致；回滚 tag `csmall-mall-ai:before-step1` 就位。

📄 **完整记录见 [[AI模型名停用风险与thinking参数改造方案]]**（§3.1 实验 / §4.5 模型可配化 / §4.6-4.7 Spring AI 与 Boot 升级评估 / §十一 实施与部署记录）、[[TODO第三批实现与原理-1]]（技术选型过程）

---

## 十五、🔥 生产故障抢修：nginx 静态解析上游 IP → 网关重建后全站 API 502（2026-09-10 发现并修复，非计划内）

> **说明**：本条**不是 TODO 计划项**，是 2026-09-10 晚的**突发生产故障抢修**（用户报"登不上、全是 502"）。因其有完整排查链与一类根因价值，按纪律归档于此。
> **原理与话术**：[[问题解决--服务注册与网关路由]] **问题 2**（与二批 #6"身份错"归为同一族"调用方持有错误地址"）

**根因（一句话）**：`proxy_pass http://mall-gateway:10087` 里的主机名，nginx **只在加载配置那一刻解析一次并永久缓存**（等价写死 IP）；网关容器 **09-09 19:22(CST) 被重建**（IP `172.18.0.9` → `172.18.0.7`）后，nginx 仍把 `/user /front /ai` 全打到 `.9` —— 而 `.9` 在 **09-10 19:44 被重建的 mall-ai 占用**，其 10087 无人监听 → `connect() failed (111)` → **502**。

**故障窗口**：**约 24.5 小时**（09-09 19:22 → 09-10 19:58）。

**排查链（逐层排除，7 步）**：① `docker ps` 21 容器全 Up（非挂）→ ② 内存/磁盘/负载正常（非资源）→ ③ `curl 网关 /actuator/health` = **200**（网关健康）→ ④ nginx 日志 `connect() failed (111) upstream http://172.18.0.9:10087`（**502 是 nginx 报的**）→ ⑤ `docker inspect` 网关真实 IP = `172.18.0.7`，`.9` = **mall-ai**（打错容器）→ ⑥ conf 里写的是主机名，容器内 `getent hosts mall-gateway` = `172.18.0.7` **正确**（**DNS 没问题，是 nginx 缓存**）→ ⑦ frontend StartedAt `09-08 04:14 UTC` + `Restarts=0`，晚于它的网关重建 → **缓存旧值确认**。

**处置（先恢复、再根治、不夹带）**

1. **秒级恢复**：`docker restart csmall-frontend`（零配置改动、可重复）
2. **隔离 A/B 验证**（临时网络 + 一次性容器，**全程未碰生产**）：
   | 场景 | 动态解析 | 静态解析（原状） |
   |---|---|---|
   | 基线 | 200 | 200 |
   | **容器重建后** | **200（自动跟随）** | **502（仍打旧 IP）** |
   附带实证：静态写法启动时解析不到主机名 → `host not found in upstream` **直接起不来**
3. **生产改造**：`resolver 127.0.0.11 valid=10s ipv6=off` + `upstream mall_gateway { zone …; server mall-gateway:10087 resolve; }`，6 处 `proxy_pass http://mall-gateway:10087` → `http://mall_gateway`。**刻意不用 `set $var` 变量法**（会改变 URI 处理语义，本项目有 `/api/ 剥前缀` rewrite）→ 用 `upstream` 块保证语义零变化
4. **应用**：`docker cp` → `nginx -t` → **`nginx -s reload`（热加载、零停机、未重启容器）**
5. **烘进镜像**：先打回滚标签 `csmall-frontend:pre-dynamic-20260910`（旧镜像 `1ed14bbc615b`），再 `docker commit`（新镜像 `917eead3…`）—— 因服务器**无 `nginx:alpine` 基础镜像**，`docker compose build` 短期不可用，不 commit 则容器重建即回退

**验证（逐条路由）**：真登录 `/user/sso/login` 返回 **`tokenValue`** ✓；`/api/front/category/all`（**rewrite 剥前缀**）语义未变 ✓；`/admin/dashboard`（Accept:json → `@gateway`）✓；`/ai/chat/history` / `/seckill/spu/list` / SPA `/` 均 200 ✓；nginx **无 `[error]/[emerg]/[crit]`** ✓；镜像内 conf md5 = 修复版 `9ce6f00d…` ✓；终态 **21 容器全 Up、0 异常** ✓。

**衍生待办**：**#61 外部端到端探活** —— 本次最值得记的教训是"**21 容器全 Up、网关 health 200，业务却挂了 24.5 小时**"，内部健康检查天然抓不到"路由层地址漂移"。

**附带发现（已处理）**：前端 nginx conf 与仓库副本**双向漂移**且 **conf 未入版本控制**（`.gitignore` 有 `deploy/`）→ 已将修复版 `git add -f deploy/docker/frontend/nginx.conf` 纳入跟踪；漂移的两块（SSE 180s 超时 / `/seckill/:id` 的 SPA 保护，后者**生产确实坏**：`GET /seckill/123` 原返回 401 JSON 而非页面）→ **用户决策"两块都合并"并已上线**（`GET /seckill/123` 现为 `text/html`；`/seckill/spu/list`、`/seckill/sku/list/{id}`、`POST /seckill/{code}` 仍走网关 JSON，真 API 未被吃掉；`nginx -T` 确认 SSE location 内 180s），二次 `docker commit` + 回滚点 `dynamic-only-20260910`。
**⚠️ 遗留动作（需 ecs-user）**：服务器源文件 `/data/csmall/frontend/nginx.conf` 仍是旧版（md5 `8bdd4cd2…`）→ 需把**合并版**（md5 `1382897db1b09d05bfe1aace2058fe8e`）覆盖上去，否则将来 `docker compose build frontend` 会**覆盖回退**本次修复。

**镜像基线（回滚用）**：`pre-dynamic-20260910` = `1ed14bbc615b`（原始静态版）→ `dynamic-only-20260910` = `917eead35007`（仅动态解析）→ `latest` = `f8ea5dd5aab5`（**当前：动态解析 + 两块合并**）。

---

## 十六、#32-P0 AI 导购 Agent（Function Calling）阶段完成并部署（2026-09-10）

> **说明**：本条归档 **P0 阶段**（同步 Agent）；**P1（流式 Agent + 两个新工具 + Redis 动作审计）与生产加固已于同日完成并部署、2026-09-11 复核通过 → 见 §十七**。至此 **#32 全部收口**，条目已从 [[TODO文件]] 正文整段移入本文件 §二十「📦 已完成条目归档区」。
> **设计与原理**：[[TODO第三批实现与原理-1]] §一/§二/§三（含 **§3.6 实际实现**）；**清单与偏差**：[[AI导购Agent升级方案]] §八

**交付（提交 `09d22b1` 代码 + `0fe1a1e` 部署准备）**

1. **`chatWithTools`**：工具轮请求体 = `buildBody(AiTask.AGENT)` + `tools` + `tool_choice:auto`；**关思考**（开思考必须回传 `reasoning_content` 否则 400）、**绝不带 `response_format`**（实测与 tools 互斥）
2. **工具层**：`AiToolCall` / `AiToolRound`（含**可回灌的 assistant 消息**）、`AiTool` / `AiToolResult`（`observation` 给模型 + `hits` 给前端）、`ToolRegistry`（自动收集 + **同名工具启动期报错**）、`SearchProductsTool`（复用 `RagServiceImpl`；**参数一律边界收敛**；无命中时放宽兜底并在 observation 里如实标注）
3. **Agent 分支**：`sendWithAgent`（轮数用尽**摘掉工具强制收口**）+ `sendWithPipeline`（原流程变兜底）；**任何异常降级回旧流水线**
4. **可运维化**：开关走 `.env` 的 `AI_AGENT_ENABLED`（compose 注入）→ **启用/回滚只改 `.env` + recreate，不重编译**；启动日志打印开关状态
5. **测试**：`ChatServiceImplAgentTest` 用**假 LLM 脚本化多轮**锁死 6 条路径（离线、零成本、可重复）；mall-ai 模块 **26 项全绿**

**生产部署与验证（两阶段，2026-09-10 晚）**

| 阶段 | 结果 |
|---|---|
| **A 零回归**（开关=false，只换 jar）| jar md5 落位一致；启动日志 `agent-enabled=false`；`/ai/search`、`/ai/ask` 正常；`content 为空`=0、真实 4xx=0 |
| **B 开启 Agent**（`.env`→true + recreate）| 问"我想买 5000 以内的手机" → `工具 search_products … 命中 4 条` → `Agent 第 1 轮：调用工具 1 次` → **`Agent 第 2 轮收敛`**（413 字、带表格推荐）|
| **兜底复验**（独立提问）| 问"1000 以内的单反相机" → `已放宽=true` → 回答**如实说明库里无此类目、不编造**，并给替代建议 |

**两个可写进面试的真实细节**：① 模型**主动剔除误召回**（ES 把"联想天逸510S 台式机"召回到"手机"结果，模型自己注明"它是台式机，已帮你排除"）—— 工具只给候选、判断交给模型；② 兜底放宽后仍没有该品类时，模型**没有硬推荐**，而是如实说明 —— 上下文里"已放宽"的标注起了作用。

**⚠️ 生产当前开关为 `true`**（`AI_AGENT_ENABLED=true`）；**回滚**：改 `.env` 为 `false` + `docker compose up -d mall-ai`（~45s，**无需换 jar**）；**成本**：Agent 单次约为旧路径 **2~2.5 倍**（工具轮 + 收敛轮），`ai:daily_cost` 正常累加。

**归档时的镜像/备份基线**：`csmall-mall-ai:before-step2` = `734aaf72a80b`（Step 1 版）→ `latest` = `343170e667a0`（P0 版）；jar/compose 备份在 `/data/csmall/jars/backup-20260910-step2/`。

**同时记录（非本次引入，避免后续误判）**：mall-ai 启动日志有 1 条 Dubbo ERROR（`Failed register interface application mapping …`，error code 5-10）—— **未改动**的 `csmall-product` 里有 7 次、`csmall-order` 1 次（09-09 启动即有），且服务发现与 TCP 连通均正常 → **项目既有的启动噪声，无功能影响**。

---

## 十七、#32-P1 AI 导购 Agent 完整版（流式 Agent + 两工具 + Redis 审计）+ 生产加固（2026-09-10 执行，2026-09-11 复核归档）

> **说明**：本条与 §十六 合起来 = **#32 全部完成**。P0 是"最小可行的 Function Calling"，P1 把 Agent 做完整（**流式**、多工具、可观测），并修掉生产验证挖出的 3 个真问题。
> **设计与原理**：[[TODO第三批实现与原理-1]] §3.7（P1 实际实现）+ §五（13 格实测实验，含 K/L/M）；**逐类实现说明**：[[AI导购Agent实现详解]]；**清单与偏差**：[[AI导购Agent升级方案]] §八

**交付（提交 `10a4ab0` P1 + `2fd305d` 加固）**

1. **流式 Agent**：`AiClient.streamChatWithTools` —— 抽出 **SSE 公共管道** `openSseStream`（取流/断连/记账/容错只写一处），正文实时回调、**`tool_calls` 按 index 拼接连缀 `arguments`**（实测 K：arguments 被切成 19 段）；`sendStreamWithAgent` 让**事件顺序与旧流水线完全一致**（thinking → products → sessionId → chunk* → done）→ **前端零改动**；`products` 之前**先缓冲正文**（实测 K：工具轮会先吐 preamble）
2. **两个新工具（都走真实 Dubbo，非 ES）**：`compare_products`（`IForFrontSpuService`，SPU 映射成 ES 文档形状 → 对比也能出前端商品卡片；**刻意不复用** `ProductCompareServiceImpl` —— 它内部还会再调一次 LLM）、`get_stock`（`IForFrontSkuService`，SKU 级 + 总库存）
3. **Redis 动作审计**：`AgentActionAuditor` → `ai:agent:action:{sessionId}`，`LPUSH {ts,round,tool,args,hits,costMs,ok}` + `LTRIM 0 49` + `EXPIRE 7d`；**写失败只记 WARN**（mall-ai 无数据库栈）
4. **每轮 `thinking` 进度事件**（前端已有卡片，无需改前端）；**分层降级**：未写出内容 → 降级旧流水线；已写出内容 → **如实报错、绝不降级**（防两段回答拼接）
5. **生产加固（部署后验证挖出的 3 个真问题，全部修复并复验）**：
   ① **商品类问题首轮 `tool_choice=required`**（实验 M 证明官方支持）—— 修"模型凭历史作答 → 没有工具结果 → 前端商品卡片为空"
   ② system 提示词加两条规则（即使与历史相似也要重新核对 / 没有工具数据不要给具体商品与价格）
   ③ **`get_stock` 回传 `spuName`**（先 `getSpuById` 确认）—— 修"模型先猜 spuId 再按提问里的商品名作答"的张冠李戴风险
6. **测试**：**26 → 50 → 56 项全绿**（Agent 循环 19 / 请求体 9 / 对比工具 8 / 库存工具 8 / 检索工具 7 / 注册表 2 / 配置绑定 3）

**生产验证（2026-09-10 当晚 + 2026-09-11 复核）**

| 项 | 证据 |
|---|---|
| 流式 Agent | **241~249 个 chunk** 逐字流式；`event: products` **早于**第一个 `event: chunk`（2026-09-11 复核：211 事件、products 行 10 < chunk 行 22）✓ |
| 新工具 | `compare_products：请求 2 个，取到 2 个（缺失 0）` ✓；`get_stock：spuId=3（小米 14 Pro）→ 2 个 SKU，总库存 100` ✓ |
| 加固① | 日志 `Agent 首轮强制调用工具（命中商品意图关键词「买」）`；`products` **4 件非空**（此前为空数组）✓ |
| 加固③ | 模型编造的 `spuId=1001` 被工具**优雅拒绝**（`查询SPU详情失败…数据不存在` → 审计 `ok=false`，循环继续）✓ |
| 稳定性 | **客户端连续 18 次请求（含"流式传输中并发 6 次"）全部 200** ✓ |
| 审计/预算 | `LRANGE ai:agent:action:{sid} 0 -1` 有完整记录、TTL 7 天量级；`ai:daily_cost` 正常累加 ✓ |
| 2026-09-11 复核 | 21 容器全 Up；mall-ai `restarts=0`；jar md5 `e7bbe489…`；`AI_AGENT_ENABLED=true`；3 工具已注册；同步 200 + 流式正常 ✓ |

**⚠️ 生产开关当前为 `true`**；**回滚三级**：① 关开关（改 `.env` + `docker compose up -d mall-ai`，~45s）② 换回 jar + 镜像（`before-step4`）③ 连 compose 一起回。
**已知边界**：Agent 路径不发 `categories` 事件；`get_stock` 只含常规库存（不含秒杀）；成本约为旧路径 2~3 倍（工具轮 + 收敛轮）。
**衍生/未了**：**#61 外部端到端探活**、**#62 mall-ai 偶发 500（`AccessDeniedException`，已定性为 ERROR 派发次生现象，非 Agent 问题）** 仍在 [[TODO文件]] 跟踪。

---

## 十八、#63 ES 索引 mapping 缺陷修复 + #59 余额问题解决 + 向量链路加固（2026-09-11 完成并复核）

> **来源**：为评估"是否把商品从 20 扩到 60"而对 ES 链路做**只读取证**时**顺带发现**的存量缺陷。
> **完整叙述**：[[TODO第三批实现与原理-1]] **§九**（#63 复核与修复）/ **§十**（#59 → #31 前置解除）/ **§十一·§十二**（Embedding 可替换性评估与实施）；**实施方案**：[[商品与秒杀扩容方案]] §一。
> **本条由 [[TODO文件]] 正文迁出**（2026-09-11，原 `### 63.` 段与 `#59` 表行）。

### 18.1 #63 —— ES `cool_shark_mall_ai` 的 mapping 与代码期望完全不符 ✅ 已修复并复核

**缺陷**：线上索引是 **dynamic mapping**（无 `semanticVector`、无 IK 分词、`brandName` 是 `text`、`suggestField` 是 `text`、`listPrice` 是 `float`），而 `EsIndexInitializer` 期望的是 `dynamic:false` + `ik_max_word` + `keyword` + `dense_vector(1024)` + `completion`。
🔴 **该初始化器只在索引"不存在"时才创建 → 这份漂移永不自愈**（索引 `creation_date` ≈ 2026-07-31，早于初始化类上线）。

**实测后果**：① **品牌过滤恒失效**（`term brandName` = 0 命中）② **`/ai/search/suggest` 补全恒返回空** ③ **中文被切成单字**（`name^5/title^4/…` 的权重设计在单字粒度上失效，能匹配但精度差）④ **`semanticVector` 字段不存在 → #31 无处写入向量**。

**修复动作**（用户执行，2026-09-11 07:37）：备份现场（`_mapping`/`_settings`/`_count` 落盘）→ `docker compose stop mall-ai` → `curl -X DELETE …/cool_shark_mall_ai` → `up -d`（`EsIndexInitializer` 重建索引 + `SyncOnStartupRunner` 全量同步 19 条）。

**验收（AI 用 `ai-deepseek` 账号独立复验，不是只看用户回贴）**

| 项 | 修前 | 修后 |
|---|---|---|
| `dynamic` | 未设置（= `true`） | **`false`** ✅ |
| `name/title/description/semanticText` | 4 个单字 `<IDEOGRAPHIC>` | **`小米`/`手机`（`CN_WORD`）** ✅ |
| `brandName/categoryName/pictures/tags` | `text` | **`keyword`** ✅ |
| `listPrice` / `sales` | `float` / `long` | **`double` / `integer`** ✅ |
| `semanticVector` | **不存在** | **`dense_vector dims=1024 similarity=cosine index=true`** ✅ |
| `suggestField` | `text` | **`completion` + `analyzer=ik_max_word`** ✅ |
| `term brandName="小米"` | **0** | **4** ✅ |
| 补全 | **恒空** | **`prefix=小米` → 返回「小米 14」** ✅ |
| 文档数 / 带向量文档数 | 19 / 0 | 19 / 0（`embedding-enabled=false`，符合预期）✅ |

**召回回归（担心的"keyword 化导致召回塌"未发生）**：`小米手机` 8 · `小米` 4 · `手机` 7 · `华为` 3 · `笔记本` 5 · `旗舰` 3；价格 ≤5000 + 升序排序正常。
**原因**：`semanticText` 里本身就写着「品牌：小米 | 分类：手机 | 标签：…」→ 品牌/分类召回由它（权重 3）兜住。

**⚠️ 方法论教训（AI 自己犯的）**：验收命令 `grep -c ik_max_word` 期望 **4** 是错的 —— ① `grep -c` 数的是**行数**，而 mapping 是**单行 JSON**（永远只返回 0/1）；② 实际出现次数是 **5**（4 个 text 字段 + `suggestField`）。正确写法 `grep -o … | wc -l`。
→ **"验收命令本身也要被验收"**；所幸关键那条 `_analyze` 是确定性的，用户看到 "1 ≠ 4" 时没有误判为失败。

**🔴 风险已解除**：`semanticVector` 字段就位 → **#31 之后只需改配置 + 同步，零停机、不必再删索引**。

### 18.2 #59 —— 硅基流动余额不足（embedding 402）✅ 已充值解决

| 项 | 内容 |
|---|---|
| 原问题（2026-09-10） | 新 key 认证有效（`GET /v1/models` = 200），但 `POST /v1/embeddings` 返 **402** `{"code":30001,"message":"Sorry, your account balance is insufficient"}` → **`BAAI/bge-m3` 调用被拒**；生产 `embedding-enabled: false` 故**线上零影响**，但**阻断 #31** |
| 处置 | 用户 **2026-09-11 充值 10 元** |
| 验证（AI 用生产 key 实测） | `POST https://api.siliconflow.cn/v1/embeddings`（`BAAI/bge-m3`）→ **HTTP 200**、**`dims = 1024`**、`usage{prompt_tokens:7}`、`model=BAAI/bge-m3` |
| ⭐ 额外收获 | **1024 维与 mapping 的 `dense_vector dims=1024`（=`cooxiao.ai.embedding-dimensions: 1024`）完全吻合** → #63 重建出的向量字段与后端要写的向量**同维、不会错配** |
| 结论 | **#59 关闭；#31 前置正式解除**（顺序决策见 [[商品与秒杀扩容方案]] §十：**先 #63 → 再 #31**） |

### 18.3 衍生加固（无 TODO 编号，同日完成）：向量链路降级 + Embedding 可替换性 P0~P3

**为什么顺手做**：#63 的复核暴露出**同一类根问题** —— "**代码/配置与真实环境的契约不一致，却没有任何一方主动校验**"（#59 的 402、#63 的 mapping 漂移，都是这个模式）。

| 块 | 内容 | 落点 |
|---|---|---|
| **向量链路降级** | `ask()` 三种失败（embedding 失败 / 向量检索失败 / 结果为空）**全部回落全文检索**；`syncAll`/`syncSpu` 向量化失败**降级为"仅全文索引"**（原实现下这批商品**一条都进不了 ES**，`synced=0`）并在汇总**显式暴露降级条数**；修正 `vectorSearch()` 那条"日志说降级、实际只 return 空"的日志 | 补册 **§10.4** |
| **Embedding 可替换性 P0~P3** | **P1** 抽 `EmbeddingClient` 接口（**依赖倒置**）+ 实现 `git mv` 改名 `OpenAiCompatEmbeddingClient` + 两处注入改接口；**P0** 新增 `EmbeddingSelfCheck` 启动自检（**维度不一致 → 启动失败**并给出"删索引重建"步骤；**瞬态失败只 WARN 不阻断**）+ `EsIndexInitializer` 用 `@DependsOn` 保证"自检先跑"；**P2** 维度变更步骤写进 `AiProperties`/yml 注释；**P3** `encoding_format` 可配 | 补册 **§十一 / §十二** |
| **3 处真实边界修复** | ① `syncAll` 的 `vectors.get(i)` **越界**（外部接口少返向量 → 整批商品进不了 ES）② `vectorSearchWithFallback` 的 **null/空向量** ③ **`getAllSpus()` 两处 NPE**（`getList()` 可空 + **`getTotalPage()` 是可空 `Integer`，原直接比较会拆箱 NPE**）→ 改为"**空页即到底**"主终止 + `MAX_PAGES` 防御上限 + 触顶告警 | 补册 **§12.6** |
| **测试** | **56 → 82 项全绿**（新增 3 个测试类 **26 条**：`EmbeddingSelfCheckTest` 6 · `RagServiceImplVectorFallbackTest` 6 · `VectorSyncServiceImplDegradeTest` 14）；手法 = **最小侵入的测试接缝**（只放开可见性 / 抽方法，不改行为），Dubbo 用 `java.lang.reflect.Proxy` 伪造，**不用 Mockito** | 补册 **§12.4** |

> **⚠️ 部署状态**：本轮代码改动**尚未构建镜像、未部署**。`embedding-enabled=false` 时"降级 + 自检"均为**死代码**，故可**先单独部署验证零回归**，再翻 #31 开关。

### 18.4 关联

- **原理 / 过程**：[[TODO第三批实现与原理-1]] **§九**（#63 复核与修复，含 §9.6 验收、§9.7 召回回归）/ **§十**（#59 → #31 前置解除）/ **§十一·§十二**（Embedding 可替换性评估与实施）
- **实施方案**：[[商品与秒杀扩容方案]]（§一 #63 前置修复 · §十 #31 顺序决策 · §1.7 修复执行结果）
- **仍在跟踪（[[TODO文件]]）**：**#31**（可实施、未实施）· **#64**（秒杀预热 `spu_id` 语义冲突，本次新登记）· **#57**（补两处 schema 漂移发现）

---

## 十九、#48 第一层「模拟数据生成」+ 数据标识 + 可观测展示工具链（2026-09-11 完成并复核）

> **归档范围说明**：**#48 是一个两层方案**，本次归档的是**已完成的第一层 + 展示工具链**；
> **第二层「AI 并发压测」（mock LLM / Agent 双链路）完全未开始**，连同"录像、正式造数"等**剩余 6 项已另立 [[TODO文件]]#67**。
> **原理与 8 处踩坑的完整过程** → [[TODO第三批实现与原理-1]] **§十三**；**方案正文与实测记录** → [[Python模拟数据与数据隔离方案]]（§〇.1 复核 / §2.2.9 标识设计 / §5.1 压测实测 / §6 可观测 SOP / §6.4.1 录像 Runbook）。

### 19.1 交付物清单（代码 · 文档 · 生产）

| 类 | 交付物 | 说明 |
|---|---|---|
| **迁移** | `ums V3` / `oms V7` / `seckill V6` / `resource V2` 四个 Flyway 文件 | **9 张表**加专用列 `data_source`；已重建镜像并生效（四个库 `flyway_schema_history` 均 `success=1`） |
| **脚本** | `deploy/scripts/sim/simulate_data.py`（54.5KB） | 造数主脚本：80/15/5 漏斗 + **预检三道防线** + **按登记表回填** + 逆序清理（分批/幂等/dry-run） |
| | `deploy/scripts/sim/load_test.py`（21KB） | **两档**压测：`--mode browse`（pass 曲线）/ `--mode limit`（block 曲线）+ `--check` 自检 |
| | `deploy/scripts/sim/init_sim_db.sql` | 影子登记库 `cs_mall_sim` 的 DDL（`sim_batch` / `sim_entity` / `sim_baseline`） |
| | `deploy/scripts/sim/README.md` | 脚本说明书（含全部实测与验收记录） |
| **文档** | [[演示录像操作手册]]（162 行） | 独立录像操作手册（在哪执行 / 前置 / 两档步骤 / 预期数字 / 分镜台词 / 常见疑问）——**归属 #67**（录像尚未执行） |
| **生产** | 老机 `cs_mall_sim` 库 + 4 服务镜像重建 | 影子库三表就位；4 个模块 jar 重新打包并重建镜像使迁移生效 |
| **顺带修复** | **A 步 #66**：8 个服务补 Sentinel dashboard 地址 | compose 8 处新增 → **11/11 服务上报** → **面板已见 8 个服务**（验收闭环） |

### 19.2 核心机制一：**专用列 `data_source`**（用户拍板，替代"借用自由字段"）

| 项 | 内容 |
|---|---|
| **为什么不用借用** | 初版想复用 `oms_order.tag` / `oms_order_item.data`（零 DDL）→ 但那是**语义借用**（`tag` 本是展示标签）+ 同一行挂两种标识 → **用户 2026-09-11 拍板改专用列**，理由是**避免歧义** |
| **值域** | `NULL` = 常规/真实；`SIM` = 模拟造数（可扩展 `LOADTEST` / `REPLAY` = 数据血缘） |
| **谁写值** | **服务端零改动** —— 造完数由脚本**按影子登记表回填** |

#### 🔴 两条"会让服务起不来"的纪律（都实测踩到并固化）

1. **只加迁移文件、禁止手工 ALTER**：手工加列后再执行迁移 → `Duplicate column name` → Flyway 失败 → **应用启动失败**
2. **`docker restart` 对新迁移是空操作**：迁移文件在 **jar 里**、jar 被 `COPY` 烘进镜像 → 重启只是"用旧镜像跑旧 jar" → Flyway 报 `Schema is up to date` → **必须重新打包 + 重建镜像**（本次实测踩到，白跑一轮）
   - 另：**不要"顺手统一"已被应用迁移文件的行尾** —— Flyway 校验文件**字节 checksum**，改了 → `Migration checksum mismatch` → 同样起不来

### 19.3 核心机制二：**回填矩阵 + 校验（含补掉一个校验盲点）**

| 回填口径 | 表 | 键 |
|---|---|---|
| ① 脚本直接 INSERT | `ums_user` / `oms_cart` / `oms_order` / `oms_order_item` | 登记主键 `id` |
| ② **服务端在链路中写的**（脚本没有插入点） | `ums_login_log` / `oms_payment_record` / `success` / `seckill_message_retry` / `res_upload_record` | **`user_id`**（实测这 5 张全部有 `user_id`） |
| ③ 子表兜底 | `oms_order_item`（它没有 `user_id`） | 父订单 `order_id` |

> 🔴 **G7 的教训（最值钱的一条）**：`verify_backfill()` 原来**只校验"登记表里出现过的表"** → 而 `oms_order_item` 因**事务快照**问题漏登记 → **不在校验范围** → **漏标却打印"✅ 回填校验通过"**。
> **"只校验你登记过的东西 = 校验盲点"** —— 已补"子表按父订单"校验堵住。

### 19.4 实测结果（全部有证据）

| 项 | 实测 |
|---|---|
| **校准（第 5 次真跑）** | `浏览 38 / 加购 8 / 下单 4（已支付 4，支付失败 0）/ 失败 0`；🏷️ **合计标记 60 行** + `✅ 回填校验通过`；9 张表 SIM ⇄ 登记**逐一对齐**；**6 项漏标检查全 0** |
| **🔑 关键机制验证** | **`oms_payment_record`（服务端写的表）被按 `user_id` 兜底回填 4/4**、`ums_login_log` 20/20 ⇒ "服务端零改动"这条路**彻底走通** |
| **清理演练（C-8）** | 批次 1925 精确删 **56 行**（含**未登记**的 3 条订单项，靠 `order_child` 模式照样删掉）；`--clean --apply` 幂等收尾空批次 |
| **浏览档压测** | 20/50/100 并发 → **69.0 / 97.2 / 99.6 RPS**，p50 278/488/796ms，**p99 539/976/2466ms**，**失败 0** ⇒ **吞吐上限 ≈100 req/s、拐点落在 50~100 并发**（判据 = RPS 平台 + p99 陡增） |
| **限流档压测** | `adminLogin`（QPS 10）20 并发 → RPS **165.5**、**被限流 1259 次（93.26%）**；`支付订单` 实测**只有 0.66%**（`@Idempotent` 切面在 Sentinel 之外先拦） |
| **A 步 #66 验收闭环** | compose 8 处新增 → 传输 md5 一致 → **11/11 服务有 dashboard 变量** → 面板轮询 **10 个客户端端点**、最近 10 分钟拉取失败 **0** → **用户目视确认面板出现 8 个服务** |

### 19.5 校准抓出的 8 处踩坑 + 2 个衍生缺陷

| 编号 | 内容 | 性质 |
|---|---|---|
| **G1** | 用户名不许有下划线（服务端正则只允许字母数字） | 我的设计错 |
| **G2** | 联系人姓名只能 2~4 字符（提前读码发现） | 我的设计错 |
| **G3** | 🔴 **我自己手抄正则抄错了**（phone 多抄一组 `[0-9]`）→ 已建"机器比对"防线 | **我的错误** |
| **G4** | 假号段 `1390000` 已被既有 bench 用户占满 → 409 | 我的核对不完整 |
| **G5** | 🔴 **浏览接口也需要登录**（**推翻了方案 §6.2 旧结论**） | **文档结论被证伪** |
| **G6** | 支付渠道必须 `2=支付宝`（0=银联会 500） | 业务约束 |
| **G7** | 🔴 订单项**漏登记** + **校验盲点**（漏标却报通过） | **我的校验漏洞** |
| **G8** | 🔴 **普通订单库存扣减 MQ 链路整体失效** → **衍生 #65** | **既有缺陷** |
| — | `mall-front` 等 8 服务不上面板 → **衍生 #66**（本轮已修复并验收） | 既有配置缺口 |
| **G10** | 未能通过 OAP API 程序化取指标（UI 有数、API 取 0）—— **未解决、如实记录** | 我的工具能力边界 |

> 方法论收获：**"注册到面板" ≠ "有数据"**（曲线只反映流量）· **判据陷阱：`409` 不是 block，只有 `429` 才是** · **别一次 recreate 一大片服务**（8 个同建 221~319s vs 4 个同建 107~176s = "启动踩踏"）· **判据要自适应**（网关主类叫 `MallGatewayWebApi` 而非 `Mall*Application`，我的 grep 误报它没起来）

### 19.6 关联与剩余

- **原理 / 过程**：[[TODO第三批实现与原理-1]] **§十三**（5 次真跑 + 8 处发现 + 压测标定 + 面试话术）
- **方案正文**：[[Python模拟数据与数据隔离方案]]（§〇.1 复核 9 条 · §2.2.9 标识设计 · §5.1 压测实测 · §6 可观测 SOP · **§6.4.1 录像 Runbook**）
- **脚本说明**：`deploy/scripts/sim/README.md`
- **操作手册**：[[演示录像操作手册]]（**归属 #67**）
- **⏳ 剩余（[[TODO文件]]#67）**：① **录像** ② **正式造数**（每天 1000 行为）③ **第二层 AI 并发压测**（mock LLM + Nacos 规则放开/恢复 + Agent 双链路对比）④ `--with-seckill` ⑤ **Redis 精确清理**（当前只打印告警）⑥ `sim_baseline` 基线比对
- **本次新登记**：[[TODO文件]] **#65**（库存扣减 MQ 链路失效）· **#66**（Sentinel 面板上报缺口，**已修复**）

## 二十、📦 已完成条目归档区（2026-09-11 由 [[TODO文件]] 文末整段迁入）

> **来源**：原为 [[TODO文件]] 文末的「📦 已完成条目归档区」（2026-09-10 由 TODO 正文移出）。该区自身注明"**只是临时存放，已完成明细应迁入 [[TODO已完成]]；迁入后本区可整段删除**" → **2026-09-11 按此约定整段迁入本文件**（TODO 文末该区已删除）。
> **✅ 内容零删改**：全程只搬位置 —— 原文的踩坑 / 证据 / 面试价值一字未改（仅本节标题与引言改为"迁入版"，去掉指向 TODO 文末的自指描述）。
> **📋 本次搬移清单（12 处）**：
> 1. §44 硅基流动 Embedding Key 吊销 + 轮换（✅ 已完成 2026-09-10）
> 2. §42 跨机"真集群"演示方案（已由 #4 / #9 / #14-P2 实施完成）
> 3. §9 低优先级「Redis 主从 + 哨兵高可用实验」（✅ 已完成 2026-09-09）
> 4. §6 低优先级「JMeter 联合压测」（2026-09-07 决定不录制）
> 5. 「🚀 第三批执行清单：跨机集群主线」（阶段 0~5 全部完成）
> 6. 顶部索引区「📦 B. 归档 16 个」+「✅ C. 状态头修正 7 个」
> 7. 「🟢 第三批」表中 5 行已完成项（~~#4~~ / ~~#14-P2~~ / ~~#9~~ / ~~#46~~ / ~~#47~~）
> 8. 「🧭 推荐执行路线」表中 5 行已完成项（~~#46~~ / ~~#47~~ / ~~#4~~ / ~~#9~~ / ~~#14-P2~~）
> 9~12. 散落在待办条目旁的 4 条完成标记（原位于 R6 段内 / §7 段内 / §26 前 / §35 前）

---

### 一、已完成条目（整段搬移）

### 32. 【AI】AI 导购升级 Agent（Function Calling + ReAct）✅ **全部完成并部署（P0 + P1 + 生产加固）**

> **归档说明（2026-09-11）**：本条目已**全部完成**，按文档纪律由正文**整段移入**此处。归档明细：[[TODO已完成]] **§十六**（P0）/ **§十七**（P1 + 生产加固）；实现说明书：[[AI导购Agent实现详解]]；原方案：[[AI导购Agent升级方案]]。

> **2026-09-02 新增（源自 09 Q0/Q12 讨论）**：当前 AI 导购 = "LLM 解析意图 + 代码写死执行"（无工具调用/无循环/无动作决策权）；升级 Agent = 技术演示增强 + 面试素材（生产 0 调用，业务收益为零）。
>
> ✅ **2026-09-10 决策（用户拍板）：做** —— 按 **P0（最小 Function Calling）→ P1（完整单 Agent）** 分期推进，P2 框架化可选。方案已敲定，**含 6 条实施前代码校正**：① `mall-ai` **无任何数据库栈** → 原 P1「审计落 DB + Flyway」不可行，改为只落 Redis；② 消息类型须加宽为 `Map<String,Object>`；③ Agent 循环只放**非流式**路径（最后一轮复用现有 SSE）；④ `RagServiceImpl` 待复用的是**包私有**方法；⑤ 预算/闸门/限流**全部复用**既有设施；⑥ 与 **#58** 强耦合。
> 🔗 **建议与 [[#58]] 同期落地**：先做 thinking 开关改造，给 Agent 一个稳定的 `tool_calls` 模型契约（两个改动都只动 `mall-ai`，同一维护窗口更省事）。

**关键结论**：
- **值得做但定位"演示增强"**：面试价值高（Function Calling + ReAct 实战）、学习价值高、业务价值低
- DeepSeek 支持 tools 参数（function calling），现有 intentSearch 可直接声明为第一个工具
- **边界是核心**：只读工具自动执行 / 写操作用户确认（human-in-the-loop）/ 参数 schema 约束 / 轮数上限 3 / 预算复用（2 元/天）/ 动作审计 / 失败降级回 RAG
- **工作量**：P0 最小 Function Calling ≈ 0.5~1 天（DeepSeekAiClient tools 支持 + search_products 工具 + ChatServiceImpl 循环）；P1 完整单 Agent ≈ 2~3 天（compare/get_stock 工具 + 3 轮 ReAct + 审计 + 降级）；P2 框架化可选（Spring AI 重构）

📄 **完整方案（现状/差距/场景工具集/9 条边界/分阶段到文件级/成本风险/面试话术/执行清单）见 [[AI导购Agent升级方案]]**；**技术选型过程（为何手写而非 Spring AI）+ 13 格实测实验 + 模拟数据设计见 [[TODO第三批实现与原理-1]]**

**🚀 进度（2026-09-10）**：

| 阶段 | 状态 | 证据 |
|---|---|---|
| **#58**（前置：模型契约 + 配置可配化）| ✅ **已完成并部署生产** | 见 [[TODO已完成]] §十四 |
| **P0 代码**（Function Calling）| ✅ **已完成**（2026-09-10，提交 `09d22b1`）| `chatWithTools` + `AiTool/AiToolResult/ToolRegistry/SearchProductsTool` + `AiToolCall/AiToolRound` + `ChatServiceImpl` Agent 分支（开关默认关）；**离线测试 26 项全绿**（工具轮请求体规则 8 / 工具注册 2 / 检索工具边界 7 / 配置绑定 3 / **Agent 循环 6**：一轮收敛、轮数用尽强制收口、正文空兜底、未知工具、异常降级、开关关闭不走 Agent）|
| **P0 部署** | ✅ **已完成并验证（2026-09-10）** | 两阶段执行：**A 阶段**（开关=false）零回归通过（`/ai/search` 返回商品、`/ai/ask` 返回完整推荐、`content 为空`=0、真实 4xx=0、开关日志 =false）→ **B 阶段**开启后问"我想买 5000 以内的手机"：`工具 search_products … 命中 4 条` → `Agent 第 1 轮：调用工具 1 次` → **`Agent 第 2 轮收敛`**（413 字、带表格推荐，并**主动排除误召回的台式机**）；**兜底路径复验**（问"1000 以内的单反相机" → `已放宽=true`，回答**诚实说明库里无此类目、不编造**）。预算 `ai:daily_cost:2026-09-10` 正常累加、21 容器全 Up。**⚠️ 生产当前开关为 `true`**（回滚：改 `.env` 为 false + `docker compose up -d mall-ai`，~45s）|
| **P1 完整单 Agent** | ✅ **已部署并复验（2026-09-10）** | 新增 **流式 Agent**（`streamChatWithTools`：SSE 公共管道 + `tool_calls` 按 index 拼接连缀；事件顺序与旧流水线一致、products 前先缓冲正文）+ **两个新工具**（`compare_products` / `get_stock`，**都走真实 Dubbo**）+ **Redis 动作审计**（`ai:agent:action:{sid}`，LTRIM 50 / TTL 7d）+ 每轮 `thinking` 进度事件（前端零改动）+ 分层降级（未写出→降级流水线；已写出→如实报错不降级）。**部署后验证挖出并修掉 3 个真问题**：①商品类问题**首轮 `tool_choice=required`**（防"凭历史作答→商品卡片为空"，依据实验 M）②system 提示词防凭历史作答 ③`get_stock` 回传 **`spuName`**（防张冠李戴）。**测试 26 → 50 → 56 项全绿**；补 **3 格实验 K/L/M**。复验：products 4 件非空、`首轮强制调用工具` 日志命中、`get_stock` 带商品名、模型编造的 spuId 被优雅拒绝、**客户端连续 18 次全 200**。部署指令：`work/部署指令-step3/step4`；**实现说明书见 [[AI导购Agent实现详解]]** |

> ⚠️ **实施与原计划的偏差（如实记录）**：方案 S5 原写"最后一轮复用现有 `streamDeepSeek` 输出"，实际 P0 只做**非流式** Agent（`/ai/chat/send`），SSE（`/ai/chat/stream`）**仍走旧流水线** —— 因为"流式 + 工具轮"要处理"工具调用发生在流里"的复杂情形，放到 P1 更稳。


### 44. 【安全】吊销已泄露的硅基流动 Embedding Key ✅ **已完成（2026-09-10 吊销 + 轮换）**

> **2026-09-08 发现**：`mall-ai-webapi/src/main/resources/application-test.yml` 曾硬编码硅基流动 embedding key（`sk-***（已吊销）`），该文件已被 commit **719ff6f** 提交并 **push 到公开 GitHub 仓库**——**key 已公开泄露**（即使本地已改占位符，历史提交里仍可查到）。

**✅ 处理结果（2026-09-10，用户执行吊销 + AI 完成全量轮换评估与配置）**：

| # | 动作 | 状态 |
|---|---|---|
| 1 | 硅基流动控制台**吊销旧 key** | ✅ 用户已执行（旧 key 从此失效，故 git 历史里那串**已无害**）|
| 2 | 生成新 key（`sk-****`，51 字符） | ✅ 用户已生成 |
| 3 | **全仓库评估**「换 key 要改哪些地方」（含 1 次循环检查） | ✅ 已完成，结论见下 |
| 4 | 本地 `.env` 换新值 | ✅ `deploy/docker/.env`（替换）＋ `mall-ai/.env`、`deploy/systemd/csmall.env`、`deploy/csmall.env`（**原本就缺这行，已补齐**）|
| 5 | 服务器 `/data/csmall/.env` 换新值 | ⏳ **待用户执行**（属主 `ecs-user`，AI 账号无写权限）|
| 6 | recreate `csmall-ai` | ⏳ **待用户执行**（env 是创建时快照，**必须 recreate 不能 restart**）|

**评估结论（改哪些 / 不改哪些）**：

| 位置 | 结论 |
|---|---|
| `deploy/docker/.env` | ✅ **要改**（compose 从这里取 `${EMBEDDING_API_KEY}` 注入 mall-ai）|
| `mall-ai/mall-ai-webapi/.env`、`deploy/systemd/csmall.env`、`deploy/csmall.env` | ✅ **补齐**（这三个文件原本**只有 `AI_API_KEY`、没有 `EMBEDDING_API_KEY`** —— 属发现并修掉的配置缺口）|
| `deploy/docker/.env.example` | ❌ **不改**（模板必须保持 `sk-placeholder`）|
| 3 个 `application*.yml` | ❌ **不改** —— 都是 `${EMBEDDING_API_KEY:sk-placeholder}` 占位符，**设计正确**（密钥不进代码）|
| **git 历史里的旧 key** | ❌ **无需重写历史** —— key 已吊销即无害；公开仓库已被克隆，改写历史收益为负 |
| 服务器 `/data/csmall/.env` | ⏳ 要改（AI 无写权限，命令见下方"给用户的执行块"）|

**⚠️ 重要澄清（避免误判影响面）**：生产 `application-prod.yml` 的 **`embedding-enabled: false`**，服务器 `.env` 与容器 env 实测 **`EMBEDDING_API_KEY=sk-placeholder`** → **生产没开向量检索，所以也没配真 embedding key**，因此**本次轮换对线上功能零影响**；但它是 **#31（生产启用向量检索）的前置** —— 不先配好新 key，将来一开启就会 401。

> 🔎 **别误读成"ES 瘫痪了"（2026-09-10 生产实证）**：**「向量语义检索」和「ES 全文检索」是两条独立的路**，代码里是**显式 if/else 分支**，不是"失败才降级"：
> ```java
> // RagServiceImpl.ask()
> if (aiProperties.isEmbeddingEnabled()) { hits = vectorSearch(embeddingClient.embed(question), topK); }  // 需 embedding key
> else                                   { hits = fullTextSearch(question, topK); }                      // ← 当前走这条，不需要 key
> // VectorSyncServiceImpl:148  float[] vector = isEmbeddingEnabled() ? embed(semanticText) : null;        // 关闭时不写向量字段
> ```
> | 能力 | 依赖 embedding key | 生产状态（实测）|
> |---|---|---|
> | **ES 全文检索**（multi_match）| ❌ 不依赖 | ✅ **一直正常**：索引 `cool_shark_mall_ai` **green / 19 条**；直连查 `"手机"` 命中 **9 条**（iPhone 15 / 小米14 Pro / Redmi K70 Pro）、`"笔记本电脑"` 命中 **7 条** |
> | **ES 向量语义检索**（向量相似度）| ✅ 依赖 | ⏸️ **关闭**（设计开关，见 #31）|
> | **AI 重排 / 意图提取**（LLM）| ❌ 不依赖（用 `AI_API_KEY`）| ✅ 正常 |
> **佐证"从未跑过向量"**：ES mapping 里**根本没有 `vector` 字段**、`{"exists":{"field":"vector"}}` = **0 条**；而 `{"exists":{"field":"semanticText"}}` = **19 条** → 说明**"拼语义文本"这步一直跑，"算向量"这步被开关跳过**（所以随时可无损开启）。

**🔁 循环检查（1 遍）额外发现**：
- ⚠️ **`.idea/workspace.xml:407` 硬编码了另一个旧 DeepSeek key（`sk-****…`）** —— 这是 **IDE Run Configuration 的经典坑**（IDE 环境变量优先级最高、会覆盖 `.env`，本项目 2026-09-08 就因此排查过 401）。**与 #44 无关但同类风险**，建议清理（见下方）。
- ✅ 本机**没有**持久化的 `EMBEDDING_API_KEY` / `EMBEDDING_API_KEY_LOCAL` 用户级/系统级环境变量（`application-test.yml` 的 fallback 会落到 `sk-placeholder`）。
- ✅ 前端仓库、`work/` 临时目录、其它 compose 文件均无 key 命中。

**✅ 执行结果与实测验证（2026-09-10，AI 完成）**：

| # | 验证项 | 结果 |
|---|---|---|
| 1 | 旧 key 是否真吊销 | ✅ **已吊销**：实测 `POST /v1/embeddings` 与 `GET /v1/models` 均返回 **HTTP 401 `{"code":30014,"message":"Token is invalid."}`**（旧 key `sk-****`，从 commit `719ff6f` 取出验证）|
| 2 | 新 key 认证是否有效 | ✅ **有效**：`GET /v1/models` 返回 **HTTP 200** + 模型列表 |
| 3 | 服务器 `.env` 是否已换 | ✅ 已换（`EMBEDDING_API_KEY=sk-****`），**属主/权限原样保留** `ecs-user:ecs-user 664`，备份 `.env.bak.20260910_150217`；原文件 950B → 987B（**+37B 与密钥长度差完全吻合**，证明只改了目标行）；`docker compose config` 解析通过 |
| 4 | 容器是否已生效 | ✅ `docker compose up -d --no-deps mall-ai` 重建成功；容器 env 实测已是新 key；**health 200 @ 50s**、Nacos 注册完成、老机仍 **21 容器**（只动了 mall-ai）|
| 5 | ⚠️ **新 key 能否真正调用 embedding** | 🔴 **不能 —— HTTP 402 `{"code":30001,"message":"Sorry, your account balance is insufficient"}`** |

> 🔴 **遗留问题（#44 → 转 #31 的前置条件）**：**新 key 认证通过，但硅基流动账户余额不足**，`BAAI/bge-m3` 调用被拒（402）。项目文档曾记"BGE-M3 免费（2000 万 tokens）"——实测该免费额度当前**不足以调用**（可能已耗尽或政策变化）。
> **影响**：因为生产 **`embedding-enabled: false`**，**线上功能零影响**；本地开发若开 `embedding-enabled: true` 会 402。
> **待决策**：① 硅基流动充值（小额即可）② 或改用其它 embedding 供应商 ③ 或维持 #31「不开启向量检索」的现状（那本条目即可视为**闭环**，key 只作为"备用配置已就位"）。
**给用户的执行块（服务器侧，AI 无写权限）**：

```bash
# 【老机 ecs-user】1) 改 .env（把 <新key> 替换为桌面 txt 里的值）
sed -i 's|^EMBEDDING_API_KEY=.*|EMBEDDING_API_KEY=<新key>|' /data/csmall/.env
grep -n 'EMBEDDING_API_KEY' /data/csmall/.env   # 核对（只显示行号+前缀即可）

# 【老机 ecs-user 或 AI 均可】2) recreate mall-ai（不是 restart！env 是创建时快照）
cd /data/csmall && docker compose up -d mall-ai

# 3) 验证：容器 env 已换新值
docker inspect csmall-ai --format '{{range .Config.Env}}{{println .}}{{end}}' | grep EMBEDDING_API_KEY
```

> **注**：当前 `embedding-enabled: false`，所以**功能上不验证也不会报错**；等 #31 决定开启时再验"向量化成功"即可。

**建议顺手做（IDE 旧 key）**：`D:\java\csmall\.idea\workspace.xml` 第 407 行 `<env name="AI_API_KEY" value="sk-****…" />` 是**已被替换的旧 DeepSeek key**，建议删除该 env 项（`.idea/` 已 gitignore，不入库；但会**静默覆盖**系统变量与 `.env`，是排查"配置不生效"的头号嫌疑）。

> **教训**：API key 绝不硬编码进 yml/代码；test 环境也要用占位符 + 环境变量注入。已改占位符见 commit c0d7209。**另外：`.env` 类文件要"整族同步"** —— 本次发现 3 个 env 文件缺 `EMBEDDING_API_KEY`，说明"只改一个 `.env`"的做法会留下静默缺口。


---

### 42. 【评估】跨机"真集群"演示方案（Redis HA + 秒杀双实例，2026-09-07 评估，优先级低仅评估）

> 🔴 **2026-09-09 状态变更：本评估已升级为第三批主线并进入实施** —— 用户已采购新机（`csmall-node2` 2C8G 同 VPC，2026-09-09 开通）、拓扑定稿（Redis 1主1从3哨兵 + 秒杀双实例）、管理方式选定方案 A。**本条目转为"实施依据"引用**，执行进度以 TODO 顶部「🚀 第三批执行清单」与 [[跨机集群方案-讨论纪要-2026-09-09]] 为准（下方 2026-09-07 原文保留为决策快照）。

> **2026-09-07 新增（面试"真集群"演示需求评估）**：目标不是全量容器集群（老机内存/工作量不允许），而是给**价值最高的 1-2 个**做跨机集群验证机制。**结论已定，暂不实施，待实际需要时按此推进。**

**选型结论（为什么是这两个）**：
| 候选 | 面试价值 | 全站依赖 | 跨机成本 | 结论 |
|------|---------|---------|---------|------|
| **Redis** | 极高（"挂了怎么办"必问） | 全站（秒杀/登录/幂等） | 低（主从+哨兵） | ✅ 选 |
| **秒杀服务** | 极高（高并发/负载均衡） | 核心业务 | 中 | ✅ 选 |
| Nacos | 高（raft） | 全站 | 高（**2 台做不了 3 节点 raft 多数派**） | ❌ |
| 商品/订单/Gateway | 中 | — | — | ❌ 优先级低 |

**关键判断：k3s 不是必须的**——1-2 个服务跨机双实例用 compose/手动 docker 即可跑通：
- Redis HA：老机主 + 新机从 + 哨兵（kill 主容器 → 哨兵自动切 → 服务无感）
- 秒杀双实例：两个实例都注册老机 Nacos → Gateway `lb://` 自动轮询 → 停一个 Nacos 自动剔除
- k3s 仅作为第三阶段"编排概念"锦上添花，不是双实例的前提

**硬件评估**：新服务器 **2核8G 同地域同 VPC**（成都，内网互通）足够——Redis 从 ~100M + 秒杀副本(-Xmx256m) ~500M，8G 富余。老机需先做 R7 腾内存（前提铁律）。
> ✅ **2026-09-09 已按此规格开通**：`csmall-node2` 47.109.70.197 / 私网 172.29.193.240（2C8G 经济型 e，同 VPC 同安全组，内网实测 0.43ms），Docker + <AI账号> 账号就绪。

**分阶段（每步可独立演示/回滚/不碰其他容器）**：
1. A：跨机 Redis 主从+哨兵 → kill 主演示切换（~半天）
2. B：秒杀副本跑新机 → 压测看负载均衡 + 停实例看剔除（~1 天）
3. C（可选）：新机 k3s server 学编排概念，暂不动业务
- ⚠️ 秒杀双实例真实改造点：`MessageRetryTask`（每 5s 重发 MQ）+ `SeckillReconcileTask`（P1 对账）**两个 @Scheduled 都要加 Redis 分布式锁**防双跑（本身是面试加分细节）→ 即「🚀 第三批执行清单」**阶段 0**

**诚实边界（面试别吹过头）**：2 台小机器 = 演示级 HA（防单机宕机，不防地域灾难）；只集群 Redis+秒杀，其他 19 容器仍是 compose 单实例——话术："选依赖最广的中间件+并发最高的业务验证机制，不是全量迁移"。

**面试价值**：跨机 Redis 真 HA（物理隔离 ≠ 单机哨兵）+ 秒杀多实例真实负载均衡/故障剔除/定时任务分布式锁——TODO #9/#4/#15 从"单机演示"升级为"跨机真实集群"。

---

### 9. 【学习】Redis 主从 + 哨兵高可用实验

> **2026-08-17 新增**：源自面试准备 `02-秒杀高并发.md` Q10（Redis 挂了怎么办）。当前生产为单机 Redis（standalone），秒杀链路强依赖 Redis（随机码/下单锁/库存 DECR），挂了功能直接停摆；`seckill_message_retry` 重试表只兜 MQ 可靠性。计划深入学习"主从复制 + 哨兵"高可用机制并实操实验。
> **2026-08-21 更新**：✅ 方案已定稿（1 主 + 1 从 + 2 哨兵，含 R1~R4 加固），见 [[Redis配置加固与哨兵模式方案]]；服务器确认续费至年底，待维护窗口执行后移入"已完成"。
> **🔴 2026-09-09 更新（跨机版定稿，取代上文"单机 2 哨兵"）**：拓扑 = **老机主(6379) + 新机从(6380) + 3 哨兵(26379, quorum=2)**，**跨机部署（真物理隔离）**。为什么改 3 哨兵：哨兵是**多数派**机制，容忍度 =(哨兵数-1)/2 → 3 哨兵容忍挂 1 个、2 哨兵容忍 **0 个**（挂 1 即失去仲裁）；原"2 哨兵"是单机内存受限的演示取舍。数据节点（主/从）**不需要奇数**（主从复制不是投票）。理由与答疑见 [[跨机集群方案-讨论纪要-2026-09-09]] §10.1。实施路径 = 本文件 §二十·一 内的「🚀 第三批执行清单」阶段 2→3。

**学习目标**：
- 主从复制：主节点写、从节点异步同步数据（数据冗余）
- 哨兵：监视主节点 → 挂了自动把从提升为主 → 客户端自动重连（故障转移）
- 对照同一思想：Nacos Raft 选主、ES 主分片提升、Redis 哨兵切换

**实验方案**（本地 Docker 或服务器，学习用最小拓扑即可）：
- 最小拓扑：1 主 + 1 从 + 1 哨兵；标准拓扑：1 主 + 2 从 + 3 哨兵（防脑裂需多数派）
- 端口规划：6379（主）/ 6380（从）/ 26379（哨兵），全部仅内网，不暴露公网
- Spring Boot 3.x 改造：`spring.data.redis.sentinel.master/nodes` 指向哨兵（compose 注入 `SPRING_DATA_REDIS_SENTINEL_*`）；sso/order/seckill 等连 Redis 的服务需全量回归

**边界认知（重要）**：
- **🔴 2026-09-09 更新**：**已具备跨机条件**（新机 2C8G 同 VPC）→ 采用"老机主 + 新机从"**跨机部署**，可防**整机宕机**（单机搭建只能防 Redis 进程崩溃，如被 OOM 杀）；仍不防地域灾难，托管 Redis（阿里云 Tair）仍是生产级选择
- 主从异步复制：主挂瞬间最后几笔 DECR 可能未同步到从，靠数据库 `seckill_stock >= quantity` 条件扣减兜底，不会真超卖
- 内存：**跨机版从副本放新机（~100M），老机内存零增加**；老机 available ~3G（R7 后），无需再评估
- **哨兵放哪台**：3 哨兵全部放新机（与从同机）——哨兵只做投票/监控，零业务负载；若老机挂，新机 3 哨兵仍可全票通过切换（多数派在新机侧）

**涉及文件**：`/data/csmall/docker-compose.yml`（新增 redis 副本/哨兵服务）、各服务 `application-prod.yml`（sentinel 连接配置）


---

### 6. 【演示】JMeter + Sentinel + SkyWalking 联合压测 ⏸️ 已归档（2026-09-07）

> 原用途为面试演示/简历视频。**2026-09-07 决定暂不录制**（简历已改用 HTML 新版），方案文档 `秒杀压测演示指南.md`/`秒杀服务器压测方案.md`/`演示视频讲解提纲.md` 已移入 `docs/归档/`；工具脚本保留 `deploy/jmeter/` 备日后复用。若将来录制新视频，先看归档文档。


---

### 🚀 第三批执行清单：跨机集群主线（2026-09-09 定稿，**阶段 0~5 全部完成，主线已 100% 收口**）

> **主线**：把 **#4（秒杀双实例）+ #9（Redis 主从哨兵）+ #14-P2（主从防数据）** 从"单机演示"升级为**跨机真实集群**（老机 + 新机同 VPC）。完整拓扑/答疑/演练剧本见 [[跨机集群方案-讨论纪要-2026-09-09]]；**逐阶段命令级落地步骤见 [[跨机集群实施执行清单-2026-09-09]]**（含 3 个新发现硬障碍：跨机 Dubbo provider 不可达 / 客户端需迁哨兵模式 / 地址稳定性）。
> **进度（2026-09-09）**：阶段 **0/A/2/3/3.5/3.6/4/5 全部完成** → #4/#9 已归档 [[TODO已完成]] §十三；**唯一剩余 = #14-P2**（`min-replicas-to-write`）。
> **诚实边界**：2 台小机器 = 演示级 HA（防单机宕机，**不防地域灾难**）；其余 19 容器仍是老机 compose 单实例。

**目标拓扑**：老机 = Redis 主(6379) + 秒杀实例1(10007)；新机 = Redis 从(6380) + 哨兵×3(26379, quorum=2) + 秒杀实例2(10017)；**数据唯一真源 = 老机 MySQL**，副本无状态无独立数据。

| 阶段 | 内容 | 前置 | 风险 | 状态 |
|---|---|---|---|---|
| **0** | **代码改造**：`MessageRetryTask` + `SeckillReconcileTask` 加 Redis 分布式锁（SETNX+TTL，可复用 `IdempotentAspect`/`SeckillServiceImpl` 已有 `setIfAbsent` 写法）→ 本地编译验证（不部署） | 无（纯本地，**可立即开工**） | 🟢 低 | ✅ **完成（2026-09-09）**：`RedisLockUtils`（SETNX+Lua CAS）+ 2 任务包锁；**9/9 单测 + 本地双实例联调全通过**（4 次漂移恰好 4 次修正、无重复执行、锁无残留）→ 见 [[本地双实例锁验证报告-2026-09-09]]；待阶段 4 部署到服务器复验 |
| 1 | 新机就绪：采购 + ssh 打通 + Docker 安装 | — | 🟢 | ✅ 已完成（2026-09-09：Docker 29.8.0 + Compose v5.5.1 + <AI账号> 账号） |
| **A** | **新机铺路**：compose 新增 5 服务 + 4 个 Redis/哨兵配置 + `.env` + agent 就位 | 阶段 1 | 🟢 | ✅ **已完成（2026-09-09）**：新机实测验证（文件齐/agent 23972028B/conf 密码行 2-1-1-1/compose exit=0/容器数 0）；见 [[跨机集群实施执行清单-2026-09-09]] §四·五 |
| 2 | 老机端口放行：2a MySQL 3306 / Redis 6379 改绑 `172.29.193.239`；2b product/order Dubbo 端口发布 + 注册宿主 IP | **维护窗口** | 🟡 中 | ✅ **已完成（2026-09-09 窗口 A）**：3306/6379 绑私网 IP、product/order 注册 `172.29.193.239:20880/20881`；跨机 4 端口全通、消费方动态切换成功、Dubbo 错误 0 条、21 容器零重启 → 见 [[跨机集群实施执行清单-2026-09-09]] §4.5 |
| 3 | 新机起 Redis 从 + 3 哨兵 → 验证主从复制 + kill 主演练自动切换 | 阶段 2 | 🟡 中 | ✅ **已完成（2026-09-09）**：副本 `master_link_status:up`、主从 21 键一致、副本只读拒写、3 哨兵 `quorum=2` 视角一致、配置持久化修通（踩坑 3 层：权限/目录/单文件挂载无法 rename）→ 见 [[跨机集群实施执行清单-2026-09-09]] §5.5 |
| **3.5** | **11 个服务 Redis 客户端迁哨兵模式**（canary 灰度） | 阶段 3 | 🟠 中高 | ✅ **已完成（2026-09-09）**：11 服务注入哨兵配置、4 批灰度全 Started + health 200、Redis 错误 0；行为验证 seckill 对端由容器 IP → `172.29.193.239:6379` → 见 [[跨机集群实施执行清单-2026-09-09]] §6.3 |
| **3.6** | **故障转移演练**（停老机主库 → 观察哨兵选主 → 客户端自动重连 → 恢复并切回） | 阶段 3.5 | 🟠 中高 | ✅ **已完成（2026-09-09 19:34—19:36）**：**8 条判据全通过**——选主 6.1s、客户端 Lettuce 9.1s 自动重连、11 服务 Redis 错误 0、DBSIZE 全程 21 不丢、切回 1.1s；额外发现**脑裂窗口 10.9s** 与"哨兵 conf 必然漂移"红线 → 见 [[跨机集群实施执行清单-2026-09-09]] §6.5 |
| 4 | 新机起 `mall-seckill-2`（10017，注册 IP 覆盖）→ Nacos 见 2 实例 → 压测验证负载均衡 + 停实例故障剔除 | 阶段 0 + 2 | 🟡 中 | ✅ **全部完成（2026-09-09 21:19—21:45）**：副本 `Started in 78s`、老机换带锁 jar（停机 77s）、**Nacos 2 实例**（HTTP `10007`+`10017`、Dubbo 双 provider）、**锁互斥经 Redis MONITOR 受控争用验证通过**、**网关双实例路由（TCP 连接取证）**、**停实例故障剔除通过**（存活实例承接、无 5xx、拓扑可复原）、**负载均衡分布 60 次请求精确 30:30**、Redis 错误 0；⚠️ 暴露 **16s 失败窗口** → 已立 **#53** → 见 [[跨机集群实施执行清单-2026-09-09]] §7.6~§7.8、[[TODO第三批实现与原理]] §六 |
| 5 | 文档回填（拓扑/踩坑/面试话术 → [[TODO第三批实现与原理]]）+ #4/#9/#14-P2 归档 [[TODO已完成]] | 阶段 3/4 | 🟢 | ✅ **已完成（2026-09-09）**：#4/#9 已归档 [[TODO已完成]] §十三；原理/踩坑/话术已补入 [[TODO第三批实现与原理]] §五/§六；#14-P2 仍未做（见下） |
| 6（可选） | 新机 k3s 学习区（独立区，不混业务） | — | 🟢 | ⏸️ 可选 |

**硬前置（阶段 2/4 共同依赖，缺一不可）**：
1. **端口放行**：3306/6379 绑 `127.0.0.1` 是第一批安全加固的副作用 → 跨机不可达（**实测**新机→老机 3306/6379 不通，22/80/8848/5672/8091/9200/10007 通）；改绑**宿主私网 IP** `172.29.193.239`（不是 `0.0.0.0`）；⚠️ **公网闸门始终是安全组**——阿里云公网 IP 是 NAT、不在网卡上，绑 `0.0.0.0` 与绑私网 IP 的公网可达性**等价**（见 [[TODO第三批实现与原理]] §5.6）；实测 SG 未放行 3306/6379 公网 → 公网不可达；**实测无需改安全组**（同安全组内网互通不受公网规则限制）
2. **注册 IP 覆盖（双向）**：① 新机副本默认注册容器 IP → 必须注入 `SPRING_CLOUD_NACOS_DISCOVERY_IP=172.29.193.240` + `DUBBO_IP_TO_REGISTRY`；② **老机 product/order 的 Dubbo provider 也注册容器 IP（实测 172.18.0.18/20:20880），新机路由不到** → 老机侧加 `DUBBO_IP_TO_REGISTRY=172.29.193.239` + `DUBBO_PORT_TO_REGISTRY` + 发布 20880/20881（**Path 1，已定夺**，需重启这两个服务；Path 2 静态路由为回退方案）
3. **连接串全指向老机**：副本无独立数据，`SPRING_DATASOURCE_URL` / `SPRING_DATA_REDIS_HOST` / `SPRING_RABBITMQ_HOST` / Nacos 全用老机内网 IP（**不能再用容器名** `mysql`/`redis`）
4. **代码改造**：定时任务分布式锁（阶段 0），否则双实例**双跑**（重试任务重复发 MQ + 对账任务重复跑）
5. **Quartz 任务无需改造（已核实）**：`SeckillInitialJob` / `SeckillBloomInitialJob`（`QuartzConfig` 每分钟触发）都有 `redisTemplate.hasKey` 幂等守卫（已缓存则跳过）→ 双实例双跑只多日志、不改数据；**全项目仅 3 处 `@Scheduled`（均在 mall-seckill）**，其中 2 处必须加锁

**纪律**：每阶段独立可回滚、不碰数据、低峰执行；改 compose 前 `cp` 备份；演练后确认容器全恢复（演练剧本见 [[跨机集群方案-讨论纪要-2026-09-09]] §十一）。


---

### 二、已完成表格行摘录

> 原「### 🟢 第三批：企业级演进 + 学习」表内（已从表中移除，表内仅保留未完成项）

| 编号 | 事项 | 定位 |
|---|---|---|
| ~~#4~~ | ~~秒杀集群化 + 配置中心（单机 2 实例演示级）~~ | ✅ **已完成（2026-09-09）**：跨机双实例（老机 10007 + 新机 10017）、Nacos 双实例、负载分布 30:30、定时任务锁互斥、停实例剔除通过 → 归档 [[TODO已完成]] §十三，原理见 [[TODO第三批实现与原理]] §六 |
| ~~#14-P2~~ | ~~Redis 主从防数据 - P2 配置层（min-replicas-to-write 1）~~ | ✅ **已完成（运行时 2026-09-09 21:45 + conf 持久化已落盘，2026-09-10 复核确认）**：老机主库 `min-replicas-to-write 1` + `min-replicas-max-lag 10`，**已写入 `/data/csmall/redis/redis-master.conf` 第 15~16 行**（`CONFIG REWRITE` 在单文件 bind mount 上必然 `Resource busy`，故由用户 sudo 手工追加；**无需重启 Redis**，重启后自动生效）。✅ 复核证据：`CONFIG GET` 返回 1/10，从库 `state=online,lag=0,min_slaves_good_slaves=1`。<br>**⚠️ 新机从库明确【不开】**——该参数对被提升为主库的从库同样生效，而切换后新主库手里没有从库 → **会拒绝所有写**（老机不恢复就一直不可写），代价远大于收益。<br>**代价（主库侧）**：只有一个从库 → **新机不可用时老机拒写**（`NOREPLICAS Not enough good replicas to write.`，读仍可用）；停新机做演练/维护前先 `CONFIG SET min-replicas-to-write 0`，恢复后设回 1（**"演练开关"**）。**方案文档见 [[Redis主从切换防数据问题方案]]** → 见 [[跨机集群实施执行清单-2026-09-09]] §5.9/§6.4/§7.9 |
| ~~#9~~ | ~~Redis 主从 + 哨兵实验~~ | ✅ **已完成（2026-09-09）**：新机从库 + 3 哨兵、11 服务迁哨兵客户端、**故障转移演练通过（选主 6.1s / 客户端 9.1s 自愈 / 数据零丢失）** → 归档 [[TODO已完成]] §十三，原理见 [[TODO第三批实现与原理]] §5.9 |
| ~~#46~~ | ~~nacos 数据卷挂载重启~~ | ✅ **已完成（2026-09-09）**：`csmall_nacos_data` 卷挂载生效 + 认证/6 规则/27 服务验证通过；踩坑（compose 卷名前缀）见 [[TODO第三批实现与原理]] §一、[[TODO已完成]] |
| ~~#47~~ | ~~数据库备份恢复演练（#29 收尾）~~ | ✅ **已完成（2026-09-09）**：独立临时 mysql 容器原名导入 cron 备份 `cs_mall_20260909_0230.sql.gz`，6 库 39 表 + 抽样行数 vs 生产逐行一致，验完删容器生产零接触；方案对比（sed vs 独立容器）见 [[TODO第三批实现与原理]] §三 |

> 原「### 🧭 推荐执行路线」表内（已从表中移除；该表原为 2026-09-08 优先级快照，其 P0/P1 项均已完成）

| 优先级 | 编号 | 事项 | 为什么这个优先级 | 预估工作量 |
|---|---|---|---|---|
| 🔴 **P0 必做** | ~~#46~~ | ~~nacos 数据卷挂载重启~~ | ✅ **已完成（2026-09-09）**——csmall_nacos_data 卷挂载 + 全验证通过，详见 [[TODO第三批实现与原理]] | 已完成 |
| 🔴 **P0 必做** | ~~#47~~ | ~~数据库备份恢复演练~~ | ✅ **已完成（2026-09-09）**——独立临时容器验证 cron 备份可完整还原（39 表 + 数据一致），详见 [[TODO第三批实现与原理]] §三 | 已完成 |
| 🟠 **P1 建议做** | ~~#4~~ | ~~秒杀集群化 + 配置中心（单机 2 实例）~~ | ✅ **已完成（2026-09-09 跨机版）**：集群化部分完成（**配置中心迁移未做**，见 §中优先级 4）；讲"负载均衡 / 故障剔除 / 定时任务分布式锁"三个机制实证 | 已完成 |
| 🟡 **P2 有余力做** | ~~#9~~ | ~~Redis 主从 + 哨兵实验~~ | ✅ **已完成（2026-09-09）**：跨机 1 主 1 从 + 3 哨兵，故障转移演练通过（选主 6.1s / 客户端 9.1s 自愈 / 零丢失） | 已完成 |
| 🟡 **P2 有余力做** | ~~#14-P2~~ | ~~主从防数据 P2（min-replicas-to-write 1）~~ | ✅ **已完成（运行时 09-09 + conf 落盘 09-10）** | 已完成 |

---

### 三、历史完成标记（原散落在待办条目旁）

> 这些是"顺手记下的一次性完成标记"，原先挂在别的待办条目旁边，容易误读成"这条到底做没做"，故一并移出。

- **（原 R6 段内）** ✅ **第一批 #2 已于 2026-09-07 完成**：内存止血——实测 available 2.1G 的前提，先于一切扩容（明细见 [[TODO已完成]]）
- **（原 §7 HTTPS 段内）** 🔥 已升入**第二批 #2**：唯一线上代码 bug，改动 ~10 行（✅ 2026-09-07 已修复，明细见 [[TODO已完成]]）
- **（原 §26 前）** 🟡 **第二批 #6**（✅ doRegister 已修，2026-09-07）
- **（原 §35 前）** 🟡 **第二批 #1**：用户可见 bug（search 缺 2 条新商品）✅ 已修复（2026-09-08 A2 上线）

---

### 四、文档归档 / 整理记录（原属 [[文档索引]]）

### 📦 B. `docs/归档/` 本次移入 16 个（2026-09-10）

| 文档 | 归档原因 |
|---|---|
| `AI限流与并发闸门-部署执行清单-2026-09-08` | ✅ 已执行完毕（30 并发实测生效：10×200 + 20×429） |
| `Nacos认证-部署执行清单-2026-09-08` | ✅ 已执行完毕（#13，归档 §十一） |
| `Sentinel部署执行清单-2026-09-08` | ✅ 已执行完毕（#5-P0，归档 §七） |
| `第二批部署执行清单-2026-09-08` | ✅ 已执行完毕（11 个 jar 全量上线 + Flyway V6 + ES 清理） |
| `跨机集群实施执行清单-2026-09-09` | ✅ 阶段 0~5 全部执行完毕（⚠️ 含可复用 runbook：§7.7.1 优雅下线 / §十二 命令块纪律） |
| `跨机集群方案-讨论纪要-2026-09-09` | 方案已拍板且全部实施完毕（⚠️ 含答疑与演练剧本） |
| `服务器内存优化方案` | ✅ 已执行完毕（R7：21 容器 mem_limit + Nacos 降堆 + Swap 2G） |
| `Redis配置加固与哨兵模式方案` | ✅ 加固（R1~R4 09-07）+ 哨兵（#9 09-09）均已完成 |
| `Redis主从切换防数据问题方案` | ✅ 已实施（#14-P2 已开启，2026-09-09） |
| `秒杀对账任务实现方案` | ✅ 已实施 + 已随第二批部署（#14-P1） |
| `搜索双索引统一与一致性评估` | ✅ 已实施并部署（#33，归档 §九） |
| `服务器巡检与待修复问题清单-2026-08-04` | 08-04 巡检快照；余项已收口到「⏸️ 已确认问题」章节 |
| `企业级生产评估报告-2026-08-15` | 一次性基线快照，整改项已被三批 TODO 覆盖 |
| `Redis集群适配与配置管理评估` | ✅ 评估完成，结论 = 暂不实施 |
| `Kubernetes与ZooKeeper评估` | ✅ 评估完成，结论 = 不部署（k3s 仅备用方案） |
| `待实施计划总览` | ⚠️ 2026-08 历史快照，已被本索引取代 |

> **相对路径引用已同步修正（16 处）**：`docs/评估报告/…` → `docs/归档/…`，涉及 `docs/项目上下文文档.md`、`docs/Redis使用避坑指南.md`、`CLAUDE.md` 及 3 个被移动文档自身。
> ✅ **Obsidian `[[文件名]]` 链接不受移动影响**（按文件名解析），已复核无残留旧路径。

> **🆕 2026-09-11 追加归档 2 篇**（用户逐篇评估 `docs/评估报告/` 后决定；`docs/归档/` 总数 28 → **30**）：
> | 文档 | 归档原因 | 归档前处理 |
> |---|---|---|
> | `集群化与配置中心迁移方案` | **阶段 A0（Nacos 认证，第二批 #13）+ 阶段 B（集群化，跨机双实例）均已执行完毕**，且形态由方案设计的"单机 2 实例"**升级为跨机双实例**；§二 服务器现状（available 2.1Gi / 无 Swap / 21 容器）已验证过期；§六 清单 7 项 = 已执行 4 / 未做 3（未做的全是阶段 A） | ① **阶段 A 正文逐段迁出为新文档 [[配置中心迁移方案]]**（内容零改写，归 **#4-配置中心**）；② **归档文件名保持不变** → 既有 `[[集群化与配置中心迁移方案]]` 链接继续解析；③ 状态头改写为「归档时逐阶段核对表」（✅ A0/B/R7/分布式锁 · ⏳ 阶段 A · ❌ §二 现状） |
> | `Sentinel能力评估与P0计划-2026-08-26`（原名 `Sentinel能力补充计划`） | **P0 已执行完毕并验收**（文中"待服务器执行"是历史写法；**实测 2026-09-11 `adminLogin` 20 并发 → 93.26% 被限流**）；§一 现状盘点（2026-08-26 的 4 个空转接口表）已过期；面板缺口 #66 亦已修复 | ① **P1/P2/P3（未兑现的能力）切分**到**同名活文档** [[Sentinel能力补充计划]] —— **文件名未变，6 处既有 `[[Sentinel能力补充计划]]` 链接仍指向活文档**；② 归档件**改名为「评估与P0计划」**以区分；③ 状态头改写为归档说明 + P0 证据 + 活文档指针 |

> **🆕 2026-09-11 追加归档（第二次）· 3 篇**（用户逐篇评估"已提炼"的文档后确认；`docs/归档/` 总数 30 → **33**，`docs/评估报告/` 20 → **17**）：
> | 文档 | 归档原因 | 归档前处理 |
> |---|---|---|
> | `AI导购Agent实现详解` | **#32 已全部完成并归档**（[[TODO已完成]] §十六/§十七）；核心内容已提炼 | ① **文件名不变** → 17 处 `[[wiki]]` 链接继续解析；② 状态头改写为归档说明 + 三类"仍长期可用"的内容（**三步排障法 §7.4 / 日志关键行 §7.1 / 审计键回放 §7.3 / 开关与三级回滚 §八**）+ 活文档指针 [[问题解决--LLM链路的契约漂移与分层降级]] |
> | `AI模型名停用风险与thinking参数改造方案` | **#58 已实施 + 已部署验证通过**（[[TODO已完成]] §十四）；核心内容已提炼 | 同上（文件名不变 → 25 处引用不受影响）；状态头点明 **§3.1 十格实验 / §4.5 可配化 / §4.6~4.7 为什么不引 Spring AI** 仍有参考价值 |
> | `本地双实例锁验证报告-2026-09-09` | **跨机集群阶段 0 已完成并归档**（[[TODO已完成]] §十三）；**零相对路径引用**，且内容已被**三层提炼** | 状态头写明三层提炼落点：完整方法论 [[问题解决--本地双实例分布式锁验证方法]] · 面试骨架 [[10-7-问题解决（提炼版·验证方法论）]] Q19 · 话术 [[面试速记-7 通用话术与方法论]] 第 ③ 条 |
>
> **与前两次的区别**：这一次**推翻了两篇文档原有的"已完成但保留、不归档"结论**（用户 2026-09-11 明确改主意）—— 理由是一致的：**对应待办已全部关闭 + 方法已提炼成「一类问题」文档 ⇒ 原文只剩溯源价值**。归档时统一在状态头加了"**归档 ≠ 内容失效**"，避免后来人误以为内容作废。

> **🆕 2026-09-11 另外归档 2 份工作副本**（**不在 git 里**，属 `*-副本.md` 本地备份）：`TODO第三批实现与原理-副本.md`（946 行）· `TODO第三批实现与原理-1-副本.md`（964 行）→ 移入 `docs/归档/`。
> **为什么现在才动**：这两份是**切割期间的比对底稿**；只有当 **① 切割完成、② 逐行 verbatim 复核通过、③ 循环检查（链接 / 计数 / 索引 / § 引用）全绿**之后，才能确认"原册已无独有内容" → 此时归档才安全（本次正是按这个顺序执行的）。

> **🧩 2026-09-10 追加归档 2 个**（从 `docs/问题解决/` 移入，**非第一批的 16 个**）：
> | 文档 | 归档原因 | 归档前处理 |
> |---|---|---|
> | `AI智能导购模块开发计划` | 它是**开发计划/设计文档**（与 `问题解决/` 其余 30 篇性质不同）；**四阶段已 100% 完成**；文内"当前配置/接口清单"**已漂移 5 处** | 加头注：**清单类内容一律以代码为准** + 逐项列出漂移对照 |
> | `Docker部署审计报告` | **一次性基线快照**，核心结论「不能直接 Docker 部署」已完全过时（21 容器在跑）；8 项发现 5 项已解决、1 项失效（systemd）、2 项转 TODO（#40 Step2 / #22）| 加**「归档时现状对照表」**（逐项实测），并确认 #44 等活待办在 TODO 里仍活着（**#44 已于 2026-09-10 完成**）|

> 🔴 **归档这份审计报告时的重要复核发现**：其"问题 5（真实 API Key 泄露）"**未完全收口** —— 硅基流动 embedding key `sk-***（已吊销 2026-09-10）…` **已随 commit `719ff6f` push 进公开仓库历史 = 已公开泄露**，对应 **#44 ✅ 已于 2026-09-10 吊销并轮换**（详见上方 §44）；而 DeepSeek 的两个 key（`sk-****…`、当前生产 `sk-****…`）实测**均未进 git 历史**（`git ls-files deploy` 21 个被跟踪文件全为模板/配置，无真 Key）。

### ✅ C. 本次一并修正的「状态头过期」7 个（另一种文档漂移，已全部修正）

| 文档 | 原头部写的 | 已修正为 |
|---|---|---|
| `TODO第三批实现与原理` | 🟡 逐项实施中 / 阶段 2a/2b 改动已备好 | ✅ 阶段 0~5 全部完成（2026-09-09/10） |
| `Nacos认证-部署执行清单-2026-09-08` | 📋 待服务器执行 | ✅ 已执行完毕 + 已移入归档 |
| `Sentinel部署执行清单-2026-09-08` | 📋 待服务器执行 | ✅ 已执行完毕 + 已移入归档 |
| `Redis配置加固与哨兵模式方案` | 哨兵模式部分仍未执行 | ✅ 全部执行完毕 + 已移入归档 |
| `Redis主从切换防数据问题方案` | 📋 已定稿待实施 | ✅ 已实施 + 已移入归档 |
| `搜索双索引统一与一致性评估` | 🟡 待服务器部署 | ✅ 已实施并部署 + 已移入归档 |
| `秒杀对账任务实现方案` | 🔧 需重启 seckill 加载新类 | ✅ 已部署 + 已移入归档 |


---

> **搬移记录**：2026-09-10 按用户要求搬移（已完成项 → 文末；新增顶部「📌 待办速览」）。搬移前版本见 git 历史。


---

### 五、2026-09-11 搬移：#63 ES mapping 缺陷 + #59 余额问题

> **搬移说明**：原正文 `### 63.` 整段与 `#59` 表行已于 2026-09-11 **迁出本文件**（本文件定位 = 未完成/待决策的唯一状态源）。
> **完整明细**（验收矩阵 / 召回回归 / 衍生加固 / 测试）→ **[[TODO已完成]] §十八**；**原理与过程** → [[TODO第三批实现与原理-1]] **§九**（#63）/ **§十**（#59→#31）/ **§十一·§十二**（Embedding 可替换性）。此处只留指针，避免三处重复。

| 编号 | 条目 | 结果 | 证据落点 |
|---|---|---|---|
| **#63** | ES `cool_shark_mall_ai` 的 mapping 与代码期望不符（品牌过滤恒失效 / 补全恒空 / IK 未生效 / 无 `semanticVector`） | ✅ **已修复并独立复核**：`ik_max_word` 生效（单字 → `小米`/`手机`）· `term brandName` **0 → 4** · 补全 **恒空 → 可用** · `semanticVector(1024, cosine)` 就位 · `dynamic:false` · **召回回归无异常** | [[TODO已完成]] §18.1 · [[TODO第三批实现与原理-1]] §九 |
| **#59** | 硅基流动余额不足（embedding 402）→ **阻断 #31** | ✅ **已充值 10 元解决**：实测 `POST /v1/embeddings` → **200 / `dims=1024`**（与 mapping `dims=1024` 吻合）→ **#31 前置解除** | [[TODO已完成]] §18.2 · [[TODO第三批实现与原理-1]] §十 |

### 六、2026-09-11 搬移：#48 第一层「模拟数据生成」+ 数据标识 + 可观测展示工具链

> **完整明细** → [[TODO已完成]] **§十九**；**原理与 8 处踩坑（G1~G8）** → [[TODO第三批实现与原理-1]] **§十三**；**方案正文与实测** → [[Python模拟数据与数据隔离方案]]（§5.1 压测 · §6 可观测 SOP · §6.4.1 录像 Runbook）。此处只留**指针 + 摘要**，避免多处重复。
> 🔴 **归档范围必须说清**：**#48 是一个两层方案**，本次**只归档第一层「造数」+ 数据标识 + 展示工具链**；**第二层「AI 并发压测」（mock LLM / Agent 双链路）完全未开始** → **剩余 6 项已另立 [[#67]]**（见正文待办表）。

| 项 | 状态 | 证据摘要 | 链接 |
|---|---|---|---|
| **4 个 Flyway 迁移**（9 张表加 `data_source`） | ✅ | 四库 `flyway_schema_history` 均 `success=1`（ums V3 / oms V7 / seckill V6 / resource V2）；⚠️ 教训：**`docker restart` 对新迁移是空操作**，必须重新打包 + 重建镜像 | §19.1 · §19.2 |
| **造数脚本**（预检三道防线 / 按登记表回填 / 逆序清理） | ✅ | 校准 **5 次真跑**，最后一次 `浏览 38 / 加购 8 / 下单 4（已支付 4，支付失败 0）/ 失败 0` + `✅ 回填校验通过` | §19.4 |
| **数据标识（专用列 + 回填矩阵）** | ✅ | 9 张表 SIM ⇄ 登记**逐一对齐**、**6 项漏标检查全 0**；**`oms_payment_record` 等"服务端写的表"按 `user_id` 兜底回填 4/4**（关键机制验证通过） | §19.3 · §19.4 |
| **清理演练（C-8）** | ✅ | 精确删 **56 行**（含**未登记**的 3 条订单项，靠 `order_child` 模式照样删掉）；空批次幂等收尾 | §19.4 |
| **压测脚本 `load_test.py`（两档）** | ✅ | 浏览档 20/50/100 → **69.0 / 97.2 / 99.6 RPS**（失败 0 · 拐点 50~100 并发）；限流档 `adminLogin` **93.26% 被限流**（`支付订单` 实测仅 0.66%，不可用） | §19.4 |
| **A 步 #66（Sentinel 面板可见性）** | ✅ | compose 8 处新增 → **11/11 服务上报** → 面板轮询 10 个客户端端点、最近 10 分钟拉取失败 **0** → **用户目视确认面板出现 8 个服务** | §19.1 |
| **录像 / 正式造数 / 第二层 AI 压测 / `--with-seckill` / Redis 精确清理 / `sim_baseline` 比对** | ⏳ **未完成** | **已另立 [[#67]]**（#48 剩余部分） | 正文 **#67** |
| **衍生（无编号）** | 向量链路降级（失败回落全文 / 仅全文索引）+ Embedding 可替换性 **P0~P3**（接口 · 启动自检 · 维度步骤入库 · `encoding_format` 可配）+ **3 处边界修复** | ✅ 完成，**56 → 82 项测试全绿**；⚠️ **代码未部署** | [[TODO已完成]] §18.3 · [[TODO第三批实现与原理-1]] §10.4 / §11 / §12 |

### 七、附录：#48 设计历程摘要（2026-09-11 自 [[TODO文件]] 第三批表迁入 · 零删改）

> **来源**：原为 [[TODO文件]] 「🟢 第三批」表中 `#48` 行的第 3 列（4674 字符单元格）。因该处已改为登记表 + 逐条正文，此格**原样迁入**本文件留档（#48 两层方案的完整设计历程）。

 🟢 **【已归档 2026-09-11】第一层「造数」+ 数据标识 + 可观测展示工具链已完成** → 明细见 [[TODO已完成]] **§十九**、原理与 8 处踩坑见 [[TODO第三批实现与原理-1]] **§十三**；**剩余 6 项已另立 [[#67]]**。（以下为历史设计记录，保留备查）<br>✅ **规范设计定稿（2026-09-10）** + 🆕 **09-11 实施前复核（§〇.1，9 条）** + 🆕 **09-11 可观测展示章节（§六）+ 执行顺序重排**：① 慢节奏造"运营数据"（80/15/5 漏斗）② mock LLM 测 AI 服务端并发承载（不调真实 API）+ **真实并发 ~100 req/s + 每日 12 点高峰窗口（cron）**。<br>🔴 **本次补上「数据隔离层」**——初稿只有用户名前缀，实测判定**不够**：清理 SQL 顺序错误（先删用户 → 订单删不掉）、`pms_spu.sales` / 库存 / Redis 秒杀预热键**不可逆污染**、Redis 清理想按 pattern 全删会**误伤真实数据**、mock 未实现 SSE 格式（会收不到 chunk）。<br>**定稿方案 = 影子登记表**（`cs_mall_sim.sim_batch` / `sim_entity`，清理唯一依据；逆序 + 分批 + 幂等 + dry-run）**+ 快照回滚兜底**（不可逆字段不硬算）**+ Redis 按 id 精确删**；mock 部署**新机内网**（容器内 `127.0.0.1` 不可达）且**必须支持 SSE 分片**；压测走内网并**避开 Sentinel 限流接口**。<br>⚠️ 脚本须在**新机/内网**跑（老机 5Mbps 自压自伤）。方案：[[Python模拟数据与数据隔离方案]]（第一层）+ [[AI并发测试方案]]（第二层）；**隔离设计的选型过程与实测证据见 [[TODO第三批实现与原理-1]] §四** <br>🆕 **2026-09-11 实施前复核（已完成）**：逐条实测 + 读码 → 方案文档新增 **§〇.1**（9 项修正 **D1~D9** + 2 项决策 + "复核为真"清单）。**最要命的一条**：mock 注入变量 `COOXIAO_AI_BASEURL` **根本不生效**（yml 实际读 `${AI_API_BASE_URL}`，且 compose 未透传该变量）→ 照原稿实施会**压到真实付费 API**；另有 mock 缺 `tool_calls`（生产 `AI_AGENT_ENABLED=true`）、每用户频控 10/60s 与 Sentinel `ai-chat=5` 会污染并发结论、mock 单线程 `HTTPServer` 自己是瓶颈。**决策**：生产 + 临时放开限流、Agent 双链路各压一遍（执行剧本见文档 §3.5）。**下一步**：落地第一层造数骨架（venv + 影子表 + fail-fast 预检 + dry-run 清理）。<br>🆕 **2026-09-11 晚 · 标识方案定稿为「专用列」+ AI 侧实施完成**：用户拍板**不再借用现有自由字段（`tag`/`data`），改用 9 张表统一的专用列 `data_source VARCHAR(16) NULL`**（`NULL`=常规/真实 · `SIM`=模拟造数），理由是**避免"一行挂两种标识"的歧义**（借用写法已撤回）。<br>✅ **AI 侧三项已完成**：① **4 个 Flyway 迁移文件已落盘**（`ums V3` / `oms V7` / `seckill V6` / `resource V2`；版本号经实测 `flyway_schema_history` 核对确为"下一个可用号"；**只加文件、未手工 ALTER** —— 手工 ALTER 与迁移并存会让服务重启时报 `Duplicate column name` → **Flyway 失败 → 应用起不来**）② **脚本改为「按登记表回填」**（新增 `backfill()` / `verify_backfill()`，**服务端零改动**；**4 张脚本直接写的按主键**、**5 张服务端在链路中写的按 `user_id`** 兜底回填 —— 实测这 5 张都有 `user_id`）③ **只读预检实测通过**：6 个 schema **0 个** `data_source` 列 → 迁移可安全执行。**附带修掉一处漏删**：`oms_payment_record` 原挂"按登记主键"模式但**脚本从不登记支付记录** → 清理走"无登记→跳过" → **静默残留**（全库 0 外键不报错），已改为按 `user_id` 删。<br>⏳ **当前阻塞点（用户侧，按序）**：低峰**重建镜像并重建容器**让迁移生效（🔴 **`docker restart` 是空操作** —— 2026-09-11 实测踩到：迁移文件在 jar 里、jar 被 COPY 烘进镜像，重启只是用旧镜像跑旧 jar → Flyway 报 "Schema is up to date" → 新迁移永不执行。必须：本地 `mvn -o -B -DskipTests -pl <4 模块> -am package` → scp 4 个 jar 到老机 `/data/csmall/jars/`（改名 `mall-<svc>.jar`；**该目录 ai-deepseek 不可写，须 ecs-user**）→ `docker compose build` → `docker compose up -d`。⚠️ **五层命名**：容器名 `csmall-ums` ｜ compose service 名 `mall-ums` ｜ 镜像 `csmall-mall-ums` ｜ SkyWalking 服务名 `mall-ums` ｜ Maven 模块目录 `mall-ums/`；**老机 21 个容器里没有任何 `mall-*`**）（脚本已加"列缺失即 fail-fast"门禁 → **迁移不生效就跑不起来**，这是有意设计）→ 老机建影子库 `cs_mall_sim` → `mysqldump` 六库快照 → 跑 `--days 1 --per-day 50` 校准写链路。详见方案 **§〇.2 §C 待办清单** 与 `deploy/scripts/sim/README.md`。<br>🆕🆕 **2026-09-11 晚 · 又一次修订（运行环境）**：原定"`python3 -m venv` + `pip install`"**实测跑不通** —— 两台机器均 **`ensurepip` 缺失**（未装 `python3-venv`）→ venv 建出来**没有 pip**；且**无外网**（`pypi.org` / `archive.ubuntu.com` / 清华源**全不通**，**仅阿里云镜像可达**，`mirrors.aliyun.com/pypi` 实测 HTTP 200）。→ 改为 **`sudo apt-get install -y python3-pymysql`**（apt 源 `mirrors.cloud.aliyuncs.com` 通，`python3-pymysql` 1.0.2 deb **38.2KB 实测秒下**，含 `pymysql/{__init__,connections,cursors}.py`），**免 venv、免 PEP 668、免外网**；`requests 2.31.0` 系统已自带。⚠️ 版本差异：apt 给 **1.0.2**、`requirements.txt` 钉 1.1.1，脚本只用 `pymysql.connect`/`DictCursor`，两版一致 → 不影响。详见方案 §〇.1 **D6 修订** + `sim/README.md` §二。<br>🆕 **2026-09-11 19:14 · 校准第一次真跑（C-7）——抓出 2 个"必然失败" + 我 1 个转录错误**（详见方案 **§〇.2 §G**）：① **用户名不许有下划线**（服务端 `^[a-zA-Z]{1}[0-9a-zA-Z]{3,15}$`）→ 前缀 `test_sim_` 改 **`testsim`**；② **联系人姓名只能 2~4 字符**（`REGEXP_CONTACT_NAME=".{2,4}"`，提前读码发现）→ 原 `模拟用户1234`（8 字符）改 **`模拟42`**；③ 我**手抄** phone 正则时多抄一组 `[0-9]`（写成 12 位，真值 11 位）→ 已改用"从 Java 源抽字面量逐条 diff"的**机器比对，7/7 一致**。<br>**新增两道防线**：脚本内置服务端 **7 条真实正则** + `validate_local()`，**预检阶段一次性验完全部字段**（不再"跑一次撞一个"）；**禁止手抄正则**。<br>✅ **失败零污染**：`sim_entity` **0 行**、业务表**零写入**、`sales`/`stock` 未动，只多 `sim_batch` 行（可用 `--clean --batch` 幂等收尾）。<br>🆕 **19:19 第二次真跑又抓到 1 个（G4）**：**假号段撞既有用户** —— 实测 `1390000` 段已被 `benchuser01..100` **占满 100 个**（`13900000001`~`13900000100`）→ 注册 `409 注册手机号已存在`（**我的疏漏**：上轮只看脱敏样例 `1390******98`，没核实整段占用）。处置：`SIM_PHONE_PREFIX` `1390000` → **`1390009`**（实测空闲；另有 1390090/1390100/1391111/1380000/1890000/1990000 均空闲），并新增**预检第 8 项「手机号段 / 用户名段碰撞预检」**（逐条 `IN` 查询，占用即 fail-fast + 空闲段提示）。**至此预检共三道防线**：服务端 7 条真实正则本地校验 · 号段碰撞预检 · `data_source` 列就位门禁。<br>🆕 **19:23 第三次真跑：注册/登录/加购成功**（建 20 用户 + 5 购物车、`oms_cart` 新增 5、未下过单、`sales`/`stock` 未动），但 **browse 全 401** → 抓到 **G5：浏览接口也需要登录**，**推翻方案 §6.2 旧结论**（代码证据：`mall-front/.../ResourceWebSecurityConfiguration.java:56-65` → permitAll 白名单只含 `/` `/favicon.ico` `/error` `/swagger-resources/**` `/v2\
