package com.cooxiao.mall.ai.service.impl;

import java.util.List;
import java.util.Map;

/**
 * 一次工具执行的结果（TODO #32 P0）。
 *
 * <p>拆成两部分，是因为它们**流向不同**：
 * <ul>
 *   <li>{@link #observation()} —— 回灌给模型的 {@code role:"tool"} 消息内容（模型据此决定下一步）；</li>
 *   <li>{@link #hits()} —— 原始 ES 文档，**只给 Java 侧用**，转成 {@code RelatedProductVO} 返回前端。</li>
 * </ul>
 * 工具不负责组装前端 VO，也不负责拼提示词 —— 各自只做一件事。
 *
 * @param observation 给模型看的观察结果（商品检索工具返回 JSON；失败返回可读的纯文本）
 * @param hits        本次命中的原始商品文档；无命中的工具返回空列表
 */
public record AiToolResult(String observation, List<Map<String, Object>> hits) {

    /** 无商品命中的工具结果（纯观察文本） */
    public static AiToolResult text(String observation) {
        return new AiToolResult(observation, List.of());
    }

    /** 工具执行失败 —— 不抛异常中断 Agent 循环，让模型自己决定改参数重试还是如实告知用户 */
    public static AiToolResult error(String message) {
        return new AiToolResult("工具执行失败：" + message, List.of());
    }
}
