# CoolShark 项目待办事项

> **创建日期**: 2026-05-13
> **最后更新**: 2026-09-07（新增顶部"执行路线图"，按批次重组优先级：第一批=安全+资源止血、第二批=正确性+演示价值、第三批=企业级演进。原 #1~#41 全部保留在正文，供逐条评估）
> **关联文档**: [[服务器巡检与待修复问题清单-2026-08-04]]、[[JVM调优方案]]、[[阿里云ECS服务器情况]]、[[Redis配置加固与哨兵模式方案]]、[[服务器内存优化方案]]、[[连接池统一HikariCP方案]]、[[Python模拟数据与AI并发测试方案]]、[[集群化与配置中心迁移方案]]、[[Sentinel能力补充计划]]、[[Redis主从切换防数据问题方案]]、[[TraceId链路日志规范方案]]、[[认证安全企业级升级方案]]

---

## 🎯 执行路线图（2026-09-07 重组，按"从止血到演进"排序）

> **用法**：明天起从头逐条评估/实现时，按下面三个批次推进；每条详细方案见正文对应编号（编号未变）。
> **原则**：第一批解决"出事会真出事"的（安全洞/资源红线）；第二批解决"用户可见 bug + 面试/演示价值"；第三批是"企业级展望"，演示项目可后置。

### 🔥 第一批：安全 + 资源止血（1~2 个维护窗口内完成）

| 顺序 | 编号 | 事项 | 为什么第一批（服务器实测依据） |
|------|------|------|------------------------------|
| 1 | **#25** | JWT_SECRET 生产随机化 | 实测生产密钥=代码默认值，clone 仓库即可伪造任意用户 JWT = **全项目最严重安全洞** |
| 2 | **R7** | 内存优化（mem_limit+Nacos降堆+Swap） | 实测 available 仅 2.1G、无 Swap、全容器 mem_limit=0 → 后续一切操作的前提 |
| 3 | **#24** | MySQL 强密码 + 端口 127.0.0.1 | 实测 `MYSQL_ROOT_PASSWORD=root`（比记录"4 位"更严重）+ 端口全映射 0.0.0.0 |
| 4 | **R1+R2+R3+R4** | Redis 加固（密码/AOF/内存上限/conf） | 实测全空：无 requirepass、appendonly no、maxmemory 0——一次性配 redis.conf 固化 |
| 5 | **#38** | Dockerfile 双份清理 | 5 分钟低成本；模块目录残留 Alpine 旧版，防误用重踩坑 |

### 🟡 第二批：正确性 + 面试/演示价值（有时间就做，优先级从高到低）

| 顺序 | 编号 | 事项 | 为什么这批 |
|------|------|------|-----------|
| 1 | **#33** | 双索引数据不一致 | 实测 ai=20 > search=18，**普通搜索查不到 2 条新商品 = 用户可见 bug**（不是技术债） |
| 2 | **#8** | AI 预算按北京时间结算 | 唯一已确认的线上代码 bug（预算 8:00 重置），改动 ~10 行 |
| 3 | **#36** | DLX 死信 + OrderQueueConsumer requeue 修复 | 实测 basicNack(requeue=true) = 毒消息无限重试挂单 |
| 4 | **#13** | Nacos 开启认证 | 实测无 token 读配置 200，内网失陷可注册假服务（服务伪装） |
| 5 | **#29** | 数据库定期备份 | 实测无任何 mysqldump；数据是"命"，备份是运维底线 |
| 6 | **#23** | 漏触发接口补 @Validated | 注册/地址/管理员更新校验静默失效（非法数据可入库） |
| 7 | **#5** | Sentinel 能力补齐 | 3 接口规则空转 + 热点限流——面试价值最高 |
| 8 | **#2 + #34** | AI 接口限流 + 并发闸门 | AI 慢请求占线程 + 外部 LLM 配额，上生产前必修（当前 0 调用可缓） |

### 🟢 第三批：企业级演进 + 学习（演示项目可后置，按兴趣/时间取用）

| 编号 | 事项 | 定位 |
|------|------|------|
| #4 | 秒杀集群化 + 配置中心（单机 2 实例演示级） | 面试高价值，需先做 R7 腾内存 |
| #9 | Redis 主从 + 哨兵实验 | 学习 HA，方案已定稿 |
| #15 | K8s 实操（k3s） | 学习用，需评估新服务器（见 TODO 顶部咨询结论） |
| #39 | 镜像仓库 + CI/CD 流水线 | 企业级交付 |
| #40 | 微服务 actuator healthcheck | 可观测基础课 |
| #41 | 日志聚合 Loki | 可观测闭环 |
| #30 | Prometheus + 告警 | 根治静默故障 |
| #16/#22/#26 | TraceId 落日志 / CORS 收敛 / 网络隔离 | 企业级细节 |
| #35 | 统一 Jackson（替换 fastjson） | 安全 + 规范 |

### ⏸️ 明确暂缓/仅评估（不实现，面试讲认知即可）

| 编号 | 事项 | 说明 |
|------|------|------|
| #10/#11/#12/#18/#21/#27 | Lua/ZSET/Redis集群/购物车缓存/BFF补强/KMS | 均为"未来商业化/规模化"评估，当前不做 |
| #3/#19/#20 | 秒杀活动管理/价格库存校验/死权限码 | P2，演示场景影响小，面试讲清即可 |
| #7 | HTTPS/SSL | 阻塞链=域名+ICP备案+预算，外部条件限制 |
| 低区 #2/#3/#4/#5/#6 | 图片打包/RedisBloom/微信支付/支付宝/压测演示 | 见低优先级区原文 |

---

## ⏸️ 已确认问题（演示项目暂缓修复，2026-08-21 巡检确认）

> **背景**：2026-08-21 对生产服务器（8.156.77.197）做 Redis 专项巡检 + 配置事实核查。以下问题全部经服务器实测确认（docker inspect / redis-cli CONFIG GET / 与本地 `deploy/docker/docker-compose.yml` 比对）。**当前为演示项目，暂不修复**，待有空时按方案处理。

### R1. 【Redis】无密码认证 + 密码链路三处脱节 🔴

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

### R2. 【Redis】未启用 AOF，仅 RDB 快照，重启丢数据 🔴

**现状（已核实）**：`appendonly no`；仅 RDB 快照（`save 3600 1 300 100 60 10000`）。秒杀库存预热、购买标记（reseckill）、随机码、AI 会话上下文全部存在 Redis。

**影响**：进程重启/崩溃最多丢 60s~15min 数据；购买标记（`mall:seckill:reseckill:*`）丢失 → 可能重复秒杀；库存 DECR 丢失靠 DB `seckill_stock >= quantity` 条件兜底不超卖，但预扣库存会与 DB 不一致。

**解决方案**：挂载自定义 redis.conf，开启 `appendonly yes` + `appendfsync everysec`（性能与安全平衡，秒杀场景标准做法），RDB 与 AOF 并存。📄 详见 [[Redis配置加固与哨兵模式方案]] §2.1/§2.2

### R3. 【Redis】无内存上限 + noeviction，内存失控风险 🔴

**现状（已核实）**：`maxmemory 0`（无上限）、`maxmemory-policy noeviction`。

**影响**：Redis 可吃满服务器 16G 内存（系统内存基线已 71%）；noeviction 在内存写满时**拒绝写入** → 秒杀随机码/库存写入直接失败，功能停摆而非降级。

**解决方案**：redis.conf 设 `maxmemory 256mb` + `maxmemory-policy volatile-lru`（只淘汰带 TTL 的缓存键，保护 `mall:seckill:reseckill:*` 永久购买标记不被误淘汰）。📄 详见 [[Redis配置加固与哨兵模式方案]] §2.1

### R4. 【Redis】无自定义 redis.conf，配置无法持久化 🟡

**现状（已核实）**：裸 `redis-server` 启动，无配置文件挂载；所有 `CONFIG SET` 调整重启即失。

**解决方案**：在 `/data/csmall/redis/` 生成 `redis-master.conf` / `redis-replica.conf` 并挂载进容器，`command: ["redis-server", "/usr/local/etc/redis/redis.conf"]`，一次性固化 R1~R3 全部配置。⚠️ 挂载**不加 `:ro`**（Redis 运行时会自动 REWRITE conf 记录角色/replicaof，只读挂载会导致故障切换失败或脑裂）。📄 详见 [[Redis配置加固与哨兵模式方案]] §二/§三

### R5. 【连接池】文档称"已全面替换 HikariCP"，实际生产 6 个模块仍是 Druid 🟡

**现状（已核实）**：`mall-sso`（3 个数据源）/`mall-product`/`mall-order`/`mall-seckill`/`mall-ums`/`mall-ams` 的 `application-prod.yml` 均为 `type: com.alibaba.druid.pool.DruidDataSource`，且各 webapi pom 显式引入 druid 1.2.24（`mall-ums-service`/`mall-product-service` 的 pom 反而排除 HikariCP）；仅 `mall-resource` 无 druid 依赖（用 Spring Boot 默认 HikariCP）。

