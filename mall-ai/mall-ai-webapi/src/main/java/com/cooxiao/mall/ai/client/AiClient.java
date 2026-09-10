package com.cooxiao.mall.ai.client;

import com.cooxiao.mall.ai.config.AiTask;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI API 客户端抽象接口。
 *
 * <p>调用方只声明<b>任务类型</b>（{@link AiTask}），由 {@code cooxiao.ai.tasks} 决定
 * 「档位 + 思考模式 + 温度 + max_tokens」—— <b>模型名不出现在调用方代码里</b>。
 *
 * <p>⚠️ 消息类型为 {@code List<Map<String, Object>>}（而非 {@code String}）：
 * Function Calling 场景下 assistant 消息要携带 {@code tool_calls} 数组、
 * {@code role:"tool"} 消息要携带 {@code tool_call_id}，纯 String 装不下（TODO #32 校正②）。
 *
 * <p>说明：本接口原先还有 {@code embed / embedBatch}（按"可切换 AI 供应商"设想）。
 * 实测全项目 embedding 一律走 {@code SiliconFlowEmbeddingClient}（不同供应商 + 不同 baseUrl），
 * 该两方法属<b>死代码</b>（且实现打的是 DeepSeek 地址 + 硅基流动模型名，真调用必失败），
 * 已于 2026-09-10 删除（TODO #58 §5.3）。
 */
public interface AiClient {

    /**
     * 对话 / 深度推理（{@link AiTask#CHAT}，思考模式开）。
     */
    String chat(String systemPrompt, String userMessage);

    /**
     * 对话 / 深度推理（{@link AiTask#CHAT}，思考模式开），自由 message 列表 —— 多轮对话与 Agent 工具轮使用。
     */
    String chat(List<Map<String, Object>> messages);

    /**
     * 按指定任务类型 + 简单消息（system + user）调用。
     */
    String chat(AiTask task, String systemPrompt, String userMessage);

    /**
     * 按指定任务类型调用（档位与思考模式由 {@code cooxiao.ai.tasks.<task>} 决定）。
     */
    String chat(AiTask task, List<Map<String, Object>> messages);

    /**
     * JSON 结构化任务（{@link AiTask#JSON}）：<b>思考模式关闭</b> + {@code response_format=json_object}。
     * <p>意图提取 / 重排 / 偏好提取用这个（查询扩展输出的是纯文本，走 {@link AiTask#EXPAND}）——
     * 从机制上避免 reasoning 挤空 content，不再依赖提示词"求它别想"。
     */
    String chatJson(String systemPrompt, String userMessage);

    /**
     * 流式对话（{@link AiTask#CHAT}，思考模式开 + SSE），逐片回调。
     * <p>并发闸门与预算记账均在实现内完成，调用方不需要重复处理。
     */
    void streamChat(List<Map<String, Object>> messages, Consumer<String> onChunk) throws Exception;
}
