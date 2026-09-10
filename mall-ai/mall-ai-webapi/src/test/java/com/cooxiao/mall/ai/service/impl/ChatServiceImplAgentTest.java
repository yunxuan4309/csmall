package com.cooxiao.mall.ai.service.impl;

import com.cooxiao.mall.ai.client.AiClient;
import com.cooxiao.mall.ai.client.AiToolCall;
import com.cooxiao.mall.ai.client.AiToolRound;
import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.ai.config.AiTask;
import com.cooxiao.mall.ai.service.PreferenceExtractor;
import com.cooxiao.mall.ai.service.SessionManager;
import com.cooxiao.mall.ai.service.TokenBudgetService;
import com.cooxiao.mall.pojo.ai.model.ChatSession;
import com.cooxiao.mall.pojo.ai.vo.ChatResultVO;
import com.cooxiao.mall.pojo.ai.vo.RelatedProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 循环的**离线**测试（TODO #32 P0）—— 用假 LLM 脚本化多轮响应，把 5 条关键路径钉死。
 *
 * <p>为什么必须离线：Agent 的正确性主要在**循环控制**（何时收敛 / 轮数用尽怎么收口 / 工具异常怎么降级），
 * 这些与真实模型无关而只与我们自己的代码有关；而真实模型调用**不稳定、要花钱、跑得慢**，
 * 拿它当回归测试等于没有测试。
 *
 * <p>覆盖：① 一轮工具 + 收敛；② 轮数用尽强制收口；③ 无工具但正文为空；④ 未知工具；⑤ Agent 异常降级回固定流水线；⑥ 开关关闭时不走 Agent。
 */
class ChatServiceImplAgentTest {

    private ChatServiceImpl service;
    private FakeAiClient aiClient;
    private FakeRagService ragService;
    private FakeSessionManager sessionManager;
    private FakeTool tool;
    private ChatSession session;
    private AiProperties props;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        props.getModels().put("flash", "test-model");
        props.setAgentEnabled(true);
        props.setAgentMaxRounds(3);

        aiClient = new FakeAiClient();
        ragService = new FakeRagService();
        sessionManager = new FakeSessionManager();
        tool = new FakeTool("search_products");
        session = ChatSession.create("sid-1", 1L);

        service = new ChatServiceImpl();
        ReflectionTestUtils.setField(service, "aiClient", aiClient);
        ReflectionTestUtils.setField(service, "ragService", ragService);
        ReflectionTestUtils.setField(service, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(service, "tokenBudgetService", new NoBudgetService());
        ReflectionTestUtils.setField(service, "preferenceExtractor", new NoopPreferenceExtractor());
        ReflectionTestUtils.setField(service, "aiProperties", props);
        ReflectionTestUtils.setField(service, "toolRegistry", new ToolRegistry(List.of(tool)));
    }

    // ================================================================
    // 假件（不用 Mockito：其 inline mock maker 需要动态 attach，在本机受限环境无法初始化）
    // ================================================================

    private static class NoBudgetService extends TokenBudgetService {
        @Override public boolean isBudgetExceeded() { return false; }
        @Override public void record(double amount) { }
    }

    private static class NoopPreferenceExtractor extends PreferenceExtractor {
        @Override public Map<String, Object> extract(List<Map<String, String>> recentHistory) {
            return Map.of();
        }
    }

    private class FakeSessionManager extends SessionManager {
        int saves;
        @Override public ChatSession loadSession(String sessionId, Long userId) { return session; }
        @Override public ChatSession createSession(Long userId) { return session; }
        @Override public void save(ChatSession s) { saves++; }
    }

    private static class FakeRagService extends RagServiceImpl {
        List<Map<String, Object>> intentHits = List.of();
        int intentSearchCalls;

        @Override
        List<Map<String, Object>> intentSearch(Object intent, int topK) {
            intentSearchCalls++;
            return intentHits;
        }

        @Override
        public List<Map<String, Object>> fullTextSearchNoPrice(String question, int topK) {
            return intentHits;
        }

        @Override
        List<RelatedProductVO> buildRelatedProducts(List<Map<String, Object>> hits) {
            List<RelatedProductVO> list = new ArrayList<>();
            for (Map<String, Object> doc : hits) {
                RelatedProductVO vo = new RelatedProductVO();
                vo.setName(String.valueOf(doc.get("name")));
                vo.setListPrice(BigDecimal.valueOf(((Number) doc.get("listPrice")).doubleValue()));
                list.add(vo);
            }
            return list;
        }
    }

