package com.cooxiao.mall.ai.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.cooxiao.mall.ai.client.EmbeddingClient;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 商品向量数据同步服务
 * 将 SPU 数据从数据库同步到 ES（含语义向量）
 */
@Slf4j
@Service
public class VectorSyncServiceImpl {

    private static final String INDEX_NAME = "cool_shark_mall_ai";
    private static final int BATCH_SIZE = 20;
    /** 末尾清理单次扫描上限（商品量小够用；⚠️ 商品数超过它时差集清理会漏，见 TODO #63 关联评估） */
    private static final int MAX_CLEANUP_SCAN = 1000;
    /** 全量分页的防御上限（200 × 500 = 10 万商品，远超本项目量级；防 provider 返回异常 totalPage 时无限翻页） */
    private static final int MAX_PAGES = 200;

    @DubboReference
    private IForFrontSpuService spuService;

    /** 依赖的是**接口**（依赖倒置，P1 2026-09-11） */
    @Autowired
    private EmbeddingClient embeddingClient;

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
        int vectorDegraded = 0;   // 向量化失败、降级为"仅全文"的条数（2026-09-11 新增）

        for (int start = 0; start < allSpus.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, allSpus.size());
            List<Spu> batch = allSpus.subList(start, end);

            try {
                // 1. 生成语义文本
                List<String> texts = batch.stream().map(this::buildSemanticText).toList();

                // 2. 批量调用 embedding API（仅在启用向量检索时）
                //    ⚠️ 2026-09-11 降级修复：embedding 失败**不再整批跳过**，而是退化为"仅全文索引" ——
                //       商品照样能被 BM25 搜到，只是暂不参与语义召回（外部依赖故障不放大成"搜不到"）
                List<float[]> vectors = null;
                boolean embeddingFailed = false;
                if (aiProperties.isEmbeddingEnabled()) {
                    try {
                        vectors = embeddingClient.embedBatch(texts);
                    } catch (Exception ee) {
                        embeddingFailed = true;
                        vectorDegraded += batch.size();
                        log.warn("第 {}~{} 批向量化失败，降级为仅全文索引（BM25 检索不受影响）: {}",
                                start, end, ee.toString());
                    }
                }

                // 3. 构建 bulk 操作并写入 ES
                List<BulkOperation> operations = new ArrayList<>();
                for (int i = 0; i < batch.size(); i++) {
                    Spu spu = batch.get(i);
                    // 🛡️ 越界保护（2026-09-11 复核补）：外部接口**不保证**"返回向量条数 == 入参条数"
                    //    （少返、部分失败都可能）→ 取不到就按"仅全文"处理，绝不用 get(i) 冒 IndexOutOfBounds
                    float[] vector = null;
                    if (!embeddingFailed && vectors != null) {
                        if (i < vectors.size()) {
                            vector = vectors.get(i);
                        } else {
                            vectorDegraded++;   // 少返的那几条也计入降级
                        }
                    }
                    Map<String, Object> doc = buildDoc(spu, texts.get(i), vector);
                    operations.add(BulkOperation.of(b -> b
                            .index(idx -> idx.index(INDEX_NAME).id(String.valueOf(spu.getId())).document(doc))
                    ));
                }

                bulkUpsert(operations);
                synced += batch.size();
                log.info("同步进度: {}/{}", synced, allSpus.size());

            } catch (Exception e) {
                log.error("批量同步失败，起始索引: {}", start, e);
            }
        }

        // ⚠️ 2026-09-11：汇总里**显式暴露降级条数** —— 否则"商品同步完成"这句话会掩盖外部依赖故障
        if (vectorDegraded > 0) {
            log.warn("商品同步完成，共 {} 条（其中 {} 条向量化失败，仅写入全文索引；这些商品暂不参与语义检索）",
                    synced, vectorDegraded);
        } else {
            log.info("商品同步完成，共 {} 条", synced);
        }
        // 4. 末尾清理：删除索引中 DB 已失效的文档（TODO #33：只 upsert 不 delete 的残留修正）
        int cleaned = cleanupInvalidDocs(allSpus);
        if (cleaned > 0) {
            log.warn("全量同步末尾清理：从 ES 删除 {} 个已失效商品文档", cleaned);
        }
        return synced;
    }

    /**
     * ES bulk 写入 —— <b>单测接缝</b>（2026-09-11 复核新增）。
     *
     * <p>把这一行抽成方法只是为了让同包测试能重写它（{@code ElasticsearchClient} 是具体类、无法伪造），
     * <b>行为与原来完全一致</b>（仍在调用方的 try 内，异常语义不变）。
     */
    void bulkUpsert(List<BulkOperation> operations) throws Exception {
        esClient.bulk(b -> b.operations(operations));
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
        // ⚠️ 2026-09-11 降级修复：向量化失败**不阻断**本次同步 —— 仍写入全文索引（BM25 可搜），
        //    只是该商品暂不参与语义召回；否则一次 embedding 抖动会让商品更新完全进不了索引
        float[] vector = null;
        if (aiProperties.isEmbeddingEnabled()) {
            try {
                vector = embeddingClient.embed(semanticText);
            } catch (Exception e) {
                log.warn("SPU {} 向量化失败，降级为仅全文索引: {}", spuId, e.toString());
            }
        }
        Map<String, Object> doc = buildDoc(spu, semanticText, vector);

        try {
            indexDoc(String.valueOf(spuId), doc);
            log.info("SPU {} 同步完成", spuId);
        } catch (Exception e) {
            // ⚠️ 容错：单条写入失败（含"向量维度不对"被 ES 拒绝）**只记 error、不向上抛** ——
            //    否则一次 embedding/ES 抖动会让整个商品更新链路失败。维度级根因由 EmbeddingSelfCheck 启动自检前置拦截。
            log.error("SPU {} 同步失败", spuId, e);
        }
    }

    /** ES 单条写入 —— <b>单测接缝</b>（便于伪造"写入失败"，行为与原实现一致） */
    void indexDoc(String id, Map<String, Object> doc) throws Exception {
        esClient.index(i -> i
                .index(INDEX_NAME)
                .id(id)
                .document(doc));
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
     * （兜底：即使某次增量同步漏了 delete，全量同步也能把残留洗掉）。
     *
     * <p><b>包级可见（不是 private）</b>：单测接缝。
     * <p>2026-09-11 复核重构：把"拉取索引 id 列表"抽成 {@link #listIndexedDocIds()} 后，
     * 这里只剩**纯逻辑**（差集 / 非数字 id 跳过 / 异常兜底），可被单测完整覆盖 —— <b>行为不变</b>。
     */
    int cleanupInvalidDocs(List<Spu> validSpus) {
        try {
            Set<String> validIds = new HashSet<>();
            for (Spu spu : validSpus) {
                validIds.add(String.valueOf(spu.getId()));
            }

            int deleted = 0;
            for (String docId : listIndexedDocIds()) {
                if (docId == null || validIds.contains(docId)) {
                    continue;
                }
                // 索引文档 _id = spuId 字符串；不在本次有效集合中 → 删除
                try {
                    deleteSpu(Long.parseLong(docId));
                    deleted++;
                } catch (NumberFormatException nfe) {
                    log.warn("跳过非数字文档 id: {}", docId);
                }
            }
            return deleted;
        } catch (Exception e) {
            log.warn("全量同步末尾清理失败（不影响主流程）: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 拉取索引里现有的全部文档 id —— <b>单测接缝</b>
     * （{@code ElasticsearchClient} 是具体类、无法伪造，故把这次 ES 调用单独抽出）。
     */
    @SuppressWarnings("unchecked")
    List<String> listIndexedDocIds() throws Exception {
        co.elastic.clients.elasticsearch.core.SearchResponse<Map> resp = esClient.search(s -> s
                        .index(INDEX_NAME)
                        .size(MAX_CLEANUP_SCAN)
                        .query(q -> q.matchAll(m -> m))
                        .source(sr -> sr.filter(f -> f.includes("spuId"))),
                Map.class);

        List<String> ids = new ArrayList<>();
        for (co.elastic.clients.elasticsearch.core.search.Hit<Map> hit : resp.hits().hits()) {
            ids.add(hit.id());
        }
        return ids;
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

    /**
     * 拉取全量"有效"SPU（分页；有效性由 product 侧 {@code getSpuByPage} 过滤）。
     *
     * <p><b>包级可见（不是 private）</b>：单测接缝，便于测试直接喂固定的 SPU 列表、不碰 Dubbo。
     *
     * <h3>🛡️ 2026-09-11 复核加固（原来有两处会炸）</h3>
     * <ol>
     *   <li><b>{@code getList()} 为 null → NPE</b>：原来直接 {@code all.addAll(spuPage.getList())}；</li>
     *   <li><b>{@code getTotalPage()} 为 null → NPE</b>：它声明是 {@code Integer}（**可空**），
     *       原来直接 {@code totalPage <= page} 自动拆箱会 NPE；</li>
     *   <li><b>异常 totalPage 导致翻空页</b>：现在以"**空页 = 到底**"为<b>主终止条件</b>（不依赖 totalPage 可信），
     *       并加 {@link #MAX_PAGES} 兜底上限 + 触顶告警（不再可能无限循环）。</li>
     * </ol>
     */
    List<Spu> getAllSpus() {
        List<Spu> all = new ArrayList<>();
        int page = 1;
        int pageSize = 500;

        do {
            JsonPage<Spu> spuPage = spuService.getSpuByPage(page, pageSize);
            List<Spu> list = (spuPage == null) ? null : spuPage.getList();
            if (list == null || list.isEmpty()) {
                break;                      // 空页即到底（同时天然防住 NPE）
            }
            all.addAll(list);

            Integer totalPage = spuPage.getTotalPage();
            if (totalPage == null || totalPage <= page) {
                break;                      // totalPage 不可信/已到末页
            }
            page++;
        } while (page <= MAX_PAGES);

        if (page > MAX_PAGES) {
            log.warn("getAllSpus 触达分页防御上限 {} 页（已取 {} 条）—— 疑似 provider 返回的 totalPage 异常，"
                    + "请核对该接口", MAX_PAGES, all.size());
        }

        return all;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
