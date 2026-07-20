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

@Component
@RabbitListener(queues = OrderQueueConfig.ORDER_QUEUE)
@Slf4j
public class OrderQueueConsumer {

    @DubboReference
    private IForOrderSkuService dubboSkuService;

    @RabbitHandler
    public void process(String messageJson, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            List<OrderItemMessage> items = JSON.parseArray(messageJson, OrderItemMessage.class);
            for (OrderItemMessage item : items) {
                int rows = dubboSkuService.reduceStockNum(item.getSkuId(), item.getQuantity());
                if (rows == 0) {
                    log.error("库存扣减失败，skuId: {}, quantity: {}", item.getSkuId(), item.getQuantity());
                    channel.basicNack(deliveryTag, false, true);
                    return;
                }
                log.debug("库存扣减成功，skuId: {}, quantity: {}", item.getSkuId(), item.getQuantity());
            }
            channel.basicAck(deliveryTag, false);
            log.info("订单库存扣减完成，共 {} 个商品", items.size());
        } catch (Exception e) {
            log.error("订单库存扣减异常", e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
