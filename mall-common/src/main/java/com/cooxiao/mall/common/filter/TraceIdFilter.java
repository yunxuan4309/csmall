package com.cooxiao.mall.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 从请求头 X-Trace-Id 中读取 Gateway 生成的 traceId，写入 MDC。
 * 之后的日志自动带上 [traceId]，贯穿整个请求链路。
 * 各模块通过 FilterRegistrationBean 注册即可。
 */
@Slf4j
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String traceId = httpRequest.getHeader("X-Trace-Id");
        if (!StringUtils.hasText(traceId)) {
            traceId = "N/A";
        }
        try {
            MDC.put("traceId", traceId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