    private static class FakeTool implements AiTool {
        private final String name;
        List<Map<String, Object>> hits = List.of();
        int calls;
        Map<String, Object> lastArgs;

        FakeTool(String name) { this.name = name; }

        @Override public String name() { return name; }
        @Override public String description() { return "fake"; }
        @Override public Map<String, Object> parameters() { return Map.of("type", "object"); }

        @Override
        public AiToolResult execute(Map<String, Object> args) {
            calls++;
            lastArgs = args;
            return new AiToolResult("{\"count\":" + hits.size() + "}", hits);
        }
    }

    /** 假 LLM：按脚本逐轮吐响应，并记录每轮收到的 messages 便于断言回灌内容 */
    private static class FakeAiClient implements AiClient {
        final Deque<AiToolRound> script = new ArrayDeque<>();
        final List<List<Map<String, Object>>> toolRoundInputs = new ArrayList<>();
        boolean throwOnToolCall;
        String finalAnswer = "（无工具的最终回答）";
        String pipelineReply = "（固定流水线回答）";
        int noToolFinalCalls;
        int pipelineChatCalls;

        @Override
        public AiToolRound chatWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
            toolRoundInputs.add(new ArrayList<>(messages));
            if (throwOnToolCall) throw new IllegalStateException("模拟 LLM 异常");
            return script.isEmpty() ? convergeRound("（脚本耗尽）") : script.poll();
        }

        @Override
        public String chat(AiTask task, List<Map<String, Object>> messages) {
            if (task == AiTask.AGENT) {
                noToolFinalCalls++;
                return finalAnswer;
            }
            pipelineChatCalls++;
            return pipelineReply;
        }

