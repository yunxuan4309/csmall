package com.cooxiao.mall.pojo.ai.vo;

import com.cooxiao.mall.pojo.ai.model.ChatMessage;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class ChatHistoryVO implements Serializable {

    @ApiModelProperty(value = "会话ID")
    private String sessionId;

    @ApiModelProperty(value = "对话历史")
    private List<ChatMessage> messages;

    @ApiModelProperty(value = "用户偏好")
    private Map<String, Object> preferences;
}
