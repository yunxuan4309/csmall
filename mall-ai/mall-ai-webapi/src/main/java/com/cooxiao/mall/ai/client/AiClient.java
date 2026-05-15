package com.cooxiao.mall.ai.client;

import java.util.List;
import java.util.Map;

/**
 * AI API 客户端抽象接口
 * 支持切换不同的 AI 供应商（DeepSeek、通义千问等）
 */
public interface AiClient {

    /**
     * 调用 AI 聊天 API，返回文本回复
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return AI 回复文本
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * 调用 AI 聊天 API（自由 message 列表，用于多轮对话）
     */
    String chat(List<Map<String, String>> messages);

    /**
     * 调用 AI 聊天 API，指定模型 + 是否启用 JSON 模式
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @param model        模型名称（如 deepseek-v4-pro）
     * @param jsonMode     是否强制 JSON 输出
     * @return AI 回复文本
     */
    String chatWithModel(String systemPrompt, String userMessage, String model, boolean jsonMode);

    /**
     * 调用 Embedding API，将文本转为向量
     *
     * @param text 输入文本
     * @return float 数组向量
     */
    float[] embed(String text);

    /**
     * 批量调用 Embedding API
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    List<float[]> embedBatch(List<String> texts);
}
