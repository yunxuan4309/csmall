package com.cooxiao.mall;

import com.cooxiao.mall.common.config.MallCommonConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jsonb.JsonbAutoConfiguration;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    JsonbAutoConfiguration.class,
    GsonAutoConfiguration.class
})
@Import({MallCommonConfiguration.class})
public class MallPassportWebApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MallPassportWebApiApplication.class,args);
    }
}
