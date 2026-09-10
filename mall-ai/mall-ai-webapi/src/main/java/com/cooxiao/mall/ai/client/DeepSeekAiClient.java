package com.cooxiao.mall.ai.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
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
 * <h3>本次改造要点（TODO #58，2026-09-10）</h3>
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
    // Function Calling（TODO #32 P0）
    // ================================================================

    @Override
    public AiToolRound chatWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        return chatWithTools(messages, tools, null);
    }

    @Override
    public AiToolRound chatWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                     String toolChoice) {
        checkBudget();
        AiTask task = AiTask.AGENT;
        Map<String, Object> body = buildToolBody(task, messages, tools, toolChoice);
        log.debug("调用 AI（带工具）：task={}, model={}, tools={}, toolChoice={}",
                task.key(), body.get("model"), tools == null ? 0 : tools.size(), body.get("tool_choice"));

        concurrencyGuard.acquire("chat:" + task.key());
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiProperties.getBaseUrl() + CHAT_COMPLETIONS_PATH,
                    new HttpEntity<>(body, buildHeaders()),
                    String.class);

            JSONObject json = JSON.parseObject(response.getBody());
            recordUsage(json);
            return parseToolRound(json);
        } finally {
            concurrencyGuard.release();
        }
    }

    /**
     * 工具轮请求体 = {@link #buildBody} 的结果 + {@code tools} + {@code tool_choice=auto}。
     * <p>复用 {@code buildBody} 保证"模型/思考/温度/max_tokens 只有一处定义"；
     * 又因为 {@link AiTask#AGENT} 不是 JSON 任务，{@code buildBody} <b>天然不会加 response_format</b> ——
     * 这正是必需的行为（实测二者共存时模型不再返回 tool_calls，见 TODO #32 校正⑦）。
     */
    private Map<String, Object> buildToolBody(AiTask task, List<Map<String, Object>> messages,
                                             List<Map<String, Object>> tools, String toolChoice) {
        Map<String, Object> body = buildBody(task, messages);
        body.put("tools", tools == null ? List.of() : tools);
        // auto = 模型自己决定；required = 强制必须调工具（首轮商品类问题用，避免凭历史作答）
        body.put("tool_choice", hasText(toolChoice) ? toolChoice : "auto");
        return body;
    }

    /** 解析 {@code choices[0].message} → {@link AiToolRound}（含可回灌的 assistant 消息） */
    private AiToolRound parseToolRound(JSONObject json) {
        JSONObject message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
        String content = message.getString("content");

        List<AiToolCall> calls = new ArrayList<>();
        JSONArray rawToolCalls = message.getJSONArray("tool_calls");
        if (rawToolCalls != null) {
            for (int i = 0; i < rawToolCalls.size(); i++) {
                JSONObject call = rawToolCalls.getJSONObject(i);
                JSONObject function = call.getJSONObject("function");
                calls.add(new AiToolCall(
                        call.getString("id"),
                        function == null ? null : function.getString("name"),
                        function == null ? null : function.getString("arguments")));
            }
        }

        return new AiToolRound(content, calls, assistantMessage(content, calls));
    }

    /**
     * 组装"可直接回灌"的 assistant 消息 —— 同步与流式两条工具轮路径共用（结构错一处就是 400）。
     * <p>{@code tool_calls} 必须原样带回（OpenAI 协议要求 {@code id/type/function} 齐全）；
     * ⚠️ <b>刻意不带 {@code reasoning_content}</b> —— 工具轮 {@code thinking=disabled} 本就没有，
     * 若哪天改成开思考则必须回传，否则接口 400。
     */
    private Map<String, Object> assistantMessage(String content, List<AiToolCall> calls) {
        Map<String, Object> assistant = new HashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", content == null ? "" : content);
        if (calls != null && !calls.isEmpty()) {
            List<Map<String, Object>> rawCalls = new ArrayList<>();
            for (AiToolCall call : calls) {
                rawCalls.add(Map.of(
                        "id", call.id() == null ? "" : call.id(),
                        "type", "function",
                        "function", Map.of(
                                "name", call.name() == null ? "" : call.name(),
                                "arguments", call.argumentsJson() == null ? "{}" : call.argumentsJson())));
            }
            assistant.put("tool_calls", rawCalls);
        }
        return assistant;
    }

    @Override
    public AiToolRound streamChatWithTools(List<Map<String, Object>> messages,
                                           List<Map<String, Object>> tools,
                                           Consumer<String> onContentChunk) throws Exception {
        return streamChatWithTools(messages, tools, null, onContentChunk);
    }

    @Override
    public AiToolRound streamChatWithTools(List<Map<String, Object>> messages,
                                           List<Map<String, Object>> tools,
                                           String toolChoice,
                                           Consumer<String> onContentChunk) throws Exception {
        checkBudget();
        AiTask task = AiTask.AGENT;
        Map<String, Object> body = buildToolBody(task, messages, tools, toolChoice);

        // 正文实时回吐给前端；tool_calls 按 index 累积（⚠️ arguments 是**分片到达**的字符串，必须拼接）
        StringBuilder content = new StringBuilder();
        Map<Integer, String> ids = new HashMap<>();
        Map<Integer, String> names = new HashMap<>();
        Map<Integer, StringBuilder> args = new HashMap<>();

        concurrencyGuard.acquire("chat:" + task.key());
        try {
            openSseStream(body, data -> {
                JSONObject delta = deltaOf(data);
                if (delta == null) {
                    return;
                }
                String chunk = delta.getString("content");
                if (chunk != null && !chunk.isEmpty()) {
                    content.append(chunk);
                    onContentChunk.accept(chunk);
                }
                JSONArray toolCalls = delta.getJSONArray("tool_calls");
                if (toolCalls == null) {
                    return;
                }
                for (int i = 0; i < toolCalls.size(); i++) {
                    JSONObject tc = toolCalls.getJSONObject(i);
                    int index = tc.getIntValue("index");
                    if (tc.getString("id") != null) {
                        ids.put(index, tc.getString("id"));       // id/name 只在首个分片出现
                    }
                    JSONObject function = tc.getJSONObject("function");
                    if (function == null) {
                        continue;
                    }
                    if (function.getString("name") != null) {
                        names.put(index, function.getString("name"));
                    }
                    String fragment = function.getString("arguments");
                    if (fragment != null) {
                        args.computeIfAbsent(index, k -> new StringBuilder()).append(fragment);
                    }
                }
            });
        } finally {
            concurrencyGuard.release();
        }

        List<AiToolCall> calls = new ArrayList<>();
        java.util.TreeSet<Integer> indexes = new java.util.TreeSet<>();
        indexes.addAll(ids.keySet());
        indexes.addAll(names.keySet());
        indexes.addAll(args.keySet());
        for (Integer index : indexes) {
            StringBuilder buf = args.get(index);
            calls.add(new AiToolCall(
                    ids.getOrDefault(index, "call_" + index),
                    names.get(index),
                    buf == null ? null : buf.toString()));
        }

        log.debug("流式工具轮结束：正文 {} 字，tool_calls {} 个", content.length(), calls.size());
        return new AiToolRound(content.length() == 0 ? null : content.toString(),
                calls, assistantMessage(content.toString(), calls));
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

    /** 纯流式对话：只关心 {@code delta.content}（旧流水线的最终作答轮用它） */
    private void doStream(Map<String, Object> body, Consumer<String> onChunk) throws Exception {
        openSseStream(body, data -> {
            String content = contentOf(data);
            if (content != null && !content.isEmpty()) {
                onChunk.accept(content);
            }
            // 说明：思考模式下 delta 里还有 reasoning_content（实测分片数约为 content 的 2.3 倍）。
            // 前端不展示思考过程 → 此处天然忽略，不影响 SSE 解析（2026-09-10 实测验证）。
        });
    }

    /**
     * SSE 公共管道：把每个 {@code data:} 分片解析成 JSON，usage 分片就地记账，其余交给回调。
     * <p>{@link #doStream} 与 {@link #streamChatWithTools} 共用 —— 两条流式路径只有"怎么解读分片"不同，
     * 取流/断连/记账/容错这些易错细节只写一次。
     */
    private void openSseStream(Map<String, Object> body, Consumer<JSONObject> onData) throws Exception {
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
                        onData.accept(data);
                    } catch (Exception ignored) {
                        // 单个 chunk 解析失败不中断整条流
                    }
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    /** 取 {@code choices[0].delta}（流式分片） */
    private JSONObject deltaOf(JSONObject data) {
        JSONArray choices = data.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.getJSONObject(0).getJSONObject("delta");
    }

    /** 取流式分片里的正文 */
    private String contentOf(JSONObject data) {
        JSONObject delta = deltaOf(data);
        return delta == null ? null : delta.getString("content");
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
