package com.cooxiao.mall.seckill.task;

import com.alibaba.fastjson.JSON;
import com.cooxiao.mall.pojo.seckill.model.SeckillMessageRetry;
import com.cooxiao.mall.pojo.seckill.model.Success;
import com.cooxiao.mall.seckill.config.RabbitMqComponentConfiguration;
import com.cooxiao.mall.seckill.mapper.SeckillMessageRetryMapper;
import com.cooxiao.mall.seckill.utils.RedisLockUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * MQ 消息重试定时任务
 * 每5秒扫描 seckill_message_retry 表中 status=0 且 retryCount<3 的记录
 * 重新发送到 RabbitMQ，成功则标记为已发送，失败则递增重试计数
 *
 * <p>集群化改造（TODO #4 阶段 0）：秒杀服务双实例部署后，本任务在两个实例上都会按 fixedDelay 触发。
 * 若不加锁，两实例会同时扫描到同一批 pending 记录 → 重复发送 MQ 消息。
 * 故用 Redis 分布式锁保证同一时刻只有一个实例执行；串行执行后，后一次扫描时前一次已把记录标记为
 * status=1（{@code updateStatusSent}），不会重复投递。
 */
@Slf4j
@Component
public class MessageRetryTask {

    private static final int MAX_RETRIES = 3;

    /** 分布式锁 key（双实例互斥） */
    private static final String LOCK_KEY = RedisLockUtils.key("message-retry");

    /** 锁 TTL：远大于单次扫描耗时，用于兜住进程被 kill 的情况 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    @Autowired
    private SeckillMessageRetryMapper retryMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisLockUtils redisLockUtils;

    @Scheduled(fixedDelay = 5000)
    public void retryFailedMessages() {
        String token = redisLockUtils.tryLock(LOCK_KEY, LOCK_TTL);
        if (token == null) {
            log.debug("未抢到消息重试任务锁，跳过本次执行（另一实例正在执行）");
            return;
        }
        try {
            List<SeckillMessageRetry> pendingList;
            try {
                pendingList = retryMapper.selectPending(MAX_RETRIES);
            } catch (Exception e) {
                log.debug("查询待重试消息失败（可能DB未就绪）: {}", e.getMessage());
                return;
            }

            if (pendingList.isEmpty()) return;

            log.info("发现 {} 条待重试消息", pendingList.size());

            for (SeckillMessageRetry msg : pendingList) {
                try {
                    Success success = JSON.parseObject(msg.getMessageBody(), Success.class);
                    rabbitTemplate.convertAndSend(
                            RabbitMqComponentConfiguration.SECKILL_EX,
                            RabbitMqComponentConfiguration.SECKILL_RK,
                            success);
                    retryMapper.updateStatusSent(msg.getId());
                    log.info("消息重试成功, orderSn={}", msg.getOrderSn());
                } catch (Exception e) {
                    log.warn("消息重试失败, id={}, orderSn={}, retry={}/{}: {}",
                            msg.getId(), msg.getOrderSn(),
                            msg.getRetryCount() + 1, MAX_RETRIES, e.getMessage());
                    retryMapper.incrementRetry(msg.getId(),
                            e.getMessage() != null ? e.getMessage().substring(0, Math.min(500, e.getMessage().length())) : "unknown");
                    if (msg.getRetryCount() + 1 >= MAX_RETRIES) {
                        retryMapper.updateStatusFailed(msg.getId());
                        log.error("消息已达最大重试次数, 标记为失败, orderSn={}", msg.getOrderSn());
                    }
                }
            }
        } finally {
            redisLockUtils.unlock(LOCK_KEY, token);
        }
    }
}
