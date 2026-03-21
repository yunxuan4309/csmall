package com.cooxiao.mall.seckill.exception;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.seckill.dto.SeckillOrderAddDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/10/7
 */
// 秒杀业务限流异常处理类
@Slf4j
public class SeckillBlockHandler {
    // 声明限流的方法,返回值和参数大多和被限流的控制器方法一致
    // 只是参数末尾要添加一个BlockException的参数
    // 当限流\降级方法编写的位置和控制器不同时,那么限流\降级方法要声明为static
    public static JsonResult seckillBlock(String randCode,
                                          SeckillOrderAddDTO seckillOrderAddDTO,
                                          BlockException e){
        log.error("限流了一个请求!");
        return JsonResult.failed(
                ResponseCode.INTERNAL_SERVER_ERROR,"服务器忙,请稍后再试");
    }
}
