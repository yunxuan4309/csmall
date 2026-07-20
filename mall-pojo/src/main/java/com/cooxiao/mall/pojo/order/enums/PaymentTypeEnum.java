package com.cooxiao.mall.pojo.order.enums;

import lombok.Getter;

@Getter
public enum PaymentTypeEnum {

    UNION_PAY(0, "银联"),
    WECHAT_PAY(1, "微信支付"),
    ALIPAY(2, "支付宝");

    private final Integer code;
    private final String displayName;

    PaymentTypeEnum(Integer code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public static PaymentTypeEnum fromCode(Integer code) {
        if (code == null) return null;
        for (PaymentTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
