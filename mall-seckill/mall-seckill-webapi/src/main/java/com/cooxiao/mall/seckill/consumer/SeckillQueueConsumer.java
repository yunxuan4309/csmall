package com.cooxiao.mall.seckill.consumer;

import com.cooxiao.mall.pojo.seckill.model.Success;
import com.cooxiao.mall.seckill.config.RabbitMqComponentConfiguration;
import com.cooxiao.mall.seckill.mapper.SeckillSkuMapper;
import com.cooxiao.mall.seckill.mapper.SuccessMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    // 下面的方法会在类上注解中标记的队列接到消息时运行
    // 方法的参数就是接收到消息的内容
    @RabbitHandler
    public void process(Success success){
        // 连个数据库操作没有必然先后顺序,这里先写库存的减少了
        seckillSkuMapper.updateReduceStockBySkuId(
                success.getSkuId(),success.getQuantity());
        // 新增success到数据库里
        successMapper.saveSuccess( success);
        // 如果上面两个数据库操作其中有异常
        // 可能会引发事务问题,如果本次统计不需要精确数据,不处理也行
        // 如果需要处理,可以将上面代码使用try-catch管理,在catch中进行重试操作
        // 如果在重试操作中还失败,就可以将错误信息发送给"死信队列"
        // 因为"死信队列"是人工处理的,所以需要较多人力资源,而且修改不是非常及时,实际开发中慎用
    }
}
