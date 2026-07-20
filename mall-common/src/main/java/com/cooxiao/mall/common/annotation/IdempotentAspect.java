package com.cooxiao.mall.common.annotation;

import com.alibaba.fastjson.JSON;
import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.exception.CoolSharkServiceException;
import com.cooxiao.mall.common.restful.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/**
 * 幂等性切面 — 拦截 @Idempotent 注解的方法。
 * 仅在同时满足以下条件时加载：
 *   a) Redis 可用（StringRedisTemplate 在 classpath）
 *   b) AspectJ 可用（ProceedingJoinPoint 在 classpath）
 * 缺任一条件则整个切面类不会被 Spring 加载，也不会有 Dubbo/Spring 去反射它。
 *
 * 关键：@ConditionalOnClass 用字符串 name 而非 class 字面量。
 * 字符串不会被 JVM 在解析注解时强制执行类加载，Spring Boot
 * 底层用 ClassUtils.forName 检查，不存在就静默跳过整个切面类。
 */
@Aspect
@Component
@ConditionalOnClass(name = {
    "org.springframework.data.redis.core.StringRedisTemplate",
    "org.aspectj.lang.ProceedingJoinPoint"
})
@Slf4j
public class IdempotentAspect {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint point, Idempotent idempotent) throws Throwable {
        String lockKey = buildKey(idempotent, point);
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", idempotent.expire(), TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("幂等拦截：重复提交, key={}", lockKey);
            throw new CoolSharkServiceException(ResponseCode.CONFLICT, idempotent.message());
        }

        log.debug("幂等锁获取成功: {}", lockKey);
        try {
            return point.proceed();
        } catch (Throwable e) {
            // 业务异常时释放锁，允许用户重试
            stringRedisTemplate.delete(lockKey);
            throw e;
        }
    }

    private String buildKey(Idempotent idempotent, ProceedingJoinPoint point) {
        String userId = resolveUserId();
        String argsDigest = md5Args(point.getArgs());
        return String.format("idempotent:%s:%s:%s", idempotent.key(), userId, argsDigest);
    }

    private String resolveUserId() {
        try {
            UsernamePasswordAuthenticationToken token =
                    (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
            if (token != null && token.getCredentials() instanceof CsmallAuthenticationInfo info) {
                return String.valueOf(info.getId());
            }
        } catch (Exception e) {
            log.debug("无法从 SecurityContext 获取 userId", e);
        }
        return "anonymous";
    }

    /** 对方法参数做 MD5，保证不同参数的请求不会误判为重复 */
    private String md5Args(Object[] args) {
        try {
            String json = JSON.toJSONString(args);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }
}
