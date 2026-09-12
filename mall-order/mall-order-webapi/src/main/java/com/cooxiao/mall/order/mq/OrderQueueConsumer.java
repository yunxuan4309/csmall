package com.cooxiao.mall.order.mq;

import com.cooxiao.mall.product.service.order.IForOrderSkuService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 普通订单「异步扣减库存」消费者（{@code order_queue}）。
 *
 * <p>🔴 <b>#65 修复（2026-09-12）—— 方法参数类型必须与"转换后的载荷类型"一致</b>：
 * <ul>
 *   <li>生产端 {@code rabbitTemplate.convertAndSend(ORDER_EX, ORDER_RK, List<OrderItemMessage>)} 经
 *       {@link org.springframework.amqp.support.converter.Jackson2JsonMessageConverter} 发出的是
 *       <b>JSON 数组</b>，消费端转换后的载荷类因此是 {@code java.util.ArrayList}；</li>
 *   <li>本类原先的参数是 {@code String} ⇒「类级 {@code @RabbitListener} + {@code @RabbitHandler}」下
 *       <b>框架会按载荷类型挑方法</b>，找不到匹配就抛
 *       {@code NoSuchMethodException: No listener method found ... for class java.util.ArrayList}；</li>
 *   <li>⇒ 消息重试 3 次后进死信，<b>整条库存扣减链路静默失效</b>（{@code pms_sku.stock} 永不减少，
 *       下单也失去库存校验）—— 这就是 TODO #65 的根因。</li>
 * </ul>
 *   <li>⚠️ <b>仅把参数改成 {@code List<OrderItemMessage>} 也不够</b>：实测
 *       {@code SmartMessageConverter.fromMessage(msg, methodParam)} <b>不会按方法参数推断泛型</b>，
 *       集合元素会退化成 {@code LinkedHashMap} ⇒ 运行到 {@code item.getSkuId()} 时抛
 *       {@code ClassCastException}（回归测试 {@code OrderQueuePayloadContractTest} 已复现）。</li>
 * </ul>
 * 现采用本工程<b>已被生产验证</b>的写法：载荷是<b>非泛型 POJO</b> {@link OrderStockMessage}
 * （对照 {@code SeckillQueueConsumer(Success success, ...)}），泛型集合放在 POJO 字段里。
 */
@Component
@RabbitListener(queues = OrderQueueConfig.ORDER_QUEUE)
@Slf4j
public class OrderQueueConsumer {

    /**
     * 最大 requeue 次数（不含首次投递）。达到后不再 requeue，丢弃并留痕，
     * 避免毒消息（如永久库存不足/商品下架）无限重试挂单 —— TODO #36
     */
    private static final int MAX_REQUEUE = 3;

    @DubboReference
    private IForOrderSkuService dubboSkuService;

    @RabbitHandler
    public void process(OrderStockMessage message, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) {
        // 统计已 requeue 次数（x-death 由 RabbitMQ 在每次 requeue 后自动附加）
        int requeueCount = countRequeue(xDeath);
        try {
            List<OrderItemMessage> items = message == null ? null : message.getItems();
            if (items == null || items.isEmpty()) {
                log.warn("订单库存扣减消息体为空（payload 为 null 或空集合），直接确认，避免无意义重投");
                channel.basicAck(deliveryTag, false);
                return;
            }
            for (OrderItemMessage item : items) {
                int rows = dubboSkuService.reduceStockNum(item.getSkuId(), item.getQuantity());
                if (rows == 0) {
                    // 🔴 TODO #70 修复（2026-09-12）：rows==0 = 这条消息**永远不可能成功**（库存不足 / SKU 不可用）
                    //   ⇒ 属**毒消息**，必须直接进死信，绝不能 requeue。
                    //   ⚠️ 原写法 `basicNack(tag, false, requeueCount < MAX_REQUEUE)` 实测会**无限重投**：
                    //      classic 队列下 `basicNack(requeue=true)` **不会**给消息加 `x-death` 头，
                    //      而 countRequeue() 只数 x-death ⇒ 计数**恒为 0** ⇒ `0 < 3` 恒真
                    //      （实测：10 分钟重投 **114 次**，约每秒一次，一直打 Dubbo 商品服务）。
                    //   现在抛 AmqpRejectAndDontRequeueException ⇒ 容器 reject(requeue=false) ⇒ 进 DLX，
                    //   由 OrderDlxConsumer 打【MQ死信告警】留痕，供人工补偿。
                    log.warn("库存扣减失败（毒消息），skuId: {}, quantity: {}, x-death 计数: {}（classic 队列恒 0，故不再用它判上限）→ 转入 DLX",
                            item.getSkuId(), item.getQuantity(), requeueCount);
                    throw new AmqpRejectAndDontRequeueException(
                            "库存扣减失败（库存不足或 SKU 不可用）→ 死信：" + item);
                }
                log.debug("库存扣减成功，skuId: {}, quantity: {}", item.getSkuId(), item.getQuantity());
            }
            channel.basicAck(deliveryTag, false);
            log.info("订单库存扣减完成，共 {} 个商品", items.size());
        } catch (AmqpRejectAndDontRequeueException e) {
            throw e;   // 毒消息（库存不足等）：放行给容器 reject(requeue=false) → DLX
        } catch (Exception e) {
            // 🔴 TODO #70 修复（2026-09-12）：不再手动 nack。抛出后由容器按
            //   `spring.rabbitmq.listener.simple.retry.max-attempts=3` 重试；耗尽后
            //   RejectAndDontRequeueRecoverer 会 reject(requeue=false) ⇒ 自动进 DLX（带告警）。
            //   ⚠️ 这里**必须抛**：若吞掉异常，容器会按自动确认认为消费成功 ⇒ **静默丢消息**。
            log.error("订单库存扣减异常（容器将重试 {} 次后转入死信）", MAX_REQUEUE, e);
            throw new RuntimeException("订单库存扣减异常", e);
        }
    }

    /**
     * 从 x-death 头统计该消息已被 requeue 的次数。
     *
     * <p>⚠️ <b>2026-09-12（TODO #70）实测更正</b>：这个计数**在 classic 队列上恒为 0** ——
     * {@code basicNack(requeue=true)} 只是把消息放回原队列，<b>不会</b>写 {@code x-death} 头
     * （{@code x-death} 只在"被死信"时才加；只有 quorum 队列才带 {@code reason=requeued}）。
     * ⇒ <b>不能用它做"限次重试"的上限判断</b>（本类曾因此无限重投）。
     * 现在只用于日志留痕；真正的限次重试交给容器（{@code retry.max-attempts=3} + reject → DLX）。
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
