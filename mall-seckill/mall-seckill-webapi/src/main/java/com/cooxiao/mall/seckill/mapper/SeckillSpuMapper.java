package com.cooxiao.mall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.pojo.seckill.model.SeckillSpu;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/29
 */
@Repository
public interface SeckillSpuMapper extends BaseMapper<SeckillSpu> {
    // 查询秒杀商品列表
    IPage<SeckillSpu> findSeckillSpus(Page<SeckillSpu> page);

    // 根据给定时间,查询该时间正在进行秒杀的商品
    List<SeckillSpu> findSeckillSpusByTime(LocalDateTime time);

    // 根据 spuId,查询 spu 秒杀信息
    SeckillSpu findSeckillSpuById(@Param("spuId") Long spuId);

    // 布隆过滤器使用: 查询所有秒杀spu商品的spuId,返回Long[]
    Long[] findAllSeckillSpuIds();
}
