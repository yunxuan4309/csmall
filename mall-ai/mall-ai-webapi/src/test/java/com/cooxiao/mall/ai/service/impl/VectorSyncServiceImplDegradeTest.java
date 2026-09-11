package com.cooxiao.mall.ai.service.impl;

import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.cooxiao.mall.ai.client.EmbeddingClient;
import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.pojo.product.model.Spu;
import com.cooxiao.mall.pojo.product.vo.SpuStandardVO;
import com.cooxiao.mall.product.service.front.IForFrontSpuService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code VectorSyncServiceImpl} 的<b>降级 / 容错 / 边界</b>单测（2026-09-11）。
 *
 * <h3>覆盖三块</h3>
 * <ol>
 *   <li><b>同步侧降级</b>（{@code syncAll}）：embedding 失败时商品**仍写入 ES（仅全文索引）**——
 *       原实现是整批 catch 跳过，这批商品连全文都搜不到（{@code synced=0}）。</li>
 *   <li><b>单条容错</b>（{@code syncSpu}）：写入失败（含"向量维度不对"被 ES 拒绝）**只记 error、不向上抛**；
 *       不可搜索的商品走"从 ES 删除"分支。</li>
 *   <li><b>末尾清理逻辑</b>（{@code cleanupInvalidDocs}）：差集计算、非数字 id / null id 跳过、ES 异常兜底返回 0。</li>
 * </ol>
 *
 * <h3>测试接缝（只放开可见性 / 抽方法，均不改行为）</h3>
 * {@code getAllSpus} · {@code listIndexedDocIds} · {@code cleanupInvalidDocs} · {@code bulkUpsert} · {@code indexDoc}
 * 为包级可见；{@code deleteSpu} 本来就是 {@code public}。Dubbo 用 {@link Proxy} 伪造。
 * 手写 fake、不用 Mockito（本项目沙箱里 Mockito inline MockMaker 加载失败）。
 */
class VectorSyncServiceImplDegradeTest {

    /** 测试替身：只替换 I/O 边界（Dubbo / ES），业务逻辑全走真实代码 */
    static class Probe extends VectorSyncServiceImpl {

        private final List<Spu> spus;

        int bulkCalls = 0;
        int bulkOps = 0;
        int cleanupCalls = 0;

        /** true = syncAll 用例里把清理短路（不碰 ES）；cleanup 用例把它置 false 走真实逻辑 */
        boolean stubCleanup = true;

        /** listIndexedDocIds 的返回值 / 是否抛错 */
        List<String> indexedIds = List.of();
        boolean indexedIdsThrows = false;

        /** 记账 */
        final List<Long> deletedSpuIds = new ArrayList<>();
        final List<Map<String, Object>> indexedDocs = new ArrayList<>();
        boolean indexDocThrows = false;

        Probe(List<Spu> spus, EmbeddingClient client) {
            this.spus = spus;
            inject(this, "embeddingClient", client);
            AiProperties props = new AiProperties();
            props.setEmbeddingEnabled(client != null);
            props.setEmbeddingModel("fake-model");
            inject(this, "aiProperties", props);
        }

        @Override
        List<Spu> getAllSpus() {
            return spus;
        }

        @Override
        void bulkUpsert(List<BulkOperation> operations) {
            bulkCalls++;
            bulkOps += operations.size();
        }

        @Override
        int cleanupInvalidDocs(List<Spu> validSpus) {
            cleanupCalls++;
            return stubCleanup ? 0 : super.cleanupInvalidDocs(validSpus);
        }

        @Override
        List<String> listIndexedDocIds() throws Exception {
            if (indexedIdsThrows) {
                throw new IllegalStateException("ES 挂了");
            }
            return indexedIds;
        }

        @Override
        public void deleteSpu(Long spuId) {
            deletedSpuIds.add(spuId);
        }

        @Override
        void indexDoc(String id, Map<String, Object> doc) throws Exception {
            if (indexDocThrows) {
                throw new IllegalArgumentException("dense_vector dims mismatch");
            }
            indexedDocs.add(doc);
        }
    }

    // ---------------------------------------------------------------- 基础设施

    /** 字段都声明在父类 {@link VectorSyncServiceImpl} 上 */
    private static void inject(Object target, String field, Object value) {
        try {
            Field f = VectorSyncServiceImpl.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("注入失败: " + field, e);
        }
    }

