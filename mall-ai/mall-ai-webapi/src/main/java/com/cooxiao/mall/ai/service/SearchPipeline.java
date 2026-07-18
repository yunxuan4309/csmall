package com.cooxiao.mall.ai.service;

import com.alibaba.fastjson.JSON;
import com.cooxiao.mall.ai.client.AiClient;
import com.cooxiao.mall.ai.model.SearchIntent;
import com.cooxiao.mall.ai.service.impl.RagServiceImpl;
import com.cooxiao.mall.pojo.ai.vo.RelatedProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

/**
 * 企业级搜索流水线
 * 预处理 → AI查询扩展 → 多路召回 → 融合排序 → 结果输出
 * 每个阶段通过 thinking 回调输出可视化思考过程
 */
@Slf4j
@Service
public class SearchPipeline {

    @Autowired private RagServiceImpl ragService;
    @Autowired private AiClient aiClient;

    /** 运行搜索流水线，每一步通过 onThinking 回调输出 */
    public PipelineResult run(SearchIntent intent, String userMessage, int topK,
                               Consumer<String> onThinking) {

        List<RelatedProductVO> allResults = new ArrayList<>();
        Map<Long, RelatedProductVO> merged = new LinkedHashMap<>();

        // ========== Stage 1: 预处理 ==========
        onThinking.accept("🔍 意图解析 → " + describeIntent(intent));

        // ========== Stage 2: AI 查询扩展 ==========
        String expandedQuery = expandQuery(userMessage);
        onThinking.accept("📝 查询扩展 → " + (expandedQuery.equals(userMessage) ?
                "无需扩展" : expandedQuery));

        // ========== Stage 3: 多路召回 ==========
        // 路线A: 关键词检索（带扩展查询）
        String searchText = (intent.getKeywords() != null ? intent.getKeywords() + " " : "") + expandedQuery;
        List<Map<String, Object>> keywordHits = ragService.structuredSearch(searchText,
                intent.getBudgetMin(), intent.getBudgetMax(), intent.getBrand(), topK);
        onThinking.accept("🔎 关键词召回 → " + keywordHits.size() + " 条");

        // 路线B: 无价格过滤的全量搜索（兜底）
        List<Map<String, Object>> fullHits = ragService.fullTextSearchNoPrice(searchText, topK);
        onThinking.accept("🔎 全文召回 → " + fullHits.size() + " 条（兜底）");

        // ========== Stage 4: 融合去重 & 排序 ==========
        for (List<Map<String, Object>> hits : List.of(keywordHits, fullHits)) {
            for (Map<String, Object> hit : hits) {
                RelatedProductVO vo = mapToVO(hit);
                if (vo != null && vo.getSpuId() != null) {
                    merged.putIfAbsent(vo.getSpuId(), vo);
                }
            }
        }
        allResults.addAll(merged.values());
        onThinking.accept("🎯 融合排序 → 合并去重得 " + allResults.size() + " 条");

        if (allResults.isEmpty()) {
            // 最后的兜底：按预算范围宽泛搜索
            List<Map<String, Object>> wideHits = ragService.fullTextSearchNoPrice("", topK * 2);
            for (Map<String, Object> hit : wideHits) {
                RelatedProductVO vo = mapToVO(hit);
                if (vo != null && vo.getSpuId() != null) {
                    allResults.add(vo);
                }
            }
            onThinking.accept("⚠️ 扩量兜底 → " + allResults.size() + " 条（触发零结果保护）");
        }

        PipelineResult result = new PipelineResult();
        result.setProducts(allResults);
        result.setProductCount(allResults.size());

        // 构建搜索上下文
        StringBuilder ctx = new StringBuilder();
        for (RelatedProductVO p : allResults) {
            ctx.append("【").append(p.getName()).append("】")
               .append(" ¥").append(p.getListPrice())
               .append(" | ").append(p.getBrandName())
               .append(" | ").append(p.getCategoryName())
               .append(" | 销量").append(p.getSales())
               .append(" | ").append(p.getTitle()).append("\n");
        }
        result.setSearchContext(ctx.toString());

        // Stage 5: 可用分类提示（用于零结果时引导用户）
        Set<String> availableCategories = new LinkedHashSet<>();
        for (RelatedProductVO p : allResults) {
            if (p.getCategoryName() != null) availableCategories.add(p.getCategoryName());
        }
        result.setAvailableCategories(new ArrayList<>(availableCategories));

        return result;
    }

