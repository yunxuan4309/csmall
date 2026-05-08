package com.cooxiao.mall.pojo.order.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@ApiModel(value = "支付结果VO")
@Data
public class PayOrderVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "订单id")
    private Long id;

    @ApiModelProperty(value = "订单编号")
    private String sn;

    @ApiModelProperty(value = "支付方式,0=银联，1=微信，2=支付宝")
    private Integer paymentType;

    @ApiModelProperty(value = "支付金额")
    private BigDecimal payAmount;

    @ApiModelProperty(value = "支付时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime gmtPay;

    @ApiModelProperty(value = "订单状态")
    private Integer state;
}
