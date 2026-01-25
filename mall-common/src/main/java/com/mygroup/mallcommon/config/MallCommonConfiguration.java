package com.mygroup.mallcommon.config;


import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 当前模块（mall-common）的配置类，当其它模块依赖此模块时，应导入此配置类
 */
@Configuration
@ComponentScan({
        "com.cooxiao.mall.common.exception.handler",
        "com.cooxiao.mall.common.utils"})
public class MallCommonConfiguration {
}