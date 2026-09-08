package com.cooxiao.mall.ai.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.cooxiao.mall.ai.config.AiUserRateLimiter;
import com.cooxiao.mall.ai.service.impl.ChatServiceImpl;
import com.cooxiao.mall.ai.service.impl.ProductCompareServiceImpl;
import com.cooxiao.mall.ai.service.impl.RagServiceImpl;
import com.cooxiao.mall.ai.service.impl.SearchServiceImpl;
import com.cooxiao.mall.ai.service.impl.VectorSyncServiceImpl;
import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.common.restful.ResponseCode;
import com.cooxiao.mall.pojo.ai.dto.AskDTO;
import com.cooxiao.mall.pojo.ai.dto.ChatSendDTO;
import com.cooxiao.mall.pojo.ai.dto.ProductCompareDTO;
import com.cooxiao.mall.pojo.ai.dto.SearchDTO;
import com.cooxiao.mall.pojo.ai.vo.AskResultVO;
import com.cooxiao.mall.pojo.ai.vo.ChatHistoryVO;
import com.cooxiao.mall.pojo.ai.vo.ChatResultVO;
import com.cooxiao.mall.pojo.ai.vo.CompareResultVO;
import com.cooxiao.mall.pojo.ai.vo.RelatedProductVO;
import com.cooxiao.mall.pojo.ai.vo.SearchResultVO;
import com.cooxiao.mall.pojo.ai.vo.SuggestVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RestController
@RequestMapping("/ai")
@Api(tags = "AI 智能导购")
public class AiController {

    /** Sentinel 资源名（与 Nacos mall-ai-flow-rules 规则精确匹配，TODO #2+#34） */
    static final String RES_CHAT = "ai-chat";      // 流式/同步对话（重,LLM）
    static final String RES_REASON = "ai-reason";  // 搜索重排/问答/对比（中,LLM+ES）
    static final String RES_LIGHT = "ai-light";    // 补全/相关推荐（轻,纯ES无LLM）

    @Autowired
    private ProductCompareServiceImpl compareService;

    @Autowired
    private RagServiceImpl ragService;

    @Autowired
    private VectorSyncServiceImpl vectorSyncService;

    @Autowired
    private ChatServiceImpl chatService;

    @Autowired
    private SearchServiceImpl searchService;

    @Autowired
    private AiUserRateLimiter userRateLimiter;

    // ========== Phase 4: AI 搜索增强 ==========

    @PostMapping("/search")
    @ApiOperation("AI 语义搜索 — ES 召回 Top-15 → AI 按意图重排序 → 返回 Top-5 + 解释")
    @SentinelResource(value = "ai-reason", blockHandler = "searchBlock")
    public JsonResult<SearchResultVO> search(@Valid @RequestBody SearchDTO dto) {
        userRateLimiter.checkRate(getCurrentUserId(), "search");
        SearchResultVO result = searchService.search(
                dto.getKeyword(), dto.getPage(), dto.getPageSize());
        return JsonResult.ok(result);
    }

    @GetMapping("/search/suggest")
    @ApiOperation("搜索自动补全 — 输入部分文字实时返回补全建议")
    @SentinelResource(value = "ai-light", blockHandler = "suggestBlock")
    public JsonResult<SuggestVO> suggest(@RequestParam String keyword) {
        SuggestVO result = searchService.suggest(keyword);
        return JsonResult.ok(result);
    }

    @GetMapping("/product/{spuId}/related")
    @ApiOperation("相关商品推荐 — 基于 ES more_like_this，返回与当前商品相似的商品")
    @SentinelResource(value = "ai-light", blockHandler = "relatedBlock")
    public JsonResult<List<RelatedProductVO>> getRelated(@PathVariable Long spuId) {
        List<RelatedProductVO> result = searchService.getRelated(spuId);
        return JsonResult.ok(result);
    }

    // ========== Phase 1-2: 商品对比 + RAG 问答 ==========

