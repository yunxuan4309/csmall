package com.cooxiao.mall.ai.service;

import com.cooxiao.mall.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Token 预算控制服务
 * 通过 Redis 记录每日 AI API 调用花费，超出预算时拒绝服务
 */
@Slf4j
@Service
public class TokenBudgetService {

    private static final String KEY_PREFIX = "ai:daily_cost:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AiProperties aiProperties;

    /**
     * 检查是否已超出每日预算
     */
    public boolean isBudgetExceeded() {
        String key = buildKey();
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return false;
        }
        double currentCost = Double.parseDouble(value);
        boolean exceeded = currentCost >= aiProperties.getDailyBudget();
        if (exceeded) {
            log.warn("今日 AI 预算已超限：{}/{} 元", String.format("%.4f", currentCost),
                    aiProperties.getDailyBudget());
        }
        return exceeded;
    }

    /**
     * 记录一笔 Token 消耗（元）
     */
    public void record(double amount) {
        String key = buildKey();
        Double newValue = stringRedisTemplate.opsForValue().increment(key, amount);
        // 设置 TTL 到次日凌晨，避免 key 永远不删除
        stringRedisTemplate.expire(key, getSecondsUntilMidnight(), TimeUnit.SECONDS);
        log.debug("AI 费用累计：{} 元（今日累计：{} 元）", String.format("%.4f", amount),
                String.format("%.4f", newValue != null ? newValue : 0));
    }

    private String buildKey() {
        return KEY_PREFIX + LocalDate.now();
    }

    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight).getSeconds();
    }
}
