package com.cooxiao.mall.order.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单项 MQ 消息体 — 异步扣库存时传递。
 */
@Data
public class OrderItemMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long skuId;
    private Integer quantity;
    private Long orderItemId;
}
