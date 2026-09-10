package com.cooxiao.mall.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cooxiao.mall.ai.client.AiClient;
import com.cooxiao.mall.ai.client.AiToolCall;
import com.cooxiao.mall.ai.client.AiToolRound;
import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.ai.config.AiTask;
import com.cooxiao.mall.ai.model.SearchIntent;
import com.cooxiao.mall.ai.service.AgentActionAuditor;
import com.cooxiao.mall.ai.service.SearchPipeline;
import com.cooxiao.mall.ai.service.PreferenceExtractor;
import com.cooxiao.mall.ai.service.SessionManager;
import com.cooxiao.mall.ai.service.TokenBudgetService;
import com.cooxiao.mall.pojo.ai.model.ChatMessage;
import com.cooxiao.mall.pojo.ai.model.ChatSession;
import com.cooxiao.mall.pojo.ai.vo.ChatHistoryVO;
import com.cooxiao.mall.pojo.ai.vo.ChatResultVO;
import com.cooxiao.mall.pojo.ai.vo.RelatedProductVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.*;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 多轮对话服务实现 — 企业级升级
 * 流式输出 + AI 意图提取 → ES 结构化检索 → AI 生成回答
 */
@Slf4j
@Service
public class ChatServiceImpl {

    private static final int SEARCH_TOP_K = 10;
    /** Agent 回放的历史消息条数上限：工具轮请求体最大（含 tools schema + observation），历史必须封顶 */
    private static final int AGENT_HISTORY_LIMIT = 8;

    /**
     * 商品/购买意图的**保守**关键词表 —— 命中则首轮强制调用工具（`tool_choice=required`）。
     * <p>宁可多强制一次检索，也不让模型"凭历史对话编商品"：没有工具结果就没有商品卡片（2026-09-10 实测踩到）。
     */
    private static final List<String> PRODUCT_INTENT_KEYWORDS = List.of(
            "买", "推荐", "找", "搜", "有没有", "有货", "库存", "多少钱", "价格", "预算",
            "便宜", "贵", "对比", "比较", "哪个", "选", "适合");
    private static final ExecutorService SSE_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    @PreDestroy
    void shutdown() { SSE_EXECUTOR.shutdown(); }

    @Autowired private SessionManager sessionManager;
    @Autowired private PreferenceExtractor preferenceExtractor;
    @Autowired private TokenBudgetService tokenBudgetService;
    @Autowired private RagServiceImpl ragService;
    @Autowired private SearchPipeline searchPipeline;
    @Autowired private AiClient aiClient;
    @Autowired private AiProperties aiProperties;
    @Autowired private ToolRegistry toolRegistry;
    @Autowired private AgentActionAuditor agentActionAuditor;

    /** 创建新会话 */
    public ChatResultVO createSession(Long userId) {
        ChatSession session = sessionManager.createSession(userId);
        ChatResultVO vo = new ChatResultVO();
        vo.setSessionId(session.getSessionId());
        vo.setReply("您好！我是 CoolShark 智能导购助手。聊聊您的需求，比如预算、用途、品牌，我帮您选最合适的商品。");
        vo.setPreferences(Map.of());
        vo.setRelatedProducts(List.of());
        return vo;
    }

    // ================================================================
    // 核心：AI 意图提取 → ES 结构化检索
    // ================================================================

    /** 发送消息（同步版本，兼容旧接口） */
    public ChatResultVO send(Long userId, String sessionId, String message) {
        ChatSession session = loadOrCreate(userId, sessionId);
        if (budgetExceeded(sessionId)) return budgetExceededVO(session);

        // TODO #32 P0：Agent 分支（开关默认关）。任何异常都降级回固定流水线 ——
        // 新链路的探索成本不能转嫁给用户，最差也要给出与升级前完全一致的答案。
        if (aiProperties.isAgentEnabled()) {
            try {
                return sendWithAgent(session, message);
            } catch (Exception e) {
                log.warn("Agent 流程异常，降级为固定流水线：{}", e.getMessage(), e);
            }
        }
        return sendWithPipeline(session, message);
    }

