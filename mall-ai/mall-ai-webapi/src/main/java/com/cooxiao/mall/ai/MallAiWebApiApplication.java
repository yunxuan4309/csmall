package com.cooxiao.mall.ai;

import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.common.config.MallCommonConfiguration;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
@EnableDubbo
@Import(MallCommonConfiguration.class)
public class MallAiWebApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAiWebApiApplication.class, args);
    }
}
