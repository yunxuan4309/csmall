package com.cooxiao.mall.ai.client;

import com.alibaba.fastjson.JSON;
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

        AiProperties.TaskOptions agent = new AiProperties.TaskOptions();
        agent.setTier("flash");
        agent.setThinking(false);      // 工具轮必须关思考（开思考要回传 reasoning_content，否则 400）
        agent.setMaxTokens(1000);

        props.getTasks().put(AiTask.CHAT.key(), chat);
        props.getTasks().put(AiTask.JSON.key(), json);
        props.getTasks().put(AiTask.EXPAND.key(), expand);
        props.getTasks().put(AiTask.AGENT.key(), agent);

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

    // ================================================================
    // TODO #32 P0：Function Calling 请求体 / 响应解析
    // ================================================================

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolBodyOf(List<Map<String, Object>> tools) {
        Map<String, Object> body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                client, "buildToolBody", AiTask.AGENT, List.<Map<String, Object>>of(), tools);
        assertThat(body).isNotNull();
        return body;
    }

    @Test
    void agentToolBody_carriesToolsAndToolChoice_butNeverResponseFormat() {
        List<Map<String, Object>> tools = List.of(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "search_products",
                        "description", "检索商品",
                        "parameters", Map.of("type", "object"))));

        Map<String, Object> body = toolBodyOf(tools);

        assertThat(body.get("tools")).isEqualTo(tools);
        assertThat(body.get("tool_choice")).isEqualTo("auto");
        // 工具轮必须关思考：开思考时 reasoning_content 必须回传，否则接口 400（#32 校正 / #58 实验 B）
        assertThat(body.get("thinking")).isEqualTo(Map.of("type", "disabled"));
        // ⚠️ 核心回归点：tools 与 response_format 互斥 —— 同时下发时模型只输出 JSON、不再返回 tool_calls（#32 校正⑦ 实测）
        assertThat(body).doesNotContainKey("response_format");
        assertThat(body.get("max_tokens")).isEqualTo(1000);
    }

    @Test
    void toolBody_withNullTools_stillSendsEmptyArray() {
        // tools 传 null 时下发的必须是空数组而不是 null —— 否则请求体非法
        assertThat(toolBodyOf(null).get("tools")).isEqualTo(List.of());
    }

    @Test
    void parseToolRound_extractsCalls_intoReplayableAssistantMessage() {
        String raw = """
                {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                "tool_calls":[{"id":"call_1","type":"function","function":{"name":"search_products",
                "arguments":"{\\"keywords\\":\\"手机\\",\\"budgetMax\\":5000}"}}]}}]}
                """;

        AiToolRound round = ReflectionTestUtils.invokeMethod(client, "parseToolRound", JSON.parseObject(raw));

        assertThat(round).isNotNull();
        assertThat(round.hasToolCalls()).isTrue();
        assertThat(round.content()).isNull();          // 只调工具时不带正文

        AiToolCall call = round.toolCalls().get(0);
        assertThat(call.id()).isEqualTo("call_1");
        assertThat(call.name()).isEqualTo("search_products");
        // arguments 是"字符串形式的 JSON"，必须二次解析（Function Calling 最容易写错的地方）
        assertThat(call.args()).containsEntry("keywords", "手机").containsEntry("budgetMax", 5000);

        // 回灌用消息：id/type/function 必须齐全，且刻意不带 reasoning_content
        Map<String, Object> assistant = round.assistantMessage();
        assertThat(assistant).containsEntry("role", "assistant");
        assertThat(assistant).doesNotContainKey("reasoning_content");
        assertThat((List<?>) assistant.get("tool_calls")).hasSize(1);
    }

    @Test
    void parseToolRound_withoutToolCalls_meansConverged() {
        String raw = """
                {"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"推荐这两款"}}]}
                """;

        AiToolRound round = ReflectionTestUtils.invokeMethod(client, "parseToolRound", JSON.parseObject(raw));

        assertThat(round.hasToolCalls()).isFalse();     // 收敛信号
        assertThat(round.content()).isEqualTo("推荐这两款");
        assertThat(round.assistantMessage()).doesNotContainKey("tool_calls");
    }
}
