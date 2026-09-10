package com.cooxiao.mall.ai.service;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Agent 动作审计（TODO #32-P1）—— 记录"AI 到底查了什么、查了几次、花了多久"。
 *
 * <p><b>为什么落 Redis 而不是数据库</b>：{@code mall-ai} <b>没有任何数据库栈</b>（无 JDBC / MyBatis / Flyway），
 * 为了审计给它引入整套持久化，与"无状态服务"的定位冲突。审计是**短周期、按会话**的数据，
 * Redis List + TTL 天然合适：{@code ai:agent:action:{sessionId}}，`LTRIM 0 49` 保留最近 50 条，7 天过期。
 *
 * <p>⚠️ <b>审计是"尽力而为"</b>：写失败只记 WARN，绝不因为审计把对话搞挂 —— 它是观察手段，不是业务链路。
 */
@Slf4j
@Service
public class AgentActionAuditor {

    private static final String KEY_PREFIX = "ai:agent:action:";

    /** 每个会话最多保留的动作条数（`LTRIM 0 49`） */
    private static final int MAX_ACTIONS = 50;

    private static final int TTL_DAYS = 7;

    /** 参数只留摘要，避免把整段 observation 级别的内容塞进审计 */
    private static final int MAX_ARG_LEN = 200;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 记一次工具调用。
     *
     * @param sessionId 会话 id（为空则不记）
     * @param round     第几轮工具调用（从 1 开始）
     * @param toolName  工具名
     * @param argsJson  模型给的参数（原始 JSON 字符串，会截断）
     * @param hitCount  结果条数
     * @param costMs    执行耗时（毫秒）
     * @param ok        是否成功（异常/未知工具为 false）
     */
    public void record(String sessionId, int round, String toolName, String argsJson,
                       int hitCount, long costMs, boolean ok) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("ts", LocalDateTime.now().format(TS));
            rec.put("round", round);
            rec.put("tool", toolName);
            rec.put("args", abbreviate(argsJson));
            rec.put("hits", hitCount);
            rec.put("costMs", costMs);
            rec.put("ok", ok);

            String key = KEY_PREFIX + sessionId;
            stringRedisTemplate.opsForList().leftPush(key, JSON.toJSONString(rec));
            stringRedisTemplate.opsForList().trim(key, 0, MAX_ACTIONS - 1);
            stringRedisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            // 审计失败不影响对话（例如 Redis 抖动）—— 这正是"尽力而为"的含义
            log.warn("Agent 动作审计写入失败（不影响对话）：{}", e.getMessage());
        }
    }

    /** 读取某会话的动作轨迹（最新在前）；运维/演示用，失败返回空列表 */
    public List<String> history(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = stringRedisTemplate.opsForList().range(KEY_PREFIX + sessionId, 0, -1);
            return list == null ? List.of() : new ArrayList<>(list);
        } catch (Exception e) {
            log.warn("读取 Agent 动作审计失败：{}", e.getMessage());
            return List.of();
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= MAX_ARG_LEN ? s : s.substring(0, MAX_ARG_LEN) + "…";
    }
}
