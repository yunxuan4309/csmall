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

    @ApiModelProperty(value = "支付表单HTML（支付宝电脑网站支付时返回，前端需渲染为页面跳转），为null表示支付已直接完成")
    private String paymentForm;

    @ApiModelProperty(value = "支付链接（扫码支付场景的二维码URL），为null表示无需扫码")
    private String paymentUrl;
}