**矛盾点**：`docs/项目上下文文档.md` §6.1(8)/§8.12 记载"Druid 曾致上传线程挂死、已全面替换为 HikariCP"——与事实不符，文档需更正。

**影响**：文档与事实脱节，若 Druid 历史问题（上传线程挂死）在生产复现，排查方向会被误导。

**解决方案**（二选一，建议先 a 后 b）：
- a) **✅ 已执行（2026-08-17）**：更正文档——`docs/项目上下文文档.md` §6.1(8)/§8.12 已如实记录"仅 mall-resource 切换 HikariCP，其余 6 服务仍 Druid 稳定运行"；`docs/面试准备/03-数据库设计.md` Q7 已同步更新
- b) **⏸️ 真正切换（暂缓）**：6 个模块 prod yml 删除 `type:` 行（回落 Spring Boot 默认 HikariCP）+ webapi pom 移除 druid 依赖 + service pom 移除 HikariCP exclusion → 需全量回归 + 重启窗口，演示项目暂缓

📄 **详细执行方案（含 Step 0~7 步骤 / HikariCP exclusion 陷阱 / SSO 三数据源注意事项 / 回滚方案 / 监控说明）见 [[连接池统一HikariCP方案]]**

### R6. 【文档勘误】Sentinel 端口映射描述错误 🟢

**现状（已核实）**：compose 为 `8090:8858` + `JAVA_OPTS="-Dserver.port=8858"`，服务器实际 `0.0.0.0:8090->8858/tcp`（容器内 8858）；`docs/阿里云ECS服务器情况.md` §4.1 表格误写 "8090→8080"。

**解决方案**：更正 `docs/阿里云ECS服务器情况.md` 为 "8090→8858（容器内 8858）"。TODO 文件下方 §中优先级#2 提到的 "Sentinel 控制台（8090 端口）" 指宿主机端口，无需改动。

> 🔥 **第一批 #2**：内存止血——实测 available 2.1G 的前提，先于一切扩容
### R7. 【内存】容器无 mem_limit + 无 Swap + Nacos/Sentinel 堆可降 🟡

> **2026-08-21 确认**：服务器 available 仅 2.1G、无 Swap、**全部 21 个容器 `mem_limit=0`**（任何进程失控可直接吃满宿主 → OOM 杀服务，历史杀过 ES）。8-04 已做过一轮 JVM 调优（`docs/JVM调优方案.md`），本轮为增量优化。

**四项优化**（按收益排序）：
1. **全容器加 mem_limit**（安全网）：11 微服务 768m~1g、es/oap 1.5g、其余 256m~512m；`docker update --memory` **零重启即时生效** + compose 持久化
2. **Nacos 堆 1g→512m**：省 ~480M（最大单点收益，standalone 注册量小，需重启 nacos）
3. **Sentinel 加 -Xmx256m**：省 ~50-100M 并消除默认堆 ~3.5G 隐患（需重启）
4. **Swap 2G**：防瞬时峰值 OOM 缓冲垫（需 ecs-user sudo 执行）

**明确不做**：再压 product/order/seckill 堆（秒杀突发 Full GC 风险）、降 ES 堆 / MySQL buffer pool（影响性能收益小）。

**执行后预估**：available 2.1G → ~2.7G，OOM 失控风险基本消除。

📄 **完整方案（含执行步骤/验证/回滚）见 [[服务器内存优化方案]]**

### R8. 【运维】Redis 大键定期巡检（防患于未然）🟢

> **2026-08-21 新增**：实测确认当前 Redis **无大键**（21 键，最大 `token_list_.lock` 仅 9.7KB，慢日志为空），但商业化/数据量增长后需定期巡检，防止"单线程阻塞 + DEL 卡顿 + 内存不均"。

**巡检方案**（生产标准三件套）：
```bash
redis-cli --bigkeys                       # 快速体检(SCAN 渐进式,不阻塞)
redis-cli MEMORY USAGE <key>              # 逐 key 精确定位(4.0+)
redis-cli SLOWLOG GET 10                  # 慢日志=大键操作痕迹
# ⚠️ 绝不用 KEYS * 扫描(阻塞)
```
- **阈值建议**：单 key > 10MB 或单集合 > 1 万成员 → 告警（按业务定）
- **频率**：月度巡检 + 每次大版本部署前；与 R7 内存优化同节奏（可合并进同一维护窗口）
- **处理**：`UNLINK` 异步删除（禁用 DEL）/ 拆 key / TTL 兜底 / 序列化优化（java→JSON）
- **相关**：本项目 `RedisTemplate` 使用 Java 序列化（体积大、跨语言不可读），数据量上来后可换 JSON 序列化

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

### 3. 【秒杀管理】缺少秒杀活动管理功能（后台 UI + 场次维度购买标记）

> **2026-08-26 新增**：审计发现秒杀模块**完全没有管理功能**——活动/时间窗口/价格/库存全靠手动改数据库（`seckill_spu`/`seckill_sku` 表），且**无后台管理界面**。

**现状（已核实，2026-08-26 服务器 + 代码双重确认）**：
- 6 个秒杀场次时间窗口均为 `2026-01-01 ~ 2026-12-31`（全年有效，无真实"场次"概念）
- **购买标记维度问题**：三 key（orderLock/ordered/reseckill）均按 `skuId:userId` 维度，**与场次无关**；`reseckill` 支付后**永久标记**（无 TTL，见 `OmsOrderServiceImpl.markSeckillPurchased`）→ **同一 SKU 同一用户支付后永久不能再买**，即使开新一轮秒杀
- `success` 表 `UNIQUE KEY uk_sku_user (sku_id, user_id)` 是数据库层最终防线，同样按 SKU 维度
- **业务影响**：无法支持"每轮秒杀同一商品可复购一次"的常见运营模式

**目标**（按需拆分，均可独立实施）：
1. **秒杀活动管理后台**：创建场次（时间窗口/秒杀价/库存/限购）、启停用、列表——参考现有 admin 商品管理风格
2. **场次维度购买标记**：`reseckill` key 改为含 `seckillId` 维度（如 `mall:seckill:reseckill:{seckillId}:{skuId}:{userId}`），支持"每场限购一次"
3. **success 唯一索引调整**：`uk_sku_user` → `uk_seckill_sku_user`（含场次），与 Redis 标记对齐

**涉及文件**：`mall-seckill-webapi`（SeckillController/Service + `SeckillCacheUtils`）、`mall-order-webapi`（`OmsOrderServiceImpl.markSeckillPurchased`）、前端 admin 新增秒杀管理页、`database/cs_mall_seckill/`（索引变更走 Flyway V+n）

> ⚠️ 注意：改购买标记维度会影响已产生的 success 数据（旧数据无场次），迁移时需评估（演示项目数据量小，可清理重建）。

---

### 4. 【集群+配置中心】秒杀/订单/商品三模块集群化 + 配置迁 Nacos（模拟企业级场景）

> **2026-08-26 新增（评估+计划，暂不实施）**：目标 = 三模块各生成集群（多实例）+ 几乎所有配置进 Nacos，模拟真实企业级场景。**用户已确认：仅讨论计划，不做任何操作**。

**方案决策（用户已选）**：
- 内存：先做 R7 优化（Nacos 降堆省 460M + Swap 2G）+ 副本压缩堆（-Xmx256m）
- 配置范围：非敏感全进 Nacos（数据源/Redis/MQ/Seata/限流），敏感留 .env（密码/JWT/AI Key）
- 顺序：先配置中心迁移，后集群化
- 集群形态：单机 2 实例演示级（可验证负载均衡/故障剔除/热更新，非真实 HA）
- **✅ 2026-08-26 已确认：仅秒杀模块加 1 副本（10007 + 10017），商品/订单暂不加**

**评估结论（关键）**：
- 服务器 available 仅 2.1G；**单秒杀副本（~650M）+ R7 释放（~560M）→ 加完后剩 ~2.0G ✅ 内存可行**
- 选秒杀理由：演示价值最高（秒杀压测看负载均衡 + 限流规则热更新），且"定时任务分布式锁"是面试亮点；商品/订单收益/改动比更低
- 多实例改造点：仅 seckill 有定时任务（`MessageRetryTask` 每 5s）→ 需 Redis 分布式锁；Sentinel 限流按实例统计（副本后集群 QPS 翻倍，要讲清）；Seata/MQ/Redis 天然多实例安全
- **单机集群 = 演示级 HA，机器宕机所有实例一起挂**，面试只讲"验证机制"不讲"高可用"

📄 **完整方案（内存测算/三模块价值对比/两阶段步骤/风险与回滚/执行清单）见 [[集群化与配置中心迁移方案]]**