    /** 用 JDK 动态代理伪造 Dubbo 接口（不引入任何 mock 框架） */
    private static IForFrontSpuService proxySpuService(SpuStandardVO vo) {
        return (IForFrontSpuService) Proxy.newProxyInstance(
                IForFrontSpuService.class.getClassLoader(),
                new Class<?>[]{IForFrontSpuService.class},
                (proxy, method, args) -> {
                    if ("getSpuById".equals(method.getName())) {
                        return vo;
                    }
                    return null;      // 其余方法（getSpuByPage 等）本测试不会调用
                });
    }

    private static Spu spu(long id) {
        Spu s = new Spu();
        s.setId(id);
        s.setName("测试商品" + id);
        s.setTitle("测试标题" + id);
        s.setDescription("测试描述");
        s.setBrandName("测试品牌");
        s.setCategoryName("测试分类");
        s.setPictures("[\"p.jpg\"]");
        s.setTags("测试");
        s.setListPrice(new BigDecimal("1999.00"));
        s.setSales(0);
        return s;
    }

    private static SpuStandardVO spuVo(long id, int published) {
        SpuStandardVO vo = new SpuStandardVO();
        vo.setId(id);
        vo.setName("测试商品" + id);
        vo.setTitle("测试标题" + id);
        vo.setDescription("测试描述");
        vo.setBrandName("测试品牌");
        vo.setCategoryName("测试分类");
        vo.setPictures("[\"p.jpg\"]");
        vo.setTags("测试");
        vo.setChecked(1);
        vo.setPublished(published);
        vo.setDeleted(0);
        return vo;
    }

