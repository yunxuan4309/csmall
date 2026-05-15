package com.cooxiao.mall.pojo.ai.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class ChatResultVO implements Serializable {

    @ApiModelProperty(value = "会话ID（前端需保存，后续请求传入）")
    private String sessionId;

    @ApiModelProperty(value = "AI 回复内容")
    private String reply;

    @ApiModelProperty(value = "当前识别的用户偏好")
    private Map<String, Object> preferences;

    @ApiModelProperty(value = "相关商品推荐（与 RAG 问答一致）")
    private List<RelatedProductVO> relatedProducts;
}
