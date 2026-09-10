package com.cooxiao.mall.ai.client;

import java.util.List;
import java.util.Map;

/**
 * 一轮「带工具」的 LLM 响应（TODO #32 P0 的返回值）。
 *
 * <p>Agent 循环靠 {@link #hasToolCalls()} 判断收敛：<b>没有工具调用 ⇒ 模型认为信息够了，可以生成最终答案</b>。
 *
 * @param content          模型文本（可能为 {@code null} —— 只发起工具调用、不附带说明时没有 content）
 * @param toolCalls        模型请求调用的工具；为空表示已收敛
 * @param assistantMessage 可直接回灌 {@code messages} 的 assistant 消息（含 {@code tool_calls} 原样结构）。
 *                         ⚠️ <b>刻意不携带 {@code reasoning_content}</b> —— 工具轮走 {@code thinking: disabled}，
 *                         本就没有思考内容；若某天把工具轮改成开思考，<b>必须回传 {@code reasoning_content} 否则接口 400</b>
 *                         （实测复现，见 [[AI模型名停用风险与thinking参数改造方案]] §3.1 实验 B/D）
 */
public record AiToolRound(String content, List<AiToolCall> toolCalls, Map<String, Object> assistantMessage) {

    /** 模型是否仍需要工具（false ⇒ 循环收敛，可以生成最终答案） */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
