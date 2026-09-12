# CoolShark 中 / 低优先级待办（暂缓 · 仅评估 · 未来计划）

> **创建**：2026-09-11（自 [[TODO文件]] **按优先级拆分**）
> **定位**：收纳 **🟡 中优先级** 与 **⏸️ 暂缓 / 仅评估 / 未来计划** 的登记与正文 —— 即"**知道、讲得清，但当前不安排**"的事。
> **与其它文件的关系**：**高优先级状态源** = [[TODO文件]] · **已完成明细 + 归档区** = [[TODO已完成]] · **方案 / 评估文档登记** = [[文档索引]]。
> **⚠️ 编号与 [[TODO文件]] 共用同一套**（绝不重编号）；**同一编号只在一处有正文** —— 高优先级条目正文在 [[TODO文件]]。
> **为什么拆出来**：这些条目占原文约 **六成篇幅**，却都是"暂不动手"的事；混在一起会淹没真正要做的事（`#34` 那种"查不到"的问题也由此而来）。

---

## 📌 中 / 低优先级编号登记表（一行查一条）

> **用途**：查"某个中 / 低优先级编号是什么、什么状态、正文在哪"。**高优先级编号**（待执行 / 风险 / 安全 / 可实施）→ 见 [[TODO文件]] 顶部登记表。

### B. 🟡 中优先级（待做）

| 编号 | 一句话 | 状态 | 正文 |
|---|---|---|---|
| **#1** | 后台**相册 / 图片管理界面**（表与 Controller 已就绪，缺前端页） | 🟡 待做 | §1 |
| **#3** | **秒杀活动管理后台 + 场次维度购买标记**（现全靠手工改库；`reseckill` 永久标记导致同 SKU 不可复购） | 🟡 待做 | §3 |
| **#4-配置中心** | 集群化**已完成**（跨机双实例）；**配置中心迁移未做**（评估结论：暂缓） | 🟡 暂缓 | §4 · [[配置中心迁移方案]] |

### C. ⏸️ 暂缓 / 仅评估（**不动手，面试讲认知**）

| 编号 | 一句话 | 状态 | 正文 |
|---|---|---|---|
| **#2** | 图片打包进 JAR 浪费空间 | ⏸️ 低 | §2 |
| **#3** | RedisBloom 模块安装（秒杀现用 Redis Set 替代） | ⏸️ 低 | §3 |
| **#4** | 【支付】微信支付模拟策略 | ⏸️ 低 | §4 |
| **#5** | 【支付】真实支付宝支付（现 `simulated: true`） | ⏸️ 低 | §5 |
| **#7** | HTTPS/SSL（阻塞链：域名 + ICP 备案 + 预算） | ⏸️ 阻塞 | §7 |
| **#10** | Redis Lua 脚本引入评估 | ⏸️ 仅评估 | §10 |
| **#11** | ZSET 延迟队列（超时关单升级方向） | ⏸️ 仅计划 | §11 |
| **#12** | Redis 集群适配 / 配置中心 / 多副本评估 | ⏸️ 仅评估 | §12 |
| **#15** | K8s 实操（k3s 学习用，暂不部署） | ⏸️ 学习 | §15 |
| **#16** | TraceId 落日志（SW logback 集成） | ⏸️ 待实施 | §16 |
| **#17** | 认证安全企业级升级（登录限流 / 密码策略 / 多算法编码器） | ⏸️ 待实施 | §17 |
| **#18** | 购物车 Redis 缓存演进（结论：不做） | ⏸️ 暂不实施 | §18 |
| **#19** | 订单价格 / 库存服务端校验补强 | ⏸️ 暂缓 | §19 |
| **#20** | 死权限码清理（冒号式权限码 0 引用） | ⏸️ 暂缓 | §20 |
| **#21** | mall-front BFF 补强（缓存 / 并行 / 扩容） | ⏸️ 暂不实施 | §21 |
| **#26** | 容器网络隔离（应用网 / 数据网分网段） | ⏸️ 待实施 | §26 |
| **#27** | KMS 密钥管理 | ⏸️ 仅认知 | §27 |
| **#28** | 容器 JRE 无诊断工具（换 JDK 镜像 / Arthas / JFR） | ⏸️ 待实施 | §28 |
| **#30** | Prometheus + Alertmanager 主动告警 | ⏸️ 待实施 | §30 |
| **#35** | 统一 Jackson（替换 fastjson） | ⏸️ 待实施 | §35 |
| **#37** | 测试账号密码哈希验证清理（liucs/wangkj） | ⏸️ 待验证 | §37 |
| **#39** | CI/CD 镜像仓库 + 版本 tag + 流水线 | ⏸️ 待实施 | §39 |
| **#41** | 日志聚合（Loki / ELK） | ⏸️ 待实施 | §41 |
| **#43** | 合并重复的 `BindException` 全局异常处理器 | ⏸️ 低 | §43 |
| **#49** | **商品集群：验证 Dubbo 层负载均衡**（唯一有新增验证价值的候选） | 📌 未来计划 | §49 |
| **#60** | Spring AI 引入评估（结论：**暂不引入**，前置 = Boot 全站升级） | ⏸️ 仅评估 | §60 |
| **#67** | **= #48 剩余 6 项**（录像 · 正式造数 · **第二层 AI 并发压测** · `--with-seckill` · Redis 精确清理 · 基线比对） | ⏳ **未开始** | §67 · [[AI并发测试方案]] |
| **R5** | 6 服务 Druid → HikariCP（文档已勘误；真正切换暂缓） | ⏸️ 暂缓 | R5 |
| **R6** | 文档勘误：Sentinel 端口映射（`8090→8858`） | 🟢 待改 | R6 |
| **R8** | Redis 大键定期巡检（月度 + 大版本前） | 🟢 定期 | R8 |

