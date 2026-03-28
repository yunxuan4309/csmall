package com.cooxiao.mall.seckill.service.impl;

import java.util.concurrent.ThreadLocalRandom;
import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.product.vo.SkuStandardVO;
import com.cooxiao.mall.pojo.seckill.model.SeckillSku;
import com.cooxiao.mall.pojo.seckill.vo.SeckillSkuVO;
import com.cooxiao.mall.product.service.seckill.IForSeckillSkuService;
import com.cooxiao.mall.seckill.mapper.SeckillSkuMapper;
import com.cooxiao.mall.seckill.service.ISeckillSkuService;
import com.cooxiao.mall.seckill.utils.RedisBloomUtils;
import com.cooxiao.mall.seckill.utils.SeckillCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/29
 */
@Service
@Slf4j
public class SeckillSkuServiceImpl implements ISeckillSkuService {

    @Autowired
    private SeckillSkuMapper seckillSkuMapper;
    // dubbo调用product查询sku常规信息
    @DubboReference
    private IForSeckillSkuService dubboSkuService;
    // 需要将sku对象保存到Redis
    @Autowired
    private RedisTemplate redisTemplate;

    // 装配操作布隆过滤器的对象
    @Autowired
    private RedisBloomUtils redisBloomUtils;


    @Override
    public List<SeckillSkuVO> listSeckillSkus(Long spuId) {
        // 这里应该先从Redis中获取布隆过滤器
        String bloomDayKey=SeckillCacheUtils
                .getBloomFilterKey(LocalDate.now());
        // 判断这个key是否存在
        if( ! redisTemplate.hasKey(bloomDayKey)){
            throw new CoolSharkServiceException(
                    ResponseCode.INTERNAL_SERVER_ERROR,"布隆过滤器未创建");
        }
        // 使用布隆过滤器判断参数spuId是否在数据库中存在,如果不存在直接抛出异常
        if( ! redisBloomUtils.bfexists(bloomDayKey,spuId+"")){
            // 进if表示当前spuId不在布隆过滤器中,布隆过滤器生效,防止了缓存穿透
            throw new CoolSharkServiceException(
                    ResponseCode.NOT_FOUND,"您访问的商品不存在(布隆过滤器生效)");
        }
        //以上为布隆过滤器判断某个spu是否在redis中,防止redis穿透;

        // 根据spuId查询sku列表
        List<SeckillSku> seckillSkus=seckillSkuMapper
                .findSeckillSkusBySpuId(spuId);
        // 声明一个集合,作为返回值,SeckillSkuVO是既包含常规信息又包含秒杀信息的对象
        List<SeckillSkuVO> seckillSkuVOs=new ArrayList<>();
        // 遍历从数据库查询出的秒杀sku列表
        for(SeckillSku sku : seckillSkus){
            // 后面多次使用skuId,取出备用
            Long skuId=sku.getSkuId();
            // 获得sku对应的key
            String skuVOKey= SeckillCacheUtils.getSeckillSkuVOKey(skuId);
            // 声明SeckillSkuVO类型对象
            SeckillSkuVO seckillSkuVO=null;
            // 判断redis中是否有这个key
            if(redisTemplate.hasKey(skuVOKey)){
                seckillSkuVO= (SeckillSkuVO)
                        redisTemplate.boundValueOps(skuVOKey).get();
            }else{
                // Redis中没有这个key,需要连接数据库查询常规信息
                // dubbo调用product模块查询sku信息的方法
                SkuStandardVO skuStandardVO=dubboSkuService.getById(skuId);
                // 秒杀信息就是上面循环正在遍历的sku对象
                // 先实例化现在为null的seckillSkuVO
                seckillSkuVO=new SeckillSkuVO();
                // 同名信息赋值
                BeanUtils.copyProperties(skuStandardVO,seckillSkuVO);
                // 手动赋值秒杀信息
                seckillSkuVO.setSeckillPrice(sku.getSeckillPrice());
                seckillSkuVO.setStock(sku.getSeckillStock());
                seckillSkuVO.setSeckillLimit(sku.getSeckillLimit());
                // 将赋好值的对象seckillSkuVO,保存到Redis中
                redisTemplate.boundValueOps(skuVOKey).set(
                        seckillSkuVO,
                        1000*60*5 + ThreadLocalRandom.current().nextInt(10000),
                        TimeUnit.MILLISECONDS);
            }
            // if-else结束后,seckillSkuVO一定已经被赋值了
            // 将它保存到seckillSkuVOs集合中
            seckillSkuVOs.add(seckillSkuVO);
        }
        // 最后千万别忘了返回!!!!
        return seckillSkuVOs;
    }
}
