package com.cooxiao.mall.order.payment;

import lombok.Builder;
import lombok.Data;

/**
 * 发起支付后的返回结果
 */
@Data
@Builder
public class PaymentResult {

    /** 是否成功创建支付订单 */
    private Boolean success;

    /** 第三方交易号（预支付ID或支付宝trade_no） */
    private String tradeNo;

    /** 商户订单号（传给第三方的订单号） */
    private String outTradeNo;

    /** 支付表单HTML（支付宝电脑网站支付返回的自动提交表单） */
    private String paymentForm;

    /** 支付链接（微信Native支付返回的二维码URL等场景） */
    private String paymentUrl;

    /** 错误信息（支付创建失败时） */
    private String errorMsg;
}