---

## 🟡 中优先级（正文）

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

**🔴 2026-09-09 更新（跨机版，取代上文"单机 2 实例"形态）**：
- **拓扑改为跨机**：老机 `mall-seckill`(10007) + 新机 `mall-seckill-2`(10017，2C8G 同 VPC) → 从"单机演示级"升级为**跨机真实集群**（老机宕机时副本仍可服务）
- **内存压力解除**：副本不再挤老机（老机 available ~3G 不变），新机 8G 余量充足 → 原"单机内存可行"测算作废，改为"跨机零内存冲突"
- **配置中心迁移**：本阶段**暂不做**（跨机副本配置仍走 compose env 注入，理由见 #12 评估：变更点已集中、无动态刷新场景）
- **实施路径 = 阶段 0~4**（见[[TODO已完成]] §二十·一内的「🚀 第三批执行清单」）；**唯一代码改造点仍是定时任务分布式锁**（`MessageRetryTask` + `SeckillReconcileTask`）
- **新增前置**：老机 MySQL/Redis 端口放行（3306/6379 绑 `127.0.0.1` → 内网 IP）+ 副本注册 IP 覆盖（`SPRING_CLOUD_NACOS_DISCOVERY_IP`）
- 📄 方案细节/答疑/演练剧本见 [[跨机集群方案-讨论纪要-2026-09-09]]

**评估结论（关键）**：
- 服务器 available 仅 2.1G；**单秒杀副本（~650M）+ R7 释放（~560M）→ 加完后剩 ~2.0G ✅ 内存可行**（⚠️ 2026-09-09 起副本改跑新机，此测算仅作历史参考）
- 选秒杀理由：演示价值最高（秒杀压测看负载均衡 + 限流规则热更新），且"定时任务分布式锁"是面试亮点；商品/订单收益/改动比更低
- 多实例改造点：仅 seckill 有定时任务（`MessageRetryTask` 每 5s）→ 需 Redis 分布式锁；Sentinel 限流按实例统计（副本后集群 QPS 翻倍，要讲清）；Seata/MQ/Redis 天然多实例安全
- **单机集群 = 演示级 HA，机器宕机所有实例一起挂**，面试只讲"验证机制"不讲"高可用"
  > 🔴 **2026-09-09 修正**：跨机版（新机副本）**可防单机宕机**——老机挂时新机副本仍能服务、Redis 主挂时哨兵切到新机从；但仍**不防地域灾难**，且其余 19 容器仍是老机单实例 → 话术仍是"验证机制"而非"高可用架构"。

