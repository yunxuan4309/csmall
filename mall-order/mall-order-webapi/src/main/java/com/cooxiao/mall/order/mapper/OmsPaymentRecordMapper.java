package com.cooxiao.mall.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cooxiao.mall.pojo.order.model.PaymentRecord;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OmsPaymentRecordMapper extends BaseMapper<PaymentRecord> {

    int insertRecord(PaymentRecord record);

    PaymentRecord selectByOrderId(@Param("orderId") Long orderId);

    PaymentRecord selectByTradeNo(@Param("tradeNo") String tradeNo);

    int updateRecordById(PaymentRecord record);
}
