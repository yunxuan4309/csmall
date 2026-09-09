package com.cooxiao.mall.seckill.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RedisLockUtils 集成测试（对本地 Redis 真实执行，不使用 Mockito）。
 *
 * <p>为什么不用 Mockito：Mockito 5 的 inline mock maker 依赖 JVM self-attach，在受限环境下会
 * 报 {@code Could not self-attach to current VM}；而锁的语义（SETNX 原子性、TTL、Lua CAS 释放）
 * 恰恰必须打真实 Redis 才有意义，所以这里直接连本地 Redis 验证。
 *
 * <p>本地 Redis 不可用时自动跳过（{@link Assumptions}），不会让无 Redis 环境构建失败。
 * 覆盖点：双实例互斥、释放后可再抢、错误 token 释放无效、TTL 生效并自动过期、参数校验。
 */
class RedisLockUtilsTest {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 6379;

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private String lockKey;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(HOST, PORT));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        lockKey = RedisLockUtils.key("unit-test-" + UUID.randomUUID());

        boolean redisAvailable;
        try {
            // 显式转型，避免 execute(RedisCallback) / execute(SessionCallback) 二义性
            String pong = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            redisAvailable = "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            redisAvailable = false;
        }
        Assumptions.assumeTrue(redisAvailable, "本地 Redis " + HOST + ":" + PORT + " 不可用，跳过锁集成测试");
    }

    @AfterEach
    void tearDown() {
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(lockKey);
            }
        } catch (Exception ignored) {
            // 清理失败不影响断言结果
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    @DisplayName("双实例互斥：A 抢到后 B 抢不到；A 释放后 B 可抢到")
    void mutualExclusion() {
        RedisLockUtils nodeA = new RedisLockUtils(redisTemplate);
        RedisLockUtils nodeB = new RedisLockUtils(redisTemplate);
        Duration ttl = Duration.ofSeconds(30);

        String tokenA = nodeA.tryLock(lockKey, ttl);
        assertNotNull(tokenA, "节点A 应抢到锁");

        assertNull(nodeB.tryLock(lockKey, ttl), "节点B 在锁被持有时必须抢不到");

        nodeA.unlock(lockKey, tokenA);
        assertNull(redisTemplate.opsForValue().get(lockKey), "释放后 key 应被删除");

        assertNotNull(nodeB.tryLock(lockKey, ttl), "节点A 释放后节点B 应能抢到");
    }

    @Test
    @DisplayName("错误 token 释放无效：拿别人的 token 删不掉锁（Lua CAS 保护）")
    void unlockWithWrongToken_doesNotRelease() {
        RedisLockUtils nodeA = new RedisLockUtils(redisTemplate);
        RedisLockUtils nodeB = new RedisLockUtils(redisTemplate);
        Duration ttl = Duration.ofSeconds(30);

        String tokenA = nodeA.tryLock(lockKey, ttl);
        assertNotNull(tokenA);

        nodeB.unlock(lockKey, "not-my-token");

        assertNotNull(redisTemplate.opsForValue().get(lockKey), "锁仍在（未被误删）");
        assertNull(nodeB.tryLock(lockKey, ttl), "锁仍被 A 持有，B 依然抢不到");
    }

    @Test
    @DisplayName("TTL 生效：写入即带过期时间，到期后可重新抢到")
    void ttlExpiresAndAllowsReacquire() throws InterruptedException {
        RedisLockUtils nodeA = new RedisLockUtils(redisTemplate);

        assertNotNull(nodeA.tryLock(lockKey, Duration.ofSeconds(1)));

        // 用毫秒精度断言：秒级 TTL 会被 Redis 向下取整，容易得到 0 而误判
        Long ttlMillis = redisTemplate.getExpire(lockKey, TimeUnit.MILLISECONDS);
        assertNotNull(ttlMillis);
        assertTrue(ttlMillis > 0 && ttlMillis <= 1000, "TTL 应为 (0,1000] 毫秒，实际=" + ttlMillis);

        Thread.sleep(1300);

        assertNull(redisTemplate.opsForValue().get(lockKey), "TTL 到期后 key 应自动消失");
        assertNotNull(nodeA.tryLock(lockKey, Duration.ofSeconds(30)), "过期后应能重新抢到（避免死锁）");
    }

    @Test
    @DisplayName("参数校验：key 为空或 TTL 非正数直接抛异常（防写入永不过期的死锁）")
    void invalidArgs() {
        RedisLockUtils lockUtils = new RedisLockUtils(redisTemplate);

        assertThrows(IllegalArgumentException.class, () -> lockUtils.tryLock(null, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> lockUtils.tryLock("", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> lockUtils.tryLock(lockKey, null));
        assertThrows(IllegalArgumentException.class, () -> lockUtils.tryLock(lockKey, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> lockUtils.tryLock(lockKey, Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("key() 统一前缀拼接")
    void keyPrefix() {
        assertEquals("mall:seckill:lock:message-retry", RedisLockUtils.key("message-retry"));
    }
}
