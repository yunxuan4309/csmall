package com.cooxiao.mall.seckill.task;

import com.alibaba.fastjson.JSON;
import com.cooxiao.mall.pojo.seckill.model.SeckillMessageRetry;
import com.cooxiao.mall.pojo.seckill.model.Success;
import com.cooxiao.mall.seckill.config.RabbitMqComponentConfiguration;
import com.cooxiao.mall.seckill.mapper.SeckillMessageRetryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MQ 消息重试定时任务
 * 每5秒扫描 seckill_message_retry 表中 status=0 且 retryCount<3 的记录
 * 重新发送到 RabbitMQ，成功则标记为已发送，失败则递增重试计数
 */
@Slf4j
@Component
public class MessageRetryTask {

    private static final int MAX_RETRIES = 3;

    @Autowired
    private SeckillMessageRetryMapper retryMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 5000)
    public void retryFailedMessages() {
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
    }
}
