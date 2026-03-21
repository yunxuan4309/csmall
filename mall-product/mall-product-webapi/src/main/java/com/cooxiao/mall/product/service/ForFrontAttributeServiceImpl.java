package com.cooxiao.mall.product.service;

import com.cooxiao.mall.pojo.product.vo.AttributeStandardVO;
import com.cooxiao.mall.product.service.front.IForFrontAttributeService;
import com.cooxiao.mall.product.mapper.AttributeMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@DubboService
@Service
public class ForFrontAttributeServiceImpl implements IForFrontAttributeService {
    @Autowired
    private AttributeMapper attributeMapper;
    @Override
    public List<AttributeStandardVO> getSpuAttributesBySpuId(Long spuId) {
        return attributeMapper.selectAttributesBySpuId(spuId);
    }
}
