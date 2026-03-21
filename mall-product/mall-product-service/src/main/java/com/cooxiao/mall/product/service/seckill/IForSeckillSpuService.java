package com.cooxiao.mall.product.service.seckill;

import com.cooxiao.mall.pojo.product.vo.SpuDetailStandardVO;
import com.cooxiao.mall.pojo.product.vo.SpuStandardVO;

public interface IForSeckillSpuService {
    SpuStandardVO getSpuById(Long spuId);

    SpuDetailStandardVO getSpuDetailById(Long spuId);
}
