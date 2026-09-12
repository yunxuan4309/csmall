package com.cooxiao.mall.seckill.consumer;

import com.cooxiao.mall.seckill.config.RabbitMqComponentConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 秒杀成功记录消息的【死信消费者】（TODO #72，与 mall-order 的 OrderDlxConsumer 同款口径）。
 *
 * <p>链路：{@code seckill_queue} 消费失败 → 容器重试 3 次
 * （AUTO ack + {@code RejectAndDontRequeueRecoverer}）→ reject(requeue=false)
 * → 经 broker policy 施加的 {@code dead-letter-exchange} 转入 {@code seckill_queue_dlx}
 * → 本类只做三件事：
 * <ol>
 *   <li>记录死信原因（x-death 头 —— 注意 classic 队列的 requeue **不写** x-death，reject 才写）</li>
 *   <li>输出 ERROR 级告警（含消息体原文，供人工核对订单/库存后补偿）</li>
 *   <li>正常返回 ⇒ 容器自动 ack（**不要手动 ack**，否则是 406 双确认噪音，见 #70 E）</li>
 * </ol>
 *
 * <p>⚠️ <b>与 mall-order 的有意差异</b>：{@code order_queue} 是应用自己带
 * {@code x-dead-letter-*} 参数声明建起来的；而 {@code seckill_queue} <b>早已存在于 broker</b>，
 * RabbitMQ <b>不允许修改已存在队列的参数</b>（会 {@code PRECONDITION_FAILED} ⇒ 容器起不来、消费直接断）
 * ⇒ 秒杀侧改用 <b>broker policy</b> 施加死信（命令见
 * {@link RabbitMqComponentConfiguration#seckillQueue()} 的注释）。
 * 因此「本类部署」与「policy 生效」<b>缺一不可</b>：只部署本类却不打 policy ⇒ 消息仍会被丢弃。
 *
 * <p>故意<b>不反序列化为业务类型</b>：死信可能来自不同版本/格式，按原始字节留痕最稳
 * （这也是 #65 的教训：按载荷类型挑方法会在格式变化时连告警一起失效 ⇒ 监听注解写在方法上、
 * 参数收原始 {@code Message}）。
 */
@Slf4j
@Component
public class SeckillDlxConsumer {

    private static final String ALARM_PREFIX = "【MQ死信告警】";

    @RabbitListener(queues = RabbitMqComponentConfiguration.SECKILL_QUEUE_DLX)
    public void onDlxMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        String reason = extractDeathReason(message);
        log.error(ALARM_PREFIX + "秒杀落库消息进入死信队列（需人工核对订单/库存后补偿），原因: {}，消息体: {}",
                reason, body);
    }

    /**
     * 从消息头 x-death 提取最可能的死信原因（RabbitMQ 在 reject/nack 后附加）。
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
