package com.cooxiao.mall.ai.security.filter;

import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.utils.JwtTokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class SSOFilter extends OncePerRequestFilter {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JwtTokenUtils jwtTokenUtils;
    @Value("${jwt.tokenHead}")
    private String jwtTokenHead;

    private static final String REQUEST_HEADER_AUTHORIZATION = "Authorization";

    @Override
    protected void doFilterInternal(
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = httpServletRequest.getHeader(REQUEST_HEADER_AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(jwtTokenHead)) {
            String authToken = authHeader.substring(jwtTokenHead.length()).trim();
            String lockedTokenList = "token_list_.lock";
            Boolean member = stringRedisTemplate.boundSetOps(lockedTokenList).isMember(authToken);
            if (member) {
                log.info("token已登出,视为无效:" + authToken);
                SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
                filterChain.doFilter(httpServletRequest, httpServletResponse);
                return;
            }
            CsmallAuthenticationInfo userInfo = jwtTokenUtils.getUserInfo(authToken);
            UsernamePasswordAuthenticationToken authentication = null;
            if (userInfo != null) {
                List<String> authoritiesString = userInfo.getAuthorities();
                List<GrantedAuthority> authorities = new ArrayList<>();
                for (String authorityValue : authoritiesString) {
                    authorities.add(new SimpleGrantedAuthority(authorityValue));
                }
                authentication = new UsernamePasswordAuthenticationToken(
                        userInfo.getUsername(), userInfo, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpServletRequest));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
            }
        }
        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }
}
