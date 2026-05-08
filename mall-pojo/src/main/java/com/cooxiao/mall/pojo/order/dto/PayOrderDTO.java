package com.cooxiao.mall.pojo.order.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

@ApiModel(value = "支付订单DTO")
@Data
public class PayOrderDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String VALIDATE_MESSAGE_PREFIX = "支付失败，";

    @ApiModelProperty(value = "订单id", example = "2000", required = true)
    @NotNull(message = VALIDATE_MESSAGE_PREFIX + "请提供订单id")
    private Long id;

    @ApiModelProperty(value = "支付方式,0=银联，1=微信，2=支付宝", example = "0", notes = "当前版本仅支持模拟支付，后续将接入微信和支付宝")
    private Integer paymentType;
}
