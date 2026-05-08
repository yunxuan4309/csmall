package com.cooxiao.mall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cooxiao.mall.pojo.seckill.model.SeckillSku;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/29
 */
@Repository
public interface SeckillSkuMapper extends BaseMapper<SeckillSku> {
    // 根据SpuId查询sku列表
    List<SeckillSku> findSeckillSkusBySpuId(Long spuId);
    // 根据skuId减少秒杀库存数
    int updateReduceStockBySkuId(@Param("skuId") Long skuId,
                                 @Param("quantity") Integer quantity);
}
