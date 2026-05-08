package com.cooxiao.mall.pojo.seckill.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@ApiModel(value = "秒杀SKU新增DTO")
@Data
public class SeckillSkuAddDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "商品SKU id", required = true, example = "1")
    private Long skuId;

    @ApiModelProperty(value = "秒杀SPU id(seckill_spu表的主键id)", required = true, example = "1")
    private Long spuId;

    @ApiModelProperty(value = "秒杀库存", required = true, example = "50")
    private Integer seckillStock;

    @ApiModelProperty(value = "秒杀价格", required = true, example = "6999.00")
    private BigDecimal seckillPrice;

    @ApiModelProperty(value = "限购数量,默认1", example = "1")
    private Integer seckillLimit;
}
