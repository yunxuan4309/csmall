package com.cooxiao.mall.pojo.ai.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CompareResultVO implements Serializable {

    @ApiModelProperty(value = "对比维度列表")
    private List<String> dimensions;

    @ApiModelProperty(value = "参与对比的商品列表")
    private List<ProductCompareItemVO> products;

    @ApiModelProperty(value = "AI综合推荐建议")
    private String summary;
}
