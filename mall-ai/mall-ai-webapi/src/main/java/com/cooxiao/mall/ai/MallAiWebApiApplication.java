package com.cooxiao.mall.ai;

import com.cooxiao.mall.common.config.MallCommonConfiguration;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * mall-ai 启动类。
 *
 * <p>⚠️ <b>不要在这里再加 {@code @EnableConfigurationProperties(AiProperties.class)}</b>：
 * {@link com.cooxiao.mall.ai.config.AiProperties} 自身带 {@code @Component}，已由组件扫描注册
 * （bean 名 {@code aiProperties}）；再显式启用会注册<b>第二个</b>同类型 bean
 * （bean 名 {@code cooxiao.ai-com.cooxiao.mall.ai.config.AiProperties}），
 * 导致「类型注入歧义 + {@code @PostConstruct} 执行两遍」——
 * 当前能跑只是因为注入字段名恰好等于 bean 名（按名回退消歧），属隐患。
 * 2026-09-11 已移除该重复注册；与 {@code mall-order} 的 AlipayConfig 写法保持一致。
 */
@SpringBootApplication
@EnableDubbo
@Import(MallCommonConfiguration.class)
public class MallAiWebApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAiWebApiApplication.class, args);
    }
}
