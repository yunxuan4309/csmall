package com.cooxiao.mall.product;

import com.cooxiao.mall.common.config.MallCommonConfiguration;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

//@Import 将指定的配置类加载到容器当中
@SpringBootApplication
@Import(MallCommonConfiguration.class)
@EnableDubbo
public class MallProductWebApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallProductWebApiApplication.class, args);
    }

}
