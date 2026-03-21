package com.cooxiao.mall.product.service.front;

import com.cooxiao.mall.pojo.product.vo.AttributeStandardVO;

import java.util.List;

public interface IForFrontAttributeService {
    /**
     * 利用spuId 查询对应所有属性的数据
     * @param spuId
     * @return
     */
    List<AttributeStandardVO> getSpuAttributesBySpuId(Long spuId);
}
