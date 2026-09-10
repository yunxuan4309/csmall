package com.cooxiao.mall.ai.client;

import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.ai.config.AiTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证请求体构造规则（TODO #58 的核心行为，2026-09-10）。
 *
 * <p>依据官方文档 + 2026-09-10 实测（10 格实验）：
 * <ul>
 *   <li>思考模式 <b>只能</b>下发 {@code {"thinking":{"type":"enabled|disabled"}}} —— 取代"换个模型名 + 提示词求它别想"</li>
 *   <li>⚠️ 思考模式下 {@code temperature} 官方<b>不生效</b> → 刻意不下发，避免"调了温度"的假象</li>
 *   <li>只有 JSON 任务下发 {@code response_format}（提示词必须含 "json"）</li>
 *   <li>模型 id 必须来自档位表，<b>代码里不出现任何模型名</b></li>
 * </ul>
 */
class DeepSeekAiClientRequestBodyTest {

    private static final String TEST_MODEL_ID = "test-model-flash";

    private DeepSeekAiClient client;

    @BeforeEach
    void setUp() {
        AiProperties props = new AiProperties();
        props.getModels().put("flash", TEST_MODEL_ID);
        props.setDefaultTier("flash");
        props.setMaxTokens(3000);
        props.setTemperature(0.7);

        AiProperties.TaskOptions chat = new AiProperties.TaskOptions();
        chat.setTier("flash");
        chat.setThinking(true);
        chat.setMaxTokens(3000);

        AiProperties.TaskOptions json = new AiProperties.TaskOptions();
        json.setTier("flash");
        json.setThinking(false);
        json.setTemperature(0.3);

        AiProperties.TaskOptions expand = new AiProperties.TaskOptions();
        expand.setTier("flash");
        expand.setThinking(false);
        expand.setTemperature(0.3);

        props.getTasks().put(AiTask.CHAT.key(), chat);
        props.getTasks().put(AiTask.JSON.key(), json);
        props.getTasks().put(AiTask.EXPAND.key(), expand);

        client = new DeepSeekAiClient();
        ReflectionTestUtils.setField(client, "aiProperties", props);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bodyOf(AiTask task) {
        Map<String, Object> body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                client, "buildBody", task, List.<Map<String, Object>>of());
        assertThat(body).isNotNull();
        return body;
    }

    @Test
    void chatTask_enablesThinking_andOmitsTemperature() {
        Map<String, Object> body = bodyOf(AiTask.CHAT);

        assertThat(body.get("thinking")).isEqualTo(Map.of("type", "enabled"));
        // 思考模式下温度不生效 → 必须不下发
        assertThat(body).doesNotContainKey("temperature");
        assertThat(body).doesNotContainKey("response_format");
        assertThat(body.get("max_tokens")).isEqualTo(3000);
    }

    @Test
    void jsonTask_disablesThinking_andSendsResponseFormatAndTemperature() {
        Map<String, Object> body = bodyOf(AiTask.JSON);

        assertThat(body.get("thinking")).isEqualTo(Map.of("type", "disabled"));
        assertThat(body.get("response_format")).isEqualTo(Map.of("type", "json_object"));
        // 关掉思考后温度才真正生效 → 应当下发
        assertThat(body.get("temperature")).isEqualTo(0.3);
    }

    @Test
    void expandTask_disablesThinking_butNoResponseFormat() {
        Map<String, Object> body = bodyOf(AiTask.EXPAND);

        assertThat(body.get("thinking")).isEqualTo(Map.of("type", "disabled"));
        // 查询扩展输出纯文本；开启 response_format 会因提示词缺 "json" 被拒
        assertThat(body).doesNotContainKey("response_format");
        assertThat(body.get("temperature")).isEqualTo(0.3);
    }

    @Test
    void modelIdComesFromTierConfig() {
        for (AiTask task : AiTask.values()) {
            Map<String, Object> body = bodyOf(task);
            assertThat(body.get("model")).as("task=%s 的模型应来自档位表", task.key()).isEqualTo(TEST_MODEL_ID);
        }
    }
}
