package com.cooxiao.mall.order.mq;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * {@code order_queue} 的消息体 —— 库存扣减任务（<b>非泛型 POJO 包装</b>）。
 *
 * <p>🔴 <b>TODO #65 修复（2026-09-12）：为什么必须包一层</b>
 *
 * <p>Spring AMQP 的 {@code Jackson2JsonMessageConverter} 在发送时会把<b>载荷的运行时类</b>
 * 写进 {@code __TypeId__} 头，消费端"类级 {@code @RabbitListener} + {@code @RabbitHandler}"
 * 会先按这个头把消息反序列化成对象、<b>再按对象类型挑处理方法</b>。
 *
 * <p>原先生产端直接发 {@code List<OrderItemMessage>} ⇒ {@code __TypeId__ = java.util.ArrayList}：
 * <ul>
 *   <li>方法参数写成 {@code String} ⇒ 挑不中方法（{@code No listener method found ... for class java.util.ArrayList}）；</li>
 *   <li>方法参数写成 {@code List<OrderItemMessage>} 也<b>不够</b> —— 实测
 *       {@link org.springframework.amqp.support.converter.SmartMessageConverter#fromMessage}
 *       不会按方法参数推断泛型，元素会退化成 {@code LinkedHashMap}，
 *       运行到 {@code item.getSkuId()} 时抛 {@code ClassCastException}（回归测试
 *       {@code OrderQueuePayloadContractTest} 已复现并锁死这个结论）。</li>
 * </ul>
 *
 * <p>⇒ 采用本工程<b>已被生产验证过</b>的写法：载荷是<b>非泛型 POJO</b>
 * （对照秒杀链路的 {@code Success}，其消费端参数就是 POJO 类型且线上正常）。
 * 泛型集合放进 POJO 的字段里，由 Jackson 按 {@code __TypeId__ = OrderStockMessage} 正常绑定。
 *
 * <p>⚠️ 生产端（{@code OmsOrderServiceImpl}）与消费端（{@code OrderQueueConsumer}）
 * 在<b>同一个 jar</b> 内 ⇒ 必须一起发布；发布前确认 {@code order_queue} 无积压旧格式消息。
 */
@Data
public class OrderStockMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 本次下单涉及的全部订单项（每项含 skuId / quantity / orderItemId） */
    private List<OrderItemMessage> items;

    public OrderStockMessage() {
    }

    public OrderStockMessage(List<OrderItemMessage> items) {
        this.items = items;
    }
}
