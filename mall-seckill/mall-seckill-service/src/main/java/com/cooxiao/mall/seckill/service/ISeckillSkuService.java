package com.cooxiao.mall.seckill.service;

import com.cooxiao.mall.pojo.seckill.vo.SeckillSkuVO;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author cooxiao.com
 * @since 2022-02-23
 */
public interface ISeckillSkuService {

    List<SeckillSkuVO> listSeckillSkus(Long spuId);
}
