package com.cooxiao.mall.pojo.order.enums;

import lombok.Getter;

@Getter
public enum PaymentStatusEnum {

    PENDING(0, "待支付"),
    SUCCESS(1, "支付成功"),
    FAILED(2, "支付失败"),
    CLOSED(3, "已关闭"),
    REFUNDED(4, "已退款");

    private final Integer code;
    private final String displayName;

    PaymentStatusEnum(Integer code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public static PaymentStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (PaymentStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}
