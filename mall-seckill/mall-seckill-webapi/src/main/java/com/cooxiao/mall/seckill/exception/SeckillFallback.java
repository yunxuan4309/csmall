package com.cooxiao.mall.seckill.exception;

import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.seckill.dto.SeckillOrderAddDTO;
import lombok.extern.slf4j.Slf4j;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/10/7
 */
// 秒杀业务的降级处理类
@Slf4j
public class SeckillFallback {
    // 降级处理方法基本和限流一致,只是参数修改为Throwable
    public static JsonResult seckillFallback(String randCode,
                                             SeckillOrderAddDTO seckillOrderAddDTO,
                                             Throwable e){
        log.error("某个请求因为发生异常降级了!");
        e.printStackTrace();
        return JsonResult.failed(
                ResponseCode.INTERNAL_SERVER_ERROR,
                "发生异常:错误信息为:"+e.getMessage());
    }
}
