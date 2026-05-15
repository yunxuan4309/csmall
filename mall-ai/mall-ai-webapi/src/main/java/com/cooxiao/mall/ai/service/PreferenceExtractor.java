package com.cooxiao.mall.ai.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cooxiao.mall.ai.client.AiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户偏好提取器 — 让 AI 从对话历史中提取购物偏好（预算、用途、品牌等）
 * 只提取最近 3 轮对话，减少 token 消耗
 */
@Slf4j
@Service
public class PreferenceExtractor {

    @Autowired
    private AiClient aiClient;

    private static final String EXTRACT_PROMPT = """
            从以下最近的对话中提取用户的购物偏好。
            严格按 JSON 格式返回，不要添加任何额外内容。

            {
              "budget": 数字或null,
              "category": "类别名"或null,
              "brandPreference": "品牌名"或null,
              "purpose": "拍照"|"游戏"|"办公"|"学生"|"商务"|"日常"或null,
              "extraRequirements": "其他要求"或null
            }

            对话内容：
            %s
            """;

    @SuppressWarnings("unchecked")
    public Map<String, Object> extract(List<Map<String, String>> recentHistory) {
        if (recentHistory.isEmpty()) {
            return Map.of();
        }

        StringBuilder historyText = new StringBuilder();
        for (Map<String, String> msg : recentHistory) {
            historyText.append(msg.get("role")).append(": ")
                        .append(msg.get("content")).append("\n");
        }

        try {
            String response = aiClient.chat(EXTRACT_PROMPT.formatted(historyText),
                    "请按JSON格式输出用户偏好。");
            String cleaned = cleanResponse(response);
            JSONObject json = JSON.parseObject(cleaned);
            Map<String, Object> prefs = new HashMap<>();
            for (String key : json.keySet()) {
                prefs.put(key, json.get(key));
            }
            log.debug("提取偏好: {}", prefs);
            return prefs;
        } catch (Exception e) {
            log.warn("偏好提取失败，返回空", e);
            return Map.of();
        }
    }

    private String cleanResponse(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}
