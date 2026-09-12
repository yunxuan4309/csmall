# 面试速记-2 电商-秒杀与 Redis（面试前 5 分钟快速过）

> **所属**：面试速记版分册 2/7。完整深挖见 02-秒杀高并发.md。
> **场景**：被问"秒杀怎么做/高并发/防超卖/Redis"时。

---

## 秒杀四层防护

```
Redis 预扣库存（DECR 原子防超卖）
Sentinel 限流（QPS=10，超过返回"服务器忙"）
RabbitMQ 异步落库（削峰）
Seata 分布式事务（扣库存+建订单一致）
消息可靠性：JSON 序列化（跨服务版本兼容）+ 本地重试表兜底（先写表再发MQ，失败每5秒重发×3）
```

## 防重复三 key

```
orderLock（下单锁1分钟）→ ordered（未支付拦截）→ reseckill（购买标记）
分离原因：历史踩坑，混用导致"支付后还能再下单"
```

## 关键数字

- 11 微服务 / 老机 21 容器 + 新机 5 容器（合计 26 服务）/ 6 库 39 表（业务 30）/ 45 Controller / 554 Java 文件 / 32 Vue 页面
- 压测：100 并发 → 8-36 成功 / 64-89 限流 / 0 超卖
- JVM 调优：内存 93%→71%，释放 ~3G，可用 1.1G→4.4G（历史快照；当前 ~86% 见 11-服务器情况 Q5）

## Redis 速记（Q7）

```
三兄弟防御：穿透=查不存在key打DB→Set当布隆拦截(SeckillBloomInitialJob预热spuId)
            击穿=热点key过期瞬间→预热常驻不过期(SeckillInitialJob)
            雪崩=大量key同时过期→关键数据无TTL(reseckill永久)
            企业级分层：CDN→网关IP限流→应用层(你的)→数据层；你只做应用层
锁：2种SETNX——幂等锁@Idempotent(AOP,秒杀/订单/支付3处)+orderLock下单锁
    三要素：SETNX原子+TTL防死锁+异常释放；key=前缀+userId+参数MD5
    演进四代：SETNX→+TTL(你)→+唯一标识/看门狗(Redisson)→RedLock(有争议)
    pom引了Redisson 3.24.3但代码没用=诚实点
布隆vs Set：布隆=概率(省内存百万id~1MB,有假阳,不能删)；Set=精确可删占内存
    数据量小(12 SKU)用Set合理，百万级再换真布隆(TODO#3)
预扣一致性三层：下单失败补偿Redis(increment) / MQ重试表兜底(每5s×3)
               / DB条件扣减(seckill_stock>=qty)防超卖
    本质=最终一致(宁可少卖不超卖)，生产加对账任务+库存预热校验；Redis 主从已做(#9 老机主+新机从+3 哨兵,选主 6.1s)
随机码=6位随机数预热Redis(key含spuId,TTL=2h+5min+随机30s)，提交比对防接口直刷
幂等=@Idempotent注解+AOP+SETNX(key=前缀+userId+参数MD5,异常删锁允许重试)
防重复三key=orderLock(1min防重复提交)+ordered(2h防未支付重复)+reseckill(永久防支付后)
四道防线时序(小明)：幂等锁3s(防连点"请勿重复提交")→orderLock 1min(防刚买"已购买过")
                  →ordered 2h(防未支付"先支付")→reseckill永久(支付后不能再买)
主从切换丢数据：异步复制主挂瞬间丢最后几笔→DECR丢=DB条件扣减不超卖(5台6人抢,
  第6个扣不动订单失败)；reseckill丢=success唯一键uk_sku_user兜底
  MQ短暂挂=重试表兜底(先写表再发,每5s补发×3)；永久挂=单机救不了,生产配镜像队列
幂等概念：同一操作执行多次效果=执行一次；例子=扣款100两次只扣一次
数据结构全景：DECR=Decrement(递减,对称INCR)；String计数/锁、Hash对象/购物车、
          List队列/时间线、Set去重/交集、ZSet排行榜/延迟队列(项目没用,TODO#11评估)、
          HyperLogLog UV、GEO附近、Stream队列；选型=数据形态决定结构
```
