package com.cooxiao.mall.pojo.ai.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SearchResultVO implements Serializable {

    @ApiModelProperty(value = "搜索结果商品列表")
    private List<RelatedProductVO> products;

    @ApiModelProperty(value = "AI 排序说明，解释为什么这样推荐")
    private String aiExplanation;

    @ApiModelProperty(value = "总匹配数")
    private Long totalCount;
}
