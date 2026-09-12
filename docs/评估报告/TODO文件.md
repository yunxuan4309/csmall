# CoolShark 项目待办事项

> **创建日期**: 2026-05-13 ｜ **最后更新**：2026-09-12（**第 ⑯ 批：结构重构 + 收口决策**）
> **本文件定位**：**唯一状态源** —— 读法就是从上往下：① **现在要做**（第一节）② **暂不做**（第二节）③ **编号总登记表**（查编号）④ **条目正文**（`§N`）。**已完成明细** → [[TODO已完成]]；**文档登记** → [[文档索引]]；**中/低优先级老正文** → [[TODO中低优先级]]。
> **关联**：[[TODO已完成]] · [[文档索引]] · 三个「实现与原理」册（[[TODO第三批实现与原理]] / [[TODO第三批实现与原理-1]] / [[TODO第三批实现与原理-2]]）· [[问题解决]] / [[面试准备]] 系列
> ⚠️ **编号永不重编号**（`docs/` 下 80+ 文件、1600+ 处引用）。**图例**：🔴 待执行 / 风险 · ✅ 已完成 · 📦 已归档 · ⏸️ 暂不做。

---

## 🎯 一、现在要做（简历前收口 · 2026-09-12 决策）

> **决策背景**：用户即将写简历，**不再扩展**，最多修 bug + 录视频。下表每项的**代码/脚本/操作单/评估我都已做完**，**剩下的动作需要你执行**。

| 顺位 | 编号 | 事项 | 我已完成的部分 | 你要做的动作 | 预计 |
|---|---|---|---|---|---|
| ~~1~~ | ~~**#55**~~ | ✅ **已收口（2026-09-12 · 无需动作）**：**root 账号本就是锁定的**（无密码可爆破）· `ecs-user` 保留密码登录属**接受项**（实测 247 次/6 天 爆破噪音，密码强 + 学习用途）· 副产品：**密钥登录已打通** | — | **无** | — |
| **2** | **#65** | 普通订单**库存扣减 MQ 链路整条失效**（`stock` 永不减少 + 下单无库存校验） | ✅ 修复 + 回归测试 **3/3 绿**；**已部署并验收（2026-09-12）**：容器内 jar md5 一致 · `No listener method found` **0 次** · 消费者收到**正确类型**的消息（见 §65） | ~~重建 mall-order~~ ✅ 已完成 | ✅ |
| **2b** | **#70** 🆕 | **#65 修复后暴露的两个真缺陷**：① `mall-product` 库存扣减 SQL **off-by-one**（`stock>#{quantity}` ⇒ **买最后一件永远失败**）② `mall-order` 失败路径**无限重投**（classic 队列 requeue **不写 `x-death`** ⇒ 限次计数恒 0；实测 10 分钟重投 **114 次**） | ✅ **代码已修**（含 `application-prod.yml` 补 manual ack + 限次重试） | 重建并部署 **`mall-product` + `mall-order`** 两个服务（jar 已由 AI 传到老机 `/tmp`，见 §70） | 20~30 分钟 |
| **3** | **#54** | RabbitMQ 仍是 `guest/guest`（同 VPC 任何实例可拿 administrator） | ✅ compose 已补 `SPRING_RABBITMQ_*` + 两步操作单（见 §54） | 同步 compose → 建新用户 → 切服务 → **最后**删 `guest` | 15 分钟（两步） |
| **4** | **#31** | 生产开启**向量检索**（RAG 真实运行，简历亮点） | ✅ 可行性评估完成：**三项前置实测通过**，且改为 env 覆盖**免重建**（见 §31） | **拍板开/不开**；开 = 改 `.env` + `recreate mall-ai` + 回归几条搜索 | 30 分钟 |
| **5** | **#61** | 外部端到端探活（防"全 Up、health 200，业务却挂 24h"） | ✅ 脚本 `deploy/scripts/ops/e2e_probe.py` **真机验证 9/9 PASS**（见 §61） | 脚本放新机 + 挂 cron（§61 有现成 cron 行） | 10 分钟 |
| **6** | **#67①** | 演示**录像** | ✅ [[演示录像操作手册]] 就绪（②~⑥ 已全部完成） | 按手册录 —— ⚠️ **建议先做完 #65 再录**（否则演示里库存/销量不动） | 1~2 小时 |

> ⚠️ **推荐顺序：#65 → #54 → #31 → #61 → 录像**（**#55 已于 2026-09-12 收口为"已评估·接受"，无需动作**）。
> 理由：**#65** 让"下单后库存/销量变化"这个演示卖点**真正成立** · **#31** 会**改变搜索排序**，必须在**录像定稿前**决定 · #54/#61 是成本小、收益稳的收尾。

### ✅ 本次已关闭（2026-09-12，正文仍留在第六节）
**#57**（Schema 漂移只读核实 → **0 处需要 ALTER**；两处"疑似坑"追到根因是 `database/` 快照滞后）· **#62**（`/ai/chat/stream` 并发 50% 500）· **#64**（预热 Job 查错 SKU）· **#66**（Sentinel 面板上报）· **#68**（造数前快照 → `--require-dump` 闸门）· **#69**（秒杀销量写错商品）· **#67 ②~⑥** · **#55**（SSH 暴露面 → ✅ **已评估·接受**：root **本就是锁定账号**、`ecs-user` 保留密码登录；副产品**密钥登录已打通**）。

---

## ⏸️ 二、暂不做（2026-09-12 决策 · 面试讲认知即可）

> **为什么集中在这里**：这些**都要重建服务或 recreate 容器**（成本 > 收益），而**方案与取舍都已写清楚** —— 面试时讲"我知道该做什么、为什么现在不做"比"我做了"更值钱。
> **每行都留了"想做时从哪捡起来"**，正文在第五节。

| 编号 | 事项 | 为什么暂不做 | 想做时从哪捡 |
|---|---|---|---|
| **#5-P1** | Sentinel **热点参数限流**（按 `spuId` 差异化，替代整接口共享 QPS） | ⭐ **面试价值最高**（电商必问），但要 ~0.5 天 + 改代码部署 —— **若你还愿意留一个增量，只有它值得做** | §5-P1 · [[Sentinel能力补充计划]]（P0 已完成） |
| **#40-2** | 微服务自身 healthcheck（Step 2） | **隐藏前置**：要先放行 `/actuator/**` → **重建 11 个服务**；当前"假健康"问题用 #61 外部探活覆盖了 | §40（Step 1 已完成） |
| **#22** | CORS 收敛到网关（5 服务 `allowedOriginPatterns("*")` 加 `@Profile`） | 要改 5 个服务 + 重建；生产暴露面已由**网关显式白名单**覆盖 | §22 |
| **#51** | 容器 restart 策略改 `unless-stopped` | 要 **recreate 25 个容器**（注意 ES 分片）；实测证据（`on-failure` 不扛 daemon 重启）已留档 | §51 |
| **#53-①②④** | 网关重试 / 缩短 LB 缓存 TTL / 断路器 | 要**重启网关**（全站入口 1~2 分钟）；**③ 优雅下线已做完并验证 30/30 请求 0 失败** | §53 |
| **#45** | 统一 Dubbo 应用名（front/search/ams 撞名但无 provider） | 纯规范、当前**无实际风险**；**顺手重建某个服务时再改** | §45 |
| **#30 #39 #41 #15 #16 #26 #35** | 监控 / 日志 / CI-CD / K8s / TraceId / 网络 / Jackson 统一 | 都是"企业级完整度"项，**面试用嘴讲方案即可**（#35 可挑 JSON 安全讲 fastjson 漏洞史） | [[TODO中低优先级]] 对应 § |
| **#56** | 公开仓库暴露 IP / 拓扑 / 弱凭据事实 | ✅ **已正式决定"接受"**（公开仓库是简历作品集；公网端口已被安全组挡住、真实凭据未入库） | §56（结论已记录，**不再挂账**） |

---
## 📌 三、编号总登记表（权威 · 一行查一条）

> **本表只登记「高优先级：待执行 / 风险 / 安全 / 可实施」+「已完成指针」** —— 🟡 **中优先级**与 ⏸️ **暂缓/仅评估**的登记表与正文已外移 → **[[TODO中低优先级]]**（2026-09-11 拆分）。
> **⚠️ 编号永不重编号**（`docs/` 下 81 个文件、1600+ 处引用）。**图例**：🔴 待执行 / 风险 · ✅ 已完成 · 📦 已归档。

### A. 🔴 待执行 / 风险 / 安全（**当前真正要做的事**）

