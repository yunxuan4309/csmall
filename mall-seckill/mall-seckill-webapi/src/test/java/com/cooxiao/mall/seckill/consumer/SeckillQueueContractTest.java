package com.cooxiao.mall.seckill.consumer;

import com.cooxiao.mall.pojo.seckill.model.Success;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.core.MethodParameter;
import org.springframework.util.ClassUtils;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TODO #70 同款修复的回归测试 —— 锁死秒杀消费者的两条契约，防止 2026-09-12 修复被改回去。
 *
 * <p><b>为什么要有它</b>：{@code seckill_queue} 原先的失败处理是
 * {@code basicNack(deliveryTag, false, requeueCount < MAX_REQUEUE)}，而 {@code requeueCount} 取自
 * {@code x-death} 头 —— 实测（#70 B）<b>classic 队列 requeue 根本不写 x-death</b> ⇒ 计数恒 0
 * ⇒ {@code requeue=true} 永远成立 ⇒ <b>无限重投</b>；且 MANUAL 模式下抛异常<b>不会 reject</b>
 * （消息永久 unacked）。修复方式与 {@code OrderQueueConsumer} 完全一致：<b>ack 全交容器</b>
 * （{@code acknowledge-mode: auto}）+ 重试耗尽后 {@code reject} 且不 requeue。
 *
 * <p>本测试不需要 broker / Spring 容器，只用真实 Spring AMQP 转换器与反射：
 * <ol>
 *   <li>生产端 JSON 能否绑定成 {@link Success}（并满足"框架按载荷类型挑方法"的约束）；</li>
 *   <li>监听方法签名<b>不得</b>再出现 {@link Channel} / deliveryTag（出现就意味着又有人要手动 ack）；</li>
 *   <li>yml 的 ack 模式必须是 auto 且 default-requeue-rejected=false（#70 C 的教训：
 *       配置可能因自定义 factory 变成影子配置 —— 这里锁的是本服务真实生效的那份）。</li>
 * </ol>
 */
class SeckillQueueContractTest {

    /** 与 RabbitMqComponentConfiguration#jackson2JsonMessageConverter() 一致的转换器 */
    private final Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

    private static Method processMethod() throws Exception {
        // #70 同款修复：ack 交容器（AUTO），监听器不再收 Channel/deliveryTag
        return SeckillQueueConsumer.class.getMethod("process", Success.class, List.class);
    }

    /** 生产端 {@code convertAndSend(SECKILL_EX, SECKILL_RK, success)} 发出的 JSON 必须能被消费端绑定 */
    @Test
    void producerJson_mustBindToSuccess_andBeAcceptedByConsumerParam() throws Exception {
        // ① 生产端（RabbitTemplate.convertAndSend 用的就是同一个转换器）
        Success sent = new Success();
        sent.setId(1234567890L);
        sent.setSkuId(1001L);
        sent.setOrderSn("SK20260912001");
        sent.setQuantity(1);
        Message message = converter.toMessage(sent, new MessageProperties());
        assertNotNull(message.getMessageProperties().getHeader("__TypeId__"),
                "生产端转换器应写入 __TypeId__ 头；没有它、又没有方法参数提示时，载荷会退化成 LinkedHashMap");

        Object payload = converter.fromMessage(message);
        assertInstanceOf(Success.class, payload, "载荷必须绑定成 Success 实体，而不是 LinkedHashMap");
        Success success = (Success) payload;
        assertEquals(1001L, success.getSkuId());
        assertEquals(1, success.getQuantity());
        assertEquals("SK20260912001", success.getOrderSn());

        MethodParameter payloadParam = new MethodParameter(processMethod(), 0);
        assertTrue(ClassUtils.isAssignable(payloadParam.getParameterType(), payload.getClass()),
                "方法首参类型必须能接受转换后的载荷类，否则框架报 No listener method found for class "
                        + payload.getClass().getName());

        // ② ⚠️ 实测结论（2026-09-12）：**仅靠"方法参数提示"不足以保证类型** ——
        //    把同一段 JSON 去掉 __TypeId__ 再转，得到的仍是 LinkedHashMap。
        //    ⇒ **__TypeId__ 头是载荷类型的承重结构**：生产端一旦抑制类型头（或换成裸 JSON 发送），
        //      消费端就会退化成 LinkedHashMap，方法按载荷类型挑不中 ⇒ 又回到 #65 那类静默失效。
        //    所以本测试锁的是①（生产端转换器必须写入 __TypeId__）而不是"提示能兜住"。
    }

    /**
     * 核心回归：<b>手动 ack 的入口必须消失</b>。
     * 参数里一旦重新出现 {@link Channel} 或 long deliveryTag，就说明有人把 ack 又拿回监听器自己管
     * （AUTO 下会变成双重确认 406 / MANUAL 下抛异常不 reject ⇒ #70 D 那类"消息永远 unacked"）。
     */
    @Test
    void consumer_mustNotTakeChannelOrDeliveryTag() throws Exception {
        Method process = processMethod();
        Class<?>[] params = process.getParameterTypes();
        assertEquals(2, params.length,
                "监听方法应只有 (Success, x-death List) 两个参数；多出 Channel/deliveryTag 就是手动 ack 回来了");
        assertEquals(Success.class, params[0], "首参必须是实体 Success");
        assertFalse(Channel.class.isAssignableFrom(params[0]), "不得把 Channel 当载荷参数");
        for (Class<?> p : params) {
            assertFalse(Channel.class.isAssignableFrom(p), "监听方法不得再收 Channel（ack 交容器）");
        }
    }

    /** #70 C 的教训：配置必须落在"真正生效的那份"上 —— 本服务无自定义 factory，故 prod yml 生效 */
    @Test
    void prodYml_mustUseAutoAck_andNotRequeue() throws Exception {
        String yml;
        try (InputStream in = getClass().getResourceAsStream("/application-prod.yml")) {
            assertNotNull(in, "application-prod.yml 必须在测试类路径上（src/main/resources）");
            yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(yml.contains("acknowledge-mode: auto"),
                "必须是 acknowledge-mode: auto（ack 交容器）");
        assertTrue(yml.contains("default-requeue-rejected: false"),
                "必须显式 default-requeue-rejected: false；否则重试耗尽后仍会 requeue = 无限重投");
        assertFalse(yml.contains("acknowledge-mode: manual"),
                "不得再退回 acknowledge-mode: manual（手动 ack 的土壤，见 #70 B/D）");
    }
}
