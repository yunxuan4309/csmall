package com.cooxiao.mall.ai.controller;

import com.cooxiao.mall.ai.service.impl.ChatServiceImpl;
import com.cooxiao.mall.ai.service.impl.ProductCompareServiceImpl;
import com.cooxiao.mall.ai.service.impl.RagServiceImpl;
import com.cooxiao.mall.ai.service.impl.VectorSyncServiceImpl;
import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
import com.cooxiao.mall.common.restful.JsonResult;
import com.cooxiao.mall.pojo.ai.dto.AskDTO;
import com.cooxiao.mall.pojo.ai.dto.ChatSendDTO;
import com.cooxiao.mall.pojo.ai.dto.ProductCompareDTO;
import com.cooxiao.mall.pojo.ai.vo.AskResultVO;
import com.cooxiao.mall.pojo.ai.vo.ChatHistoryVO;
import com.cooxiao.mall.pojo.ai.vo.ChatResultVO;
import com.cooxiao.mall.pojo.ai.vo.CompareResultVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@Api(tags = "AI 智能导购")
public class AiController {

    @Autowired
    private ProductCompareServiceImpl compareService;

    @Autowired
    private RagServiceImpl ragService;

    @Autowired
    private VectorSyncServiceImpl vectorSyncService;

    @Autowired
    private ChatServiceImpl chatService;

    @PostMapping("/compare")
    @ApiOperation("AI 商品对比 — 选择多个商品后，AI 自动生成结构化对比结果")
    public JsonResult<CompareResultVO> compareProducts(
            @Valid @RequestBody ProductCompareDTO dto) {
        return compareService.compare(dto.getSpuIds(), dto.getDimensions());
    }

    @PostMapping("/ask")
    @ApiOperation("RAG 智能问答 — 用自然语言提问，AI 基于商品数据生成回答")
    public JsonResult<AskResultVO> ask(@Valid @RequestBody AskDTO dto) {
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
    public JsonResult<ChatResultVO> sendMessage(@Valid @RequestBody ChatSendDTO dto) {
        ChatResultVO result = chatService.send(getCurrentUserId(),
                dto.getSessionId(), dto.getMessage());
        return JsonResult.ok(result);
    }

    @GetMapping("/chat/history")
    @ApiOperation("获取对话历史")
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
