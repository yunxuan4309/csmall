package com.cooxiao.mall.pojo.order.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("oms_payment_record")
public class PaymentRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;

    private String orderSn;

    private Long userId;

    private Integer paymentType;

    private BigDecimal payAmount;

    private String tradeNo;

    private String outTradeNo;

    private Integer paymentStatus;

    private String buyerInfo;

    private String callbackLog;

    private BigDecimal refundAmount;

    private String refundTradeNo;

    private String extraData;

    private LocalDateTime gmtRequest;

    private LocalDateTime gmtPayment;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;
}
