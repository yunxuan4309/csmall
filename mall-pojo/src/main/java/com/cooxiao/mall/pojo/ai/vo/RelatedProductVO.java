package com.cooxiao.mall.pojo.ai.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class RelatedProductVO implements Serializable {

    @ApiModelProperty(value = "SPU ID")
    private Long spuId;

    @ApiModelProperty(value = "商品名称")
    private String name;

    @ApiModelProperty(value = "商品标题")
    private String title;

    @ApiModelProperty(value = "价格")
    private BigDecimal listPrice;

    @ApiModelProperty(value = "品牌名称")
    private String brandName;

    @ApiModelProperty(value = "分类名称")
    private String categoryName;

    @ApiModelProperty(value = "商品图片URL")
    private String picture;

    @ApiModelProperty(value = "标签")
    private String tags;

    @ApiModelProperty(value = "销量")
    private Integer sales;

    @ApiModelProperty(value = "匹配分数")
    private Double score;
}
