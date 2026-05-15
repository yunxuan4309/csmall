package com.cooxiao.mall.pojo.ai.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;

@Data
public class ChatSession implements Serializable {

    private String sessionId;
    private Long userId;
    private List<ChatMessage> messages = new ArrayList<>();
    private Map<String, Object> preferences = new HashMap<>();
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;

    public static ChatSession create(String sessionId, Long userId) {
        ChatSession s = new ChatSession();
        s.sessionId = sessionId;
        s.userId = userId;
        s.createdAt = LocalDateTime.now();
        s.lastActiveAt = LocalDateTime.now();
        return s;
    }

    /** 添加消息（超出 30 条则压缩前半部分） */
    public void addMessage(ChatMessage msg) {
        messages.add(msg);
        if (messages.size() > 30) {
            messages = new ArrayList<>(messages.subList(14, messages.size()));
        }
        lastActiveAt = LocalDateTime.now();
    }

    /** 合并偏好（新值覆盖旧值，null 不覆盖） */
    public void mergePreferences(Map<String, Object> newPrefs) {
        if (newPrefs != null) {
            newPrefs.forEach((k, v) -> {
                if (v != null && !"null".equals(v)) {
                    preferences.put(k, v);
                }
            });
        }
    }
}
