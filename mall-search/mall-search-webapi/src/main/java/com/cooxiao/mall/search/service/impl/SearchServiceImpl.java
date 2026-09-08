package com.cooxiao.mall.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.alibaba.fastjson.JSONArray;
import com.cooxiao.mall.pojo.ai.vo.RelatedProductVO;
import com.cooxiao.mall.pojo.ai.vo.SearchResultVO;
import com.cooxiao.mall.search.service.ISearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 普通搜索服务实现（TODO #33 方案 A2 改造后）
 *
 * <p>从"自建 index2 + 手动全量同步"改为 <b>只读统一索引</b>：
 * <ul>
 *   <li>查询 mall-ai 维护的单一索引 cool_shark_mall_ai（与 AI 搜索同一份数据，天然零漂移）</li>
 *   <li>纯 ES 普通关键词召回（multi_match），<b>无 AI 意图解析、无语义重排、零 LLM 调用</b>——毫秒级</li>
 *   <li>定位：进程级降级通道 —— mall-ai（AI 搜索）整体不可用时，前端 fallback 到本服务仍可搜索</li>
 * </ul>
 */
@Slf4j
@Service
public class SearchServiceImpl implements ISearchService {

    private static final String INDEX_NAME = "cool_shark_mall_ai";

    /** 默认/上限分页 */
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 普通搜索召回字段（与 mall-ai esKeywordSearch 对齐，但<b>不加 semanticText</b>：
     * semanticText 是拼接的语义文本，普通关键词搜索应基于商品真实字段即可）
     * 权重：名称 > 标题 > 描述 > 品牌 > 分类 > 标签
     */
    private static final List<String> SEARCH_FIELDS = List.of(
            "name^5", "title^4", "description^2",
            "brandName", "categoryName", "tags");

    @Autowired
    private ElasticsearchClient esClient;

    @Value("${custom.file-upload.resource-host:}")
    private String resourceHost;

    @Override
    public SearchResultVO search(String keyword, Integer page, Integer pageSize) {
        // ========== 边界归一化 ==========
        // keyword 空白：不执行 matchAll 全量返回（普通搜索必须有词；无词浏览走前端分类接口，不经 ES）
        String kw = keyword == null ? "" : keyword.trim();
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE
                : Math.min(pageSize, MAX_PAGE_SIZE);

        SearchResultVO result = new SearchResultVO();
        result.setProducts(new ArrayList<>());
        result.setTotalCount(0L);

        if (kw.isEmpty()) {
            log.debug("普通搜索关键词为空，返回空结果");
            return result;
        }

        // ========== ES 普通召回 ==========
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .query(q -> q.multiMatch(mm -> mm
                                    .query(kw)
                                    .fields(SEARCH_FIELDS)))
                            .from((p - 1) * size)
                            .size(size),
                    Map.class);

            long total = response.hits() == null || response.hits().total() == null
                    ? 0 : response.hits().total().value();

            List<RelatedProductVO> products = new ArrayList<>();
            if (response.hits() != null && response.hits().hits() != null) {
                for (Hit<Map> hit : response.hits().hits()) {
                    RelatedProductVO vo = mapToVO(hit);
                    if (vo != null) {
                        products.add(vo);
                    }
                }
            }

            result.setProducts(products);
            result.setTotalCount(total);
            result.setAiExplanation(null); // 普通搜索无 AI 解释
            log.info("普通搜索 keyword={} 命中 {} 条，返回 {} 条", kw, total, products.size());
            return result;
        } catch (Exception e) {
            // 兜底：ES 故障时普通搜索返回空结果而非 500（前端有 AI 主链路，此处是降级通道，空结果可接受）
            log.error("普通搜索失败，keyword={}, page={}, size={}", kw, p, size, e);
            return result;
        }
    }

    // ========== 辅助方法 ==========

    @SuppressWarnings("unchecked")
    private RelatedProductVO mapToVO(Hit<Map> hit) {
        if (hit == null || hit.source() == null) {
            return null;
        }
        Map<String, Object> source = hit.source();
        RelatedProductVO vo = new RelatedProductVO();

        Object spuId = source.get("spuId");
        if (spuId instanceof Number n) {
            vo.setSpuId(n.longValue());
        }
        if (vo.getSpuId() == null) {
            // 兜底：某些历史文档可能只有 _id（= spuId）
            try {
                vo.setSpuId(Long.parseLong(hit.id()));
            } catch (NumberFormatException ignored) {
                log.warn("普通搜索命中文档缺少 spuId 且 _id 非数字: {}", hit.id());
                return null;
            }
        }

        vo.setName(nullSafe(source.get("name")));
        vo.setTitle(nullSafe(source.get("title")));
        vo.setBrandName(nullSafe(source.get("brandName")));
        vo.setCategoryName(nullSafe(source.get("categoryName")));
        vo.setTags(nullSafe(source.get("tags")));
        vo.setPicture(extractFirstPicture(source.get("pictures")));

        Object price = source.get("listPrice");
        if (price instanceof Number n) {
            vo.setListPrice(BigDecimal.valueOf(n.doubleValue()));
        } else if (price != null) {
            try {
                vo.setListPrice(new BigDecimal(price.toString()));
            } catch (NumberFormatException e) {
                log.warn("普通搜索命中文档 listPrice 解析失败: {}", price);
            }
        }

        Object sales = source.get("sales");
        if (sales instanceof Number n) {
            vo.setSales(n.intValue());
        }

        // 搜索分数（ES 相关性得分）
        if (hit.score() != null) {
            vo.setScore(hit.score());
        }
        return vo;
    }

    /**
     * 从 pictures 字段（JSON 数组字符串，存相对路径）提取首图并拼接完整 URL
     * 已含 http(s):// 前缀的路径直接返回（兼容历史数据）
     */
    private String extractFirstPicture(Object picturesObj) {
        if (picturesObj == null) {
            return "";
        }
        String pictures = picturesObj.toString();
        if (pictures.isBlank() || "[]".equals(pictures)) {
            return "";
        }
        try {
            JSONArray arr = JSONArray.parseArray(pictures);
            if (arr == null || arr.isEmpty()) {
                return "";
            }
            String first = arr.getString(0);
            if (first == null || first.isBlank()) {
                return "";
            }
            if (first.startsWith("http://") || first.startsWith("https://")) {
                return first;
            }
            String host = resourceHost == null ? "" : resourceHost;
            return host + first;
        } catch (Exception e) {
            log.warn("普通搜索 pictures 解析失败，原样返回: {}", pictures);
            return pictures;
        }
    }

    private String nullSafe(Object o) {
        return o == null ? "" : o.toString();
    }
}
