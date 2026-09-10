package com.cooxiao.mall.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 模块配置（{@code cooxiao.ai.*}）。
 *
 * <h3>模型配置可配化（TODO #58，2026-09-11）</h3>
 * 目标：官方模型<b>更名 / 换代 / 下架</b>时，<b>只改配置（环境变量）+ 重建容器</b>，不重编译、不改代码。
 *
 * <ul>
 *   <li><b>档位层 {@link #models}</b>：逻辑档位（{@code flash} / {@code pro}）→ 实际 model id。
 *       代码只引用档位名，<b>Java 里不出现任何模型名</b>。</li>
 *   <li><b>任务层 {@link #tasks}</b>：任务类型（{@link AiTask}）→ 档位 + 思考模式 +（可选）温度 / max_tokens。</li>
 * </ul>
 *
 * ⚠️ yml 里模型名<b>必须用显式占位符</b>（如 {@code ${AI_MODEL_FLASH:...}}）：
 * {@code cooxiao.ai.chat-model} 对应的环境变量是 {@code COOXIAO_AI_CHATMODEL}（去横线），
 * 写 {@code COOXIAO_AI_CHAT_MODEL} 并不映射 —— 依赖 Spring relaxed binding 隐式且易错。
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "cooxiao.ai")
public class AiProperties {

    // ================================================================
    // 档位层：逻辑档位 → 实际 model id
    // ================================================================

    /**
     * 模型档位映射。官方改名 / 换代 / 下架 <b>只改这里</b>（yml 用环境变量占位符注入）。
     * <p><b>刻意不在代码里给默认模型名</b>（chat 档位、embedding 模型均如此）—— 单一事实源是配置文件；
     * 缺失时由 {@link #modelFor(AiTask)} 报明确错误。
     * <p>代码里保留的默认值只有<b>服务端点</b>（{@link #baseUrl} / {@link #embeddingBaseUrl}）——
     * 那是"连到哪"，不是"用哪个模型"，同样可由环境变量覆盖。
     */
    private Map<String, String> models = new LinkedHashMap<>();

    /** 默认档位（任务未显式指定 {@code tier} 时回退） */
    private String defaultTier = "flash";

    /** 任务 → 档位 + 思考模式（键为 {@link AiTask#key()}） */
    private Map<String, TaskOptions> tasks = new LinkedHashMap<>();

    // ================================================================
    // 供应商 / 凭据
    // ================================================================

    /** DeepSeek API Key */
    private String apiKey;

    /** API 基础地址，默认 DeepSeek */
    private String baseUrl = "https://api.deepseek.com";

    /** API 调用超时时间（毫秒） */
    private int timeout = 60000;

    // ================================================================
    // 全局默认（可被 {@link TaskOptions} 逐任务覆盖）
    // ================================================================

    /**
     * 生成温度。
     * ⚠️ 官方明确：<b>思考模式下 temperature 不生效</b>（设了不报错）→ 本实现只在
     * {@code thinking=disabled} 时下发，避免留下"调了温度"的假象（见 TODO #58 §5.1）。
     */
    private double temperature = 0.7;

    /** 最大输出 token 数（思考模式下建议留足，否则 reasoning 吃满会让 content 为空） */
    private int maxTokens = 3000;

    // ================================================================
    // Embedding（硅基流动 BGE-M3，独立供应商）
    // ================================================================

    /** Embedding 服务 API Key（硅基流动等第三方供应商） */
    private String embeddingApiKey;

    /** Embedding 服务基础地址（默认硅基流动） */
    private String embeddingBaseUrl = "https://api.siliconflow.cn";

    /** Embedding 模型名称（硅基流动 BGE-M3）—— 与其他模型标识一致：**配置是唯一事实源**，代码不写默认值 */
    private String embeddingModel;

    /** 向量维度（BGE-M3 = 1024）；ES mapping 的 dense_vector dims 由它决定 */
    private int embeddingDimensions = 1024;

    /** 是否启用向量语义检索（默认关闭，使用 ES 全文检索） */
    private boolean embeddingEnabled = false;

    // ================================================================
    // 预算控制
    // ================================================================

    /** 每日预算上限（元） */
    private double dailyBudget = 10.0;

    /** Chat 模型输入价格（元/百万 tokens） */
    private double chatInputPricePerMillion = 1.0;

    /** Chat 模型输出价格（元/百万 tokens） */
    private double chatOutputPricePerMillion = 2.0;

    /** Embedding 模型价格（元/百万 tokens） */
    private double embeddingPricePerMillion = 1.0;

    // ================================================================
    // 部署运维
    // ================================================================

    /** 启动时自动执行全量同步 */
    private boolean syncAutoOnStartup = false;

    /** sync 接口是否加入白名单 */
    private boolean syncWhitelisted = false;

    // ================================================================
    // 高并发防护（TODO #2+#34）
    // ================================================================

    /** 并发闸门：同时进行的 LLM 调用数上限（含 chat / json / stream） */
    private int concurrentMax = 20;

    /** 并发闸门等待超时（毫秒）。0 = 不等待直接失败（推荐） */
    private int concurrentWaitMs = 0;

    /** 每用户频控开关 */
    private boolean userRateLimitEnabled = true;

    /** 每用户频控：60 秒窗口内最大请求数 */
    private int userRateLimit = 10;

    // ================================================================
    // Agent（TODO #32，Step 2 使用；Step 1 仅落地配置）
    // ================================================================

    /** Agent 双路径开关（默认关，走现有快速路径 RAG） */
    private boolean agentEnabled = false;

    /** ReAct 循环轮数上限（防循环失控） */
    private int agentMaxRounds = 3;

    // ================================================================
    // 解析器
    // ================================================================

    /**
     * 取某任务的有效配置：任务未配置的字段用全局默认补齐。**不返回 null**。
     */
    public TaskOptions taskOptions(AiTask task) {
        TaskOptions raw = tasks == null ? null : tasks.get(task.key());
        TaskOptions eff = new TaskOptions();
        eff.setTier(hasText(raw == null ? null : raw.getTier()) ? raw.getTier() : defaultTier);
        eff.setThinking(raw == null || raw.isThinking());
        eff.setReasoningEffort(raw == null ? null : raw.getReasoningEffort());
        eff.setTemperature(raw != null && raw.getTemperature() != null ? raw.getTemperature() : temperature);
        eff.setMaxTokens(raw != null && raw.getMaxTokens() != null ? raw.getMaxTokens() : maxTokens);
        return eff;
    }

    /**
     * 逻辑档位 → 实际 model id。缺失时给出可操作的报错（指向 yml 的 {@code cooxiao.ai.models}）。
     */
    public String modelFor(AiTask task) {
        String tier = taskOptions(task).getTier();
        String modelId = models == null ? null : models.get(tier);
        if (!hasText(modelId)) {
            throw new IllegalStateException(
                    "模型档位未配置：tier=[" + tier + "]（任务 " + task.key() + "）。"
                            + "请检查 cooxiao.ai.models 是否定义了该档位，或环境变量 AI_MODEL_* 是否注入。"
                            + " 当前已配置档位=" + (models == null ? "{}" : models.keySet()));
        }
        return modelId;
    }

    /** 启动时打印一次"任务 → 档位 → 模型 id / 思考模式"，配置错误可当场看见 */
    @PostConstruct
    public void logRouting() {
        StringBuilder sb = new StringBuilder();
        for (AiTask task : AiTask.values()) {
            TaskOptions o = taskOptions(task);
            String id = models == null ? null : models.get(o.getTier());
            sb.append(task.key())
                    .append('[').append(o.getTier()).append("→")
                    .append(hasText(id) ? id : "❌未配置")
                    .append(",thinking=").append(o.isThinking() ? "on" : "off");
            if (hasText(o.getReasoningEffort())) {
                sb.append(",effort=").append(o.getReasoningEffort());
            }
            sb.append("] ");
        }
        log.info("AI 模型路由（cooxiao.ai）：{}", sb.toString().trim());
        if (models == null || models.isEmpty()) {
            log.warn("cooxiao.ai.models 为空 —— AI 调用将失败，请在配置中定义档位（如 flash/pro）");
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 单个任务的配置项（档位 / 思考模式 / 可选覆盖项）。
     */
    @Data
    public static class TaskOptions {

        /** 档位名（对应 {@link #models} 的 key）；空则用 {@code defaultTier} */
        private String tier;

        /** 是否开启思考模式（默认 true，与官方默认一致） */
        private boolean thinking = true;

        /**
         * 思考强度（可选，取值 low / high / max）；留空则不下发，用官方默认。
         * <p>⚠️ 实验实测（TODO #58 §3.1 实验 I）：思考极耗 token，`max_tokens` 必须留足，
         * 否则 reasoning 吃满会让 content 为空。
         */
        private String reasoningEffort;

        /** 温度（可选）；⚠️ 思考模式下不下发（官方不生效） */
        private Double temperature;

        /** 本任务 max_tokens（可选）；空则用全局 {@link #maxTokens} */
        private Integer maxTokens;
    }
}
