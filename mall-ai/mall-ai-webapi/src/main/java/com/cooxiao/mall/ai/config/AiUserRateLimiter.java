package com.cooxiao.mall.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AI 每用户频控（TODO #34 层 5）
 *
 * 背景：日预算（2 元）防"总量冲超"，但防不了单用户短时间内刷爆——
 * 一个用户连续快速提问即可打满并发闸门/耗尽预算，影响其他用户。
 *
 * 机制：Redis INCR + TTL，key = ai:user:rate:{userId}:{当前 60s 窗口}，
 * 窗口 = epochSeconds / 60（自然分钟窗口，无跨进程时钟问题）。
 * 同用户 60 秒内超过 {@code cooxiao.ai.user-rate-limit} 次 → 抛 {@link AiBusyException}（429 语义）。
 *
 * 注意：单命令原子（INCR + 首次设 TTL），与项目"单命令原子"Redis 策略一致（见上下文文档 §6.6）。
 */
@Slf4j
@Component
public class AiUserRateLimiter {

    private static final String KEY_PREFIX = "ai:user:rate:";
    private static final long WINDOW_SECONDS = 60;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AiProperties aiProperties;

    /**
     * 校验当前用户是否超频。超限抛 {@link AiBusyException}。
     *
     * @param userId 用户 ID（未登录 0 时跳过——实际所有 /ai/** 都需登录，0 是防御分支）
     * @param scene  场景名（日志用）
     */
    public void checkRate(Long userId, String scene) {
        if (!aiProperties.isUserRateLimitEnabled()) {
            return;
        }
        if (userId == null || userId <= 0) {
            return;
        }
        int limit = aiProperties.getUserRateLimit();
        String key = buildKey(userId);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // 首次计数：设 TTL 到窗口结束（+1s 余量），避免 key 永久残留
            stringRedisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS + 1));
        }
        if (count != null && count > limit) {
            log.warn("【AI每用户频控】userId={} 场景={} 已调用 {} 次/{}s，超限 {} 次，拒绝",
                    userId, scene, count, WINDOW_SECONDS, limit);
            throw new AiBusyException("操作太频繁，请稍后再试");
        }
    }

    private String buildKey(Long userId) {
        long window = System.currentTimeMillis() / 1000 / WINDOW_SECONDS;
        return KEY_PREFIX + userId + ":" + window;
    }
}
