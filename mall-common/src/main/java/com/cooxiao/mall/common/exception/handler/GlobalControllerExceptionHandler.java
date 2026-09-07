package com.cooxiao.mall.common.exception.handler;

import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalControllerExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler({CoolSharkServiceException.class})
    public JsonResult<Void> handleCoolSharkServiceException(CoolSharkServiceException e) {
        log.debug("出现业务异常，业务错误码={}，描述文本={}", e.getResponseCode().getValue(), e.getMessage());
        e.printStackTrace();
        JsonResult<Void> result = JsonResult.failed(e);
        log.debug("即将返回：{}", result);
        return result;
    }

    /**
     * 处理绑定异常（通过Validation框架验证请求参数时的异常）
     */
    @ExceptionHandler(BindException.class)
    public JsonResult<Void> handleBindException(BindException e) {
        log.debug("验证请求数据时出现异常：{}", e.getClass().getName());
        e.printStackTrace();
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        JsonResult<Void> result = JsonResult.failed(ResponseCode.BAD_REQUEST, message);
        log.debug("即将返回：{}", result);
        return result;
    }

    /**
     * 处理 @RequestBody + @Valid/@Validated 校验失败异常
     * （Spring MVC 对 @RequestBody 校验失败抛 MethodArgumentNotValidException，
     *   若不单独处理会落入 Throwable → 返回 500 而非 400 —— TODO #23）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public JsonResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.debug("@RequestBody 校验失败：{}", e.getClass().getName());
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "请求参数校验失败！";
        JsonResult<Void> result = JsonResult.failed(ResponseCode.BAD_REQUEST, message);
        return result;
    }

    /**
     * 处理方法级/参数级校验失败异常（类级 @Validated + @RequestParam/@PathVariable 约束，
     *   或 GET 绑定 DTO 参数前 @Validated —— TODO #23）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public JsonResult<Void> handleConstraintViolationException(ConstraintViolationException e) {
        log.debug("参数校验失败：{}", e.getClass().getName());
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse("请求参数校验失败！");
        JsonResult<Void> result = JsonResult.failed(ResponseCode.BAD_REQUEST, message);
        return result;
    }

    /**
     * 处理系统（其它）异常
     */
    @ExceptionHandler({Throwable.class})
    public JsonResult<Void> handleSystemError(Throwable e) {
        log.debug("出现系统异常，异常类型={}，描述文本={}", e.getClass().getName(), e.getMessage());
        e.printStackTrace();
        JsonResult<Void> result = JsonResult.failed(ResponseCode.INTERNAL_SERVER_ERROR, e);
        log.debug("即将返回：{}", result);
        return result;
    }

}