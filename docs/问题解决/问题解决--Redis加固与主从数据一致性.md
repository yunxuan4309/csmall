# 问题解决 — Redis 加固与主从数据一致性

> **性质**: **一类问题的合集** —— Redis 的**认证加固、持久化、内存上限**与**主从复制下的数据一致性**
> **覆盖**: 一批 **R1~R4**（加固）/ 二批 **#14**（主从切换防数据：P0 三层 + order_type + 方案Y + P1 对账）/ **#14-P2**（`min-replicas-to-write`）+ 实战踩坑两则（RDB→AOF 丢数据、conf 挂载权限）
> **来源**: 由 [[TODO第一批实现与原理]] §四、§9.2、§9.4 与 [[TODO第二批实现与原理]] §四 **切割**而来（2026-09-10；原文 §4.2「落库失败不静默」属消息可靠性类，已切至 [[问题解决--消息可靠性与毒消息治理]]）
> **关联（仅互链、不合并）**: [[问题解决--秒杀]] §三（秒杀库存变负数 = 本题「DB 条件扣减是最终防线」的前身）、[[问题解决--秒杀已购买标记与重复下单逻辑修复]]、[[Redis使用避坑指南]]、[[问题解决--生产安全加固与凭据治理]]（Redis 口令）
> **方案/状态**: [[Redis主从切换防数据问题方案]]（📦 已归档）、[[Redis配置加固与哨兵模式方案]]（📦 已归档）、[[TODO文件]] #14 / #14-P2 / #9

---

## 一、这一类问题的共性（★ 先看这张表）

**一句话本质**：**Redis 是"内存里的真相"，而内存没有承诺** —— 持久化不做会丢、内存不设顶会写停、异步复制会丢最后几笔写。本题三个问题串成一条因果链：

```
R1~R4 加固（认证/持久化/内存上限/自定义 conf）
   ↓ 但 AOF 首启 + 备份没出卷 → 踩了"数据全丢"的坑
#14 主从切换防数据（异步复制必然丢最后几笔写）
   ↓ 代码层无法 100% 消灭（本质），目标是让丢失【无害化】
#14-P2 min-replicas-to-write（让"没有从库确认"的主库拒绝写）
```

### 1.1 三个层次的问题，三种解法思路

| 层次 | 问题 | 解法思路 | 关键认知 |
|---|---|---|---|
| **① 单机加固** | 无密码 / 无 AOF / 无内存上限 / CONFIG SET 重启即失 | 用 **conf 固化**配置 + 挂载 | `maxmemory-policy` 用 `volatile-lru` 而非 `allkeys-lru` → **保护无 TTL 的永久购买标记** |
| **② 持久化迁移** | RDB→AOF 切换时**数据全丢** | **先在内存热开 AOF**（`CONFIG SET appendonly yes`），再切容器 | Redis 7 在 `appendonly yes` 但 AOF 目录不存在时**不回退加载 RDB**，直接以空库启动 |
| **③ 分布式一致性** | 异步复制 → 主挂瞬间丢最后几笔写（库存/购买标记/幂等锁）| **闸门/账本模型** + 让丢失无害化（三层防护 + 对账）| 秒杀成交资格在 **Redis 预扣时已确定**，DB 只是记账 |

### 1.2 本类最有价值的一个思维模型：**闸门与账本**

| 角色 | 承担者 | 职责 |
|---|---|---|
| **闸门** | Redis 预扣（`DECR`）| 决定"最多放行 N 单" —— **成交资格在此确定** |
| **账本** | MySQL 条件扣减（`seckill_stock >= qty`）| 真正记账；`rows==0` 即不成交 —— **最终防线** |
| **对账** | 定时任务（运行期轻量 + 凌晨全量）| 以**账本为唯一权威基准**修正闸门漂移 |

> 💡 想清了这个模型，才能理解为什么"付款前查剩余库存"是**错的**（无法区分"本单货被 MQ 扣掉(正常)"与"本单货没扣上(异常)"）→ 只能查"本单是否已成交"（方案Y）。

