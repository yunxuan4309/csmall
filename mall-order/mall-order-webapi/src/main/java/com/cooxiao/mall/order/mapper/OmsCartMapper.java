package com.cooxiao.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cooxiao.mall.pojo.order.model.OmsCart;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OmsCartMapper extends BaseMapper<OmsCart> {
    //查询(判断)当前登录用户购物车中是否包含指定skuId的商品
    OmsCart selectExistCart(@Param("userId") Long userId, @Param("skuId") Long skuId);

    //新增sku信息到购物车表
    int saveCart(OmsCart omsCart);

    //修改购物车中sku商品数量
    int updateQuantityById(OmsCart omsCart);

    // 根据用户选中的一个或多个购物车商品id删除商品(支持批量删除)
    int deleteCartsByIds(Long[] ids);

    // 清空指定用户购物车中所有商品
    int deleteCartsByUserId(Long userId);

    // 根据用户Id和skuId删除购物车职工商品(新增订单业务中使用)
    int deleteCartByUserIdAndSkuId(OmsCart omsCart);
}
