package com.cooxiao.mall.pojo.ai.dto;

import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class ChatSendDTO implements Serializable {

    @ApiModelProperty(value = "会话ID（首次传 null 或不传，后端自动创建）")
    private String sessionId;

    @NotBlank(message = "消息不能为空")
    @ApiModelProperty(value = "用户消息", required = true, example = "我想买一款3000元左右的手机")
    private String message;
}