    /** 固定流水线：AI 意图提取 → ES 结构化检索 → 拼接上下文 → 生成回答（Agent 关闭时的唯一路径） */
    private ChatResultVO sendWithPipeline(ChatSession session, String message) {
        // 1. AI 提取搜索意图（替代正则猜预算）
        SearchIntent intent = extractSearchIntent(message, session);
        log.info("AI 提取搜索意图: {}", JSON.toJSONString(intent));

        // 2. 结构化 ES 检索
        List<Map<String, Object>> hits = ragService.intentSearch(intent, SEARCH_TOP_K);
        if (hits.isEmpty()) {
            log.info("结构化检索无结果，降级为全文搜索");
            hits = ragService.fullTextSearchNoPrice(message, SEARCH_TOP_K);
        }

        String searchContext = ragService.buildContext(hits);
        List<RelatedProductVO> relatedProducts = ragService.buildRelatedProducts(hits);

        // 3. 更新偏好上下文
        if (intent.getBudgetMin() != null) {
            session.getPreferences().put("budget", intent.getBudgetMin().intValue());
        }

        // 4. 构建对话并调用 AI
        String preferenceContext = buildPreferenceContext(session.getPreferences());
        List<Map<String, Object>> allMessages = buildMessages(session, message, preferenceContext, searchContext);

        String aiResponse;
        try {
            aiResponse = aiClient.chat(allMessages);
        } catch (Exception e) {
            log.error("AI 对话失败", e);
            return errorVO(session, relatedProducts);
        }

        // 5. 保存会话
        saveSession(session, message, aiResponse);

        ChatResultVO vo = new ChatResultVO();
        vo.setSessionId(session.getSessionId());
        vo.setReply(aiResponse);
        vo.setPreferences(session.getPreferences());
        vo.setRelatedProducts(relatedProducts);
        return vo;
    }

    // ================================================================
    // Agent 分支（TODO #32 P0：Function Calling）
    // ================================================================

    /**
     * 同步 Agent（{@code /ai/chat/send}）：让模型自己决定"查什么、查几次、够不够"，
     * 模型不再请求工具时那一轮的正文就是最终回答。
     */
    private ChatResultVO sendWithAgent(ChatSession session, String message) {
        List<Map<String, Object>> messages = agentMessages(session, message);
        List<Map<String, Object>> tools = toolRegistry.toolSchemas();
        List<Map<String, Object>> hits = List.of();
        int maxRounds = Math.max(1, aiProperties.getAgentMaxRounds());
        String firstToolChoice = forceFirstTool(message);

        for (int round = 1; round <= maxRounds; round++) {
            AiToolRound toolRound = aiClient.chatWithTools(messages, tools, round == 1 ? firstToolChoice : null);

            if (!toolRound.hasToolCalls()) {
                // ✅ 收敛：模型认为信息够了，本轮的正文就是最终回答
                String reply = (toolRound.content() == null || toolRound.content().isBlank())
                        ? finalAnswerWithoutTools(messages)   // 罕见：既不调工具又没正文 → 再问一次要答案
                        : toolRound.content();
                log.info("Agent 第 {} 轮收敛：命中商品 {} 条，回答 {} 字", round, hits.size(),
                        reply == null ? 0 : reply.length());
                return buildAgentResult(session, message, reply, hits);
            }

            // 模型要工具：assistant 消息（含 tool_calls）必须原样入栈，否则回灌的 tool 消息无处挂靠
            messages.add(toolRound.assistantMessage());
            for (AiToolCall call : toolRound.toolCalls()) {
                AiToolResult result = executeToolCall(session, round, call);
                if (result.hits() != null && !result.hits().isEmpty()) {
                    hits = result.hits();   // 取"最近一次真正有命中"的结果给前端（模型可能多次调用）
                }
                messages.add(toolMessage(call.id(), result.observation()));
            }
            log.info("Agent 第 {} 轮：调用工具 {} 次，累计命中 {} 条", round, toolRound.toolCalls().size(), hits.size());
        }

        // ⛔ 轮数用尽：摘掉 tools 逼它用已有信息作答，而不是把"超出轮数"这种内部细节抛给用户
        log.warn("Agent 达到最大轮数 {}，强制收口", maxRounds);
        return buildAgentResult(session, message, finalAnswerWithoutTools(messages), hits);
    }

    // ================================================================
    // Agent 公共部件（同步 / 流式共用）
    // ================================================================