### 1.3 共性排查/验证手法

- **受控争用验证**：不是"看起来加了锁"，而是用 `Redis MONITOR` + 源 IP 把命令归属到实例，**观测真实争用**
- **以权威源对齐**：对账一律"以 DB 为准"，不做双向合并（避免互相污染）
- **容错瞬时差**：`|diff|==1` 连续 3 次同向才修 —— 规避"Redis 刚 DECR、MQ 未及时扣"的合法时间差

---

## 二、问题 1：R1~R4 Redis 加固（第一批最复杂）

> 原文编号：[[TODO第一批实现与原理]] §四（4.x）


### 4.1 实测现状（四连坑，2026-08-21 巡检）

| 编号 | 问题 | 实测 |
|---|---|---|
| R1 | 无密码 | `requirepass` 空；compose redis 无 environment；11 服务无 `SPRING_DATA_REDIS_PASSWORD` |
| R2 | 无 AOF | `appendonly no`，仅 RDB（丢 60s~15min 数据）；购买标记 reseckill 丢失 → 可能重复秒杀 |
| R3 | 无内存上限 | `maxmemory 0` + `noeviction` → 写满拒绝写入 = 功能停摆 |
| R4 | 无自定义 conf | 裸 `redis-server`，CONFIG SET 重启即失 |

### 4.2 redis-master.conf 设计（每行都能讲为什么）

```conf
bind 0.0.0.0            # 容器内监听（Docker 网络隔离，非裸奔）
port 6379
requirepass <密码>       # R1: 认证
masterauth <密码>        # 主从/哨兵互连用（✅ **现状：#9 已跨机落地** —— 主从 + 3 哨兵、故障转移演练选主 6.1s / 客户端 9.1s 自愈，见 [[TODO已完成]] §十三）
maxmemory 256mb         # R3: 内存硬顶（实测仅 21 键，256m 充裕）
maxmemory-policy volatile-lru  # 只淘汰带 TTL 的键 → 保护永久购买标记 reseckill
appendonly yes          # R2: AOF
appendfsync everysec    # 性能/安全平衡（秒杀标准做法）
dir /data               # 与数据卷 redis_data:/data 对应
# —— 以下 4 行为第三批跨机集群（2026-09-09）追加，本批执行时尚无 ——
replica-announce-ip 172.29.193.239    # 哨兵用宿主私网 IP 认主（容器 IP 重建即变，不可用）
replica-announce-port 6379
min-replicas-to-write 1               # #14-P2：无健康从库则拒写（仅主库开，新机从库【不开】）
min-replicas-max-lag 10
```

> ⚠️ **2026-09-10 复核（防止误读）**：`bind 0.0.0.0` **是容器内监听地址，至今未变**（Docker 网络隔离，非裸奔）。第三批改的是**宿主侧端口绑定**（`127.0.0.1:` → `172.29.193.239:`，见 [[问题解决--生产安全加固与凭据治理]] §三 ⚠️），**不是**这行 conf。服务器实测 conf 第 1 行仍为 `bind 0.0.0.0`，追加项在第 **11/12/15/16** 行（`replica-announce-ip`/`replica-announce-port`/`min-replicas-to-write`/`min-replicas-max-lag`）；`CONFIG GET bind` 返回 `0.0.0.0`。上表原始 10 行是第一批的成果，后 4 行属于第三批（[[Redis主从切换防数据问题方案]]，已归档）。

**⚠️ 挂载不加 `:ro`**：Redis 运行时会自动 REWRITE 自身 conf（记录角色等），只读挂载会导致故障切换失败/脑裂（为哨兵铺路时的关键坑）。

**healthcheck 必须带密码**（易漏坑）：加 requirepass 后 `redis-cli ping` 返回 NOAUTH → healthcheck 误判 unhealthy → compose 里用 `redis-cli -a "$$REDIS_PASSWORD" ping`（`$$` 是 compose 转义，容器内展开环境变量）。

