package com.cooxiao.mall.ai.config;

import com.cooxiao.mall.ai.client.EmbeddingClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Embedding <b>启动自检</b>（P0，2026-09-11）—— 让"配置错误"在<b>启动时</b>暴露，而不是等到检索时才炸。
 *
 * <h3>为什么需要它（两次真实踩坑的共同教训）</h3>
 * <ul>
 *   <li><b>TODO #59</b>：账户余额不足、embedding 调用返 402 —— 没有前置校验，
 *       直到开了开关才发现"向量化全失败"。</li>
 *   <li><b>TODO #63</b>：ES 索引 mapping 与代码期望完全不同（无 {@code semanticVector}、分词器不对）——
 *       索引只在不存在时创建，<b>漂移了也不会被任何人发现</b>，直到逐条实测才查出来。</li>
 * </ul>
 * 共同点：<b>配置/环境与代码的契约不一致，却没有任何一方主动校验。</b>
 *
 * <h3>校验策略（刻意区分两类失败 —— 只让"确定性配置错误"阻断启动）</h3>
 * <table border="1">
 *   <tr><th>情形</th><th>处置</th><th>为什么</th></tr>
 *   <tr>
 *     <td>返回维度 ≠ {@code cooxiao.ai.embedding-dimensions}</td>
 *     <td><b>抛异常 → 启动失败</b></td>
 *     <td><b>确定性配置错误</b>（换了模型没同步改维度，或改了维度没重建索引）。
 *         带着它启动只会往 ES 写维度不符的向量 → 必须当场拦住并给出修复步骤。</td>
 *   </tr>
 *   <tr>
 *     <td>调用失败（网络 / 余额 / 401 / 超时）</td>
 *     <td><b>只 WARN，启动继续</b></td>
 *     <td><b>瞬态外部故障</b> —— 若让它阻断启动，就把"外部 API 挂了"放大成"服务起不来"，
 *         那是更大的故障。运行期已有降级兜底（向量失败 → 回落全文检索 / 仅全文索引）。</td>
 *   </tr>
 *   <tr>
 *     <td>返回空向量（异常响应）</td>
 *     <td>WARN，启动继续</td>
 *     <td>不是维度配置问题，按瞬态处理；保持"**只有维度不一致才致命**"这条规则足够简单。</td>
 *   </tr>
 *   <tr>
 *     <td>{@code embedding-enabled=false}</td>
 *     <td>直接跳过</td>
 *     <td>没开向量检索就不该调用外部接口（启动零副作用、零 token 消耗）。</td>
 *   </tr>
 * </table>
 *
 * <p><b>执行时机</b>：{@code @PostConstruct}，且 {@link com.cooxiao.mall.ai.init.EsIndexInitializer}
 * 用 {@code @DependsOn("embeddingSelfCheck")} 声明了依赖 → <b>自检先跑、建索引后跑</b>，
 * 避免"用错误的维度先把索引建出来"。
 */
@Slf4j
@Component
public class EmbeddingSelfCheck {

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private EmbeddingClient embeddingClient;

    /** 探针文本（只消耗个位数 token） */
    private static final String PROBE_TEXT = "embedding self-check probe";

    @PostConstruct
    public void init() {
        if (!aiProperties.isEmbeddingEnabled()) {
            log.info("Embedding 启动自检：跳过（embedding-enabled=false，走 ES 全文检索）");
            return;
        }

        float[] vector;
        try {
            vector = embeddingClient.embed(PROBE_TEXT);
        } catch (Exception e) {
            // 瞬态外部故障 → 不阻断启动（运行期有降级兜底）
            log.warn("Embedding 启动自检未通过（外部调用失败，不影响启动；运行期将按降级策略回落全文检索）：{}",
                    e.toString());
            return;
        }

        if (vector == null || vector.length == 0) {
            log.warn("Embedding 启动自检：接口返回空向量（按瞬态异常处理，不阻断启动）");
            return;
        }

        int expected = aiProperties.getEmbeddingDimensions();
        if (vector.length != expected) {
            // 🔴 确定性配置错误 → 抛出去让 Spring 启动失败，并给出可操作的修复步骤
            //    ⚠️ 这个 throw 刻意放在 try 之外，免得被上面的 catch 吞掉
            throw new IllegalStateException(String.format(
                    "Embedding 维度不一致：模型 [%s] 实际返回 %d 维，而配置 cooxiao.ai.embedding-dimensions = %d。"
                            + "修复步骤：① 让 embedding-dimensions 与模型实际维度一致（或换成同维度的模型）；"
                            + "② dense_vector.dims 建成后不可改 → 必须删掉 ES 索引"
                            + "（curl -X DELETE localhost:9200/cool_shark_mall_ai），再重启 mall-ai，"
                            + "由 EsIndexInitializer 重建索引并全量重同步写入新向量。",
                    aiProperties.getEmbeddingModel(), vector.length, expected));
        }

        log.info("Embedding 启动自检通过：模型={}, 维度={}, baseUrl={}",
                aiProperties.getEmbeddingModel(), vector.length, aiProperties.getEmbeddingBaseUrl());
    }
}
