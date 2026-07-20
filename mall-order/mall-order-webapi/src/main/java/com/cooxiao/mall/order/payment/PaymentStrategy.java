package com.cooxiao.mall.order.payment;

import com.cooxiao.mall.pojo.order.enums.PaymentTypeEnum;
import com.cooxiao.mall.pojo.order.model.OmsOrder;

/**
 * 支付策略接口 —— 统一各种支付渠道的调用方式。
 * 后续接入微信支付等渠道时，只需新增实现类并注册到工厂即可。
 */
public interface PaymentStrategy {

    PaymentTypeEnum getPaymentType();

    /**
     * 发起支付
     * @param order 订单对象
     * @return 支付结果（包含跳转表单/二维码链接等）
     */
    PaymentResult initiatePayment(OmsOrder order);

    /**
     * 处理第三方异步回调，验签并返回标准化的回调结果
     * @param params 回调请求参数
     * @return 回调结果
     */
    PaymentCallbackResult handleCallback(java.util.Map<String, String> params);

    /**
     * 主动查询支付结果（用于收不到回调时前端轮询查单）
     * @param outTradeNo 商户订单号（即 oms_order.sn）
     * @return 查询结果，verified=false 表示未支付或查询失败
     */
    PaymentCallbackResult queryPayment(String outTradeNo);
}
