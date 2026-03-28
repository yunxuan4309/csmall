package com.cooxiao.mall.sso.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j (OpenAPI 3) 配置
 */
@Configuration
public class Knife4jConfiguration {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("酷鲨商城SSO单点登录认证中心在线API")
                        .description("酷鲨商城SSO单点登录认证中心在线API")
                        .version("1.0"));
    }

}
