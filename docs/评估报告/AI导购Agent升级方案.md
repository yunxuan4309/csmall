# AI 导购 Agent 升级方案

> **状态**: 📋 评估 + 计划(2026-09-02),**不执行**——与集群化方案同款"仅讨论计划"模式
> **关联**: [[TODO文件]]#32、[[面试准备/09-AI模块]] Q0/Q12
> **定位**: 技术演示增强 + 面试素材(业务收益为零——生产 0 调用,简历未投)

---

## 一、现状盘点(代码实证)

| 已有能力 | 代码证据 |
|---------|---------|
| LLM 意图解析 | `ChatServiceImpl.extractSearchIntent`:消息 → SearchIntent JSON(预算/品牌/品类) |
| 意图驱动检索 | `RagServiceImpl.intentSearch`:按预算/品牌/品类过滤 + ES 多路召回 |
| 跨轮偏好记忆 | `session.preferences` 存 budget("5000 以内"会记住) |
| SSE 流式 + 思考卡片 | ChatServiceImpl + SearchPipeline(onThinking) |
| 预算控制 | TokenBudgetService(2 元/天,Redis 日计数) |
| 会话管理 | SessionManager(ai:chat:session:* TTL 24h) |

**本质**: "LLM 解析意图 → 代码写死执行路径 → 生成"——LLM 只当**解析器**,没有动作决策权,无工具调用,无循环。

---

## 二、与 Agent 的差距清单

| # | 差距 | 当前 | Agent 应该 |
|---|------|------|-----------|
| 1 | LLM 无动作决策权 | LLM 只输出 SearchIntent,执行由代码决定 | LLM 自己选工具("要查商品/要对比/要看库存") |
| 2 | 无 Function Calling | "检索"是固定代码,LLM 不可见不可选 | 声明工具集,LLM 按需调用 |
| 3 | 无执行循环 | 意图→检索→答,一次到底 | ReAct:查→评估→再查→答 |
| 4 | 无执行验证/反思 | 生成完就完 | 工具结果校验,答非所问重检索 |
| 5 | 记忆 | ✅ 偏好记忆(超纯聊天记忆,亮点) | + 执行轨迹记忆(做了哪些动作) |

**一句话**: 现在是"会听指令的检索器";Agent 是"会自己决定怎么干活的助手"。

---

## 三、电商 Agent 场景与工具集设计

### 候选场景

| 场景 | 说明 | 工具 |
|------|------|------|
| 深度导购(升级现有) | 多轮追问预算/品牌/需求 → 推荐+理由 | search_products |
| 主动比价 | "这个比 X 便宜吗?" → 对比表 | compare_products(复用 ProductCompareServiceImpl) |
| 库存/秒杀助手 | "Y 有货吗?" → 实时库存、场次提醒 | get_stock(Dubbo 调 product/seckill) |
| 订单/售后客服 | "订单到哪了?" → 查订单/物流 | query_order(Dubbo 调 order) |
| 优惠/凑单 | 查优惠券、凑满减建议 | get_coupon |
| 写操作(需谨慎) | 加购/下单 | add_to_cart / create_order(建议期不做) |

### 工具声明格式(OpenAI 兼容 tools 参数)

```json
{
  "type": "function",
  "function": {
    "name": "search_products",
    "description": "按条件检索商品,返回商品列表",
    "parameters": {
      "type": "object",
      "properties": {
        "keywords": {"type": "string"},
        "budgetMax": {"type": "number"},
        "brand": {"type": "string"}
      },
      "required": ["keywords"]
    }
  }
}
```

---

## 四、边界设计(重点——Agent 面试最值钱考点)

### 1. 操作权限分级

| 级别 | 示例 | 策略 |
|------|------|------|
| 🔵 只读 | 检索/比价/查库存/查订单 | 登录即可,AI 自动执行 |
| 🟡 写操作 | 加购/改订单 | **AI 只"建议",用户点确认才执行**(human-in-the-loop) |
| 🔴 高敏 | 退款/改价/发券 | 角色限制 + 双重确认 + 审计,或**不给 AI 该工具** |