---

### 5. 【Sentinel】能力补充计划（2026-08-26 评估，待实施）

> **2026-08-26 新增**：对项目 Sentinel 使用程度全面评估（代码实证）——当前只用了"基础三件套"（秒杀 QPS=10 流控 + 规则存 Nacos + 控制台），**4 个 @SentinelResource（秒杀/新增订单/支付订单/adminLogin）只有秒杀配了规则，其余 3 个是"注解但无规则"空转**；企业级高级能力（热点限流/系统保护/授权/集群流控）均未使用。

**优先级划分（用户确认后实施）**：
- 🔴 **P0 补齐 3 接口规则**（新增订单/支付订单 QPS=20、adminLogin QPS=10，Nacos 建规则即可，消除空转）——极低成本
- 🟠 **P1 热点参数限流**（秒杀按 spuId 差异化：爆款 QPS=100、普通 1000，当前整接口共享 QPS=10 互相误伤）——面试价值最高
- 🟡 **P2 集群流控**（秒杀集群化后 token server 统一配额，否则双实例各自 QPS=10 总量翻倍失真）——与 #4 集群化配套
- ⚪ **P3 暂不推荐**（系统自适应保护：CPU<5%无场景；授权规则：Gateway+Security 已挡）

📄 **完整计划（现状盘点/优先级/实施步骤/回滚/执行清单）见 [[Sentinel能力补充计划]]**

---

### 6. 【网关】mall-product Dubbo 应用名撞名 → lb:// 混入 Dubbo 实例（2026-08-28 评估，待实施）

> **2026-08-28 新增（Nacos 实例列表实测确认）**：`mall-product` 服务下有 **2 个实例** = `172.18.0.19:20880`（Dubbo provider，`protocol=dubbo`）+ `172.18.0.19:9010`（Spring Cloud HTTP）→ 网关 `lb://mall-product` 轮询**一半请求打到 Dubbo 端口**（非 HTTP 协议）报错。这是 prod pms 路由直连 `http://mall-product:9010` 的**真实原因**（配置注释："直连 HTTP 端口，避免 Nacos 混入 Dubbo 20880"）。

**根因**：`mall-product` 的 `dubbo.application.name` 与 `spring.application.name` 都叫 `mall-product`（**撞名**）→ Dubbo 3.x 应用级注册把 20880 实例混进同一服务名。对比 `mall-order`：dubbo 应用名 `mall-order-dubbo`（分开）→ "mall-order" 服务下只有 HTTP 实例，lb:// 安全。

