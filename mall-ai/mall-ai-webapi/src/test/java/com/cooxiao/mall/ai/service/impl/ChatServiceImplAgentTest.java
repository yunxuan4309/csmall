package com.cooxiao.mall.ai.service.impl;

import com.cooxiao.mall.ai.client.AiClient;
import com.cooxiao.mall.ai.client.AiToolCall;
import com.cooxiao.mall.ai.client.AiToolRound;
import com.cooxiao.mall.ai.config.AiProperties;
import com.cooxiao.mall.ai.config.AiTask;
import com.cooxiao.mall.ai.service.AgentActionAuditor;
import com.cooxiao.mall.ai.service.PreferenceExtractor;
import com.cooxiao.mall.ai.service.SearchPipeline;
import com.cooxiao.mall.ai.service.SessionManager;
import com.cooxiao.mall.ai.service.TokenBudgetService;
import com.cooxiao.mall.pojo.ai.model.ChatSession;
import com.cooxiao.mall.pojo.ai.vo.ChatResultVO;
import com.cooxiao.mall.pojo.ai.vo.RelatedProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 循环的**离线**测试（TODO #32 P0 + P1）—— 用假 LLM 脚本化多轮响应，把关键路径钉死。
 *
 * <p>为什么必须离线：Agent 的正确性主要在**循环控制、事件顺序、降级策略**，这些与真实模型无关而只与我们自己的代码有关；
 * 真实模型调用不稳定、要花钱、跑得慢 → 拿它当回归测试等于没有测试。
 *
 * <p>覆盖（同步）：①一轮工具+收敛 ②轮数用尽强制收口 ③正文空兜底 ④未知工具 ⑤异常降级回流水线 ⑥开关关闭不走 Agent
 * <br>覆盖（流式 P1）：⑦products 必须排在 chunk 之前且正文分片能拼接 ⑧无工具时也发空 products ⑨失败且未写内容→降级流水线
 * ⑩已写出内容后失败→如实报错、**不降级**（避免两段回答拼接）
 * <br>覆盖（审计 P1）：⑪每次工具调用都落一条动作记录（含轮次/耗时/命中数/成功标记）
 */
class ChatServiceImplAgentTest {

    private ChatServiceImpl service;
    private FakeAiClient aiClient;
    private FakeRagService ragService;
    private FakeSessionManager sessionManager;
    private FakeTool tool;
    private FakeAuditor auditor;
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
        auditor = new FakeAuditor();
        session = ChatSession.create("sid-1", 1L);

