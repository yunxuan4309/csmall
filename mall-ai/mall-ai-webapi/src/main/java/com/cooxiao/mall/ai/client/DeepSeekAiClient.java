package com.cooxiao.mall.ai.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cooxiao.mall.ai.config.AiConcurrencyGuard;
import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.ai.service.TokenBudgetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek API 客户端实现
 * <p>
 * API 文档：https://platform.deepseek.com/api-docs
 * <p>
 * 并发控制（TODO #2+#34）：所有同步 LLM 调用在进入真实 HTTP 前经过 {@link AiConcurrencyGuard}
 * 并发闸门——闸门满抛 AiBusyException → 调用方走既有降级路径（纯 ES 结果），而非无限排队占线程。
 */
@Slf4j
@Component
public class DeepSeekAiClient implements AiClient {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private TokenBudgetService tokenBudgetService;

    @Autowired
    private AiConcurrencyGuard concurrencyGuard;

    // ========== Chat API ==========

    @Override
    public String chat(String systemPrompt, String userMessage) {
        HttpHeaders headers = buildHeaders();
        List<Map<String, String>> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        return doChat(headers, messages);
    }

    @Override
    public String chat(List<Map<String, String>> messages) {
        HttpHeaders headers = buildHeaders();
        return doChat(headers, messages);
    }

    @Override
    public String chatWithModel(String systemPrompt, String userMessage,
                                 String model, boolean jsonMode) {
        checkBudget();
        HttpHeaders headers = buildHeaders();
        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", aiProperties.getMaxTokens());
        if (jsonMode) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        log.debug("Calling DeepSeek Chat API, model={}, jsonMode={}", model, jsonMode);

        // 并发闸门：意图提取/重排等 JSON 结构化任务也是真实 LLM 调用，受同一闸门保护
        concurrencyGuard.acquire("chat:" + model);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiProperties.getBaseUrl() + "/v1/chat/completions",
                    request,
                    String.class);

            JSONObject json = JSON.parseObject(response.getBody());

            // 记录 token 费用
            JSONObject usage = json.getJSONObject("usage");
            if (usage != null) {
                int promptTokens = usage.getIntValue("prompt_tokens");
                int completionTokens = usage.getIntValue("completion_tokens");
                double inputCost = aiProperties.getChatInputPricePerMillion() * promptTokens / 1_000_000;
                double outputCost = aiProperties.getChatOutputPricePerMillion() * completionTokens / 1_000_000;
                tokenBudgetService.record(inputCost + outputCost);
            }

            String content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
            if (content == null || content.isBlank()) {
                // reasoning 模型思考过长可能把 max_tokens 吃满，content 为空 → 调用方解析 null
                log.warn("DeepSeek 响应 content 为空（model={}, jsonMode={}, 可能 reasoning 耗尽 max_tokens={}），usage={}",
                        model, jsonMode, aiProperties.getMaxTokens(), usage);
            }
            return content;
        } finally {
            concurrencyGuard.release();
        }
    }

    private String doChat(HttpHeaders headers, List<Map<String, String>> messages) {
        checkBudget();
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", aiProperties.getChatModel());
        requestBody.put("messages", messages);
        requestBody.put("temperature", aiProperties.getTemperature());
        requestBody.put("max_tokens", aiProperties.getMaxTokens());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        log.debug("Calling DeepSeek Chat API, model={}", aiProperties.getChatModel());

        // 并发闸门：多轮对话同步回复也是真实 LLM 调用
        concurrencyGuard.acquire("chat");
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiProperties.getBaseUrl() + "/v1/chat/completions",
                    request,
                    String.class);

            JSONObject json = JSON.parseObject(response.getBody());

            // 记录 token 费用
            JSONObject usage = json.getJSONObject("usage");
            if (usage != null) {
                int promptTokens = usage.getIntValue("prompt_tokens");
                int completionTokens = usage.getIntValue("completion_tokens");
                double promptCost = aiProperties.getChatInputPricePerMillion() * promptTokens / 1_000_000;
                double completionCost = aiProperties.getChatOutputPricePerMillion() * completionTokens / 1_000_000;
                tokenBudgetService.record(promptCost + completionCost);
            }

            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } finally {
            concurrencyGuard.release();
        }
    }

    /**
     * 预算强制检查：所有 DeepSeek API 调用发起前执行。
     * 任何遗漏入口检查的调用路径（如 /ai/search）也会在此被拦下。
     */
    private void checkBudget() {
        if (tokenBudgetService.isBudgetExceeded()) {
            log.warn("AI 日预算已超限，拒绝调用 DeepSeek API");
            throw new IllegalStateException("AI daily budget exceeded");
        }
    }

    // ========== Embedding API ==========

    @Override
    public float[] embed(String text) {
        JSONObject result = doEmbed(List.of(text));
        JSONArray embeddingArray = result.getJSONArray("data")
                .getJSONObject(0)
                .getJSONArray("embedding");
        return toFloatArray(embeddingArray);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        JSONObject result = doEmbed(texts);
        JSONArray dataArray = result.getJSONArray("data");
        List<float[]> embeddings = new ArrayList<>(dataArray.size());
        for (int i = 0; i < dataArray.size(); i++) {
            embeddings.add(toFloatArray(dataArray.getJSONObject(i).getJSONArray("embedding")));
        }
        return embeddings;
    }

    private JSONObject doEmbed(List<String> inputs) {
        checkBudget();
        HttpHeaders headers = buildHeaders();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", aiProperties.getEmbeddingModel());
        requestBody.put("input", inputs.size() == 1 ? inputs.get(0) : inputs);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        log.debug("Calling DeepSeek Embedding API, model={}, batchSize={}",
                aiProperties.getEmbeddingModel(), inputs.size());

        // 并发闸门：embedding 也是外部 API 调用，与 chat 共享闸门（启动全量向量化受并发 20 约束）
        concurrencyGuard.acquire("embed");
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    aiProperties.getBaseUrl() + "/v1/embeddings",
                    request,
                    String.class);

            JSONObject json = JSON.parseObject(response.getBody());

            // 记录 token 费用
            JSONObject usage = json.getJSONObject("usage");
            if (usage != null) {
                int promptTokens = usage.getIntValue("prompt_tokens");
                double cost = aiProperties.getEmbeddingPricePerMillion() * promptTokens / 1_000_000;
                tokenBudgetService.record(cost);
            }

            return json;
        } finally {
            concurrencyGuard.release();
        }
    }

    // ========== Common ==========

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiProperties.getApiKey());
        return headers;
    }

    private float[] toFloatArray(JSONArray array) {
        float[] result = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.getFloatValue(i);
        }
        return result;
    }
}
