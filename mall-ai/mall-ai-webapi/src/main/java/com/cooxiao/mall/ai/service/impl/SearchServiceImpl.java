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
        // 1. ES 多路召回 Top-15
        List<Map<String, Object>> candidates = esKeywordSearch(keyword, RERANK_CANDIDATE);
        if (candidates.isEmpty()) {
            SearchResultVO empty = new SearchResultVO();
            empty.setProducts(List.of());
            empty.setTotalCount(0L);
            return empty;
        }

        // 2. AI 重排序
        List<Long> rankedIds;
        String explanation;
        try {
            String candidateText = buildCandidateList(candidates);
            String prompt = String.format(SEARCH_PROMPT, keyword, candidates.size(),
                    RERANK_RESULT, candidateText);
            String aiResponse = aiClient.chat(
                    "你是专业的电商导购，以JSON格式回复。", prompt);
            JSONObject aiJson = JSON.parseObject(aiResponse);
            rankedIds = aiJson.getJSONArray("rankedIds")
                    .stream().map(o -> ((Number) o).longValue()).toList();
            explanation = aiJson.getString("explanation");
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