| 编号 | 一句话 | 状态 | 正文 |
|---|---|---|---|
| **#5-P1** | **Sentinel 热点参数限流**（秒杀按 `spuId` 差异化，替代整接口共享 QPS=20）—— 面试主菜 | 🔴 待做 | §5-P1 |
| **#22** | **CORS 收敛到网关**（5 服务 `allowedOriginPatterns("*")` 加 `@Profile("dev")` 限定） | 🔴 待做 | §22 |
| **#40-2** | **微服务 healthcheck 落 compose**（⚠️ 前置：先放行 `/actuator/**` 白名单，否则探针永远是"假健康"200） | 🔴 Step1 ✅ / Step2 待做 | §40 |
| **#45** | **统一 Dubbo 应用名**（front/search/ams 撞名但无 provider，加 `-dubbo` 后缀防未来踩坑） | 🔴 规范项 | §45 |
| **#51** | **容器 restart 策略改 `unless-stopped`**（实测 `on-failure` **不扛 daemon 重启** → 老机 20/21、新机 5/5 不恢复） | 🔴 P2 | §51 |
| **#53** | **网关重试 / 优雅下线兜底**（③ 优雅下线已实现；①②④ 需重启网关窗口） | 🔴 P2（部分） | §53 |
| **#54** | **RabbitMQ 凭据仍是 `guest/guest`**（服务侧变量名写错：应 `SPRING_RABBITMQ_*`） | 🔴 P2 | §54 |
| **#55** | SSH 暴露面 → ✅ **已评估·接受（2026-09-12 实测）**：**root 本就是锁定账号**（`passwd -S root` = `L`、shadow = `*` ⇒ 无密码可爆破）；`ecs-user` 保留密码登录（实测 `Failed password` **247 次 / 6 天**，密码强 + 学习用途 ⇒ 接受）；副产品：**密钥登录已打通** | ✅ 已评估·接受 | §55 |
| **#56** | 公开仓库暴露 IP/拓扑/弱凭据事实 | 🟡 P3（**已决定接受**） | §56 |
| **#57** | ✅ **只读核实完成（2026-09-12）**：**14 处差异全部是"服务器比快照新"**（9 张表的 `data_source` / `oms_order.order_type` / 4 处 `gmt_modified` / `ams_permission.value` / `seckill_message_retry` 整表）⇒ **0 处需要 ALTER** | ✅ 已完成 | §57 |
| **#65** | **普通订单库存扣减 MQ 链路整体失效** → `pms_sku.stock` 永不减少、下单无库存校验 | ✅ **已修复（代码+回归测试全绿，2026-09-12）· 待你部署 mall-order** | §65 |
| **#66** | Sentinel 面板上报缺口（8 服务未配 dashboard 地址） | ✅ 已修复验收 | §66 |
| **#61** | **外部端到端探活**（"21 容器全 Up、health 200，业务却挂了 24h"的根治） | ✅ **脚本已交付并真机验证（2026-09-12）· 待你挂 cron** | §61 |
| **#62** | mall-ai `/ai/chat/stream` 并发下 ~50% HTTP 500（`AccessDeniedException`：ASYNC/ERROR 二次派发被授权规则拒绝） | ✅ **已修复验收**（2026-09-12） | §62 |
| ~~**#64**~~ | ✅ **已修复并部署验收**（2026-09-12 晚）：预热 Job 改用 `seckill_spu.getId()`；预热日志现覆盖 **12/12** 个 SKU，且 11/12/13/14 的 key 已由 Job 按 DB 值**带 TTL** 重新预热（不再是永久 key） | §64 |
| **#69** | ✅ **已修复并部署验收**（2026-09-12 晚）：秒杀时把 `seckill_spu.id` 当 pms spu 用 → `incrementSales` **给"错误商品"加销量**（实测 `pms_spu.sales[4] 1→2→3`、`sales[6] 1→2`）。**两台实例**（老机 `csmall-seckill` + 新机 `csmall-seckill-2`）均已升级并 A/B 证伪 | §69 |
| **#67** | **#48 的剩余部分**：① 录像 🟡暂缓 · ~~② 正式造数~~ ✅ · ~~③ AI 并发压测~~ ✅（含晚间可选深化）· ~~④ `--with-seckill` 真跑~~ ✅ · ~~⑤ `--clean` Redis 精确清理~~ ✅ · ~~⑥ `sim_baseline` 基线比对~~ ✅ | 🟢 **5/6 完成**（2026-09-12 一天内做完；**仅 ① 录像暂缓**） | [[TODO中低优先级]] §67 |
| **#68** | ✅ **已关闭（2026-09-12）**：dump 已执行一次（`cs_mall_20260912_1452.sql.gz`）+ **机制固化**（`--require-dump` 不提供即拒绝开跑 / 新鲜度校验 / 自动登记 `dump_file`） | ✅ 已完成 | §68 |

| **#31** | **生产开启向量检索**（✅ **2026-09-12 可行性评估完成：三项前置实测通过**（额度 200 / ES `dense_vector` 已就位 / 启动自检+运行时降级均已上线）⇒ **改为 env 覆盖、免重建镜像**） | 🟢 **可实施（待你拍板开或不开）** | §31 |
| **#70** | 🆕 **库存扣减链路的两个真缺陷**（**#65 修复后暴露**）：① `mall-product` SQL **off-by-one**（买最后一件必失败）② `mall-order` 失败路径**无限重投**（DLX 永远收不到） | ✅ **已修复（2026-09-12）· 待部署 mall-product + mall-order** | §70 |
### D. ✅ 已完成 / 📦 已归档（**明细见 [[TODO已完成]] 与 [[文档索引]]**，此处只保证"**编号可查**"）

| 编号 | 一句话 | 明细 |
|---|---|---|
| **R1~R4** · **R7** | Redis 加固（`requirepass` / AOF / `maxmemory` 256mb / 自定义 conf）· 内存优化（21 容器 `mem_limit` + Nacos 降堆 + Swap 2G） | [[TODO已完成]] |
| **#6** · **#8** | Dubbo 应用名撞名（`mall-product` 混入 20880）· AI 预算按北京时间结算（UTC → Asia/Shanghai） | [[TODO已完成]] |
| **#9** · **#14** | Redis 主从 + 3 哨兵（选主 6.1s / 自愈 9.1s / 零丢失）· 秒杀主从切换防数据（P0 三层 + `order_type` 治本 + 方案Y + P1 对账 + **P2 `min-replicas-to-write`**） | [[TODO已完成]] §六 / §十三 |
| **#13** · **#46** | Nacos 开启认证 · nacos 数据卷挂载（重启不丢配置） | [[TODO已完成]] §十一 / §十三 |
| **#23** · **#24** · **#25** | 漏触发 `@Validated` 补全 · MySQL 强密码（43 位 ALTER USER 双 host）+ 3306 收窄 · JWT_SECRET 生产随机化 | [[TODO已完成]] §四 / §十 |
| **#29** · **#47** | 数据库每日备份（cron 02:30 + 保留策略）· 备份恢复演练（独立临时容器验证） | [[TODO已完成]] §十二 / §十三 |
| **#32** · **#34** · **#58** · **#59** | AI 导购 Agent 升级（P0 Function Calling + P1 流式 + 生产加固）· **AI 并发闸门**（`AiConcurrencyGuard` Semaphore 20，满即 429 不排队）· 模型名停用风险 + `thinking` 开关 + 配置可配化 · 硅基流动 402 → 充值解决 | [[TODO已完成]] §八 / §十四 / §十六·§十七 / §18.2 |
| **#33** · **#36** · **#63** | 双索引数据不一致（统一索引 + 搜索只读降级层）· 订单 DLX 死信 + `requeue` 限次 · ES 索引 mapping 与代码期望不符 | [[TODO已完成]] §九 / §18.1 |
| **#38** · **#42** · **#44** | Dockerfile 双份不一致清理 · 跨机"真集群"演示（Redis HA + 秒杀双实例）· Embedding Key 吊销 + 全仓库轮换 | [[TODO已完成]] §十三 / §18.2 |
| **#48** | Python 模拟数据 + AI 并发测试（**两层**：第一层 📦 已归档；第二层 → 见 **#67**） | [[TODO已完成]] §十九 · §20-七 |
| 🆕 **#62 #64 #66 #68 #69** | 2026-09-12 当天关闭的五项（**各自正文仍保留在第六节**，不在别处） | 第六节 §62 / §64 / §66 / §68 / §69 |

> ⚠️ **`#34` 特别说明**：它**没有独立正文**，只在"#2+#34"里出现过 —— 实际含义是 **AI 并发闸门**（`AiConcurrencyGuard`，Semaphore 上限 20，超出直接 429 不排队），属第二批已完成，明细 [[TODO已完成]] **§八**。本表把它补成一行可查。
> 📌 **本表只负责"编号不漏"**；**过程/原理**在 [[TODO已完成]] 与三个「实现与原理」册，**面试话术**在 [[面试准备]] 系列（10-1 ~ 10-11）。
### 📚 文档索引（已外移 → [[文档索引]]，2026-09-11）

> 📇 **本节已外移为独立文件** → **[[文档索引]]**（`docs/评估报告/文档索引.md`）：登记**全部方案 / 评估文档**的「有没有被跟踪、放在哪」（📍A 现存清单 · A2 切割产出的「一类问题」文档 · A3 前端文档表）。
> **⚠️ 纪律**：**新增任何方案 / 评估文档时，必须在 [[文档索引]] 登记** —— 未登记 = 漏跟踪；并在对应 TODO 条目里 `[[链接]]` 该文档（**双向索引**）。
> **本文件（TODO）只保留**：① **编号总登记表**（编号 → 一句话 → 状态 → 位置）② **未完成条目正文** ③ 执行路线图。**已完成明细** → [[TODO已完成]]；**文档记账** → [[文档索引]]。

## 📋 四、要做条目正文（#31 · #54 · #55 · #57 · #61 · #65）

### 31. 【AI】评估生产开启向量检索 embedding-enabled: true（2026-09-02 记录，待决策）

> ✅ **2026-09-11 前置已解除**：用户**已充值 10 元**，AI 用生产 key 实测 `POST /v1/embeddings`（`BAAI/bge-m3`）→ **HTTP 200 / `dims = 1024`**（详见 **#59**）。~~原 2026-09-10 "P0 级阻断"~~ 不再成立。
> 🔴 **但顺序有硬要求：先修 #63（ES mapping），再开本项。** 原因：线上 `cool_shark_mall_ai` 索引**根本没有 `semanticVector` 字段**（2026-09-11 二次复核确认为 dynamic mapping）→ 开关一开，向量**无处可写**；而修 #63 的"删索引 → 重建"动作会把正确 mapping（含 `dense_vector dims=1024`）建出来 → **#31 之后只需改配置 + 同步，零停机、不必再删索引**。规划与验证清单见 [[商品与秒杀扩容方案]] §十 / [[TODO第三批实现与原理-1]] §三·3.2（#31 实施顺序与验证清单 —— 未实施部分仍原样保留在该册）。

> **2026-09-02 新增（源自 09-AI模块 Q3 服务器复核）**：生产 `embedding-enabled: false`（ES 全文检索），向量检索 = 完整代码 + test 验证 + 预留开关。**用户倾向开启（原以为 BGE-M3 免费），待后续考虑**。⚠️ **2026-09-10 实测更正**：硅基流动免费额度**已不足**、`POST /v1/embeddings` 返 **402** → 见 **#59**。

**现状（服务器 + 代码实证，2026-09-11 复核更新）**：
- prod yml `embedding-enabled: false` + 注释"生产默认关闭，按需开启"；test 环境 true
- ~~ES `cool_shark_mall_ai` 索引实测无 `semanticVector` 字段、mapping 是 dynamic 的~~ ✅ **2026-09-12 复核更正：已由 #63 重建修复** —— 线上实测 `semanticVector: {"type":"dense_vector","dims":1024,"index":true,"similarity":"cosine"}` **已就位**（`dynamic` 问题同步修掉）⇒ **"必须先修 #63" 这个前置✅已完成；本项不再需要删索引、也不需要停机**
- .env 已有 `EMBEDDING_API_KEY`（硅基流动，**2026-09-11 实测可用：HTTP 200 / `dims=1024`**）；`AiProperties`/`EsIndexInitializer`/`VectorSyncServiceImpl`/`RagServiceImpl` 代码全就绪