    @PostMapping("/compare")
    @ApiOperation("AI 商品对比 — 选择多个商品后，AI 自动生成结构化对比结果")
    @SentinelResource(value = "ai-reason", blockHandler = "compareBlock")
    public JsonResult<CompareResultVO> compareProducts(
            @Valid @RequestBody ProductCompareDTO dto) {
        userRateLimiter.checkRate(getCurrentUserId(), "compare");
        return compareService.compare(dto.getSpuIds(), dto.getDimensions());
    }

    @PostMapping("/ask")
    @ApiOperation("RAG 智能问答 — 用自然语言提问，AI 基于商品数据生成回答")
    @SentinelResource(value = "ai-reason", blockHandler = "askBlock")
    public JsonResult<AskResultVO> ask(@Valid @RequestBody AskDTO dto) {
        userRateLimiter.checkRate(getCurrentUserId(), "ask");
        AskResultVO result = ragService.ask(dto.getQuestion(), dto.getTopK());
        return JsonResult.ok(result);
    }

    // ========== Phase 3: 多轮对话 ==========

    @PostMapping("/chat/session")
    @ApiOperation("创建 AI 导购对话会话")
    public JsonResult<ChatResultVO> createChatSession() {
        ChatResultVO result = chatService.createSession(getCurrentUserId());
        return JsonResult.ok(result);
    }

    @PostMapping("/chat/send")
    @ApiOperation("发送消息给 AI 导购（多轮对话，带上下文记忆）")
    @SentinelResource(value = "ai-chat", blockHandler = "chatBlock")
    public JsonResult<ChatResultVO> sendMessage(@Valid @RequestBody ChatSendDTO dto) {
        userRateLimiter.checkRate(getCurrentUserId(), "chat");
        ChatResultVO result = chatService.send(getCurrentUserId(),
                dto.getSessionId(), dto.getMessage());
        return JsonResult.ok(result);
    }

