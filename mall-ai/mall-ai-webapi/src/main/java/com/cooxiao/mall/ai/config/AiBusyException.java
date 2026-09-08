package com.cooxiao.mall.ai.config;

/**
 * AI 服务繁忙异常（TODO #2+#34）
 *
 * 触发场景：并发闸门满 / 每用户频控超限。
 * 语义 = "系统忙，请稍后再试"——调用方应走【降级路径】而非 500：
 * - Search → 返回纯 ES 结果（"AI 服务繁忙，已按关键词匹配度排序"）
 * - Ask/Compare → 返回 busy VO
 * - SSE Chat → 发送 error 事件 + done
 */
public class AiBusyException extends RuntimeException {

    public AiBusyException(String message) {
        super(message);
    }

    public AiBusyException(String message, Throwable cause) {
        super(message, cause);
    }
}
