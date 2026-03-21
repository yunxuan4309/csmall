package com.cooxiao.mall.seckill.service;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.seckill.vo.SeckillSpuDetailSimpleVO;
import com.cooxiao.mall.pojo.seckill.vo.SeckillSpuVO;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author cooxiao.com
 * @since 2022-02-23
 */
public interface ISeckillSpuService {

    JsonPage<SeckillSpuVO> listSeckillSpus(Integer page, Integer pageSize);

    SeckillSpuVO getSeckillSpu(Long spuId);

    SeckillSpuDetailSimpleVO getSeckillSpuDetail(Long spuId);
}
