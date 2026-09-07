package com.cooxiao.mall.order.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 普通订单 RabbitMQ 组件：交换机 + 队列 + 路由绑定 + 死信队列（DLX）。
 * 用于异步扣减库存，与秒杀队列分离。
 *
 * <p>DLX 说明（TODO #36 第二步）：
 * <ul>
 *   <li>order_queue 声明 dead-letter 指向 order_ex_dlx —— 消费者 requeue=false
 *       （OrderQueueConsumer 限 3 次重试耗尽）的消息自动进死信队列</li>
 *   <li>order_queue_dlx 由 OrderDlxConsumer 监听，记录原因 + 告警，供人工补偿</li>
 * </ul>
 *
 * <p>⚠️ 部署注意：order_queue 已在 RabbitMQ 存在且无死信参数时，
 * Spring 声明不会更新已存在队列 —— 需先删除旧队列（当前 0 积压，零风险）。
 */
@Configuration
public class OrderQueueConfig {

    public static final String ORDER_EX = "order_ex";
    public static final String ORDER_QUEUE = "order_queue";
    public static final String ORDER_RK = "order_rk";

    /** 死信交换机/队列/路由（DLX） */
    public static final String ORDER_DLX_EX = "order_ex_dlx";
    public static final String ORDER_DLX_QUEUE = "order_queue_dlx";
    public static final String ORDER_DLX_RK = "order_dlx_rk";

    @Bean
    public Queue orderQueue() {
        // durable + 死信参数：requeue=false / TTL 过期的消息进 DLX
        return QueueBuilder.durable(ORDER_QUEUE)
                .deadLetterExchange(ORDER_DLX_EX)
                .deadLetterRoutingKey(ORDER_DLX_RK)
                .build();
    }

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EX);
    }

    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderQueue()).to(orderExchange()).with(ORDER_RK);
    }

    @Bean
    public Queue orderDlxQueue() {
        return new Queue(ORDER_DLX_QUEUE, true);
    }

    @Bean
    public DirectExchange orderDlxExchange() {
        return new DirectExchange(ORDER_DLX_EX);
    }

    @Bean
    public Binding orderDlxBinding() {
        return BindingBuilder.bind(orderDlxQueue()).to(orderDlxExchange()).with(ORDER_DLX_RK);
    }

    @Bean
    public MessageConverter orderMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /** 让 @RabbitListener 使用 JSON 反序列化，解决 LinkedHashMap 转换失败 */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        return factory;
    }
}
