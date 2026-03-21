package com.cooxiao.mall.resource.config;

import com.cooxiao.mall.common.config.MallCommonConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({MallCommonConfiguration.class})
public class MallResourceConfiguration {
}
