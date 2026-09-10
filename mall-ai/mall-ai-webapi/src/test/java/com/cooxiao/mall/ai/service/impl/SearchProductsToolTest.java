package com.cooxiao.mall.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cooxiao.mall.ai.model.SearchIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品检索工具（TODO #32 P0）—— 重点验证<b>模型给的参数不可信</b>这条底线。
 *
 * <p>Agent 与传统流水线最大的安全差异：查询条件不再由我方代码拼装，而是<b>模型给出的自由文本/数字</b>。
 * 因此边界收敛（范围钳制、枚举白名单、长度截断、上下限颠倒纠正）必须被测试锁住。
 */
class SearchProductsToolTest {

    private FakeRagService ragService;
    private SearchProductsTool tool;

    @BeforeEach
    void setUp() {
        ragService = new FakeRagService();
        tool = new SearchProductsTool();
        ReflectionTestUtils.setField(tool, "ragService", ragService);
    }

    /**
     * 手写假实现，不用 Mockito：{@code mockito-inline} 的 mock maker 需要 byte-buddy agent
     * 动态 attach（Windows 上走命名管道），在本机受限环境下无法初始化。
     * 这里只需要"记录入参 + 返回固定结果"，手写反而更直观、更稳。
     */
    private static class FakeRagService extends RagServiceImpl {

        SearchIntent lastIntent;
        int lastTopK;
        int intentSearchCalls;
        int fallbackCalls;
        String lastFallbackQuestion;

        List<Map<String, Object>> intentHits = List.of();
        List<Map<String, Object>> fallbackHits = List.of();

        @Override
        List<Map<String, Object>> intentSearch(Object intent, int topK) {
            this.lastIntent = (SearchIntent) intent;
            this.lastTopK = topK;
            this.intentSearchCalls++;
            return intentHits;
        }

        @Override
        public List<Map<String, Object>> fullTextSearchNoPrice(String question, int topK) {
            this.fallbackCalls++;
            this.lastFallbackQuestion = question;
            return fallbackHits;
        }
    }

    private static Map<String, Object> hit(String name, double price) {
        Map<String, Object> doc = new LinkedHashMap<>();   // 真实 ES 文档字段可能为 null → 用允许 null 的 Map
        doc.put("spuId", 1001L);
        doc.put("name", name);
        doc.put("brandName", "酷鲨");
        doc.put("categoryName", "手机");
        doc.put("listPrice", price);
        doc.put("sales", 300);
        doc.put("tags", "5G");
        return doc;
    }

    @Test
    void schema_isOpenAiCompatible() {
        Map<String, Object> schema = tool.parameters();

        assertThat(tool.name()).isEqualTo("search_products");
        assertThat(tool.description()).isNotBlank();
        assertThat(schema).containsEntry("type", "object");
        assertThat(schema.get("required")).isEqualTo(List.of("keywords"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKeys("keywords", "budgetMin", "budgetMax", "brand", "category", "sortBy");
    }

    @Test
    void sanitizesUntrustedArguments() {
        ragService.intentHits = List.of(hit("酷鲨手机", 4999));

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("keywords", "手".repeat(200));            // 超长 → 截断
        args.put("brand", "品".repeat(80));                 // 超长 → 截断
        args.put("budgetMin", 8000);                        // 上下限颠倒 → 交换
        args.put("budgetMax", 3000);
        args.put("sortBy", "'; DROP TABLE x; --");          // 白名单外 → 忽略（绝不拼进 ES 查询）
        args.put("category", "手机");

        AiToolResult result = tool.execute(args);

        assertThat(result.hits()).hasSize(1);
        SearchIntent intent = ragService.lastIntent;
        assertThat(intent.getBudgetMin()).isEqualTo(3000);
        assertThat(intent.getBudgetMax()).isEqualTo(8000);
        assertThat(intent.getSortBy()).isNull();
        assertThat(intent.getKeywords()).hasSize(100);
        assertThat(intent.getBrand()).hasSize(50);
        assertThat(intent.getCategory()).isEqualTo("手机");
        assertThat(ragService.lastTopK).isEqualTo(10);
    }

    @Test
    void acceptsNumericStrings_andDropsNegativeBudget() {
        ragService.intentHits = List.of(hit("酷鲨手机", 3999));

        AiToolResult result = tool.execute(Map.of("keywords", "手机", "budgetMax", "-1"));

        assertThat(result.hits()).hasSize(1);
        // 负数预算视为无效，而不是当成"≤ -1 元"
        assertThat(ragService.lastIntent.getBudgetMax()).isNull();
        assertThat(ragService.lastIntent.getBudgetMin()).isNull();
    }

    @Test
    void sortByWhitelist_keepsLegalValue() {
        ragService.intentHits = List.of(hit("酷鲨手机", 3999));

        tool.execute(Map.of("keywords", "手机", "sortBy", "price_asc"));

        assertThat(ragService.lastIntent.getSortBy()).isEqualTo("price_asc");
    }

    @Test
    void emptyStrictResult_fallsBackToPlainFullTextSearch_andTellsModelWhy() {
        ragService.intentHits = List.of();
        ragService.fallbackHits = List.of(hit("酷鲨手机", 5999));

        AiToolResult result = tool.execute(Map.of("keywords", "手机", "brand", "不存在的品牌"));

        assertThat(result.hits()).hasSize(1);
        assertThat(ragService.fallbackCalls).isEqualTo(1);
        JSONObject observation = JSON.parseObject(result.observation());
        assertThat(observation.getIntValue("count")).isEqualTo(1);
        // 必须让模型知道"已放宽条件"，否则它会以为这就是严格匹配的结果、继续误导用户
        assertThat(observation.getString("note")).contains("放宽");
        assertThat(observation.getJSONArray("products").getJSONObject(0).getString("name")).isEqualTo("酷鲨手机");
    }

    @Test
    void blankKeywords_skipsFallback_butStillReturnsObservation() {
        ragService.intentHits = List.of();

        AiToolResult result = tool.execute(Map.of());

        assertThat(result.hits()).isEmpty();
        assertThat(ragService.intentSearchCalls).isEqualTo(1);
        assertThat(ragService.fallbackCalls).isZero();   // 连关键词都没有 → 放宽检索也无从谈起
        assertThat(JSON.parseObject(result.observation()).getIntValue("count")).isZero();
    }

    @Test
    void nullArgs_doNotThrow() {
        AiToolResult result = tool.execute(null);

        assertThat(result.observation()).isNotBlank();
        assertThat(result.hits()).isEqualTo(new ArrayList<>());
    }
}