### 4.3 原子切换（与 #25 同构的坑，可类比讲）

**不能只开服务端 requirepass**（微服务全连不上）；**不能只给客户端配密码**（服务端无密码时 AUTH 报 "no password is set"）。两条必须**同一维护窗口**：
1. .env 加 `REDIS_PASSWORD=<强随机>`
2. 部署 conf 到 `/data/csmall/redis/redis-master.conf`（替换占位符）
3. compose redis 加 command + 挂载 + environment + healthcheck
4. **11 个微服务 environment 全部补 `SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}`**
5. 重建 redis → 验证 → 重建 11 服务

各模块 prod yml 已预留 `password: ${REDIS_PASSWORD:}`（2026-09 已核实 10 个 yml 一致），所以**只需 compose 注入，无需改 Java/yml**——这是当时埋好的接口。

**更稳的降级技巧**：重建前先在线 `CONFIG SET appendonly yes` 热开 AOF（Redis 自动做首次 rewrite，数据无损零停机），conf 只做固化——避免"已有 RDB、AOF 首启"的边界问题。

### 4.4 为什么 maxmemory-policy 用 volatile-lru 不用 allkeys-lru（细节加分）

- `volatile-lru`: 只淘汰**带 TTL** 的键（缓存类）→ `mall:seckill:reseckill:*` 永久购买标记**无 TTL，永不被淘汰** ✅
- `allkeys-lru`: 全部键可淘汰 → 可能误删购买标记 → 用户重复秒杀
- `noeviction`(现状): 写满直接拒绝写入 → 秒杀库存/随机码写入失败 = 功能停摆而非降级

### 4.5 R1~R4 面试速答

**Q: AOF 和 RDB 怎么选？** A: RDB 快照（省空间、恢复快、最多丢最后一次快照后数据）；AOF 日志（每命令追加、最多丢 1 秒 everysec）。生产两者并存：RDB 快速恢复 + AOF 保数据。秒杀标记/库存是"丢了就出事"的数据 → 必须 AOF。

**Q: 加密码对秒杀性能有影响吗？** A: AUTH 是连接时一次性握手，Lettuce 连接池复用连接，QPS 路径无额外开销。

**Q: 为什么不直接上哨兵？** A: 第一批只做单机加固（R1~R4）；哨兵（#9）是学习/HA 实验，需先 R7 腾内存 + 单机哨兵不防整机宕机，边界要讲清。

---


---

## 三、问题 2：#14 Redis 主从切换防数据（P0 三层 + order_type 治本 + 方案Y + P1 对账）

> 原文编号：[[TODO第二批实现与原理]] §四（4.x）
> 📎 原文 **§4.2「P0 第3层：SeckillQueueConsumer 落库失败不静默」** 与本题同源但主体属**消息可靠性**（x-death 限次重试），已切至 [[问题解决--消息可靠性与毒消息治理]]；此处保留其在"三层防护"中的位置说明。


**秒杀链路**：`Redis 预扣（DECR 闸门，最多放行 N 单）→ MQ 异步 DB 条件扣减（seckill_stock>=qty，逐单）`。

**曾计划 P0"付款前查 DB 库存"**——推演发现**语义缺陷**：
- **缺陷1（误拦已成交）**：库存一致时（Redis=DB=N），放行 N 单 MQ 全部扣成功，**最后一件成交后 DB=0 是正常结果**，其用户付款时查剩余库存会被**误拦**。查"剩余库存"无法区分"本单货已被 MQ 扣掉（正常）"与"本单货没扣上（异常）"。
- **缺陷2（防不了真问题）**：要防的"Redis 多放导致超卖"，DB 条件扣减已兜底（rows==0 即不成交）；查剩余库存既不拦"该拦的"，又误拦"不该拦的"。

> **结论**：秒杀成交资格在 **Redis 预扣时已确定**，DB 只是记账（闸门/账本模型）。"付款前查剩余库存"不可正确实现 → **改为"查本单是否成交"**（方案Y）。