📄 **原完整方案（内存测算/三模块价值对比/两阶段步骤/风险与回滚/执行清单）已归档** → [[集群化与配置中心迁移方案]]（2026-09-11 移入 `docs/归档/`：其阶段 A0「Nacos 认证」与阶段 B「集群化」**均已执行完毕**，且形态由"单机 2 实例"升级为**跨机双实例**）
📄 **唯一未做部分的方案**（配置中心迁移）**仍活着** → **[[配置中心迁移方案]]**（边界表 + 7 步 + 2 个坑；当前暂缓，理由见 #12）

---

## 🟢 低优先级 / 暂缓 / 仅评估（正文）

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



### 7. HTTPS/SSL 配置 ⏸️

```
阻塞链: HTTPS → SSL证书 → 域名 → ICP备案 → 服务器续费≥3个月
                                                    ↑
                                            卡在预算（8月29日到期）
```


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

> 🔗 **与 #4 阶段0 分布式锁的关系（2026-09-09 补充）**：`MessageRetryTask` 目前是"每 5s 扫表 + 发 MQ"，集群化后已用 Redis 分布式锁（`RedisLockUtils`）防双实例重复投递。**若将来按本项迁移到 ZSET 延迟队列**（`ZREM` 返回 1 者消费，天然防重复），该任务的锁即可一并移除——即锁是"DB 轮询方案"的配套产物，换队列后配套消失。设计上我们把锁**包在任务外层**、业务代码零改动，所以移除成本很低。详见 [[TODO第三批实现与原理]] §4.7 / §4.8。

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

### 30. 【监控】Prometheus + Alertmanager 主动告警（2026-08-28 记录，待实施）

> **2026-08-28 记录（源自 07 Q10）**：07 Q10 提到"改进方向是 Prometheus + Alertmanager"但 TODO 从未记录——**文档缺口补上**。**"resource 挂 2 天才发现"的根治手段**：restart 只能拉起崩溃，静默挂/循环崩靠人工发现——主动告警是唯一根治。

**方案（P2，运维最短板）**：
1. Prometheus 采集：容器指标（cAdvisor/Docker exporter）+ 服务指标（actuator）+ JVM 指标
2. 告警规则：容器挂 / CPU 高 / 内存高 / 接口错误率 / 磁盘
3. 通知：Alertmanager → 邮件 / 钉钉 webhook
4. 与 R8 巡检、Q8/Q10 教训呼应

**面试价值**：能讲"被动监控（SkyWalking/Sentinel/docker）和主动告警（Prometheus）的区别，告警是根治静默故障"

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


### 37. 【数据】测试账号密码哈希验证清理（liucs/wangkj + 造数规范）（2026-09-03 记录，待验证）

> **2026-09-03 新增（源自 10-Q11 BCrypt 归因纠错实验）**：服务器实验证明 admin 登录失败的"跨平台 $2a/$2b"归因错误——真因是**测试数据哈希与声称密码不匹配**。实验时发现 **liucs/wangkj 用户仍存旧 `$2a$10$N.zmdr...` 哈希**（与 admin 当初失败的同一个），若其密码声称 123456 同样登录失败。

**待验证/处理**：
1. 用 bcrypt 验证 liucs/wangkj 的哈希到底匹配什么密码（或确认它们实际用什么密码登录）
2. 若密码与测试预期不符 → 重置为正确哈希（目标平台生成 $2b）
3. 顺带排查其他表是否有"手填未验证哈希"的账号
4. **固化规范**：造测试数据的密码哈希必须 `bcrypt.checkpw(密码, 哈希)` 验证过再入库，禁止手工复制哈希（三用户同哈希 = 复制粘贴特征）

