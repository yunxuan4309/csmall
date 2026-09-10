package com.cooxiao.mall.ai.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cooxiao.mall.ai.config.AiConcurrencyGuard;
import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.ai.config.AiTask;
import com.cooxiao.mall.ai.service.TokenBudgetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DeepSeek API 客户端实现（OpenAI 兼容协议）。
 *
 * <h3>本次改造要点（TODO #58，2026-09-11）</h3>
 * <ol>
 *   <li><b>模型名全部外置</b>：由 {@code cooxiao.ai.models} 的档位解析，Java 里不出现模型 id</li>
 *   <li><b>思考模式显式化</b>：{@code thinking: enabled/disabled} —— 取代"换个模型名"和"提示词求它别想"</li>
 *   <li><b>请求体只在一处构造</b>：{@link #buildBody} —— 历史上 SSE 分支自建过一份重复请求体（模型硬编码），
 *       是"改一处漏一处"的根源（TODO #58 §2.4 第 6 项）</li>
 *   <li><b>SSE 收敛进客户端</b>：{@link #streamChat} 复用同一套档位/思考/预算/闸门逻辑</li>
 * </ol>
 *
 * <p>并发控制：所有真实 HTTP 调用前经 {@link AiConcurrencyGuard} 闸门，闸门满抛 AiBusyException
 * → 调用方走既有降级路径，而非无限排队占线程。
 */
@Slf4j
@Component
public class DeepSeekAiClient implements AiClient {

    private static final String CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private TokenBudgetService tokenBudgetService;

    @Autowired
    private AiConcurrencyGuard concurrencyGuard;

    // ================================================================
    // 同步调用
    // ================================================================

    @Override
    public String chat(String systemPrompt, String userMessage) {
        return chat(AiTask.CHAT, simpleMessages(systemPrompt, userMessage));
    }

    @Override
    public String chat(List<Map<String, Object>> messages) {
        return chat(AiTask.CHAT, messages);
    }

    @Override
    public String chatJson(String systemPrompt, String userMessage) {
        return chat(AiTask.JSON, simpleMessages(systemPrompt, userMessage));
    }

    @Override
    public String chat(AiTask task, String systemPrompt, String userMessage) {
        return chat(task, simpleMessages(systemPrompt, userMessage));
    }

    @Override
    public String chat(AiTask task, List<Map<String, Object>> messages) {
        checkBudget();
        Map<String, Object> body = buildBody(task, messages);
        log.debug("调用 AI：task={}, model={}, thinking={}, maxTokens={}",
                task.key(), body.get("model"),
                aiProperties.taskOptions(task).isThinking() ? "on" : "off", body.get("max_tokens"));

        concurrencyGuard.acquire("chat:" + task.key());
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiProperties.getBaseUrl() + CHAT_COMPLETIONS_PATH,
                    new HttpEntity<>(body, buildHeaders()),
                    String.class);

            JSONObject json = JSON.parseObject(response.getBody());
            recordUsage(json);

            String content = extractContent(json);
            if (content == null || content.isBlank()) {
                // 思考模式下 reasoning 可能吃满 max_tokens → content 为空（实测：max_tokens=120 时必现）
                log.warn("AI 响应 content 为空（task={}, model={}, max_tokens={}, usage={}）",
                        task.key(), body.get("model"), body.get("max_tokens"), json.getJSONObject("usage"));
            }
            return content;
        } finally {
            concurrencyGuard.release();
        }
    }

    // ================================================================
    // 流式调用（SSE）
    // ================================================================

    @Override
    public void streamChat(List<Map<String, Object>> messages, Consumer<String> onChunk) throws Exception {
        checkBudget();
        AiTask task = AiTask.CHAT;
        // 并发闸门：SSE 是"最长寿"的 LLM 调用（秒级~十几秒），不设闸门时少量并发即可占满线程/打满外部配额
        concurrencyGuard.acquire("stream");
        try {
            doStream(buildBody(task, messages), onChunk);
        } finally {
            concurrencyGuard.release();
        }
    }

    private void doStream(Map<String, Object> body, Consumer<String> onChunk) throws Exception {
        body.put("stream", true);
        // 预算修复：请求返回 usage，否则流式调用无法记账（2 元/日预算形同虚设）
        body.put("stream_options", Map.of("include_usage", true));

        HttpURLConnection conn = (HttpURLConnection) URI
                .create(aiProperties.getBaseUrl() + CHAT_COMPLETIONS_PATH)
                .toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + aiProperties.getApiKey());
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(aiProperties.getTimeout());

        try {
            try (OutputStream os = conn.getOutputStream()) {
                os.write(JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8));
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data: ") || "data: [DONE]".equals(line)) {
                        continue;
                    }
                    try {
                        JSONObject data = JSON.parseObject(line.substring(6));

                        // 末尾 chunk 携带 usage（stream_options.include_usage=true 时返回）
                        if (data.getJSONObject("usage") != null) {
                            recordUsage(data);
                            continue;
                        }

                        JSONObject delta = data.getJSONArray("choices")
                                .getJSONObject(0).getJSONObject("delta");
                        String content = delta == null ? null : delta.getString("content");
                        if (content != null && !content.isEmpty()) {
                            onChunk.accept(content);
                        }
                        // 说明：思考模式下 delta 里还有 reasoning_content（实测分片数约为 content 的 2.3 倍）。
                        // 前端不展示思考过程 → 此处天然忽略，不影响 SSE 解析（2026-09-11 实测验证）。
                    } catch (Exception ignored) {
                        // 单个 chunk 解析失败不中断整条流
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    // ================================================================
    // ★ 唯一构造请求体的地方
    // ================================================================

    /**
     * 按任务类型组装请求体 —— <b>模型 / 思考模式 / 温度 / max_tokens 只在这里出现一次</b>。
     * <p>SSE 与同步共用此方法，避免"改一处漏一处"。
     */
    private Map<String, Object> buildBody(AiTask task, List<Map<String, Object>> messages) {
        AiProperties.TaskOptions opt = aiProperties.taskOptions(task);

        Map<String, Object> body = new HashMap<>();
        body.put("model", aiProperties.modelFor(task));   // 档位解析；Java 代码不出现模型名
        body.put("messages", messages);
        body.put("max_tokens", opt.getMaxTokens());

        if (opt.isThinking()) {
            body.put("thinking", Map.of("type", "enabled"));
            if (hasText(opt.getReasoningEffort())) {
                body.put("reasoning_effort", opt.getReasoningEffort());
            }
            // ⚠️ 思考模式下 temperature 官方不生效（设了不报错）→ 刻意不下发，避免"调了温度"的假象
        } else {
            body.put("thinking", Map.of("type", "disabled"));
            if (opt.getTemperature() != null) {
                body.put("temperature", opt.getTemperature());   // 只有非思考模式温度才真正生效
            }
        }

        // 只有 JSON 任务下发 response_format（提示词必须含 "json"）
        // ⚠️ 它不能与 tools 同时使用 —— 同时给出时模型会直接输出 JSON、不再触发 tool_calls（TODO #32 校正⑦实测）
        if (task == AiTask.JSON) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    // ================================================================
    // 公共辅助
    // ================================================================

    private List<Map<String, Object>> simpleMessages(String systemPrompt, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (hasText(systemPrompt)) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage == null ? "" : userMessage));
        return messages;
    }

    /** 记录 token 费用（输入 + 输出） */
    private void recordUsage(JSONObject json) {
        JSONObject usage = json.getJSONObject("usage");
        if (usage == null) {
            return;
        }
        int promptTokens = usage.getIntValue("prompt_tokens");
        int completionTokens = usage.getIntValue("completion_tokens");
        double cost = aiProperties.getChatInputPricePerMillion() * promptTokens / 1_000_000.0
                + aiProperties.getChatOutputPricePerMillion() * completionTokens / 1_000_000.0;
        tokenBudgetService.record(cost);
    }

    private String extractContent(JSONObject json) {
        return json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
    }

    /**
     * 预算强制检查：所有 DeepSeek API 调用发起前执行。
     * 任何遗漏入口检查的调用路径也会在此被拦下。
     */
    private void checkBudget() {
        if (tokenBudgetService.isBudgetExceeded()) {
            log.warn("AI 日预算已超限，拒绝调用 DeepSeek API");
            throw new IllegalStateException("AI daily budget exceeded");
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiProperties.getApiKey());
        return headers;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
