package com.cooxiao.mall.pojo.ai.dto;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class SearchDTO implements Serializable {

    @NotBlank(message = "搜索关键词不能为空")
    @ApiModelProperty(value = "搜索关键词", required = true, example = "学生党高性价比手机")
    private String keyword;

    @ApiModelProperty(value = "页码", example = "1")
    private Integer page = 1;

    @ApiModelProperty(value = "每页数量", example = "10")
    private Integer pageSize = 10;
}
