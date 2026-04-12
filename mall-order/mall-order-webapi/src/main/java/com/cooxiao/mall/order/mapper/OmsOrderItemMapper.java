package com.cooxiao.mall.order.mapper;

import com.cooxiao.mall.pojo.order.model.OmsOrderItem;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/21
 */
@Repository
public interface OmsOrderItemMapper {
    // 新增订单项(oms_order_item)的方法
    // 一个订单可以包含多个商品,每个商品形成一行订单项数据
    // 如果一个订单中商品较多,就需要循环连接数据库进行新增,频繁连接数据库效率低
    // 想实现连接一次数据库新增多条数据,就需要动态sql,参数设计为List<OmsOrderItem>
    int insertOrderItemList(List<OmsOrderItem> omsOrderItems);

    // 根据订单id查询订单项列表
    List<OmsOrderItem> selectOrderItemsByOrderId(@Param("orderId") Long orderId);
}