        service = new ChatServiceImpl();
        ReflectionTestUtils.setField(service, "aiClient", aiClient);
        ReflectionTestUtils.setField(service, "ragService", ragService);
        ReflectionTestUtils.setField(service, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(service, "tokenBudgetService", new NoBudgetService());
        ReflectionTestUtils.setField(service, "preferenceExtractor", new NoopPreferenceExtractor());
        ReflectionTestUtils.setField(service, "aiProperties", props);
        ReflectionTestUtils.setField(service, "toolRegistry", new ToolRegistry(List.of(tool)));
        ReflectionTestUtils.setField(service, "agentActionAuditor", auditor);
        ReflectionTestUtils.setField(service, "searchPipeline", new FakePipeline(ragService));
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

    /** 假审计器：记录调用，不碰 Redis */
    private static class FakeAuditor extends AgentActionAuditor {
        final List<String> records = new ArrayList<>();
        @Override
        public void record(String sessionId, int round, String toolName, String argsJson,
                           int hitCount, long costMs, boolean ok) {
            records.add(sessionId + "|" + round + "|" + toolName + "|" + hitCount + "|" + ok);
        }
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

    /** 假流水线：只服务"降级"测试，返回一份固定的检索结果 */
    private static class FakePipeline extends SearchPipeline {
        private final FakeRagService ragService;
        FakePipeline(FakeRagService ragService) { this.ragService = ragService; }

        @Override
        public PipelineResult run(com.cooxiao.mall.ai.model.SearchIntent intent, String userMessage,
                                  int topK, Consumer<String> onThinking) {
            PipelineResult result = new PipelineResult();
            result.setProducts(ragService.buildRelatedProducts(ragService.intentHits));
            result.setProductCount(result.getProducts().size());
            result.setSearchContext("（假上下文）");
            result.setAvailableCategories(List.of());
            return result;
        }
    }

    private static class FakeTool implements AiTool {
        private final String name;
        List<Map<String, Object>> hits = List.of();
        int calls;
        Map<String, Object> lastArgs;
        boolean throwsOnExecute;

        FakeTool(String name) { this.name = name; }

        @Override public String name() { return name; }
        @Override public String description() { return "fake"; }
        @Override public Map<String, Object> parameters() { return Map.of("type", "object"); }

        @Override
        public AiToolResult execute(Map<String, Object> args) {
            calls++;
            lastArgs = args;
            if (throwsOnExecute) throw new IllegalStateException("工具炸了");
            return new AiToolResult("{\"count\":" + hits.size() + "}", hits);
        }
    }

    /** 假 LLM：按脚本逐轮吐响应，并记录每轮收到的 messages 便于断言回灌内容 */
    private static class FakeAiClient implements AiClient {
        final Deque<AiToolRound> script = new ArrayDeque<>();
        final Deque<AiToolRound> streamScript = new ArrayDeque<>();
        final List<List<Map<String, Object>>> toolRoundInputs = new ArrayList<>();
        /** 每轮实际下发的 tool_choice（null = auto）：用来断言"首轮强制"是否生效 */
        final List<String> toolChoices = new ArrayList<>();
        final List<String> streamToolChoices = new ArrayList<>();
        boolean throwOnToolCall;
        /** 在第 N 次流式调用时"先吐一片再抛异常"（-1 = 不抛）：用来测"已经写出去之后再失败" */
        int streamThrowOnCall = -1;
        int streamCalls;
        String finalAnswer = "（无工具的最终回答）";
        String pipelineReply = "（固定流水线回答）";
        String streamReply = "（流式回答）";
        int noToolFinalCalls;
        int pipelineChatCalls;

        @Override
        public AiToolRound chatWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
            return chatWithTools(messages, tools, null);
        }

        @Override
        public AiToolRound chatWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                         String toolChoice) {
            toolRoundInputs.add(new ArrayList<>(messages));
            toolChoices.add(toolChoice);
            if (throwOnToolCall) throw new IllegalStateException("模拟 LLM 异常");
            return script.isEmpty() ? convergeRound("（脚本耗尽）") : script.poll();
        }

        @Override
        public AiToolRound streamChatWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                               Consumer<String> onContentChunk) {
            return streamChatWithTools(messages, tools, null, onContentChunk);
        }

