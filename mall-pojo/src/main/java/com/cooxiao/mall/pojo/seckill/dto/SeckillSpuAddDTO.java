package com.cooxiao.mall.pojo.seckill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@ApiModel(value = "秒杀SPU新增DTO")
@Data
public class SeckillSpuAddDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private static final String MESSAGE_PREFIX = "新增秒杀SPU失败，";

    @ApiModelProperty(value = "商品SPU id", required = true, example = "1")
    @NotNull(message = MESSAGE_PREFIX + "请提供商品SPU id！")
    private Long spuId;

    @ApiModelProperty(value = "秒杀参考价", required = true, example = "6999.00")
    @NotNull(message = MESSAGE_PREFIX + "请填写秒杀参考价！")
    @DecimalMin(value = "0.01", message = MESSAGE_PREFIX + "秒杀参考价必须大于 0！")
    private BigDecimal listPrice;

    @ApiModelProperty(value = "秒杀开始时间", required = true, example = "2026-05-09 10:00:00")
    @NotNull(message = MESSAGE_PREFIX + "请填写秒杀开始时间！")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "秒杀结束时间", required = true, example = "2026-05-09 12:00:00")
    @NotNull(message = MESSAGE_PREFIX + "请填写秒杀结束时间！")
    private LocalDateTime endTime;
}
