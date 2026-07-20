package com.cooxiao.mall.order.config;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sentinel 限流/熔断全局异常处理。
 * 当请求被 Sentinel 拦截时，返回统一 JSON 格式而非默认错误页。
 */
@RestControllerAdvice
@Slf4j
public class SentinelBlockHandler {

    @ExceptionHandler(BlockException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public JsonResult<Void> handleBlock(BlockException e) {
        log.warn("Sentinel 限流拦截: {}", e.getRule().getResource());
        return JsonResult.failed(ResponseCode.TOO_MANY_REQUESTS, "系统繁忙，请稍后再试");
    }
}