**当时关闭的理由（2026-09-02；部分仍成立）**：① 20 条商品 IK 毫秒级且准，语义优势兑现不了 = 收益 0 ② 向量化依赖硅基流动外部 API = 多一个故障点 ③ 稳定优先（sync-auto-on-startup 要部署即用）④ 全量重同步几千条会限流耗时

**开启评估（2026-09-11 更新：前置已解除，但读码发现一个必须先补的缺口）**：
1. **先修 #63**（删索引 → 重建 → 拿到含 `dense_vector(1024)` 的正确 mapping）—— **跳过这步会白开**
2. prod yml 改 `embedding-enabled: true` → `recreate` mall-ai（~45s）
3. `sync-auto-on-startup` 全量向量化（19 条，成本可忽略：`embedding-price-per-million: 0.0`）
4. 验证：**带 `semanticVector` 的文档数 = 19** + `/ai/search` 语义召回（"学生党性价比"等）
5. 🔴 **风险（2026-09-11 读码确认）：目前没有真正的降级链路** ——
   - `RagServiceImpl.ask():82-89`：`embeddingEnabled=true` 时**直接** `embed()` → `vectorSearch()`，**`embed()` 抛错会一路冒到接口** → **外部 API 挂了 = 搜索直接报错**，不是降级；
   - `vectorSearch():352-355`：catch 里日志写 **"ES 向量检索失败，降级到全文检索"**，但**实际 `return List.of()`** → **日志与行为不符**：检索变空、回答变成"未检索到相关商品信息"，**排查时会被这条日志误导**；
   - ⇒ ✅ **已于 2026-09-11 补齐（用户授权"顺便处理"）**：`ask()` 改为走 `vectorSearchWithFallback()`（embedding 失败 / 向量检索失败 / 结果为空 **三种都回落 `fullTextSearch()`**）；`vectorSearch()` 的误导日志已改成"是否回落由调用方决定"；`syncAll`/`syncSpu` 的向量化失败**降级为"仅全文索引"**（商品照旧可被 BM25 搜到）并**在汇总里显式暴露降级条数**。**56 项单测全绿**（`mvn -o -pl mall-ai/mall-ai-webapi -am test`）→ 详见 [[问题解决--外部依赖的降级与可替换性]] §二 / §三。

> ✅ **2026-09-12 可行性评估完成（三项前置全部实测通过 ⇒ 可实施，且比原计划更省事）**：
> 1. **额度**：用生产 key 实测 `POST /v1/embeddings`（`BAAI/bge-m3`）→ **HTTP 200 + 返回真实向量**（不再 402）⇒ **#59 充值已生效，外部依赖可用**。
> 2. **ES 地基**：线上已有 **`semanticVector: dense_vector(dims=1024, index=true, similarity=cosine)`**（#63 重建时建好）⇒ **无需删索引、无需停机**；当前 `_count?q=semanticVector:*` = **0 / 19**（开关关着，符合预期）。
> 3. **安全网已在线上**：启动日志实证 `EmbeddingSelfCheck - Embedding 启动自检：跳过（embedding-enabled=false…）`（2026-09-12 06:44）⇒ **翻开关后启动会真的做维度探针**（维度不符则**启动失败**并给可操作报错）；且部署 jar 时间（**09-12 03:33 UTC**）**晚于**修复提交 `9122e5d`（09-11 08:06 UTC）⇒ **运行时三支降级（`vectorSearchWithFallback`）也已上线**。
>
> **✅ 执行方案（改进：改用环境变量覆盖，免重建镜像/免重推 jar）**
> - `.env` 加 `AI_EMBEDDING_ENABLED`；compose 的 `mall-ai` 透传 `COOXIAO_AI_EMBEDDING_ENABLED: ${AI_EMBEDDING_ENABLED:-false}`（Spring Boot relaxed binding 覆盖 jar 内 yml）⇒ **开/关都只 `docker compose up -d mall-ai`（~45s），回滚同一条命令**。
> - **验证三步**：① 启动日志 `Embedding 启动自检：通过`；② `sync-auto-on-startup` 后 ES `_count?q=semanticVector:*` = **19**；③ `/ai/search` 语义召回抽查（如"学生党性价比"）+ **旧查询回归**（确认 IK 侧没退化）。
>
> **⚠️ 开启后的真实代价（诚实边界）**：① 每次检索**多一次外部 API 调用**（embedding 按量计费、单价极低；与 DeepSeek 的 2 元/日预算**不是同一账**）；② **排序会变**（语义召回 ≠ IK 全文）⇒ **录像前必须回归**几条演示查询；③ 可用性多一个外部依赖（已有三支降级兜底，最坏=回落全文检索）。
> **📌 建议**：**开**（简历/面试价值：从"代码写了、开关关着"变成"线上真在跑 RAG 向量检索"），但**在录像定稿前完成并回归**，避免演示期间排序变化。

**面试价值**：开 = 完整 RAG 链路真实运行；关 = 讲"按需开"的工程判断——两者都可讲；决策点 = 外部 API 稳定性能否接受（**2026-09-12 实测：可接受**）

---


---


### 54. RabbitMQ 生产凭据仍是默认 `guest`（安全）

 🔴 **P2（2026-09-09 推送前敏感数据审查发现）**：生产 RabbitMQ 只有 `guest` 一个用户（`tags=[administrator]`），且**允许非本机登录**——实测 `rabbitmqctl list_connections` 显示来自 `172.18.0.18`、`172.18.0.19`、**`172.29.193.240`（新机）** 的连接用户均为 `guest`；5672/15672 监听 `0.0.0.0`。**公网已被安全组挡住**（实测 blocked），但**同 VPC / 同安全组内任何实例**都能用公开的默认值 `guest/guest` 拿到 administrator 权限（读写所有队列、经 management 插件改配置）。根因：compose 里服务侧注入的是 `RABBITMQ_USERNAME/PASSWORD`（**Spring Boot 不读这两个名字**）→ 服务实际用 Spring Boot 默认值 `guest/guest`。

**方案**：① `.env` 设强密码 + RabbitMQ 侧 `rabbitmqctl add_user/change_password/set_user_tags/set_permissions`（⚠️ `RABBITMQ_DEFAULT_*` 只在**首次初始化**生效，数据卷已存在时无效）→ 删或禁用 `guest`；② compose 服务侧改用 **`SPRING_RABBITMQ_USERNAME` / `SPRING_RABBITMQ_PASSWORD`**（当前名字是错的）；③ 可选 `loopback_users.guest = true` 恢复默认限制。

**注**：`.env.example` 里的 `RABBITMQ_PASSWORD=guest` **不是泄露**（公开默认值），但**等于生产无密码**；**且仓库是公开的**（`github.com/yunxuan4309/csmall`，`private: false`）→ 这个事实等于公开写明了"生产 MQ 用 guest"。

**✅ 可执行操作单（2026-09-12 补 · 分两步，第一步零回归）**

**第 1 步：让服务"认得出"新凭据（纯配置补变量，部署后行为与现状等价）**
- ✅ **仓库侧已改**（`deploy/docker/docker-compose.yml`，2026-09-12）：3 个用 MQ 的服务（`mall-order` / `mall-seckill` / `mall-seckill-2`）**都补上** `SPRING_RABBITMQ_USERNAME` / `SPRING_RABBITMQ_PASSWORD`，并**保留**原有 `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD`。
- ⚠️ **为什么两套都要（2026-09-12 读码核实，很重要）**：`mall-order/mall-order-webapi/src/main/resources/application-prod.yml` **没有任何 rabbitmq 段** ⇒ 它只认 Spring Boot 标准的 `SPRING_RABBITMQ_*`（**此前它永远用默认 `guest/guest`，与 `.env` 无关**）；而 `mall-seckill/.../application-prod.yml:73-74` 写的是 **`${RABBITMQ_USERNAME:guest}` 占位符** ⇒ 它只认 `RABBITMQ_*`。**只改一套，另一个服务就会掉线**。
- **你要做**：把 `deploy/docker/docker-compose.yml` 同步到**两台** → `docker compose up -d mall-order mall-seckill mall-seckill-2`（~1 分钟）→ 验证功能无损（下单后仍出现 `订单库存扣减完成`）。

**第 2 步：真正换掉 guest（强密码 + 删默认用户）**
```bash
# ① 生成强密码写入两台 .env（ecs-user）
RABBITMQ_USERNAME=cs_mq_admin
RABBITMQ_PASSWORD=$(openssl rand -base64 24)
#    ⚠️ RABBITMQ_DEFAULT_* 只在"首次初始化空数据卷"时生效 —— 数据卷已存在，必须用 rabbitmqctl：
# ② 建用户 + 授权（在跑 rabbitmq 的老机执行）
docker exec csmall-rabbitmq rabbitmqctl add_user "$RABBITMQ_USERNAME" "$RABBITMQ_PASSWORD"
docker exec csmall-rabbitmq rabbitmqctl set_user_tags "$RABBITMQ_USERNAME" administrator
docker exec csmall-rabbitmq rabbitmqctl set_permissions -p / "$RABBITMQ_USERNAME" ".*" ".*" ".*"
# ③ recreate 三个 MQ 服务，让它们用新凭据
docker compose up -d mall-order mall-seckill mall-seckill-2
# ④ 验证：三条连接的 user 都应是新用户
docker exec csmall-rabbitmq rabbitmqctl list_connections user peer_host
# ⑤ 确认已无 guest 连接后，才删默认用户
docker exec csmall-rabbitmq rabbitmqctl delete_user guest
# ⑥ 复验：再重启一次服务，确认仍能连（排除"只在新容器首次生效"的假象）
```
⚠️ **顺序纪律**：**先建新用户 → 再切服务 → 最后删 guest**；反过来会让服务侧断连、消息堆积。
🔙 **回滚**：`.env` 改回 `guest` → `docker compose up -d` 三个服务 → `rabbitmqctl add_user guest guest && rabbitmqctl set_user_tags guest administrator && rabbitmqctl set_permissions -p / guest ".*" ".*" ".*"`。
📌 **另外两个可选收尾**：① `loopback_users.guest = true`（**2026-09-12 实测当前 `loopback_users=[]`** ⇒ guest **可远程登录**，这正是风险来源）；② 5672/15672 只绑私网（当前监听 `0.0.0.0`，公网靠安全组挡）。 


