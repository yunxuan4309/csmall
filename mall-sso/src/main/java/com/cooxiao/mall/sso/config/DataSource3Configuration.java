package com.cooxiao.mall.sso.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/** OMS 订单数据源，仪表盘统计专用 */
@Configuration
public class DataSource3Configuration {

    @Bean(name = "db3DataSource")
    @ConfigurationProperties(prefix = "spring.datasource.order")
    public DataSource orderDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "orderJdbcTemplate")
    public JdbcTemplate orderJdbcTemplate(@Qualifier("db3DataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
