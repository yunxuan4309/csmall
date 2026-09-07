package com.cooxiao.mall.order.mq;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 订单库存扣减消息的【死信消费者】（TODO #36 第二步 DLX）。
 *
 * <p>职责：order_queue 中消费失败且重试耗尽（OrderQueueConsumer requeue=false）
 * 的消息会进入 order_queue_dlx。此处只做：
 * <ol>
 *   <li>记录死信原因（x-death 头含 requeue/rejected 历史）</li>
 *   <li>输出 ERROR 级告警日志（含消息体原文，供人工补偿）</li>
 *   <li>确认消费（防止死信队列无限堆积）</li>
 * </ol>
 *
 * <p>注意：故意<b>不反序列化为业务类型</b>——死信消息可能来自不同版本/格式，
 * 按原始 String 记录最稳妥。未来可扩展：落库到死信表 / 发告警通知（TODO #30）。
 */
@Slf4j
@Component
@RabbitListener(queues = OrderQueueConfig.ORDER_DLX_QUEUE)
public class OrderDlxConsumer {

    private static final String ALARM_PREFIX = "【MQ死信告警】";

    @RabbitHandler
    public void onDlxMessage(Message message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String reason = extractDeathReason(message);
        log.error(ALARM_PREFIX + "订单库存扣减消息进入死信队列，原因: {}，消息体: {}",
                reason, body);
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("死信消息确认失败", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 从消息头 x-death 提取最可能的死信原因（RabbitMQ 在 nack/reject 后附加）。
     */
    private String extractDeathReason(Message message) {
        Object xDeathObj = message.getMessageProperties().getHeader("x-death");
        if (xDeathObj instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    sb.append(entry.getKey()).append('=').append(entry.getValue()).append(' ');
                }
                return sb.toString().trim();
            }
        }
        return "未知（无 x-death 头，可能 TTL 过期或队列删除）";
    }
}
