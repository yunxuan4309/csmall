package com.cooxiao.mall.ai.service;

import com.alibaba.fastjson.JSON;
import com.cooxiao.mall.pojo.ai.model.ChatSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 会话管理器
 * key: ai:chat:session:{sessionId}, TTL: 24h
 */
@Slf4j
@Service
public class SessionManager {

    private static final String KEY_PREFIX = "ai:chat:session:";
    private static final int TTL_HOURS = 24;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 创建新会话 */
    public ChatSession createSession(Long userId) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        ChatSession session = ChatSession.create(sessionId, userId);
        save(session);
        log.info("创建会话: sessionId={}, userId={}", sessionId, userId);
        return session;
    }

    /** 加载会话，不存在则返回 null */
    public ChatSession loadSession(String sessionId) {
        String json = stringRedisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(json, ChatSession.class);
        } catch (Exception e) {
            log.warn("会话 JSON 解析失败: sessionId={}", sessionId, e);
            return null;
        }
    }

    /** 保存或更新会话 */
    public void save(ChatSession session) {
        String key = KEY_PREFIX + session.getSessionId();
        stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(session),
                TTL_HOURS, TimeUnit.HOURS);
    }

    /** 删除会话 */
    public void delete(String sessionId) {
        stringRedisTemplate.delete(KEY_PREFIX + sessionId);
        log.info("删除会话: sessionId={}", sessionId);
    }
}
