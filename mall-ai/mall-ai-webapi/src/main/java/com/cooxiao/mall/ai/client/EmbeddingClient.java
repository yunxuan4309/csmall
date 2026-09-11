package com.cooxiao.mall.ai.client;

import java.util.List;

/**
 * 向量化（Embedding）客户端抽象 —— <b>上游只依赖这个接口，不依赖任何厂商实现</b>（依赖倒置）。
 *
 * <h3>为什么要有这个接口（2026-09-11 重构，TODO #63 衍生）</h3>
 * 重构前 {@code RagServiceImpl} / {@code VectorSyncServiceImpl} 是<b>直接注入具体类</b>
 * {@code SiliconFlowEmbeddingClient} 的，导致：
 * <ul>
 *   <li>换<b>非 OpenAI 兼容</b>的协议 → 必须改上游源码；</li>
 *   <li>想加<b>多供应商并存 / 本地模型 / 缓存装饰器 / 熔断</b> → 同样要改上游；</li>
 *   <li>单元测试必须伪造具体类（比伪造接口更笨重）。</li>
 * </ul>
 *
 * <h3>契约（刻意保持"供应商无关"）</h3>
 * <ul>
 *   <li>入参：待向量化的文本</li>
 *   <li>出参：{@code float[]}（<b>不暴露任何 SDK/HTTP 类型</b>）</li>
 *   <li>失败：抛运行时异常；<b>调用方负责降级</b>（见 {@code RagServiceImpl.vectorSearchWithFallback}
 *       与 {@code VectorSyncServiceImpl} 的"仅全文索引"降级）</li>
 *   <li>维度：由 {@code cooxiao.ai.embedding-dimensions} 约定，实现方无需感知；
 *       启动自检见 {@link com.cooxiao.mall.ai.config.EmbeddingSelfCheck}</li>
 * </ul>
 *
 * <p><b>当前实现</b>：{@link OpenAiCompatEmbeddingClient}（OpenAI 兼容协议，
 * 由 {@code cooxiao.ai.embedding-base-url} + {@code embedding-model} 决定具体平台与模型）。
 * 将来要接本地模型（Ollama/ONNX），只要再写一个实现类，<b>上游一行不用改</b>。
 *
 * <p>⚠️ <b>加第二个实现时注意</b>：上游是按<b>类型</b>注入的（{@code @Autowired EmbeddingClient}），
 * 所以一旦有两个实现同时成为 Bean，Spring 会报"expected single matching bean but found 2" ——
 * 那时需要 {@code @Primary} 或 {@code @Qualifier} + 配置开关（{@code @ConditionalOnProperty}）来选实现。
 * 这是"依赖倒置"的常见配套动作，不是缺陷。
 */
public interface EmbeddingClient {

    /** 将单条文本转为向量 */
    float[] embed(String text);

    /** 批量将文本转为向量（返回顺序与入参一致） */
    List<float[]> embedBatch(List<String> texts);
}
