package com.cooxiao.mall.order.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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
public class OrderDlxConsumer {

    private static final String ALARM_PREFIX = "【MQ死信告警】";

    /**
     * 🔴 <b>#65 修复（2026-09-12）：监听注解必须写在方法上、方法参数收原始 {@code Message}</b>。
     *
     * <p>原来用的是「类级 {@code @RabbitListener} + {@code @RabbitHandler}」—— 框架会先把载荷
     * 反序列化成对象、再<b>按载荷类型挑方法</b>；死信消息的载荷是 {@code java.util.ArrayList}
     * （JSON 数组反序列化结果），与 {@code Message} 参数不匹配 ⇒ <b>连死信告警也一起失效</b>
     * （本该打印 {@code 【MQ死信告警】} 的 ERROR 根本没执行）。
     * 方法级监听没有"按类型挑方法"这一步 ⇒ 无论载荷是什么，都能拿到原始字节做留痕。
     */
    @RabbitListener(queues = OrderQueueConfig.ORDER_DLX_QUEUE)
    public void onDlxMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String reason = extractDeathReason(message);
        log.error(ALARM_PREFIX + "订单库存扣减消息进入死信队列，原因: {}，消息体: {}",
                reason, body);
        // 🔴 #70 补修 E（2026-09-12）：**不要手动 ack** —— 容器是 AUTO 模式（见 OrderQueueConfig 的 factory 说明），
        //   方法正常返回即自动确认；这里再手动 ack 一次会**双确认** ⇒
        //   `channel error 406 PRECONDITION_FAILED unknown delivery tag`（实测：2 条死信 ⇒ 2 次 406）。
        //   消息本身已被 ack（死信队列归 0），所以那只是**日志噪音**；但既然顺手，就一并去掉。
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
