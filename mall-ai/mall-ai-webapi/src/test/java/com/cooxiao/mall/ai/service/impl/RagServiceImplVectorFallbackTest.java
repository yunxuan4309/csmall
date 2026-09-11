package com.cooxiao.mall.ai.service.impl;

import com.cooxiao.mall.ai.client.EmbeddingClient;
import com.cooxiao.mall.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code RagServiceImpl.vectorSearchWithFallback()} 的降级分支单测（2026-09-11 补）。
 *
 * <h3>为什么当初没测、现在怎么测</h3>
 * 原方法 {@code private} + 直接依赖 {@code ElasticsearchClient}（具体类，难以伪造），
 * 属"想测就得先动生产代码"的情形。处理方式是把
 * {@code vectorSearchWithFallback} / {@code vectorSearch} 降为<b>包级可见</b>（test seam），
 * 于是同包测试可以<b>只重写两个检索入口</b>，而
 * <b>embedding 调用、三条降级判断、日志全部仍走真实代码</b> ——
 * 比"把整个类 mock 掉"更能测到真实行为。
 *
 * <h3>覆盖矩阵</h3>
 * <table border="1">
 *   <tr><th>用例</th><th>模拟的故障</th><th>期望</th></tr>
 *   <tr><td>embeddingFailure_fallsBackToFullText</td><td>embedding 抛错（额度/网络）</td><td>回落全文</td></tr>
 *   <tr><td>vectorSearchFailure_fallsBackToFullText</td><td>ES 向量检索抛错</td><td>回落全文</td></tr>
 *   <tr><td>emptyVectorResult_fallsBackToFullText</td><td>向量检索返回空</td><td>回落全文</td></tr>
 *   <tr><td>vectorHits_areUsedDirectly_withoutFullText</td><td>一切正常</td><td>直接用向量结果，**不再多打一次全文**</td></tr>
 * </table>
 * 最后一条是"反向保护"：防止将来有人改坏成"永远回落全文"（那样语义检索就白开了）。
 */
class RagServiceImplVectorFallbackTest {

    private static final List<Map<String, Object>> TEXT_HITS = List.of(Map.of("src", "fullText"));
    private static final List<Map<String, Object>> VEC_HITS = List.of(Map.of("src", "vector"));

    /** 测试替身：只重写两个检索接缝 */
    static class Probe extends RagServiceImpl {

        boolean fullTextCalled = false;
        private final boolean vectorThrows;
        private final List<Map<String, Object>> vectorResult;

        Probe(EmbeddingClient client, boolean vectorThrows, List<Map<String, Object>> vectorResult) {
            this.vectorThrows = vectorThrows;
            this.vectorResult = vectorResult;
            inject(this, "embeddingClient", client);
            AiProperties props = new AiProperties();
            props.setEmbeddingEnabled(true);
            props.setEmbeddingModel("fake-model");
            inject(this, "aiProperties", props);
        }

        @Override
        List<Map<String, Object>> fullTextSearch(String question, int topK) {
            fullTextCalled = true;
            return TEXT_HITS;
        }

        @Override
        List<Map<String, Object>> vectorSearch(float[] queryVector, int topK) {
            if (vectorThrows) {
                throw new IllegalStateException("ES 向量检索挂了");
            }
            return vectorResult;
        }
    }

    /** 字段声明在父类 {@link RagServiceImpl} 上，故按父类取字段再设到子类实例 */
    private static void inject(Object target, String field, Object value) {
        try {
            Field f = RagServiceImpl.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("注入失败: " + field, e);
        }
    }

    private static EmbeddingClient okEmbedding() {
        return new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                return new float[1024];
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                return List.of(new float[1024]);
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

    /** 边界：接口返回 null 向量 */
    private static EmbeddingClient nullVectorEmbedding() {
        return new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                return null;
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                return List.of();
            }
        };
    }

    /** 边界：接口返回长度 0 的向量 */
    private static EmbeddingClient emptyVectorEmbedding() {
        return new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                return new float[0];
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                return List.of();
            }
        };
    }

    // ---------------------------------------------------------------- 三条降级分支

    @Test
    void embeddingFailure_fallsBackToFullText() {
        Probe p = new Probe(brokenEmbedding(), false, VEC_HITS);
        assertSame(TEXT_HITS, p.vectorSearchWithFallback("手机", 10), "embedding 失败时应返回全文结果");
        assertTrue(p.fullTextCalled, "embedding 失败必须回落全文检索（原来会直接抛到接口）");
    }

    @Test
    void vectorSearchFailure_fallsBackToFullText() {
        Probe p = new Probe(okEmbedding(), true, null);
        assertSame(TEXT_HITS, p.vectorSearchWithFallback("手机", 10), "向量检索失败时应返回全文结果");
        assertTrue(p.fullTextCalled, "向量检索失败必须回落全文检索（原来只是静默返回空）");
    }

    @Test
    void emptyVectorResult_fallsBackToFullText() {
        Probe p = new Probe(okEmbedding(), false, List.of());
        assertSame(TEXT_HITS, p.vectorSearchWithFallback("手机", 10), "向量结果为空时应返回全文结果");
        assertTrue(p.fullTextCalled, "向量结果为空也必须回落全文检索");
    }

    // ---------------------------------------------------------------- 反向保护

    @Test
    void vectorHits_areUsedDirectly_withoutFullText() {
        Probe p = new Probe(okEmbedding(), false, VEC_HITS);
        assertSame(VEC_HITS, p.vectorSearchWithFallback("手机", 10), "向量有结果时应直接返回向量结果");
        assertFalse(p.fullTextCalled, "向量有结果时不应再多打一次全文检索（否则语义检索白开）");
    }

    // ---------------------------------------------------------------- 边界（2026-09-11 复核补）

    @Test
    void nullQueryVector_fallsBackToFullText() {
        Probe p = new Probe(nullVectorEmbedding(), false, VEC_HITS);
        assertSame(TEXT_HITS, p.vectorSearchWithFallback("手机", 10),
                "embedding 返回 null 向量时应回落全文，而不是把坏向量喂给 ES");
        assertTrue(p.fullTextCalled, "null 向量必须回落全文检索");
    }

    @Test
    void emptyQueryVector_fallsBackToFullText() {
        Probe p = new Probe(emptyVectorEmbedding(), false, VEC_HITS);
        assertSame(TEXT_HITS, p.vectorSearchWithFallback("手机", 10),
                "embedding 返回空向量时应回落全文，不必白跑一次 ES 向量检索");
        assertTrue(p.fullTextCalled, "空向量必须回落全文检索");
    }
}