> 📎 **P0 第 3 层（消费者落库失败不静默 + x-death 限次重试）见 [[问题解决--消息可靠性与毒消息治理]]** —— 它与本题的"第 1 层（order_type 治本）/ 第 2 层（方案Y 支付前校验）"共同构成 #14 的 P0 三层防护。


**问题根源**：`markSeckillPurchased` / `clearSeckillOrdered`（支付/取消时写/清 reseckill 购买标记）**无条件执行**——普通订单也被误当秒杀单写 reseckill 标记（普通单不该影响秒杀限购）。

**修复**：`oms_order` 加 `order_type` 列（**Flyway V6**，历史数据默认 0=普通）：
- 秒杀入口 `SeckillServiceImpl` 置 `orderType=1`
- 普通入口 `OmsOrderController.addOrder` **强制 `orderType=0`**（防前端伪造秒杀单）
- `markSeckillPurchased` / `clearSeckillOrdered` **仅秒杀单执行**（`isSeckillOrder(order)` 判断）

> 💡 这也是方案Y的**前置**——只有明确标识秒杀单，才能在支付前对秒杀单校验成交状态。

### 4.4 方案Y：支付前校验"本单成交状态"（⭐ 替代放弃的"查库存"）✅

**新增 Dubbo 链路**：order 支付前查 `success` 表**本单是否已落库**（非查剩余库存）：

```java
// seckill 端新增对外服务
public interface IForOrderSeckillRecordService {
    boolean isSeckillSuccessRecorded(String orderSn);  // 查 success 表 order_sn 是否有记录
}

// order 端 payOrder 3.5 步：秒杀单(orderType=1)支付前校验
if (isSeckillOrder(order)) {
    validateSeckillRecordBeforePay(order);  // 未落库则拦截支付，Dubbo 异常保守放行
}
```

**为什么查"本单"而非"剩余库存"**：
- 成交单必有 success 记录 → **不误拦**已成交的最后一件（缺陷1 解决）
- 未落库单（Redis 放行但 DB 扣减失败 rows==0）→ 拦截支付（缺陷2 解决）

### 4.5 本地实测验证（⭐ 全链路证据）

| 验证项 | 结果 |
|---|---|
| iPad Pro 秒杀+支付全链路 | 订单 `order_type=1`、`state=3`(已支付)、`success` 有记录、Redis `reseckill:13:1` 标记已加 → **方案Y + reseckill 都正常** |
| 普通购买守卫生效 | order_type 强制 0，普通单不写 reseckill 标记 |

### 4.6 P1 对账任务：Redis vs DB 定时比对修正漂移 ✅

**问题**：Redis（闸门）与 DB（账本）异步，任何一侧失败都会漂移——Redis 偏大（扣少了→可能超卖）/ Redis 偏小（多放行→有货不能买）。

**设计（业界分层，调研佐证）**：对账**不能**只放凌晨、也**不能**只在高峰期——社区主流是**分层**：

| 层 | 调度 | 范围 | 阈值 |
|----|------|------|------|
| **A 运行期轻量纠偏** | `@Scheduled(fixedDelay=5min)` | 只处理已预热 sku（Redis 有 key） | \|diff\|≥2 直接修；\|diff\|=1 连续 3 次同向才修（容错瞬时差） |
| **B 凌晨全量校准** | `@Scheduled(cron="0 30 3 * * ?")` | 全量所有 sku + 补建缺失 key | \|diff\|≥1 即修（低峰无并发） |

**核心逻辑**：以 DB `seckill_sku.seckill_stock` 为**唯一权威基准**，修正 Redis `mall:seckill:sku:stock:{skuId}`：
- `redisValue > dbValue`（扣少了）→ 拉回 DB（防超卖）
- `redisValue < dbValue`（多放行）→ 回补到 DB（防有货不能买）
- **容错瞬时差**：Redis 刚 DECR、MQ 未及时扣 → 合法的时间差，运行期 ±1 不即修，凌晨全量才彻底对齐

