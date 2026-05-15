package com.cooxiao.mall.pojo.ai.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class AskResultVO implements Serializable {

    @ApiModelProperty(value = "AI 回答内容")
    private String answer;

    @ApiModelProperty(value = "相关的商品列表")
    private List<RelatedProductVO> relatedProducts;
}
