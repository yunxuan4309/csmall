package com.cooxiao.mall.ams.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {"com.cooxiao.mall.ams.mapper"})
public class MybatisConfig {
}
