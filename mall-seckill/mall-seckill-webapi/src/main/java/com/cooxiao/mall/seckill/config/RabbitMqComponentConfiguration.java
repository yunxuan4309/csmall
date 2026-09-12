package com.cooxiao.mall.seckill.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 生成必要的rabbtimq组件
 * 1个交换机
 * 1个队列
 * 1个路由key值
 */
@Configuration
public class RabbitMqComponentConfiguration {
    public static final String SECKILL_EX="seckill_ex";
    public static final String SECKILL_QUEUE="seckill_queue";
    public static final String SECKILL_RK="seckill_routing_key";

    /** 死信交换机 / 死信队列 / 死信路由（DLX，TODO #72 —— 与 mall-order 的 order_ex_dlx 同款口径） */
    public static final String SECKILL_EX_DLX="seckill_ex_dlx";
    public static final String SECKILL_QUEUE_DLX="seckill_queue_dlx";
    public static final String SECKILL_DLX_RK="seckill_dlx_rk";

    @Bean
    public Queue seckillQueue(){
        // 🔴 TODO #72（2026-09-12）：**故意不在这里声明 x-dead-letter-* 参数**。
        //   原因：seckill_queue **早已存在**于 broker（当前只有 x-queue-type=classic），而 RabbitMQ
        //   **不允许修改已存在队列的参数**（会 PRECONDITION_FAILED ⇒ 容器起不来、消费直接断）。
        //   ⇒ 死信改用 **broker policy** 施加（不动队列参数、无需删队列、不丢消息）：
        //      docker exec csmall-rabbitmq rabbitmqctl set_policy seckill-dlx '^seckill_queue$' \
        //        '{"dead-letter-exchange":"seckill_ex_dlx","dead-letter-routing-key":"seckill_dlx_rk"}' --apply-to queues
        //   ⚠️ **顺序**：先部署本版本（把下面三个 DLX 组件声明出来）**再打 policy** —— 否则
        //      dead-letter-exchange 还不存在，被 reject 的消息会被**静默丢弃**（比现在还糟：现在至少只有日志）。
        //   （SeckillQueueContractTest 会断言本方法**没有**死信参数，防止有人顺手加回去把消费弄断。）
        return new Queue(SECKILL_QUEUE);
    }

    @Bean
    public Queue seckillDlxQueue(){
        return new Queue(SECKILL_QUEUE_DLX, true);
    }

    @Bean
    public DirectExchange seckillDlxExchange(){
        return new DirectExchange(SECKILL_EX_DLX);
    }

    @Bean
    public Binding seckillDlxBinding(){
        return BindingBuilder.bind(seckillDlxQueue()).to(seckillDlxExchange()).with(SECKILL_DLX_RK);
    }
    @Bean
    public DirectExchange seckillExchange(){
        return new DirectExchange(SECKILL_EX);
    }
    @Bean
    public Binding seckillBinding(){
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_RK);
    }
    // 使用JSON序列化替代Java默认序列化，跨服务兼容性更好
    @Bean
    public MessageConverter jackson2JsonMessageConverter(){
        return new Jackson2JsonMessageConverter();
    }
}
