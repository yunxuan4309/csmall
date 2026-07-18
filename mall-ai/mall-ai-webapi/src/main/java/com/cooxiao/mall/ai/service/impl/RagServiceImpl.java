package com.cooxiao.mall.ai.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.alibaba.fastjson.JSONArray;
import com.cooxiao.mall.ai.client.AiClient;
import com.cooxiao.mall.ai.client.SiliconFlowEmbeddingClient;
import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.ai.service.TokenBudgetService;
import com.cooxiao.mall.pojo.ai.vo.AskResultVO;
import com.cooxiao.mall.pojo.ai.vo.RelatedProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG 智能问答服务
 * 支持两种检索模式：
 * 1. ES IK 分词全文检索（当前默认）— 不依赖 embedding API
 * 2. 向量语义检索（预留扩展）— 通过 embedding API 将问题转为向量，余弦相似度匹配
 */
@Slf4j
@Service
public class RagServiceImpl {

    private static final String INDEX_NAME = "cool_shark_mall_ai";

    /** ES 全文检索字段权重：名称 > 标题 > 语义文本 > 描述 > 品牌 > 标签 */
    private static final List<String> SEARCH_FIELDS = List.of(
            "name^5", "title^4", "semanticText^3", "description^2",
            "brandName", "categoryName", "tags");

    @Autowired
    private AiClient aiClient;

    @Autowired
    private SiliconFlowEmbeddingClient embeddingClient;

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private TokenBudgetService tokenBudgetService;

    @Autowired
    private AiProperties aiProperties;

    @Value("${custom.file-upload.resource-host:}")
    private String resourceHost;

    /**
     * RAG 问答
     *
     * @param question 用户问题
     * @param topK     返回结果数
     * @return 回答 + 相关商品
     */
    @SuppressWarnings("unchecked")
    public AskResultVO ask(String question, int topK) {
        // 0. 预算检查
        if (tokenBudgetService.isBudgetExceeded()) {
            AskResultVO busyResult = new AskResultVO();
            busyResult.setAnswer("服务繁忙，请稍后再试。");
            busyResult.setRelatedProducts(List.of());
            log.warn("今日 AI 预算已超限，拒绝 RAG 请求");
            return busyResult;
        }

        // 1. 检索相关商品（根据配置选择全文检索或向量检索）
        List<Map<String, Object>> hits;
        if (aiProperties.isEmbeddingEnabled()) {
            log.info("使用向量语义检索模式（硅基流动 BGE-M3）");
            float[] queryVector = embeddingClient.embed(question);
            hits = vectorSearch(queryVector, topK);
        } else {
            log.info("使用 ES 全文检索模式");
            hits = fullTextSearch(question, topK);
        }

        // 2. 构建检索结果上下文
        String context = buildContext(hits);
        List<RelatedProductVO> relatedProducts = buildRelatedProducts(hits);

        // 3. AI 生成回答
        String answer = generateAnswer(question, context, relatedProducts);

        // 4. 组装结果
        AskResultVO result = new AskResultVO();
        result.setAnswer(answer);
        result.setRelatedProducts(relatedProducts);
        return result;
    }

    // ========== ES 结构化检索（AI 意图提取后使用） ==========

