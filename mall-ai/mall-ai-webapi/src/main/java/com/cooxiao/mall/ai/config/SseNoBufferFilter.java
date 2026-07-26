package com.cooxiao.mall.ai.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * SSE 流式输出专用过滤器：关闭 Tomcat 响应缓冲。
 * 不加此过滤器，Tomcat 默认 8KB 缓冲区会把 SSE 事件攒一起发送，
 * 导致前端看到"一次性全部输出"而非逐字输出。
 */
@Component
public class SseNoBufferFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        var httpReq = (jakarta.servlet.http.HttpServletRequest) request;
        // 对 SSE 流式接口关闭响应缓冲，使每个 chunk 立即发送
        if (httpReq.getRequestURI().contains("/chat/stream")) {
            ((HttpServletResponse) response).setBufferSize(0);
        }
        chain.doFilter(request, response);
    }
}
