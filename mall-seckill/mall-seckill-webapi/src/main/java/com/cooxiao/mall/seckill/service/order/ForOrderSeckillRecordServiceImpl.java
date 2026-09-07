package com.cooxiao.mall.seckill.service.order;

import com.cooxiao.mall.pojo.seckill.model.Success;
import com.cooxiao.mall.seckill.mapper.SuccessMapper;
import com.cooxiao.mall.seckill.service.order.IForOrderSeckillRecordService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 秒杀成交状态查询实现（供 order 模块支付前校验本单是否已落库 —— TODO #14 P0 方案Y）
 */
@DubboService
@Service
public class ForOrderSeckillRecordServiceImpl implements IForOrderSeckillRecordService {

    @Autowired
    private SuccessMapper successMapper;

    @Override
    public boolean isSeckillSuccessRecorded(String orderSn) {
        if (orderSn == null || orderSn.isBlank()) {
            return false;
        }
        Success success = successMapper.selectByOrderSn(orderSn);
        return success != null;
    }
}
