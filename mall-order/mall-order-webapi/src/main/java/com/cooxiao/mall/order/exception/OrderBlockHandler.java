package com.cooxiao.mall.order.exception;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.order.dto.OrderAddDTO;
import com.cooxiao.mall.pojo.order.dto.PayOrderDTO;
import com.cooxiao.mall.pojo.order.vo.OrderAddVO;
import com.cooxiao.mall.pojo.order.vo.PayOrderVO;
import lombok.extern.slf4j.Slf4j;

/**
 * Sentinel 限流/熔断时的兜底处理。
 * 注意：blockHandler 方法签名必须与原始方法一致，且多一个 BlockException 参数。
 * 方法必须是 static 的。
 */
@Slf4j
public class OrderBlockHandler {

    public static JsonResult<OrderAddVO> addOrderBlock(OrderAddDTO orderAddDTO, BlockException e) {
        log.warn("新增订单接口被限流: {}", e.getRule().getResource());
        JsonResult<Void> failed = JsonResult.failed(ResponseCode.TOO_MANY_REQUESTS, "下单人数过多，请稍后再试");
        return new JsonResult<OrderAddVO>() {{
            setState(failed.getState());
            setMessage(failed.getMessage());
        }};
    }

    public static JsonResult<PayOrderVO> payOrderBlock(PayOrderDTO payOrderDTO, BlockException e) {
        log.warn("支付接口被限流: {}", e.getRule().getResource());
        JsonResult<Void> failed = JsonResult.failed(ResponseCode.TOO_MANY_REQUESTS, "支付人数过多，请稍后再试");
        return new JsonResult<PayOrderVO>() {{
            setState(failed.getState());
            setMessage(failed.getMessage());
        }};
    }
}
