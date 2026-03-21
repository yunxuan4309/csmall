package com.cooxiao.mall.seckill.timer.job;

import com.cooxiao.mall.seckill.mapper.SeckillSpuMapper;
import com.cooxiao.mall.seckill.utils.RedisBloomUtils;
import com.cooxiao.mall.seckill.utils.SeckillCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/10/16
 */
@Slf4j
public class SeckillBloomInitialJob implements Job {
    // 装配操作布隆过滤器的类
    @Autowired
    private RedisBloomUtils redisBloomUtils;
    // 装配能查询数据库中所有秒杀spu id的mapper
    @Autowired
    private SeckillSpuMapper seckillSpuMapper;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        // 这个方法要求将秒杀商品包中的spuId保存到布隆过滤器中
        // 在秒杀时减少缓存穿透,提升服务器运行效率
        // 为布隆过滤器生成的Key可以和日期关联,如果每天只有一次秒杀,直接使用日期即可
        // spu:bloom:filter:2023-4-12
        String bloomDayKey= SeckillCacheUtils
                .getBloomFilterKey(LocalDate.now());
        // 查询数据库中所有spuId
        Long[] spuIds=seckillSpuMapper.findAllSeckillSpuIds();
        // 从数据库查询出的数组类型是Long[],但是布隆过滤器只支持String[]
        // 所以我们要进行一个转换,将Long类型数组元素转换为String类型
        String[] spuIdsStr=new String[spuIds.length];
        // 对spuIds进行遍历,元素转换后赋值到spuIdsStr中
        for(int i=0;i<spuIds.length;i++){
            spuIdsStr[i]=spuIds[i]+"";
        }
        // 获取了包含所有与spuId的String数组,将它保存到布隆过滤器中
        redisBloomUtils.bfmadd(bloomDayKey,spuIdsStr);
        log.info("布隆过滤器加载数据完成!");
    }
}