    /**
     * 首轮是否**强制调用工具**（{@code tool_choice=required}）：仅当开关打开且消息命中商品意图关键词。
     * <p>依据 2026-09-10 生产实测：不加这道约束时，模型遇到与历史相似的问题会**直接引用历史里的商品**作答 ——
     * 内容也许没错，但**这一轮没有任何工具结果 → 前端商品卡片为空**。首轮强制检索一次，之后回到 auto。
     */
    private String forceFirstTool(String message) {
        if (!aiProperties.isAgentForceFirstTool() || message == null || message.isBlank()) {
            return null;
        }
        for (String keyword : PRODUCT_INTENT_KEYWORDS) {
            if (message.contains(keyword)) {
                log.info("Agent 首轮强制调用工具（命中商品意图关键词「{}」）", keyword);
                return "required";
            }
        }
        return null;
    }

    /** 组装 Agent 初始消息：system + 最近若干轮历史（只回放 role/content，工具过程不跨轮持久化）+ 本轮用户消息 */
    private List<Map<String, Object>> agentMessages(ChatSession session, String message) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(msg("system", buildAgentSystemPrompt(session.getPreferences())));
        List<ChatMessage> history = session.getMessages();
        for (int i = Math.max(0, history.size() - AGENT_HISTORY_LIMIT); i < history.size(); i++) {
            ChatMessage hist = history.get(i);
            messages.add(msg(hist.getRole(), hist.getContent()));
        }
        messages.add(msg("user", message));
        return messages;
    }

    /**
     * 执行一次工具调用（同步 / 流式共用）：**计时 → 写动作审计 → 兜住一切异常**。
     * <p>未知工具、工具抛异常，都转成可读 observation 回灌给模型（让它自己决定改参数重试还是如实告知），
     * 绝不让工具的问题打断整个循环 —— 这是 Agent 相对"写死流水线"最实在的鲁棒性差异。
     */
    private AiToolResult executeToolCall(ChatSession session, int round, AiToolCall call) {
        long start = System.currentTimeMillis();
        AiTool tool = toolRegistry.find(call.name());
        AiToolResult result;
        boolean ok = true;
        if (tool == null) {
            ok = false;
            result = AiToolResult.error("未知工具 " + call.name());
        } else {
            try {
                result = tool.execute(call.args());
            } catch (Exception e) {
                ok = false;
                log.warn("工具 {} 执行异常：{}", call.name(), e.getMessage(), e);
                result = AiToolResult.error(e.getMessage());
            }
        }
        long cost = System.currentTimeMillis() - start;
        int hitCount = result.hits() == null ? 0 : result.hits().size();
        // 审计"尽力而为"：写失败只记 WARN，不影响对话（见 AgentActionAuditor）
        agentActionAuditor.record(session.getSessionId(), round, call.name(),
                call.argumentsJson(), hitCount, cost, ok);
        return result;
    }

    /** 把工具调用翻译成用户能看懂的进度文案（前端渲染成 thinking 卡片，无需改前端） */
    private String describeTool(AiToolCall call, AiToolResult result) {
        int hits = result.hits() == null ? 0 : result.hits().size();
        String name = call.name() == null ? "未知工具" : call.name();
        String label = switch (name) {
            case "search_products" -> "🔎 商品检索";
            case "compare_products" -> "📊 商品对比";
            case "get_stock" -> "📦 库存查询";
            default -> "🔧 " + name;
        };
        if (hits > 0) {
            return label + "完成：拿到 " + hits + " 条数据";
        }
        String observation = result.observation() == null ? "" : result.observation();
        if (observation.contains("失败") || observation.contains("未知工具")) {
            return label + "没成功，正在换个方式…";
        }
        return label + "完成：这次没查到匹配结果";
    }

    // ================================================================
    // 流式 Agent（TODO #32-P1）
    // ================================================================

    /**
     * 流式 Agent：模型"边想边吐"，工具轮穿插其间。
     *
     * <p><b>事件顺序刻意与旧流水线保持一致</b>（thinking → products → sessionId → chunk* → done），前端**零改动**。
     * 真正的难点是：工具轮里模型可能先吐一段正文（preamble），而那时商品列表还没拿到。
     * 处理办法是在 products 发出之前**先把正文缓冲**，等 products/sessionId 发完再补发；products 之后正文实时转发。
     * 于是常见路径（1 轮工具 + 1 轮作答）里的作答正文是**真正逐字流式**的，事件顺序也没被破坏。
     *
     * @return true = 已完整处理（含已报错收尾）；false = 一个字都没写出去就失败，调用方可以降级到旧流水线
     */
    private boolean sendStreamWithAgent(ChatSession session, String message, java.io.OutputStream out) {
        List<Map<String, Object>> messages = agentMessages(session, message);
        List<Map<String, Object>> tools = toolRegistry.toolSchemas();
        List<Map<String, Object>> hits = List.of();
        int maxRounds = Math.max(1, aiProperties.getAgentMaxRounds());
        String firstToolChoice = forceFirstTool(message);

        boolean[] productsSent = {false};
        boolean[] anythingWritten = {false};
        StringBuilder reply = new StringBuilder();

        try {
            for (int round = 1; round <= maxRounds; round++) {
                writeSSE(out, "thinking", "🔧 第 " + round + " 轮：判断是否需要查询商品…");

                List<String> buffered = new ArrayList<>();
                AiToolRound toolRound = aiClient.streamChatWithTools(messages, tools,
                        round == 1 ? firstToolChoice : null, chunk -> {
                    if (productsSent[0]) {
                        anythingWritten[0] = true;
                        reply.append(chunk);
                        writeSSE(out, "chunk", chunk);      // products 已发 → 正文实时转发
                    } else {
                        buffered.add(chunk);                // 否则先缓冲，保证 products 排在 chunk 之前
                    }
                });

                if (!toolRound.hasToolCalls()) {
                    // ✅ 收敛：本轮正文即最终回答
                    if (!productsSent[0]) {
                        emitProductsAndSession(out, session, hits);
                        productsSent[0] = true;
                    }
                    flushBuffered(out, buffered, reply, anythingWritten);
                    finishAgentStream(out, session, message, reply);
                    return true;
                }

                messages.add(toolRound.assistantMessage());
                for (AiToolCall call : toolRound.toolCalls()) {
                    AiToolResult result = executeToolCall(session, round, call);
                    if (result.hits() != null && !result.hits().isEmpty()) {
                        hits = result.hits();
                    }
                    writeSSE(out, "thinking", describeTool(call, result));
                    messages.add(toolMessage(call.id(), result.observation()));
                }
                log.info("Agent(流式) 第 {} 轮：调用工具 {} 次，累计命中 {} 条",
                        round, toolRound.toolCalls().size(), hits.size());

                if (!productsSent[0]) {
                    emitProductsAndSession(out, session, hits);
                    productsSent[0] = true;
                }
                flushBuffered(out, buffered, reply, anythingWritten);
            }

            // ⛔ 轮数用尽：摘掉工具、流式收口
            log.warn("Agent(流式) 达到最大轮数 {}，强制收口", maxRounds);
            if (!productsSent[0]) {
                emitProductsAndSession(out, session, hits);
                productsSent[0] = true;
            }
            writeSSE(out, "thinking", "💬 信息已足够，正在生成回答…");
            messages.add(msg("user", "请立即基于以上工具返回的真实商品信息给出最终推荐，不要再请求调用工具。"));
            aiClient.streamChat(messages, chunk -> {
                anythingWritten[0] = true;
                reply.append(chunk);
                writeSSE(out, "chunk", chunk);
            });
            finishAgentStream(out, session, message, reply);
            return true;
        } catch (Exception e) {
            log.warn("流式 Agent 失败：{}", e.getMessage(), e);
            if (anythingWritten[0]) {
                // 已经吐过内容 → 不能悄悄改走旧流水线（用户会看到两段拼接），如实报错收尾
                writeSSE(out, "error", "AI 服务暂时不可用，请稍后重试。");
                writeSSE(out, "done", "");
                closeQuietly(out);
                return true;
            }
            return false;   // 一个字都没写 → 调用方降级到旧流水线
        }
    }

    /** 发商品列表 + sessionId（顺序与旧流水线一致：都在 chunk 之前） */
    private void emitProductsAndSession(java.io.OutputStream out, ChatSession session, List<Map<String, Object>> hits) {
        writeSSE(out, "products", JSON.toJSONString(ragService.buildRelatedProducts(hits)));
        writeSSE(out, "sessionId", session.getSessionId());
        writeSSE(out, "thinking", "💬 AI 正在生成回答…");
    }

    /** 补发"products 之前"缓冲下来的正文分片 */
    private void flushBuffered(java.io.OutputStream out, List<String> buffered,
                               StringBuilder reply, boolean[] anythingWritten) {
        for (String chunk : buffered) {
            anythingWritten[0] = true;
            reply.append(chunk);
            writeSSE(out, "chunk", chunk);
        }
        buffered.clear();
    }

    /** 收尾：空答复给降级话术；非空则先关流、再在后台落会话（不阻塞连接释放） */
    private void finishAgentStream(java.io.OutputStream out, ChatSession session, String message, StringBuilder reply) {
        String text = reply == null ? "" : reply.toString();
        if (text.isBlank()) {
            log.warn("流式 Agent 最终回答为空，返回降级话术");
            writeSSE(out, "error", "很抱歉，AI 服务暂时不可用，请稍后重试。");
            writeSSE(out, "done", "");
            closeQuietly(out);
            return;
        }
        writeSSE(out, "done", "");
        closeQuietly(out);
        saveSession(session, message, text);
    }

    private void closeQuietly(java.io.OutputStream out) {
        try {
            out.close();
        } catch (Exception ignored) {
        }
    }

    /** 最后一次调用不带工具：把已有 observation 压成最终回答（也用于"既不调工具又没正文"的兜底） */
    private String finalAnswerWithoutTools(List<Map<String, Object>> messages) {
        List<Map<String, Object>> finalMessages = new ArrayList<>(messages);
        finalMessages.add(msg("user", "请立即基于以上工具返回的真实商品信息给出最终推荐，不要再请求调用工具。"));
        return aiClient.chat(AiTask.AGENT, finalMessages);
    }

    private ChatResultVO buildAgentResult(ChatSession session, String message, String reply,
                                          List<Map<String, Object>> hits) {
        List<RelatedProductVO> relatedProducts = ragService.buildRelatedProducts(hits);
        if (reply == null || reply.isBlank()) {
            log.warn("Agent 最终回答为空，返回降级话术");
            return errorVO(session, relatedProducts);
        }
        saveSession(session, message, reply);

        ChatResultVO vo = new ChatResultVO();
        vo.setSessionId(session.getSessionId());
        vo.setReply(reply);
        vo.setPreferences(session.getPreferences());
        vo.setRelatedProducts(relatedProducts);
        return vo;
    }

    private String buildAgentSystemPrompt(Map<String, Object> preferences) {
        return """
                你是CoolShark电商平台的智能导购助手，可以调用工具查询真实的商品库。

                工作方式：
                1. 用户想找商品时，必须先调用 search_products 工具取真实数据，不要凭记忆回答
                2. 一次调用只表达一组条件；条件不同（不同价位/不同品类）就分多次调用
                3. 工具返回 count=0 时，可放宽条件（去掉品牌或价格）再试一次；仍为空才如实告知用户暂无匹配商品
                4. 推荐必须基于工具返回的商品，不得编造名称、价格、销量；不要罗列全部商品，挑最合适的 2~4 个并说明理由
                5. 信息足够时直接给出最终回答，不要再调用工具
                6. **即使本轮问题与历史对话相似，也必须重新调用工具核对当前数据** —— 历史里的商品名/价格/库存
                   不能直接当成当前结果（用户看到的是现在可买的商品，历史数据可能已变）
                7. 没有工具返回的数据时，不要给出具体商品名、价格或库存

                用户历史偏好（仅供理解需求，不是硬约束）：
                %s
                """.formatted(preferences.isEmpty() ? "暂无" : buildPreferenceContext(preferences));
    }

    /** 构造消息：统一 null 兜底（{@code Map.of} 遇到 null 值会直接 NPE，而历史里确实可能出现 null 正文） */
    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role == null ? "user" : role);
        m.put("content", content == null ? "" : content);
        return m;
    }

    private static Map<String, Object> toolMessage(String toolCallId, String observation) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("tool_call_id", toolCallId == null ? "" : toolCallId);
        m.put("content", observation == null ? "" : observation);
        return m;
    }

    // ================================================================
    // SSE 流式输出
    // ================================================================

    /** 流式发送消息 */
    /** 直接写 OutputStream，flush 到 TCP 层 */
    public void sendStream(Long userId, String sessionId, String message,
                           java.io.OutputStream outputStream) {
        ChatSession session = loadOrCreate(userId, sessionId);
        try {
            if (budgetExceeded(sessionId)) {
                writeSSE(outputStream, "error", "服务繁忙，请稍后再试。");
                writeSSE(outputStream, "done", "");
                outputStream.close();
                return;
            }

            writeSSE(outputStream, "thinking", "🤖 AI 正在理解您的需求...");

            // TODO #32-P1：开启 Agent 时走"流式工具循环"（模型边想边吐，工具轮穿插其间）。
            // ⚠️ 只有"一个字都还没写出去"的失败才允许降级到旧流水线 —— 否则两段回答会拼在一起。
            if (aiProperties.isAgentEnabled() && sendStreamWithAgent(session, message, outputStream)) {
                return;
            }

            sendStreamWithPipeline(session, message, outputStream);
        } catch (Exception e) {
            log.error("SSE 流式对话失败", e);
            try {
                writeSSE(outputStream, "error", "AI 服务暂时不可用，请稍后重试。");
                writeSSE(outputStream, "done", "");
                outputStream.close();
            } catch (Exception ignored) {}
        }
    }

    /** 旧流水线（意图提取 → 多路召回 → 生成），也是 Agent 不可用时的兜底 */
    private void sendStreamWithPipeline(ChatSession session, String message,
                                        java.io.OutputStream outputStream) throws Exception {
        SearchIntent intent = extractSearchIntent(message, session);
        log.info("AI 提取搜索意图: {}", JSON.toJSONString(intent));

        SearchPipeline.PipelineResult pipelineResult = searchPipeline.run(
                intent, message, SEARCH_TOP_K,
                thinking -> writeSSE(outputStream, "thinking", thinking));

        if (intent.getBudgetMin() != null) {
            session.getPreferences().put("budget", intent.getBudgetMin().intValue());
        }

        writeSSE(outputStream, "products", JSON.toJSONString(pipelineResult.getProducts()));

        if (pipelineResult.getProductCount() == 0 && !pipelineResult.getAvailableCategories().isEmpty()) {
            writeSSE(outputStream, "categories",
                    JSON.toJSONString(pipelineResult.getAvailableCategories()));
        }

        writeSSE(outputStream, "sessionId", session.getSessionId());
        writeSSE(outputStream, "thinking", "💬 AI 正在生成回答...");

        String preferenceContext = buildPreferenceContext(session.getPreferences());
        List<Map<String, Object>> allMessages = buildMessages(session, message,
                preferenceContext, pipelineResult.getSearchContext());

        StringBuilder fullResponse = new StringBuilder();
        // 模型 / 思考模式 / 温度 / max_tokens 全部由 AiClient 按任务类型决定（含并发闸门与预算记账）
        aiClient.streamChat(allMessages, chunk -> {
            fullResponse.append(chunk);
            writeSSE(outputStream, "chunk", chunk);
        });

        // 先关闭 SSE 流，避免 saveSession 阻塞导致连接不释放
        writeSSE(outputStream, "done", "");
        outputStream.close();

        // 后台保存会话（不阻塞 SSE 响应）
        saveSession(session, message, fullResponse.toString());
    }

    /** 直接写 OutputStream 字节 + flush，穿越所有缓冲层 */
    private void writeSSE(java.io.OutputStream out, String eventName, String data) {
        try {
            String sse = "event: " + eventName + "\ndata: " + data.replace("\n", "\\n") + "\n\n";
            out.write(sse.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            log.error("SSE 写入失败", e);
        }
    }

    public SseEmitter sendStreamLegacy(Long userId, String sessionId, String message) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2分钟超时
        ChatSession session = loadOrCreate(userId, sessionId);

        if (budgetExceeded(sessionId)) {
            try {
                emitter.send(SseEmitter.event().name("error").data("服务繁忙，请稍后再试。"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }

        SSE_EXECUTOR.submit(() -> {
            try {
                // Stage 0: AI 意图提取
                emitter.send(SseEmitter.event().name("thinking").data("🤖 AI 正在理解您的需求..."));

                SearchIntent intent = extractSearchIntent(message, session);
                log.info("AI 提取搜索意图: {}", JSON.toJSONString(intent));

                // Stage 1-4: 运行搜索流水线（每个阶段发出 thinking 事件）
                SearchPipeline.PipelineResult pipelineResult = searchPipeline.run(
                        intent, message, SEARCH_TOP_K,
                        thinking -> {
                            try {
                                emitter.send(SseEmitter.event().name("thinking").data(thinking));
                            } catch (Exception ignored) {}
                        });

                if (intent.getBudgetMin() != null) {
                    session.getPreferences().put("budget", intent.getBudgetMin().intValue());
                }

                // 发送商品列表
                String productsJson = JSON.toJSONString(pipelineResult.getProducts());
                emitter.send(SseEmitter.event().name("products").data(productsJson));

                // 发送可用分类（引导用户）
                if (pipelineResult.getProductCount() == 0 && !pipelineResult.getAvailableCategories().isEmpty()) {
                    emitter.send(SseEmitter.event().name("categories")
                            .data(JSON.toJSONString(pipelineResult.getAvailableCategories())));
                }

                // 发送 sessionId
                emitter.send(SseEmitter.event().name("sessionId").data(session.getSessionId()));

                // 流式 AI 回答
                emitter.send(SseEmitter.event().name("thinking").data("💬 AI 正在生成回答..."));

                String preferenceContext = buildPreferenceContext(session.getPreferences());
                List<Map<String, Object>> allMessages = buildMessages(session, message,
                        preferenceContext, pipelineResult.getSearchContext());

                StringBuilder fullResponse = new StringBuilder();
                aiClient.streamChat(allMessages, chunk -> {
                    try {
                        fullResponse.append(chunk);
                        emitter.send(SseEmitter.event().name("chunk").data(chunk));
                    } catch (Exception e) {
                        log.error("SSE 发送 chunk 失败", e);
                    }
                });

                saveSession(session, message, fullResponse.toString());
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();

            } catch (Exception e) {
                log.error("SSE 流式对话失败", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        emitter.onCompletion(() -> log.debug("SSE 完成"));
        emitter.onTimeout(() -> log.warn("SSE 超时"));
        return emitter;
    }

    // ================================================================
    // AI 意图提取
    // ================================================================

    /** 调用 AI 将用户自然语言转为结构化搜索参数 */
    private SearchIntent extractSearchIntent(String message, ChatSession session) {
        String preferenceContext = buildPreferenceContext(session.getPreferences());
        String prompt = """
                你是一个电商搜索意图解析器。根据用户消息和历史偏好，输出JSON格式的搜索参数。
                直接输出JSON，不要任何思考过程，不要输出 reasoning，不要任何解释。

                历史偏好：
                %s

                用户消息：
                %s

                JSON格式（所有字段可选）：
                {
                  "budgetMin": 数字或null,
                  "budgetMax": 数字或null,
                  "brand": "品牌名或null",
                  "category": "分类词或null（直接使用用户原话中的品类词，如用户说'衣服'就填'衣服'，不要说'男装'，系统会自动模糊匹配）",
                  "keywords": "搜索关键词（包含品类、用途、风格等核心词）",
                  "sortBy": "sales/price_asc/price_desc/null"
                }

                注意：
                - "七千左右"→budgetMin=5250,budgetMax=9100
                - "降低2000左右"→根据历史偏好计算(原7000-2000=5000)→budgetMin=3750,budgetMax=6500
                - "5000以内"→budgetMax=5000
                - "1000以内的衣服"→budgetMax=1000, category="衣服", keywords="衣服 服装"
                - "衣服"或"鞋子"等品类词同时放入category和keywords，系统用IK分词模糊匹配
                """.formatted(preferenceContext.isBlank() ? "无" : preferenceContext, message);

        try {
            // 意图提取是 JSON 结构化任务 → chatJson：官方 thinking=disabled 从机制上关掉思考，
            // 不再靠"提示词求它别想"，也不会出现 reasoning 挤空 content（TODO #58，2026-09-10）
            String raw = aiClient.chatJson(null, prompt);
            // 清理 AI 可能输出的 markdown 包裹
            raw = raw.trim();
            if (raw.startsWith("```")) raw = raw.replaceAll("```json?", "").replace("```", "").trim();
            SearchIntent intent = JSON.parseObject(raw, SearchIntent.class);
            if (intent == null) intent = fallbackIntent(message);
            return intent;
        } catch (Exception e) {
            log.warn("意图提取失败，降级为关键词搜索: {}", e.getMessage());
            return fallbackIntent(message);
        }
    }

    /** 降级：用原始消息做关键词搜索 */
    private SearchIntent fallbackIntent(String message) {
        SearchIntent intent = new SearchIntent();
        intent.setKeywords(message);
        return intent;
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    public ChatHistoryVO getHistory(String sessionId) {
        ChatSession session = sessionManager.loadSession(sessionId, null);
        ChatHistoryVO vo = new ChatHistoryVO();
        if (session == null) {
            vo.setMessages(List.of());
            vo.setPreferences(Map.of());
            return vo;
        }
        vo.setSessionId(sessionId);
        vo.setMessages(session.getMessages());
        vo.setPreferences(session.getPreferences());
        return vo;
    }

    private ChatSession loadOrCreate(Long userId, String sessionId) {
        ChatSession session = sessionManager.loadSession(sessionId, userId);
        if (session == null) {
            session = sessionManager.createSession(userId);
        }
        return session;
    }

    private boolean budgetExceeded(String sessionId) {
        return tokenBudgetService.isBudgetExceeded();
    }

    private ChatResultVO budgetExceededVO(ChatSession session) {
        ChatResultVO busy = new ChatResultVO();
        busy.setSessionId(session.getSessionId());
        busy.setReply("服务繁忙，请稍后再试。");
        busy.setPreferences(session.getPreferences());
        busy.setRelatedProducts(List.of());
        return busy;
    }

    private ChatResultVO errorVO(ChatSession session, List<RelatedProductVO> products) {
        ChatResultVO vo = new ChatResultVO();
        vo.setSessionId(session.getSessionId());
        vo.setReply("很抱歉，AI 服务暂时不可用，请稍后重试。");
        vo.setPreferences(session.getPreferences());
        vo.setRelatedProducts(products);
        return vo;
    }

    private List<Map<String, Object>> buildMessages(ChatSession session, String message,
                                                     String preferenceContext, String searchContext) {
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(Map.of("role", "system", "content", buildSystemPrompt(preferenceContext, searchContext)));
        for (ChatMessage hist : session.getMessages()) {
            msgs.add(Map.of("role", hist.getRole(), "content", hist.getContent()));
        }
        msgs.add(Map.of("role", "user", "content", message));
        return msgs;
    }

    private void saveSession(ChatSession session, String userMessage, String aiResponse) {
        session.addMessage(new ChatMessage("user", userMessage, LocalDateTime.now()));
        session.addMessage(new ChatMessage("assistant", aiResponse, LocalDateTime.now()));
        // 先保存消息到 Redis，确保对话上下文不丢
        sessionManager.save(session);
        // 偏好提取作为尽力而为操作，失败不影响会话持久化
        try {
            Map<String, String> recentHistory = new LinkedHashMap<>();
            int start = Math.max(0, session.getMessages().size() - 6);
            for (int i = start; i < session.getMessages().size(); i++) {
                ChatMessage msg = session.getMessages().get(i);
                recentHistory.put(msg.getRole(), msg.getContent());
            }
            List<Map<String, String>> historyList = new ArrayList<>();
            for (Map.Entry<String, String> e : recentHistory.entrySet()) {
                historyList.add(Map.of("role", e.getKey(), "content", e.getValue()));
            }
            Map<String, Object> newPrefs = preferenceExtractor.extract(historyList);
            session.mergePreferences(newPrefs);
            sessionManager.save(session);
        } catch (Exception e) {
            log.warn("偏好提取失败（不影响对话）：{}", e.getMessage());
        }
    }

    private String buildSystemPrompt(String preferenceContext, String searchContext) {
        return """
                你是CoolShark电商平台的智能导购助手。
                根据以下商品信息帮用户挑选商品，以对话方式交流。

                关键规则：
                1. 「相关商品信息」是系统实时检索的结果，代表当前实际可选的商品，是权威数据源
                2. 用户偏好仅供参考，不要作为硬约束
                3. 只基于提供的商品信息回答，不要编造
                4. 回答简洁自然，每次推荐说明理由

                用户历史偏好：
                %s

                相关商品信息（权威数据源）：
                %s
                """.formatted(
                        preferenceContext.isBlank() ? "暂无" : preferenceContext,
                        searchContext);
    }

    private String buildPreferenceContext(Map<String, Object> prefs) {
        if (prefs.isEmpty()) return "暂无";
        StringBuilder sb = new StringBuilder();
        if (prefs.get("budget") != null) sb.append("预算: ").append(prefs.get("budget")).append("元; ");
        if (prefs.get("category") != null) sb.append("类别: ").append(prefs.get("category")).append("; ");
        if (prefs.get("brandPreference") != null) sb.append("品牌偏好: ").append(prefs.get("brandPreference")).append("; ");
        if (prefs.get("purpose") != null) sb.append("用途: ").append(prefs.get("purpose")).append("; ");
        if (prefs.get("extraRequirements") != null) sb.append("其他要求: ").append(prefs.get("extraRequirements")).append("; ");
        return sb.toString();
    }
}