        @Override
        public AiToolRound streamChatWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> tools,
                                               String toolChoice, Consumer<String> onContentChunk) {
            toolRoundInputs.add(new ArrayList<>(messages));
            streamToolChoices.add(toolChoice);
            streamCalls++;
            if (streamThrowOnCall > 0 && streamCalls == streamThrowOnCall) {
                onContentChunk.accept("先吐一半");
                throw new IllegalStateException("模拟流中断");
            }
            if (throwOnToolCall) throw new IllegalStateException("模拟 LLM 异常");
            AiToolRound round = streamScript.isEmpty() ? convergeRound("") : streamScript.poll();
            String content = round.content();
            if (content != null && !content.isEmpty()) {
                int mid = content.length() / 2;      // 拆两片：验证分片拼接
                onContentChunk.accept(content.substring(0, mid));
                onContentChunk.accept(content.substring(mid));
            }
            return round;
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

        @Override
        public void streamChat(List<Map<String, Object>> messages, Consumer<String> onChunk) {
            onChunk.accept(streamReply);
        }
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

    private static List<Map<String, Object>> messagesOf(List<Map<String, Object>> messages, String role) {
        List<Map<String, Object>> found = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            if (role.equals(m.get("role"))) found.add(m);
        }
        return found;
    }

    /** 跑一次流式对话，返回 SSE 原文 */
    private String sendStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.sendStream(1L, "sid-1", "找手机", out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static int eventIndex(String sse, String event) {
        return sse.indexOf("event: " + event + "\n");
    }

    // ================================================================
    // ① 同步：一轮工具 + 收敛
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

        assertThat(sessionManager.saves).isGreaterThanOrEqualTo(1);
        assertThat(aiClient.noToolFinalCalls).isZero();
    }

    // ================================================================
    // ② 轮数用尽：摘掉工具强制收口
    // ================================================================

    @Test
    void maxRoundsExhausted_forcesFinalAnswerWithoutTools() {
        props.setAgentMaxRounds(2);
        aiClient.finalAnswer = "强制收口答案";
        aiClient.script.add(toolCallRound("c1", "search_products", "{\"keywords\":\"手机\"}"));
        aiClient.script.add(toolCallRound("c2", "search_products", "{\"keywords\":\"手机\"}"));

        ChatResultVO vo = service.send(1L, "sid-1", "找手机");

        assertThat(vo.getReply()).isEqualTo("强制收口答案");
        assertThat(aiClient.noToolFinalCalls).isEqualTo(1);
        assertThat(tool.calls).isEqualTo(2);
    }

    // ================================================================
    // ③ 既没工具调用、正文也为空 → 再问一次要答案
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
    // ④ 未知工具：回灌一条可读观察，循环继续
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
    // ⑤ Agent 异常 → 降级回固定流水线
    // ================================================================

    @Test
    void agentFailure_degradesToFixedPipeline() {
        aiClient.throwOnToolCall = true;
        ragService.intentHits = List.of(hit("酷鲨手机", 3999));

        ChatResultVO vo = service.send(1L, "sid-1", "找手机");

        assertThat(vo.getReply()).isEqualTo("（固定流水线回答）");
        assertThat(ragService.intentSearchCalls).isEqualTo(1);
        assertThat(vo.getRelatedProducts()).hasSize(1);
    }

    // ================================================================
    // ⑥ 开关关闭 → 完全不碰 Agent 路径
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

    // ================================================================
    // ⑪ 审计：每次工具调用都落一条记录
    // ================================================================

    @Test
    void everyToolCall_isWrittenToAudit() {
        tool.hits = List.of(hit("酷鲨手机", 4999));
        aiClient.script.add(toolCallRound("c1", "search_products", "{\"keywords\":\"手机\"}"));
        aiClient.script.add(convergeRound("推荐。"));

        service.send(1L, "sid-1", "找手机");

        assertThat(auditor.records).hasSize(1);
        assertThat(auditor.records.get(0)).isEqualTo("sid-1|1|search_products|1|true");
    }

    @Test
    void toolException_isAuditedAsFailure_butLoopContinues() {
        tool.throwsOnExecute = true;
        aiClient.script.add(toolCallRound("c1", "search_products", "{\"keywords\":\"手机\"}"));
        aiClient.script.add(convergeRound("工具坏了，我如实告知。"));

        ChatResultVO vo = service.send(1L, "sid-1", "找手机");

        assertThat(vo.getReply()).isEqualTo("工具坏了，我如实告知。");
        assertThat(auditor.records).hasSize(1).allSatisfy(r -> assertThat(r).endsWith("|false"));
        assertThat(aiClient.noToolFinalCalls).isZero();   // 工具失败不该让整个循环改用收口调用
    }

    // ================================================================
    // ⑦ 流式：products 必须排在 chunk 之前，且正文分片能拼接
    // ================================================================

    @Test
    void streamAgent_sendsProductsBeforeChunks_andStreamsAnswer() {
        tool.hits = List.of(hit("酷鲨手机", 4999));
        aiClient.streamScript.add(toolCallRound("c1", "search_products", "{\"keywords\":\"手机\"}"));
        aiClient.streamScript.add(convergeRound("推荐酷鲨手机（4999 元），很划算。"));

        String sse = sendStream();

        int products = eventIndex(sse, "products");
        int sessionId = eventIndex(sse, "sessionId");
        int firstChunk = eventIndex(sse, "chunk");
        assertThat(products).as("必须发 products").isGreaterThanOrEqualTo(0);
        assertThat(sessionId).isGreaterThanOrEqualTo(0);
        assertThat(firstChunk).as("必须发 chunk").isGreaterThanOrEqualTo(0);
        // ★ 核心断言：商品列表在正文之前（前端依赖这个顺序渲染卡片）
        assertThat(products).isLessThan(firstChunk);
        assertThat(sessionId).isLessThan(firstChunk);
        // 商品内容真的进去了
        assertThat(sse).contains("酷鲨手机");
        // 正文两片都到齐（分片是两条独立的 data 行，所以按"落在哪一片里"断言，不假设切分位置）
        assertThat(sse).contains("推荐酷鲨手机");
        assertThat(sse).contains("很划算。");
        assertThat(sse).contains("event: done");
        assertThat(tool.calls).isEqualTo(1);
        assertThat(auditor.records).hasSize(1);
        assertThat(sessionManager.saves).isGreaterThanOrEqualTo(1);
    }

    @Test
    void streamAgent_thinkingEventsDescribeToolProgress() {
        tool.hits = List.of(hit("酷鲨手机", 4999));
        aiClient.streamScript.add(toolCallRound("c1", "search_products", "{\"keywords\":\"手机\"}"));
        aiClient.streamScript.add(convergeRound("推荐。"));

        String sse = sendStream();

        assertThat(sse).contains("第 1 轮：判断是否需要查询商品");
        assertThat(sse).contains("商品检索完成：拿到 1 条数据");
    }

    // ================================================================
    // ⑧ 流式：模型直接作答（没用工具）也要发空 products，保持前端契约
    // ================================================================

    @Test
    void streamAgent_withoutTools_stillEmitsProductsEvent() {
        aiClient.streamScript.add(convergeRound("你好，我可以帮你选商品。"));

        String sse = sendStream();

        assertThat(eventIndex(sse, "products")).isGreaterThanOrEqualTo(0);
        assertThat(eventIndex(sse, "products")).isLessThan(eventIndex(sse, "chunk"));
        assertThat(sse).contains("event: done");
        assertThat(tool.calls).isZero();
    }

    // ================================================================
    // ⑨ 流式：还没写出任何内容就失败 → 降级到旧流水线
    // ================================================================

    @Test
    void streamAgent_failureBeforeAnyChunk_fallsBackToPipeline() {
        aiClient.throwOnToolCall = true;
        ragService.intentHits = List.of(hit("酷鲨手机", 3999));

        String sse = sendStream();

        assertThat(sse).contains("（流式回答）");      // 旧流水线的回答
        assertThat(sse).contains("酷鲨手机");          // 旧流水线的商品
        assertThat(sse).doesNotContain("event: error");
        assertThat(sse).contains("event: done");
    }

    // ================================================================
    // ⑩ 流式：已经写出去之后失败 → 如实报错收尾，**不**降级（避免两段回答拼接）
    // ================================================================

    @Test
    void streamAgent_failureAfterProductsSent_reportsErrorWithoutFallingBack() {
        tool.hits = List.of(hit("酷鲨手机", 4999));
        aiClient.streamScript.add(toolCallRound("c1", "search_products", "{\"keywords\":\"手机\"}"));
        aiClient.streamThrowOnCall = 2;                 // 第 1 次（工具轮）成功；第 2 次吐一片后断流
        ragService.intentHits = List.of(hit("酷鲨手机", 3999));

        String sse = sendStream();

        assertThat(sse).contains("先吐一半");                       // 这一片已经发给前端了
        assertThat(sse).contains("event: error");
        assertThat(sse).contains("event: done");
        assertThat(sse).doesNotContain("（流式回答）");              // 没有偷偷改走旧流水线
        assertThat(ragService.intentSearchCalls).isZero();
    }

    /**
     * 反例（很值钱）：正文才吐了一半、但**还卡在缓冲里没发给前端**时断流 → 缓冲被丢弃、安全降级。
     * 这说明"products 之前先缓冲"不只是为了事件顺序，还顺带保证了降级时不会出现两段回答拼接。
     */
    @Test
    void streamAgent_failureWhileOnlyBuffered_fallsBackSafely() {
        aiClient.streamThrowOnCall = 1;                 // 第 1 次就"吐一片（进缓冲）后断流"
        ragService.intentHits = List.of(hit("酷鲨手机", 3999));

        String sse = sendStream();

        assertThat(sse).doesNotContain("event: error");
        assertThat(sse).doesNotContain("先吐一半");       // 缓冲内容从未写出去
        assertThat(sse).contains("（流式回答）");         // 完整走了旧流水线
        assertThat(sse).contains("event: done");
    }

    // ================================================================
    // ⑫ 流式：轮数用尽 → 摘掉工具、流式收口
    // ================================================================

    // ================================================================
    // ⑫ 流式：轮数用尽 → 摘掉工具、流式收口
    // ================================================================

    @Test
    void streamAgent_maxRoundsExhausted_streamsForcedConclusion() {
        props.setAgentMaxRounds(1);
        tool.hits = List.of(hit("酷鲨手机", 4999));
        aiClient.streamScript.add(toolCallRound("c1", "search_products", "{\"keywords\":\"手机\"}"));

        String sse = sendStream();

        assertThat(sse).contains("信息已足够，正在生成回答");
        assertThat(sse).contains("（流式回答）");       // 收口用的是流式作答（aiClient.streamChat）
        assertThat(sse).contains("event: done");
        assertThat(tool.calls).isEqualTo(1);
    }

    // ================================================================
    // ⑬ 首轮强制调工具（商品类问题）—— 防"凭历史作答导致商品卡片为空"
    // ================================================================

    @Test
    void productQuery_forcesToolCallOnFirstRound_thenBackToAuto() {
        tool.hits = List.of(hit("酷鲨手机", 4999));
        aiClient.script.add(toolCallRound("c1", "search_products", "{\"keywords\":\"手机\"}"));
        aiClient.script.add(convergeRound("推荐。"));

        service.send(1L, "sid-1", "我想买5000以内的手机");

        assertThat(aiClient.toolChoices).containsExactly("required", null);   // 首轮 required，之后 auto
    }

    @Test
    void nonProductQuery_doesNotForce() {
        aiClient.script.add(convergeRound("你好，我可以帮你选商品。"));

        service.send(1L, "sid-1", "你好呀");

        assertThat(aiClient.toolChoices).containsExactly((String) null);
    }

    @Test
    void forceFirstTool_canBeDisabledByConfig() {
        props.setAgentForceFirstTool(false);
        aiClient.script.add(convergeRound("推荐。"));

        service.send(1L, "sid-1", "我想买手机");

        assertThat(aiClient.toolChoices).containsExactly((String) null);
    }

    @Test
    void streamAgent_alsoForcesFirstToolForProductQuery() {
        tool.hits = List.of(hit("酷鲨手机", 4999));
        aiClient.streamScript.add(toolCallRound("c1", "search_products", "{\"keywords\":\"手机\"}"));
        aiClient.streamScript.add(convergeRound("推荐。"));

        sendStream();

        assertThat(aiClient.streamToolChoices).containsExactly("required", null);
        assertThat(tool.calls).isEqualTo(1);
    }
}