    private static EmbeddingClient okEmbedding(int dims, int returnedCount) {
        return new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                return new float[dims];
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                List<float[]> out = new ArrayList<>();
                for (int i = 0; i < Math.min(returnedCount, texts.size()); i++) {
                    out.add(new float[dims]);
                }
                return out;      // ← 刻意允许"少返"，模拟外部接口行为
            }
        };
    }

    private static EmbeddingClient brokenEmbedding() {
        return new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                throw new IllegalStateException("402 Sorry, your account balance is insufficient");
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                throw new IllegalStateException("402 余额不足");
            }
        };
    }

    private static EmbeddingClient mustNotBeCalledEmbedding() {
        return new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                throw new AssertionError("embedding 关闭时不应调用外部接口");
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                throw new AssertionError("embedding 关闭时不应调用外部接口");
            }
        };
    }

    // ================================================================ ① syncAll 降级

    @Test
    void embeddingFailure_stillWritesAllDocsAsFullTextOnly() {
        // ⭐ 本条降级的核心：以前 embedding 一挂，这批商品**一条都进不了 ES**
        Probe p = new Probe(List.of(spu(1), spu(2), spu(3)), brokenEmbedding());
        int synced = p.syncAll();

        assertEquals(3, synced, "向量化失败也必须把 3 条商品写进 ES（仅全文索引）");
        assertEquals(1, p.bulkCalls, "应仍执行一次 bulk 写入");
        assertEquals(3, p.bulkOps, "bulk 里应有 3 个文档操作");
        assertEquals(1, p.cleanupCalls, "末尾清理仍应执行");
    }

    @Test
    void embeddingSuccess_writesAllDocs() {
        Probe p = new Probe(List.of(spu(1), spu(2)), okEmbedding(1024, 2));
        assertEquals(2, p.syncAll(), "正常路径应写入全部文档");
        assertEquals(2, p.bulkOps);
    }

    @Test
    void shortVectorResponse_doesNotThrowAndStillWritesAll() {
        // 🛡️ 越界保护：入参 3 条、接口只回 1 条向量 → 不能 IndexOutOfBounds，且仍写入 3 条
        Probe p = new Probe(List.of(spu(1), spu(2), spu(3)), okEmbedding(1024, 1));
        int synced = p.syncAll();

        assertEquals(3, synced, "少返向量时仍应写入全部文档（缺向量的按仅全文处理）");
        assertEquals(3, p.bulkOps);
    }

    @Test
    void emptyVectorResponse_doesNotThrowAndStillWritesAll() {
        Probe p = new Probe(List.of(spu(1), spu(2)), okEmbedding(1024, 0));
        assertEquals(2, p.syncAll(), "一条向量都没回时，仍应全部按仅全文写入");
    }

    @Test
    void embeddingDisabled_writesAllDocsWithoutCallingApi() {
        Probe p = new Probe(List.of(spu(1)), mustNotBeCalledEmbedding());
        AiProperties off = new AiProperties();
        off.setEmbeddingEnabled(false);
        off.setEmbeddingModel("fake-model");
        inject(p, "aiProperties", off);

        assertEquals(1, p.syncAll(), "关闭向量检索时应写入全部文档");
        assertEquals(1, p.bulkOps);
    }

    @Test
    void emptySpuList_returnsZeroAndDoesNotBulkWrite() {
        // 边界：DB 无有效商品 → 不应发起 bulk（只做清理）
        Probe p = new Probe(List.of(), okEmbedding(1024, 0));
        assertEquals(0, p.syncAll(), "无有效商品时应返回 0");
        assertEquals(0, p.bulkCalls, "无有效商品时不应发起 bulk 写入");
        assertEquals(1, p.cleanupCalls, "无有效商品时仍应执行末尾清理（防 ES 残留）");
    }

    // ================================================================ ② syncSpu 单条容错

    @Test
    void syncSpu_indexFailure_doesNotThrow() {
        // 模拟"向量维度不对 → ES 拒绝写入"：只记 error，不能把异常抛给调用方（否则商品更新链路整体失败）
        Probe p = new Probe(List.of(), okEmbedding(768, 1));
        inject(p, "spuService", proxySpuService(spuVo(1, 1)));
        p.indexDocThrows = true;

        assertDoesNotThrow(() -> p.syncSpu(1L), "单条写入失败不应向上抛异常");
        assertTrue(p.indexedDocs.isEmpty());
    }

    @Test
    void syncSpu_searchable_writesDoc() {
        Probe p = new Probe(List.of(), okEmbedding(1024, 1));
        inject(p, "spuService", proxySpuService(spuVo(1, 1)));

        p.syncSpu(1L);

        assertEquals(1, p.indexedDocs.size(), "可搜索商品应写入一个文档");
        assertEquals(1L, p.indexedDocs.get(0).get("spuId"));
    }

    @Test
    void syncSpu_notSearchable_deletesFromEs() {
        // published=0 → 不可搜索 → 应从 ES 删除（防下架商品被搜到），且不写入
        Probe p = new Probe(List.of(), okEmbedding(1024, 1));
        inject(p, "spuService", proxySpuService(spuVo(9, 0)));

        p.syncSpu(9L);

        assertEquals(List.of(9L), p.deletedSpuIds, "不可搜索商品应从 ES 删除");
        assertTrue(p.indexedDocs.isEmpty(), "不可搜索商品不应写入 ES");
    }

    // ================================================================ ③ cleanupInvalidDocs 逻辑

    @Test
    void cleanup_deletesOnlyStaleIds() {
        Probe p = new Probe(List.of(), okEmbedding(1024, 0));
        p.stubCleanup = false;
        p.indexedIds = List.of("1", "2", "999");

        assertEquals(1, p.cleanupInvalidDocs(List.of(spu(1), spu(2))), "只应删掉不在有效集合里的 999");
        assertEquals(List.of(999L), p.deletedSpuIds);
    }

    @Test
    void cleanup_noStaleIds_returnsZero() {
        Probe p = new Probe(List.of(), okEmbedding(1024, 0));
        p.stubCleanup = false;
        p.indexedIds = List.of("1", "2");

        assertEquals(0, p.cleanupInvalidDocs(List.of(spu(1), spu(2))));
        assertTrue(p.deletedSpuIds.isEmpty());
    }

    @Test
    void cleanup_skipsNonNumericDocIds() {
        Probe p = new Probe(List.of(), okEmbedding(1024, 0));
        p.stubCleanup = false;
        p.indexedIds = List.of("abc", "7");

        assertEquals(1, p.cleanupInvalidDocs(List.of(spu(1))), "非数字 id 跳过、只删真正的 stale id 7");
        assertEquals(List.of(7L), p.deletedSpuIds);
    }

    @Test
    void cleanup_nullDocId_skipped() {
        Probe p = new Probe(List.of(), okEmbedding(1024, 0));
        p.stubCleanup = false;
        p.indexedIds = Arrays.asList("1", null);   // List.of 不允许 null，用 Arrays.asList

        assertEquals(0, p.cleanupInvalidDocs(List.of(spu(1))));
        assertTrue(p.deletedSpuIds.isEmpty());
    }

    @Test
    void cleanup_esFailure_returnsZeroAndDoesNotThrow() {
        Probe p = new Probe(List.of(), okEmbedding(1024, 0));
        p.stubCleanup = false;
        p.indexedIdsThrows = true;

        assertDoesNotThrow(() -> assertEquals(0, p.cleanupInvalidDocs(List.of(spu(1)))),
                "ES 拉取失败时清理应兜底返回 0，不影响主流程");
    }
}