**方案（待实施，改动小风险低）**：
1. 改 `mall-product-webapi` 的 application-{prod,test,dev}.yml：`dubbo.application.name: mall-product` → `mall-product-dubbo`（三环境对齐）
2. 验证 Nacos：`curl "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=mall-product"` → 应只剩 9010 实例
3. 可选：网关 pms 路由改回 `lb://mall-product`（去掉直连特例，统一风格）
4. 回归：后台商品管理（/pms/**）+ 前台商品 Dubbo 链路（消费者按**接口**引用，不受应用名影响；`providers:com.cooxiao.mall.product.*` 接口级注册不随应用名变）

**影响**：改后 lb:// 恢复可用；风险低（仅注册名变更）；不做也不影响现状（直连可用），只是路由风格不统一 + 多副本时会踩坑。

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

> 🔥 已升入**第二批 #2**：唯一线上代码 bug，改动 ~10 行
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
> **2026-08-21 更新**：✅ 方案已定稿（1 主 + 1 从 + 2 哨兵，含 R1~R4 加固），见 [[Redis配置加固与哨兵模式方案]]；服务器确认续费至年底，待维护窗口执行后移入"已完成"。

**学习目标**：
- 主从复制：主节点写、从节点异步同步数据（数据冗余）
- 哨兵：监视主节点 → 挂了自动把从提升为主 → 客户端自动重连（故障转移）
- 对照同一思想：Nacos Raft 选主、ES 主分片提升、Redis 哨兵切换

**实验方案**（本地 Docker 或服务器，学习用最小拓扑即可）：
- 最小拓扑：1 主 + 1 从 + 1 哨兵；标准拓扑：1 主 + 2 从 + 3 哨兵（防脑裂需多数派）
- 端口规划：6379（主）/ 6380（从）/ 26379（哨兵），全部仅内网，不暴露公网
- Spring Boot 3.x 改造：`spring.data.redis.sentinel.master/nodes` 指向哨兵（compose 注入 `SPRING_DATA_REDIS_SENTINEL_*`）；sso/order/seckill 等连 Redis 的服务需全量回归

**边界认知（重要）**：
- 单机搭建 = 演示级 HA：只防 Redis 进程崩溃（如被 OOM 杀），**不防整机宕机**；真实 HA 需 ≥2 台机器，或直接买阿里云托管 Redis（自带主从+哨兵）
- 主从异步复制：主挂瞬间最后几笔 DECR 可能未同步到从，靠数据库 `seckill_stock >= quantity` 条件扣减兜底，不会真超卖
- 当前服务器可用内存约 2.2G，加副本前先评估；服务器 8-29 到期，可结合续费/升级一起规划

**涉及文件**：`/data/csmall/docker-compose.yml`（新增 redis 副本/哨兵服务）、各服务 `application-prod.yml`（sentinel 连接配置）

### 10. 【评估】Redis Lua 脚本引入评估（仅评估，不实现）

> **2026-08-21 新增**：源自 `docs/项目上下文文档.md` §6.6（Redis 一致性策略分析）。当前项目未使用 Redis 事务（MULTI/EXEC）；**若未来**出现"多 key 强原子"需求，优先评估引入 Lua 脚本。**仅为评估记录，不会真的实现**，优先级低，要做也是之后再做。

**评估依据**（为什么 Lua 而非 Redis 事务）：
- Redis 事务 MULTI/EXEC 是"弱事务"：只保证命令隔离执行，**不保证回滚**（EXEC 前某命令失败，其余命令照常执行）；期间其他客户端可插入命令（需 `WATCH` 才部分缓解）
- Lua 脚本优点：整段脚本**原子执行**（期间无其他命令插入）、可写回滚逻辑、单次网络往返、Redis 官方推荐的复杂原子操作方式
- 触发场景（未来可能）：秒杀"扣库存 + 标记已购 + 写随机码"多 key 原子化；抢券/抽奖/资格预占等"读-改-写"；批量限流计数
- 约束：Redis Cluster 下 Lua 仅支持**同 slot 的 key**（跨 key 报 `CROSSSLOT`）
- 现状：所有 Redis 写入均为"单命令原子 + 锁 + 补偿"，暂无强原子需求 → **暂不实现**

**Lua 与哨兵模式共存评估**（2026-08-21 补充，修正早期"脚本与事务同样会中断"的不准确表述）：
- 切换瞬间的**连接中断**是客户端操作共性问题（普通命令/事务/Lua 都会遇到），客户端重连重试即可，非 Lua 特有
- Lua 特有的风险是**长脚本阻塞主节点 → 哨兵误判下线**（脚本执行期间不响应 PING，若超过 `down-after-milliseconds` 可能误触发故障转移）；`lua-time-limit`（默认 5s）超时后只读脚本可 `SCRIPT KILL`，写脚本无法中断
- 业界共存方案（大厂已验证，阿里云 Tair / AWS ElastiCache 均同时支持 Lua + 哨兵）：① 脚本保持毫秒级短小（第一原则，秒杀扣库存类仅几行微秒级）；② 调小 `lua-time-limit` 尽早暴露超长脚本；③ `down-after-milliseconds` 与最坏脚本时长保持数量级差距（本项目哨兵方案设 5000ms，微秒级脚本零风险）；④ **3 哨兵 + quorum=2 多数派**防单点误判；⑤ `SLOWLOG` + 慢脚本告警；⑥ 客户端重连 + 脚本幂等设计
- **结论**：Lua 与哨兵可安全共存，无本质冲突；本项目未来即使引入 Lua，也是微秒级短脚本，不构成误判风险

**涉及文件（若实施）**：`SeckillServiceImpl.java`（库存扣减 Lua 化）、`RedisBloomUtils.java`（已有 `DefaultRedisScript` 先例可参考）

### 11. 【升级计划】ZSET 延迟队列（仅计划，不实现）

> **2026-08-21 新增**：记录 MQ+Redis 联动现状与 ZSET 延迟队列升级评估。**仅为升级计划，不实现**；若未来项目商业化（面向营收/规模化），再评估实施。可用于**面试讲解**（展示系统设计深度与演进思路）。

**项目 MQ+Redis 联动现状（已代码确认）**：
- 秒杀下单：`SeckillServiceImpl` — Redis 预扣库存 `DECR`（防超卖闸门）+ ordered 标记 → RabbitMQ 异步落库（削峰填谷）
- 普通订单：`OmsOrderServiceImpl` — 支付回调写 reseckill 购买标记（Redis）+ MQ 发库存扣减消息
- **延迟/重试现状**：`MessageRetryTask` 每 5 秒 **DB 轮询** `seckill_message_retry` 表（status=0, retryCount<3）重发 MQ —— 教科书级"低配延迟队列"，演示场景够用
- 全项目 **ZSet 零使用**；无 RabbitMQ 延迟插件（x-delayed-message）；未实现"订单超时自动关单"（状态机已预留 `1=超时关闭`）

**ZSET 延迟队列方案（升级目标）**：
- 原理：`score` = 任务执行时间戳，`ZADD` 入队；后台每秒 `ZRANGEBYSCORE tasks 0 now` 拉取到期任务，`ZREM` 删除——**谁 `ZREM` 成功（返回 1）谁消费，天然防重复**
- 高级点：① `O(logN+M)` 有序范围查询，高并发优于 DB 轮询；② 单命令原子，分布式并发消费无锁；③ 精度可控到毫秒（score 可带小数）；④ RDB/AOF 持久化，重启不丢（对比内存时间轮）；⑤ 复用现有 Redis，零新组件；⑥ Redisson `RDelayedQueue` 即此实现，订单超时关单是经典案例
- 局限：拉模式非精准定时（轮询间隔 = 最大延迟）；任务全在内存（海量堆积成本高）；无 MQ 消费确认/死信语义，需 Lua 或业务代码自建

**升级触发条件（商业化时考虑）**：
- 订单超时未支付自动关闭（状态机已预留）
- 优惠券/活动过期、秒杀定时上下架
- 消息重试从 DB 轮询迁移到 ZSET（替换 `MessageRetryTask`）

> ⚠️ **2026-08-28 补充（05 Q8 竞态分析）**：做"超时自动关单"必须**同时**把支付更新改成条件更新（`UPDATE ... WHERE state=0`）——现状是 `updateOrderById` 无条件 setState(3)。关单 + 支付都靠条件更新原子互斥，先关后付自动退款，先付后关关单失败；另加对账兜底。**关单和支付改造是配套的，不能只做一边。**

**面试讲解要点**：
- "为什么用 ZSET 不用 DB 轮询"：复杂度（O(logN) vs 全表扫）/ 原子性 / 精度 / 持久化 四维对比
- "为什么不直接上 MQ 延迟插件"：ZSET 复用已有 Redis、零依赖、精度可控；RabbitMQ TTL+DLX 每档延迟一个队列、x-delayed 需装插件
- "怎么保证不重复消费"：`ZREM` 原子性 + Lua 封装"取+删"一步完成
- "怎么配合高可用"：ZSET + 哨兵（已规划）组合，与 Lua 短脚本共存无冲突（见 #10）

### 12. 【评估】Redis 集群适配 + 配置中心 + 多副本（仅评估，不实施）

> **2026-08-21 新增**：三份评估完成，**均暂不实施**，未来商业化/规模扩大时再尝试。

**评估结论摘要**：
1. **Redis 集群扩容，业务代码可适配** ✅：全项目零跨 key 操作（无 mget/pipeline/事务/Lua/集合运算），单 key 天然分布任意 slot；MOVED/ASK 重定向由 Lettuce 自动处理，业务代码无感。3 个适配点：① 未来新 key 约定 hash tag（防 CROSSSLOT）；② 客户端切换仅改 compose 注入 `SPRING_DATA_REDIS_CLUSTER_NODES`；③ 每节点 requirepass（并入 R1）
2. **配置写进 Nacos 统一管理：当前没必要** ❌：实际变更点已集中在 compose/.env 一处；变更频率极低、动态刷新无场景；引入成本（依赖/启动顺序/运维）大于收益；哨兵落地后节点对客户端透明，配置中心场景进一步缩小
3. **jar 容器多副本：当前没必要** ❌：服务器 2.2G 可用内存放不下副本（硬约束）；无流量压力（CPU<5%）；定时任务/Seata 多实例需改造；正确路径是"先基础加固 → 多机 → 再副本"

📄 **完整评估（含重定向机制/成本收益表/演进路径）见 [[Redis集群适配与配置管理评估]]**

> 🟡 **第二批 #4**
### 13. 【安全】Nacos 开启认证（方案 A，2026-08-26 评估，待实施）

> **2026-08-26 新增**：实测 Nacos **完全无认证**（无 token 直接读配置返回 200、控制台免登录、默认账号 nacos 未改）。公网安全组只开 22/80 挡得住外部，但内网失陷后可无认证读写 Nacos + 注册假服务（服务伪装）→ 消费者被引流到攻击者机器。

**判定：值得加**（成本低 + 生产化标配 + 面试必问），**优先级中**（当前公网进不来，非紧急）。
**方案 A（最小可行认证）**：
1. compose nacos 加 `NACOS_AUTH_ENABLE=true` + `NACOS_AUTH_TOKEN`（Base64 ≥32字节随机串）+ `NACOS_AUTH_IDENTITY_KEY/VALUE`
2. 重启 nacos → 无 token 访问返回 403
3. **全量同步**：11 微服务 + Seata + Dubbo 全配 username/password（任一漏配 = 该服务起不来），须同一维护窗口完成
4. 验证：注册/发现正常 + Sentinel 规则仍能拉取

> ⚠️ 与集群化方案的阶段 A0 合并执行（同窗口重启服务）；备选 B=8848 映射改 127.0.0.1（只挡外部，不解决内网）。
> 📄 详见 [[集群化与配置中心迁移方案]] 阶段 A0

### 14. 【Redis】主从切换防数据问题（五层方案，2026-08-26 评估，待实施）

> **2026-08-26 新增**：Redis 主从复制异步 → 主挂瞬间丢最后几笔写（库存 DECR/购买标记/幂等锁可能丢）。代码层无法 100% 消灭（本质），目标是"让丢失无害化"。

**五层方案（按优先级）**：
- 🔴 **P0 付款前校验 DB 库存**：支付接口加 DB 库存校验（不是 Redis），不够拦截不让付款——把"付款后补救"变"付款前拦截"（防最严重事故：付了钱没货）
- 🔴 **P0 落库失败不静默**：SeckillQueueConsumer 库存不足改"失败留痕 + 已付款告警 + nack 重试"（现在是 basicAck 静默丢弃）
- 🟠 **P1 对账任务**：定时 Redis vs DB 比对，以 DB 为准自动修正漂移 + 预热校验
- 🟡 **P2 配置层**：min-replicas-to-write 1（随 R2 主从哨兵一起）

> 架构层已做（Redis=闸门/DB=账本，条件扣减兜底）；配置层依托 R1~R4。
> 📄 **完整方案（五层详解/代码示例/实施清单/风险回滚）见 [[Redis主从切换防数据问题方案]]**

### 15. 【学习】K8s 实操实验（k3s 方案备用，2026-08-26 评估，暂不部署）

> **2026-08-26 新增**：K8s 架构已理解（控制平面/etcd/节点），**当前单机 4C16G 不部署**（场景不匹配 + 内存剩 2G），但 k3s 轻量方案已备好，未来学习实操/迁移演练时用。

- **现状**：服务器纯 Docker Compose（21 容器），无 k8s/etcd/zookeeper 进程（2026-08-22 实测）
- **选型**：k3s（~500MB，一个二进制搞定控制平面+节点，自带 etcd 替代，可加节点变集群）> minikube（学习 demo）> kubeadm（生产多机）
- **安装**：`curl -sfL https://get.k3s.io | sh -`
- **面试价值**：能讲清"控制平面（API Server/etcd/Scheduler/Controller）+ 工作节点（kubelet/kube-proxy）+ etcd 为什么难维护 + 为什么单机用 Compose 是对的"
- **时机**：学习 K8s 实操 / 模拟多机集群实验 / 迁移演练时启用

> 📄 **完整方案（k3s 安装步骤/架构名词/诚实边界/面试话术）见 [[Kubernetes与ZooKeeper评估]] 五、k3s 轻量部署方案**

### 16. 【日志】TraceId 链路日志规范：SW logback 集成（2026-08-28 评估，待实施）

> **2026-08-28 新增**：应用层 traceId 只覆盖 HTTP 链路——定时任务（SeckillInitialJob/SeckillBloomInitialJob/MessageRetryTask）、MQ 消费者（SeckillQueueConsumer/OrderQueueConsumer）、Dubbo provider 线程日志 `[]` 空（MDC 线程绑定 + X-Trace-Id 只走 HTTP 头）。SW agent 已全量接入（9.4.0），**traceId 一直在生成，只是没写进日志**。

**方案（只做 A，B/C/D 不做）**：
- 🔴 **A. SW logback 集成（推荐，~2 小时）**：mall-common 加 `apm-toolkit-logback-1.x` 依赖 + `logback-spring.xml`（`TraceIdMDCPatternLogbackLayout`，pattern 加 `%X{tid}`）→ 一处配置 12 个应用全部生效，覆盖 HTTP/Dubbo/MQ/定时任务全场景，且能和 SW UI 对号
- ⚪ B. Dubbo attachment 传播（~0.5 天）/ C. MQ 消息头（~0.5 天）/ D. 定时任务前缀（~2 小时）——均与 A 重复，**不做**
- 全做约 1.5 人天，演示项目不必要

📄 **完整方案（现状盘点/方案对比/实施步骤/风险回滚/面试价值）见 [[TraceId链路日志规范方案]]**

### 17. 【安全】认证安全企业级升级（2026-08-28 评估，待实施）

> **2026-08-28 新增（源自 05/06 面试准备讨论）**：BCrypt 密码存储与企业一致，但"**哈希只是第一层**"——企业还有 TLS / 登录限流锁定 / MFA / 密钥轮换 / 风控。现状：HTTPS 未上（无域名）、**无按账号/IP 登录限流**、无密码策略、无 MFA、BCrypt cost 默认 10、JWT 密钥明文无轮换。

**建议（只做 P0，其余讲认知）**：
- 🔴 **P0 登录限流**（Redis 失败计数，5 次/分钟锁 15 分钟，复用 @Idempotent AOP 经验，~0.5 天）
- 🔴 **P0 密码策略**（复杂度 + 弱密码黑名单，~0.5 天）
- 🔴 **P0 DelegatingPasswordEncoder 化**（{bcrypt} 前缀多算法并存，为迁移铺路，~0.5 天）
- 🟠 P1：HTTPS（依赖域名/备案阻塞链）/ Argon2id 切换 / JWT 密钥版本化
- 🟡 P2：MFA / 风控告警 / 会话管理——演示项目不做，面试讲认知即可

📄 **完整方案（现状盘点/差距清单/优先级建议/实施顺序/面试关系）见 [[认证安全企业级升级方案]]**

### 18. 【缓存】购物车 Redis 缓存演进（游客购物车，2026-08-28 评估，暂不实施）

> **2026-08-28 新增（源自 05-业务逻辑 Q4 评估）**：当前购物车纯 MySQL（oms_cart，按 user_id 分片，索引查询毫秒级）。**结论：现在不做 Redis 缓存**——购物车是"写多 + 不可丢 + 用户天然分片"的数据，缓存收益为零风险不为零（双写一致性 / 列表价与结算价不一致 / Redis 丢数据 = 用户体验事故）。

**演进路径（量级上来再实施）**：
1. **游客购物车 Redis**（收益最大）：key=`cart:guest:{deviceId}`，TTL 7 天（游客数据本就该过期），登录后合并进 DB——Redis 购物车最有价值的场景
2. **已登录购物车**：保持 DB 为主 + Redis 缓存读（收益有限，可选）
3. **红线**：绝不用 Redis 当购物车唯一存储（丢数据不可接受）

**面试价值**：能讲清"缓存适合读多写少可丢失（商品详情），购物车不适合纯缓存"的选型逻辑

### 19. 【订单】价格/库存服务端校验补强（2026-08-28 评估，待实施）

> **2026-08-28 新增（源自 05-业务逻辑 Q4 快照讨论）**：两个诚实点——① **订单价来自前端传入**（OrderItemAddDTO，BeanUtils copy），无服务端价格重算 → 价格篡改风险 + 商品改价后订单仍按旧价（商家亏）；② **普通订单库存扣减是异步 MQ**（下单不校验 → OrderQueueConsumer 扣减失败 basicNack **无限重试挂单**），秒杀才有前置预扣（Redis DECR + DB 条件扣减）。

**方案（P1，演示项目暂缓）**：
1. **结算价服务端重算**：下单接口按 skuId 批量查当前价（Dubbo 商品服务），以服务端价为准覆盖前端传入价；价格变化提示"价格已更新"
2. **库存前置校验**：普通订单下单前校验库存（或乐观预扣），不足直接返回"库存不足"——不做"下单成功再异步失败"的挂单
3. 可选（P2）：**购物车列表实时价覆盖**——listCartByPage 查完快照 → 收集 skuId → 一次 Dubbo 批量查当前价覆盖展示 → 失败回退快照（~20 行 + 批量查价接口，拼多多式体验；演示项目优先级低，先做 1 再考虑）
4. 可选：购物车列表已下架商品标灰/清理

**面试价值**：能讲清"快照只用于展示、结算回服务端实时数据"的铁律

### 20. 【权限】死权限码清理（2026-08-28 评估，待实施）

> **2026-08-28 新增（源自 05-业务逻辑 Q6 审计）**：ams_permission 表存在两种格式权限码——URL 式（`/ams/admin/read`，被 104 处 @PreAuthorize 使用）+ 冒号式（`pms:manage`、`oms:order:list` 等，**代码 0 引用、前端无权限判断 = 死数据**）。管理员登录时两种都被塞进 JWT。

**方案（P2，演示项目暂缓）**：
1. 先查清 ams_permission：URL 式保留，冒号式列出待删清单
2. Flyway V+n 幂等删除冒号式权限码 + 清 role_permission 关联
3. 重启 → 新登录管理员 token authorities 只剩 URL 式
4. 验证：后台管理各接口权限正常

**面试价值**：能讲"审计发现死权限码，清理统一，不是迁移"的审计能力

### 21. 【BFF】mall-front 企业级补强（2026-08-28 评估，暂不实施）

> **2026-08-28 新增（源自 05-业务逻辑 Q7）**：mall-front 是轻量 BFF（只读 + 无 DB + Dubbo 聚合），三个企业级差距——① **无缓存层**（商品详情是读多写少热点，企业 BFF 配 Redis Cache Aside；你无商品缓存，18 条商品毫秒级）；② **串行 Dubbo 调用**（组装详情 VO 依次查 SPU→SKU→分类→属性，企业用 CompletableFuture 并行 / 聚合 RPC）；③ **单实例无独立扩容**（企业 BFF 无状态按流量弹性扩，你单机内存不足场景不匹配）。

**方案（量级到了再实施）**：
1. **商品缓存**：详情/列表 Redis Cache Aside（key=商品ID，TTL 5-10 分钟），商品变更主动失效（复用 ISpuSyncService 同步 ES 的思路同步缓存）
2. **聚合并发**：详情 VO 组装改 CompletableFuture 并行 Dubbo 调用（延迟从 4 次之和 → 最慢一次）
3. **独立扩容**：多机/流量上来后 mall-front 单独多实例（无状态天然可扩）

**面试价值**：能讲清"BFF 三件套（缓存/并行/扩容）我全知道，量级到了再上"

### 22. 【CORS】服务端 CORS 收敛到网关（2026-08-28 评估，待实施）

> **2026-08-28 新增（源自 06-安全设计 Q5 审计）**：CORS 全景——网关 CorsConfig 显式白名单 ✅；但 5 个服务（ums/search/product/resource/seckill）WebMvcConfiguration 有 `allowedOriginPatterns("*")` 宽松通配（dev 直连需要，但**没限定 profile → 生产也生效 = 多余且宽松**）；mall-ai 自带 CORS（SSE dev 直连 10010 绕过网关，vite.config 注释实证）；order 已是范例（已注释并写明"由网关统一处理"）；ai 路由 DedupeResponseHeader 是重复头补丁。

**方案（P2）**：
1. 5 服务 WebMvcConfiguration 的 CORS 加 `@Profile("dev")` 限定（不是删——dev 直连还要用）
2. mall-ai CORS 同样收窄 dev-only
3. 网关显式白名单不动
4. ai 路由 DedupeResponseHeader 可删（重复源消失，可选保留兜底）
5. 验证：生产 curl -i 响应头只有一个 Access-Control-Allow-Origin；dev 跨域 + ai SSE 回归

**面试价值**：能讲清"CORS 是浏览器机制，生产收敛到网关、dev 直连才需要服务端 CORS"

> 🟡 **第二批 #6**
### 23. 【校验】漏触发接口补 @Validated（2026-08-28 审计，待实施）

> **2026-08-28 新增（源自 06-安全设计 Q6 审计）**：全项目审计 39 个 @RequestBody 接口，**5 个漏了 @Validated 触发开关**（规则在 DTO 但没触发 = 校验静默失效）：`UserController.doRegister`（**注册最严重**——UserRegistryDTO 的 @NotNull/@Pattern 全失效，非法数据可入库）、`DeliveryAddressController.addAddress/editAddress`（地址增改）、`AdminController.updateAdmin`（管理员更新）。`PaymentCallbackController.wechatNotify`（String body）无需 DTO 校验，可豁免。

**方案（P1）**：
1. 5 个接口（注册/地址增改/管理员更新）方法参数补 `@Validated`（注册最优先）
2. 回归：传非法值断言返回 400（注册接口重点验证用户名/邮箱/手机号格式）
3. 可选：AOP 统一给 @RequestBody 加校验，从根上消灭漏触发

**面试价值**：能讲"注解校验会静默失效（规则与触发分离），我审计出 5 个漏触发接口"的审计能力

### 24. 【安全】MySQL 密码强化 + 端口 127.0.0.1 绑定 + 容器非 root（2026-08-28 审计，待实施）

> **2026-08-28 新增（06 Q10 全量安全审计实测）**：① **MySQL root 密码仅 4 位**（.env 长度实测）——太弱，内网可爆破；② **中间件端口全映射宿主机 0.0.0.0**（Redis 6379/MySQL 3306/Nacos 8848/ES 9200/Seata 8091/RabbitMQ 5672/SkyWalking 11800...）——安全组挡了公网，但宿主机网卡全监听，纵深防御为零；③ 中间件容器以 root 运行（redis/nacos/gateway，mysql 已 500 非 root）。

**方案（P1）**：
1. .env 改 MySQL root 强密码（≥16 位随机）+ compose 同步 + 应用连接串验证
2. compose 端口映射改 `127.0.0.1:6379:6379` 等（宿主机仅本机监听，Docker 内网不受影响——服务间走容器网络）——**零功能影响、纵深防御大幅提升**
3. 中间件容器加非 root 用户（redis/nacos 镜像支持 -u）

**面试价值**：能讲"安全审计实测出 MySQL 密码太短、端口映射过宽、容器 root"的审计深度

> 🔥 **第一批 #1 当前最优先**：生产密钥=代码默认值
### 25. 【安全】JWT_SECRET 生产随机化（2026-08-28 审计，最优先）

> **2026-08-28 新增（06 Q10 审计实测）**：生产 .env 的 `JWT_SECRET` **和代码默认值一样**（CooxiaoMall2026Jwt...开头）——"生产用环境变量"形同虚设，**攻击者 clone 公开仓库就能伪造任意用户 JWT**。这是当前最优先的安全项。

**方案（P0，改动小）**：
1. 生成随机强密钥（≥64 字节，如 `openssl rand -base64 48`）
2. 更新服务器 /data/csmall/.env 的 JWT_SECRET
3. **同步更新全部 11 个服务的配置引用**（JWT_SECRET 环境变量名不变，只换值）
4. **全站重启 + 所有用户重新登录**（旧 token 全部失效，可接受）
5. 验证：登录/秒杀/支付全链路 + 旧 token 被拒

**注意**：换密钥 = 所有已签发 token 立即失效（用户要重新登录）——选低峰期执行；顺带把密钥版本化（kid）提上 TODO #17 P1。

**面试价值**：能讲"我发现生产 JWT 密钥=代码默认值（伪造 token 风险），主动换随机密钥"——最有力的安全审计案例

### 26. 【网络】容器网络隔离：应用网/数据网分网段（2026-08-28 记录，待实施）

> **2026-08-28 记录（源自 07-部署运维 Q3）**：csmall-net 是**单网段大锅烩**（21 容器全挂一个 bridge 网络），无网络隔离。企业做法：**应用网/数据网分网段**——应用（gateway/front/order...）在应用网，数据库（mysql/redis/es）在数据网，**数据网只允许应用网访问** → 网络隔离 = 纵深防御（比端口映射收窄更进一步）。

**方案（P2，演示项目暂缓）**：
1. compose 定义两个网络：app-net（应用服务）/ data-net（数据库 + 中间件）
2. 应用服务挂 app-net + 仅访问 data-net；数据库只挂 data-net
3. 中间件端口映射收窄 127.0.0.1（配合 TODO #24）
4. 验证：应用正常访问数据库、数据库不被外部网络访问

**面试价值**：能讲"容器网络也要纵深防御——应用网/数据网分网段"

### 27. 【安全】KMS 密钥管理（仅认知记录，不实施）

> **2026-08-28 记录（源自 07 Q5）**：企业敏感机密用 KMS/Vault/Secrets Manager（加密存储 + 审计 + 自动轮换），用法 = 存机密 → 授权 → 应用解密调用（最常用"加密后存配置"）。**本项目不上**——单机单团队 .env 够用；优先级排在 JWT 密钥随机化（#25）/ MySQL 强密码（#24）/ Nacos 认证（#13）之后。面试讲认知即可。

### 28. 【运维】容器 JRE 无诊断工具（换 JDK 镜像 / 装 Arthas / JFR，2026-08-28 实测，待实施）

> **2026-08-28 实测（源自 08-JVM Q5）**：生产容器 `eclipse-temurin:21-jre-alpine` 是 **JRE**——bin 里只有 java/jfr/keytool，**无 jstack/jmap/jstat/jcmd/jinfo/jps**。当前生产遇到 CPU 暴涨 / Full GC 想抓线程栈、堆转储**抓不了**（只有 OOM 自动 dump + JFR 可用）。

**方案（P2，三选一或组合）**：
1. **镜像换 JDK**：temurin:21-jdk-alpine（有全套工具，代价镜像大 ~300MB）
2. **装 Arthas**（阿里开源，JRE 可 attach）：生产在线诊断 dashboard/thread/stack，免重启
3. **JFR 常开**：启动参数 `-XX:StartFlightRecording=...`（JDK 自带，出事回放 CPU/GC/内存事件）

**面试价值**：能讲"我实测发现生产容器是 JRE 抓不了 jstack，改进方案是 JDK 镜像/Arthas/JFR"——比背流程高一个段位的诚实审计

> 🟡 **第二批 #5**
### 29. 【备份】数据库定期备份（2026-08-28 记录，待实施）

> **2026-08-28 记录（源自 07 Q9）**：07 Q9 提到"定期 mysqldump（可改进项）"但 TODO 从未记录——**文档缺口补上**。当前数据库数据在 Docker 卷（csmall_mysql_data），无定时备份；数据是"命"，备份是运维底线。

**方案（P2）**：
1. cron 定时 mysqldump（每日全量 6 库）+ 保留策略（保留 7 天/循环覆盖）
2. 备份文件落 `/data/csmall/backup/`（或对象存储）
3. **定期恢复演练**（备份没验证过 = 没有备份）
4. 与 R8 巡检同节奏（月检 + 大版本前）

**面试价值**：能讲"备份要有恢复演练，没验证过的备份等于没有"的运维底线

### 30. 【监控】Prometheus + Alertmanager 主动告警（2026-08-28 记录，待实施）

> **2026-08-28 记录（源自 07 Q10）**：07 Q10 提到"改进方向是 Prometheus + Alertmanager"但 TODO 从未记录——**文档缺口补上**。**"resource 挂 2 天才发现"的根治手段**：restart 只能拉起崩溃，静默挂/循环崩靠人工发现——主动告警是唯一根治。

**方案（P2，运维最短板）**：
1. Prometheus 采集：容器指标（cAdvisor/Docker exporter）+ 服务指标（actuator）+ JVM 指标
2. 告警规则：容器挂 / CPU 高 / 内存高 / 接口错误率 / 磁盘
3. 通知：Alertmanager → 邮件 / 钉钉 webhook
4. 与 R8 巡检、Q8/Q10 教训呼应

**面试价值**：能讲"被动监控（SkyWalking/Sentinel/docker）和主动告警（Prometheus）的区别，告警是根治静默故障"

---

### 31. 【AI】评估生产开启向量检索 embedding-enabled: true（2026-09-02 记录，待决策）

> **2026-09-02 新增（源自 09-AI模块 Q3 服务器复核）**：生产 `embedding-enabled: false`（ES 全文检索），向量检索 = 完整代码 + test 验证 + 预留开关。**用户倾向开启（BGE-M3 免费），待后续考虑**。

**现状（服务器 + 代码实证）**：
- prod yml `embedding-enabled: false` + 注释"生产默认关闭，按需开启"；test 环境 true
- ES cool_shark_mall_ai 索引实测**无 semanticVector 字段**（全文模式建的索引）
- .env 已有 EMBEDDING_API_KEY（硅基流动）；AiProperties/EsIndexInitializer/VectorSyncServiceImpl/RagServiceImpl 代码全就绪

**当时关闭的理由**：① 20 条商品 IK 毫秒级且准，语义优势兑现不了 = 收益 0 ② 向量化依赖硅基流动外部 API = 多一个故障点 ③ 稳定优先（sync-auto-on-startup 要部署即用）④ 全量重同步几千条会限流耗时

**开启评估（用户倾向：免费值得开）**：
1. prod yml 改 `embedding-enabled: true` → 重启
2. 重建/补索引：EsIndexInitializer 需建含 dense_vector(1024) 的 mapping（当前索引无向量字段）
3. sync-auto-on-startup 全量向量化（20 条，免费）
4. 验证：/ai/search 语义召回（"学生党性价比"等）+ ES 出现 semanticVector 字段
5. 风险：硅基流动 API 依赖——挂了向量化失败，**⚠️ 开启前必查：RagServiceImpl 是否有全文降级逻辑（无则先补），否则搜索直接报错**

**面试价值**：开 = 完整 RAG 链路真实运行；关 = 讲"按需开"的工程判断——两者都可讲；决策点 = 外部 API 稳定性能否接受

---

### 32. 【AI】AI 导购升级 Agent（Function Calling + ReAct）（2026-09-02 评估，仅计划不执行）

> **2026-09-02 新增（源自 09 Q0/Q12 讨论）**：当前 AI 导购 = "LLM 解析意图 + 代码写死执行"（无工具调用/无循环/无动作决策权）；升级 Agent = 技术演示增强 + 面试素材（生产 0 调用，业务收益为零）。

**关键结论**：
- **值得做但定位"演示增强"**：面试价值高（Function Calling + ReAct 实战）、学习价值高、业务价值低
- DeepSeek 支持 tools 参数（function calling），现有 intentSearch 可直接声明为第一个工具
- **边界是核心**：只读工具自动执行 / 写操作用户确认（human-in-the-loop）/ 参数 schema 约束 / 轮数上限 3 / 预算复用（2 元/天）/ 动作审计 / 失败降级回 RAG
- **工作量**：P0 最小 Function Calling ≈ 0.5~1 天（DeepSeekAiClient tools 支持 + search_products 工具 + ChatServiceImpl 循环）；P1 完整单 Agent ≈ 2~3 天（compare/get_stock 工具 + 3 轮 ReAct + 审计 + 降级）；P2 框架化可选（Spring AI 重构）

📄 **完整方案（现状/差距/场景工具集/9 条边界/分阶段到文件级/成本风险/面试话术/执行清单）见 [[AI导购Agent升级方案]]**

---

> 🟡 **第二批 #1**：用户可见 bug（search 缺 2 条新商品）
### 33. 【搜索】双索引数据不一致（mall-search 与 mall-ai 各维护一个 ES 索引）（2026-09-02 实测，待决策）

> **2026-09-02 新增（源自 09 Q7 复核，服务器实测确认）**：普通搜索和 AI 搜索**各维护一个 ES 索引 + 各一套同步链路**——数据同源（pms_spu，都经 Dubbo IForFrontSpuService 拉取），但**数据不一致实锤**。

**现状（2026-09-02 服务器实测）**：
- ES 三个索引：`cool_shark_mall_index`（**0 条**，空/旧索引遗留）、`cool_shark_mall_index2`（**18 条**，mall-search 用）、`cool_shark_mall_ai`（**20 条**，mall-ai 用）
- **不一致实锤：AI 索引 20 > search 索引 18**——2 条新商品只进了 AI 索引，普通搜索查不到新商品（两条同步链路各自为政）
- mall-search 同步：手动 `/search/sync` → Dubbo 分页拉 DB → Spring Data ES 批量写（SearchServiceImpl.loadSpuByPage，pageSize 注释还写 2）
- mall-ai 同步：启动自动 sync-auto-on-startup + `/ai/syncAll`（VectorSyncServiceImpl）

**影响**：① 同一批商品重复存储两份 + 旧索引垃圾（index 空索引）② 两套同步链路都要维护，漏同步 = 普通搜索缺新商品（已发生）③ 面试若被问"两个索引怎么保证一致"无解——当前不一致

**方案（P2，演示项目暂缓）**：
1. **统一单一索引**：mall-search 与 mall-ai 共用同一个 ES 索引（如保留 cool_shark_mall_ai 或重建 index2）
2. mall-search 只做**召回层**（过滤/分页/排序），mall-ai 在召回基础上做意图重排（AI 不自己建数据）≈ 淘宝架构
3. 清理空索引 cool_shark_mall_index（0 条）
4. 同步收敛到一条链路（Dubbo 变更通知 + 全量启动同步）

**面试价值**：能主动讲"我发现两个搜索各维护索引、数据不一致（18 vs 20），演进方向是统一索引 + AI 只做重排层"——比被面试官问出来强

---

### 34. 【AI】AI 模块高并发应对（2026-09-02 记录，待实施）

> **2026-09-02 新增（源自 09 Q9）**：AI 问答高并发与秒杀本质不同——秒杀是大量快请求（限流削峰可控），AI 是**少量慢请求占 Tomcat 线程 + 外部 LLM API 配额有限**。两个铁约束：① 每个 SSE 请求秒级 + 挂一个线程（少量用户就能占满线程池，拖垮其他接口）② LLM API 外部共享资源（QPS 配额 + token 收费），不能无限调。

**应对层次（从便宜到贵）**：
1. 🔴 **Sentinel 入口限流**：QPS 限流 /ai/**（与 TODO #2 合并——ai 接口现在无限流）
2. 🔴 **并发闸门**：Semaphore 限制"同时进行的 LLM 调用数"（如 20），超出 429"AI 繁忙"——**不设闸门，Tomcat 线程被 AI 占满，其他接口全挂**
3. 🟠 **问答缓存**：常见问答 Redis 缓存 2min TTL（IoT AssistantService 已有 2min 缓存先例可复制）→ 重复问题不调 LLM
4. 🟠 **降级链路**：LLM 满/挂 → 降级纯 ES 检索（直接给商品列表不生成）→ 保证"有响应"而非"卡死"（与 TODO #31 向量降级同一思想）
5. 🟡 **每用户频控**：Redis 计数（如 10 次/分钟），防单用户刷爆预算
6. 🟡 **多实例扩容**：会话在 Redis = 无状态 → 多实例分压 SSE 长连接（内存允许时，配合 TODO #4）

**项目现状**：SSE 异步 ✅ / 每日预算 TokenBudget ✅ / 无状态会话 ✅；Sentinel ai 限流 ❌ / 并发闸门 ❌ / 问答缓存 ❌ / 降级 ❌

**面试价值**：能讲清"AI 高并发 ≠ 秒杀高并发——核心是并发闸门防线程占满 + 缓存降 LLM 调用 + 降级保可用 + 无状态扩容"，AI 上生产前必修课

---

### 35. 【JSON】统一 Jackson，替换 fastjson（2026-09-03 记录，待实施）

> **2026-09-03 新增（源自 10 Q6 讨论，用户拍板）**：项目 JSON 混用——**Web 层默认 Jackson（Spring 集成），业务手写解析用 fastjson**。决定统一 Jackson。

**现状（代码实证）**：
- fastjson 2.0.43（根 pom）显式依赖 8 模块：mall-ai（DeepSeekAiClient/SiliconFlowEmbeddingClient/SessionManager/ChatServiceImpl/PreferenceExtractor/SearchPipeline）、mall-order（mq/支付宝拼参）、mall-seckill（MessageRetryTask/SeckillServiceImpl）、mall-common（IdempotentAspect/JwtTokenUtils）、mall-product（ImageUrlPrefixHelper）、mall-gateway、mall-sso、mall-front
- Jackson 已在：Web 层序列化（@JsonFormat/@JsonSerialize 全 VO）、JacksonConfiguration（Long→String 精度 + jsr310）、security 401/403 ObjectMapper、ListConvertUtils、jjwt-jackson
- 混用点：手写解析 JSON.parseObject/JSONObject/JSONArray（几十处）

**为什么统一 Jackson**：fastjson 1.x 有反序列化 RCE 连环漏洞史（2020-2022，1.2.24/1.2.68 多版本爆洞）；2.x 重写已修但团队规范常统一；Jackson 与 Spring Boot 深度集成（消息转换器/注解/配置中心化）+ 安全生态

**方案（P2，演示项目暂缓）**：
1. 手写解析替换清单：`JSON.parseObject/JSONObject/JSONArray` → ObjectMapper（readValue/writeValueAsString），JSON.toJSONString → objectMapper.writeValueAsString
2. mall-common 提供统一 ObjectMapper Bean（已有 JacksonConfiguration 可注入）
3. 依赖收敛：逐步移除各模块 fastjson 依赖（根 pom 保留管理或删）
4. 回归重点：AI 对话（DeepSeek 请求拼装/会话 JSON）、MQ 消息序列化（OrderQueueConsumer/MessageRetryTask）、支付宝支付 bizContent 拼参、JWT、幂等 AOP 参数 MD5

**面试价值**：能讲 JSON 库选型（Jackson vs fastjson 分工 + fastjson 1.x 漏洞史 + 为何统一 Jackson）

---

> 🟡 **第二批 #3**
### 36. 【MQ】死信队列 DLX 评估 + 订单消费者 requeue 修复（2026-09-03 记录，待实施）

> **2026-09-03 新增（源自 10 Q8 讨论）**：项目 MQ 可靠性"有重试没死信"——企业级五环里缺死信队列 + 可观测。

**现状（代码实证）**：
- acknowledge-mode: manual + Spring listener retry（max-attempts=3 间隔 1s，prod yml）
- 秒杀"先落库 seckill_message_retry + MessageRetryTask 每 5s 重发（retryCount<3）"（TODO #11 低配延迟队列）
- **全项目无 x-dead-letter 配置 = 没有死信队列**——重试耗尽仍失败的消息无归宿（丢或卡）
- ⚠️ **OrderQueueConsumer 仍是 basicNack(requeue=true)** = 毒消息无限重试挂单（与 TODO #19 关联）

**方案（P2，评估 + 待实施）**：
1. **先修订单消费者**（最小改动）：OrderQueueConsumer 失败处理改"重试有上限或 requeue=false + 记录"，消除无限重试（配合 TODO #19 价格/库存校验）
2. **DLX 死信队列**：业务队列声明 x-dead-letter-exchange → 重试耗尽 nack requeue=false → 死信队列 → 死信消费者记录原因/告警/人工补偿
3. 可观测：重试次数 / DLX 堆积告警（呼应 TODO #30）
4. 与 TODO #11（延迟队列/超时关单）评估合并考虑

**面试价值**：能讲"项目有重试没死信（TODO #36）+ 订单消费者 requeue 还在（TODO #19）——企业级五环：发送确认/消费重试/DLX/可观测/幂等，缺哪环我清楚"

---

### 37. 【数据】测试账号密码哈希验证清理（liucs/wangkj + 造数规范）（2026-09-03 记录，待验证）

> **2026-09-03 新增（源自 10-Q11 BCrypt 归因纠错实验）**：服务器实验证明 admin 登录失败的"跨平台 $2a/$2b"归因错误——真因是**测试数据哈希与声称密码不匹配**。实验时发现 **liucs/wangkj 用户仍存旧 `$2a$10$N.zmdr...` 哈希**（与 admin 当初失败的同一个），若其密码声称 123456 同样登录失败。

**待验证/处理**：
1. 用 bcrypt 验证 liucs/wangkj 的哈希到底匹配什么密码（或确认它们实际用什么密码登录）
2. 若密码与测试预期不符 → 重置为正确哈希（目标平台生成 $2b）
3. 顺带排查其他表是否有"手填未验证哈希"的账号
4. **固化规范**：造测试数据的密码哈希必须 `bcrypt.checkpw(密码, 哈希)` 验证过再入库，禁止手工复制哈希（三用户同哈希 = 复制粘贴特征）

**面试价值**：能讲"我推翻了自己文档里的错误归因（$2a 跨平台 → 哈希与密码不匹配），并排查出同类测试数据问题（liucs/wangkj）"

---

> 🔥 **第一批 #5**：低成本防踩坑
### 38. 【部署】Dockerfile 双份不一致：模块目录残留 Alpine 旧版（2026-09-03 实测，待清理）

> **2026-09-03 新增（源自容器化部署企业级评估）**：项目存在**两份 Dockerfile**——compose 实际构建用 `deploy/docker/dockerfiles/mall-*.Dockerfile`（11 份，`FROM eclipse-temurin:21-jre` Debian 系 + 完整 JVM 参数 + SW agent，**已修复版**）；但**各模块目录下残留 11 份过期版**（`mall-sso/Dockerfile`、`mall-seckill/Dockerfile` 等，全部 `FROM eclipse-temurin:21-jre-alpine` + 无 JVM 参数 + 无 agent，**Alpine 事故旧版未删**）。

**风险**：任何人（包括未来的自己）照着模块目录 Dockerfile 手动 build → **重新踩 Q12 的坑**（Alpine musl 与 MD5 不兼容，全服务崩溃）；且缺 MaxMetaspaceSize/SW agent 参数 = 运行时行为与生产不一致。

**方案（低成本，建议尽快）**：
1. **删除模块目录 11 份过期 Dockerfile**（推荐——单一事实源 = `deploy/docker/dockerfiles/`，compose 已指向它）
2. 或保留但加注释头"⚠️ 生产勿用，以 deploy/docker/dockerfiles/ 为准"（防误用）
3. 验证：`docker compose config` 确认全部服务 dockerfile 指向 deploy 版

**面试价值**：能主动讲"我发现项目 Dockerfile 双份不一致（模块目录残留 Alpine 旧版），清理统一到单一事实源"——运维洁癖 + 防坑意识

---

> 🟢 **第三批**
### 39. 【CI/CD】镜像仓库 + 版本 tag + 构建流水线（2026-09-03 评估，待实施）

> **2026-09-03 新增（源自容器化部署企业级差距评估）**：当前构建部署全手动（本地 `mvn package` → scp jar → compose build），**无镜像仓库、无版本 tag、无 CI/CD 流水线、无镜像安全扫描**——"已完成表 #9"曾笼统记过 CI/CD，实际仓库无任何流水线配置（.github/workflows / Jenkinsfile 均不存在），部署不可复现、不可回滚。

**方案（P2，面试价值最高的规模化一层）**：
1. **镜像仓库 + tag**：构建产物推仓库（Docker Hub / 阿里云 ACR），`image: mall-sso:20260903-<sha>` 版本化 → 回滚 = 改 tag 重新拉起，不再"只有一个 jar 覆盖即丢"
2. **CI/CD 流水线**（GitHub Actions 即可，仓库已托管 GitHub）：push → 自动 mvn package → docker build → push 镜像 → 服务器 pull 重启（演示项目可只做到镜像构建推送，部署半自动）
3. **镜像安全扫描**：Trivy 扫基础镜像/依赖 CVE（可并入流水线）
4. 与 TODO #4（集群化）无冲突——先有版本化镜像，才有资格谈多副本灰度

**面试价值**：能讲"部署不可复现是隐患——我评估了镜像仓库+tag+CI 流水线，明白回滚 = 换 tag 而不是重传 jar"——把"单机 Compose"与"企业级交付"之间的差距讲清

---

### 40. 【部署】微服务自身 healthcheck 缺失（2026-09-03 审计，待实施）

> **2026-09-03 新增（源自容器化部署企业级差距评估）**：compose 中**中间件全带 healthcheck**（mysqladmin ping/redis-cli/curl），但 **11 个微服务均无 healthcheck**、无 `/actuator/health` 暴露 → `restart: on-failure` 只能拉起"进程崩溃"，**服务起但内部不健康（连不上 Nacos/DB/Redis）时不会被重启**，Docker 认为"活着"。

**方案（P2）**：
1. 引入 `spring-boot-starter-actuator`（mall-common 一处依赖，全部生效）+ 暴露 `/actuator/health`
2. compose 每个微服务加 `healthcheck: curl -f http://localhost:<port>/actuator/health`（依赖方可用 `depends_on: condition: service_healthy` 做服务级就绪等待，替代现在的"只等中间件"）
3. 注意：actuator 端点收窄（只开 health，避免暴露 env/beans 等敏感端点，呼应安全审计）

**面试价值**：能讲"进程活着 ≠ 服务健康——我补了 actuator healthcheck，让依赖方等服务真正就绪"——容器化可观测基础课

---

> 🟢 **第三批**
### 41. 【日志】日志聚合集中采集（Loki/ELK）（2026-09-03 评估，待实施）

> **2026-09-03 新增（源自容器化部署企业级差距评估）**：当前日志全部 stdout → `docker logs` 逐容器查看，**无集中采集/检索/关联**——排查跨服务问题（订单→支付→MQ）要 ssh 逐个容器翻日志。SkyWalking 有 trace 但无日志正文聚合（TODO #16 是应用层 traceId 落日志，与日志采集是两回事）。

**方案（P2，运维短板补齐，与 TODO #30 告警配套）**：
1. **Loki + Promtail**（轻量，单机 4C16G 友好，与 Prometheus 同生态）：Promtail 采集各容器 stdout → Loki 存储 → Grafana 按服务/时间/traceId 检索
2. 或 ELK（重，演示项目不推荐）
3. 保留策略：演示项目 7 天足够
4. 面试演示：出问题时"一条 traceId 捞出全链路日志"

**面试价值**：能讲"日志是最后一道可观测——我有 trace（SW）、有指标（计划 Prometheus）、缺日志聚合（Loki），补上是三件套闭环"

---

## 已完成（2026-08-04 归档）

| # | 事项 | 完成日期 | 备注 |
|----|------|---------|------|
| 1 | **ES 集群 Red 修复** | 2026-08-04 | 根因: IK 分词器丢失。安装 IK → 重试分片 → 副本清零 → cluster green || 2 | **JVM 内存调优** | 2026-08-04 | Seata 2G→512M, OAP 1G→512M, 11微服务加 DirectMemory/CodeCache 限制。系统内存 93%→71% |
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
