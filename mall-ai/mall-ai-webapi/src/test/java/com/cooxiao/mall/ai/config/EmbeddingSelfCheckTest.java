package com.cooxiao.mall.ai.config;

import com.cooxiao.mall.ai.client.EmbeddingClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EmbeddingSelfCheck} 单测（P0，2026-09-11）。
 *
 * <p><b>用手写 fake，不用 Mockito</b>：本项目沙箱里 Mockito 的 inline MockMaker 加载失败
 * （见 [[本地双实例锁验证报告-2026-09-09]]）；而 P1 把 embedding 抽成接口之后，
 * fake 只要实现两个方法 —— 这本身就是"依赖倒置让代码可测"的直接收益。
 *
 * <p>覆盖 4 条语义：**没开就不调用** / 维度一致放行 / **维度不一致启动失败且报错可操作** / 瞬态失败不阻断启动。
 */
class EmbeddingSelfCheckTest {

    // ---------------------------------------------------------------- fixtures

    private static void inject(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("注入失败: " + field, e);
        }
    }

    private static EmbeddingSelfCheck check(int dims, boolean enabled, EmbeddingClient client) {
        AiProperties props = new AiProperties();
        props.setEmbeddingEnabled(enabled);
        props.setEmbeddingDimensions(dims);
        props.setEmbeddingModel("fake-model");
        props.setEmbeddingBaseUrl("http://127.0.0.1:9999");
        EmbeddingSelfCheck self = new EmbeddingSelfCheck();
        inject(self, "aiProperties", props);
        inject(self, "embeddingClient", client);
        return self;
    }

    /** 正常实现：返回指定维度 */
    private static EmbeddingClient okClient(int dims) {
        return new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                return new float[dims];
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                return List.of(new float[dims]);
            }
        };
    }

    /** 任何调用都失败（模拟外部 API 不可用） */
    private static EmbeddingClient brokenClient(String message) {
        return new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                throw new IllegalStateException(message);
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                throw new IllegalStateException(message);
            }
        };
    }

    /** 一被调用就断言失败（用于验证"不该调用"） */
    private static EmbeddingClient mustNotBeCalledClient() {
        return new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                throw new AssertionError("embedding-enabled=false 时不应调用外部接口");
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                throw new AssertionError("embedding-enabled=false 时不应调用外部接口");
            }
        };
    }

    // ---------------------------------------------------------------- tests

    @Test
    void embeddingDisabled_skipsAndNeverCallsApi() {
        assertDoesNotThrow(() -> check(1024, false, mustNotBeCalledClient()).init());
    }

    @Test
    void dimensionMatches_passes() {
        assertDoesNotThrow(() -> check(1024, true, okClient(1024)).init());
    }

    @Test
    void dimensionMismatch_failsStartup_withActionableMessage() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> check(1024, true, okClient(768)).init());
        String msg = e.getMessage();
        assertTrue(msg.contains("768"), "报错必须带上模型实际维度，实际: " + msg);
        assertTrue(msg.contains("1024"), "报错必须带上配置维度，实际: " + msg);
        assertTrue(msg.contains("DELETE"), "报错必须给出\"删索引重建\"的可操作步骤，实际: " + msg);
    }

    @Test
    void transientFailure_doesNotBlockStartup() {
        // 外部 API 挂了（网络/余额/超时）→ 不能把"外部依赖故障"放大成"服务起不来"
        assertDoesNotThrow(() -> check(1024, true, brokenClient("503 Service Unavailable")).init());
    }

    @Test
    void emptyVector_isTreatedAsTransient_notConfigError() {
        EmbeddingClient empty = new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                return new float[0];
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                return List.of();
            }
        };
        assertDoesNotThrow(() -> check(1024, true, empty).init());
    }

    @Test
    void nullVector_isTreatedAsTransient_notConfigError() {
        EmbeddingClient nulls = new EmbeddingClient() {
            @Override
            public float[] embed(String text) {
                return null;
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                return List.of();
            }
        };
        assertDoesNotThrow(() -> check(1024, true, nulls).init());
    }
}
