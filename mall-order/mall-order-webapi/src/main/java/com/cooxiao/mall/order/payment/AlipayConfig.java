package com.cooxiao.mall.order.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {

    /** 沙箱 APPID（沙箱环境自动分配） */
    private String appId;

    /** 支付宝网关：沙箱=openapi.alipaydev.com，生产=openapi.alipay.com */
    private String gateway = "https://openapi.alipaydev.com/gateway.do";

    /** 应用私钥内容（PKCS#8 PEM 格式，直接配置此项，与 privateKeyPath 二选一） */
    private String merchantPrivateKey;

    /** 应用私钥文件路径（classpath 路径，如 alipay_private_key.pem，与 merchantPrivateKey 二选一） */
    private String merchantPrivateKeyPath;

    /** 支付宝公钥（从沙箱页面复制，用于验签） */
    private String alipayPublicKey;

    /** 签名算法，默认 RSA2 */
    private String signType = "RSA2";

    /** 异步通知地址（必须公网可访问，本地开发用 ngrok） */
    private String notifyUrl;

    /** 同步跳转地址（支付完成后页面跳转） */
    private String returnUrl;

    /** 字符编码 */
    private String charset = "UTF-8";

    /** 数据格式 */
    private String format = "json";

    /** 模拟支付模式：true=跳过真实API，直接模拟支付成功（沙箱不可用时的开发测试方案） */
    private Boolean simulated = false;
}
