package com.cooxiao.mall.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证「模型配置可配化」的 <b>yml → Java 绑定</b>（TODO #58，2026-09-10）。
 *
 * <p>这是本次改造最易出错的部分：嵌套 {@code Map<String, TaskOptions>} + kebab-case 键
 * （{@code max-tokens} / {@code default-tier} / {@code reasoning-effort}）。绑定错了只会在运行期暴露，
 * 所以用 {@link ConfigDataApplicationContextInitializer} 真加载 application.yml 来断言。
 *
 * <p>不启动完整 Spring 上下文（不连 Nacos/ES/Redis），毫秒级完成。
 */
class AiPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(AiProperties.class)
    static class TestConfig {
    }

    @Test
    void bindsModelTiersAndTasks() {
        runner.run(ctx -> {
            AiProperties props = ctx.getBean(AiProperties.class);

            // ===== 档位层：逻辑档位 → 实际 model id =====
            assertThat(props.getModels()).containsKeys("flash", "pro");
            assertThat(props.getDefaultTier()).isEqualTo("flash");

            // 任务解析出的 model id 必须来自档位表（而非代码里的硬编码）
            String chatModel = props.modelFor(AiTask.CHAT);
            assertThat(chatModel).isNotBlank();
            assertThat(chatModel).isEqualTo(props.getModels().get("flash"));

            // ===== 任务层：思考模式 =====
            // JSON 结构化任务必须关思考（这是 #58 的核心，靠它根治 reasoning 挤空 content）
            assertThat(props.taskOptions(AiTask.JSON).isThinking()).isFalse();
            // 查询扩展：关思考但走纯文本（不带 response_format）
            assertThat(props.taskOptions(AiTask.EXPAND).isThinking()).isFalse();
            // 对话 / 对比：思考开
            assertThat(props.taskOptions(AiTask.CHAT).isThinking()).isTrue();
            assertThat(props.taskOptions(AiTask.COMPARE).isThinking()).isTrue();

            // ===== 任务级覆盖与全局回退 =====
            // json 明确配了 temperature: 0.3（只有非思考任务温度才生效）
            assertThat(props.taskOptions(AiTask.JSON).getTemperature()).isEqualTo(0.3);
            // expand 未配 max-tokens → 回退全局默认
            assertThat(props.taskOptions(AiTask.EXPAND).getMaxTokens()).isEqualTo(props.getMaxTokens());
            // chat 明确配了 max-tokens: 3000
            assertThat(props.taskOptions(AiTask.CHAT).getMaxTokens()).isEqualTo(3000);
        });
    }

    @Test
    void failsFastWhenTierMissing() {
        // 档位表空 / 档位名不存在时，必须给出可操作的报错，而不是静默用一个错误的模型名
        AiProperties props = new AiProperties();
        props.setDefaultTier("not-exist");
        assertThatThrownBy(() -> props.modelFor(AiTask.CHAT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模型档位未配置");
    }

    @Test
    void fallsBackToGlobalDefaultsWhenTaskNotConfigured() {
        // 未配置的任务：用 default-tier + 全局 temperature/max-tokens，不能 NPE
        AiProperties props = new AiProperties();
        props.getModels().put("flash", "m-flash");
        props.setDefaultTier("flash");
        props.setTemperature(0.7);
        props.setMaxTokens(1234);

        AiProperties.TaskOptions opt = props.taskOptions(AiTask.EXPAND);
        assertThat(opt.getTier()).isEqualTo("flash");
        assertThat(opt.getTemperature()).isEqualTo(0.7);
        assertThat(opt.getMaxTokens()).isEqualTo(1234);
        // 默认思考为开（与官方默认一致）
        assertThat(opt.isThinking()).isTrue();
        assertThat(props.modelFor(AiTask.EXPAND)).isEqualTo("m-flash");
    }
}
