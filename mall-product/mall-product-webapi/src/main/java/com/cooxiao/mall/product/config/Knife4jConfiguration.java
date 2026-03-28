package com.cooxiao.mall.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
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
                        .title("酷鲨商城在线API文档--商品管理")
                        .description("酷鲨商城在线API文档--商品管理")
                        .termsOfService("http://www.apache.org/licenses/LICENSE-2.0")
                        .contact(new Contact()
                                .name("Java教学研发部")
                                .url("http://java.cooxiao.com")
                                .email("java@cooxiao.com"))
                        .version("1.0.0"));
    }

}
