package com.cooxiao.mall.gateway.knife4j.handler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import org.springdoc.core.properties.SwaggerUiConfigProperties;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/swagger-resources")
public class Knife4jSwaggerHandler {

    @Autowired
    private SwaggerUiConfigProperties swaggerUiConfig;

    // 安全配置接口（无修改，原本就是正确的）
    @GetMapping("/configuration/security")
    public Mono<ResponseEntity<Map<String, Object>>> securityConfiguration() {
        return Mono.just(ResponseEntity.ok()
                .body(Collections.singletonMap("configUrl", swaggerUiConfig.getConfigUrl())));
    }

    // ====================== 修复点1：解决类型不兼容 ======================
    // 原错误：直接返回 swaggerUiConfig 对象，与声明的 Map<String,Object> 不匹配
    @GetMapping("/configuration/ui")
    public Mono<ResponseEntity<Map<String, Object>>> uiConfiguration() {
        // 手动封装为 Map，符合返回值类型要求
        Map<String, Object> map = new HashMap<>();
        map.put("configUrl", swaggerUiConfig.getConfigUrl());
        map.put("urls", swaggerUiConfig.getUrls());
        return Mono.just(ResponseEntity.ok(map));
    }

    // ====================== 修复点2：解决无参调用方法报错 ======================
    // 原错误：swaggerConfigResource.getSwaggerUiConfig() 无参调用，方法需要参数
    // Gateway 环境下直接返回空列表即可（路由聚合由 Gateway 配置管理）
    @GetMapping
    public Mono<ResponseEntity<List<Map<String, String>>>> swaggerResources() {
        return Mono.just(ResponseEntity.ok(Collections.emptyList()));
    }
}