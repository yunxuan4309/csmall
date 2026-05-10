package com.cooxiao.mall.pojo.order.dto;

import com.cooxiao.mall.pojo.valid.order.OrderRegExpression;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@ApiModel(value="查询订单时间条件DTO")
public class OrderListTimeDTO implements OrderRegExpression,Serializable {
    @ApiModelProperty(value="查询订单起始时间",example = "默认一个月前")
    private LocalDateTime startTime;
    @ApiModelProperty(value="查询订单结束时间",example = "默认当前系统时间")
    private LocalDateTime endTime;
    @ApiModelProperty(value="用户id",example = "1")
    private Long userId;
    @ApiModelProperty(value="页数")
    private Integer page;
    @ApiModelProperty(value="条数")
    private Integer pageSize;
    @ApiModelProperty(value="订单状态，0=未支付，1=已关闭，2=已取消，3=已支付，4=已签收，5=已拒收，6=退款处理中，7=已退款")
    private Integer state;
}
