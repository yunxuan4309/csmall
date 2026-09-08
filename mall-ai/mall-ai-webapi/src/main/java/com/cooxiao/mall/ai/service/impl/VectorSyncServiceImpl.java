package com.cooxiao.mall.ai.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.cooxiao.mall.ai.client.SiliconFlowEmbeddingClient;
import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.common.restful.JsonPage;
import com.cooxiao.mall.pojo.product.model.Spu;
import com.cooxiao.mall.pojo.product.vo.SpuStandardVO;
import com.cooxiao.mall.product.service.front.IForFrontSpuService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品向量数据同步服务
 * 将 SPU 数据从数据库同步到 ES（含语义向量）
 */
@Slf4j
@Service
public class VectorSyncServiceImpl {

    private static final String INDEX_NAME = "cool_shark_mall_ai";
    private static final int BATCH_SIZE = 20;

    @DubboReference
    private IForFrontSpuService spuService;

    @Autowired
    private SiliconFlowEmbeddingClient embeddingClient;

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private AiProperties aiProperties;

    /**
     * 全量同步所有商品到 ES
     * 1. 从 DB 拉取全量"有效"SPU（checked=1 && published=1 && deleted=0，由 product 侧 getSpuByPage 过滤）
     * 2. 批量 upsert 到 ES
     * 3. 末尾清理：ES 中已存在、但 DB 已下架/删除/失效的文档（防止残留可被搜到）
     */
    public int syncAll() {
        List<Spu> allSpus = getAllSpus();

        if (allSpus.isEmpty()) {
            // 边界：DB 无有效商品时不清空逻辑也应走——ES 可能残留已失效文档，统一交给 cleanupInvalidDocs 清洗
            log.warn("DB 无有效商品，跳过 upsert，仅执行索引清理");
            int cleaned = cleanupInvalidDocs(allSpus);
            if (cleaned > 0) {
                log.warn("全量同步末尾清理：从 ES 删除 {} 个已失效商品文档", cleaned);
            }
            return 0;
        }

        log.info("开始同步 {} 个商品到 ES...", allSpus.size());
        int synced = 0;

        for (int start = 0; start < allSpus.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, allSpus.size());
            List<Spu> batch = allSpus.subList(start, end);

            try {
                // 1. 生成语义文本
                List<String> texts = batch.stream().map(this::buildSemanticText).toList();

                // 2. 批量调用 embedding API（仅在启用向量检索时）
                List<float[]> vectors = null;
                if (aiProperties.isEmbeddingEnabled()) {
                    vectors = embeddingClient.embedBatch(texts);
                }

                // 3. 构建 bulk 操作并写入 ES
                List<BulkOperation> operations = new ArrayList<>();
                for (int i = 0; i < batch.size(); i++) {
                    Spu spu = batch.get(i);
                    float[] vector = (vectors != null) ? vectors.get(i) : null;
                    Map<String, Object> doc = buildDoc(spu, texts.get(i), vector);
                    operations.add(BulkOperation.of(b -> b
                            .index(idx -> idx.index(INDEX_NAME).id(String.valueOf(spu.getId())).document(doc))
                    ));
                }

                esClient.bulk(b -> b.operations(operations));
                synced += batch.size();
                log.info("同步进度: {}/{}", synced, allSpus.size());

            } catch (Exception e) {
                log.error("批量同步失败，起始索引: {}", start, e);
            }
        }

