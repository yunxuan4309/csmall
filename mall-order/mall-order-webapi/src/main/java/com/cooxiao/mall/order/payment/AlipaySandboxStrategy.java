package com.cooxiao.mall.order.payment;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.cooxiao.mall.pojo.order.enums.PaymentTypeEnum;
import com.cooxiao.mall.pojo.order.model.OmsOrder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@Slf4j
public class AlipaySandboxStrategy implements PaymentStrategy {

    @Autowired
    private AlipayConfig alipayConfig;

    private AlipayClient alipayClient;

    @PostConstruct
    public void init() {
        if (Boolean.TRUE.equals(alipayConfig.getSimulated())) {
            log.info("支付宝模拟模式，跳过 AlipayClient 初始化");
            return;
        }
        String privateKey = resolvePrivateKey();
        String publicKey = cleanPemKey(alipayConfig.getAlipayPublicKey());
        this.alipayClient = new DefaultAlipayClient(
                alipayConfig.getGateway(),
                alipayConfig.getAppId(),
                privateKey,
                alipayConfig.getFormat(),
                alipayConfig.getCharset(),
                publicKey,
                alipayConfig.getSignType()
        );
        log.info("支付宝沙箱客户端初始化完成，网关: {}", alipayConfig.getGateway());
    }

    /** 剥离 PEM 头尾和所有空白字符，SDK 需要裸 base64 */
    private String cleanPemKey(String key) {
        if (key != null && key.contains("-----BEGIN")) {
            key = key.replace("-----BEGIN PUBLIC KEY-----", "")
                     .replace("-----END PUBLIC KEY-----", "")
                     .replace("-----BEGIN PRIVATE KEY-----", "")
                     .replace("-----END PRIVATE KEY-----", "")
                     .replaceAll("\\s+", "");
        }
        return key;
    }

    private String resolvePrivateKey() {
        String content = null;
        // 优先从 classpath 文件加载（避免 YAML 多行字符串格式问题）
        if (alipayConfig.getMerchantPrivateKeyPath() != null
                && !alipayConfig.getMerchantPrivateKeyPath().isBlank()) {
            try {
                ClassPathResource resource = new ClassPathResource(alipayConfig.getMerchantPrivateKeyPath());
                try (InputStream is = resource.getInputStream()) {
                    content = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                    log.info("从 classpath 加载私钥文件: {}", alipayConfig.getMerchantPrivateKeyPath());
                }
            } catch (Exception e) {
                log.error("加载私钥文件失败: {}", alipayConfig.getMerchantPrivateKeyPath(), e);
                throw new RuntimeException("无法加载支付宝私钥文件: " + alipayConfig.getMerchantPrivateKeyPath(), e);
            }
        } else {
            content = alipayConfig.getMerchantPrivateKey();
        }

        // 剥离 PEM 头尾和所有空白字符，SDK 需要裸 base64
        if (content != null && content.contains("-----BEGIN")) {
            content = cleanPemKey(content);
            log.info("私钥 PEM 头已剥离，base64 长度: {}", content.length());
        }
        return content;
    }

    @Override
    public PaymentTypeEnum getPaymentType() {
        return PaymentTypeEnum.ALIPAY;
    }

    @Override
    public PaymentResult initiatePayment(OmsOrder order) {
        // 模拟模式：跳过支付宝 API，直接返回成功
        if (Boolean.TRUE.equals(alipayConfig.getSimulated())) {
            log.info("支付宝模拟支付模式，订单号: {}, 金额: {}", order.getSn(), order.getAmountOfActualPay());
            return PaymentResult.builder()
                    .success(true)
                    .tradeNo("SIM" + System.currentTimeMillis())
                    .outTradeNo(order.getSn())
                    .build();
        }

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setReturnUrl(alipayConfig.getReturnUrl());
        request.setNotifyUrl(alipayConfig.getNotifyUrl());

        com.alibaba.fastjson.JSONObject bizContent = new com.alibaba.fastjson.JSONObject();
        bizContent.put("out_trade_no", order.getSn());
        bizContent.put("total_amount", order.getAmountOfActualPay().toString());
        bizContent.put("subject", "CoolShark商城订单-" + order.getSn());
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        // qr_pay_mode=4: 嵌入式二维码模式，避免跳转到沙箱收银台（沙箱收银台页面有JS兼容bug）
        bizContent.put("qr_pay_mode", "4");
        bizContent.put("qrcode_width", "200");
        request.setBizContent(bizContent.toJSONString());

        try {
            AlipayTradePagePayResponse response = alipayClient.pageExecute(request);
            if (response.isSuccess()) {
                log.info("支付宝PC支付订单创建成功，订单号: {}", order.getSn());
                return PaymentResult.builder()
                        .success(true)
                        .outTradeNo(response.getOutTradeNo())
                        .paymentForm(response.getBody())
                        .build();
            } else {
                log.error("支付宝PC支付订单创建失败，订单号: {}, 错误: {} {}, {}",
                        order.getSn(), response.getCode(), response.getMsg(), response.getSubMsg());
                return PaymentResult.builder()
                        .success(false)
                        .errorMsg(response.getMsg() + (response.getSubMsg() != null ? " - " + response.getSubMsg() : ""))
                        .build();
            }
        } catch (AlipayApiException e) {
            log.error("支付宝支付请求异常，订单号: {}", order.getSn(), e);
            return PaymentResult.builder()
                    .success(false)
                    .errorMsg("支付宝接口调用异常: " + e.getErrMsg())
                    .build();
        }
    }