> 电商涉及钱,与 IoT 不同:IoT 调参可自动(有 AuditLog);电商写操作必须"AI 提议 → 用户拍板"。

### 2. 工具级边界(代码层强制)

```
① 工具白名单:暴露哪些是设计决策,最小权限
② 参数 JSON Schema 约束 + 服务端二次校验(复用 IoT 7 参数白名单思路)
③ 轮数上限:ReAct 最多 3 轮,防循环失控
④ 预算复用:每轮 record 进 TokenBudgetService(2 元/天)
⑤ Sentinel 限流:工具调用接口防刷
```

### 3. 行为级边界

```
⑥ 结果真实性:生成只能基于工具返回,禁止编造(幻觉防护)
⑦ 角色映射:user/admin 工具集不同
⑧ 审计:每个动作落库(谁/何时/调了什么/结果)——IoT 已有 DecisionLog/AuditLog 先例可复制
⑨ 降级兜底:工具失败 → 回退纯 RAG 回答(与 TODO #31 向量降级同一思想)
```

---

## 五、分阶段实施计划

### 🔴 P0 最小可行 Function Calling —— 约 0.5~1 天

目标:让 LLM 拥有**第一个工具选择权**("要不要检索"由 AI 决定)。

1. **DeepSeekAiClient 改造**:chatCompletion 支持 `tools` 参数 + 解析响应 `tool_calls`(手写,无需新依赖——OpenAI 兼容协议)
2. **定义 ToolSpec**:工具声明(name/description/parameters JSON Schema)
3. **实现工具 1 `search_products`**:内部复用 `RagServiceImpl.intentSearch` / `SearchServiceImpl`
4. **ChatServiceImpl 改造**:请求带 tools → 响应含 tool_calls → 执行工具 → 结果拼进 messages → 再调一次 → 流式回复
5. **验证**:curl/Postman 测"推荐 5000 以内的手机"→ 观察是否触发工具调用 + 结果回填正确

改动文件:DeepSeekAiClient.java、ChatServiceImpl.java、新增 ToolRegistry.java(或 AiTool 接口)

### 🟠 P1 完整单 Agent —— 约 2~3 天

1. **工具集扩展**:`compare_products`(复用 ProductCompareServiceImpl)、`get_stock`(Dubbo 调 mall-product/mall-seckill)
2. **ReAct 循环**:max 3 轮,循环执行 tool_calls,记录每轮动作
3. **动作审计**:AgentActionLog(先 Redis 后 DB + Flyway,参考 IoT DecisionLog 表)
4. **降级兜底**:工具失败 → 回退纯 RAG
5. **边界落地**:只读工具登录可用;写操作本期**不做**或只"建议不执行"
6. **预算联动**:每轮调用 record 进 TokenBudgetService

### 🟡 P2 框架化(可选,更久)

- Spring AI Function Calling 重构(呼应 09 Q0:切换只动 mall-ai 内部,对外接口不变)
- 多 Agent 分工(导购/售后/比价)
- human-in-the-loop 确认流(写操作前端弹确认)

---

## 六、成本与风险

| 项 | 评估 |
|----|------|
| Token 成本 | 循环 = 多轮调用,费用翻倍;2 元/天预算下限轮数(max 3) |
| DeepSeek 支持 | ✅ 支持 tools 参数(function calling),OpenAI 兼容 |
| 风险 | 工具调用失败/超时 → 必须降级(否则搜索挂) |
| 演示风险 | 生产 0 调用,升级纯为面试演示,无真实流量验证 |

---

## 七、面试价值

> "我评估过把 AI 导购升级成 Agent:当前是'LLM 解析意图 + 代码写死执行',差距在工具声明、动作决策、多步循环。DeepSeek 支持 function calling,现有 intentSearch 直接声明成第一个工具——P0 半天让 AI 自己决定'要不要检索';P1 加对比/库存工具 + 3 轮 ReAct 循环 = 完整单 Agent。**边界是重点**:只读自动、写操作用户确认、参数 schema 约束、轮数上限、预算复用、动作审计、失败降级——AI 越自由越要设边界。升级定位是演示增强,业务收益为零(无真实用户)"