> **关键权衡（诚实声明）**：运行期回补在 MQ 积压时可能短暂让 Redis 大于真实可成交数，但最终仍被 DB `seckill_stock>=qty` 条件扣减拦下（rows==0 不成交），**不会真超卖**——只是从"拦在 Redis"退化到"拦在 DB"（DB 条件扣减仍是最终防线）。

**实现文件**：`SeckillReconcileTask`（两个 @Scheduled，复用 `reconcileAll(online)`）；配置在 `application-test.yml`（`seckill.reconcile.*`，全默认值）。

### 4.7 P1 对账本地实测（⭐ 端到端证明）

| 验证项 | 结果 |
|---|---|
| 制造临时漂移 sku35 100→102（\|diff\|=2） | 运行期对账**自动修回 100**（证明 @Scheduled 加载并调度） |
| 全量比对 12 sku | **0 个不一致**（对一致数据零误改、对漂移正确修复） |
| 既有漂移样本 sku26（149/DB=148） | 已按 DB 修正为 148 |

### 4.8 疑惑点与原理（面试深挖）

| 疑惑/问题 | 结论 |
|---|---|
| 为什么付款前查"剩余库存"是错的？ | 无法区分"本单货被 MQ 扣掉(正常)" vs "本单货没扣上(异常)"——只剩"是否已成交"可判 |
| 为什么必须有 order_type？ | 无标识时普通订单也会被当秒杀单写 reseckill 标记，污染限购；且方案Y需要识别秒杀单 |
| 对账为什么分运行期+凌晨两层？ | 只放凌晨 → 白天漂移持续伤用户（看到有货买不到/超卖）；只放高峰期 → 全量比对有并发干扰。运行期轻量纠偏 + 凌晨彻底校准 |
| 对账"以 DB 为准"为什么不超卖？ | 即使回补让 Redis 短暂偏大，DB 条件扣减 `seckill_stock>=qty` 仍是最终防线（rows==0 不成交） |
| 对账会误伤合法预扣吗？ | 运行期 \|diff\|==1 连续 3 次才修，规避 Redis 刚 DECR/MQ 未扣的瞬时差 |
| 多实例并发对账？ | ✅ **已实现**：`SeckillReconcileTask` 已用 `RedisLockUtils`（`LOCK_ONLINE`/`LOCK_DAILY`）做**双实例互斥**（#4 定时任务锁思路）；原文「单机无此问题」为过期口径 |

### 4.9 面试话术

**主线**："#14 我处理 Redis 与 DB 库存一致性。首先我推翻了自己第一版的 P0 方案——原打算付款前查 DB 剩余库存，但推演发现它无法区分『本单货已被 MQ 扣掉』和『本单货没扣上』，会误拦已成交的最后一件。正确做法是查『本单是否已写入 success 表』（方案Y）——成交单必有记录，不误拦；未落库单拦截支付，防付钱没货。同时我补了 order_type 列（Flyway V6）标识秒杀/普通单，让 markSeckillPurchased 只在秒杀单执行、普通单强制 0 防伪造。第3层我把 SeckillQueueConsumer 的静默丢弃改成三兜底：失败留痕 + 已付款告警 + x-death 限次重试。最后是 P1 对账任务——业界分两层：运行期每 5 分钟轻量纠偏（容错瞬时差），凌晨全量校准（以 DB 为唯一基准彻底对齐）。我调研确认这是社区主流设计，不是拍脑袋。所有 P0 + P1 已本地端到端实测通过（对账确实把临时漂移修回 DB 值，12 个 sku 零误改）。"

---


---

## 四、问题 3：实战踩坑两则（持久化迁移丢数据 / conf 挂载权限）

> 原文编号：[[TODO第一批实现与原理]] §9.2、§9.4

### 踩坑 A：Redis 从 RDB 切 AOF 的数据丢失（⚠️ 最值得讲）


