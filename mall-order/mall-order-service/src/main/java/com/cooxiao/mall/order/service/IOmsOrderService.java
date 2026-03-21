package com.cooxiao.mall.order.service;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.order.dto.OrderAddDTO;
import com.cooxiao.mall.pojo.order.dto.OrderListTimeDTO;
import com.cooxiao.mall.pojo.order.dto.OrderStateUpdateDTO;
import com.cooxiao.mall.pojo.order.vo.OrderAddVO;
import com.cooxiao.mall.pojo.order.vo.OrderDetailVO;
import com.cooxiao.mall.pojo.order.vo.OrderListVO;

/**
 * <p>
 * 订单数据表 服务类
 * </p>
 *
 * @author cooxiao.com
 * @since 2022-02-16
 */
public interface IOmsOrderService{
    /**
     * 新增订单
     * @param orderAddDTO
     * @return 订单编号
     */
    OrderAddVO addOrder(OrderAddDTO orderAddDTO);

    /**
     * 更新订单状态
     * @param orderStateUpdateDTO
     */
    void updateOrderState(OrderStateUpdateDTO orderStateUpdateDTO);

    /**
     * 根据起始结束时间查询订单列表
     * @param orderListTimeDTO
     */
    JsonPage<OrderListVO> listOrdersBetweenTimes(OrderListTimeDTO orderListTimeDTO);

    /**
     * 根据sn查询订单详细信息
     * @param id
     * @return
     */
    OrderDetailVO getOrderDetail(Long id);
}
