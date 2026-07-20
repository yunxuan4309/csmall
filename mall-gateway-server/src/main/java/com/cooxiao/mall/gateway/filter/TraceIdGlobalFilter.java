package com.cooxiao.mall.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Gateway 全局过滤器 —— 为每个请求生成 traceId。
 * 注入请求头 X-Trace-Id，后续微服务从请求头读取并写入 MDC。
 * 最高优先级，确保 traceId 最先产生。
 */
@Component
@Slf4j
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        exchange = exchange.mutate()
                .request(r -> r.header("X-Trace-Id", traceId))
                .build();
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1000;
    }
}