**面试价值**：能讲"我推翻了自己文档里的错误归因（$2a 跨平台 → 哈希与密码不匹配），并排查出同类测试数据问题（liucs/wangkj）"

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


### 41. 【日志】日志聚合集中采集（Loki/ELK）（2026-09-03 评估，待实施）

> **2026-09-03 新增（源自容器化部署企业级差距评估）**：当前日志全部 stdout → `docker logs` 逐容器查看，**无集中采集/检索/关联**——排查跨服务问题（订单→支付→MQ）要 ssh 逐个容器翻日志。SkyWalking 有 trace 但无日志正文聚合（TODO #16 是应用层 traceId 落日志，与日志采集是两回事）。

**方案（P2，运维短板补齐，与 TODO #30 告警配套）**：
1. **Loki + Promtail**（轻量，单机 4C16G 友好，与 Prometheus 同生态）：Promtail 采集各容器 stdout → Loki 存储 → Grafana 按服务/时间/traceId 检索
2. 或 ELK（重，演示项目不推荐）
3. 保留策略：演示项目 7 天足够
4. 面试演示：出问题时"一条 traceId 捞出全链路日志"

**面试价值**：能讲"日志是最后一道可观测——我有 trace（SW）、有指标（计划 Prometheus）、缺日志聚合（Loki），补上是三件套闭环"

---


### 43. 【整洁】合并重复的 BindException 全局异常处理器（2026-09-07 记录，低优先级）

> **2026-09-07 记录（源自 #23 校验审计）**：项目存在**两个 @RestControllerAdvice 都声明了逻辑相同的 `BindException` handler**：
> - `GlobalControllerExceptionHandler`（mall-common/.../exception/handler/）
> - `BindExceptionHandler`（mall-common/.../validation/handler/）
>
> **冗余但不冲突**：Spring 对跨 advice 的同异常声明按注册顺序取第一个匹配，不 Ambiguous（生产旧 jar 双 advice 共存运行多时无歧义日志实证）。
>
> **合并方案（低优先级，整洁项）**：
> 1. 把 `BindExceptionHandler.handleBindException` 并入 `GlobalControllerExceptionHandler`（逻辑已相同，保留一份即可）
> 2. 删除旧 advice `BindExceptionHandler.java`
> 3. 回归：表单绑定（@ModelAttribute）/ @RequestBody 校验失败仍返回 400
>
> **面试价值**：能讲"我在校验审计中发现两个全局 advice 冗余声明同一异常 handler——跨 advice 不冲突（按序取一），但整洁上应合并"，展示对 Spring 异常解析机制的准确认知。

---

## 📋 其它第三批条目正文（未来计划 / 观察项 / 已解决 / 仅评估）

### 49. 商品集群：验证 Dubbo 层负载均衡（未来计划，秒杀集群稳定后）

 **2026-09-09 记录（用户确认列入未来计划）**：秒杀集群（#4）验证的是 gateway HTTP `lb://` 负载均衡；**商品集群能额外验证 Dubbo provider 层负载均衡 + 故障剔除**（消费者 order/seckill/ai 按接口自动均衡）——与秒杀是**不同类型**的负载均衡，唯一有新增验证价值的候选。**触发条件**：① 秒杀集群稳定运行 ② 时间允许。商品无定时任务、无 MQ 消费者，改造点少（仅副本 compose + 注册 IP），内存可行（+~600M，8G 新机余量充足）。订单/其余模块不建议集群（Seata 事务核心回归面大、低流量无可见收益）→ 面试用"按需集群"叙事。评估详见 [[跨机集群方案-讨论纪要-2026-09-09]] 


