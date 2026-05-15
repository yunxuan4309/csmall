package com.cooxiao.mall.pojo.ai.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class ProductCompareItemVO implements Serializable {

    @ApiModelProperty(value = "SPU ID")
    private Long id;

    @ApiModelProperty(value = "商品名称")
    private String name;

    @ApiModelProperty(value = "商品图片URL")
    private String picture;

    @ApiModelProperty(value = "各维度的对比值，与dimensions顺序对应")
    private java.util.List<String> dimensionValues;
}
