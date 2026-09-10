package com.cooxiao.mall.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.cooxiao.mall.ai.model.SearchIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品检索工具（TODO #32 P0 唯一的工具，{@code search_products}）。
 *
 * <p>它把原先"每次对话都先跑一遍意图提取 → ES 检索"的<b>固定流水线</b>，改造成
 * <b>由模型自己决定何时查、查什么、要不要换个条件再查</b> —— 这是 P0 与旧流程的本质区别。
 *
 * <p>复用 {@link RagServiceImpl#intentSearch} 等现有方法（同一个包，包级可见），
 * 检索条件与旧的意图提取流程<b>完全一致</b>：换的是"谁来定条件"，不是"怎么查"，
 * 所以检索质量不会因为引入 Agent 而回退。
 */
@Slf4j
@Component
public class SearchProductsTool implements AiTool {

    /** 单次检索返回条数：够模型挑，又不至于把 observation 撑爆（10 条 ≈ 600 token） */
    private static final int TOP_K = 10;

    /** sortBy 白名单 —— 模型给的字符串必须落在这里，否则一律按相关度（不拼进 ES 查询） */
    private static final Set<String> SORT_WHITELIST = Set.of("sales", "price_asc", "price_desc");

    private static final int MAX_KEYWORDS_LEN = 100;
    private static final int MAX_WORD_LEN = 50;

    @Autowired
    private RagServiceImpl ragService;

    @Override
    public String name() {
        return "search_products";
    }

    @Override
    public String description() {
        return "在 CoolShark 商城的真实商品库中检索商品，返回商品名/品牌/分类/价格/销量。"
                + "用户提出找商品、比价、要推荐时必须先调用它取真实数据，不要凭记忆回答。"
                + "一次调用只表达一组条件；需要多组条件（例如先看 5000 元以内、再看带降噪的）就分多次调用。"
                + "如果返回 count=0，可以放宽条件（例如去掉品牌或价格）再调用一次。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keywords", Map.of("type", "string",
                "description", "搜索关键词：品类/用途/风格等核心词，例如「手机」「女士外套」「降噪耳机」「运动鞋」"));
        properties.put("budgetMin", Map.of("type", "number",
                "description", "最低价（元）。用户说「3000 左右」时传 2250；说「5000 以内」时不传"));
        properties.put("budgetMax", Map.of("type", "number",
                "description", "最高价（元）。用户说「3000 左右」时传 3900；说「5000 以内」时传 5000"));
        properties.put("brand", Map.of("type", "string",
                "description", "品牌名，例如「华为」。用户没提品牌就不要填，不要在品牌维度上自行发挥"));
        properties.put("category", Map.of("type", "string",
                "description", "品类词，直接使用用户原话，例如用户说「衣服」就填「衣服」（系统会模糊匹配，不要改写成「男装」）"));
        properties.put("sortBy", Map.of("type", "string", "enum", List.of("sales", "price_asc", "price_desc"),
                "description", "排序：sales 销量优先 / price_asc 价格从低到高 / price_desc 价格从高到低。默认按相关度"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("keywords"));
        return schema;
    }

    @Override
    public AiToolResult execute(Map<String, Object> args) {
        Map<String, Object> safeArgs = args == null ? Map.of() : args;

        // ---- ① 边界收敛：模型给的参数不可信（可能超长、可能上下限颠倒、可能给白名单外的排序）----
        String keywords = truncate(str(safeArgs.get("keywords")), MAX_KEYWORDS_LEN);
        String category = truncate(str(safeArgs.get("category")), MAX_WORD_LEN);
        String brand = truncate(str(safeArgs.get("brand")), MAX_WORD_LEN);
        String sortBy = str(safeArgs.get("sortBy"));
        if (sortBy != null && !SORT_WHITELIST.contains(sortBy)) {
            log.warn("工具参数 sortBy 不在白名单内，已忽略: {}", sortBy);
            sortBy = null;
        }

        Double budgetMin = num(safeArgs.get("budgetMin"));
        Double budgetMax = num(safeArgs.get("budgetMax"));
        if (budgetMin != null && budgetMin < 0) budgetMin = null;
        if (budgetMax != null && budgetMax < 0) budgetMax = null;
        if (budgetMin != null && budgetMax != null && budgetMin > budgetMax) {
            Double swap = budgetMin;   // 模型把上下限填反是常见错误 → 纠正比报错好
            budgetMin = budgetMax;
            budgetMax = swap;
        }

        // ---- ② 检索（与旧意图提取流程同一套方法，条件一致）----
        SearchIntent intent = new SearchIntent();
        intent.setKeywords(keywords);
        intent.setCategory(category);
        intent.setBrand(brand);
        intent.setBudgetMin(budgetMin);
        intent.setBudgetMax(budgetMax);
        intent.setSortBy(sortBy);

        try {
            List<Map<String, Object>> hits = ragService.intentSearch(intent, TOP_K);
            boolean fallback = false;
            if (hits.isEmpty()) {
                // 价格/品牌过滤后为空很常见（品牌名差一个字就没了）→ 去掉过滤再兜一次，
                // 让模型看到"确实有这类商品只是不满足某条件"，而不是一个干巴巴的 count=0
                String plain = (keywords == null || keywords.isBlank())
                        ? (category == null ? "" : category)
                        : (category == null || category.isBlank() ? keywords : keywords + " " + category);
                if (!plain.isBlank()) {
                    hits = ragService.fullTextSearchNoPrice(plain, TOP_K);
                    fallback = !hits.isEmpty();
                }
            }

            log.info("工具 search_products：keywords={}, brand={}, price={}~{}（已放宽={}）→ 命中 {} 条",
                    keywords, brand, budgetMin, budgetMax, fallback, hits.size());
            return new AiToolResult(buildObservation(hits, fallback), hits);
        } catch (Exception e) {
            log.error("工具 search_products 执行失败", e);
            return AiToolResult.error(e.getMessage());
        }
    }

    /**
     * 组装给模型看的观察结果 —— 用紧凑 JSON 而不是 {@code buildContext} 的散文格式：
     * 同样的商品信息，token 约为散文的三分之一，而 Agent 每一轮都要把历史全带上，省下来的是复利。
     */
    private String buildObservation(List<Map<String, Object>> hits, boolean relaxed) {
        List<Map<String, Object>> products = new ArrayList<>(hits.size());
        for (Map<String, Object> doc : hits) {
            Map<String, Object> item = new LinkedHashMap<>();   // Map.of 不接受 null 值，这里必须用 LinkedHashMap
            item.put("spuId", doc.get("spuId"));
            item.put("name", doc.get("name"));
            item.put("brand", doc.get("brandName"));
            item.put("category", doc.get("categoryName"));
            item.put("price", doc.get("listPrice"));
            item.put("sales", doc.get("sales"));
            item.put("tags", doc.get("tags"));
            products.add(item);
        }

        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("count", products.size());
        if (relaxed) {
            observation.put("note", "严格匹配（含价格/品牌过滤）无结果，已放宽条件返回近似商品");
        }
        observation.put("products", products);
        return JSON.toJSONString(observation);
    }

    /** 取字符串：模型可能把纯数字的参数写成数字类型，统一 toString */
    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Double num(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
