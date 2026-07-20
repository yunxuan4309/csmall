package com.cooxiao.mall.order.payment;

import com.cooxiao.mall.pojo.order.enums.PaymentTypeEnum;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentStrategyFactory {

    @Autowired(required = false)
    private List<PaymentStrategy> strategies;

    private final Map<PaymentTypeEnum, PaymentStrategy> strategyMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        if (strategies != null) {
            for (PaymentStrategy strategy : strategies) {
                strategyMap.put(strategy.getPaymentType(), strategy);
            }
        }
    }

    public PaymentStrategy getStrategy(Integer paymentType) {
        PaymentTypeEnum type = PaymentTypeEnum.fromCode(paymentType);
        if (type == null) {
            throw new IllegalArgumentException("不支持的支付方式: " + paymentType);
        }
        PaymentStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("支付渠道 [" + type.getDisplayName() + "] 暂未实现");
        }
        return strategy;
    }
}
