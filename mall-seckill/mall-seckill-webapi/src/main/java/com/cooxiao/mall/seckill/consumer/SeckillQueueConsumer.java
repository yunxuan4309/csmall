package com.cooxiao.mall.seckill.consumer;

import com.cooxiao.mall.pojo.seckill.model.Success;
import com.cooxiao.mall.seckill.config.RabbitMqComponentConfiguration;
import com.cooxiao.mall.seckill.mapper.SeckillSkuMapper;
import com.cooxiao.mall.seckill.mapper.SuccessMapper;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/10/10
 */
@Component
@RabbitListener(queues = RabbitMqComponentConfiguration.SECKILL_QUEUE)
@Slf4j
public class SeckillQueueConsumer {
    @Autowired
    private SeckillSkuMapper seckillSkuMapper;
    @Autowired
    private SuccessMapper successMapper;

    @RabbitHandler
    @Transactional
    public void process(Success success, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){
        try {
            // 兼容旧消息：如果id为null，手动生成雪花算法ID
            if(success.getId() == null){
                success.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
            }
            // 扣减数据库中的秒杀库存,SQL中已添加seckill_stock>=#{quantity}防止超卖
            int rows = seckillSkuMapper.updateReduceStockBySkuId(
                    success.getSkuId(),success.getQuantity());
            if(rows == 0){
                log.warn("库存不足,扣减失败,skuId:{},订单号:{}",
                        success.getSkuId(), success.getOrderSn());
                channel.basicAck(deliveryTag, false);
                return;
            }
            // 新增success到数据库里
            successMapper.saveSuccess(success);
            // 手动确认消息处理成功
            channel.basicAck(deliveryTag, false);
            log.info("秒杀成功记录处理完成,订单号:{}", success.getOrderSn());
        } catch (Exception e) {
            log.error("秒杀成功记录处理异常,订单号:{},异常信息:{}",
                    success.getOrderSn(), e.getMessage());
            // 抛出异常让@Transactional回滚事务,确保库存扣减也被撤销
            throw new RuntimeException(e);
        }
    }
}