    /** 简化的结构化搜索，用于 SearchPipeline 多路召回 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> structuredSearch(String keywords, Double budgetMin,
                                                       Double budgetMax, String brand, int topK) {
        try {
            SearchResponse<Map<String, Object>> response = esClient.search(s -> {
                s.index(INDEX_NAME).size(topK);
                s.query(q -> q.bool(b -> {
                    if (keywords != null && !keywords.isBlank()) {
                        b.must(m -> m.multiMatch(mm -> mm.query(keywords).fields(SEARCH_FIELDS)));
                    } else {
                        b.must(m -> m.matchAll(ma -> ma));
                    }
                    if (budgetMin != null)
                        b.filter(f -> f.range(r -> r.field("listPrice").gte(JsonData.of(budgetMin))));
                    if (budgetMax != null)
                        b.filter(f -> f.range(r -> r.field("listPrice").lte(JsonData.of(budgetMax))));
                    if (brand != null && !brand.isBlank())
                        b.filter(f -> f.term(t -> t.field("brandName").value(brand)));
                    return b;
                }));
                return s;
            }, (Class<Map<String, Object>>) (Class<?>) Map.class);
            return response.hits().hits().stream().map(Hit::source)
                    .filter(s -> s != null).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("结构化检索失败", e);
            return List.of();
        }
    }

    List<Map<String, Object>> intentSearch(Object intentObj, int topK) {
        com.cooxiao.mall.ai.model.SearchIntent intent =
                (com.cooxiao.mall.ai.model.SearchIntent) intentObj;
        try {
            Double budgetMin = intent.getBudgetMin();
            Double budgetMax = intent.getBudgetMax();
            String brand = intent.getBrand();
            String category = intent.getCategory();
            String keywords = intent.getKeywords();

            // 将分类词合并到关键词中，利用 multi_match 的 IK 分词模糊匹配
            // 避免 term 精确过滤导致"衣服"匹配不到"男装/女装"
            String searchText = keywords;
            if (category != null && !category.isBlank()) {
                searchText = (searchText != null && !searchText.isBlank())
                        ? searchText + " " + category : category;
            }

            final String finalSearchText = searchText;
            SearchResponse<Map<String, Object>> response = esClient.search(s -> {
                s.index(INDEX_NAME).size(topK);
                s.query(q -> q.bool(b -> {
                    if (finalSearchText != null && !finalSearchText.isBlank()) {
                        b.must(m -> m.multiMatch(mm -> mm
                                .query(finalSearchText).fields(SEARCH_FIELDS)));
                    } else {
                        b.must(m -> m.matchAll(ma -> ma));
                    }
                    if (budgetMin != null)
                        b.filter(f -> f.range(r -> r.field("listPrice").gte(JsonData.of(budgetMin))));
                    if (budgetMax != null)
                        b.filter(f -> f.range(r -> r.field("listPrice").lte(JsonData.of(budgetMax))));
                    if (brand != null && !brand.isBlank())
                        b.filter(f -> f.term(t -> t.field("brandName").value(brand)));
                    return b;
                }));
                String sort = intent.getSortBy();
                if ("sales".equals(sort)) {
                    s.sort(srt -> srt.field(f -> f.field("sales").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)));
                } else if ("price_asc".equals(sort)) {
                    s.sort(srt -> srt.field(f -> f.field("listPrice").order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)));
                } else if ("price_desc".equals(sort)) {
                    s.sort(srt -> srt.field(f -> f.field("listPrice").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)));
                }
                return s;
            }, (Class<Map<String, Object>>) (Class<?>) Map.class);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(s -> s != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("结构化 ES 检索失败", e);
            return List.of();
        }
    }

    // ========== ES 全文检索 ==========

    /**
     * 纯文本搜索，不过滤价格（用于价格过滤结果为空时的降级兜底）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fullTextSearchNoPrice(String question, int topK) {
        try {
            SearchResponse<Map<String, Object>> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(topK)
                            .query(q -> q.multiMatch(mm -> mm
                                    .query(question)
                                    .fields(SEARCH_FIELDS))),
                    (Class<Map<String, Object>>) (Class<?>) Map.class);
            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(s -> s != null)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES 全文检索（无价格过滤）失败", e);
            return List.of();
        }
    }

    /**
     * ES IK 分词 full-text search — 含价格范围智能过滤
     * 从用户问题中提取价格约束（如"3000元左右"→2700~3300，"7000以上"→≥7000）
     */
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> fullTextSearch(String question, int topK) {
        try {
            PriceRange price = extractPriceRange(question);

            SearchResponse<Map<String, Object>> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(topK)
                            .query(q -> q.bool(b -> {
                                b.must(m -> m.multiMatch(mm -> mm
                                        .query(question)
                                        .fields(SEARCH_FIELDS)));
                                if (price != null) {
                                    log.info("提取到价格约束: {}~{}", price.min, price.max);
                                    if (price.min != null && price.max != null) {
                                        b.filter(f -> f.range(r -> r
                                                .field("listPrice")
                                                .gte(JsonData.of(price.min))
                                                .lte(JsonData.of(price.max))));
                                    } else if (price.min != null) {
                                        b.filter(f -> f.range(r -> r
                                                .field("listPrice")
                                                .gte(JsonData.of(price.min))));
                                    } else {
                                        b.filter(f -> f.range(r -> r
                                                .field("listPrice")
                                                .lte(JsonData.of(price.max))));
                                    }
                                }
                                return b;
                            })),
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(s -> s != null)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("ES 全文检索失败", e);
            return List.of();
        }
    }

