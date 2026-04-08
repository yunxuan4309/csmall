package com.cooxiao.mall.sso.security.config;

import com.cooxiao.mall.sso.security.MyAccessDeniedHandler;
import com.cooxiao.mall.sso.security.MyAuthenticationEntryPoint;
import com.cooxiao.mall.sso.security.filter.SSOFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SSOWebSecurityConfig {

    @Autowired
    private SSOFilter ssoFilter;
    @Autowired
    private MyAccessDeniedHandler myAccessDeniedHandler;
    @Autowired
    private MyAuthenticationEntryPoint myAuthenticationEntryPoint;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] permitList = {
                // Swagger/Knife4j 文档
                "/swagger-resources/**",
                "/v2/api-docs/**",
                "/v3/api-docs/**",
                "/doc.html",
                "/favicon.ico",
                // 根路径和错误处理
                "/",
                "/error",
                // SSO 登录登出接口 - 使用精确路径
                "/admin/sso/login",
                "/admin/sso/logout",
                "/admin/sso/home",
                "/admin/sso/debug",
                "/admin/sso/hash",
                "/user/sso/login",
                "/user/sso/logout"
        };

        http.csrf(csrf -> csrf.disable());
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
        http.sessionManagement(session -> {
            session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);
            session.sessionFixation().none();
        });
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(permitList).permitAll()
                .anyRequest().authenticated()
        );
        http.exceptionHandling(exception -> exception
                .accessDeniedHandler(myAccessDeniedHandler)
                .authenticationEntryPoint(myAuthenticationEntryPoint)
        );
        http.addFilterBefore(ssoFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
