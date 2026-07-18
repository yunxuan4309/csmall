package com.cooxiao.mall.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cooxiao.mall.ai.client.AiClient;
import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.ai.model.SearchIntent;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
        List<Map<String, String>> allMessages = buildMessages(session, message, preferenceContext, searchContext);

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
    // SSE 流式输出
    // ================================================================

    /** 流式发送消息 */
    public SseEmitter sendStream(Long userId, String sessionId, String message) {
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
                List<Map<String, String>> allMessages = buildMessages(session, message,
                        preferenceContext, pipelineResult.getSearchContext());

                StringBuilder fullResponse = new StringBuilder();
                streamDeepSeek(allMessages, chunk -> {
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
                只输出JSON，不要任何解释。

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
            String raw = aiClient.chatWithModel(null, prompt, "deepseek-v4-flash", true);
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
    // DeepSeek SSE 调用
    // ================================================================

    /** 通过 SSE 流式调用 DeepSeek API */
    private void streamDeepSeek(List<Map<String, String>> messages,
                                java.util.function.Consumer<String> onChunk) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI
                .create(aiProperties.getBaseUrl() + "/v1/chat/completions")
                .toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + aiProperties.getApiKey());
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "deepseek-v4-flash");
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 2000);
        body.put("stream", true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
                    try {
                        JSONObject data = JSON.parseObject(line.substring(6));
                        JSONObject delta = data.getJSONArray("choices")
                                .getJSONObject(0).getJSONObject("delta");
                        String content = delta.getString("content");
                        if (content != null && !content.isEmpty()) {
                            onChunk.accept(content);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    public ChatHistoryVO getHistory(String sessionId) {
        ChatSession session = sessionManager.loadSession(sessionId);
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
        ChatSession session = sessionManager.loadSession(sessionId);
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

    private List<Map<String, String>> buildMessages(ChatSession session, String message,
                                                     String preferenceContext, String searchContext) {
        List<Map<String, String>> msgs = new ArrayList<>();
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

        // 提取偏好（最近 3 轮）
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
