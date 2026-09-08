package com.cooxiao.mall.ai.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggester;
import co.elastic.clients.elasticsearch.core.search.FieldSuggester;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cooxiao.mall.ai.client.AiClient;
import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.ai.service.TokenBudgetService;
import com.cooxiao.mall.pojo.ai.vo.RelatedProductVO;
import com.cooxiao.mall.pojo.ai.vo.SearchResultVO;
import com.cooxiao.mall.pojo.ai.vo.SuggestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 搜索增强服务
 * 三个功能：
 * 1. AI 语义重排序 — ES 召回 Top-15 → AI 按意图重排 → 返回 Top-5 + 解释
 * 2. 搜索自动补全 — ES Completion Suggester，<50ms
 * 3. 相关商品推荐 — ES more_like_this，纯 ES，不消耗 AI Token
 */
@Slf4j
@Service
public class SearchServiceImpl {

    private static final String INDEX_NAME = "cool_shark_mall_ai";
    private static final String SUGGEST_FIELD = "suggestField";
    private static final int RERANK_CANDIDATE = 15;
    private static final int RERANK_RESULT = 5;
    private static final String SEARCH_PROMPT =
            "你是电商导购。用户搜索：「%s」。请从以下%d个候选商品中，挑选最符合用户需求的%d个，" +
            "按推荐优先级排序。考虑因素：价格匹配度、品牌偏好、关键词匹配、商品热度。\n\n" +
            "候选商品列表：\n%s\n\n" +
            "请以JSON格式返回，只返回JSON不要其他内容：\n" +
            "{\"rankedIds\": [spuId按优先级排序，如[3,7,1,5,9]], \"explanation\": \"一句话说明排序逻辑(20字内)\"}";

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private AiClient aiClient;

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private TokenBudgetService tokenBudgetService;

    @Value("${custom.file-upload.resource-host:}")
    private String resourceHost;

    // ==================== AI 语义重排序 ====================

