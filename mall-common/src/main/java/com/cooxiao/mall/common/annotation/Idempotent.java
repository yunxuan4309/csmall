package com.cooxiao.mall.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等性注解 — 基于 Redis SETNX 防止重复提交。
 * 加在 Controller 方法上即可，AOP 自动拦截。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /** Redis key 前缀，用于区分不同业务场景 */
    String key() default "default";

    /** 锁过期时间（秒），防止死锁。按业务场景设置：支付 10s，下单 5s，秒杀 3s */
    long expire() default 5;

    /** 防重失败时返回的提示信息 */
    String message() default "请勿重复提交";
}
