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
 * 硅基流动（SiliconFlow）Embedding 客户端
 * 使用 BGE-M3 模型，兼容 OpenAI API 格式，免费调用
 */
@Slf4j
@Component
public class SiliconFlowEmbeddingClient {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private TokenBudgetService tokenBudgetService;

    /** 将文本转为向量 */
    public float[] embed(String text) {
        JSONObject result = doEmbed(List.of(text));
        JSONArray embeddingArray = result.getJSONArray("data")
                .getJSONObject(0)
                .getJSONArray("embedding");
        return toFloatArray(embeddingArray);
    }

    /** 批量将文本转为向量 */
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiProperties.getEmbeddingApiKey());

        Map<String, Object> body = new HashMap<>();
        body.put("model", aiProperties.getEmbeddingModel());
        body.put("input", inputs.size() == 1 ? inputs.get(0) : inputs);
        body.put("encoding_format", "float");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.debug("Calling SiliconFlow Embedding API, model={}, batchSize={}",
                aiProperties.getEmbeddingModel(), inputs.size());

        ResponseEntity<String> response = restTemplate.postForEntity(
                aiProperties.getEmbeddingBaseUrl() + "/v1/embeddings",
                request,
                String.class);

        JSONObject json = JSON.parseObject(response.getBody());

        // 记录 token 费用
        JSONObject usage = json.getJSONObject("usage");
        if (usage != null) {
            int totalTokens = usage.getIntValue("total_tokens");
            double cost = aiProperties.getEmbeddingPricePerMillion() * totalTokens / 1_000_000;
            tokenBudgetService.record(cost);
            log.debug("Embedding tokens: {}, cost: {} 元", totalTokens, String.format("%.6f", cost));
        }

        return json;
    }

    private float[] toFloatArray(JSONArray array) {
        float[] result = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            result[i] = array.getFloatValue(i);
        }
        return result;
    }
}