    @CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173", "http://8.156.77.197"})
    @PostMapping("/chat/stream")
    @ApiOperation("流式发送消息给 AI 导购（逐字输出 + 商品卡片）")
    @SentinelResource(value = "ai-chat", blockHandler = "chatStreamBlock")
    public ResponseEntity<StreamingResponseBody> streamMessage(@Valid @RequestBody ChatSendDTO dto) {
        Long userId = getCurrentUserId();
        userRateLimiter.checkRate(userId, "chat-stream");
        StreamingResponseBody body = outputStream -> {
            // 直接写 OutputStream（绕过 PrintWriter 和 Tomcat buffer）
            chatService.sendStream(userId, dto.getSessionId(), dto.getMessage(), outputStream);
        };
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    // 保留旧接口兼容
    @CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173", "http://8.156.77.197"})
    @PostMapping(value = "/chat/stream-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ApiOperation("流式发送消息给 AI 导购（SseEmitter 兼容）")
    @SentinelResource(value = "ai-chat", blockHandler = "chatStreamSseBlock")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamMessageLegacy(
            @Valid @RequestBody ChatSendDTO dto) {
        userRateLimiter.checkRate(getCurrentUserId(), "chat-stream-sse");
        return chatService.sendStreamLegacy(getCurrentUserId(),
                dto.getSessionId(), dto.getMessage());
    }

    @GetMapping("/chat/history")
    @ApiOperation("获取对话历史")
    @SentinelResource(value = "ai-light", blockHandler = "historyBlock")
    public JsonResult<ChatHistoryVO> getHistory(@RequestParam String sessionId) {
        ChatHistoryVO result = chatService.getHistory(sessionId);
        return JsonResult.ok(result);
    }

    // ========== 数据同步 ==========

    @PostMapping("/sync")
    @ApiOperation("全量同步商品数据到 ES（含向量化），供 RAG 检索使用")
    public JsonResult<String> syncAll() {
        int count = vectorSyncService.syncAll();
        return JsonResult.ok("同步完成，共 " + count + " 条商品");
    }

    @PostMapping("/sync/{spuId}")
    @ApiOperation("增量同步指定 SPU 到 ES")
    public JsonResult<String> syncSpu(@PathVariable Long spuId) {
        vectorSyncService.syncSpu(spuId);
        return JsonResult.ok("SPU " + spuId + " 同步完成");
    }

    // ========== Sentinel blockHandler（TODO #2：限流触发 → 429，非 500） ==========
    // ⚠️ 签名铁律：blockHandler 参数列表 = 原方法全部参数 + 末尾 BlockException（Sentinel 反射精确匹配，
    //    缺原参数会找不到方法 → FlowException 原样抛出 → 500。2026-09-08 部署实测踩坑）

    /** /ai/search 被限流 */
    public JsonResult<SearchResultVO> searchBlock(SearchDTO dto, BlockException e) {
        return busyResult("AI 服务繁忙，请稍后再试");
    }

    /** /ai/search/suggest 被限流 */
    public JsonResult<SuggestVO> suggestBlock(String keyword, BlockException e) {
        return busyResult("请求过于频繁，请稍后再试");
    }

    /** /ai/product/{spuId}/related 被限流 */
    public JsonResult<List<RelatedProductVO>> relatedBlock(Long spuId, BlockException e) {
        return busyResult("请求过于频繁，请稍后再试");
    }

    /** /ai/compare 被限流 */
    public JsonResult<CompareResultVO> compareBlock(ProductCompareDTO dto, BlockException e) {
        return busyResult("AI 服务繁忙，请稍后再试");
    }

    /** /ai/ask 被限流 */
    public JsonResult<AskResultVO> askBlock(AskDTO dto, BlockException e) {
        return busyResult("AI 服务繁忙，请稍后再试");
    }

    /** /ai/chat/send 被限流 */
    public JsonResult<ChatResultVO> chatBlock(ChatSendDTO dto, BlockException e) {
        return busyResult("AI 对话请求过于频繁，请稍后再试");
    }

    /** /ai/chat/history 被限流 */
    public JsonResult<ChatHistoryVO> historyBlock(String sessionId, BlockException e) {
        return busyResult("请求过于频繁，请稍后再试");
    }

    /** /ai/chat/stream 被限流：返回"繁忙"的 SSE 流（前端按 error 事件处理，与正常 error 分支一致） */
    public ResponseEntity<StreamingResponseBody> chatStreamBlock(ChatSendDTO dto, BlockException e) {
        StreamingResponseBody body = outputStream -> {
            try {
                String sse = "event: error\ndata: AI 服务繁忙，请稍后再试。\n\n"
                        + "event: done\ndata: \n\n";
                outputStream.write(sse.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (Exception ignored) {
            } finally {
                try { outputStream.close(); } catch (Exception ignored) {}
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .body(body);
    }

    /** /ai/chat/stream-sse（SseEmitter）被限流：发 error 事件后 complete */
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chatStreamSseBlock(
            ChatSendDTO dto, BlockException e) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter();
        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("error").data("AI 服务繁忙，请稍后再试。"));
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("done").data(""));
            emitter.complete();
        } catch (Exception ignored) {
            emitter.completeWithError(ignored);
        }
        return emitter;
    }

    /** 构造 429 busy JSON（泛型擦除，运行时类型安全） */
    private <T> JsonResult<T> busyResult(String message) {
        JsonResult<T> result = new JsonResult<>();
        result.setState(ResponseCode.TOO_MANY_REQUESTS.getValue());
        result.setMessage(message);
        return result;
    }

    /** 从 SecurityContext 获取当前登录用户 ID */
    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof UsernamePasswordAuthenticationToken token
                && token.getCredentials() instanceof CsmallAuthenticationInfo info) {
            return (long) info.getId();
        }
        return 0L; // 未登录用户（实际不会出现，因为所有接口都需要鉴权）
    }
}
