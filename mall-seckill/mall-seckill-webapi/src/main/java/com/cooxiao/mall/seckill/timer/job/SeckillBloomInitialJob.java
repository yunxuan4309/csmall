package com.cooxiao.mall.seckill.timer.job;

import com.cooxiao.mall.seckill.mapper.SeckillSpuMapper;
import com.cooxiao.mall.seckill.utils.SeckillCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/10/16
 */
@Slf4j
public class SeckillBloomInitialJob implements Job {
    // 装配Redis操作模板
    @Autowired
    private RedisTemplate redisTemplate;
    // 装配能查询数据库中所有秒杀spu id的mapper
    @Autowired
    private SeckillSpuMapper seckillSpuMapper;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        // 这个方法要求将秒杀商品包中的spuId保存到Redis Set中
        // 在秒杀时减少缓存穿透,提升服务器运行效率
        // 使用Redis Set替代布隆过滤器,所有Redis版本均支持
        // 为Set生成的Key可以和日期关联
        // spu:bloom:filter:2023-4-12
        String bloomDayKey= SeckillCacheUtils
                .getBloomFilterKey(LocalDate.now());
        // 查询数据库中所有spuId
        Long[] spuIds=seckillSpuMapper.findAllSeckillSpuIds();
        // 如果数据库中没有秒杀商品，跳过加载
        if(spuIds == null || spuIds.length == 0){
            log.warn("当前没有秒杀商品数据，跳过缓存加载");
            return;
        }
        // 将所有spuId添加到Redis Set中
        for(Long spuId : spuIds){
            redisTemplate.boundSetOps(bloomDayKey).add(spuId+"");
        }
        // 设置过期时间为2天,确保第二天数据也能正常使用
        redisTemplate.expire(bloomDayKey,2, TimeUnit.DAYS);
        log.info("秒杀商品缓存加载完成!共加载{}个spuId", spuIds.length);

        // TODO 部署到Linux服务器安装RedisBloom模块后,替换为布隆过滤器实现:
        // redisBloomUtils.bfmadd(bloomDayKey,spuIdsStr);
    }
}
