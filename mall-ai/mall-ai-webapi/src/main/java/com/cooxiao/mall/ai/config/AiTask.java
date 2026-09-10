package com.cooxiao.mall.ai.config;

/**
 * AI 任务类型 —— <b>代码只认"任务"，不认"模型名"</b>。
 *
 * <p>设计动机（TODO #58 / #32，2026-09-10）：
 * 原先"按模型名区分任务能力"（JSON 小任务用 {@code deepseek-chat}、对话用 {@code deepseek-v4-flash}）
 * 把<b>任务语义</b>和<b>模型 id</b>耦在了一起 —— 官方一改名或换代，就得重新踩一遍"思考吃满 max_tokens"的坑。
 *
 * <p>现在改为：调用方声明<b>任务类型</b>，由 {@code cooxiao.ai.tasks.<task>} 决定
 * 「用哪个档位 + 要不要思考」，档位再到实际模型 id 的映射放在 {@code cooxiao.ai.models}。
 * 模型名一个都不出现在 Java 代码里。
 *
 * <p><b>注意 {@link #JSON} 与 {@link #EXPAND} 的区别</b>（2026-09-10 读码修正）：
 * 两者都要"关思考"（短任务、要稳定输出），但只有 {@link #JSON} 会下发
 * {@code response_format=json_object} —— 而 DeepSeek 要求开启该模式时<b>提示词里必须含 "json"</b>，
 * 否则 400。查询扩展输出的是空格分隔的关键词，<b>不是</b> JSON，所以单列一个任务类型。
 */
public enum AiTask {

    /** 对话 / 深度推理（RAG 回答、多轮对话、对比摘要均为其变体）—— 思考模式默认开 */
    CHAT("chat"),

    /**
     * JSON 结构化任务（意图提取、重排、偏好提取）—— 思考模式<b>关</b> + {@code response_format=json_object}。
     * ⚠️ 提示词必须含 "json" 字样。
     */
    JSON("json"),

    /** 查询扩展：输出空格分隔的关键词（纯文本）—— 思考模式<b>关</b>，但<b>不下发</b> {@code response_format} */
    EXPAND("expand"),

    /** 商品对比摘要（需要推理，思考模式开）；当前与 CHAT 同档，单列便于将来单独调档 */
    COMPARE("compare");

    private final String key;

    AiTask(String key) {
        this.key = key;
    }

    /** 配置键名（对应 {@code cooxiao.ai.tasks.<key>}） */
    public String key() {
        return key;
    }
}