    public SearchResultVO search(String keyword, int page, int pageSize) {
        // 0. 预算检查（2026-08-14 补充：/ai/search 此前无预算检查，可被无限调用）
        if (tokenBudgetService.isBudgetExceeded()) {
            log.warn("今日 AI 预算已超限，跳过 AI 重排序，按 ES 原始排序返回");
            List<Map<String, Object>> rawCandidates = esKeywordSearch(keyword, RERANK_RESULT);
            List<RelatedProductVO> rawProducts = rawCandidates.stream()
                    .map(this::mapToVO)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            SearchResultVO busy = new SearchResultVO();
            busy.setProducts(rawProducts);
            busy.setAiExplanation("AI 服务繁忙，已按关键词匹配度排序");
            busy.setTotalCount((long) rawProducts.size());
            return busy;
        }

        // 1. ES 多路召回 Top-15
        List<Map<String, Object>> candidates = esKeywordSearch(keyword, RERANK_CANDIDATE);
        if (candidates.isEmpty()) {
            SearchResultVO empty = new SearchResultVO();
            empty.setProducts(List.of());
            empty.setTotalCount(0L);
            return empty;
        }

        // 2. AI 重排序（reasoning 模型偶发 content 为空 → 重试一次再降级）
        List<Long> rankedIds;
        String explanation;
        try {
            String candidateText = buildCandidateList(candidates);
            String prompt = String.format(SEARCH_PROMPT, keyword, candidates.size(),
                    RERANK_RESULT, candidateText);
            String[] result = callRerank(prompt);   // [rankedIdsJson, explanation] 或 null
            if (result == null) {
                // 重试一次：reasoning 模型思考过长致 content 为空是偶发的，二次调用通常成功
                log.warn("AI重排序首次返回异常，重试一次");
                result = callRerank(prompt);
            }
            if (result == null) {
                throw new IllegalStateException("AI重排序两次均失败");
            }
            rankedIds = com.alibaba.fastjson.JSONArray.parseArray(result[0])
                    .stream().map(o -> ((Number) o).longValue()).toList();
            explanation = result[1];
        } catch (Exception e) {
            log.warn("AI重排序失败，降级为ES原始排序: {}", e.getMessage());
            // 降级：直接返回 ES 原始排序的 Top-5
            rankedIds = candidates.stream()
                    .limit(RERANK_RESULT)
                    .map(m -> ((Number) m.get("spuId")).longValue())
                    .toList();
            explanation = "按关键词匹配度排序";
            // 不计费（AI 调用失败）
        }

        // 3. 组装结果
        Map<Long, Map<String, Object>> candidateMap = candidates.stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("spuId")).longValue(),
                        m -> m, (a, b) -> a, LinkedHashMap::new));

        List<RelatedProductVO> products = rankedIds.stream()
                .filter(candidateMap::containsKey)
                .map(id -> mapToVO(candidateMap.get(id)))
                .collect(Collectors.toList());

        SearchResultVO result = new SearchResultVO();
        result.setProducts(products);
        result.setAiExplanation(explanation);
        result.setTotalCount((long) candidates.size());
        return result;
    }

    // ==================== 搜索自动补全 ====================

    @SuppressWarnings("unchecked")
    public SuggestVO suggest(String keyword) {
        try {
            Suggester suggester = Suggester.of(s -> s
                    .suggesters("completion_suggest", FieldSuggester.of(fs -> fs
                            .prefix(keyword)
                            .completion(c -> c
                                    .field(SUGGEST_FIELD)
                                    .size(8)
                                    .skipDuplicates(true)
                            )
                    ))
            );

            SearchResponse<Map> response = esClient.search(req -> req
                    .index(INDEX_NAME)
                    .suggest(suggester), Map.class);

            List<String> suggestions = new ArrayList<>();
            if (response.suggest() != null) {
                var suggestResult = response.suggest().get("completion_suggest");
                if (suggestResult != null) {
                    suggestResult.forEach(s -> {
                        if (s.completion() != null) {
                            s.completion().options().forEach(opt -> {
                                String text = opt.text();
                                if (text != null) suggestions.add(text);
                            });
                        }
                    });
                }
            }

            SuggestVO vo = new SuggestVO();
            vo.setSuggestions(suggestions);
            return vo;
        } catch (Exception e) {
            log.warn("补全建议查询失败: {}", e.getMessage());
            SuggestVO fallback = new SuggestVO();
            fallback.setSuggestions(List.of());
            return fallback;
        }
    }

    // ==================== 相关商品推荐 ====================

    @SuppressWarnings("unchecked")
    public List<RelatedProductVO> getRelated(Long spuId) {
        try {
            // 先用 spuId 查到对应文档确认存在
            SearchResponse<Map> response = esClient.search(req -> req
                    .index(INDEX_NAME)
                    .query(q -> q.moreLikeThis(mlt -> mlt
                            .fields(List.of("name", "title", "description", "tags", "brandName"))
                            .like(l -> l.document(doc -> doc
                                    .index(INDEX_NAME)
                                    .id(String.valueOf(spuId))
                            ))
                            .minTermFreq(1)
                            .minDocFreq(1)
                            .maxQueryTerms(12)
                    ))
                    .size(6), Map.class);

            return response.hits().hits().stream()
                    .filter(hit -> !hit.id().equals(String.valueOf(spuId)))
                    .map(hit -> (Map<String, Object>) hit.source())
                    .filter(Objects::nonNull)
                    .map(this::mapToVO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("相关商品查询失败, spuId={}: {}", spuId, e.getMessage());
            return List.of();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 调用 AI 重排并解析结果
     *
     * @return String[2] = {rankedIds 的 JSON 数组字符串, explanation}；解析失败返回 null（供调用方重试/降级）
     */
    private String[] callRerank(String prompt) {
        try {
            String aiResponse = aiClient.chatWithModel(
                    // ⚠️ 关键：deepseek-v4-flash 是 reasoning 模型，若不禁思考会把 max_tokens 全耗在
                    // reasoning_content 上导致 content 为空（实测 reasoning_tokens=4000=max_tokens）。
                    // "不要任何思考过程，直接输出" 可关掉过度思考（实测 reasoning_tokens 降到 58，1.6s 返回）
                    "你是专业的电商导购。直接输出 JSON，不要任何思考过程，不要输出 reasoning，不要解释。",
                    prompt, aiProperties.getChatModel(), true);
            // 清理 AI 可能输出的 markdown 包裹（```json ... ```）
            if (aiResponse != null) {
                aiResponse = aiResponse.trim();
                if (aiResponse.startsWith("```")) {
                    aiResponse = aiResponse.replaceAll("```json?", "").replace("```", "").trim();
                }
            }
            if (aiResponse == null || aiResponse.isBlank()) {
                log.warn("AI重排序响应为空（可能 reasoning 耗尽 max_tokens）");
                return null;
            }
            JSONObject aiJson = JSON.parseObject(aiResponse);
            if (aiJson == null) {
                log.warn("AI重排序响应非 JSON: {}", truncate(aiResponse, 200));
                return null;
            }
            String rankedIdsJson = aiJson.getJSONArray("rankedIds").toJSONString();
            String explanation = aiJson.getString("explanation");
            return new String[]{rankedIdsJson, explanation};
        } catch (Exception e) {
            log.warn("AI重排序调用异常: {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String s, int max) {
        return s == null ? "" : (s.length() > max ? s.substring(0, max) + "..." : s);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> esKeywordSearch(String keyword, int size) {
        try {
            SearchResponse<Map> response = esClient.search(req -> req
                    .index(INDEX_NAME)
                    .query(q -> q.bool(b -> b
                            .should(s1 -> s1.match(m -> m.field("name").query(keyword).boost(5.0f)))
                            .should(s2 -> s2.match(m -> m.field("title").query(keyword).boost(4.0f)))
                            .should(s3 -> s3.match(m -> m.field("semanticText").query(keyword).boost(3.0f)))
                            .should(s4 -> s4.match(m -> m.field("description").query(keyword).boost(2.0f)))
                            .should(s5 -> s5.match(m -> m.field("brandName").query(keyword)))
                            .should(s6 -> s6.match(m -> m.field("categoryName").query(keyword)))
                            .should(s7 -> s7.match(m -> m.field("tags").query(keyword)))
                    ))
                    .size(size), Map.class);

            return response.hits().hits().stream()
                    .map(hit -> (Map<String, Object>) hit.source())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES搜索失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildCandidateList(List<Map<String, Object>> candidates) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            Map<String, Object> item = candidates.get(i);
            sb.append(String.format("%d. [spuId=%s] %s | %s | ¥%s | 销量:%s | 标签:%s\n",
                    i + 1,
                    item.get("spuId"),
                    item.get("name"),
                    item.get("title"),
                    item.get("listPrice"),
                    item.getOrDefault("sales", 0),
                    item.getOrDefault("tags", "")));
        }
        return sb.toString();
    }

    private RelatedProductVO mapToVO(Map<String, Object> source) {
        RelatedProductVO vo = new RelatedProductVO();
        vo.setSpuId(toLong(source.get("spuId")));
        vo.setName(toString(source.get("name")));
        vo.setTitle(toString(source.get("title")));
        vo.setListPrice(toBigDecimal(source.get("listPrice")));
        vo.setBrandName(toString(source.get("brandName")));
        vo.setCategoryName(toString(source.get("categoryName")));
        vo.setTags(toString(source.get("tags")));
        vo.setSales(toInt(source.get("sales")));

        // 提取首张图片
        String pictures = toString(source.get("pictures"));
        if (pictures != null && !pictures.isEmpty()) {
            try {
                JSONArray arr = JSON.parseArray(pictures);
                if (!arr.isEmpty()) {
                    String firstPic = arr.getString(0);
                    vo.setPicture(firstPic.startsWith("http") ? firstPic
                            : resourceHost + firstPic);
                }
            } catch (Exception ignored) {
                vo.setPicture(pictures);
            }
        }
        return vo;
    }

    private String toString(Object v) { return v == null ? null : v.toString(); }
    private Long toLong(Object v) { return v instanceof Number n ? n.longValue() : null; }
    private Integer toInt(Object v) { return v instanceof Number n ? n.intValue() : null; }
    private BigDecimal toBigDecimal(Object v) {
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (v instanceof String s) return new BigDecimal(s);
        return null;
    }
}
