package com.cooxiao.mall.ams.security;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SpringBootTest
public class SecurityTests {

    @Test
    void testBcrypt() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        // 测试用户密码 123456
        String userHash = "$2a$10$LKBk.ZoWkmKyyExV39Yz7.EGAzMdX/aXbA0lvPpIAHgx9RsW3xZOm";
        boolean userMatch = passwordEncoder.matches("123456", userHash);
        log.info("用户密码测试 (123456): {}", userMatch);
        assertTrue(userMatch);

        // 测试管理员密码
        String adminHash = "$2a$10$ec5yFOLAmmIn7oxViycEw.36u3wBCSnuhjexFAP7wj1yvQLCIw7sK";
        boolean adminMatch = passwordEncoder.matches("123456", adminHash);
        log.info("管理员密码测试 (123456): {}", adminMatch);
        assertTrue(adminMatch);
    }

}
