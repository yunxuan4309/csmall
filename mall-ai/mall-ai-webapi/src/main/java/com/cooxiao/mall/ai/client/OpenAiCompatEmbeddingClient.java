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
 * <b>OpenAI 兼容协议</b>的 Embedding 客户端 —— 具体平台与模型<b>完全由配置决定</b>。
 *
 * <h3>为什么类名不写厂商（2026-09-11 重构，P1）</h3>
 * 原名 {@code SiliconFlowEmbeddingClient} 把<b>厂商写进了类名和日志</b>，会误导维护者
 * （旧 javadoc 甚至写着"免费调用" —— 这条早已过期，正是 TODO #59 误判的来源）。
 * 本类真正依赖的契约只有一条：<b>OpenAI 兼容的 {@code POST {base-url}/v1/embeddings}</b>。
 * 所以换到任何兼容平台（通义 / 智谱 / OpenAI / 本地 vLLM / Ollama 兼容层）→ <b>只改配置，不改代码</b>。
 *
 * <ul>
 *   <li>平台/模型：{@code cooxiao.ai.embedding-base-url} + {@code embedding-model}（生产用硅基流动 BGE-M3，1024 维）</li>
 *   <li>{@code encoding_format} 可选下发（个别平台不认这个 OpenAI 扩展字段 → 可关，P3）</li>
 * </ul>
 *
 * <p>上游（{@code RagServiceImpl} / {@code VectorSyncServiceImpl}）依赖的是接口 {@link EmbeddingClient}，
 * <b>不是本类</b> —— 将来接本地模型只要再写一个实现，上游一行都不用改。
 */
@Slf4j
@Component
public class OpenAiCompatEmbeddingClient implements EmbeddingClient {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private TokenBudgetService tokenBudgetService;

    /** 将文本转为向量 */
    @Override
    public float[] embed(String text) {
        JSONObject result = doEmbed(List.of(text));
        JSONArray embeddingArray = result.getJSONArray("data")
                .getJSONObject(0)
                .getJSONArray("embedding");
        return toFloatArray(embeddingArray);
    }

    /** 批量将文本转为向量（返回顺序与入参一致） */
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiProperties.getEmbeddingApiKey());

        Map<String, Object> body = new HashMap<>();
        body.put("model", aiProperties.getEmbeddingModel());
        body.put("input", inputs.size() == 1 ? inputs.get(0) : inputs);
        // P3（2026-09-11）：encoding_format 是 OpenAI 的扩展字段，少数平台不认 → 允许关掉
        if (aiProperties.isEmbeddingSendEncodingFormat()) {
            body.put("encoding_format", "float");
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        log.debug("调用 Embedding 接口: baseUrl={}, model={}, batchSize={}",
                aiProperties.getEmbeddingBaseUrl(), aiProperties.getEmbeddingModel(), inputs.size());

        ResponseEntity<String> response = restTemplate.postForEntity(
                aiProperties.getEmbeddingBaseUrl() + "/v1/embeddings",
                request,
                String.class);

        JSONObject json = JSON.parseObject(response.getBody());

        // 记录 token 费用（单价可配；embedding-price-per-million 为 0 时不产生预算消耗）
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