---

## 八、执行清单(决定做时)

- [ ] P0:DeepSeekAiClient tools 支持 + ToolRegistry + search_products 工具 + ChatServiceImpl 循环
- [ ] P0 验证:curl 触发工具调用
- [ ] P1:compare_products / get_stock 工具 + 3 轮 ReAct + 审计日志 + 降级
- [ ] P1 验证:多轮对话 + 工具审计记录 + 失败降级
- [ ] 边界验收:写操作不自动执行 / 参数越界被拦 / 轮数超限停止

---

## 附录 A:与现有 RAG 的共存设计(双路径,2026-09-02 补充)

**不是两套系统,是"RAG 变成 Agent 的工具之一"——分层共存**:

```
对外接口(前端无感):/ai/ask、/ai/chat/send、/ai/chat/stream ← 全不变

                    ChatServiceImpl(对话中枢)
                    ┌───────────────────────────────┐
                    │ 双路径(关键:省 token)          │
    简单问题 ──→ ① 快速路径(现有):意图→RAG→生成      │
                    │   不调 LLM 决策 = 便宜           │
    复杂问题 ──→ ② Agent 路径(新增):LLM 决策          │
                    │   ├ search_products(内部=RAG)   │
                    │   ├ compare_products            │
                    │   ├ get_stock ...               │
                    │   └ 3 轮 ReAct → 生成           │
                    └───────────────────────────────┘
```

| 要点 | 说明 |
|------|------|
| ① RAG 降级为工具 | search_products 内部就是现有 RAG pipeline,不重复实现 |
| ② 双路径分流 | 简单问题走快速路径(不调 LLM 决策 = 省 token);复杂问题进 Agent——呼应 IoT 三段式"先便宜后智能" |
| ③ 配置开关 | agent-enabled(像 embedding-enabled),灰度切换 |
| ④ 降级链 | 工具失败 → 回退快速路径 RAG → 再失败纯 LLM 对话,永远有兜底 |

**代码形态**:ChatServiceImpl 保留现有快速路径 + 新增 agent 分支;Agent 的 search_products 工具直接注入现有 RagServiceImpl/SearchServiceImpl(复用,零重复)。

---

## 附录 B:商品数据补充方案(更贴近真实电商,2026-09-02 补充)

**现状**:ES 20 条真实品牌商品(小米/苹果/耐克/阿迪等),upload 目录 57 张图;同步机制已自动化(/ai/syncAll 接口 + sync-auto-on-startup: true 启动全量同步)——**缺的不是同步,是数据源**。

**结论**:非必需(20 条已够演示);想做时 30-50 条封顶,重点造"真实型号 + 高质量文本描述"(AI 检索的是文本,图片只影响前端观感)。

**批量方案(按推荐排序)**:

| 方案 | 做法 | 优点 | 缺点 |
|------|------|------|------|
| ① 调后台接口(推荐) | 脚本循环调 AdminSpuController 添加接口(addNew) | 走完整业务链路(自动同步 ES/缓存/校验) | 要写脚本 |
| ② SQL 批量插 | INSERT pms_spu/pms_sku | 快 | 绕过业务,要手动触发同步 |
| ③ 生成脚本 | Python 生成真实型号 JSON(手机/笔记本/鞋服)→ 喂给方案① | 一次 50 条真实感 | 要整理型号清单 |
| ④ 爬虫抓电商 | ❌ 不建议 | — | 版权/法律风险 |

**图片来源**:复用 /data/csmall-upload/(57 张)/ 品牌官网素材(少量自用)/ 免费占位图(开发用);检索主要吃文本,图片次要。

**工作量**:脚本方案 0.5~1 天(含型号清单);同步零成本(syncAll 已有)。
