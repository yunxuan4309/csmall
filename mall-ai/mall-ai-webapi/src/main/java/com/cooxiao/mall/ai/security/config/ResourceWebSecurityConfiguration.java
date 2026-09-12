package com.cooxiao.mall.ai.security.config;

import com.cooxiao.mall.ai.security.MyAccessDeniedHandler;
import com.cooxiao.mall.ai.security.MyAuthenticationEntryPoint;
import com.cooxiao.mall.ai.security.filter.SSOFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class ResourceWebSecurityConfiguration {

    static {
        // 异步线程继承 SecurityContext，SSE async dispatch 时不会丢失认证信息
        org.springframework.security.core.context.SecurityContextHolder
                .setStrategyName(org.springframework.security.core.context.SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    @Autowired
    private SSOFilter ssoFilter;
    @Autowired
    private MyAccessDeniedHandler myAccessDeniedHandler;
    @Autowired
    private MyAuthenticationEntryPoint myAuthenticationEntryPoint;

    /** config from yml: cooxiao.ai.sync-whitelisted */
    @Value("${cooxiao.ai.sync-whitelisted:false}")
    private boolean syncWhitelisted;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        // 仅允许前端开发服务器直连（绕过代理的 SSE 流式请求需要 CORS）
        http.cors(cors -> {
            CorsConfiguration config = new CorsConfiguration();
            config.addAllowedOrigin("http://localhost:5173");
            config.addAllowedOrigin("http://127.0.0.1:5173");
            config.addAllowedOrigin("http://8.156.77.197");
            config.addAllowedMethod("*");
            config.addAllowedHeader("*");
            config.setAllowCredentials(true);
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", config);
            cors.configurationSource(source);
        });
        // 🔴 2026-09-12 修复 TODO #62：ASYNC / ERROR 是**已鉴权请求的内部再派发**（外部客户端无法
        //    伪装成这两种 dispatcher type 进来），必须放行 —— 否则 `/ai/chat/stream`
        //    （返回 StreamingResponseBody）在异步线程写流完成后的 ASYNC 派发会被
        //    `anyRequest().authenticated()` 判为未认证 → 抛 AccessDeniedException。
        //    症状：串行时响应已提交（客户端仍看到 200，只在日志里留一条 ERROR），
        //          **并发时提交时序变化 → 客户端直接看到 HTTP 500**（2026-09-12 实测：20 并发 ~50%）。
        //    ⚠️ 首次 REQUEST 派发**仍然要求登录**，`/ai/**` 防匿名刷 Token 的收紧不受影响。
        //    ⚠️ 下方 static 块里的 MODE_INHERITABLETHREADLOCAL 对容器线程池的 ASYNC 派发**无效**
        //       —— 那不是"当前线程的子线程"，所以那条尝试解决不了本问题。
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                .requestMatchers(buildPermitAllMatchers()).permitAll()
                .anyRequest().authenticated()
        );
        http.exceptionHandling(exception -> exception
                .accessDeniedHandler(myAccessDeniedHandler)
                .authenticationEntryPoint(myAuthenticationEntryPoint)
        );
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.addFilterBefore(ssoFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private String[] buildPermitAllMatchers() {
        List<String> matchers = new ArrayList<>(List.of(
                "/",
                "/favicon.ico",
                "/error",
                "/doc.html",
                "/webjars/**",
                "/swagger-resources/**",
                "/v2/api-docs/**",
                "/v3/api-docs/**"));
        // 2026-08-14 安全收紧：/ai/** 全部要求登录（含 /ai/chat/stream、/ai/search），
        // 防止匿名用户无限消耗 DeepSeek API Token。stream 的 userId 在控制器同步阶段已捕获。
        // 🔴 2026-09-12 更正（#62）：原文续写"async 写流不依赖 SecurityContext，登录后流式功能不受影响"
        //    —— **这个假设是错的**：ASYNC 二次派发仍会走授权规则，而认证对象已不在容器线程上
        //    → AccessDeniedException（并发下表现为 500）。已在 securityFilterChain 里放行 ASYNC/ERROR 派发。
        if (syncWhitelisted) {
            matchers.add("/ai/sync");
            matchers.add("/ai/sync/**");
        }
        return matchers.toArray(new String[0]);
    }
}
