package com.cooxiao.mall.ai.config;

import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * mall-ai 专属异常处理（TODO #2+#34）
 *
 * AiBusyException（并发闸门满 / 每用户频控超限）→ HTTP 429 语义 JSON。
 * 注意：业务层调用 LLM 的内部降级路径（Search/Ask/Compare 的 try-catch）会先吞掉该异常走
 * "纯 ES 结果/busy VO"；只有 Controller 层直接抛出的（@SentinelResource blockHandler 不覆盖时）
 * 才落到这里——保证"繁忙"永远不是 500。
 */
@RestControllerAdvice
@Slf4j
public class AiBusyExceptionAdvice {

    @ExceptionHandler(AiBusyException.class)
    public JsonResult<Void> handleAiBusy(AiBusyException e) {
        log.warn("AI 繁忙拦截：{}", e.getMessage());
        return JsonResult.failed(ResponseCode.TOO_MANY_REQUESTS, e.getMessage());
    }
}
