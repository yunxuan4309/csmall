package com.cooxiao.mall.ai.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cooxiao.mall.ai.client.AiClient;
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

import java.time.LocalDateTime;
import java.util.*;

/**
 * 多轮对话服务实现
 * 整合 ES 检索 + AI 对话 + 会话管理 + 偏好提取
 */
@Slf4j
@Service
public class ChatServiceImpl {

    private static final int SEARCH_TOP_K = 5;

    @Autowired
    private SessionManager sessionManager;
    @Autowired
    private PreferenceExtractor preferenceExtractor;
    @Autowired
    private TokenBudgetService tokenBudgetService;
    @Autowired
    private RagServiceImpl ragService;
    @Autowired
    private AiClient aiClient;

    /** 创建新会话 */
    public ChatResultVO createSession(Long userId) {
        ChatSession session = sessionManager.createSession(userId);
        ChatResultVO vo = new ChatResultVO();
        vo.setSessionId(session.getSessionId());
        vo.setReply("您好！我是 CoolShark 智能导购助手。请告诉我您的需求，比如预算、用途、品牌偏好，我帮您挑选最合适的商品。");
        vo.setPreferences(Map.of());
        vo.setRelatedProducts(List.of());
        return vo;
    }

    /** 发送消息 */
    public ChatResultVO send(Long userId, String sessionId, String message) {
        // 1. 加载或创建会话
        ChatSession session = sessionManager.loadSession(sessionId);
        if (session == null) {
            session = sessionManager.createSession(userId);
            sessionId = session.getSessionId();
        }

        // 2. 预算检查（复用已有逻辑）
        if (tokenBudgetService.isBudgetExceeded()) {
            ChatResultVO busy = new ChatResultVO();
            busy.setSessionId(sessionId);
            busy.setReply("服务繁忙，请稍后再试。");
            busy.setPreferences(session.getPreferences());
            busy.setRelatedProducts(List.of());
            return busy;
        }

        // 3. ES 检索商品（价格过滤优先，空结果时降级为全量搜索）
        List<Map<String, Object>> hits = ragService.fullTextSearch(message, SEARCH_TOP_K);
        if (hits.isEmpty()) {
            log.info("价格过滤无结果，降级为全量搜索");
            hits = ragService.fullTextSearchNoPrice(message, SEARCH_TOP_K);
        }
        String searchContext = ragService.buildContext(hits);
        List<RelatedProductVO> relatedProducts = ragService.buildRelatedProducts(hits);

        // 4. 从当前消息提取价格，即时更新偏好（避免旧偏好误导 AI）
        updateBudgetFromMessage(session, message);

        // 5. 构建用户偏好描述
        String preferenceContext = buildPreferenceContext(session.getPreferences());

        // 5. 构建 messages（历史 + 当前消息）
        List<Map<String, String>> allMessages = new ArrayList<>();
        allMessages.add(Map.of("role", "system", "content", buildSystemPrompt(preferenceContext, searchContext)));
        for (ChatMessage hist : session.getMessages()) {
            allMessages.add(Map.of("role", hist.getRole(), "content", hist.getContent()));
        }
        allMessages.add(Map.of("role", "user", "content", message));

        // 6. 调用 AI
        String aiResponse;
        try {
            aiResponse = aiClient.chat(allMessages);
        } catch (Exception e) {
            log.error("AI 对话失败", e);
            ChatResultVO errorVo = new ChatResultVO();
            errorVo.setSessionId(sessionId);
            errorVo.setReply("很抱歉，AI 服务暂时不可用，请稍后重试。");
            errorVo.setPreferences(session.getPreferences());
            errorVo.setRelatedProducts(relatedProducts);
            return errorVo;
        }

        // 7. 更新会话
        session.addMessage(new ChatMessage("user", message, LocalDateTime.now()));
        session.addMessage(new ChatMessage("assistant", aiResponse, LocalDateTime.now()));

        // 8. 提取偏好（最近 3 轮）
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

        // 9. 保存会话
        sessionManager.save(session);

        // 10. 组装结果
        ChatResultVO vo = new ChatResultVO();
        vo.setSessionId(sessionId);
        vo.setReply(aiResponse);
        vo.setPreferences(session.getPreferences());
        vo.setRelatedProducts(relatedProducts);
        return vo;
    }

    /** 获取历史 */
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

    private String buildSystemPrompt(String preferenceContext, String searchContext) {
        return """
                你是CoolShark电商平台的智能导购助手。
                根据以下商品信息帮用户挑选商品，以对话方式交流。

                关键规则：
                1. 「相关商品信息」是系统实时检索的结果，代表当前实际可选的商品，
                   它的优先级高于「用户偏好」。如果偏好说预算3000但列表里最低4000，
                   请如实告知用户并推荐最接近的商品，不要说"没有"或"不存在"
                2. 只基于提供的商品信息回答，不要编造
                3. 回答简洁自然，每次推荐说明理由

                用户历史偏好（仅供参考，不要作为硬约束）：
                %s

                相关商品信息（权威数据源）：
                %s
                """.formatted(
                        preferenceContext.isBlank() ? "暂无" : preferenceContext,
                        searchContext);
    }

    /** 从用户当前消息中提取价格并即时更新到会话偏好，避免 AI 被旧偏好误导 */
    private void updateBudgetFromMessage(ChatSession session, String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{4,5})\\s*元")
                .matcher(message);
        if (m.find()) {
            double budget = Double.parseDouble(m.group(1));
            session.getPreferences().put("budget", (int) budget);
            log.info("从当前消息提取预算: {}元，即时更新偏好", (int) budget);
        }
    }

    private String buildPreferenceContext(Map<String, Object> prefs) {
        if (prefs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        if (prefs.get("budget") != null) sb.append("预算: ").append(prefs.get("budget")).append("元; ");
        if (prefs.get("category") != null) sb.append("类别: ").append(prefs.get("category")).append("; ");
        if (prefs.get("brandPreference") != null) sb.append("品牌偏好: ").append(prefs.get("brandPreference")).append("; ");
        if (prefs.get("purpose") != null) sb.append("用途: ").append(prefs.get("purpose")).append("; ");
        if (prefs.get("extraRequirements") != null) sb.append("其他要求: ").append(prefs.get("extraRequirements")).append("; ");
        return sb.toString();
    }
}