### 50. 哨兵认证/网络收敛（观察项，非必修）

 ℹ️ **观察项（2026-09-09 巡检发现，经 Redis 官方文档复核后降级）**：新机哨兵监听 `0.0.0.0:26379-26381` 且无 `requirepass`。**但 Redis 官方的哨兵安全模型是"网络隔离"而非"认证"**——官方 `sentinel.conf` 原文：*"protected mode is disabled in sentinel mode… Make sure the sentinel instance is protected from the outside world **via firewalling or other means**"*。**本项目判断：当前信任边界成立 → 不做**（公网实测 blocked，仅 80 通；同 SG 只有自己的两台机器；新机同机仅 4 个容器）。**若边界变化**（多租户 VPC / 临时放开 SG / 新机加入其他实例）再提升优先级：加 `requirepass`（三哨兵同一密码，**官方说明会自动互相认证**）+ 11 客户端配 `spring.data.redis.sentinel.password`。⚠️ 两个无效手段：`bind` 私网 IP 挡不住公网（公网 IP 是 NAT、不在网卡上，闸门只有 SG）；`protected-mode no` 本就是哨兵默认值。→ 见 [[跨机集群实施执行清单-2026-09-09]] §6.5.8 


### 52. 镜像获取路径（运维事实，已解决）

 ℹ️ **2026-09-09 实测记录（下次直接照做）**：① **两台都直连不了 Docker Hub**（新机 `dial tcp 128.242.240.61:443: i/o timeout`）；② 老机 `/etc/docker/daemon.json` 配的是**第三方**镜像站，其中 **`dockerpull.org` 已失效**（8–10s 超时）→ **普通 `docker pull` 会先试死站而卡死**（实测 90s 无进展）；③ **可用办法（无需 sudo、无需改 daemon.json）**：镜像名前加镜像站前缀 `docker.1ms.run/<image>`，两台均实测成功；仓库 Dockerfile 写的是 `FROM eclipse-temurin:21-jre`，拉下来后 `docker tag` 成标准名即可；④ **新机原本没有任何 Java 环境**（宿主机无 `java`、唯一镜像 `redis:7-alpine` 里也没有），且老机的 `eclipse-temurin:21-jre` 曾被清理（`docker history` 显示 `<missing>`、无悬空镜像）→ **2026-09-09 已把基础镜像重新拉回两台并打标准名**（Java 21.0.12 LTS）。→ 见 [[跨机集群实施执行清单-2026-09-09]] §7.1

**🟢 决策（2026-09-09，用户确认）：暂不给新机配 daemon.json 镜像加速器**——前缀法已够用；配它需 sudo + **重启 docker**（新机 4 容器 `on-failure` 不会自动恢复、老机 20/21 不恢复见 #51），且会让**所有** pull 都走第三方镜像站。**触发条件**：等 #51 修完（restart 改 `unless-stopped`）+ 有维护窗口时，再配阿里云官方加速器（地址在控制台「容器镜像服务 → 镜像加速器」获取）。 


### 67. ⏳ #48 的剩余部分（第一层 + 工具链已完成并归档 → [[TODO已完成]] §十九）

 ⏳ **2026-09-11 登记（#48 拆分而来）**：**#48 是两层方案，第二层「AI 并发压测」完全未开始**，剩余 6 项：

① **录像**（脚本 / runbook / [[演示录像操作手册]] **均已就绪且实测过**，用户决定之后录；§6.4 三段式）

② **正式造数**：每天 1000 行为 × 1~2 天（**目前只跑过 50 次动作的校准**）

③ 🟢 **第二层 AI 并发压测（首轮已完成 · 2026-09-12）**：✅ mock 已写（自测 14/14）· ✅ compose 透传 D1 + override · ✅ Nacos 热改 5→200 · ✅ 800 用户池 · ✅ 阶梯 20/50/100 **两条链路已压**（**结果见 [[AI并发测试方案]] §十**；过程中修掉 **#62**——并发下 ~50% 500）。原计划细节：`mock_llm.py` **未写**（需 `ThreadingHTTPServer` + **tool_calls 工具轮**）· compose 需加 `AI_API_BASE_URL` 透传 + override 指向 mock · Nacos 规则**备份 → 热改 `ai-chat` 阈值 → 恢复** · 构造 **N ≥ 120** 个模拟用户 token · 阶梯 20→50→100 **各压 `AI_AGENT_ENABLED=false/true` 两条链路**（产出"**Agent 双轮 vs 流水线单轮**"的承载代价）

