package com.cooxiao.mall.seckill.task;

import com.cooxiao.mall.pojo.seckill.model.SeckillMessageRetry;
import com.cooxiao.mall.seckill.mapper.SeckillMessageRetryMapper;
import com.cooxiao.mall.seckill.utils.RedisLockUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MessageRetryTask 分布式锁行为测试（TODO #4 阶段 0，不依赖 Mockito / Spring 容器 / 真实中间件）。
 *
 * <p>用手写替身（子类 + 动态代理）替代 Mockito：Mockito 5 的 inline mock maker 需要 JVM self-attach，
 * 在受限环境会失败；本测试只验证"锁分支 + finally 释放"的编排逻辑，替身足够。
 *
 * <p>验证两条关键行为：① 抢不到锁必须直接返回（不扫表、不发 MQ）；② 抢到锁时执行并在 finally 释放
 * （含扫描抛异常的路径），避免任务永久停摆。
 */
class MessageRetryTaskTest {

    /** 可编排的假锁：控制能否抢到，并记录释放调用 */
    private static class FakeLock extends RedisLockUtils {
        String tokenToReturn = "token-1";
        final List<String> releasedTokens = new ArrayList<>();

        FakeLock() {
            super(null);
        }

        @Override
        public String tryLock(String key, Duration ttl) {
            return tokenToReturn;
        }

        @Override
        public void unlock(String key, String token) {
            releasedTokens.add(token);
        }
    }

    /** 可编排的假 Mapper（动态代理，接口只有 5 个方法） */
    private static class FakeMapper {
        List<SeckillMessageRetry> pending = Collections.emptyList();
        RuntimeException selectError;
        int selectCalls;
        final List<Long> sentIds = new ArrayList<>();

        SeckillMessageRetryMapper proxy() {
            return (SeckillMessageRetryMapper) Proxy.newProxyInstance(
                    SeckillMessageRetryMapper.class.getClassLoader(),
                    new Class<?>[]{SeckillMessageRetryMapper.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "selectPending":
                                selectCalls++;
                                if (selectError != null) {
                                    throw selectError;
                                }
                                return pending;
                            case "updateStatusSent":
                                sentIds.add((Long) args[0]);
                                return 1;
                            case "incrementRetry":
                            case "updateStatusFailed":
                            case "insert":
                                return 0;
                            default:
                                return null;
                        }
                    });
        }
    }

    /** 假 RabbitTemplate：只记录发送的消息体 */
    private static class FakeRabbitTemplate extends RabbitTemplate {
        final List<Object> sent = new ArrayList<>();

        @Override
        public void convertAndSend(String exchange, String routingKey, Object object) {
            sent.add(object);
        }
    }

    private FakeLock fakeLock;
    private FakeMapper fakeMapper;
    private FakeRabbitTemplate fakeRabbit;
    private MessageRetryTask task;

    @BeforeEach
    void setUp() {
        fakeLock = new FakeLock();
        fakeMapper = new FakeMapper();
        fakeRabbit = new FakeRabbitTemplate();

        task = new MessageRetryTask();
        ReflectionTestUtils.setField(task, "redisLockUtils", fakeLock);
        ReflectionTestUtils.setField(task, "retryMapper", fakeMapper.proxy());
        ReflectionTestUtils.setField(task, "rabbitTemplate", fakeRabbit);
    }

    @Test
    @DisplayName("抢不到锁：直接返回，不扫描表、不发 MQ、不释放")
    void notAcquired_skipsEverything() {
        fakeLock.tokenToReturn = null;

        task.retryFailedMessages();

        assertEquals(0, fakeMapper.selectCalls, "未抢到锁不应扫描表");
        assertTrue(fakeRabbit.sent.isEmpty(), "未抢到锁不应发送消息");
        assertTrue(fakeLock.releasedTokens.isEmpty(), "未抢到锁不应调用释放");
    }

    @Test
    @DisplayName("抢到锁 + 无待重试：扫描一次并在 finally 释放锁")
    void acquired_scansAndReleasesLock() {
        task.retryFailedMessages();

        assertEquals(1, fakeMapper.selectCalls);
        assertEquals(List.of("token-1"), fakeLock.releasedTokens, "必须在 finally 释放锁");
        assertTrue(fakeRabbit.sent.isEmpty());
    }

    @Test
    @DisplayName("抢到锁 + 有 pending：重发 MQ 并标记已发送，最后释放锁")
    void acquired_withPending_resendsAndMarksSent() {
        SeckillMessageRetry msg = new SeckillMessageRetry();
        msg.setId(100L);
        msg.setOrderSn("SN-1");
        msg.setMessageBody("{}");
        msg.setRetryCount(0);
        fakeMapper.pending = Collections.singletonList(msg);

        task.retryFailedMessages();

        assertEquals(1, fakeRabbit.sent.size(), "应重发 1 条 MQ 消息");
        assertEquals(List.of(100L), fakeMapper.sentIds, "应标记该记录为已发送");
        assertEquals(List.of("token-1"), fakeLock.releasedTokens);
    }

    @Test
    @DisplayName("扫描抛异常：异常不冒泡，锁仍被释放（任务不会永久停摆）")
    void selectPendingThrows_stillReleasesLock() {
        fakeMapper.selectError = new RuntimeException("db down");

        assertDoesNotThrow(() -> task.retryFailedMessages());

        assertEquals(1, fakeMapper.selectCalls);
        assertEquals(List.of("token-1"), fakeLock.releasedTokens, "异常路径也必须释放锁");
    }
}
