package com.cooxiao.mall.ams.jwt;


import com.cooxiao.mall.common.utils.JwtTokenUtils;
import com.cooxiao.mall.sso.security.service.admin.AdminSSOUserDetailsService;
import com.cooxiao.mall.sso.security.service.user.UserSSOUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class JWTTest {

    @Autowired
    private JwtTokenUtils jwtTokenUtils;

    @Autowired
    private AdminSSOUserDetailsService adminSSOService;
    @Autowired
    private UserSSOUserDetailsService userSSOservice;

    @Test
    public void test01() {

    }
}
