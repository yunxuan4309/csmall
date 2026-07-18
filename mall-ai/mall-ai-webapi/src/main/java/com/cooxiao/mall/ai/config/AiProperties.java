package com.cooxiao.mall.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cooxiao.ai")
public class AiProperties {

    /** DeepSeek API Key */
    private String apiKey;

    /** API 基础地址，默认 DeepSeek */
    private String baseUrl = "https://api.deepseek.com";

    /** 聊天模型名称 */
    private String chatModel = "deepseek-chat";

    /** 商品对比专用模型，默认用 Flash 节省成本 */
    private String compareModel = "deepseek-v4-flash";

    /** 生成温度 0~2，默认 0.7 */
    private double temperature = 0.7;

    /** 最大输出 token 数 */
    private int maxTokens = 2000;

    /** API 调用超时时间（毫秒） */
    private int timeout = 15000;

    /** Embedding 服务 API Key（硅基流动等第三方供应商） */
    private String embeddingApiKey;

    /** Embedding 服务基础地址（默认硅基流动） */
    private String embeddingBaseUrl = "https://api.siliconflow.cn";

    /** Embedding 模型名称（硅基流动 BGE-M3，免费） */
    private String embeddingModel = "BAAI/bge-m3";

    /** 向量维度（BGE-M3 = 1024） */
    private int embeddingDimensions = 1024;

    /** 是否启用向量语义检索（默认关闭，使用 ES 全文检索） */
    private boolean embeddingEnabled = false;

    // ========== 预算控制 ==========

    /** 每日预算上限（元），默认 10 元 */
    private double dailyBudget = 10.0;

    /** Chat 模型输入价格（元/百万 tokens），deepseek-v4-flash = 1 */
    private double chatInputPricePerMillion = 1.0;

    /** Chat 模型输出价格（元/百万 tokens），deepseek-v4-flash = 2 */
    private double chatOutputPricePerMillion = 2.0;

    /** Embedding 模型价格（元/百万 tokens），deepseek-v4-flash = 1 */
    private double embeddingPricePerMillion = 1.0;

    // ========== 部署运维 ==========

    /** 启动时自动执行全量同步（生产环境建议开启，确保部署后 ES 有数据） */
    private boolean syncAutoOnStartup = false;

    /** sync 接口是否加入白名单（开发环境建议开启，生产环境建议关闭） */
    private boolean syncWhitelisted = false;
}