    @Override
    public PaymentCallbackResult handleCallback(Map<String, String> params) {
        try {
            boolean verified = AlipaySignature.rsaCheckV1(
                    params,
                    cleanPemKey(alipayConfig.getAlipayPublicKey()),
                    alipayConfig.getCharset(),
                    alipayConfig.getSignType()
            );

            if (!verified) {
                log.error("支付宝回调验签失败，参数: {}", params);
                return PaymentCallbackResult.builder()
                        .verified(false)
                        .responseText("failure")
                        .rawData(JSON.toJSONString(params))
                        .build();
            }

            String tradeStatus = params.get("trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                log.info("支付宝回调交易状态非成功: {}, 订单号: {}", tradeStatus, params.get("out_trade_no"));
                return PaymentCallbackResult.builder()
                        .verified(true)
                        .responseText("success")
                        .rawData(JSON.toJSONString(params))
                        .build();
            }

            return PaymentCallbackResult.builder()
                    .verified(true)
                    .tradeNo(params.get("trade_no"))
                    .outTradeNo(params.get("out_trade_no"))
                    .payAmount(new BigDecimal(params.get("total_amount")))
                    .buyerInfo(params.get("buyer_logon_id"))
                    .gmtPayment(parseAlipayDateTime(params.get("gmt_payment")))
                    .rawData(JSON.toJSONString(params))
                    .responseText("success")
                    .build();

        } catch (AlipayApiException e) {
            log.error("支付宝回调验签异常", e);
            return PaymentCallbackResult.builder()
                    .verified(false)
                    .responseText("failure")
                    .rawData(JSON.toJSONString(params))
                    .build();
        }
    }

    @Override
    public PaymentCallbackResult queryPayment(String outTradeNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        com.alibaba.fastjson.JSONObject bizContent = new com.alibaba.fastjson.JSONObject();
        bizContent.put("out_trade_no", outTradeNo);
        request.setBizContent(bizContent.toJSONString());

        try {
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            if (response.isSuccess() &&
                    ("TRADE_SUCCESS".equals(response.getTradeStatus())
                            || "TRADE_FINISHED".equals(response.getTradeStatus()))) {
                log.info("支付宝查单成功，订单号: {}, 交易号: {}, 状态: {}",
                        outTradeNo, response.getTradeNo(), response.getTradeStatus());
                return PaymentCallbackResult.builder()
                        .verified(true)
                        .tradeNo(response.getTradeNo())
                        .outTradeNo(response.getOutTradeNo())
                        .payAmount(new BigDecimal(response.getTotalAmount()))
                        .buyerInfo(response.getBuyerLogonId())
                        .gmtPayment(response.getSendPayDate() != null
                                ? response.getSendPayDate().toInstant()
                                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                                : LocalDateTime.now())
                        .rawData(JSON.toJSONString(response))
                        .responseText("success")
                        .build();
            } else if (response.isSuccess() && "WAIT_BUYER_PAY".equals(response.getTradeStatus())) {
                log.info("支付宝查单：等待买家付款，订单号: {}", outTradeNo);
                return PaymentCallbackResult.builder()
                        .verified(false)
                        .responseText("pending")
                        .build();
            } else {
                log.warn("支付宝查单失败，订单号: {}, 响应: {}", outTradeNo, response.getBody());
                return PaymentCallbackResult.builder()
                        .verified(false)
                        .responseText("failure")
                        .build();
            }
        } catch (AlipayApiException e) {
            log.error("支付宝查单异常，订单号: {}", outTradeNo, e);
            return PaymentCallbackResult.builder()
                    .verified(false)
                    .responseText("error: " + e.getErrMsg())
                    .build();
        }
    }

    private LocalDateTime parseAlipayDateTime(String gmtPayment) {
        if (gmtPayment == null) return null;
        try {
            return LocalDateTime.parse(gmtPayment, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            log.warn("解析支付宝支付时间失败: {}", gmtPayment, e);
            return LocalDateTime.now();
        }
    }
}
