package com.cooxiao.mall.seckill.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * Redis 分布式锁工具（秒杀集群化改造 —— TODO #4 阶段 0）。
 *
 * <p>背景：秒杀服务集群化（老机 10007 + 新机 10017）后，{@code @Scheduled} 定时任务会在两个实例上
 * <b>同时触发</b>。对"扫表 → 发 MQ → 标记已发"这类任务，两个实例同时扫描会拿到同一批待处理记录
 * → <b>重复发送消息</b>；对账任务则可能重复执行校准逻辑。故需分布式锁保证"同一时刻只有一个实例执行"。
 *
 * <p>实现要点（三个坑，缺一不可）：
 * <ol>
 *   <li><b>加锁原子</b>：{@code SET key token NX EX ttl} 一条命令完成"判断 + 写入 + 过期"，
 *       不能用 {@code SETNX} + {@code EXPIRE} 两步（中间宕机会留下永不过期的死锁）。</li>
 *   <li><b>TTL 兜底</b>：锁必须有过期时间，且远大于任务耗时——实例被 kill / 网络异常时锁自动释放，
 *       不会让任务永久停摆。</li>
 *   <li><b>释放要校验持有者</b>：解锁用 Lua 做"比较 token + 删除"的原子操作，避免删掉
 *       其他实例刚抢到的锁（先 GET 再 DEL 是非原子的，存在竞态）。</li>
 * </ol>
 *
 * <p>与项目现状的一致性：本项目未使用 Redis 事务（见上下文文档 §6.6），全部依赖"单命令原子 + 锁 + 补偿"，
 * 本类沿用该风格；Lua 脚本先例见 {@link RedisBloomUtils}，SETNX 先例见
 * {@code com.cooxiao.mall.common.annotation.IdempotentAspect}。
 *
 * <p>边界：单机 Redis 下该锁即有效；Redis 主从/哨兵切换瞬间的锁可靠性依赖 Redis 自身语义
 * （异步复制下极端情况可能短暂双持锁），对"定时任务不并发"这一目标足够。
 *
 * @since 2026-09-09
 */
@Slf4j
@Component
public class RedisLockUtils {

    /** 秒杀业务锁统一前缀（与 PrefixConfiguration.SeckillPrefixConfiguration 同风格） */
    public static final String SECKILL_LOCK_PREFIX = "mall:seckill:lock:";

    /**
     * 释放锁脚本：仅当 value 与调用方 token 一致才删除，返回 1 表示释放成功、0 表示不是自己的锁。
     * 用 Lua 保证"比较 + 删除"在 Redis 端原子执行。
     */
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisLockUtils(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 按业务名拼装锁 key（统一前缀，避免散落的硬编码字符串）。
     *
     * @param name 业务名，如 {@code message-retry}
     * @return 完整锁 key，如 {@code mall:seckill:lock:message-retry}
     */
    public static String key(String name) {
        return SECKILL_LOCK_PREFIX + name;
    }

    /**
     * 尝试加锁（非阻塞）。
     *
     * @param key 锁 key
     * @param ttl 锁自动过期时间（必须大于任务最坏耗时，用于兜住进程崩溃）
     * @return 加锁成功返回释放凭证 token；已被其他实例持有返回 {@code null}
     */
    public String tryLock(String key, Duration ttl) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("锁 key 不能为空");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("锁 TTL 必须为正数: " + ttl);
        }
        String token = UUID.randomUUID().toString();
        // SET key token NX EX ttl —— 单命令原子，Spring Data Redis 3.x 的 setIfAbsent(key, value, timeout)
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        if (Boolean.TRUE.equals(acquired)) {
            log.debug("抢锁成功, key={}, ttl={}s", key, ttl.getSeconds());
            return token;
        }
        return null;
    }

    /**
     * 释放锁（仅释放自己持有的锁；token 不匹配时不做任何事）。
     * 释放失败不抛异常——锁有 TTL 兜底，不应影响任务主流程。
     *
     * @param key   锁 key
     * @param token {@link #tryLock} 返回的释放凭证
     */
    public void unlock(String key, String token) {
        if (key == null || token == null) {
            return;
        }
        try {
            Long released = stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
            if (Long.valueOf(1L).equals(released)) {
                log.debug("释放锁成功, key={}", key);
            } else {
                log.debug("锁已不属于当前实例(可能已过期或被他人持有), key={}", key);
            }
        } catch (Exception e) {
            log.warn("释放锁异常，将由 TTL 自动兜底, key={}: {}", key, e.getMessage());
        }
    }
}
