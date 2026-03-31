package com.cooxiao.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cooxiao.mall.pojo.order.dto.OrderListTimeDTO;
import com.cooxiao.mall.pojo.order.model.OmsOrder;
import com.cooxiao.mall.pojo.order.vo.OrderListVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/21
 */
@Repository
public interface OmsOrderMapper extends BaseMapper<OmsOrder> {

    // 新增订单的方法
    int insertOrder(OmsOrder omsOrder);

    // 查询当前登录用户指定时间范围内所有订单信息（支持分页）
    IPage<OrderListVO> selectOrderBetweenTimes(Page<OrderListVO> page, @Param("dto") OrderListTimeDTO orderListTimeDTO);

    // 利用动态sql,实现对订单对象中指定字段的修改
    // 参数OmsOrder对象,其中必须包含id值,其它属性哪列有值就修改哪列
    int updateOrderById(OmsOrder order);
}