        log.info("商品同步完成，共 {} 条", synced);
        // 4. 末尾清理：删除索引中 DB 已失效的文档（TODO #33：只 upsert 不 delete 的残留修正）
        int cleaned = cleanupInvalidDocs(allSpus);
        if (cleaned > 0) {
            log.warn("全量同步末尾清理：从 ES 删除 {} 个已失效商品文档", cleaned);
        }
        return synced;
    }

    /**
     * 同步指定 SPU（TODO #33 同步模型补全）
     * 以 DB 业务状态为准：仅 checked=1 && published=1 && deleted=0 的 SPU 写入 ES；
     * 其余（未审核/已下架/已删除/不存在）一律从 ES 删除——保证"索引里能搜到的都是有效在售商品"
     */
    public void syncSpu(Long spuId) {
        if (spuId == null) {
            log.warn("syncSpu 收到空 spuId，忽略");
            return;
        }
        SpuStandardVO spu;
        try {
            spu = spuService.getSpuById(spuId);
        } catch (Exception e) {
            // 仅当"业务确认不存在"（NOT_FOUND）才从 ES 删除兜底；
            // Dubbo 网络超时/序列化等基础设施异常保守放行（不误删正常商品，留待下次同步或全量清洗）
            if (isNotFound(e)) {
                log.warn("SPU {} 业务确认不存在，从 ES 删除兜底: {}", spuId, e.getMessage());
                deleteSpu(spuId);
            } else {
                log.warn("SPU {} 查询异常（疑似基础设施故障），保守放行不删除: {}", spuId, e.getMessage());
            }
            return;
        }
        if (spu == null) {
            log.warn("SPU {} 不存在，从 ES 删除兜底", spuId);
            deleteSpu(spuId);
            return;
        }

        // 状态校验：不满足"已审核 + 已上架 + 未删除"则从 ES 删除（防下架/未审核商品被搜到）
        if (!isSearchable(spu)) {
            log.info("SPU {} 状态不可搜索(checked={}, published={}, deleted={})，从 ES 删除",
                    spuId, spu.getChecked(), spu.getPublished(), spu.getDeleted());
            deleteSpu(spuId);
            return;
        }

        String semanticText = buildSemanticText(spu);
        float[] vector = aiProperties.isEmbeddingEnabled() ? embeddingClient.embed(semanticText) : null;
        Map<String, Object> doc = buildDoc(spu, semanticText, vector);

        try {
            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(String.valueOf(spuId))
                    .document(doc));
            log.info("SPU {} 同步完成", spuId);
        } catch (Exception e) {
            log.error("SPU {} 同步失败", spuId, e);
        }
    }

    /**
     * 从 ES 删除指定 SPU 文档（幂等：不存在也返回成功）
     */
    public void deleteSpu(Long spuId) {
        if (spuId == null) {
            return;
        }
        try {
            esClient.delete(d -> d
                    .index(INDEX_NAME)
                    .id(String.valueOf(spuId)));
            log.info("SPU {} 已从 ES 删除", spuId);
        } catch (Exception e) {
            log.warn("SPU {} 从 ES 删除失败（可能是 404 不存在，幂等无碍）: {}", spuId, e.getMessage());
        }
    }

    // ========== 状态判定与清理 ==========

    private boolean isSearchable(SpuStandardVO spu) {
        return Integer.valueOf(1).equals(spu.getChecked())
                && Integer.valueOf(1).equals(spu.getPublished())
                && !Integer.valueOf(1).equals(spu.getDeleted());
    }

    /**
     * 识别"业务确认不存在"异常（NOT_FOUND），用于区分：SPU 真不存在（可删） vs 基础设施故障（Dubbo 超时等，不删）
     * Dubbo 跨服务异常可能被包装多层（RpcException → cause），需遍历 cause 链
     */
    private boolean isNotFound(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof com.cooxiao.mall.common.exception.CoolSharkServiceException cse
                    && cse.getResponseCode() != null
                    && com.cooxiao.mall.common.restful.ResponseCode.NOT_FOUND == cse.getResponseCode()) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * 清理 ES 中 DB 已失效的文档：拉取 ES 现有全部 id，与本次全量同步的有效 id 集合比对，删差集
     * （兜底：即使某次增量同步漏了 delete，全量同步也能把残留洗掉）
     */
    @SuppressWarnings("unchecked")
    private int cleanupInvalidDocs(List<Spu> validSpus) {
        try {
            java.util.Set<String> validIds = new java.util.HashSet<>();
            for (Spu spu : validSpus) {
                validIds.add(String.valueOf(spu.getId()));
            }

            // 用 scroll/search 拉 ES 现有全部 id（商品量小，单次 size 足够）
            co.elastic.clients.elasticsearch.core.SearchResponse<Map> resp = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .size(1000)
                            .query(q -> q.matchAll(m -> m))
                            .source(sr -> sr.filter(f -> f.includes("spuId"))),
                    Map.class);

            int deleted = 0;
            for (co.elastic.clients.elasticsearch.core.search.Hit<Map> hit : resp.hits().hits()) {
                String docId = hit.id();
                if (docId == null) {
                    continue;
                }
                // 索引文档 _id = spuId 字符串；若不在本次有效集合中则删除
                if (!validIds.contains(docId)) {
                    try {
                        deleteSpu(Long.parseLong(docId));
                        deleted++;
                    } catch (NumberFormatException nfe) {
                        log.warn("跳过非数字文档 id: {}", docId);
                    }
                }
            }
            return deleted;
        } catch (Exception e) {
            log.warn("全量同步末尾清理失败（不影响主流程）: {}", e.getMessage());
            return 0;
        }
    }

    // ========== 辅助方法 ==========

    private String buildSemanticText(Spu spu) {
        return "商品名称：" + nullSafe(spu.getName())
                + " | 标题：" + nullSafe(spu.getTitle())
                + " | 描述：" + nullSafe(spu.getDescription())
                + " | 品牌：" + nullSafe(spu.getBrandName())
                + " | 分类：" + nullSafe(spu.getCategoryName())
                + " | 标签：" + nullSafe(spu.getTags());
    }

    private String buildSemanticText(SpuStandardVO spu) {
        return "商品名称：" + nullSafe(spu.getName())
                + " | 标题：" + nullSafe(spu.getTitle())
                + " | 描述：" + nullSafe(spu.getDescription())
                + " | 品牌：" + nullSafe(spu.getBrandName())
                + " | 分类：" + nullSafe(spu.getCategoryName())
                + " | 标签：" + nullSafe(spu.getTags());
    }

    private Map<String, Object> buildDoc(Spu spu, String semanticText, float[] vector) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("spuId", spu.getId());
        doc.put("name", nullSafe(spu.getName()));
        doc.put("title", nullSafe(spu.getTitle()));
        doc.put("description", nullSafe(spu.getDescription()));
        doc.put("categoryName", nullSafe(spu.getCategoryName()));
        doc.put("brandName", nullSafe(spu.getBrandName()));
        doc.put("listPrice", spu.getListPrice());
        doc.put("pictures", nullSafe(spu.getPictures()));
        doc.put("tags", nullSafe(spu.getTags()));
        doc.put("sales", spu.getSales());
        doc.put("semanticText", nullSafe(semanticText));
        doc.put("suggestField", buildSuggestInputs(spu));
        if (vector != null) {
            doc.put("semanticVector", vector);
        }
        return doc;
    }

    private Map<String, Object> buildDoc(SpuStandardVO spu, String semanticText, float[] vector) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("spuId", spu.getId());
        doc.put("name", nullSafe(spu.getName()));
        doc.put("title", nullSafe(spu.getTitle()));
        doc.put("description", nullSafe(spu.getDescription()));
        doc.put("categoryName", nullSafe(spu.getCategoryName()));
        doc.put("brandName", nullSafe(spu.getBrandName()));
        doc.put("listPrice", spu.getListPrice());
        doc.put("pictures", nullSafe(spu.getPictures()));
        doc.put("tags", nullSafe(spu.getTags()));
        doc.put("sales", spu.getSales());
        doc.put("semanticText", nullSafe(semanticText));
        doc.put("suggestField", buildSuggestInputs(spu));
        if (vector != null) {
            doc.put("semanticVector", vector);
        }
        return doc;
    }

    private List<String> buildSuggestInputs(Spu spu) {
        return buildSuggestInputs(spu.getName(), spu.getBrandName(), spu.getCategoryName(), spu.getTitle());
    }

    private List<String> buildSuggestInputs(SpuStandardVO spu) {
        return buildSuggestInputs(spu.getName(), spu.getBrandName(), spu.getCategoryName(), spu.getTitle());
    }

    private List<String> buildSuggestInputs(String name, String brandName, String categoryName, String title) {
        List<String> inputs = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            inputs.add(name);
            if (brandName != null && !brandName.isBlank()) {
                inputs.add(brandName + " " + name);
            }
            if (categoryName != null && !categoryName.isBlank()) {
                inputs.add(categoryName + " " + name);
            }
        }
        if (title != null && !title.isBlank() && title.length() <= 50) {
            inputs.add(title);
        }
        return inputs;
    }

    private List<Spu> getAllSpus() {
        List<Spu> all = new ArrayList<>();
        int page = 1;
        int pageSize = 500;

        do {
            JsonPage<Spu> spuPage = spuService.getSpuByPage(page, pageSize);
            all.addAll(spuPage.getList());
            if (spuPage.getTotalPage() <= page) break;
            page++;
        } while (true);

        return all;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