    /** 从用户问题中提取价格范围 */
    private PriceRange extractPriceRange(String question) {
        // "降低X/减少X" 等是相对调整，不是绝对价格，跳过
        if (Pattern.compile("(降低|减少|下调|砍|降|减)\\s*\\d").matcher(question).find()) {
            log.info("检测到相对价格调整表述，跳过价格提取");
            return null;
        }

        // "X元以上" / "超过X" / "高于X" / "X起" / "X+"
        Matcher above = Pattern.compile("(\\d{4,5})\\s*元?\\s*(以上|及以上|往上|起)").matcher(question);
        if (above.find()) {
            return new PriceRange(Double.parseDouble(above.group(1)), null);
        }

        // "X元以内" / "低于X" / "不超过X" / "X以下"
        Matcher below = Pattern.compile("(\\d{4,5})\\s*元?\\s*(以内|以下|之内)").matcher(question);
        if (below.find()) {
            return new PriceRange(null, Double.parseDouble(below.group(1)));
        }

        // "X元左右" / "X上下" / "X附近" → 下限偏宽松，上限放宽 30% 兜住稍高价商品
        Matcher around = Pattern.compile("(\\d{4,5})\\s*元?\\s*(左右|上下|附近)").matcher(question);
        if (around.find()) {
            double center = Double.parseDouble(around.group(1));
            return new PriceRange(center * 0.75, center * 1.3);
        }

        // "X到Y" / "X-Y" / "X~Y"
        Matcher range = Pattern.compile("(\\d{4,5})\\s*[-~到至]\\s*(\\d{4,5})").matcher(question);
        if (range.find()) {
            return new PriceRange(
                    Double.parseDouble(range.group(1)),
                    Double.parseDouble(range.group(2)));
        }

        // 兜底："X元" 或 "X块钱" 没有修饰词 → 默认按"左右"处理，上限放宽
        Matcher bare = Pattern.compile("(\\d{4,5})\\s*(?:元|块|块钱)").matcher(question);
        if (bare.find()) {
            double center = Double.parseDouble(bare.group(1));
            return new PriceRange(center * 0.7, center * 1.35);
        }

        return null;
    }

    private record PriceRange(Double min, Double max) {}

    // ========== ES 向量语义检索（预留扩展） ==========