**现象**：compose 重建后 Redis DBSIZE=0（原 21 键丢失），数据卷 dump.rdb 从 8157 字节变 89 字节（空库）。

**根因链（三层）**：
1. **Redis 7 启动逻辑**：`appendonly yes` 但 AOF 目录不存在时，**不会回退加载已有 RDB**，直接以空库启动并创建空 AOF——日志里没有 "DB loaded from disk" 就是信号
2. **我的"备份"无效**：`redis-cli SAVE` 把数据写回**同一个数据卷**的 dump.rdb，并未复制到卷外 → 后续空库关闭时覆盖了原文件
3. **正确做法没执行**：文档 §4.3 写过"重建前先在线 `CONFIG SET appendonly yes` 热开 AOF"，但执行时直接重建容器，跳过了这步

**正确迁移姿势（教训固化）**：
```bash
# 数据还在内存时先热开 AOF（零停机，自动做首次 rewrite）
redis-cli CONFIG SET appendonly yes
# 确认 appendonlydir/ 生成后，再以 appendonly yes 配置重启容器
# 备份必须 docker cp 到卷外：docker cp csmall-redis:/data/dump.rdb /data/csmall/backup/
```

**影响与自愈**：丢失的是缓存数据（秒杀库存/随机码/购买标记/token 黑名单），非业务数据；DB（success 唯一键 + 条件扣减）兜底不超卖；库存/随机码由 `SeckillInitialJob` 每分钟自动重建。**演示项目可接受，商业化前必须按正确姿势迁移**。

**面试价值**：能讲"我实操 Redis RDB→AOF 切换踩了数据丢失的坑，根因是 Redis 7 不回退加载 RDB + 备份没出卷，正确做法是热开 AOF"——比背文档强得多。


### 踩坑 B：redis.conf 挂载权限（容器 redis 用户读不了 600 属主文件）


**现象**：redis 容器启动失败 `can't open config file: Permission denied`。

**根因**：conf 被 `chmod 600` 且属主是宿主机 ecs-user，容器内 redis 以 `redis` 用户（uid 999）运行 → 读不了。

**修复**：通过 docker root 方式改属主（无需宿主机 sudo）：
```bash
docker run --rm --user root -v /data/csmall/redis:/data/redis redis:7-alpine \
  sh -c "chown -R redis:redis /data/redis && chmod 600 /data/redis/redis-master.conf"
```
**经验**：挂载给容器的配置文件，属主必须是**容器内运行用户**（redis=999），不是宿主机用户；用 docker root 容器 chown 是无需 sudo 的正解。

---

**维护提示**: TODO 文件第一批条目**已全部完成并复核**（#38 → git 清理；#25/R7/#24/R1~R4 → 2026-09-07 执行；**Swap → 亦已执行**）。已移入 [[TODO已完成]]。

---

## 五、跨问题的共性认知与踩坑清单