### 55. SSH 暴露面 → ✅ **已评估·接受并收口（2026-09-12）**

> ✅ **2026-09-12 实测复核与收口决策（本节以此为准）**
> - **root 部分：风险不存在。** 两台实测 `sudo passwd -S root` = **`root L`**、`/etc/shadow` 第二字段 = **`*`** ⇒ **root 账号被锁定、没有可用密码**（阿里云创建实例时只给了 `ecs-user` 密码）。因此 `PermitRootLogin yes` 是**一扇没有锁的门**，原结论"公网可**直接暴力破解**"对 root **不成立** —— 我此前**照抄旧结论、没先验证严重度**，此处更正。
> - **`ecs-user` 部分：确实有爆破噪音，已量化。** 老机 `/var/log/auth.log`（**2026-09-06 起，约 6 天**）：**`Failed password` 247 次**（≈40/天）+ **`Invalid user` 80 次**；这**不含我方探测**（我方的探测在日志里是 `Connection reset ... [preauth]`，另一种形态）。
> - **决策（用户 2026-09-12）**：**接受现状**（密码强度高 + 学习用途服务器）⇒ **本条关闭为「已评估·接受」**。
> - **副产品（有实际收益）**：`csmall_ecs_key` 的公钥已装入**两台** `ecs-user` 的 `authorized_keys`（指纹 `SHA256:UCv9+VStmzsFj52cguuGj+Dsr50dsat3XqOapwY1eqY`，与本地私钥一致；落地证据 `/tmp/keyauth-evidence-OLD.txt` / `-NEW.txt`）⇒ **今后可用密钥免密登录**（本为"关密码登录"做准备，虽最终没关，密钥通道留下了）。
> - **当前文件状态**：老机 `PermitRootLogin` 已改为 `prohibit-password`（L42/L134，**无害保留**，属纵深防御）；新机**未改**（root 已锁定，无必要）。`PasswordAuthentication` **两台都保持 `yes`**。
> - **遗留物**：`/etc/ssh/sshd_config.bak-2026-09-12`、`bak2-2026-09-12`（留作记录，可随时删）。
> - **若将来要收紧**（两条路，现在都不必做）：① 关 `PasswordAuthentication`（**密钥已可用 ✅**）；② 安全组把 22 收紧到固定来源 IP（更彻底，但换网络会连不上）。
> - ⚠️ **两条判据教训（下次别重犯）**：① **Ubuntu 24.04 单元名是 `ssh` 不是 `sshd`**，且是 **socket 激活** —— 改端口 / `Match` 需 `systemctl daemon-reload` + `systemctl restart ssh.socket`（[Ubuntu bug 2069041 官方回复](https://lists.ubuntu.com/archives/foundations-bugs/2024-June/517043.html)）；generator 处理不当会让 sshd 以默认配置运行（[bug 2076023](https://lists.ubuntu.com/archives/foundations-bugs/2024-August/520089.html)）。② **验证"某指令是否生效"只能用 `sshd -T -C user=…,host=…,addr=…`（或真去试一次）**；"客户端看到的认证方式列表"**只反映 `PasswordAuthentication`，对 `PermitRootLogin` 不敏感**（我就误判在这上面）；"sshd PID 是否变化"也**无效**（sshd 是 `execve` 重执行、PID 不变）。

 🔴 **P1（2026-09-09 原始评估 · 严重度已更正 · 仅留档）**：两台 `sshd_config` 均为 `PermitRootLogin yes` + `PasswordAuthentication yes`（无 drop-in 覆盖），而安全组 **22 端口对 `0.0.0.0/0` 开放** → 公网可**直接暴力破解**（阿里云 ECS 是扫描最密集的目标之一）。**方案**：① `PasswordAuthentication no`（`ecs-user` / `ai-*` 均已配置密钥登录，不影响使用）；② `PermitRootLogin prohibit-password`；③ 可选：安全组把 22 收紧到固定来源 IP。**⚠️ 操作顺序**：先确认密钥登录可用（`ssh -i <key> ecs-user@<ip>`）→ `sshd -t` 校验语法 → `systemctl reload sshd`（**reload 不断开现有连接**，比 restart 安全）。

**⛔ 以下操作单已作废（2026-09-12 收口）· 仅留档**：其中 `systemctl reload sshd` 的**单元名是错的**（Ubuntu 是 `ssh`），尖括号占位符也**不可直接粘贴**；正确做法与权威判据见上方"实测复核与收口决策"。

```bash
# 0) 先确认“密钥登录”可用（另开一个窗口验证，别关当前会话！）
ssh -i <你的key> ecs-user@<老机IP> "echo key-ok"
ssh -i <你的key> ecs-user@<新机IP> "echo key-ok"

# 1) 备份原配置 + 落盘加固（两台各执行一次）
sudo cp -a /etc/ssh/sshd_config /etc/ssh/sshd_config.bak-$(date +%F)
sudo sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin prohibit-password/' /etc/ssh/sshd_config
sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
sudo sshd -T | grep -Ei 'permitrootlogin|passwordauthentication'   # 期望：prohibit-password / no

# 2) 语法校验（必须通过才继续）
sudo sshd -t && echo SYNTAX-OK

# 3) reload（不断开现有连接，比 restart 安全）
sudo systemctl reload sshd

# 4) 验证：另开窗口确认密钥登录仍可用；再试密码登录应被拒
#    ssh ecs-user@<IP>   → Permission denied (publickey)
```

⚠️ **顺序纪律**：`sshd -t` 不过**绝不 reload**；reload 后**先确认密钥能进**再关当前窗口。
🔙 **回滚（一条命令）**：`sudo cp -a /etc/ssh/sshd_config.bak-<日期> /etc/ssh/sshd_config && sudo systemctl reload sshd`。
⚠️ **可选第三步**：安全组把 22 收紧到固定来源 IP（比改 sshd 更彻底）—— 但会让你换网络时进不去，**演示/面试期间建议先不动安全组**。
✅ **做完请在登记表把 #55 标为已完成**（并把 `sshd -T` 的实际输出贴一行到本条）。 


### 57. 数据库 Schema 漂移：服务器是否已执行 ALTER 待核实

 🟡 **P3（2026-09-10 文档核查发现，来源 [[数据库Schema漂移审计]]）**：该审计（2026-08-04；本地 MySQL 39 表 × mall-pojo 32 实体 × `database/` 34 个 SQL 三方对比）结论是"**本地已全部修复**（企业级升级过程中已补列），但**服务器部署前需执行 [[本次修改部署指南--2026-08-04]] 同款 ALTER**"。**待办**：① 用报告里的对比清单在服务器 `information_schema` 上做**一次只读核对**；② 若有差异 → 生成 ALTER 并在低峰执行；③ 核对完回填两份文档状态。

🆕 **2026-09-11 又发现 2 处漂移（同属本条目范围，本次一并登记）**：① **`cs_mall_seckill.seckill_message_retry` 在生产存在（79 行）但 `database/` 目录里没有对应 DDL 文件**；② **生产 `ams_permission` 表多出一列 `value`（全 NULL），DDL 里没有该列**。两处均**未追根因**，待并入本条的只读核对清单。

**✅ 只读核对结果（2026-09-12 完成 · 结论：无需任何 ALTER）**：

- **方法**：服务器 `information_schema`（实际事实）↔ `database/*/*.sql`（仓库快照）**逐表逐列 diff**（脚本落在 `work/schema-diff.ps1`，服务器快照 `work/schema-server-dump.txt`，均本地未入库）。
- **表级**：服务器 **30** 张业务表（不含 `undo_log` / `flyway_schema_history`）× 快照 **29** 张 → **唯一差异 = `cs_mall_seckill.seckill_message_retry`（服务器有、快照无文件）**；**快照里没有任何"服务器上不存在"的表**。
- **列级**：**14 张表有差异，全部是 `ONLY_ON_SERVER`（服务器多列），`ONLY_IN_DDL` = 0** ⇒ **服务器从不缺列**，原担心的"服务器未执行 ALTER"被**证伪**：

| 服务器多出的列 | 涉及表 | 来源（已核实） |
|---|---|---|
| `data_source`（造数标识） | **9 张**：`oms_cart` / `oms_order` / `oms_order_item` / `oms_payment_record` / `res_upload_record` / `success` / `ums_login_log` / `ums_user` / `seckill_message_retry` | **#48**（各模块 Flyway `V6` 迁移加列） |
| `order_type` | `oms_order` | **#14-P0 治本**（秒杀订单类型） |
| `gmt_modified` | **4 张**：`ams_admin_role` / `ams_role_permission` / `pms_brand_category` / `pms_category_attribute_template` | 企业级升级补列（本地已补、快照未导出） |
| `value` | `ams_permission` | ⭐ **`Permission.java:38` 实体本来就声明了它**（`insertPermission` 不写该列 ⇒ 15 行全 NULL 合理）⇒ **是快照缺列，不是"生产多列"** |
| 整表 11 列 | `seckill_message_retry` | ⭐ **Flyway `V5__seckill_message_retry.sql` 建的**（`V6` 再加 `data_source`，与线上列逐一对上）⇒ **是快照缺文件，不是"线上长出来的表"** |

- **结论**：**生产 = 最新（与实体/迁移一致）**，`database/` 导出**滞后 14 处** ⇒ ① **不需要任何 ALTER**；② 原登记的"疑似历史残留、删不删要追根因"**方向反了** —— `value` 是实体字段，**保留不动**（删了反而与实体冲突）。
- **遗留（可选 · 纯仓库文件、零生产风险）**：刷新 `database/` 导出（补 1 个文件 + 13 个文件的列），消除"快照滞后 ⇒ 下次三方对比又误报"的坑。 


### 61. 【监控】外部端到端探活（防"静默故障"）🟡 P2（2026-09-10 抢修衍生）

> **来源**：2026-09-10 生产故障（nginx 静态上游 IP 缓存 → 网关重建后**全站 API 502 约 24.5 小时无人发现**）。原理与排查链见 [[问题解决--服务注册与网关路由]] **问题 2**。

**✅ 脚本已交付并真机验证（2026-09-12）**：`deploy/scripts/ops/e2e_probe.py`（纯标准库 Python3，无需 pip）。

- **它刻意不查 `/actuator/health`** —— 那次故障里每个组件、每个 health 都是 200，坏的是**组件之间那条路**；所以本探针只按**真实业务入口**打（nginx:80 → 网关 → 具体服务）。
- **必须跑在被测系统之外**（新机/本机），跨机走**内网私网地址**（同 VPC 不计费、不限速）：
  ```bash
  python3 e2e_probe.py --base http://172.29.193.239 --gateway http://172.29.193.239:10087
  # 深度模式（带浏览器里复制的 JWT）：会真的打到 MySQL/Redis/ES，验证"业务数据可用"
  python3 e2e_probe.py --base http://172.29.193.239 --token '<JWT>'
  ```
- **两条实测得到的判据（决定了脚本怎么判）**：① 🔴 **本项目"鉴权失败"是 HTTP 200 + body 里 `state=401`**（不是 HTTP 401）⇒ **只看状态码的监控会把"没登录"当"一切正常"，必须解析 body**；② **所有业务 API（`/front/*` `/seckill/*` `/ai/*` `/pms/*` `/search/*` `/admin/*`）都要登录**，未带 token 时探针证明的是「**nginx 路由 + 网关 + 鉴权链**」这一层（正是那次坏掉的层），带 token 才验业务数据。
- **判定规则**：连接失败/超时/**502/503/504** → FAIL；API 路径却返回 **HTML（SPA 兜底）** → FAIL（路由漏配）；`state ∈ {500,...}` → FAIL；`state=401/403` → PASS（未带 token 时）；`--token` 下仍 401 → FAIL（token 过期）。
- **真机验证（已做）**：在新机跑 → **9 项全 PASS**（`✅ 全部通过`）；失败分支也已实测（早期版本 3 项 FAIL 时 ssh 退出码为 1）⇒ 可直接接 cron 告警。
- **建议的 cron（跑在新机 · 每 5 分钟 · 失败发邮件）**：
  ```bash
  */5 * * * * /usr/bin/python3 /tmp/e2e_probe.py --base http://172.29.193.239 >/tmp/e2e.log 2>&1 || \
    tail -n 20 /tmp/e2e.log | mail -s "[CoolShark] 端到端探活失败" you@example.com
  ```
  ⚠️ 探针脚本本身建议纳入仓库（已入库 `deploy/scripts/ops/`），cron 里用绝对路径指向部署副本。
> **痛点一句话**：**"21 个容器全 Up、网关 `/actuator/health` = 200，业务却全挂了 24 小时"** —— 内部健康检查（容器级 / 服务级）**天然抓不到**"路由层地址漂移""证书过期""上游 DNS 变了"这类故障，因为**每个组件自己都是健康的**。

**要做什么（最小可用版，先不做全套 Prometheus）**：

| 层次 | 探针 | 判据 |
|---|---|---|
| ① 首页可达 | `curl -fsS http://127.0.0.1/` | 200 且返回 HTML |
| ② **业务闭环**（关键） | `curl -fsS -X POST /user/sso/login`（测试账号） | 返回 `state:200` 且含 `tokenValue` |
| ③ **经真实入口链路** | 探针**必须走公网入口/nginx**，不能只探容器端口 | 才能覆盖"nginx → 网关 → 上游"整条链 |
| ④ 失败即告警 | 连续 2 次失败 → 写日志/告警（邮件、钉钉、或先落 `/var/log`＋可选 cron 检查） | 让故障在**分钟级**被发现，而不是靠人撞见 |

**与既有条目的关系**：**#40**（微服务 actuator healthcheck）解决"**容器内**的健康可见性"（且当前 `/actuator/health` 还被自身 SSO 拦成"假健康"）；**#61 是外部的、端到端的**。两者互补，**不重复**：内部探针擅长发现"进程/依赖坏了"，外部探针擅长发现"**整条链路里某一环地址/配置漂移了**"。

**成本**：一条 cron（或 systemd timer）+ 一个 bash 脚本，~1h。**风险**：探针要用测试账号，注意别把真实凭据写进脚本（用受限账号或只探到"401 说明链路通"级别）。

---


### 65. 🔴 普通订单的库存扣减 MQ 链路失效（`pms_sku.stock` 永不减少）

 🔴 **P1（2026-09-11 由 #48 校准实测发现；既有缺陷，与造数无关 —— `mall-order` 本次重建的源码里这两个文件我们一行未改，只加了 Flyway SQL）**：

**优先级理由**：不是安全问题、不丢数据，但**核心下单链路的一个关键副作用整条失效**，且**下单完全没有库存校验**（可无限超卖）→ 影响面试主菜"0 超卖"的可信度，故列 P1。

**现象**：4 笔订单**支付成功**（`state=3`、`gmt_pay` 有值），但 `pms_sku.stock` 与 `pms_spu.sales` **完全没变**（1456 / 83）。

**定位证据（逐环）**：① `OmsOrderServiceImpl:87` 注释原文"**库存扣减改为 MQ 异步处理**，不再需要 `@GlobalTransactional`" → 下单只发消息（`:137-138 rabbitTemplate.convertAndSend(ORDER_EX, ORDER_RK, orderItemMessages)`）；② 日志 `ListenerExecutionFailedException: Failed to convert message` → `NoSuchMethodException: No listener method found in OrderQueueConsumer for class java.util.ArrayList`；③ 重试耗尽进死信后，`OrderDlxConsumer` **同样不接受 `ArrayList`** → 再次致命失败 → **消息彻底丢失**；④ 本次启动日志内 `订单库存扣减完成`（消费者成功时的 INFO）出现 **0 次**，而 `No listener method found` **21 次**（= 7 笔订单 × 3 次重试）。

**根因**：`OrderQueueConfig:80` 给监听器配了 **JSON 反序列化**（注释写"解决 LinkedHashMap 转换失败"）→ 消息体被转成 `ArrayList`；而两个消费者的 `@RabbitHandler` 分别只接受 **`String`**（`OrderQueueConsumer:32`）与 **`Message`**（`OrderDlxConsumer:38`）→ **类型不匹配**，被错误处理器判为"致命转换错误"。

**后果**：① 库存永不减少（演示数据里"库存下降"这条曲线缺失）；② **下单完全没有库存校验** → 理论上可无限超卖，项目"100 并发 0 超卖"的卖点**需重新评估**；③ 死信告警链路同时失效（本该输出 `【MQ死信告警】` 的那条 ERROR 根本没执行）。

**修复方向（未实施，待用户决策）**：`OrderQueueConsumer` 入参改为 `List<OrderItemMessage>`（或去掉该监听器的 JSON 转换器、让它收 `String`）；`OrderDlxConsumer` 同理改为接收原始 `Message`/`byte[]`。⚠️ 需**重建 mall-order 镜像 + 重启**，属变更窗口事项。

**对 #48 的影响**：方案 §2.2.1「下单累加 `sales` / 扣库存 → 不可逆污染」**实测不成立** —— `sales` 只由**秒杀**链路累加（`incrementSales` 全仓唯一调用方是 `SeckillQueueConsumer:98`），普通订单本就不加；库存那条是**缺陷导致失效**而非设计。→ 普通订单造数**不会**不可逆消耗库存，**但不能依赖这个"幸运"**。

**🔎 只读验证（任何人可复现，无需改代码）**：① **消费者侧**：`docker logs csmall-order 2>&1 | grep -c "No listener method found"` → **21 次**（= 7 笔订单 × 3 次重试），而成功分支 `docker logs csmall-order 2>&1 | grep -c "订单库存扣减完成"` → **0 次**；② **数据侧**：订单支付成功后 `SELECT stock FROM pms_sku WHERE id=<skuId>`（实测 **1456**）与 `SELECT sales FROM pms_spu WHERE id=<spuId>`（实测 **83**）**均不变**，而 `oms_order.state=3` 且 `gmt_pay` 有值；③ **死信侧**：`docker exec csmall-rabbitmq rabbitmqctl list_queues name messages` 可见死信队列**持续堆积**（`OrderDlxConsumer` 同样类型不匹配 ⇒ 本该打印 `【MQ死信告警】` 的 ERROR 根本没执行）。

**✅ 修复完成（2026-09-12 · 代码改动 + 本地回归测试全绿；部署由用户执行）**

- **根因再澄清一步（比"类型不匹配"更准确）**：Spring AMQP 在「**类级 `@RabbitListener` + `@RabbitHandler`**」下，会先用消息转换器把载荷转成对象、**再按对象类型挑处理方法**。生产端发的是 `List<OrderItemMessage>` ⇒ 转换器写入 `__TypeId__ = java.util.ArrayList` ⇒ 消费端的 `String` 参数**挑不中**（`No listener method found ... for class java.util.ArrayList`）。
- ⚠️ **而"把参数改成 `List<OrderItemMessage>`"并不够** —— **本地回归测试实测**：`SmartMessageConverter.fromMessage(msg, methodParameter)` **不会按方法参数推断泛型**，集合元素会退化成 `LinkedHashMap`，跑到 `item.getSkuId()` 就抛 `ClassCastException`。**这个结论是跑出来的，不是推测**（第一版修复正是被这条测试拦下的）。
- **修复方案（采用本工程已被生产验证的写法）**：载荷改为**非泛型 POJO**（对照秒杀链路 `SeckillQueueConsumer(Success success, ...)`，线上正常）：
  1. **新增** `OrderStockMessage` —— 包装 `List<OrderItemMessage> items` 的 POJO；
  2. **生产端** `OmsOrderServiceImpl`：`convertAndSend(..., new OrderStockMessage(orderItemMessages))`；
  3. **消费端** `OrderQueueConsumer.process(OrderStockMessage message, ...)`：取 `message.getItems()`，并补**空集合守卫**（空则直接 ack，避免无意义重投）；
  4. **死信消费者** `OrderDlxConsumer`：改为**方法级** `@RabbitListener` + 原始 `Message` 参数（方法级没有"按类型挑方法"这一步 ⇒ 无论载荷是什么都能留痕告警）。
- **本地验证（已做）**：新增回归测试 `OrderQueuePayloadContractTest`（3 项：载荷必须绑定成 `OrderStockMessage` 且元素就是 `OrderItemMessage` / 参数类型回归保护 / 死信监听必须方法级）→ **`Tests run: 3, Failures: 0` + BUILD SUCCESS**。
  ⚠️ 同模块既有测试 `OmsOrderServiceImplTest` 有 5 个 `Could not initialize plugin: MockMaker` 错误 —— **已用 `git stash` 在改动前的代码上复跑取证：同样 5 个错误**（环境级问题，与本次改动无关）。
- **部署（用户执行 · 变更窗口）**：`mvn -o -B -pl mall-order/mall-order-webapi -am -DskipTests package` → 重建 `mall-order` 镜像 → `docker compose up -d mall-order`（**只动这一个服务**）。
  ⚠️ **发布前检查**：`order_queue` 与 `order_queue_dlx` **不能有积压**（**消息格式变了**：旧格式是裸 JSON 数组、新格式是 POJO 包装）→ `docker exec csmall-rabbitmq rabbitmqctl list_queues name messages` 确认为 0。
  ⚠️ **生产端与消费端在同一个 jar**（都在 mall-order）⇒ 不存在"只升一半"的问题。
- **部署后验收（3 条）**：① 下一单 → `docker logs csmall-order 2>&1 | grep -c "订单库存扣减完成"` **≥ 1**；② `grep -c "No listener method found"` **不再增长**；③ **`SELECT stock FROM pms_sku WHERE id=<sku>` 真实下降** —— 这条就是本缺陷修好的标志（以前永不减少）。



### 70. 🆕 库存扣减链路的两个真缺陷（2026-09-12 实测发现 —— **由 #65 修复后暴露**）

> **发现路径**：把 #65（类型契约）修好、部署并**真下了一单**后，第一次真正跑到"库存扣减"的业务分支，立刻撞出这两个问题 —— **#65 之前它们是死代码**（消费者根本没被调到）。

**缺陷 A：off-by-one —— 买"最后一件"永远失败** 🔴
- **文件**：`mall-product/mall-product-webapi/src/main/resources/mapper/SkuMapper.xml` → `updateStockById`
- **问题**：`update pms_sku set stock=stock-#{stock} where id=#{id} and **stock>#{stock}**` ⇒ 当 `stock == quantity`（买最后一件）时**匹配 0 行**。
- **实测证据**：订单 `41528a60-…`（sku `2084644661212008449`，`stock=1`、quantity=1）⇒ `1 > 1` = false ⇒ 消费者判"库存扣减失败"。
- **修复**：`>` → **`>=`**（与秒杀侧 `seckill_stock >= #{quantity}` 口径一致；**防超卖靠的就是这个条件**）。

**缺陷 B：失败路径无限重投（"限次重试"从未生效）** 🔴
- **问题**：`OrderQueueConsumer` 用 `basicNack(tag, false, requeueCount < MAX_REQUEUE)` 想表达"限次重试"，但 **classic 队列上 `basicNack(requeue=true)` 不会写 `x-death` 头**（`x-death` 只在"被死信"时加；只有 quorum 队列才有 `reason=requeued`）⇒ `countRequeue()` **恒为 0** ⇒ `0 < 3` 恒真 ⇒ **永久重投**。
- **实测证据**：**近 10 分钟重投 114 次**（约每秒一次，一直打 Dubbo 商品服务）；`order_queue` 长期积压 1 条、`order_queue_dlx` **恒 0**（⇒ **DLX 永远收不到**，告警链路形同不存在）。
- **修复**：① `rows==0`（= 这条消息**永远不可能成功** = **毒消息**）→ 抛 **`AmqpRejectAndDontRequeueException`** ⇒ 容器 reject(requeue=false) ⇒ **进 DLX**，由（已修好的）`OrderDlxConsumer` 打【MQ死信告警】留痕；② 其它异常**抛出**，交给容器 `retry.max-attempts=3` 重试，耗尽后 reject → DLX；③ **删掉两处手动 `basicNack`**。
- **附带修复（配置）**：`mall-order/application-prod.yml` 此前**整段没有 `rabbitmq`** ⇒ 容器是 **AUTO ack**，与代码里的手动 ack/nack **双确认**（实测 `channel error 406 PRECONDITION_FAILED unknown delivery tag`），且异常按默认 `defaultRequeueRejected=true` **无限 requeue**。现补齐（对齐 mall-seckill 的**已验证**配置）：`acknowledge-mode: manual` + `default-requeue-rejected: false` + `retry: enabled / max-attempts: 3`。

**⚠️ 同类风险（未修 · 已登记）**：`mall-seckill` 的 `SeckillQueueConsumer` 用的是**同一套** `basicNack(requeueCount < MAX_REQUEUE)` ⇒ 它的 `rows==0`（秒杀库存不足）**同样会无限重投**（虽然它配了 manual ack + retry，但**手动 nack 绕过了重试**）。修法与上面 ② 完全一致（改 1 处 + 加一句 throw）。**代价**：秒杀是**双实例**，必须**两台一起**重建（G14 纪律）。

**✅ 部署与验收（2026-09-12）**：两个服务已部署 —— 容器内 jar md5 与构建产物一致；那条积压消息**自然成功**（`stock` 1→0 · `order_queue` 归零 · `订单库存扣减完成` +1）；且从**线上 jar 里抽出**的 `SkuMapper.xml` 已确认是 `stock>=#{stock}` ✅。

**⚠️ 补修发现 C（同日 · 被"再检查一遍"抓出）：yml 是「影子配置」，压根没生效** 🔴
- **现象**：把 `acknowledge-mode: manual` 写进 `application-prod.yml` 后，**运行日志里仍是 `acknowledgeMode=AUTO`**，且成功 ack 之后紧跟一条 `channel error 406 PRECONDITION_FAILED unknown delivery tag`（**自动 + 手动双确认**）。
- **根因**：mall-order 在 `OrderQueueConfig` 里**自定义了 `rabbitListenerContainerFactory` bean**，`@RabbitListener` 用的就是它；而 **`spring.rabbitmq.listener.simple.*` 只作用于 Spring Boot 自动配置的那个 factory** ⇒ **yml 里写什么都没用**（= **G16"改到影子 key"的同类**：配置在 jar 里，却**没有任何代码去读它**）。
- **修复**：权威设置搬进**代码里的 factory** —— ~~`setAcknowledgeMode(MANUAL)`~~ + `setDefaultRequeueRejected(false)` + `RetryInterceptorBuilder.stateless().maxAttempts(3).recoverer(new RejectAndDontRequeueRecoverer())`；并把 yml 那段**删掉、换成指路注释**（避免以后再有人改错地方）。

**⚠️ 补修发现 D（同日 · 我对 C 的修法本身有坑，又被实测抓出）：MANUAL 模式下"抛异常"不会 reject** 🔴
- **现象**：改成 MANUAL 后，毒消息的重试上限**确实生效了**（3 次后 `Retries exhausted` + `ConditionalRejectingErrorHandler`，日志见 `RejectAndDontRequeueRecoverer`）✅ **不再死循环**；但 —— `order_queue` 出现 **1 条 `unacked`**、**`order_queue_dlx` 恒 0**、**告警不响** ❌。
- **根因**：**MANUAL ack 模式下容器不碰 channel** ⇒ 监听器"抛 `AmqpRejectAndDontRequeueException`"**不会**触发 reject ⇒ 消息**永远 unacked**（只在重启/断连时才重投）；而 `RejectAndDontRequeueRecoverer` **只有 AUTO 模式才真的 reject(requeue=false)**。
- **最终修复（本次定稿）**：**`AcknowledgeMode.AUTO`**（ack 交给容器）+ **监听器完全不碰 channel**（删掉 `basicAck`/`Channel`/`deliveryTag` 参数）+ `setDefaultRequeueRejected(false)` + retry advice。三条路径因此都收敛：成功 → 容器 ack ✅ · 毒消息 → 3 次重试后 reject ⇒ **DLX + 告警** ✅ · 瞬时异常 → 同上 ✅。
- **判据/设计教训**："**抛异常让容器处理**"这条思路**只在 AUTO 模式下成立**；选 MANUAL 就等于把 ack 的责任（含"失败也要 reject"）**全部**揽给业务代码 —— 两者不能各做一半，否则就是这次的"消息永远 unacked"。
- **判据教训（又一次）**：`ack_required=true` **不能**用来判断"是否 manual ack"（AUTO 模式下 Spring 同样是 autoAck=false）；**真判据是容器日志里的 `acknowledgeMode=`**。
- ℹ️ **对照**：`mall-seckill` **没有**自定义 factory（配置类里 4 个 bean 无 Factory）⇒ 它的 yml 那套**是生效的** ✅ ⇒ **"抄别人的配置"这次不成立 —— 必须先确认自己这条链路读的是哪个配置源**。

---

## 📦 五、暂不做条目正文（想做时从这里捡起来 · 要点与取舍都留着）

> **为什么暂不做**：这些**都要重建服务 / recreate 容器**，成本大于收益；但**方案已经写清楚** —— 面试时讲"我知道该做什么、为什么现在不做"即可。
> **以后想做了怎么办**：每节都留了「**要点 / 涉及面 / 验证 / 面试讲法**」，照着做就行；更细的背景在 [[TODO中低优先级]] 与各方案文档。

### 5-P1. Sentinel 热点参数限流（秒杀按 `spuId` 差异化）
- **要点**：当前秒杀 `QPS=10` 是**整接口共享** → 爆款与普通商品**互相误伤**；应按 `spuId` 区分（爆款 QPS=100 · 普通 1000），用 `ParamFlowRule`。
- **成本 ~0.5 天**（代码改动小，但要部署窗口）。⭐ **面试价值最高**（"热点参数限流"是电商必问），且与 **#4 跨机集群**天然衔接（集群化之后才谈得上限流精准）。
- 📄 完整方案（现状盘点 / 优先级 / 实施步骤 / 回滚 / 执行清单）→ [[Sentinel能力补充计划]]（其中 **P0 已完成**）。

### 22. CORS 收敛到网关（2026-08-28 评估）
- **现状**：网关 `CorsConfig` 已是显式白名单 ✅；但 **5 个服务**（ums/search/product/resource/seckill）的 `WebMvcConfiguration` 有 `allowedOriginPatterns("*")`，**没限定 profile ⇒ 生产也生效**（dev 直连才需要）；mall-ai 自带 CORS（SSE dev 直连 10010）；order 已是范例（写明"由网关统一处理"）。
- **做法**：给那 5 处 + mall-ai 的 CORS 加 `@Profile("dev")`（**不是删**）→ 重建 5 个服务；网关白名单不动；ai 路由的 `DedupeResponseHeader` 可删。
- **验证**：生产 `curl -i` 响应头只有一个 `Access-Control-Allow-Origin`；dev 跨域 + ai SSE 回归。
- **面试讲法**：CORS 是**浏览器机制** —— 生产收敛到网关，只有 dev 直连才需要服务端 CORS。

### 40. 微服务自身 healthcheck（**Step 1 ✅ / Step 2 未做**）
- **为什么需要**：compose 里**中间件全带 healthcheck**，但 **11 个微服务都没有** → `restart: on-failure` 只能拉起"进程崩溃"，**"服务起着、内部连不上 Nacos/DB/Redis"这种假活不会被重启**（Docker 认为它活着）。
- ✅ **Step 1 已完成**：12 个 webapi/gateway 的 pom 都有 `spring-boot-starter-actuator`，11 个 `application.yml` 都暴露了 `health,info`（代码侧就绪）。
- 🔴 **Step 2 的隐藏前置（2026-09-10 实测）**：微服务 `/actuator/health` 被**自己的 SSO 安全链**拦截 → 返回 **HTTP 200 + `{"state":401,"message":"您没有登录！"}`**（"**假健康**"）⇒ **`curl -f` 会永远通过，healthcheck 形同虚设**。必须先**把 `/actuator/health`（建议连 `/actuator/info`）加进各服务 `buildPermitAllMatchers()` 白名单**（→ 重建 11 个服务）；或退回 `nc -z localhost <port>`（只证明端口在听，价值低）。**推荐前者**。ℹ️ `gateway` 是唯一例外（无 SSOFilter，返回真的 `{"status":"UP"}`）。
- **面试讲法**："**进程活着 ≠ 服务健康**"，顺带讲"**探针自己也会说谎**"（200 但 body 是 401）。

### 45. 统一 Dubbo 应用名（front / search / ams）
- **现状**：这三者 `dubbo.application.name` 与 `spring.application.name` 相同（撞名），但**均无 `@DubboService` 暴露**（不注册 20880 provider）→ Nacos 里只有 HTTP 实例、`lb://` 安全 ⇒ **当前无实际风险，所以当时没动**（避免无谓回归面）。
- **做法**：三模块 dubbo 名加 `-dubbo` 后缀（prod/test 各 2 处对齐），与 order/ai/seckill/ums/product 一致 —— **防未来给它们加 provider 时重踩 #6**（加 provider 瞬间 20880 混入同名服务，`lb://` 立刻 500）。
- **面试讲法**：**按风险分级 + 前瞻记录** —— 发现撞名、当时无风险，于是记规范项而不是乱改。

### 51. 容器 restart 策略（可用性风险）
- **现状**：老机 21 容器实测 = **14 个 `no` + 6 个 `on-failure` + 1 个 `unless-stopped`**；新机 5 个**全是 `on-failure`** → 宿主/daemon 重启后基本不会自动恢复（老机已连续运行 42 天，所以从未暴露）。
- **关键认知（反直觉，已核实 Docker 官方文档）**：*"`on-failure` only prompts a restart if the container exits with a failure. It doesn't restart the container if the daemon restarts."* ⇒ **只有 `unless-stopped` 能扛住宿主/daemon 重启**。
- **连锁后果（新机更危险）**：从库 + 3 哨兵同时消失 → 老机 Redis 因 `min-replicas-to-write 1` **拒绝所有写**（`NOREPLICAS`），且哨兵全灭无法故障转移。
- **做法**：compose 全量改 `unless-stopped`（两台：老机 20 + 新机 5）→ **逐个** `up -d --force-recreate`（低峰、注意 ES 分片）；或加 systemd 单元在 docker 起来后执行 `docker compose up -d`。⚠️ recreate 25 个容器 = 变更窗口事项。

### 53. 网关重试 / 优雅下线（消灭停实例的 16s 失败窗口）
- **现象（2026-09-09 实测）**：`docker stop csmall-seckill-2` 后 **Nacos <1s 摘除**（优雅停机主动注销），但**网关本地 LB 实例列表 ~16s 才刷新** → 期间 round-robin 把一半请求打到死实例，**9 次探测 5 次 500**。
- ✅ **③ 优雅下线已实现并验证（2026-09-09）**：`PUT ...&enabled=false`（退出负载均衡但**继续服务**）→ 等 40s（> LB 缓存 TTL）→ `docker stop` ⇒ **30/30 请求 0 失败**；脚本 `deploy/scripts/graceful-stop.sh` 已入库。⚠️ 注意"注销 API 无效"（客户端心跳会立刻重新注册），必须用 `enabled=false`。
- ⏳ **仍待维护窗口**：① LoadBalancer retry / ② 缩短 `spring.cloud.loadbalancer.cache.ttl`（默认 35s）/ ④ 断路器 —— 三者都需**重启网关**（全站入口 1~2 分钟），作为"别人直接 `docker kill`"时的兜底 → 见 [[跨机集群实施执行清单-2026-09-09]] §7.7 / §7.7.1。

### 56. 公开仓库的信息暴露（🟡 **已决定接受**）
- **事实**：`github.com/yunxuan4309/csmall` 是**公开仓库**，而文档系统记录了公网/私网 IP、主机名、端口拓扑、安全组放行清单，以及"生产 MQ 用 guest"等事实。
- ✅ **决策：接受**（学习项目 + **真实凭据未入库** + 公网端口已被安全组挡住 + **公开仓库正好当简历作品集**）；缓解项已实测：生产 JWT / MySQL / Redis 的值均未出现在仓库。
- 📌 **不再挂账**。若将来改主意，两条路：① 仓库转私有；② 文档脱敏（IP → 占位符，牺牲可读性）。

---

## 🗂️ 六、已完成条目正文（留档：现象 / 根因 / 验收）

> **为什么还留在这里**：这五项是 2026-09-12 当天关闭的，其中"**现象 → 根因 → 验收**"是最常被面试追问的部分，所以正文不删；**过程复述已压缩**，完整证据链见 [[TODO已完成]] 与对应的 [[问题解决]] 文档。

### 62. `/ai/chat/stream` 并发下 ~50% HTTP 500（`AccessDeniedException`）✅ **2026-09-12 修复**（提交 `9aec75a`）
- **现象**：经网关 `POST /ai/chat/send` 偶发 **500**；mall-ai 日志 `AccessDeniedException: Access Denied` + `Unable to handle the Spring Security Exception because the response is already committed`。
- **根因（两半，缺一不可）**：`/ai/chat/stream` 用 `StreamingResponseBody`，业务在**异步线程**写完后触发一次 **ASYNC 二次派发**，那一刻容器线程上**已无认证对象** → `anyRequest().authenticated()` 拒绝；另一半是 **ERROR** 派发去 `/error`，而 JWT 过滤器是 `OncePerRequestFilter`（默认跳过 ERROR 派发）→ 匿名 → 同样被拒。
- **修法**：`ResourceWebSecurityConfiguration` 在授权链**最前面**加 `.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()`。⚠️ **只放行 ERROR 不够**（2026-09-10 的原分析只猜到了那一半）；默认 REQUEST 派发**仍然要求登录**，`/ai/**` 防匿名刷 Token 的收紧不受影响。
- **验收**：串行 20/20 ✅ 且 **ERROR 归零**；**20 并发硬失败 50.0% → 0**、成功率 60.49% → **100%**；100 并发 0 失败。部署核验：容器内 `app.jar` md5 `73fd2e68…`、`ResourceWebSecurityConfiguration.class` **8859 → 9086 字节**且含 `DispatcherType`/`ASYNC`。
- ⭐ **影响面审计（全仓）**：会触发 **ASYNC 二次派发**的写法（`StreamingResponseBody` / `SseEmitter` / `WebAsyncTask` / `DeferredResult` / `ResponseBodyEmitter`）**只有 `mall-ai`**；其余 7 模块 + sso 都有 `anyRequest().authenticated()` 但**没有任何异步/流式端点** ⇒ **本缺陷无法触发**（属潜伏，非现存故障）⇒ **决策：不为潜伏项重建 7 个服务**（代价远大于收益）。
- 📌 **纪律**：**任何模块将来新增流式 / 异步端点时，必须在它的 `ResourceWebSecurityConfiguration` 授权链最前面补这一行**；该服务因别的原因重建时也顺手补上。
- 📄 明细：[[AI并发测试方案]] §十 · [[问题解决--LLM链路的契约漂移与分层降级]]

### 64. 秒杀预热的 `spu_id` 语义冲突 → 4/12 个 SKU 从不被预热 ✅ **2026-09-12 修复**（与 #69 同一次变更）
- **冲突**：`seckill_sku.spu_id` 存的是 **`seckill_spu.id`（秒杀表内部 id）**（前端详情 / 限购按它查），而**预热 Job 用的是 pms `spu_id`** ⇒ 二者**只在碰巧相等时一致**。
- **生产证据（三重）**：① 数据 `seckill_spu.id`=1~6、其 `spu_id`=1,2,5,6,15,20 ⇒ **4 行错位**；② 日志只预热到 8 个 sku（`{1,2,5,6,26,27,35,36}`，26/27、35/36 是数值"撞车"被顺带覆盖）⇒ **11/12/13/14 从不预热**；③ Redis 里那 4 个 key **`TTL = -1`（永不过期）**，而其余 ≈83~112 秒 ⇒ 前者**只能**来自**不带 TTL 的写** = 每天 03:30 的 `SeckillReconcileTask` 对账 ⇒ **"能下单"是靠凌晨对账兜住的巧合**（不预热时下单 500：`没有该商品缓存信息(可能在真空期,等下一分钟再试)`）。
- **修法（一行）**：`SeckillInitialJob` 的 `findSeckillSkusBySpuId(spu.getSpuId())` → **`spu.getId()`**；⚠️ **随机码键 `getRandCodeKey(spu.getSpuId())` 故意不动** —— 读码确认详情接口收的就是 **pms 主键**，那处**本来就是对的**，改它反而引 bug。
- **验收**：预热覆盖 **8/12 → 12/12**；删掉 4 个永久 key 后 Job 在 **`05:59:00`** 打印 `11/12/13/14号sku库存数成功预热到缓存!`（该分支就是**带 TTL 的写**）⇒ 值取自 DB、键不再是永久 key；删前 Redis 值(100/80/40/25) 与 `seckill_sku.seckill_stock` 逐一相等 ⇒ 删旧键不丢数据。
- **扩容硬约束**：新增秒杀必须 **`seckill_spu.id == seckill_sku.spu_id == pms_spu.id`**；⚠️ **不要用后台接口新增**（MyBatis-Plus 给**雪花 id** → 预热永不生效）→ [[商品与秒杀扩容方案]] §三。
- 📄 明细：[[问题解决--代码与线上不一致的静默失效]] §三 · 面试版 [[10-6-问题解决（提炼版·契约与一致性）]] Q16

### 66. Sentinel 面板只收到 3 个服务的指标 ✅ **2026-09-11 已实施并验收**
- **现象**：面板里只有 `sso` 与 dashboard 自己有曲线（压浏览 URL 实测 **99.6 RPS** 也看不到任何曲线）。
- **根因**：只有 `mall-order` / `mall-seckill` / `mall-sso` 配了 `SPRING_CLOUD_SENTINEL_TRANSPORT_DASHBOARD`；而 4 个模块 yml 里写的是 `dashboard: ${my.server.addr}:8858`，compose 注入 `ALIYUN_SERVER_IP=nacos` ⇒ **解析成 `nacos:8858`（那里没有面板）→ 静默不注册**；其余 7 个模块 yml 连 `dashboard:` 都没有 ⇒ **`compose` 的环境变量才是"能注册"的真正开关**。另：`mall-gateway` 是框架层例外（jar 里**没有** `sentinel-gateway` 依赖 ⇒ 它的路由从来不是 Sentinel 资源，补地址也没曲线）。
- **修复**：compose 补 **8 处** dashboard 地址 → `up -d --force-recreate` 8 个服务 → `docker inspect` **11/11 服务均有该变量**、面板轮询 10 个客户端端点、最近 10 分钟 `Failed to fetch metric` = **0**、**用户目视确认曲线出现**（面板默认凭据 `sentinel`/`sentinel`）。
- ⚠️ **过程教训（已写入方案 §F4）**：**8 个服务同时 recreate 会造成"启动踩踏"** —— 启动耗时从 107~176s 涨到 **221~319s**、load 峰值 7.45 ⇒ **下次分批 2~3 个一批**。
- **旁路（不修也能演示）**：改看 **SkyWalking** —— `mall-front` 的 Load/Latency/Apdex 确实在动（实测 `Load 1520.846 calls/min` · `Latency 124.462ms` · `Apdex 0.983`）。

### 68. 造数前快照从未落地（可逆性缺口）✅ **2026-09-12 关闭**
- **问题**：脚本每次开局都打印"记得先 mysqldump 并把文件名写进 `sim_batch.dump_file`"，但**历次 7 个批次该列全为 NULL**；影子登记表能**精确删掉造出来的行**，却**删不回被累加的字段**（`pms_spu.sales` / `pms_sku.stock`）。
- **复核修正（表述要更准）**：**不是"没有备份"** —— 每日 **02:30 的 cron dump 一直在跑**（各约 49~57 KB，秒级完成）。真正的缺口是 ① 造数/秒杀**之前那一次**没做（拿不到"操作前"的确切快照点）② **文件名从未登记** ⇒ 事后对不上号。
- **风险点**：`--with-seckill` 会**真动** `stock`/`sales` 与 Redis 预热键；脚本的"秒杀后恢复"只按**脚本内快照**回写 —— 一旦中途失败（容器重启/网络断/脚本被杀），**只剩 dump 能救回来**。
- **修复（机制固化）**：脚本新增 **`--require-dump <文件名>` 快照闸门** —— 不提供就**拒绝开跑**；提供则校验**新鲜度**（`--dump-max-age-min`，默认 120 分钟）+ **自动写进** `sim_batch.dump_file`；只读入口（`--preflight`/`--verify`/`--compare-baseline`）与 `--clean` 不受约束；`--allow-no-dump` 可显式豁免（大声警告）。
- **端到端实测**：不带 → `🔴 拒绝开跑`（附"先跑 backup-db.sh 再带文件名"两步指引）；旧包 → `快照太旧（3628 分钟前，上限 120）`；名字不合规 → `文件名不合规`；合法 → `✅ 闸门通过` + 批次 `dump_file` 自动写入成功。已执行一次真实 dump：`cs_mall_20260912_1452.sql.gz`（**319 KB / 6 库** / `gzip -t` OK）。
- 📄 明细：[[Python模拟数据与数据隔离方案]] §五 第 3 步 · [[问题解决--生产造数的数据隔离与复核方法]]

### 69. 秒杀把 `seckill_spu.id` 当 pms spu 用 → 给"错误商品"加销量 ✅ **2026-09-12 修复 + 两实例部署 + A/B 证伪**
- **现象**：同晚 3 次秒杀真跑、两次命中 —— 秒杀**成功**、DB 三值看着都"回位"了，但**全库 `SUM(sales)` 每次都多 1**，差异落在**别的商品**上：`pms_spu.sales[4] 1→2→3`、`sales[6] 1→2` ⇒ 被写错的行 = "**另一个命名空间里的同号 id**"。
- **根因（一行）**：`SeckillQueueConsumer:98` → `dubboSeckillSpuService.incrementSales(sku.getSpuId())`；`seckill_sku.spu_id` 存的是**秒杀表内部 id**，而 `SeckillSkuServiceImpl:51` 的注释恰好写着"spuId 参数为 PMS 商品主键，需先映射到 seckill_spu 内部 id" ⇒ **列表路径做了映射，消费者路径没做**。
- **为什么一直没被发现（三条叠加）**：① `sales` 只是展示字段 → **不报错、不影响下单**；② 秒杀侧"恢复"只写**目标商品**那一行 → **永远碰不到**被写错的行；③ 脚本原 `seckill_verify` 只校验 `seckill_stock`/`stock`、**不校验 sales** ⇒ 打印"✅ 全部回位"的**假通过**。
- **修复**：`SeckillQueueConsumer` 先反查 pms 主键再 `incrementSales`（新增 `SeckillSpuMapper.findPmsSpuIdBySeckillId`）+ **顺带修掉同源的 #64**；构建 ✅、`MessageRetryTaskTest,RedisLockUtilsTest` **9/9 通过**。
- **部署教训（→ 已成纪律 G14）**：🔴 **第一次只升了老机 ⇒ A/B 不通过** —— 新机副本仍是旧镜像（compose 里 tag 写死日期），实测**副本消费了那条 MQ**（其日志 `05:48:38` 有"秒杀成功记录处理完成"），销量仍写到内部 id ⇒ **两台一起升**，并各自核 `docker exec … md5sum /app/app.jar`。
- **A/B 证伪**：采样恢复窗口内全量 `sales`：**4 次动作**（含 **sku 27：内部 5 → pms 15**，**非重合 id**）→ 销量只落在**目标商品**、脚本"非目标 spu"告警 **0 次**（修复前 3 次实测次次告警）；终态 `SUM(sales)` 回 **84**、`success` **59**、残留订单 **0**、三类锁 **0**。
- ⚠️ **残留**：历史错记的 `sales`（累计值）**无法自动纠正** —— 只能按 `success` 表重算或人工订正（本次只手工还原了我方测试造成的 +1）。
- 📄 明细：[[问题解决--代码与线上不一致的静默失效]] §三 · [[Python模拟数据与数据隔离方案]] §G（**G14**）

> 📌 **维护提示**：本文件 = **高优先级状态源**（要做 / 暂不做 / 编号登记 / 条目正文）。新增条目：高优先级写入第一节 + 登记表 A；中/低优先级写入 [[TODO中低优先级]]；完成后迁 [[TODO已完成]]。

---
## 🧭 七、历史批次摘要（瘦身 · 2026-09-12）

> 原本文档顶部用 **~55 行**逐批叙述 ①~⑮，与 [[TODO已完成]] / [[文档索引]] 大量重复；现压缩为下表 —— **每条都留指针**，明细随时可查。

| 批次 | 时间 | 交付要点 | 明细 |
|---|---|---|---|
| 第一批 | 08 月中 | 安全止血（#25 JWT / R7 内存 / #24 MySQL / R1~R4 Redis / #38 Dockerfile） | [[TODO已完成]] §四 |
| 第二批 | 08 月末~09 初 | 正确性与可演示（#33 搜索双索引 / #8 时区 / #36 DLX / #23 校验 / #14-P0+P1 / #5-P0 / **#2+#34 AI 闸门** / #13 Nacos 认证 / #29 备份 + 突发 #6 Dubbo 撞名） | [[TODO已完成]] §六·§八·§九·§十·§十一 |
| 第三批 | 09-09~09-10 | 跨机集群主线（#46 Nacos 卷 / #47 备份恢复 / **#4 秒杀双实例** / **#9 Redis 主从+3 哨兵** / #14-P2），阶段 0~5 全收口 | [[TODO已完成]] §十三 · [[问题解决--集群机制的验证（负载均衡·故障转移·剔除）]] |
| ⑨~⑬ | 09-10~09-11 | 生产故障抢修（nginx 静态上游 → **全站 API 502 达 24.5 小时**）· #32 AI Agent 全量（P0/P1/加固）· #48 方案设计与两层拆分 · #58 模型名停用 + 配置可配化 · 文档切割（8 篇「一类问题」+ 面试 10-1~10-5）· 归档 6 篇 | [[TODO已完成]] §十五~§十九 · [[问题解决--服务注册与网关路由]] |
| ⑭ | 09-12 白天 | #67 ② 正式造数（1000 行为 @5 QPS）+ ③ AI 并发压测首轮（顺带修掉 #62）· 按任务拆分方案文档（A 册 / B 册） | [[Python模拟数据与数据隔离方案]] · [[AI并发测试方案]] |
| ⑮ | 09-12 晚 | #67 ④⑤⑥ 全部收口 + ③ 可选深化 · **#69 / #64 修复并两实例部署** · #62 影响面审计 · 结构化消除 **G14** | [[TODO第三批实现与原理-2]] §七·§八 |
| **⑯** | **09-12 深夜** | **本批：结构重构（要做前置 / 不做后置）+ 收口决策 + #57 关闭 + #31/#54/#65/#61 推进** | 本文档 |

> 💡 **面试视角（原「推荐执行路线」的结论，保留）**：第二批交付了"**止血 + 正确性**"的完整实证；第三批 P0/P1 交付"**高可用 + 集群**"的机制实证（Nacos 持久化 → 备份可恢复 → 秒杀双实例 → 热点限流），**足够覆盖 90% 深挖问题**；其余项的价值在于"**我知道并讲得清**"，不在"我做了"。
> 📌 原「执行路线图」的优先级逻辑（**面试价值 × 成本 × 风险 × 与已做内容的衔接**）已被第一、二节的两张表取代 —— **新到本文件只需读前两节**。