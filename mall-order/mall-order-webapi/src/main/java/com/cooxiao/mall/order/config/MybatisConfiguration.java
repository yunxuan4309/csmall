package com.cooxiao.mall.order.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * <p>Mybatis配置类</p>
 */
@Configuration
@MapperScan(basePackages = {"com.cooxiao.mall.order.mapper"})
public class MybatisConfiguration {

}