④ `--with-seckill`（秒杀动作，**目前只做预检**）

⑤ **Redis 精确清理**（按登记 user id 删 `reseckill`/`ordered`/`orderLock`；**当前只打印告警、删除代码未写**）

⑥ `sim_baseline` 基线比对（DDL 已有、**脚本未写入**）

📌 **相关**：**#65**（普通订单库存扣减 MQ 链路失效）会直接影响"正式造数"的数据真实性；**#66** 已修复（面板可见性）

📄 **方案**：**[[AI并发测试方案]]**（③ 全册 249 行）+ [[Python模拟数据与数据隔离方案]] §五 执行顺序（① ② ④ ⑤ ⑥）· [[演示录像操作手册]] 


### 60. Spring AI 引入评估（结论：暂不引入，前置 = Boot 全站升级）

 🟡 **P3（2026-09-10 评估，源自"模型配置能否适应更名/下架"的架构提问）**：

**诉求**：模型配置要能适应**未来模型更名 / 下架**（改名不改代码、不重编译）。

**现状实测**：**半可配** —— Key / API 地址已占位符化 ✅；但**模型名是字面量**（`chat-model: deepseek-chat`）+ **2 处硬编码在代码里**（`ChatServiceImpl:316` / `:367`）；prod **未覆盖** `cooxiao.ai` 段 → 生产模型名来自 jar 内 yml → **换模型必须重编译**。

**🟢 决策（2026-09-10）：本次不引入 Spring AI**，改为"**手写 + 模型配置可配化**"落地（随 #58 Step 1，第 1+2 层：占位符外置 + 能力档位层 `models`/`tasks`，代码永不出现模型名）→ 改名 = 改 `.env` + 重建 `mall-ai` 一个容器。

🔴 **不引入的硬理由**：Spring AI 官方支持代际为 **Boot 3.4.x→1.0.x / 3.5.x→1.1.x / 4.x→2.x**，**Boot 3.3 及更早不在支持范围** → 本项目 **Boot 3.2.5 低于支持下限**，接入必须先升 Boot ≥3.4，连带 **Spring Cloud 2023.0.3→2024.0.x + SCA 2023.0.1.2→新一代**，**11 个服务 + 跨机集群全量回归** = **全站框架升级项目**（非 AI 功能项目）。

**未来口子**：第 2 层"档位 + 任务映射"是**框架无关抽象**，将来迁到 Spring AI `ChatClient`/`ChatOptions` 可直接映射，本次投入不浪费。Spring AI 保留为 #32 的 **P2（框架化）**，触发条件 = 有 Boot 升级窗口。

📄 评估详见 [[AI模型名停用风险与thinking参数改造方案]] §4.5 / §4.6 / **§4.7（升级 Boot 影响面实测：Boot 版本在根 pom → 13 个模块全体继承，"只影响 AI 模块"不成立；连带 Spring Cloud 2023.0.3→2024.0.x + SCA 同代际 + springdoc/MP/Dubbo 等全套迁移 + 两台机器 12 个服务回归）** 

---

## ⏸️ 明确暂缓/仅评估（不实现，面试讲认知即可）

| 编号 | 事项 | 说明 |
|------|------|------|
| #10/#11/#12/#18/#21/#27 | Lua/ZSET/Redis集群/购物车缓存/BFF补强/KMS | 均为"未来商业化/规模化"评估，当前不做 |
| #3/#19/#20 | 秒杀活动管理/价格库存校验/死权限码 | P2，演示场景影响小，面试讲清即可 |
| ~~#42~~ | ~~跨机"真集群"演示（Redis HA+秒杀双实例）~~ | ✅ **已实施完成**（跨机集群 #4/#9/#14-P2，2026-09-09）→ 原文已移至[[TODO已完成]] §二十·一 |
| #7 | HTTPS/SSL | 阻塞链=域名+ICP备案+预算，外部条件限制 |
| 低区 #2/#3/#4/#5 | 图片打包/RedisBloom/微信支付/支付宝 | 见低优先级区原文（~~低区 #6 压测演示~~ 2026-09-07 已归档 → 见文末归档区） |


