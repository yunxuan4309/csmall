package com.cooxiao.mall.pojo.ai.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SuggestVO implements Serializable {

    @ApiModelProperty(value = "搜索建议列表")
    private List<String> suggestions;
}
