package com.cooxiao.mall.seckill.mapper;

import com.cooxiao.mall.pojo.seckill.model.SeckillSpu;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/29
 */
@Repository
public interface SeckillSpuMapper {
    // 查询秒杀商品列表
    List<SeckillSpu> findSeckillSpus();

    // 根据给定时间,查询该时间正在进行秒杀的商品
    List<SeckillSpu> findSeckillSpusByTime(LocalDateTime time);

    // 根据spuId,查询spu秒杀信息
    SeckillSpu findSeckillSpuById(Long spuId);

    // 布隆过滤器使用: 查询所有秒杀spu商品的spuId,返回Long[]
    Long[] findAllSeckillSpuIds();
}
