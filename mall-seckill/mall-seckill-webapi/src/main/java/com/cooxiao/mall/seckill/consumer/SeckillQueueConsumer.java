package com.cooxiao.mall.seckill.consumer;

import com.cooxiao.mall.order.service.IOmsOrderService;
import com.cooxiao.mall.pojo.seckill.model.SeckillSku;
import com.cooxiao.mall.pojo.seckill.model.Success;
import com.cooxiao.mall.product.service.seckill.IForSeckillSpuService;
import com.cooxiao.mall.seckill.config.RabbitMqComponentConfiguration;
import com.cooxiao.mall.seckill.mapper.SeckillSkuMapper;
import com.cooxiao.mall.seckill.mapper.SeckillSpuMapper;
import com.cooxiao.mall.seckill.mapper.SuccessMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 秒杀成功记录落库消费者（TODO #14 P0 第3层：落库失败不静默）
 *
 * 语义：本消息代表"Redis 已放行、订单已创建"的秒杀成交。
 * 若 DB 秒杀库存扣减失败（rows==0），不能像以前那样 basicAck 静默吞掉——
 * 用户可能已付款但 success 记录缺失 = 订单悬挂无痕。改造为：
 *   ① 失败留痕（ERROR 日志含 skuId/orderSn/quantity）
 *   ② 已付款告警（Dubbo 查订单状态，已支付则 ERROR 告警需人工介入）
 *   ③ 限次重试（容器 AUTO ack + listener.simple.retry，最多 3 次；耗尽后 reject 且不 requeue
 *      ——与 OrderQueueConsumer 同款方案，见 TODO #70）
 *   ⚠️ 2026-09-12 #70 同款修复：**不再手动 ack/nack**。旧代码 basicNack(requeue = x-death 计数 < 3)，
 *      而 classic 队列 requeue **不写 x-death** ⇒ 计数恒 0 ⇒ **无限重投**；且 MANUAL 模式下抛异常
 *      **不会 reject**（消息永久 unacked）。x-death 现在只用于日志展示。
 */
@Component
@RabbitListener(queues = RabbitMqComponentConfiguration.SECKILL_QUEUE)
@Slf4j
public class SeckillQueueConsumer {

    /** 最大尝试次数（与 yml 的 listener.simple.retry.max-attempts 一致），仅用于日志展示 */
    private static final int MAX_REQUEUE = 3;

    @Autowired
    private SeckillSkuMapper seckillSkuMapper;
    @Autowired
    private SeckillSpuMapper seckillSpuMapper;
    @Autowired
    private SuccessMapper successMapper;
    @DubboReference
    private IForSeckillSpuService dubboSeckillSpuService;
    @DubboReference
    private IOmsOrderService dubboOrderService;