---

## ⏸️ 已确认问题（演示项目暂缓修复，2026-08-21 巡检确认）

> **来源文档**：[[服务器巡检与待修复问题清单-2026-08-04]]（08-04 全量巡检）+ 2026-08-21 Redis 专项巡检复测。

> **背景**：2026-08-21 对生产服务器（8.156.77.197）做 Redis 专项巡检 + 配置事实核查。以下问题全部经服务器实测确认（docker inspect / redis-cli CONFIG GET / 与本地 `deploy/docker/docker-compose.yml` 比对）。**当前为演示项目，暂不修复**，待有空时按方案处理。

### R5. 【连接池】文档称"已全面替换 HikariCP"，实际生产 6 个模块仍是 Druid 🟡

**现状（已核实）**：`mall-sso`（3 个数据源）/`mall-product`/`mall-order`/`mall-seckill`/`mall-ums`/`mall-ams` 的 `application-prod.yml` 均为 `type: com.alibaba.druid.pool.DruidDataSource`，且各 webapi pom 显式引入 druid 1.2.24（`mall-ums-service`/`mall-product-service` 的 pom 反而排除 HikariCP）；仅 `mall-resource` 无 druid 依赖（用 Spring Boot 默认 HikariCP）。

**矛盾点**：`docs/项目上下文文档.md` §6.1(8)/§8.12 记载"Druid 曾致上传线程挂死、已全面替换为 HikariCP"——与事实不符，文档需更正。

**影响**：文档与事实脱节，若 Druid 历史问题（上传线程挂死）在生产复现，排查方向会被误导。

**解决方案**（二选一，建议先 a 后 b）：
- a) **✅ 已执行（2026-08-17）**：更正文档——`docs/项目上下文文档.md` §6.1(8)/§8.12 已如实记录"仅 mall-resource 切换 HikariCP，其余 6 服务仍 Druid 稳定运行"；`docs/面试准备/03-数据库设计.md` Q7 已同步更新
- b) **⏸️ 真正切换（暂缓）**：6 个模块 prod yml 删除 `type:` 行（回落 Spring Boot 默认 HikariCP）+ webapi pom 移除 druid 依赖 + service pom 移除 HikariCP exclusion → 需全量回归 + 重启窗口，演示项目暂缓

📄 **详细执行方案（含 Step 0~7 步骤 / HikariCP exclusion 陷阱 / SSO 三数据源注意事项 / 回滚方案 / 监控说明）见 [[连接池统一HikariCP方案]]**

### R6. 【文档勘误】Sentinel 端口映射描述错误 🟢

**现状（已核实）**：compose 为 `8090:8858` + `JAVA_OPTS="-Dserver.port=8858"`，服务器实际 `0.0.0.0:8090->8858/tcp`（容器内 8858）；`docs/运维/阿里云ECS服务器情况.md` §4.1 表格误写 "8090→8080"。

**解决方案**：更正 `docs/运维/阿里云ECS服务器情况.md` 为 "8090→8858（容器内 8858）"。本文件「中 / 低优先级登记表」与 [[TODO文件]] 中提到的 "Sentinel 控制台（8090 端口）" 指宿主机端口，无需改动。

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

**关联**：[[TODO文件]]（高优先级状态源）· [[TODO已完成]]（已完成明细 + §二十 归档区）· [[文档索引]]（方案 / 评估文档登记）· [[项目上下文文档]] · [[前端TODO]]（前端条目 `#F` 体系）

**维护提示**：本文件收纳"暂不安排"的事。若某条要**动手**了 → 从本文件**提升**到 [[TODO文件]] 登记表 A 并移正文；完成后迁 [[TODO已完成]]。
