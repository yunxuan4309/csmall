package com.cooxiao.mall.order.payment;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付回调处理后的标准化结果
 */
@Data
@Builder
public class PaymentCallbackResult {

    /** 回调验签是否通过 */
    private Boolean verified;

    /** 第三方交易号 */
    private String tradeNo;

    /** 商户订单号（对应oms_order.sn） */
    private String outTradeNo;

    /** 实际支付金额（以第三方返回为准） */
    private BigDecimal payAmount;

    /** 付款方信息 */
    private String buyerInfo;

    /** 支付完成时间 */
    private LocalDateTime gmtPayment;

    /** 回调原始数据（JSON，用于落库） */
    private String rawData;

    /** 返回给第三方的响应文本（支付宝要求返回"success"） */
    private String responseText;
}
