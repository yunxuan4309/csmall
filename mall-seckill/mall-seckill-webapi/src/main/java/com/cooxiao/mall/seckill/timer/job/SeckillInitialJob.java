package com.cooxiao.mall.seckill.timer.job;

import java.util.concurrent.ThreadLocalRandom;
import com.cooxiao.mall.pojo.seckill.model.SeckillSku;
import com.cooxiao.mall.pojo.seckill.model.SeckillSpu;
import com.cooxiao.mall.seckill.mapper.SeckillSkuMapper;
import com.cooxiao.mall.seckill.mapper.SeckillSpuMapper;
import com.cooxiao.mall.seckill.utils.SeckillCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/29
 */
@Service
@Slf4j
public class SeckillInitialJob implements Job {
    // 查询spu相关信息的mapper
    @Autowired
    private SeckillSpuMapper seckillSpuMapper;
    // 查询sku相关信息的mapper
    @Autowired
    private SeckillSkuMapper seckillSkuMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    /*
    RedisTemplate对象在保存数据到Redis时,会将数据进行序列化后再保存
    这样做的好处是有效利用空间,并且java在读取时也有很好的效率
    但是它的缺点就是不能在Redis内部对这个数据进行修改
    现在我们需要做的是保存秒杀sku的库存数,这个数据如果也用RedisTemplate保存
    就会容易在高并发的情况下出现线程安全问题,导致商品库存"超卖"
    解决办法就是需要一个能够在Redis内部直接修改数据的操作,避免线程安全问题,从而防止"超卖"
    使用的就是Spring Data redis框架提供的StringRedisTemplate类似对象
    它可以在Redis内部操作修改数据

    StringRedisTemplate只能向Redis保存字符串,如果字符串的内容是数值,就支持数值的增减
    因为是字符串,就没有序列化的过程,java代码可以直接操作Redis中数值的增减
    最后结合Redis内部操作数据线程只有一条的特征(Redis天生单线程),就能保证防止超卖了
    如果有多台Redis服务器构建了集群,想保证集群的数据同步,就需要使用redission分布式锁
     */
    // 装配能够在redis中直接进行数据增减操作的对象,防止超卖的发生
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        // 目标是将sku的库存数和随机码预热到redis中
        // 当前方法运行时,是秒杀开始前5分钟,所以我们创建一个5分钟后,也就是开始秒杀的时间
        LocalDateTime time=LocalDateTime.now().plusMinutes(5);
        // 用上面5分钟后的时间,去查询秒杀商品
        List<SeckillSpu> seckillSpus=
                seckillSpuMapper.findSeckillSpusByTime(time);
        log.debug("秒杀商品预热列表: {}", seckillSpus);
        // 遍历当前批次所有秒杀商品的spu列表
        for(SeckillSpu spu : seckillSpus){
            // 🔴 #64 修复（2026-09-12）：这里**必须用 seckill_spu 的主键 `getId()`** ——
            //   `seckill_sku.spu_id` 存的是 `seckill_spu.id`（秒杀内部 id，取值 1~6），
            //   而 `spu.getSpuId()` 是 **pms_spu 主键**（取值 1,2,5,6,15,20）。
            //   原实现拿 pms id 去查 `seckill_sku.spu_id` ⇒ 只有"两套 id 恰好同号"的少数 spu 能命中：
            //   实测命中 {1,2} ∪ {5,6} ∪ {26,27} ∪ {35,36} = 8 个 sku，**skus 11/12/13/14 从不被预热**
            //   （靠每天 03:30 的对账任务写出**永久 key** 兜住，TTL=-1，等于掩盖了这个 bug）。
            //   ⚠️ 随机码键仍用 pms id（下面 `getRandCodeKey(spu.getSpuId())` 是**对的**）：详情接口
            //   `/seckill/spu/{spuId}` 收的就是 pms 主键（VO.id 来自 pms 商品的 copyProperties）。
            List<SeckillSku> seckillSkus=seckillSkuMapper
                    .findSeckillSkusBySpuId(spu.getId());
            // 遍历seckillSkus集合,从集合中的sku对象中获取库存数,保存到redis
            for(SeckillSku sku : seckillSkus){
                log.info("开始将{}号sku库存数保存预热到redis",sku.getSkuId());
                // 要操作Redis,首先要确定这次操作的key,而这些key都是定义好的常量
                // SeckillCacheUtils.getStockKey就是获取库存常量前缀的方法
                // 方法的参数传入skuId,会追加到常量字符串的最后
                // 最后常量的名称可能为: mall:seckill:sku:stock:1
                String skuStockKey=
                        SeckillCacheUtils.getStockKey(sku.getSkuId());
                // 判断这个key是否已经存在于redis中
                if(redisTemplate.hasKey(skuStockKey)){
                    // 如果这个key已经存在,就证明之前已经保存过了,这里只记录日志即可
                    log.info("{}号sku的库存数,已经缓存过了",sku.getSkuId());
                }else{
                    // 如果这个key不存在,就要将数据库中sku的库存数保存到redis里
                    stringRedisTemplate.boundValueOps(skuStockKey).set(
                            sku.getSeckillStock()+"",//加引号变字符串
                            // 保存时间=秒杀持续时间2小时+提前的5分钟+防雪崩随机数30秒
                            1000*60*60*2+1000*60*5+ ThreadLocalRandom.current().nextInt(1000*30),
                            TimeUnit.MILLISECONDS);
                    log.info("{}号sku库存数成功预热到缓存!",sku.getSkuId());
                }
            }
            // 上面是sku的内存循环结束了,但是代码仍然在外层循环中
            // 正在遍历spu对象,我们生成的随机码和spu的Id关联
            // 随机码就是一个随机数,随机范围根据自身需求定义即可
            // 操作Redis先确定Key
            // randCodeKey:  mall:seckill:spu:url:rand:code:2
            String randCodeKey=SeckillCacheUtils.getRandCodeKey(spu.getSpuId());
            // 判断这个key是否已经在Redis中
            if(redisTemplate.hasKey(randCodeKey)){
                // 如果当前Redis已经有这个key了,实际开发跳过即可
                // 但是现在学习测试,需要在秒杀购买业务时获取这个随机码,所以输出一下
                int randCode=(int)redisTemplate.boundValueOps(randCodeKey).get();
                log.info("{}号spu商品的随机码已经缓存了,值为:{}",spu.getSpuId(),randCode);

            }else{
                // 如果Redis中没有这个key,就生成随机码保存到Redis
                // 我们制定一个随机码的范围100000-999999
                int randCode= ThreadLocalRandom.current().nextInt(900000)+100000;
                redisTemplate.boundValueOps(randCodeKey).set(
                        randCode,
                        // 保存时间=秒杀持续时间2小时+提前的5分钟+防雪崩随机数30秒
                        1000*60*60*2+1000*60*5+ThreadLocalRandom.current().nextInt(1000*30),
                        TimeUnit.MILLISECONDS);
                log.info("{}号spu商品的随机码生成保存完成!值为:{}",spu.getSpuId(),randCode);
            }
        }
    }
}
