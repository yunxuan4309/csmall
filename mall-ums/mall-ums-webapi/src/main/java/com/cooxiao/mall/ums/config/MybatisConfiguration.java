package com.cooxiao.mall.ums.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * <p>Mybatis配置类</p>
 */
@Configuration
@MapperScan(basePackages = {"com.cooxiao.mall.ums.mapper"})
public class MybatisConfiguration {

}