    @RabbitHandler
    @Transactional
    public void process(Success success,
                        @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) {
        // 统计已 requeue 次数（RabbitMQ 在 requeue 后自动附加 x-death 头）
        int requeueCount = countRequeue(xDeath);
        try {
            // 兼容旧消息：如果id为null，手动生成雪花算法ID
            if(success.getId() == null){
                success.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            }
            // 扣减数据库中的秒杀库存,SQL中已添加seckill_stock>=#{quantity}防止超卖
            int rows = seckillSkuMapper.updateReduceStockBySkuId(
                    success.getSkuId(),success.getQuantity());
            if(rows == 0){
                // ① 失败留痕（不再静默 basicAck）
                log.error("【秒杀落库留痕】DB库存扣减失败, skuId={}, 订单号={}, quantity={}, requeue {}/{}",
                        success.getSkuId(), success.getOrderSn(), success.getQuantity(),
                        requeueCount, MAX_REQUEUE);
                // ② 已付款告警：查订单状态，已支付(3)则必须人工介入
                try {
                    Integer state = dubboOrderService.getOrderStateBySn(success.getOrderSn());
                    if (state != null && state == 3) {
                        log.error("【秒杀落库告警】⚠️ 已付款订单落库失败，需人工介入！订单号={}, skuId={}, quantity={}",
                                success.getOrderSn(), success.getSkuId(), success.getQuantity());
                    } else {
                        log.warn("秒杀订单未支付或不存在(状态={})，扣减失败可后续重试或放弃，订单号={}",
                                state, success.getOrderSn());
                    }
                } catch (Exception e) {
                    log.warn("查询秒杀订单状态失败(可能订单模块未就绪)，订单号={}: {}",
                            success.getOrderSn(), e.getMessage());
                }
                // ③ 直接 reject 且不 requeue：库存扣减 rows==0 = 这条消息**不可自愈**（重投多少次都是 0 行）
                //    ⇒ 抛 AmqpRejectAndDontRequeueException：容器 reject(requeue=false)；
                //      重试拦截器最多再试 max-attempts 次后同样 reject —— 不再无限重投（#70 同款修复）
                //    ⚠️ seckill_queue **没有 DLX**（RabbitMqComponentConfiguration 里只有 x-queue-type），
                //      所以 reject 后消息**被丢弃**；可观测性靠上面两条 ERROR/WARN 日志（补 DLX 见 TODO #72）
                throw new AmqpRejectAndDontRequeueException(
                        "秒杀落库失败(rows==0)，拒绝且不重投: skuId=" + success.getSkuId()
                                + ", orderSn=" + success.getOrderSn());
            }
            // 新增success到数据库里
            successMapper.saveSuccess(success);
            // ✅ #70 同款修复：不再手动 basicAck —— ack 全交容器（AUTO 模式：方法正常返回即 ack）
            // 更新SPU销量
            try {
                SeckillSku sku = seckillSkuMapper.findBySkuId(success.getSkuId());
                if (sku != null) {
                    // 🔴 #69 修复（2026-09-12）：`seckill_sku.spu_id` 存的是 **seckill_spu.id（秒杀表内部 id）**，
                    //   而 `incrementSales` 的 SQL 是 `UPDATE pms_spu SET sales=sales+1 WHERE id=#{spuId}`
                    //   ⇒ 直接把内部 id 喂进去会**给另一个命名空间的同号商品加销量**
                    //   （实测：目标 pms spu 6/20，被 +1 的却是 pms spu 4/6）。这里先反查成 pms 主键再累加。
                    Long pmsSpuId = seckillSpuMapper.findPmsSpuIdBySeckillId(sku.getSpuId());
                    if (pmsSpuId != null) {
                        dubboSeckillSpuService.incrementSales(pmsSpuId);
                    } else {
                        log.warn("【秒杀销量】反查 pms spuId 为空，跳过销量累加：skuId={}, seckillSpuId={}",
                                success.getSkuId(), sku.getSpuId());
                    }
                }
            } catch (Exception e) {
                log.warn("更新SPU销量失败, skuId={}: {}", success.getSkuId(), e.getMessage());
            }
            log.info("秒杀成功记录处理完成,订单号:{}", success.getOrderSn());
        } catch (Exception e) {
            log.error("秒杀成功记录处理异常,订单号:{},异常信息:{}",
                    success.getOrderSn(), e.getMessage());
            // 抛出异常让@Transactional回滚事务,确保库存扣减也被撤销
            // ✅ #70 同款修复：AUTO 模式下抛异常 ⇒ 容器按 yml 重试策略重试 3 次，耗尽后
            //    recoverer(RejectAndDontRequeueRecoverer) reject 且不 requeue
            //    —— 不再"永久 unacked"（旧 MANUAL + 不手动 nack 的后果），也不再无限重投
            throw new RuntimeException(e);
        }
    }

    /**
     * 从 x-death 头统计该消息被入过几次死信（**仅用于日志**）。
     * ⚠️ 2026-09-12 实测（TODO #70 B）：classic 队列的 basicNack(requeue=true) **不会**写 x-death
     * （只有 quorum 队列或经 DLX 转发才写）⇒ 旧代码靠它统计"重投次数"必然恒 0 ⇒ 无限重投。
     * 重试现在由容器的 retry 拦截器负责，这里只做日志展示。
     */
    private int countRequeue(List<Map<String, Object>> xDeath) {
        if (xDeath == null || xDeath.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, Object> death : xDeath) {
            if (death != null && "requeued".equals(death.get("reason"))) {
                Object c = death.get("count");
                if (c instanceof Number) {
                    count = Math.max(count, ((Number) c).intValue());
                }
            }
        }
        return count;
    }
}