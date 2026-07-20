package com.cooxiao.mall.order.controller;

import com.cooxiao.mall.order.payment.PaymentCallbackResult;
import com.cooxiao.mall.order.payment.PaymentStrategyFactory;
import com.cooxiao.mall.order.service.impl.OmsOrderServiceImpl;
import com.cooxiao.mall.pojo.order.enums.PaymentTypeEnum;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 支付回调控制器 —— 接收第三方支付平台的异步通知。
 * 注意：支付宝沙箱回调要求 notifyUrl 公网可访问，本地开发可用 ngrok 穿透。
 * 微信支付回调接口预留。
 */
@RestController
@RequestMapping("/payment/callback")
@Api(tags = "03. 支付回调模块")
@Slf4j
public class PaymentCallbackController {

    @Autowired
    private PaymentStrategyFactory paymentStrategyFactory;

    @Autowired
    private OmsOrderServiceImpl omsOrderService;

    /**
     * 支付宝异步通知回调
     * 注意：支付宝会以 POST application/x-www-form-urlencoded 方式发送参数
     */
    @PostMapping("/alipay/notify")
    @ApiOperation("支付宝支付异步回调通知")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        log.info("收到支付宝回调通知: {}", params);
        try {
            PaymentCallbackResult result = paymentStrategyFactory
                    .getStrategy(PaymentTypeEnum.ALIPAY.getCode())
                    .handleCallback(params);
            omsOrderService.handlePaymentCallback(result);
            return result.getResponseText();
        } catch (Exception e) {
            log.error("支付宝回调处理异常", e);
            return "failure";
        }
    }

    /**
     * 微信支付异步通知回调（预留）
     */
    @PostMapping("/wechat/notify")
    @ApiOperation("微信支付异步回调通知（预留）")
    public String wechatNotify(@RequestBody String body,
                               @RequestHeader Map<String, String> headers) {
        log.info("收到微信支付回调通知，body: {}, headers: {}", body, headers);
        // 后续接入微信支付时实现
        return "success";
    }
}
