package com.cooxiao.mall.order.mq;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
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

    /**
     * 让 {@code @RabbitListener} 使用 JSON 反序列化（解决 LinkedHashMap 转换失败）。
     *
     * <p>🔴 <b>#70 补修（2026-09-12）：ack 模式与限次重试<b>必须在这里设</b>，写在 yml 里没用！</b>
     * 本类自己定义了名为 {@code rabbitListenerContainerFactory} 的 bean ⇒ {@code @RabbitListener}
     * 用的就是<b>它</b>；而 {@code spring.rabbitmq.listener.simple.*} <b>只作用于 Spring Boot 自动配置的那个 factory</b>。
     * <p><b>实测踏坑</b>：把 {@code acknowledge-mode: manual} 写进 {@code application-prod.yml} 后，
     * 运行日志仍是 {@code acknowledgeMode=AUTO} ⇒ 容器自动 ack 与代码里的手动 ack <b>双确认</b>：
     * {@code channel error 406 PRECONDITION_FAILED unknown delivery tag}。
     * （这是 G16"改到了影子 key"的同类问题：配置在 jar 里，但<b>没有任何代码去读它</b>。）
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter());
        // ↓ #70 补修 D（2026-09-12 实测）：这里必须是 **AUTO**（ack 交给容器），**不能**是 MANUAL！
        //   原因：MANUAL 模式下容器**不碰 channel**，监听器"抛异常"并不会 reject
        //   ⇒ 消息会**永远 unacked**（实测：order_queue 挂着 1 unacked、DLX 恒 0、告警不响），
        //   而 `RejectAndDontRequeueRecoverer` 只有 AUTO 模式才会真正 reject(requeue=false) ⇒ 进 DLX。
        //   配套：OrderQueueConsumer **不再调用任何 basicAck/basicNack**（成功即正常返回 ⇒ 容器自动 ack），
        //   否则自动 + 手动会**双确认**（`406 PRECONDITION_FAILED unknown delivery tag`）。
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        // ↓ 未捕获异常不再无脑 requeue（默认 true 会造成无限重投）
        factory.setDefaultRequeueRejected(false);
        // ↓ 限次重试：重试 3 次后 reject(requeue=false) ⇒ 进 DLX（等价于 yml 里那套，但**这里才生效**）
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
