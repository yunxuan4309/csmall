package com.cooxiao.mall.order.service;

import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.order.dto.OrderAddDTO;
import com.cooxiao.mall.pojo.order.dto.OrderListTimeDTO;
import com.cooxiao.mall.pojo.order.dto.OrderStateUpdateDTO;
import com.cooxiao.mall.pojo.order.dto.PayOrderDTO;
import com.cooxiao.mall.pojo.order.vo.OrderAddVO;
import com.cooxiao.mall.pojo.order.vo.OrderDetailVO;
import com.cooxiao.mall.pojo.order.vo.OrderListVO;
import com.cooxiao.mall.pojo.order.vo.PayOrderVO;

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

    /**
     * 支付订单
     * 当前版本为模拟支付，后续将接入微信/支付宝支付
     * @param payOrderDTO 支付参数（订单id、支付方式）
     * @return 支付结果
     */
    PayOrderVO payOrder(PayOrderDTO payOrderDTO);
}
