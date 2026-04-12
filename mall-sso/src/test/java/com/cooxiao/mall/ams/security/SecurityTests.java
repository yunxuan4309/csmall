package com.cooxiao.mall.ams.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootTest
public class SecurityTests {

    @Test
    void testBcrypt() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        
        // 测试用户密码 123456
        String userHash = "$2a$10$LKBk.ZoWkmKyyExV39Yz7.EGAzMdX/aXbA0lvPpIAHgx9RsW3xZOm";
        System.out.println("用户密码测试 (123456): " + passwordEncoder.matches("123456", userHash));
        
        // 测试管理员密码
        String adminHash = "$2a$10$ec5yFOLAmmIn7oxViycEw.36u3wBCSnuhjexFAP7wj1yvQLCIw7sK";
        System.out.println("管理员密码测试 (123456): " + passwordEncoder.matches("123456", adminHash));
    }

}
