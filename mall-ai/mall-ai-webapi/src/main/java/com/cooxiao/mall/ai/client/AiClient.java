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

    /**
     * 带工具的一次调用（Function Calling，{@link AiTask#AGENT}）—— Agent 循环的"一拍"。
     *
     * <p>请求体固定为：{@code tools} + {@code tool_choice=auto} + <b>{@code thinking=disabled}</b>，
     * 且 <b>绝不携带 {@code response_format}</b>（实测：二者同时出现则模型不返回 {@code tool_calls}，
     * 见 [[AI导购Agent升级方案]] §〇 校正⑦）。
     *
     * <p>调用方拿到 {@link AiToolRound} 后：有工具调用就执行并回灌 {@code assistantMessage} + {@code role=tool} 结果，
     * 没有工具调用就收敛到最终答案。轮数上限由调用方控制（{@code cooxiao.ai.agent-max-rounds}）。
     */
    AiToolRound chatWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools);

    /**
     * 同上，但**显式指定 `tool_choice`**：{@code "auto"}（默认）/ {@code "required"}（强制必须调工具）/ {@code "none"}。
     *
     * <p>用途：商品类问题在**第一轮**用 {@code "required"} 强制检索，避免模型"凭历史对话里的商品作答"——
     * 那样回答内容也许对，但**没有工具结果就没有商品卡片**，前端会空一块（2026-09-10 生产实测踩到）。
     * 后续轮次回到 {@code "auto"}，让模型自己决定收敛。
     */
    AiToolRound chatWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools, String toolChoice);

    /**
     * 带工具的<b>流式</b>调用（SSE）—— 流式 Agent 的一拍。
     *
     * <p>与 {@link #chatWithTools} 的区别：模型"边想边吐"的正文会**实时**通过 {@code onContentChunk} 回调出去，
     * 而 {@code tool_calls} 是**分片到达**的（{@code delta.tool_calls[].function.arguments} 需要按 index 拼接），
     * 实现里已累积成完整的 {@link AiToolRound} 返回。
     *
     * <p>因此调用方可以：正文直接转发给前端（用户看到逐字输出），工具调用按轮执行后继续下一拍。
     */
    AiToolRound streamChatWithTools(List<Map<String, Object>> messages,
                                    List<Map<String, Object>> tools,
                                    Consumer<String> onContentChunk) throws Exception;

    /** 同上，但显式指定 {@code tool_choice}（见 {@link #chatWithTools(List, List, String)}） */
    AiToolRound streamChatWithTools(List<Map<String, Object>> messages,
                                    List<Map<String, Object>> tools,
                                    String toolChoice,
                                    Consumer<String> onContentChunk) throws Exception;
}
