package com.cooxiao.mall.order.mq;

import com.alibaba.fastjson.JSON;
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
    public void process(String messageJson, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) {
        // 统计已 requeue 次数（x-death 由 RabbitMQ 在每次 requeue 后自动附加）
        int requeueCount = countRequeue(xDeath);
        try {
            List<OrderItemMessage> items = JSON.parseArray(messageJson, OrderItemMessage.class);
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
