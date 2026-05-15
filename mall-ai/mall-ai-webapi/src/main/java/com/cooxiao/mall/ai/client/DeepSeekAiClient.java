package com.cooxiao.mall.ai.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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

        return json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

    private String doChat(HttpHeaders headers, List<Map<String, String>> messages) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", aiProperties.getChatModel());
        requestBody.put("messages", messages);
        requestBody.put("temperature", aiProperties.getTemperature());
        requestBody.put("max_tokens", aiProperties.getMaxTokens());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        log.debug("Calling DeepSeek Chat API, model={}", aiProperties.getChatModel());

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
        HttpHeaders headers = buildHeaders();

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", aiProperties.getEmbeddingModel());
        requestBody.put("input", inputs.size() == 1 ? inputs.get(0) : inputs);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        log.debug("Calling DeepSeek Embedding API, model={}, batchSize={}",
                aiProperties.getEmbeddingModel(), inputs.size());

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
