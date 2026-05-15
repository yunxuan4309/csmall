package com.cooxiao.mall.ai.security.config;

import com.cooxiao.mall.ai.security.MyAccessDeniedHandler;
import com.cooxiao.mall.ai.security.MyAuthenticationEntryPoint;
import com.cooxiao.mall.ai.security.filter.SSOFilter;
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

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class ResourceWebSecurityConfiguration {

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
        http.cors(cors -> cors.disable());
        http.authorizeHttpRequests(auth -> auth
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
        if (syncWhitelisted) {
            matchers.add("/ai/sync");
            matchers.add("/ai/sync/**");
        }
        return matchers.toArray(new String[0]);
    }
}