    /** AI 驱动的查询扩展：将用户口语转为搜索关键词 */
    private String expandQuery(String userMessage) {
        try {
            String prompt = """
                    你是一个电商搜索关键词扩展器。将用户的口语化描述扩展为搜索关键词。
                    不要输出解释，只输出空格分隔的关键词。

                    规则：
                    1. "衣服"→"男装 女装 服装 服饰 外套"
                    2. "鞋子"→"运动鞋 休闲鞋 皮鞋 鞋类"
                    3. "电脑"→"笔记本 台式机 平板电脑"
                    4. 保留原有的价格/品牌/特征词
                    5. 不超过20个词

                    用户输入：%s
                    关键词：""".formatted(userMessage);

            String response = aiClient.chat(null, prompt);
            response = response.trim().replaceAll("[\"'\\n]", " ");
            log.info("查询扩展: {} → {}", userMessage, response);
            return response.isBlank() ? userMessage : response;
        } catch (Exception e) {
            log.warn("查询扩展失败，使用原始输入: {}", e.getMessage());
            return userMessage;
        }
    }

    private String describeIntent(SearchIntent intent) {
        StringBuilder sb = new StringBuilder();
        if (intent.getBudgetMin() != null || intent.getBudgetMax() != null) {
            sb.append("预算");
            if (intent.getBudgetMin() != null) sb.append(intent.getBudgetMin().intValue());
            sb.append("~");
            if (intent.getBudgetMax() != null) sb.append(intent.getBudgetMax().intValue());
            sb.append("元 ");
        }
        if (intent.getCategory() != null) sb.append("品类「").append(intent.getCategory()).append("」");
        if (intent.getBrand() != null) sb.append(" 品牌「").append(intent.getBrand()).append("」");
        return sb.isEmpty() ? "通用搜索" : sb.toString();
    }

    private RelatedProductVO mapToVO(Map<String, Object> doc) {
        try {
            RelatedProductVO vo = new RelatedProductVO();
            Object idObj = doc.get("spuId");
            if (idObj == null) return null;
            vo.setSpuId(Long.valueOf(idObj.toString()));
            vo.setName(Objects.toString(doc.get("name"), ""));
            vo.setTitle(Objects.toString(doc.get("title"), ""));
            vo.setListPrice(doc.get("listPrice") != null ?
                    new java.math.BigDecimal(doc.get("listPrice").toString()) : java.math.BigDecimal.ZERO);
            vo.setBrandName(Objects.toString(doc.get("brandName"), ""));
            vo.setCategoryName(Objects.toString(doc.get("categoryName"), ""));
            vo.setSales(doc.get("sales") != null ?
                    Integer.valueOf(doc.get("sales").toString()) : 0);
            vo.setPicture(ragService.extractFirstPicture(doc.get("pictures")));
            String tags = Objects.toString(doc.get("tags"), "");
            if (!tags.isEmpty()) vo.setTags(tags);
            return vo;
        } catch (Exception e) {
            return null;
        }
    }

    public static class PipelineResult {
        private List<RelatedProductVO> products;
        private int productCount;
        private String searchContext;
        private List<String> availableCategories;

        public List<RelatedProductVO> getProducts() { return products; }
        public void setProducts(List<RelatedProductVO> p) { this.products = p; }
        public int getProductCount() { return productCount; }
        public void setProductCount(int n) { this.productCount = n; }
        public String getSearchContext() { return searchContext; }
        public void setSearchContext(String s) { this.searchContext = s; }
        public List<String> getAvailableCategories() { return availableCategories; }
        public void setAvailableCategories(List<String> c) { this.availableCategories = c; }
    }
}
