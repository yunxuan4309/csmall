package com.cooxiao.mall.product.service;

import com.cooxiao.mall.pojo.product.vo.SpuDetailStandardVO;
import com.cooxiao.mall.pojo.product.vo.SpuStandardVO;
import com.cooxiao.mall.product.mapper.SpuDetailMapper;
import com.cooxiao.mall.product.mapper.SpuMapper;
import com.cooxiao.mall.product.service.seckill.IForSeckillSpuService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@DubboService
@Service
public class ForSeckillSpuServiceImpl implements IForSeckillSpuService {
    @Autowired
    private SpuMapper spuMapper;
    @Autowired
    private SpuDetailMapper spuDetailMapper;
    @Override
    public SpuStandardVO getSpuById(Long spuId) {
        return spuMapper.getById(spuId);
    }

    @Override
    public SpuDetailStandardVO getSpuDetailById(Long spuId) {
        return spuDetailMapper.getBySpuId(spuId);
    }
}
