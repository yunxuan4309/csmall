package com.cooxiao.mall.ai.service.impl;

import java.util.Map;

/**
 * Agent 可调用的工具（TODO #32 P0，Function Calling）。
 *
 * <p>实现类放在 {@code service.impl} 包下是**刻意**的：检索类工具要复用
 * {@link RagServiceImpl} 的 {@code intentSearch / buildContext / buildRelatedProducts}，
 * 而这些方法是**包级私有**的 —— 换个包就得为了工具去放宽业务方法的可见性，得不偿失。
 *
 * <p>⚠️ 参数 {@code args} 来自模型输出，**永远不可信**：实现必须自己做边界收敛
 * （范围钳制、枚举白名单、字符串截断、上下限颠倒交换），不允许把模型给的数字直接拼进查询。
 */
public interface AiTool {

    /** 工具名 —— 模型按它发起调用，必须与 {@link #parameters()} 的声明一致（当前只有 search_products） */
    String name();

    /**
     * 工具说明 —— <b>这段文字直接决定模型会不会用、用得对不对</b>。
     * 要写清"什么时候该调用"，而不是只描述功能。
     */
    String description();

    /** 参数的 JSON Schema（{@code {type:object, properties:{...}, required:[...]}}） */
    Map<String, Object> parameters();

    /** 执行工具；失败请返回 {@link AiToolResult#error(String)}，不要抛异常 */
    AiToolResult execute(Map<String, Object> args);
}
