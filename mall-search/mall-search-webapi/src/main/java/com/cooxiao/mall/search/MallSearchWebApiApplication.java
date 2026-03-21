package com.cooxiao.mall.search;

import com.cooxiao.mall.common.config.MallCommonConfiguration;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@Import({MallCommonConfiguration.class})
@EnableDubbo
public class MallSearchWebApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallSearchWebApiApplication.class,args);
    }
}
