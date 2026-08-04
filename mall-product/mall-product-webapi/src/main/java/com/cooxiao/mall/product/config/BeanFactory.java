package com.cooxiao.mall.product.config;

import org.springframework.context.annotation.Configuration;

/**
 * 产品模块的 Bean 工厂
 * 注意：不再定义 ObjectMapper Bean，由 Spring Boot 默认管理
 * （JacksonConfiguration 的 Long→String 序列化通过 Jackson2ObjectMapperBuilderCustomizer 生效）
 */
@Configuration
public class BeanFactory {

}
