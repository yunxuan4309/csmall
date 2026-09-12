package com.cooxiao.mall.order.mq;

import com.cooxiao.mall.product.service.order.IForOrderSkuService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
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
                    // 库存扣减失败：可能瞬时（Dubbo 抖动）也可能永久（库存不足/下架）
                    log.warn("库存扣减失败，skuId: {}, quantity: {}, requeue {}/{} 次后仍未成功",
                            item.getSkuId(), item.getQuantity(), requeueCount, MAX_REQUEUE);
                    channel.basicNack(deliveryTag, false, requeueCount < MAX_REQUEUE);
                    return;
                }
                log.debug("库存扣减成功，skuId: {}, quantity: {}", item.getSkuId(), item.getQuantity());
            }
            channel.basicAck(deliveryTag, false);
            log.info("订单库存扣减完成，共 {} 个商品", items.size());
        } catch (Exception e) {
            log.error("订单库存扣减异常，requeue {}/{} 次后仍未成功", requeueCount, MAX_REQUEUE, e);
            try {
                // 未达上限 requeue 重试；达上限则丢弃并留痕（配合 TODO #36 DLX/人工补偿）
                channel.basicNack(deliveryTag, false, requeueCount < MAX_REQUEUE);
            } catch (Exception ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 从 x-death 头统计该消息已被 requeue 的次数。
     * RabbitMQ 在 basicNack(requeue=true) 后重新投递时，header 会带 x-death，
     * 其中 reason=requeued 的 count 即重试次数。
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
