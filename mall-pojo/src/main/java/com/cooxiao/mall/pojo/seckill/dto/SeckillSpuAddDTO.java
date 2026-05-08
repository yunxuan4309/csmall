package com.cooxiao.mall.pojo.seckill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@ApiModel(value = "秒杀SPU新增DTO")
@Data
public class SeckillSpuAddDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "商品SPU id", required = true, example = "1")
    private Long spuId;

    @ApiModelProperty(value = "秒杀参考价", required = true, example = "6999.00")
    private BigDecimal listPrice;

    @ApiModelProperty(value = "秒杀开始时间", required = true, example = "2026-05-09 10:00:00")
    private LocalDateTime startTime;

    @ApiModelProperty(value = "秒杀结束时间", required = true, example = "2026-05-09 12:00:00")
    private LocalDateTime endTime;
}