    /**
     * ES 向量检索（余弦相似度）
     * 预留扩展点：接入 Embedding API 后将 switching 打开即可切到此模式
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> vectorSearch(float[] queryVector, int topK) {
        try {
            List<Float> vectorList = new ArrayList<>(queryVector.length);
            for (float v : queryVector) {
                vectorList.add(v);
            }

            SearchResponse<Map<String, Object>> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(topK)
                            .query(q -> q
                                    .scriptScore(ss -> ss
                                            .query(qq -> qq.matchAll(m -> m))
                                            .script(sc -> sc
                                                    .inline(inline -> inline
                                                            .source("cosineSimilarity(params.queryVector, 'semanticVector') + 1.0")
                                                            .params(Map.of("queryVector", JsonData.of(vectorList)))
                                                    )
                                            )
                                    )
                            ),
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(s -> s != null)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("ES 向量检索失败，降级到全文检索", e);
            return List.of();
        }
    }

    /**
     * 构建检索上下文文本
     */
    String buildContext(List<Map<String, Object>> hits) {
        if (hits.isEmpty()) {
            return "未检索到相关商品信息。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("以下是数据库中相关的商品信息：\n\n");

        for (int i = 0; i < hits.size(); i++) {
            Map<String, Object> doc = hits.get(i);
            sb.append("【商品").append(i + 1).append("】\n");
            sb.append("名称: ").append(nullSafe(doc.get("name"))).append("\n");
            sb.append("标题: ").append(nullSafe(doc.get("title"))).append("\n");
            sb.append("描述: ").append(nullSafe(doc.get("description"))).append("\n");
            sb.append("品牌: ").append(nullSafe(doc.get("brandName"))).append("\n");
            sb.append("分类: ").append(nullSafe(doc.get("categoryName"))).append("\n");
            sb.append("价格: ").append(nullSafe(doc.get("listPrice"))).append(" 元\n");
            sb.append("销量: ").append(nullSafe(doc.get("sales"))).append("\n");
            sb.append("标签: ").append(nullSafe(doc.get("tags"))).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 构建相关商品列表
     */
    List<RelatedProductVO> buildRelatedProducts(List<Map<String, Object>> hits) {
        List<RelatedProductVO> list = new ArrayList<>();

        for (Map<String, Object> doc : hits) {
            RelatedProductVO vo = new RelatedProductVO();

            Object spuId = doc.get("spuId");
            if (spuId instanceof Number n) {
                vo.setSpuId(n.longValue());
            }

            vo.setName(nullSafe(doc.get("name")));
            vo.setTitle(nullSafe(doc.get("title")));
            vo.setBrandName(nullSafe(doc.get("brandName")));
            vo.setCategoryName(nullSafe(doc.get("categoryName")));
            vo.setPicture(extractFirstPicture(doc.get("pictures")));
            vo.setTags(nullSafe(doc.get("tags")));

            Object price = doc.get("listPrice");
            if (price instanceof Number n) {
                vo.setListPrice(BigDecimal.valueOf(n.doubleValue()));
            }

            Object sales = doc.get("sales");
            if (sales instanceof Number n) {
                vo.setSales(n.intValue());
            }

            // 提取搜索分数
            //（分数在 Hit 对象的 score 中，但这里只取 source，后面可以通过其他方式获取）

            list.add(vo);
        }

        return list;
    }

    /**
     * AI 生成回答
     */
    private String generateAnswer(String question, String context,
                                   List<RelatedProductVO> relatedProducts) {
        String productNames = relatedProducts.stream()
                .map(p -> "  - " + p.getName())
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                你是CoolShark电商平台的智能导购助手。你的任务是根据提供的商品信息，
                准确回答用户的问题。

                规则：
                1. 只基于提供的商品信息回答，不要编造不存在的商品或信息
                2. 先推荐预算范围内的商品；如果检索结果中有价格稍高于预算（几十到几百元）
                   的商品，也要列出并提示"加预算可升级到XX"，帮用户发现更好的选择
                3. 不要说"没有适配商品"——商品就在列表中，用户可以看到
                4. 回答要简洁清晰，推荐时要说明理由
                5. 可以给出购买建议和注意事项
                """;

        String userPrompt = """
                用户问题：%s

                以下是为您检索到的相关商品：
                %s

                完整的商品信息：
                %s

                请根据以上信息回答用户的问题。
                """.formatted(question, productNames, context);

        try {
            return aiClient.chat(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.error("AI 生成回答失败", e);
            return "很抱歉，AI 服务暂时不可用，请稍后重试。";
        }
    }

    private String nullSafe(Object o) {
        return o == null ? "" : o.toString();
    }

    /**
     * 从 ES 中取出的 pictures 字段（JSON 数组字符串，相对路径）
     * 提取第一张图片并拼接完整的访问 URL
     */
    public String extractFirstPicture(Object picturesObj) {
        if (picturesObj == null) {
            return "";
        }
        String pictures = picturesObj.toString();
        if (pictures.isBlank()) {
            return "";
        }
        try {
            JSONArray arr = JSONArray.parseArray(pictures);
            if (arr.isEmpty()) {
                return "";
            }
            String first = arr.getString(0);
            if (first == null || first.isBlank()) {
                return "";
            }
            // 相对路径拼接 host 前缀
            if (first.startsWith("http://") || first.startsWith("https://")) {
                return first;
            }
            return resourceHost + first;
        } catch (Exception e) {
            return pictures;
        }
    }
}
