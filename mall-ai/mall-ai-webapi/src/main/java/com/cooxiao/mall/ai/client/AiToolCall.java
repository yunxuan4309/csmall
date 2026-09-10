package com.cooxiao.mall.ai.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.Collections;
import java.util.Map;

/**
 * 一次工具调用请求（LLM 在 assistant 消息里返回的 {@code tool_calls} 元素）。
 *
 * <p>字段结构遵循 OpenAI 兼容协议：
 * {@code {"id":"call_1","type":"function","function":{"name":"search_products","arguments":"{\"keywords\":\"手机\"}"}}}
 *
 * <p>⚠️ {@code arguments} 是<b>字符串形式的 JSON</b>（不是对象），需要二次解析 —— 这是 Function Calling 最容易写错的地方。
 *
 * @param id             工具调用 id，回灌 {@code role:"tool"} 消息时必须原样带回（{@code tool_call_id}）
 * @param name           工具名（对应 {@link com.cooxiao.mall.ai.service.impl.AiTool#name()}）
 * @param argumentsJson  模型给出的参数（JSON 字符串，可能为空串）
 */
public record AiToolCall(String id, String name, String argumentsJson) {

    /** 解析参数为 Map；空串/非法 JSON 一律返回空 Map（由工具自身做参数校验） */
    public Map<String, Object> args() {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            JSONObject obj = JSON.parseObject(argumentsJson);
            return obj == null ? Collections.emptyMap() : obj;
        } catch (Exception e) {
            // 模型偶尔会给出非合法 JSON —— 不抛异常，交给工具的参数校验去拒绝
            return Collections.emptyMap();
        }
    }
}
