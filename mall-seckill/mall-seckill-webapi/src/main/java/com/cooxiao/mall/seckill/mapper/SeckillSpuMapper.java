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
    // ⚠️ 注意语义：这里的 `spuId` 是 **pms_spu 主键**（SQL 为 `where spu_id=#{spuId}`），**不是本表主键**
    SeckillSpu findSeckillSpuById(@Param("spuId") Long spuId);

    /**
     * 🔴 #69（2026-09-12）：按 **seckill_spu 主键 id** 反查 **pms_spu 主键**。
     * <p>起因：`seckill_sku.spu_id` 存的是 `seckill_spu.id`（秒杀表内部 id，取值 1~6），
     * 而 `incrementSales` 走的是 `UPDATE pms_spu SET sales=sales+1 WHERE id=#{spuId}`
     * ⇒ 直接把内部 id 喂进去会给**另一个命名空间的同号商品**加销量。
     * 实测两套 id：seckill 内部 id = 1..6；pms 主键 = 1,2,5,6,15,20。</p>
     */
    Long findPmsSpuIdBySeckillId(@Param("id") Long id);

    // 布隆过滤器使用: 查询所有秒杀spu商品的spuId,返回Long[]
    Long[] findAllSeckillSpuIds();
}
