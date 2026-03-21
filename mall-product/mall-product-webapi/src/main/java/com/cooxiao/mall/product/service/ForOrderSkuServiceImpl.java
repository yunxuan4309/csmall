package com.cooxiao.mall.product.service;

import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.product.vo.SkuStandardVO;
import com.cooxiao.mall.product.mapper.SkuMapper;
import com.cooxiao.mall.product.service.order.IForOrderSkuService;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@DubboService
@Service
public class ForOrderSkuServiceImpl implements IForOrderSkuService {
    @Autowired
    private SkuMapper skuMapper;
    @Override
    @GlobalTransactional
    public int reduceStockNum(Long skuId, Integer quantity) {
        return skuMapper.updateStockById(skuId,quantity);
    }

    @Override
    public SkuStandardVO getById(Long id) {
        SkuStandardVO sku = skuMapper.getById(id);
        if (sku == null) {
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND, "获取SKU详情失败，尝试访问的数据不存在！");
        }
        return sku;
    }
}
