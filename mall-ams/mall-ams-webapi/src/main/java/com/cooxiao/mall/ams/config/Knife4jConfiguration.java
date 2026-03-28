package com.cooxiao.mall.ams.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * Knife4j（Swagger2）的配置
 */
@Configuration
public class Knife4jConfiguration {
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("酷鲨商城SSO角色管理在线API")
                        .description("酷鲨商城SSO角色管理在线API")
                        .termsOfService("http://www.cooxiao.com")
                        .contact(new Contact()
                                .name("jsd@cooxiao.com")
                                .url("http://jsd.cooxiao.com")
                                .email("jsd@cooxiao.com"))
                        .version("1.0.0"));
    }
}
