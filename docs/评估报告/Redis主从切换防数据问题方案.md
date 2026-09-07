# Redis 主从切换防数据问题方案（评估+计划，待实施）

> **创建**: 2026-08-26
> **状态**: 📋 已定稿待实施（用户确认：之后处理）
> **背景**: Redis 主从复制是异步的，主节点挂了瞬间最后几笔写可能未同步，
> 哨兵切换后这些写丢失。对秒杀场景的影响：库存 DECR 丢（Redis 偏大）、
> 购买标记 reseckill 丢（可能重复购买）、幂等锁丢（可能重复提交）。
> **关联**: [[Redis配置加固与哨兵模式方案]]（R1~R4，主从+哨兵落地）、
> [[Sentinel能力补充计划]]、[[集群化与配置中心迁移方案]]

---

## 一、核心认知（为什么无法 100% 避免）

```
主从复制 = 异步（主写 → 从同步，不等待）
主节点挂瞬间：最后几笔写可能没同步 → 切换后丢失
→ 这是异步复制的【本质】，配置层只能减少概率，不能消灭
→ 代码层的目标：让丢失【无害化】——即使丢了，业务也不出错
```

**核心哲学**：不追求 Redis 不丢，追求"丢了业务也不错"。

---

## 二、五层方案（从架构到配置）

### 第 1 层：架构层——Redis=闸门、DB=账本（✅ 已做，保持）

```
原则：关键业务数据 DB 必有副本，Redis 只是加速器不是唯一真相源

你的秒杀已是这个思路：
  Redis 预扣库存（闸门：判断谁抢到）→ DECR 原子
  DB 条件扣减（账本：最终谁成交）→ seckill_stock >= qty
  → Redis 数据全丢，DB 还是对的（不超卖）

兜底对照：
  库存 DECR 丢 → DB 条件扣减（不超卖）✅
  购买标记 reseckill 丢 → success 表唯一键 uk_sku_user ✅
  幂等锁丢 → 业务幂等（订单号唯一）✅
  随机码丢 → 定时任务重新预热 ✅
```

### 第 2 层：代码层——付款前校验 DB 库存（❌ 要补，最关键）

**现状问题**：秒杀预扣成功 → 用户付款 → MQ 落库扣 DB 失败
→ 用户付了钱但订单无效（最严重事故）

**修复**：付款动作前校验 DB 真实库存（不是 Redis！）：
```java
// 支付接口（现在缺这个校验）：
@PostMapping("/pay")
public JsonResult pay(@RequestBody PayOrderDTO dto) {
    // ✅ 付款前校验 DB 库存（不是 Redis）
    Integer dbStock = seckillSkuMapper.getStockBySkuId(dto.getSkuId());
    if (dbStock <= 0) {
        throw new CoolSharkServiceException("商品库存不足，无法支付");
    }
    paymentService.pay(dto);  // 库存够才允许支付
}
```
**为什么查 DB 不查 Redis**：Redis 可能虚报（主从丢 DECR 偏大），
DB 永远准确（条件扣减保证）→ 付款前查 DB，不够就拦，
把"付款后补救"变成"付款前拦截"。
**性能**：付款频率远低于下单，付款时查一次 DB 完全可接受。

### 第 3 层：落库失败不静默（❌ 要补，改 SeckillQueueConsumer）

**现状问题**：`SeckillQueueConsumer` 库存不足时 `basicAck` 直接丢弃
（静默），用户已付款场景无法发现。

**修复**（三层兜底）：
```java
if (rows == 0) {
    // ① 失败留痕（供对账/人工介入）
    insertIntoFailedTable(success, "库存不足");
    // ② 已付款订单必须告警（负责人介入）
    if (orderService.isPaid(success.getOrderSn())) {
        alert("⚠️ 已付款订单落库失败：" + success.getOrderSn());
    }
    // ③ 未付款 → nack 重试（可能瞬时问题）
    channel.basicNack(deliveryTag, false, true);  // requeue
}
```
**核心变化**：静默 ack 丢弃 → 失败留痕 + 已付款告警 + 可重试。

### 第 4 层：对账任务 + 预热校验（✅ P1 已定稿，实现见 [[秒杀对账任务实现方案]]）

> **2026-09-07 更新**：对账任务已定稿为 P1 专项方案（以 DB 为准、双向修正、容错+连续校验、@Scheduled）。
> 下方代码为早期骨架示意，**实际实现细节与决策点见 [[秒杀对账任务实现方案]]**（本段不再作为实现依据）。

```java
// ① 对账任务（每 5 分钟）：Redis vs DB 比对，以 DB 为准修正
@Scheduled(fixedDelay = 300000)
public void reconcile() {
    for (Long skuId : seckillSkuMapper.findAllIds()) {
        Long redisStock = redisTemplate.boundValueOps(stockKey(skuId)).get();
        Integer dbStock = seckillSkuMapper.getStockBySkuId(skuId);
        if (redisStock != null && !redisStock.equals(dbStock.longValue())) {
            // 差异 → 告警 + 以 DB 为准覆盖 Redis
            redisTemplate.boundValueOps(stockKey(skuId)).set(dbStock + "");
        }
    }
}

// ② 预热校验（SeckillInitialJob 秒杀前）：先修正 Redis 旧值再预热
```

**意义**：漂移在用户感知前被自动修掉。

### 第 5 层：配置层——减少丢写窗口（⚠️ 计划中，配合 R2）

| 配置 | 作用 | 代价 |
|---|---|---|
| AOF everysec（R2 已有）| 主节点重启能恢复 | 略降性能 |
| min-replicas-to-write 1 | 写须同步 ≥1 从，否则拒写 → 减少丢写窗口 | 从全挂时主拒写（牺牲可用性）|
| WAIT 命令 | 写后强制等从确认 | 每次写变慢（秒杀不适用）|
| 哨兵 quorum 合理 | 防误判切换 | 配置 |

**秒杀建议**：AOF everysec + min-replicas-to-write 1，不用 WAIT。

---

## 三、实施清单（按优先级）

- [ ] **P0 付款前校验 DB 库存**：支付接口加 DB 库存校验（防付了钱没货）
- [ ] **P0 落库失败不静默**：SeckillQueueConsumer 改失败留痕+已付款告警+nack 重试
- [ ] **P1 对账任务**：定时 Redis vs DB 比对，以 DB 为准修正漂移
- [ ] **P1 预热校验**：SeckillInitialJob 秒杀前对齐 Redis 与 DB 库存
- [ ] **P2 配置层**：min-replicas-to-write 1（随 R2 主从哨兵一起）

---

## 四、风险与回滚

| 风险 | 等级 | 缓解 |
|---|---|---|
| 付款校验误拦正常支付 | 低 | DB 查询准确，仅库存不足时拦截 |
| 对账覆盖用户刚下的单 | 低 | 对账以 DB 为准（DB 正确），不影响合法预扣 |
| nack 重试导致重复消费 | 低 | 消费者已有条件扣减幂等（stock>=qty）|
| min-replicas 导致拒写 | 中 | 只在从节点全挂时发生，可临时关闭 |

---

## 五、与现有方案的关系

- **R1~R4（Redis 配置加固 + 哨兵）**：本方案的配置层依托，主从+哨兵先落地
- **Sentinel 能力补充计划**：无直接关系，可并行
- **集群化方案**：秒杀副本后 Redis 仍是单点，本方案同样适用
