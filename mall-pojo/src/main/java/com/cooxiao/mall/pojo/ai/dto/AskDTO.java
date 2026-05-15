package com.cooxiao.mall.pojo.ai.dto;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class AskDTO implements Serializable {

    @ApiModelProperty(value = "用户问题", required = true, example = "3000元左右适合学生用的手机有哪些")
    @NotBlank(message = "问题不能为空")
    private String question;

    @ApiModelProperty(value = "返回结果数量，默认5", example = "5")
    private Integer topK = 5;
}
