package com.cooxiao.mall.order.mq;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.SmartMessageConverter;
import org.springframework.core.MethodParameter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TODO #65 回归测试 —— 锁死「生产端发的 JSON 数组」↔「消费端 {@code @RabbitHandler} 方法参数」的契约。
 *
 * <p><b>为什么要测这个</b>：这个缺陷（库存扣减链路整条静默失效）不来自任何业务逻辑，
 * 而来自一个<b>框架层的类型契约</b>：生产端 {@code convertAndSend(..., List<OrderItemMessage>)}
 * 经 {@link Jackson2JsonMessageConverter} 发出 JSON 数组，消费端"转换后的载荷类"是
 * {@code java.util.ArrayList}；框架在「类级 {@code @RabbitListener} + {@code @RabbitHandler}」下
 * 会<b>按载荷类型挑方法</b>，参数写成 {@code String} 就挑不中 ⇒ 抛
 * {@code No listener method found ... for class java.util.ArrayList} ⇒ 重试耗尽进死信。
 *
 * <p>本测试用<b>真实的 Spring AMQP 转换器</b>把这条链路的三步都跑一遍（不需要 broker）：
 * <ol>
 *   <li>生产端序列化：{@code toMessage(List<OrderItemMessage>, props)}；</li>
 *   <li>框架挑方法时的判断：参数类型必须能接受转换后的载荷类；</li>
 *   <li>选中方法后的转换：载荷必须是<b>非泛型 POJO</b> {@code OrderStockMessage}（而<b>不是</b>
 *       {@code ArrayList<LinkedHashMap>}）；泛型集合只有在 POJO 字段里才会被正确绑定。</li>
 * </ol>
 */
class OrderQueuePayloadContractTest {

    /** 与 OrderQueueConfig#orderMessageConverter() / rabbitListenerContainerFactory 完全一致的转换器 */
    private final Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

    private static Method processMethod() throws Exception {
        // #70 补修 D：ack 交给容器（AUTO），监听器不再收 Channel/deliveryTag
        return OrderQueueConsumer.class.getMethod("process", OrderStockMessage.class, List.class);
    }

    @Test
    void producerJsonArray_mustBeAcceptedByConsumerMethodParameter() throws Exception {
        OrderItemMessage first = new OrderItemMessage();
        first.setSkuId(1001L);
        first.setQuantity(2);
        first.setOrderItemId(11L);
        OrderItemMessage second = new OrderItemMessage();
        second.setSkuId(1002L);
        second.setQuantity(1);
        second.setOrderItemId(12L);

        // ① 生产端（RabbitTemplate.convertAndSend 用的就是同一个转换器）：发的是非泛型 POJO
        Message message = converter.toMessage(new OrderStockMessage(List.of(first, second)), new MessageProperties());

        // ② 框架"按载荷类型挑方法"使用的判断：参数类型必须能接受转换后的载荷类
        MethodParameter payloadParam = new MethodParameter(processMethod(), 0);
        Object rawPayload = converter.fromMessage(message);
        assertInstanceOf(OrderStockMessage.class, rawPayload,
                "载荷必须绑定成 OrderStockMessage（非泛型 POJO），而不是 ArrayList/LinkedHashMap");
        assertTrue(ClassUtils.isAssignable(payloadParam.getParameterType(), rawPayload.getClass()),
                "方法首参类型必须能接受转换后的载荷类，否则框架报 No listener method found for class "
                        + rawPayload.getClass().getName());

        // ③ 选中方法后，框架会把"方法参数"作为转换提示（conversionHint）再转一次（与运行时同一条路径）
        Object bound = ((SmartMessageConverter) converter).fromMessage(message, payloadParam);
        assertInstanceOf(OrderStockMessage.class, bound);
        List<OrderItemMessage> items = ((OrderStockMessage) bound).getItems();
        assertNotNull(items, "POJO 里的集合字段必须被绑定");
        assertEquals(2, items.size(), "两条订单项都要绑定成功");
        assertInstanceOf(OrderItemMessage.class, items.get(0),
                "元素必须是 OrderItemMessage（若是 LinkedHashMap，说明泛型没带上，运行期会 ClassCastException）");
        assertEquals(1001L, items.get(0).getSkuId());
        assertEquals(2, items.get(0).getQuantity());
        assertEquals(12L, items.get(1).getOrderItemId());
    }

    @Test
    void queueConsumer_mustNotParseStringPayloadItself() throws Exception {
        // 参数退回 String（挑不中方法）或退回泛型集合本身（元素退化成 LinkedHashMap）都是 #65 复发
        assertEquals(OrderStockMessage.class, processMethod().getParameterTypes()[0],
                "order_queue 消费者的载荷参数必须是 OrderStockMessage（非泛型 POJO）");
    }

    @Test
    void dlxListener_mustBeMethodLevelAndTakeRawMessage() throws Exception {
        Method onDlx = OrderDlxConsumer.class.getMethod("onDlxMessage", Message.class, Channel.class, long.class);
        assertNotNull(onDlx.getAnnotation(RabbitListener.class),
                "死信监听注解必须写在方法上（方法级没有\"按载荷类型挑方法\"这一步）");
        assertNull(OrderDlxConsumer.class.getAnnotation(RabbitListener.class),
                "类上不得再有 @RabbitListener（否则又会回到按载荷类型挑方法）");
        assertNull(onDlx.getAnnotation(RabbitHandler.class),
                "方法上不得再有 @RabbitHandler");
        assertEquals(Message.class, onDlx.getParameterTypes()[0],
                "死信消费者必须收原始 Message（无论载荷是什么都能留痕）");
    }
}
