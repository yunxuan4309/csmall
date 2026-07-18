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
     */
    public int syncAll() {
        List<Spu> allSpus = getAllSpus();

        if (allSpus.isEmpty()) {
            log.warn("没有找到任何商品数据");
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
        return synced;
    }

    /**
     * 同步指定 SPU
     */
    public void syncSpu(Long spuId) {
        SpuStandardVO spu = spuService.getSpuById(spuId);
        if (spu == null) {
            log.warn("SPU {} 不存在", spuId);
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
        if (vector != null) {
            doc.put("semanticVector", vector);
        }
        return doc;
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