| 认知 / 坑 | 说明 |
|---|---|
| ⭐ **单文件 bind mount 不能被 rename** | Redis/Sentinel 改写配置是「写 .tmp → rename」→ 单文件挂载必然 `Resource busy`。**哨兵必须挂目录**；主库 conf 只能"手工追加"（`CONFIG REWRITE` 无解）|
| ⭐ **挂载给容器的配置文件，属主必须是容器内运行用户** | Redis 容器内是 **uid 999**，宿主机属主是 `ecs-user` + `chmod 600` → 容器读不了。正解：`docker run --rm --user root ... chown redis:redis`（**无需宿主机 sudo**）|
| ⭐ **`volatile-lru` 保护永久键** | 只淘汰带 TTL 的缓存键；`mall:seckill:reseckill:*`（永久购买标记）**永不被淘汰** → 用 `allkeys-lru` 会误删导致重复秒杀 |
| ⚠️ **Redis 7 的 AOF 首启语义** | `appendonly yes` 但 AOF 目录不存在 → **不回退加载已有 RDB**，直接空库启动。日志里没有 `DB loaded from disk` 就是信号 |
| ⚠️ **备份必须"出卷"** | `redis-cli SAVE` 把数据写回**同一个数据卷**，等于没备份；必须 `docker cp` 到卷外 |
| ⚠️ **`min-replicas-to-write` 的适用前提是 ≥2 个从库** | 只有 1 个从库时，任何一台机器故障都会让写入停摆（本项目 2 台机器的**固有局限**，已评估并接受 + 保留"演练开关"）|
| ⚠️ **`min-replicas-to-write` 不要配在"可能被提升为主库"的从库上** | 该参数对被提升的从库同样生效，而故障转移后新主库手里没有从库 → **会拒绝所有写**，代价远大于收益 |
| 🔑 **对账为什么分两层** | 只放凌晨 → 白天漂移持续伤用户；只放高峰期 → 全量比对有并发干扰。**运行期轻量纠偏 + 凌晨彻底校准**（社区主流）|
| 🔑 **"长脚本阻塞主节点会导致哨兵误判"** | 短脚本（毫秒级）与哨兵可安全共存；需防范长 Lua 脚本 |

---

## 六、面试综述话术（贯穿本类）

> "Redis 这条线我做了三件事，而且是**因果相连**的。**第一件是加固**：发现生产 Redis 裸奔（无密码、无 AOF 只靠 RDB 最多丢 15 分钟数据、`maxmemory 0` 写满即拒写、没有自定义 conf 所以 `CONFIG SET` 重启就失）。加固时我把 `maxmemory-policy` 定为 `volatile-lru` 而不是 `allkeys-lru` —— 因为秒杀的**永久购买标记无 TTL，绝不能被淘汰**。**第二件是踩坑**：RDB 切 AOF 时数据全丢了，根因是 **Redis 7 在 `appendonly yes` 但 AOF 目录不存在时不回退加载 RDB**，加上我的"备份"写回了同一个数据卷等于没备。正解是**先在内存热开 AOF**（`CONFIG SET appendonly yes` 自动做首次 rewrite）再切容器，备份必须 `docker cp` 出卷。**第三件是分布式一致性**：主从是异步复制，主挂瞬间会丢最后几笔写（库存 DECR、购买标记、幂等锁）。代码层无法 100% 消灭这个本质，我的目标是**让丢失无害化**——想清楚"秒杀成交资格在 Redis 预扣时已确定、DB 只是记账"这个**闸门/账本模型**后，我推翻了原方案'付款前查剩余库存'（它无法区分'本单货已被 MQ 扣掉'和'本单货没扣上'，会误拦已成交的最后一件），改为**查本单是否已写入 success 表**；补 `order_type` 列区分秒杀/普通单；再加**两层对账**（运行期 5 分钟轻量纠偏、凌晨 3:30 全量以 DB 为唯一基准校准）。最后是配置层的 `min-replicas-to-write`：让"没有从库确认"的主库**拒绝写**，把静默丢数据变成明确报错 —— 但我也发现它的**前提是至少 2 个从库**，我只有 1 个，所以任何一台机器故障都会让写入停摆；这个局限我评估后选择接受，并保留了'演练开关'（停新机前先设 0）。"

---

## 七、关联文档

- **原文**：[[TODO第一批实现与原理]] §四/§9.2/§9.4、[[TODO第二批实现与原理]] §四
- **同类历史记录（仅互链）**：[[问题解决--秒杀]] §三（超卖 / DB 条件扣减）、[[问题解决--秒杀已购买标记与重复下单逻辑修复]]
- **相关**：[[问题解决--消息可靠性与毒消息治理]]（P0 第3层）、[[问题解决--生产安全加固与凭据治理]]（Redis 口令）、[[Redis使用避坑指南]]、[[问题解决--容器构建与编排卫生]]（单文件挂载约束）
- **方案/状态**：[[TODO文件]] #14 / #14-P2 / #9