        @Override public String chat(String systemPrompt, String userMessage) { pipelineChatCalls++; return pipelineReply; }
        @Override public String chat(List<Map<String, Object>> messages) { pipelineChatCalls++; return pipelineReply; }
        @Override public String chat(AiTask task, String systemPrompt, String userMessage) { return chat(task, List.of()); }
        @Override public String chatJson(String systemPrompt, String userMessage) { return "{\"keywords\":\"手机\"}"; }
        @Override public void streamChat(List<Map<String, Object>> messages, Consumer<String> onChunk) { }
    }

    private static AiToolRound toolCallRound(String id, String name, String argsJson) {
        AiToolCall call = new AiToolCall(id, name, argsJson);
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", "");
        assistant.put("tool_calls", List.of(Map.of(
                "id", id, "type", "function",
                "function", Map.of("name", name, "arguments", argsJson))));
        return new AiToolRound("", List.of(call), assistant);
    }

    private static AiToolRound convergeRound(String content) {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", content);
        return new AiToolRound(content, List.of(), assistant);
    }

    private static Map<String, Object> hit(String name, double price) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("spuId", 1001L);
        doc.put("name", name);
        doc.put("listPrice", price);
        return doc;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> messagesOf(List<Map<String, Object>> messages, String role) {
        List<Map<String, Object>> found = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            if (role.equals(m.get("role"))) found.add(m);
        }
        return found;
    }

    // ================================================================
    // ① 正常路径：一轮工具 + 收敛
    // ================================================================

    @Test
    void convergesAfterOneToolRound_andReturnsProducts() {
        tool.hits = List.of(hit("酷鲨手机", 4999));
        aiClient.script.add(toolCallRound("call_1", "search_products", "{\"keywords\":\"手机\",\"budgetMax\":5000}"));
        aiClient.script.add(convergeRound("推荐这款酷鲨手机，4999 元。"));

        ChatResultVO vo = service.send(1L, "sid-1", "我想买 5000 以内的手机");

        assertThat(vo.getReply()).isEqualTo("推荐这款酷鲨手机，4999 元。");
        assertThat(vo.getRelatedProducts()).hasSize(1);
        assertThat(vo.getRelatedProducts().get(0).getName()).isEqualTo("酷鲨手机");

        // 工具被调用，且拿到的是模型给的参数（JSON 字符串已被解析成 Map）
        assertThat(tool.calls).isEqualTo(1);
        assertThat(tool.lastArgs).containsEntry("keywords", "手机").containsEntry("budgetMax", 5000);

        // 第 2 轮请求里必须包含：assistant 的 tool_calls + role=tool 的观察结果（否则模型无从作答）
        assertThat(aiClient.toolRoundInputs).hasSize(2);
        List<Map<String, Object>> secondRound = aiClient.toolRoundInputs.get(1);
        assertThat(messagesOf(secondRound, "assistant")).anySatisfy(m -> assertThat(m).containsKey("tool_calls"));
        List<Map<String, Object>> toolMsgs = messagesOf(secondRound, "tool");
        assertThat(toolMsgs).hasSize(1);
        assertThat(toolMsgs.get(0)).containsEntry("tool_call_id", "call_1")
                .containsEntry("content", "{\"count\":1}");

        assertThat(sessionManager.saves).isGreaterThanOrEqualTo(1);   // 会话已落库
        assertThat(aiClient.noToolFinalCalls).isZero();               // 正常收敛不该再补一次调用
    }

    // ================================================================
    // ② 轮数用尽：摘掉工具强制收口（不能把"超出轮数"抛给用户）
    // ================================================================

    @Test
    void maxRoundsExhausted_forcesFinalAnswerWithoutTools() {
        props.setAgentMaxRounds(2);
        aiClient.finalAnswer = "强制收口答案";
        aiClient.script.add(toolCallRound("c1", "search_products", "{\"keywords\":\"手机\"}"));
        aiClient.script.add(toolCallRound("c2", "search_products", "{\"keywords\":\"手机\"}"));

        ChatResultVO vo = service.send(1L, "sid-1", "找手机");

        assertThat(vo.getReply()).isEqualTo("强制收口答案");
        assertThat(aiClient.noToolFinalCalls).isEqualTo(1);   // 恰好一次"不带工具"的收口调用
        assertThat(tool.calls).isEqualTo(2);                  // 轮数上限生效，没有无限循环
    }

    // ================================================================
    // ③ 既没工具调用、正文也为空（罕见）→ 再问一次要答案
    // ================================================================

    @Test
    void blankContentWithoutToolCalls_fallsBackToPlainCall() {
        aiClient.finalAnswer = "兜底答案";
        aiClient.script.add(new AiToolRound("  ", List.of(), Map.of("role", "assistant", "content", "  ")));

        ChatResultVO vo = service.send(1L, "sid-1", "随便问问");

        assertThat(vo.getReply()).isEqualTo("兜底答案");
        assertThat(aiClient.noToolFinalCalls).isEqualTo(1);
    }

    // ================================================================
    // ④ 未知工具：回灌一条可读观察，循环继续（不中断、不 500）
    // ================================================================

    @Test
    void unknownTool_isReportedToModel_andLoopContinues() {
        aiClient.script.add(toolCallRound("c9", "not_exists", "{}"));
        aiClient.script.add(convergeRound("好的，我按已有信息回答。"));

        ChatResultVO vo = service.send(1L, "sid-1", "找手机");

        assertThat(vo.getReply()).isEqualTo("好的，我按已有信息回答。");
        List<Map<String, Object>> toolMsgs = messagesOf(aiClient.toolRoundInputs.get(1), "tool");
        assertThat(toolMsgs).hasSize(1);
        assertThat(String.valueOf(toolMsgs.get(0).get("content"))).contains("未知工具");
    }

    // ================================================================
    // ⑤ Agent 异常 → 降级回固定流水线（用户至少拿到"升级前"的答案）
    // ================================================================

    @Test
    void agentFailure_degradesToFixedPipeline() {
        aiClient.throwOnToolCall = true;
        ragService.intentHits = List.of(hit("酷鲨手机", 3999));

        ChatResultVO vo = service.send(1L, "sid-1", "找手机");

        assertThat(vo.getReply()).isEqualTo("（固定流水线回答）");
        assertThat(ragService.intentSearchCalls).isEqualTo(1);   // 确实走了旧流程
        assertThat(vo.getRelatedProducts()).hasSize(1);
    }

    // ================================================================
    // ⑥ 开关关闭 → 完全不碰 Agent 路径（灰度/回滚的安全网）
    // ================================================================

    @Test
    void agentDisabled_neverCallsToolRound() {
        props.setAgentEnabled(false);
        ragService.intentHits = List.of(hit("酷鲨手机", 3999));

        ChatResultVO vo = service.send(1L, "sid-1", "找手机");

        assertThat(vo.getReply()).isEqualTo("（固定流水线回答）");
        assertThat(aiClient.toolRoundInputs).isEmpty();
        assertThat(tool.calls).isZero();
    }
}
