package com.cooxiao.mall.order.service;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.order.dto.CartAddDTO;
import com.cooxiao.mall.pojo.order.dto.CartUpdateDTO;
import com.cooxiao.mall.pojo.order.model.OmsCart;
import com.cooxiao.mall.pojo.order.vo.CartStandardVO;

/**
 * <p>
 * 购物车数据表 服务类
 * </p>
 *
 * @author cooxiao.com
 * @since 2022-02-16
 */
public interface IOmsCartService{
    /**
     * 新增购物车
     * @param cartDTO
     */
    void addCart(CartAddDTO cartDTO);

    /**
     * 查询我的购物车
     * @param page
     * @param pageSize
     * @return
     */
    JsonPage<CartStandardVO> listCarts(Integer page, Integer pageSize);

    /**
     * 批量删除购物车
     * @param ids
     */
    void removeCart(Long[] ids);

    /**
     * 清空购物车
     */
    void removeAllCarts();

    /**
     *TODO 可以和removeAllCarts合并
     * @param omsCart
     */
    void removeUserCarts(OmsCart omsCart);
    /**
     * 更新购物车商品数量
     * @param cartUpdateDTO
     */
    void updateQuantity(CartUpdateDTO cartUpdateDTO);


}
