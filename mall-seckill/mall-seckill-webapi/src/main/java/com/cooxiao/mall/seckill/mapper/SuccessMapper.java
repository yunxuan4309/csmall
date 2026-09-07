package com.cooxiao.mall.seckill.mapper;

import com.cooxiao.mall.pojo.seckill.model.Success;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/10/10
 */
@Repository
public interface SuccessMapper {
    // 新增Success对象到数据库的方法
    int saveSuccess(Success success);

    /** 根据订单号查询秒杀成功记录（order 模块支付前校验本单是否已落库 —— TODO #14 P0 方案Y） */
    Success selectByOrderSn(@Param("orderSn") String orderSn);
}
