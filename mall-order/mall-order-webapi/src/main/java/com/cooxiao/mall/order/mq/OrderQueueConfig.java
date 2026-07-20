package com.cooxiao.mall.order.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 普通订单 RabbitMQ 组件：交换机 + 队列 + 路由绑定。
 * 用于异步扣减库存，与秒杀队列分离。
 */
@Configuration
public class OrderQueueConfig {

    public static final String ORDER_EX = "order_ex";
    public static final String ORDER_QUEUE = "order_queue";
    public static final String ORDER_RK = "order_rk";

    @Bean
    public Queue orderQueue() {
        return new Queue(ORDER_QUEUE, true);
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
