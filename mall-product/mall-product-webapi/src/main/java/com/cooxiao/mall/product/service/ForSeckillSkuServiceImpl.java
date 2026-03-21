package com.cooxiao.mall.product.service;

import com.cooxiao.mall.pojo.product.vo.SkuStandardVO;
import com.cooxiao.mall.product.mapper.SkuMapper;
import com.cooxiao.mall.product.service.seckill.IForSeckillSkuService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@DubboService
@Service
public class ForSeckillSkuServiceImpl implements IForSeckillSkuService {
    @Autowired
    private SkuMapper skuMapper;
    @Override
    public SkuStandardVO getById(Long skuId) {
        return skuMapper.getById(skuId);
    }
}
