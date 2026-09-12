# CoolShark 项目待办事项

> **创建日期**: 2026-05-13
> **最后更新**：2026-09-12（**第 ⑮ 批**：[[#67]] 之 ④⑤⑥ 全部收口 + **③ 可选深化**；并**修复并部署验收 #69/#64**、审计 **#62 影响面**、结构化消除 **G14**）
>
> **🆕 本批（2026-09-12 晚 · 第 ⑮ 批）**
> - ✅ **[[#67]] 全面收口**：④ 秒杀真跑（3 次，每次"自动恢复 + 独立复核"）· ⑤ `--clean` 补 **Redis 按坐标精确删** · ⑥ `--baseline`/`--compare-baseline` 落地 → **"基线 → 造数 → 秒杀 → 恢复 → 清理 → 比对"闭环 0 差异**
> - ✅ **③ 可选深化完成**：补 **75/150 档** + 量"**单次调用占槽**" ⇒ **定量解释了"两条链路成功 RPS 持平"**（异步池默认 8 是共同墙；调用数 1.8× 但占槽只 +24%）→ [[AI并发测试方案]] **§十一**
> - ✅ **#69 修复并部署验收**（秒杀 `incrementSales` 写错商品）+ **#64 一并修掉**（预热 Job 查错 SKU，现覆盖 **12/12**）：构建 BUILD SUCCESS · 测试 **9/9** · 🔴 **只升一台时 A/B 不通过**（新机副本仍是旧 jar、MQ 被它消费）⇒ **两台一起升级**后 A/B 通过
> - ✅ **#62 影响面结论**：全仓"会触发 ASYNC 二次派发"的写法**只有 `mall-ai`**，其余 7 模块**不可能触发** ⇒ **不做 8 服务重建**，改为写死纪律
> - 🆕 **结构化消除 G14**：仓库 compose 里副本**复用同一镜像** `csmall-mall-seckill:${MALL_SECKILL_TAG:-latest}`（差异只剩 `SW_AGENT_NAME`/端口）→ **一份 jar / 一个 tag / 两台同版本**；新增 `deploy/scripts/deploy-seckill-both.ps1`（`-CheckOnly` 只读逐台 + 跨台 md5 校验）
> - 🆕 **实测认知 G14~G16**（见 [[Python模拟数据与数据隔离方案]] §G）：**G14** 双实例版本不一致（"单实例部署成功"≠"修复生效"）· **G15** 预检拦下非法号段 · **G16** Nacos 热改改到**同名影子 dataId**（PUT 返回 true 但应用零变化）
> - 🆕 **两条部署纪律**：① 秒杀类（含 MQ 消费者）改动**两台一起升**、**逐台**核 `docker exec … md5sum /app/app.jar`；② **还原动作独立成步**（长编排里必须配"**看门狗**：先武装定时自动还原再动手"）
> - 🟡 **① 录像**仍暂缓（唯一未完成项）；✅ **[[#68]] 已关闭**：dump 已执行一次（`cs_mall_20260912_1452.sql.gz`）+ **机制固化**（`--require-dump`：不提供即拒绝开跑 + 新鲜度校验 + 自动登记 `dump_file`）
>
> **📦 第 ⑭ 批（2026-09-12 白天）**（保留原文，见下）
>
> **🆕 本批（2026-09-12 · 第 ⑭ 批：[[#67]] 推进）**
> - ✅ **③ 第二层「AI 并发压测」首轮完成**：内网 mock（自测 14/14）+ compose 透传 + Nacos `ai-chat` 热改 5→200（**已恢复**）+ **800** 用户池 + 阶梯 20/50/100 **两条链路都压** → 结果 [[AI并发测试方案]] §十；**顺带修掉生产缺陷 #62**（`/ai/chat/stream` 并发下 ~50% 500 = ASYNC 二次派发丢认证）；执行记录（11 个问题 + 解决方案）→ [[TODO第三批实现与原理-2]]；🧩 已提炼 **[[问题解决--压测结论的可信性（先证伪干扰项与工具自身）]]** → 面试版 [[10-10-问题解决（提炼版·压测可信性）]]
> - ✅ **② 「正式造数」完成**：批次 `sim_20260912_1233`（1 天 × **1000 行为** @ `--qps 5`，实测 **4.98 QPS** / **失败 0** / 登记 366 / 标记 539 / **按批次复核漏标 0**）→ [[Python模拟数据与数据隔离方案]] §五·§G（**G11**）· `deploy/scripts/sim/README.md` §八
> - 🆕 **脚本强化**：造数**提量 / 提 QPS** + **`--with-seckill`（含秒杀后恢复）** + **`--verify --batch X`（按批次复核）**；🔴 修掉"回填校验是**全局口径** ⇒ 多批次共存必误报"（**G11 / F6**）
> - 🟡 **① 「录像」用户决定暂缓**（手册保持"照着做就能录"的就绪态）→ [[演示录像操作手册]]
> - ~~🔴 **剩余（今晚做）**：④ 秒杀真跑 · ⑤ Redis 精确清理 · ⑥ `sim_baseline`~~ ✅ **当晚全部完成**（见下方第 ⑮ 批与 [[TODO中低优先级]] §67）；清单原文留在 [[TODO中低优先级]] §67「🗓️ 今晚执行清单」
> - 🆕 **新登记 [[#68]]**：**`sim_batch.dump_file` 从未落盘**（历次 **7** 个批次全为 NULL）→ 方案 §五 第 3 步的"**快照先行**"从未落实；**做 ④ 之前必须先补**（秒杀会真动 `stock`/`sales`，脚本内快照只挡得住"正常返回"，挡不住中途失败）
> - ✅ **[[#67]] ④⑤⑥ 同日晚全部收口**：秒杀**真跑 3 次**（批次 `1329`/`1333`/`1335`，每次"自动恢复 + 独立复核"）· `--clean` 补上 **Redis 按坐标精确删** · `--baseline`/`--compare-baseline` 落地 → **"基线 → 造数 → 秒杀 → 恢复 → 清理 → 比对"闭环 0 差异**
> - 🔴 **新登记 [[#69]]**：**秒杀给错误的商品加销量**（两次实测命中不同实例：`pms_spu.sales[4] 1→2→3`、`sales[6] 1→2`；根因 `SeckillQueueConsumer.java:98` 把 `seckill_sku.spu_id`（**秒杀内部 id**）直接喂给 pms 的 `incrementSales`）→ 生产数据被写到**错误的行**，与 **#64** 同源、不同路径
>
> **🆕 本批（2026-09-11）**
> - **⑬ 补册 1 瘦身**：《TODO第三批实现与原理-1》**966 → 184 行**（技术正文切割为 2 篇新「一类问题」+ 1 篇扩充，见 **§A2d**）
> - 🔴 **特别约束**：**未完成部分（#48 第二层 AI 并发压测 → ✅ 2026-09-12 完成；**#31 仍未实施**）原地保留、不许提前提炼** —— 薄索引里专设 **§三 ⏳ 未完成部分**
> - **⑫ 文档归档 3 篇**：《AI导购Agent实现详解》《AI模型名停用风险与thinking参数改造方案》《本地双实例锁验证报告-2026-09-09》**已归档**（对应 #32 / #58 / 跨机集群阶段 0 均已关闭，内容已提炼为「一类问题」文档）→ 文末归档区「🆕 2026-09-11 追加归档（第二次）」；同批**新增「归档触发条件」表**（#57 → Schema 审计 / #67 → Python 方案 / #64+扩容 → 商品秒杀扩容方案）
> - **⑪ #48 拆分归档**：第一层「模拟数据生成」+ 数据标识（专用列 `data_source`）+ 可观测展示工具链（`load_test.py` 两档 + [[演示录像操作手册]]）**已完成并归档**（→ 文末归档区 **六** + [[TODO已完成]] **§十九** + [[问题解决--生产造数的数据隔离与复核方法]]）（🔴 2026-09-12：旧引用 `原理-1 §十三` 已切割迁出 → 去向见该册 §一 切割索引）
> - ~~🔴 **#48 是两层方案，第二层「AI 并发压测」完全未开始**~~ ✅ **2026-09-12 更正：第二层已首轮完成** → 原"剩余 6 项"**另立 [[#67]]**（① 录像 🟡暂缓 / ② 正式造数 ✅ / ③ AI 并发压测 ✅ / ④ `--with-seckill` / ⑤ Redis 精确清理 / ⑥ `sim_baseline` 基线比对）；**同批衍生** **#65**（库存扣减 MQ 链路失效）与 **#66**（Sentinel 面板上报缺口，**已修复验收**）
> - 🆕 **按任务拆分方案文档**：《Python模拟数据与AI并发测试方案》→ **[[Python模拟数据与数据隔离方案]]**（第一层，986 行）+ **[[AI并发测试方案]]**（第二层，249 行），原篇**原地改薄索引**（58 行；**保留文件名**使全仓库 10 处 `[[引用]]` 零失效）
>
> **⑩ #32 AI 导购 Agent 升级「全部完成并归档」（2026-09-11）**
> - P0（Function Calling）→ **P1（流式 Agent + `compare_products`/`get_stock` + Redis 动作审计）** → **生产加固**（商品意图**首轮 `tool_choice=required`** / 提示词防"凭历史作答" / `get_stock` 回传 **`spuName`**）**全部上线并复验**
> - 证据：**56 项测试全绿** · 生产开关 `true` · 客户端连续 18 次全 200 · **2026-09-11 复核**（同步 `/ai/chat/send` = 200 且"第 1 轮调工具 → 第 2 轮收敛"；流式 `/ai/chat/stream` = 211 事件且 **`products`（行 10）早于首个 `chunk`（行 22）**；`ai:daily_cost` 正常记账）
> - → **本条目已从正文整段移入[[TODO已完成]] §二十（已完成条目归档区）**；归档明细 [[TODO已完成]] §十六（P0）/ **§十七（P1 + 加固）**、实现说明书 [[AI导购Agent实现详解]]
>
> **⑦ 🔥 生产故障抢修（2026-09-10 19:47 发现 / 19:58 恢复）**
> - 根因：nginx 静态解析上游主机名**只在配置加载时解析一次并永久缓存**（等价写死 IP）→ 网关容器 09-09 重建换 IP 后，nginx 把 `/user /front /ai` 全打到**被 mall-ai 占用的旧 IP** → 全站 API 502 **约 24.5 小时**
> - 处置：重启 frontend 秒级恢复 → **隔离网络 A/B 证明**（重建后动态 200 / 静态 502）→ 生产改 `resolver + upstream ... resolve` + `nginx -s reload` **零停机** → `docker commit` 烘进镜像（回滚标签 `pre-dynamic-20260910`）；逐条路由验证 + 真登录通过
> - **随后用户决策：合并两块漂移配置并二次上线**（`/seckill/:id` 的 SPA 保护 —— `GET /seckill/123` 由 `{"state":401}` JSON 变为**页面**；SSE `proxy_read_timeout/send_timeout 180s`；真 API `/seckill/spu/list`、`/seckill/sku/list/{id}`、`POST /seckill/{code}` 未被吃掉），二次 `docker commit` + 回滚点 `dynamic-only-20260910`
> - **衍生 #61 外部端到端探活**（21 容器全 Up、网关 health 200，业务却挂了 24h → 内部探针抓不到路由层漂移）；**附带发现**：前端 nginx conf 与仓库副本双向漂移且**未入版本控制**（`deploy/` 被 ignore）→ 已 `git add -f`
> - 复盘见 [[问题解决--服务注册与网关路由]] 问题 2，归档见 [[TODO已完成]] §十五
>
> **⑧ #32-P0 AI 导购 Agent（Function Calling）完成并部署生产**
> - 提交 `09d22b1` + `0fe1a1e`；离线测试 26 项全绿
> - **两阶段验证**：A 开关关→零回归通过；B 开启→`工具 search_products 命中 4 条` + **第 2 轮收敛**、答案主动排除误召回
> - **兜底复验**：问"1000 以内的单反相机"→`已放宽=true` 且**如实回答无此类目、不编造**；⚠️ **生产开关当前 `true`**，回滚只需改 `.env` + recreate ~45s
> - → 归档 [[TODO已完成]] §十六、原理见 [[AI导购Agent实现详解]]（📦 已归档）· [[TODO已完成]] §十六
>
> **原十批记录（①~⑥ · 2026-09-10）**
> - ① **结构调整 · 仅搬位置、内容零删改**：把正文中**已完成**的条目、表格行、文档归档记录共 **12 处**统一移到文末 **「📦 已完成条目归档区」**，并在顶部新增 **「📌 待办速览」**
> - ② **#32 AI 导购 Agent 敲定**（用户拍板「做」，P0 → P1 分期，方案补 6 条实施前代码校正）
> - ③ **#48 Python 模拟数据规范设计定稿**（补数据隔离层：影子登记表 + 快照回滚 + SSE mock + 内网压测）
> - ④ **#58 完成并部署生产**（`thinking` 开关 + **模型配置可配化** + 双 bean 隐患修复；生产验证：路由日志 1 次 / JSON 重排 5s / CHAT 4s / 预算记账正常 / 告警 0）→ 已归档 [[TODO已完成]] §十四
> - ⑤ **模型配置可配化落地**（档位层 + 任务层，9 项硬编码清零 → 改名只改 `.env` 不重编译）+ **新增 #60 Spring AI 引入评估**（结论：暂不引入，前置 = Boot 全站升级）
> - ⑥ **日期笔误修正**（本会话误写 `2026-09-11` 共 63 处 → 统一为 **2026-09-10**）；历史上一次实质更新为同日更早：文档归档整理 + 状态纠偏 + 文档切割 + #44 key 轮换
> **本文件定位**：**唯一状态源**（未完成 / 暂缓 / 评估 / 第三批事项）；已完成明细在 [[TODO已完成]]，原理/踩坑/面试话术在 [[TODO第三批实现与原理]] 等三个"实现与原理"文件
> **关联文档**: [[TODO已完成]]、三个「实现与原理」文件；**文档索引**见 [[文档索引]]；**已归档方案文档清单 + 已完成条目**见[[TODO已完成]] §二十（已完成条目归档区）

---

## 📌 编号总登记表（权威 · 一行查一条）

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
| **#55** | **SSH 允许密码 + 允许 root 登录（两台）** → 公网可爆破 | 🔴 **P1** | §55 |
| **#56** | 公开仓库暴露 IP/拓扑/弱凭据事实 | 🟡 P3（**已决定接受**） | §56 |
| **#57** | **Schema 漂移只读核实**（含 2026-09-11 新发现 2 处） | 🟡 P3 | §57 |
| **#65** | **普通订单库存扣减 MQ 链路整体失效** → `pms_sku.stock` 永不减少、下单无库存校验 | 🔴 **P1** | §65 |
| **#66** | Sentinel 面板上报缺口（8 服务未配 dashboard 地址） | ✅ 已修复验收 | §66 |
| **#61** | **外部端到端探活**（"21 容器全 Up、health 200，业务却挂了 24h"的根治） | 🟡 P2 | §61 |
| **#62** | mall-ai `/ai/chat/stream` 并发下 ~50% HTTP 500（`AccessDeniedException`：ASYNC/ERROR 二次派发被授权规则拒绝） | ✅ **已修复验收**（2026-09-12） | §62 |
| ~~**#64**~~ | ✅ **已修复并部署验收**（2026-09-12 晚）：预热 Job 改用 `seckill_spu.getId()`；预热日志现覆盖 **12/12** 个 SKU，且 11/12/13/14 的 key 已由 Job 按 DB 值**带 TTL** 重新预热（不再是永久 key） | §64 |
| **#69** | ✅ **已修复并部署验收**（2026-09-12 晚）：秒杀 `incrementSales` 写到错误商品（`seckill_spu.id` 当 pms spu 用） | §69 |
| **#67** | **#48 的剩余部分**：① 录像 🟡暂缓 · ~~② 正式造数~~ ✅ · ~~③ AI 并发压测~~ ✅ · **④ `--with-seckill` 真跑** · **⑤ `--clean` 的 Redis 清理** · **⑥ `sim_baseline`** | 🟢 **2/6 完成**（②③，2026-09-12）· ④⑤⑥ 待做 | [[TODO中低优先级]] §67 |
| **#68** | 🆕 **`sim_batch.dump_file` 从未落盘** → 方案要求的"**造数前快照兜底**"从未落实（可逆性目前只靠影子登记表） | 🔴 P2（**做 ④ 秒杀真跑前先补**） | §68 |
| **#69** | ✅ **已修复并部署验收**（2026-09-12 晚）：秒杀时把 `seckill_spu.id` 当 pms spu 用 → `incrementSales` **给"错误商品"加销量**。**两台实例**（老机 `csmall-seckill` + 新机 `csmall-seckill-2`）均已升级并 A/B 证伪 | §69 |
| **#64** | ✅ **已修复并部署验收**（2026-09-12 晚）：秒杀预热 Job 用 pms id 查 `seckill_sku.spu_id` → 4/12 个 SKU（11/12/13/14）从不预热；现预热日志覆盖 **12/12**，且那 4 个 key 已由 Job 按 DB 值重新预热（**带 TTL**，不再是永久 key） | §64 |

| **#31** | **生产开启向量检索**（前置**全解除**，仅剩"改配置 + 部署 + 验证"） | 🟢 可实施 | §31 |
### D. ✅ 已完成 / 📦 已归档（**明细见 [[TODO已完成]]**，此处只留指针）

| 编号 | 一句话 | 状态 | 明细 |
|---|---|---|---|
| **#6** | Dubbo 应用名撞名（`mall-product` 混入 20880） | ✅ | [[TODO已完成]] |
| **#8** | AI 预算按北京时间结算（UTC → Asia/Shanghai） | ✅ | §18? / [[TODO已完成]] |
| **#9** | Redis 主从 + 3 哨兵（故障转移演练：选主 6.1s / 自愈 9.1s / 零丢失） | ✅ | [[TODO已完成]] §十三 |
| **#13** | Nacos 开启认证 | ✅ | [[TODO已完成]] §十一 |
| **#14** | 秒杀主从切换防数据（P0 三层 + `order_type` 治本 + 方案Y + P1 对账 + **P2 `min-replicas-to-write`**） | ✅ 全部 | [[TODO已完成]] §六 |
| **#23** | 漏触发 `@Validated` 补全（注册/地址/管理员） | ✅ | [[TODO已完成]] §十 |
| **#24** | MySQL 强密码（43 位 ALTER USER 双 host）+ 3306 收窄 | ✅ | [[TODO已完成]] §四 |
| **#25** | JWT_SECRET 生产随机化 | ✅ | [[TODO已完成]] §四 |
| **#29** | 数据库每日备份（cron 02:30 + 保留策略） | ✅ | [[TODO已完成]] §十二 |
| **#32** | **AI 导购 Agent 升级**（P0 Function Calling + P1 流式 Agent + 生产加固） | ✅ 全部 | [[TODO已完成]] §十六/§十七 |
| **#33** | 双索引数据不一致（A2：统一索引 + mall-search 只读降级层） | ✅ | [[TODO已完成]] §九 |
| **#34** | **AI 并发闸门**（`AiConcurrencyGuard` Semaphore，`concurrent-max=20`，满即 429 不排队） | ✅ | [[TODO已完成]] §八 |
| **#36** | 订单 DLX 死信 + `requeue` 限次 | ✅ | [[TODO已完成]] |
| **#38** | Dockerfile 双份不一致清理（模块目录 Alpine 旧版） | ✅ | [[TODO已完成]] |
| **#42** | 跨机"真集群"演示（Redis HA + 秒杀双实例） | ✅ | [[TODO已完成]] §十三 |
| **#44** | 硅基流动 Embedding Key 吊销 + 全仓库轮换 | ✅ | [[TODO已完成]] §18.2 |
| **#46** | nacos 数据卷挂载（重启不丢配置） | ✅ | [[TODO已完成]] §十三 |
| **#47** | 数据库备份恢复演练（独立临时容器验证） | ✅ | [[TODO已完成]] §十三 |
| **#48** | Python 模拟数据 + AI 并发测试（**两层方案**） | 📦 **第一层已归档**；第二层 → **#67** | [[TODO已完成]] §十九 · §20-七 |
| **#58** | AI 模型名停用风险 + `thinking` 开关 + 模型配置可配化 | ✅ | [[TODO已完成]] §十四 |
| **#59** | 硅基流动余额不足（402）→ 充值解决 | ✅ | [[TODO已完成]] §18.2 |
| **#63** | ES 商品索引 mapping 与代码期望不符 | ✅ | [[TODO已完成]] §18.1 |
| **R1~R4** | Redis 加固（`requirepass` / AOF / `maxmemory` 256mb / 自定义 conf） | ✅ | [[TODO已完成]] |
| **R7** | 内存优化（21 容器 `mem_limit` + Nacos 降堆 + Swap 2G） | ✅ | [[TODO已完成]] |

> **⚠️ `#34` 特别说明（你举例的那个）**：它**没有独立正文**，只在"#2+#34"里出现过 —— 实际含义是 **AI 并发闸门**（`AiConcurrencyGuard`，Semaphore 上限 20，超出直接 429 不排队），属**第二批已完成**，明细在 [[TODO已完成]] **§八**。本表把它补成一行可查。

---

> 📇 **「待办速览」已并入顶部 📌 编号总登记表**（2026-09-11）—— 原速览是"分类视角"且只覆盖 13 个编号，与总登记表重复；现**只保留一处权威**：**查编号 → 顶部登记表**；**查批次 → 下方执行路线图**。
> **新到本文件请先读**：① 顶部 **📌 编号总登记表**（一行查一条）② **🎯 执行路线图**（批次视角）③ 对应 `§N` 正文。

---

## 📚 文档索引（已外移 → [[文档索引]]，2026-09-11）

> 📇 **本节已外移为独立文件** → **[[文档索引]]**（`docs/评估报告/文档索引.md`）：登记**全部方案 / 评估文档**的「有没有被跟踪、放在哪」（📍A 现存清单 · A2 切割产出的「一类问题」文档 · A3 前端文档表）。
> **⚠️ 纪律**：**新增任何方案 / 评估文档时，必须在 [[文档索引]] 登记** —— 未登记 = 漏跟踪；并在对应 TODO 条目里 `[[链接]]` 该文档（**双向索引**）。
> **本文件（TODO）只保留**：① **编号总登记表**（编号 → 一句话 → 状态 → 位置）② **未完成条目正文** ③ 执行路线图。**已完成明细** → [[TODO已完成]]；**文档记账** → [[文档索引]]。

## 🎯 执行路线图

> **已完成**：✅ 第一批（安全止血：#25/R7/#24/R1~R4/#38）与第二批（正确性+演示：#33/#8/#36/#23/#14P0+P1/#5P0/#2+#34/#13/#29 + 突发 #6）**全部完成并部署** → 明细见 [[TODO已完成]]。
> **进行中**：🟢 第三批（企业级演进 + 学习）。**跨机集群主线（#4 秒杀双实例 + #9 Redis 主从哨兵 + #14-P2）阶段 0~5 已全部完成**（2026-09-09）→ 归档 [[TODO已完成]] §十三、原理见 [[TODO第三批实现与原理]] §五/§六。**⚠️ 2026-09-10 复核**：#14-P2 **持久化已完成**（老机 `redis-master.conf` 第 15~16 行已落盘 `min-replicas-to-write 1` / `min-replicas-max-lag 10`，实测 `CONFIG GET` 与 conf 一致、从库 `state=online,lag=0,min_slaves_good_slaves=1`）→ **跨机集群主线 100% 收口**。**剩余事项**：#5-P1 热点参数限流、#40/#45/**#67（= #48 剩余部分）**/#65/#49、安全类 #51/#53/#54/#55/#56/#57（**完整清单见顶部「📌 待办速览」**）。时间紧迫时**按下方「🧭 推荐执行路线」取舍**：P1=#5-P1（面试主菜）→ P2=#40 → P3=其余只讲认知。

### 🟢 第三批：企业级演进 + 学习（演示项目可后置，按兴趣/时间取用）

> **本批条目**：#15 #39 #40 #41 #30 #16 #22 #26 #35 #45 #48 #49 #50 #51 #52 #53 #54 #55 #56 #57 #65 #66 #67 #68 #60
> **⚠️ 自 2026-09-11 起：本节不再重复罗列状态/定位** —— 每个编号的 **一句话 + 状态 + 正文位置** 见顶部 **📌 编号总登记表**；各条目的**详细正文**见下方 `§N`。
> **为什么要这样改**：原表格把整段正文塞进单元格（`#48` 单格曾达 4674 字符），一眼看去是"墙"；现改为"登记表查状态 + `### N.` 查正文"。

| 编号 | 事项 | 正文 |
|---|---|---|
| #15 | K8s 实操（k3s） | §15 |
| #39 | 镜像仓库 + CI/CD 流水线 | §39 |
| #40 | 微服务 actuator healthcheck | §40 |
| #41 | 日志聚合 Loki | §41 |
| #30 | Prometheus + 告警 | §30 |
| #16/#22/#26 | TraceId 落日志 / CORS 收敛 / 网络隔离 | §16 · §22 · §26 |
| #35 | 统一 Jackson（替换 fastjson） | §35 |
| **#45** | 统一 Dubbo 应用名规范（front/search/ams 仍撞名但无 provider） | §45 |
| **#48** | Python 模拟数据 + AI 并发测试（面试数据素材） | 📦 [[TODO已完成]] §十九 · §20-七 |
| **#49** | 商品集群：验证 Dubbo 层负载均衡（未来计划，秒杀集群稳定后） | [[TODO中低优先级]] §49 |
| **#50** | 哨兵认证/网络收敛（观察项，非必修） | [[TODO中低优先级]] §50 |
| **#51** | 容器 restart 策略（可用性风险） | §51 |
| **#52** | 镜像获取路径（运维事实，已解决） | [[TODO中低优先级]] §52 |
| **#53** | 网关重试 / 优雅下线（消灭停实例的 16s 失败窗口） | §53 |
| **#54** | RabbitMQ 生产凭据仍是默认 `guest`（安全） | §54 |
| **#55** | SSH 暴露面：允许密码登录 + 允许 root 登录（两台） | §55 |
| **#56** | 公开仓库的信息暴露（服务器 IP / 拓扑 / 弱凭据事实） | §56 |
| **#57** | 数据库 Schema 漂移：服务器是否已执行 ALTER 待核实 | §57 |
| **#65** | 🔴 普通订单的库存扣减 MQ 链路失效（`pms_sku.stock` 永不减少） | §65 |
| **#66** | 🟠 Sentinel 面板只收到 3 个服务的指标（其余 8 个未配 dashboard 地址） | §66 |
| **#67** | ✅ **#48 的剩余部分**（① 录像🟡暂缓 · ~~②~~ ✅ · ~~③~~ ✅ **（含晚间的可选深化：75/150 档 + 单次占槽）** · ~~④~~ ✅ · ~~⑤~~ ✅ · ~~⑥~~ ✅ —— **2026-09-12 一天内做完全部可做的**） | [[TODO中低优先级]] §67 |
| **#68** | ✅ **已关闭（2026-09-12）**：**已执行一次**（`cs_mall_20260912_1452.sql.gz`，319 KB / 6 库）+ **机制固化** —— 脚本新增 **`--require-dump`**：不提供即**拒绝开跑**、校验**新鲜度**、**自动登记** `sim_batch.dump_file` | ✅ 已完成 | §68 |
| **#69** | 🔴 **秒杀 `incrementSales` 写错商品**（把 `seckill_spu.id` 当 pms spu 用） | §69 |
| **#68** | 🆕 **`sim_batch.dump_file` 从未落盘** → "造数前快照兜底"从未落实（**做 ④ 前先补**） | §68 |
| **#60** | Spring AI 引入评估（结论：暂不引入，前置 = Boot 全站升级） | [[TODO中低优先级]] §60 |

> ✅ **#58 已完成并部署（2026-09-10）** —— AI 模型名停用风险 + `thinking` 开关 + **模型配置可配化**（含「双 bean 重复注册」隐患修复）。生产验证：路由日志恰好 1 次 / JSON 重排 5s / CHAT 4s / 预算记账正常 / 告警 0。详见 [[TODO已完成]] §十四 与 [[AI模型名停用风险与thinking参数改造方案]] §十一。

> ✅ **本表原列的已完成项（~~#4~~ / ~~#14-P2~~ / ~~#9~~ / ~~#46~~ / ~~#47~~）已移至[[TODO已完成]] §二十·二**——本表只保留未完成项。

---

### 🧭 推荐执行路线（2026-09-08 评估，时间紧迫时按此取舍）

> **评估维度**：面试价值 × 成本 × 风险 × 与已做内容的衔接（演示项目定位：面试讲得清 > 工程完备）。

| 优先级 | 编号 | 事项 | 为什么这个优先级 | 预估工作量 |
|---|---|---|---|---|
| 🟠 **P1 建议做** | **#5-P1** | Sentinel 热点参数限流（秒杀按 spuId） | 与 #4 天然衔接（集群化后讲限流精准度）；代码改动小（ParamFlowRule）；面试"热点参数限流"是电商必问；**方案见 [[Sentinel能力补充计划]]**（其中 P0 已完成） | ~0.5 天 |
| 🟡 **P2 有余力做** | **#40** | actuator healthcheck | 可观测基础课，改动极小（mall-common 一处 + compose 11 处） | ~2-3h |
| 🟢 **P3 演示定稿后再看** | **#45** | 统一 Dubbo 应用名（front/search/ams） | 纯规范无风险，3 文件 6 处改名，随手做 | ~30min |
| 🟢 **P3 演示定稿后再看** | **#30/#41/#39/#15/#16/#22/#26/#35** | 监控/日志/CI/CD/K8s/TraceId/CORS/网络/Jackson | 企业级"完整度"项——**面试用嘴讲即可**（说清方案+为什么暂缓），动手收益低于投入；其中 **#35 统一 Jackson** 若面试被问"JSON 安全"可挑重点改（fastjson 漏洞史已能讲） | 各 1-3 天，暂缓 |

**执行建议（时间紧张时 · 2026-09-10 更新，已剔除已完成项）**：
1. **首攻 #5-P1**（Sentinel 热点参数限流，~0.5 天，面试主菜）
2. **有余力**：#40（healthcheck Step2，⚠️ 需先放行 `/actuator/**` 白名单）
3. **没时间**：#45 随手改；#30/#39/#41/#15 等一律**只讲认知不实现**——面试文档里已有完整方案描述
4. **已完成、勿再列入计划**：#4 / #9 / #14-P2 / #46 / #47（运维两个雷 + 跨机集群主线）→ 见[[TODO已完成]] §二十（已完成条目归档区）

> 💡 **面试视角的一句话总结**：第二批交付了"止血 + 正确性"的完整实证；第三批 P0/P1 交付"高可用 + 集群"的机制实证（nacos 持久化 → 备份可恢复 → 秒杀双实例 → 热点限流），足够覆盖 90% 深挖问题。其余项的价值在于"我知道并讲得清"，不在"我做了"。

### ⏸️ 明确暂缓 / 仅评估（不实现，面试讲认知即可）

> 📇 **清单与正文已外移** → **[[TODO中低优先级]]**（2026-09-11 拆分；原表列出的 20+ 个编号现由该文件的登记表与正文统一承载）。

## 📋 高优先级条目正文（逐条）

> **范围**：登记表「A. 待执行 / 风险 / 安全 / 可实施」各编号的正文（2026-09-11 按编号归位 —— 原先 `#22/#31/#40/#45/#61/#62/#64` 散落在"低优先级"节内）。
> 🟡 中优先级 / ⏸️ 暂缓条目正文 → **[[TODO中低优先级]]**。

### 5-P1. Sentinel 热点参数限流（秒杀按 `spuId`）

> **状态**：🔴 **待做（当前第一优先 · 约 0.5 天）** —— 登记表里 `§5-P1` 原指向空处，本条为 2026-09-11 补齐。
> **为什么高价值**：与 **#4** 跨机集群天然衔接（集群化后讲限流精准度）；代码改动小（`ParamFlowRule`）；**"热点参数限流"是电商面试必问**。
> **要点**：当前秒杀 `QPS=10` 是**整接口共享** → 爆款与普通商品互相误伤；应按 `spuId` 差异化（爆款 QPS=100 · 普通 1000）。
> 📄 **完整方案（现状盘点 / 优先级 / 实施步骤 / 回滚 / 执行清单）见 [[Sentinel能力补充计划]]**（其中 P0 已完成）。

### 51. 容器 restart 策略（可用性风险）

 🔴 **P2（2026-09-09 巡检发现；与集群无关，但比集群问题更严重）**：老机 21 个容器实测 restart 策略 = **14 个 `no` + 6 个 `on-failure` + 1 个 `unless-stopped`** → **老机一旦重启（系统重启 / `systemctl restart docker`），20/21 个容器不会自动恢复**。**2026-09-10 经 [Docker 官方文档](https://docs.docker.com/engine/containers/start-containers-automatically/) 核实**：*"`on-failure` only prompts a restart if the container exits with a failure. It doesn't restart the container if the daemon restarts."* → **只有 `unless-stopped`（`csmall-resource`）能扛住宿主/daemon 重启，`on-failure` 同样不恢复**（这一点与直觉相反，是本条的关键认知）。老机已连续运行 42 天（截至 2026-09-10），所以从未暴露。

🔴 **2026-09-10 补充：新机同样中招（当时漏记）**——新机 5 个容器**全部 `on-failure`**（redis-replica / 3 哨兵 / mall-seckill-2）→ **新机重启后 5/5 全不恢复**。**连锁后果比老机更危险**：从库+3 哨兵同时消失 → 老机 Redis 主库因 `min-replicas-to-write 1` **拒绝所有写**（`NOREPLICAS`，读仍可用）→ 秒杀/登录 token 黑名单等写入路径全挂；且哨兵全灭无法故障转移。ℹ️ 两台均 `LiveRestoreEnabled=false`、docker 均 `enabled`（开机自启 daemon），且**无任何 systemd 单元 / cron 兜底**（2026-09-10 实测）→ 重启后必须人工干预。**方案**：① 首选 compose 全量改 `restart: unless-stopped`（**两台都要改**：老机 20 个 + 新机 5 个）→ 逐个 `up -d --force-recreate`（低峰，注意 ES 分片，见 §纪律 6）；② 或加 systemd 单元，在 docker 启动后执行 `docker compose up -d`。⚠️ 改策略必须 recreate 容器 → 属变更窗口事项。 


### 53. 网关重试 / 优雅下线（消灭停实例的 16s 失败窗口）

 🟢 **P2（2026-09-09 实测发现 → ③ 已实现并验证通过）**：`docker stop csmall-seckill-2` 后 **Nacos <1s 摘除**（优雅停机主动注销），但**网关本地 LB 实例列表 ~16s 才刷新** → 期间 round-robin 把一半请求打到死实例，**9 次探测中 5 次 500**（网关日志 `Connection refused: 172.29.193.240:10017`）。

✅ **已实现（2026-09-09 21:47）**：**③ 优雅下线**——`PUT ...&enabled=false`（实例退出负载均衡但**继续服务**）→ 等 40s（> LB 缓存 TTL）→ `docker stop`，实测 **30/30 请求 0 失败**（对比粗暴停 5/9 失败）；脚本 `deploy/scripts/graceful-stop.sh` 已入库；恢复只需 `docker start`（自动恢复 enabled=true）。⚠️ 注意"注销 API 无效"（客户端心跳会立刻重新注册），必须用 `enabled=false`。

⏳ **仍待维护窗口**：① 网关重试（LoadBalancer retry）/ ② 缩短 `spring.cloud.loadbalancer.cache.ttl`（默认 35s）/ ④ 断路器——三者都需**重启网关**（全站入口 1–2 分钟），作为"别人直接 `docker kill`"时的兜底。→ 见 [[跨机集群实施执行清单-2026-09-09]] §7.7/§7.7.1 


### 54. RabbitMQ 生产凭据仍是默认 `guest`（安全）

 🔴 **P2（2026-09-09 推送前敏感数据审查发现）**：生产 RabbitMQ 只有 `guest` 一个用户（`tags=[administrator]`），且**允许非本机登录**——实测 `rabbitmqctl list_connections` 显示来自 `172.18.0.18`、`172.18.0.19`、**`172.29.193.240`（新机）** 的连接用户均为 `guest`；5672/15672 监听 `0.0.0.0`。**公网已被安全组挡住**（实测 blocked），但**同 VPC / 同安全组内任何实例**都能用公开的默认值 `guest/guest` 拿到 administrator 权限（读写所有队列、经 management 插件改配置）。根因：compose 里服务侧注入的是 `RABBITMQ_USERNAME/PASSWORD`（**Spring Boot 不读这两个名字**）→ 服务实际用 Spring Boot 默认值 `guest/guest`。

**方案**：① `.env` 设强密码 + RabbitMQ 侧 `rabbitmqctl add_user/change_password/set_user_tags/set_permissions`（⚠️ `RABBITMQ_DEFAULT_*` 只在**首次初始化**生效，数据卷已存在时无效）→ 删或禁用 `guest`；② compose 服务侧改用 **`SPRING_RABBITMQ_USERNAME` / `SPRING_RABBITMQ_PASSWORD`**（当前名字是错的）；③ 可选 `loopback_users.guest = true` 恢复默认限制。

**注**：`.env.example` 里的 `RABBITMQ_PASSWORD=guest` **不是泄露**（公开默认值），但**等于生产无密码**；**且仓库是公开的**（`github.com/yunxuan4309/csmall`，`private: false`）→ 这个事实等于公开写明了"生产 MQ 用 guest"。 


### 55. SSH 暴露面：允许密码登录 + 允许 root 登录（两台）

 🔴 **P1（2026-09-09 推送前安全审查发现）**：两台 `sshd_config` 均为 `PermitRootLogin yes` + `PasswordAuthentication yes`（无 drop-in 覆盖），而安全组 **22 端口对 `0.0.0.0/0` 开放** → 公网可**直接暴力破解**（阿里云 ECS 是扫描最密集的目标之一）。**方案**：① `PasswordAuthentication no`（`ecs-user` / `ai-*` 均已配置密钥登录，不影响使用）；② `PermitRootLogin prohibit-password`；③ 可选：安全组把 22 收紧到固定来源 IP。**⚠️ 操作顺序**：先确认密钥登录可用（`ssh -i <key> ecs-user@<ip>`）→ `sshd -t` 校验语法 → `systemctl reload sshd`（**reload 不断开现有连接**，比 restart 安全）。 


### 56. 公开仓库的信息暴露（服务器 IP / 拓扑 / 弱凭据事实）

 🟡 **P3（2026-09-09 审查发现）**：`github.com/yunxuan4309/csmall` 是**公开仓库**（`private: false`），而文档系统性地记录了：两台服务器的**公网 + 私网 IP、主机名、端口拓扑、安全组放行清单**，以及"生产 RabbitMQ 用 `guest`"等事实。**风险**：攻击者无需扫描即可拿到完整攻击面（缓解：公网端口已被 SG 挡住、**真实密码未入库**、已实测生产 JWT/MySQL/Redis 值均未出现在仓库）。**方案（择一）**：① 仓库转私有；② 文档脱敏（IP → 占位符，但削弱可读性）；③ **接受**（学习项目、无真实凭据泄露、SG 是边界）—— **当前选择 ③，已记录在案**，待以后需要投简历/公开时再评估。 


### 57. 数据库 Schema 漂移：服务器是否已执行 ALTER 待核实

 🟡 **P3（2026-09-10 文档核查发现，来源 [[数据库Schema漂移审计]]）**：该审计（2026-08-04；本地 MySQL 39 表 × mall-pojo 32 实体 × `database/` 34 个 SQL 三方对比）结论是"**本地已全部修复**（企业级升级过程中已补列），但**服务器部署前需执行 [[本次修改部署指南--2026-08-04]] 同款 ALTER**"。**待办**：① 用报告里的对比清单在服务器 `information_schema` 上做**一次只读核对**；② 若有差异 → 生成 ALTER 并在低峰执行；③ 核对完回填两份文档状态。

🆕 **2026-09-11 又发现 2 处漂移（同属本条目范围，本次一并登记）**：① **`cs_mall_seckill.seckill_message_retry` 在生产存在（79 行）但 `database/` 目录里没有对应 DDL 文件**；② **生产 `ams_permission` 表多出一列 `value`（全 NULL），DDL 里没有该列**。两处均**未追根因**，待并入本条的只读核对清单。 


### 65. 🔴 普通订单的库存扣减 MQ 链路失效（`pms_sku.stock` 永不减少）

 🔴 **P1（2026-09-11 由 #48 校准实测发现；既有缺陷，与造数无关 —— `mall-order` 本次重建的源码里这两个文件我们一行未改，只加了 Flyway SQL）**：

**优先级理由**：不是安全问题、不丢数据，但**核心下单链路的一个关键副作用整条失效**，且**下单完全没有库存校验**（可无限超卖）→ 影响面试主菜"0 超卖"的可信度，故列 P1。

**现象**：4 笔订单**支付成功**（`state=3`、`gmt_pay` 有值），但 `pms_sku.stock` 与 `pms_spu.sales` **完全没变**（1456 / 83）。

**定位证据（逐环）**：① `OmsOrderServiceImpl:87` 注释原文"**库存扣减改为 MQ 异步处理**，不再需要 `@GlobalTransactional`" → 下单只发消息（`:137-138 rabbitTemplate.convertAndSend(ORDER_EX, ORDER_RK, orderItemMessages)`）；② 日志 `ListenerExecutionFailedException: Failed to convert message` → `NoSuchMethodException: No listener method found in OrderQueueConsumer for class java.util.ArrayList`；③ 重试耗尽进死信后，`OrderDlxConsumer` **同样不接受 `ArrayList`** → 再次致命失败 → **消息彻底丢失**；④ 本次启动日志内 `订单库存扣减完成`（消费者成功时的 INFO）出现 **0 次**，而 `No listener method found` **21 次**（= 7 笔订单 × 3 次重试）。

**根因**：`OrderQueueConfig:80` 给监听器配了 **JSON 反序列化**（注释写"解决 LinkedHashMap 转换失败"）→ 消息体被转成 `ArrayList`；而两个消费者的 `@RabbitHandler` 分别只接受 **`String`**（`OrderQueueConsumer:32`）与 **`Message`**（`OrderDlxConsumer:38`）→ **类型不匹配**，被错误处理器判为"致命转换错误"。

**后果**：① 库存永不减少（演示数据里"库存下降"这条曲线缺失）；② **下单完全没有库存校验** → 理论上可无限超卖，项目"100 并发 0 超卖"的卖点**需重新评估**；③ 死信告警链路同时失效（本该输出 `【MQ死信告警】` 的那条 ERROR 根本没执行）。

**修复方向（未实施，待用户决策）**：`OrderQueueConsumer` 入参改为 `List<OrderItemMessage>`（或去掉该监听器的 JSON 转换器、让它收 `String`）；`OrderDlxConsumer` 同理改为接收原始 `Message`/`byte[]`。⚠️ 需**重建 mall-order 镜像 + 重启**，属变更窗口事项。

**对 #48 的影响**：方案 §2.2.1「下单累加 `sales` / 扣库存 → 不可逆污染」**实测不成立** —— `sales` 只由**秒杀**链路累加（`incrementSales` 全仓唯一调用方是 `SeckillQueueConsumer:98`），普通订单本就不加；库存那条是**缺陷导致失效**而非设计。→ 普通订单造数**不会**不可逆消耗库存，**但不能依赖这个"幸运"**。

**🔎 只读验证（任何人可复现，无需改代码）**：① `docker logs csmall-order \


### 66. 🟠 Sentinel 面板只收到 3 个服务的指标（其余 8 个未配 dashboard 地址）

 🟠 **P2（2026-09-11 由 #48 可观测展示实测发现）**：

**现象**：用户实测"**Sentinel 面板里只有 `sso` 与 dashboard 自己有曲线**"，压浏览 URL（实测 **99.6 RPS**）在面板上**看不到任何曲线**。

**根因（逐个 `docker inspect` 服务环境变量）**：只有 **`mall-order` / `mall-seckill` / `mall-sso`** 配了 `SPRING_CLOUD_SENTINEL_TRANSPORT_DASHBOARD=sentinel:8858`；**`mall-front` / `mall-gateway` / `mall-product` / `mall-search` / `mall-ums` / `mall-ams` / `mall-resource` / `mall-ai` 全部未配置** → 这些服务**本地有埋点（`SentinelWebInterceptor` 已注册）但指标从不外发**。

🆕 **2026-09-11 深挖补充（根因更精确）**：① **只有 4 个模块的 yml 里写了 `dashboard:`**（order/sso/seckill/ai），且写的是 `dashboard: ${my.server.addr}:8858` —— 而 compose 注入 `ALIYUN_SERVER_IP=nacos` → **`${my.server.addr}` 解析成 `nacos`** → 指向 `nacos:8858`（**那里没有面板**）→ **静默不注册**；其余 7 个模块 yml **连 `dashboard:` 都没有** → SCA 默认不注册。⇒ **compose 的环境变量才是"能注册"的真正开关**。

② 🆕 **`mall-gateway` 是框架层例外**：其 jar 里**没有 `spring-cloud-alibaba-sentinel-gateway`**（也没有 `sentinel-datasource-nacos`）→ **它的路由从来就不是 Sentinel 资源**，即使补了 dashboard 地址，**网关的 URL 曲线也不会有**（WebFlux 的 `SentinelWebInterceptor` 是 MVC 适配器，不生效）。

**影响**：① 演示"浏览流量 **pass 曲线**"在面板上拿不到（方案 §6.2 的结论依赖这个前置）② **`mall-ai` 的 AI 限流规则（`ai-chat=5` 等）也不会在面板显示** → **AI 限流演示同样受影响**（当前只有 order/seckill 的写接口能演示 block）③ 与 #5-P1（热点限流）、#48 §6 录像目标都相关。

**修复方向（未实施，待决策）**：在 `deploy/docker/docker-compose.yml` 给这 8 个服务补 `SPRING_CLOUD_SENTINEL_TRANSPORT_DASHBOARD: sentinel:8858`（或用公共 env 锚点统一注入）→ `docker compose up -d --force-recreate` 对应服务。⚠️ 属变更窗口事项。

✅ **2026-09-11 已实施并验证（用户执行 + AI 独立复核 + 用户目视确认）**：仓库 compose **新增 8 处**（`git diff --stat` = 恰好 8 insertions）、传输 md5 与仓库一致（`514d1411…`）、`docker compose up -d --force-recreate` 8 个服务成功、`docker inspect` **11/11 服务均有该变量**；**决定性证据**：面板日志显示它正在轮询客户端端点（10 个 `IP:port`，端口 8870/8872/8876/8880/8719 正对应各服务 yml 里配的 transport 端口），且**最近 10 分钟 `Failed to fetch metric` = 0** ⇒ 注册与指标拉取都通了；✅ **用户目视确认：Sentinel 面板里已出现新增的 8 个服务 → 全链路验收闭环**。ℹ️ **面板默认演示凭据 = `sentinel`/`sentinel`**（实测登录成功）。

⚠️ **过程教训（已写入方案 §F4）**：**8 个服务同时 recreate 会造成"启动踩踏"** —— 4 核机器上启动耗时从 107~176s 涨到 **221~319s（2~3 倍）**，load average 峰值 7.45；**下次应分批（2~3 个一批）**。

**旁路（不修也能演示）**：改看 **SkyWalking** —— `mall-front` 的 Load/Latency/Apdex **确实在动**（用户截图实测 `Load 1520.846 calls/min` · `Latency 124.462ms` · `Apdex 0.983`）。 


### 22. 【CORS】服务端 CORS 收敛到网关（2026-08-28 评估，待实施）

> **2026-08-28 新增（源自 06-安全设计 Q5 审计）**：CORS 全景——网关 CorsConfig 显式白名单 ✅；但 5 个服务（ums/search/product/resource/seckill）WebMvcConfiguration 有 `allowedOriginPatterns("*")` 宽松通配（dev 直连需要，但**没限定 profile → 生产也生效 = 多余且宽松**）；mall-ai 自带 CORS（SSE dev 直连 10010 绕过网关，vite.config 注释实证）；order 已是范例（已注释并写明"由网关统一处理"）；ai 路由 DedupeResponseHeader 是重复头补丁。

**方案（P2）**：
1. 5 服务 WebMvcConfiguration 的 CORS 加 `@Profile("dev")` 限定（不是删——dev 直连还要用）
2. mall-ai CORS 同样收窄 dev-only
3. 网关显式白名单不动
4. ai 路由 DedupeResponseHeader 可删（重复源消失，可选保留兜底）
5. 验证：生产 curl -i 响应头只有一个 Access-Control-Allow-Origin；dev 跨域 + ai SSE 回归

**面试价值**：能讲清"CORS 是浏览器机制，生产收敛到网关、dev 直连才需要服务端 CORS"


### 31. 【AI】评估生产开启向量检索 embedding-enabled: true（2026-09-02 记录，待决策）

> ✅ **2026-09-11 前置已解除**：用户**已充值 10 元**，AI 用生产 key 实测 `POST /v1/embeddings`（`BAAI/bge-m3`）→ **HTTP 200 / `dims = 1024`**（详见 **#59**）。~~原 2026-09-10 "P0 级阻断"~~ 不再成立。
> 🔴 **但顺序有硬要求：先修 #63（ES mapping），再开本项。** 原因：线上 `cool_shark_mall_ai` 索引**根本没有 `semanticVector` 字段**（2026-09-11 二次复核确认为 dynamic mapping）→ 开关一开，向量**无处可写**；而修 #63 的"删索引 → 重建"动作会把正确 mapping（含 `dense_vector dims=1024`）建出来 → **#31 之后只需改配置 + 同步，零停机、不必再删索引**。规划与验证清单见 [[商品与秒杀扩容方案]] §十 / [[TODO第三批实现与原理-1]] §三·3.2（#31 实施顺序与验证清单 —— 未实施部分仍原样保留在该册）。

> **2026-09-02 新增（源自 09-AI模块 Q3 服务器复核）**：生产 `embedding-enabled: false`（ES 全文检索），向量检索 = 完整代码 + test 验证 + 预留开关。**用户倾向开启（原以为 BGE-M3 免费），待后续考虑**。⚠️ **2026-09-10 实测更正**：硅基流动免费额度**已不足**、`POST /v1/embeddings` 返 **402** → 见 **#59**。

**现状（服务器 + 代码实证，2026-09-11 复核更新）**：
- prod yml `embedding-enabled: false` + 注释"生产默认关闭，按需开启"；test 环境 true
- ES `cool_shark_mall_ai` 索引实测**无 `semanticVector` 字段**，且 mapping 是 **dynamic 的（与代码期望完全不同）** → **必须先修 #63**，否则向量**无处可写**
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

**面试价值**：开 = 完整 RAG 链路真实运行；关 = 讲"按需开"的工程判断——两者都可讲；决策点 = 外部 API 稳定性能否接受

---


---


### 40. 【部署】微服务自身 healthcheck（🟡 **Step 1 已完成 / Step 2 待做**，2026-09-10 复核修正）

> **2026-09-03 新增（源自容器化部署企业级差距评估）**：compose 中**中间件全带 healthcheck**（mysqladmin ping/redis-cli/curl），但 **11 个微服务均无 healthcheck**、无 `/actuator/health` 暴露 → `restart: on-failure` 只能拉起"进程崩溃"，**服务起但内部不健康（连不上 Nacos/DB/Redis）时不会被重启**，Docker 认为"活着"。
>
> ⚠️ **2026-09-10 复核修正（原标题"待实施"已过期一半）**：本条**前半已不成立** ——
> | 步骤 | 状态 | 实测证据 |
> |---|---|---|
> | **Step 1**：引入 actuator + 暴露 `/actuator/health` | ✅ **已完成** | 11 个 webapi 模块 pom **全部有 ★实际依赖** `spring-boot-starter-actuator`（+ gateway 共 12 个）；11 个 `application.yml` 全部配 `management.endpoints.web.exposure.include: health,info`（代码侧就绪）

🔴 **但 2026-09-10 实测发现"暴露 ≠ 可访问"**：微服务的 `/actuator/health` 被**自己的 SSO 安全链拦截**（`ResourceWebSecurityConfiguration` 里 `.anyRequest().authenticated()`，而 `buildPermitAllMatchers()` **不含 `/actuator/**`**）→ 返回的是 **HTTP 200 + 响应体 `{"state":401,"message":"您没有登录！"}`**（**"假健康"**）。实测：10004/10006/10007/10010 全是这个响应；只有 **gateway(10087)** 因无 SSOFilter 返回真正的 `{"status":"UP"}`|
> | **Step 2**：compose 每个微服务加 `healthcheck:` + 用 `depends_on: condition: service_healthy` | ❌ **仍未做** | compose 里 `healthcheck` 实测只在 6 个**中间件**（mysql/redis/nacos/rabbitmq/es/seata）；`docker ps` 里只有这 6 个显示 **`(healthy)`**，**11 个微服务全无该标记** |
>
> **因此本条的剩余工作 = Step 2**（P2），价值不变：让"服务起来了但连不上 Nacos/DB/Redis"这种**假活**能被 Docker 识别并按策略重启。
>
> **方案（P2，剩余部分）**：compose 每个微服务加 `healthcheck`（依赖方可用 `depends_on: condition: service_healthy` 做服务级就绪等待，替代现在的"只等中间件"）。

> 🔴 **⚠️ 直接写 `curl -f /actuator/health` 是无效的（2026-09-10 实测发现，必须先解决）**：
> 微服务的 `/actuator/health` 被自己的 SSO 安全链拦截，返回 **HTTP 200**（响应体却是 `{"state":401,"message":"您没有登录！"}`）→ **`curl -f` 只看状态码，会永远通过** = healthcheck 形同虚设。
> **因此 Step 2 有一个隐藏前置**：先把 `/actuator/health`（建议再加 `/actuator/info`）加进各服务的 `buildPermitAllMatchers()` 白名单 → 重新构建部署 11 个服务；**或者** healthcheck 不用 HTTP 探针而用 `nc -z localhost <port>`（只证明端口在听，**证明不了依赖健康**，价值低）。**推荐前者**。
> ℹ️ 另：gateway 是唯一例外（无 SSOFilter，`/actuator/health` 返回真正的 `{"status":"UP"}`）。
3. 注意：actuator 端点收窄（只开 health，避免暴露 env/beans 等敏感端点，呼应安全审计）

**面试价值**：能讲"进程活着 ≠ 服务健康——我补了 actuator healthcheck，让依赖方等服务真正就绪"——容器化可观测基础课

---

> 🟢 **第三批**

### 45. 【规范】统一 Dubbo 应用名（front/search/ams 撞名但无 provider，2026-09-08 记录，第三批）

> **2026-09-08 记录（源自 #6 全项目排查）**：修复 seckill/ums/product 撞名时，发现 **mall-front / mall-search / mall-ams** 的 `dubbo.application.name` 与 `spring.application.name` 相同（撞名），但三者**均无 @DubboService 暴露**（不注册 20880 provider 实例）→ Nacos 实测仅 HTTP 实例、gateway `lb://` 安全，**无实际风险，本次不改**（避免无谓回归面）。

**统一规范（第三批，未来顺手做）**：三个模块 dubbo 名加 `-dubbo` 后缀（prod/test 对齐），与 order/ai/seckill/ums/product 一致——**防未来给这些模块加 Dubbo provider 时重新踩 #6 坑**（加 provider 瞬间 20880 混入现有服务名，lb:// 立刻 500）。

**涉及文件**：mall-front-webapi / mall-search-webapi / mall-ams-webapi 的 application-{prod,test}.yml（各 2 处 dubbo.application.name）。

**面试价值**：能讲"我排查 #6 时发现 3 个模块撞名但无 provider——当时没风险所以没动，但记了规范项防未来加 provider 时踩坑"，展示"按风险分级处理 + 前瞻性记录"。

---


### 61. 【监控】外部端到端探活（防"静默故障"）🟡 P2（2026-09-10 抢修衍生）

> **来源**：2026-09-10 生产故障（nginx 静态上游 IP 缓存 → 网关重建后**全站 API 502 约 24.5 小时无人发现**）。原理与排查链见 [[问题解决--服务注册与网关路由]] **问题 2**。
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


### 62. 【观察 → ✅ 已修】`/ai/chat/stream` 并发下 ~50% HTTP 500：`AccessDeniedException`（ASYNC/ERROR 派发）🟡 P2（2026-09-10 观测 · **2026-09-12 定量定位并修复**）

> **现象**：2026-09-10 P1 验证与加固验证期间，`POST /ai/chat/send`（经网关）返回 **500**（网关日志 `500 Server Error for HTTP POST "/ai/chat/send"`），mall-ai 侧日志：

```
ERROR o.a.c.c.C.[.[.[.[dispatcherServlet] - Servlet.service() for servlet [dispatcherServlet] threw exception
org.springframework.security.access.AccessDeniedException: Access Denied
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126)
ERROR ... threw exception [Unable to handle the Spring Security Exception because the response is already committed]
ERROR o.s.b.a.w.s.e.ErrorMvcAutoConfiguration$StaticView - Cannot render error page for request [null] as the response has already been committed.
```

**✅ 已修复并验收（2026-09-12 · 提交 `9aec75a`）**

> **修法**：`mall-ai/…/security/config/ResourceWebSecurityConfiguration` 在授权规则**最前面**放行**内部派发**：
> `.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()`
> 🔴 **对 2026-09-10 结论的关键更正**：原分析只指向 **ERROR** 派发、建议只放行 ERROR —— **不够**。
> 本轮实测确认另一半是 **ASYNC** 派发：`/ai/chat/stream` 返回 `StreamingResponseBody`，业务在异步线程写完流后
> 触发一次 ASYNC 派发，那一刻**容器线程上已无认证对象** → `anyRequest().authenticated()` 拒绝。
> **两种派发都必须放行**（只放 ERROR 仍会 ~50% 失败）。默认 REQUEST 派发**仍然要求登录**，
> `/ai/**` 防匿名刷 Token 的收紧（2026-08-14）不受影响；同时消掉日志噪声与"状态码不可靠"隐患。
>
> 🆕 **影响面复核（2026-09-12 **全仓审计**，结论：其余 7 个模块**不可能触发**）**
>
> | 检查项（排除 `target/`） | 结果 |
> |---|---|
> | 会触发 **ASYNC 二次派发**的写法（`StreamingResponseBody` / `SseEmitter` / `WebAsyncTask` / `DeferredResult` / `ResponseBodyEmitter`） | **只有 `mall-ai`**（`AiController:137/156/227/246` · `ChatServiceImpl:560`）✅ |
> | `dispatcherTypeMatchers(ASYNC, ERROR).permitAll()` | **只有 `mall-ai`** 有（`ResourceWebSecurityConfiguration:71`）✅ |
> | 其余 7 个模块（ams / front / order / product / search / seckill / ums）与 sso | 都有 `anyRequest().authenticated()`，但**没有任何异步 / 流式端点** ⇒ **本缺陷在这 7 个模块无法触发**（属**潜伏**，不是现存故障） |
> | ERROR 派发那一半 | 走 `/error`，而各模块的 permitAll 白名单**都含 `/error`** ⇒ 已覆盖 ✅ |
>
> **⇒ 决策：不为它做"8 服务重建"** —— 为潜伏项重建 7 个服务，代价（30+ 分钟 + 重启在生产路径上的 front/gateway）远大于收益。
> **⇒ 纪律（写在这里，避免以后重犯）**：**任何模块将来新增流式 / 异步端点时，必须在它的 `ResourceWebSecurityConfiguration` 授权链最前面补一行** `dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()`；该服务因别的原因重建时也顺手补上。
> ⚠️ 文件里原有的 `MODE_INHERITABLETHREADLOCAL` static 块**解决不了**：ASYNC 派发用的是容器线程池里的
> **另一个线程**，不是"当前线程的子线程"（该块保留未动、未夹带）。

| 场景 | 修复前 | 修复后 |
|---|---|---|
| **串行** 20 次 | 20/20 全 200，但**每次都在日志里打一条 ERROR** | 20/20 ✅ **ERROR 归零** |
| **20 并发 × 20s** | **硬失败 32/64 = 50.0%**，成功率 60.49% | **硬失败 0**，成功率 **100%**（163 次成功） |
| **100 并发 × 45s** | （未测；按趋势必然更差） | **0 失败**，605 次成功 |

⇒ 原记录"约 3/40 次、**重试即成功**"**低估了严重度**：串行时被"响应已提交"掩盖（客户端仍看到 200），
并发时提交时序变化就暴露成 500 ⇒ **在 AI 并发压测里它直接撞中止阈值（5%）把阶梯杀掉**（2026-09-12 实测确实被中止过）。
⇒ **部署验证**：容器内 `app.jar` md5 `73fd2e68bdd544a1648d0dc5d7a2f19e`；
`ResourceWebSecurityConfiguration.class` **8859 → 9086 字节**且含 `DispatcherType`/`ASYNC` 常量。
⇒ 实测明细与两链路结果见 [[AI并发测试方案]] **§十**。

**影响面（后续待办 · 未做）**：8 个模块的 `ResourceWebSecurityConfiguration` 是**同一套授权写法**，但
**只有 mall-ai 有流式（`StreamingResponseBody`）接口** ⇒ 其余 7 个（front/gateway/product/search/ums/ams/order/seckill）
属**潜伏**。本次按"最小修复、可独立回滚"**只改 mall-ai**；其余建议单独窗口统一加同一行
（改动小，但需多个服务重建 —— 注意"一次重建一大片会启动踩踏"，见 [[Python模拟数据与数据隔离方案]] §F4）。

**（以下为 2026-09-10 的历史分析，机制判断正确、但对"修哪一半"的结论已被上面更正）**
**已定性（共 3 次观测 + 3 组定量实验）**

| 证据 | 结论 |
|---|---|
| 失败请求到 mall-ai 时**没有任何 `JwtTokenUtils 解析` 日志**（同窗口其他请求都有） | 该次请求在 Security 层是**匿名**的 |
| **经网关连打 8 次 + 直连 4 次 = 12/12 全 200**；再做"SSE 流传输中并发 6 次同步请求" = **6/6 全 200** | 客户端成功率高，**与 Agent 逻辑无关** |
| ⭐ **并发实验期间客户端 6/6 全成功，日志里却仍出现 2 次 `AccessDeniedException`** | AccessDenied **不是**客户端失败引起的 → 是**内部 ERROR 派发的次生现象** |
| 栈里同时有 `ErrorReportValve.invoke` + "response has already been committed" | 某请求先失败（响应已提交，**典型是 SSE 流**）→ Tomcat 转 `/error` → Spring Security 在 **ERROR 派发**上再跑一遍过滤器链，而 **JWT 过滤器是 `OncePerRequestFilter`（默认跳过 ERROR 派发）** → 匿名 → `AuthorizationFilter` 拒绝 `/error` → 刷出这条 ERROR |

**影响**：以日志噪声为主；确有少量客户端可见 500（约 3/40 次观测），**重试即成功**。

**两条候选修法（✅ ① 已于 2026-09-12 实施，且**加强了**：同时放行 ASYNC + ERROR；② 未做）**：
1. **放行 ERROR 派发**（`ResourceWebSecurityConfiguration`）：`requestMatchers("/error").permitAll()` + 允许 `DispatcherType.ERROR`（或用 `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`）→ 让 `/error` 能正常渲染，客户端拿到**规范的 401/5xx JSON** 而非"响应已提交"噪声。⚠️ 属 **mall-common / Security 公共配置，影响所有服务**，必须单独窗口 + 回归。
2. **SSE 收尾容错**：配合 ① 才完整（`writeSSE`/`closeQuietly` 已 catch 应用层异常，但容器 flush 阶段的失败在应用之外）。

**优先级**：P2（修 ① 之前，表现为"偶发 500 + 日志噪声"）。**可能与 #53（网关重试/优雅下线）同源，建议同窗口一起看。**

---


### 64. 【正确性缺陷】秒杀预热的两套 `spu_id` 语义冲突 → 4/12 个秒杀 SKU 从不被预热（被一个"永久 key"偶然兜住）🔴 P2（2026-09-11 发现，含生产日志与 Redis TTL 双重证据）

> **本次只登记，不在本批夹带**（§6.1-5）。**这条是"看着能跑、其实靠巧合"的典型**。

**冲突本身**

| 位置 | 用的是哪个 `spu_id` |
|---|---|
| 前端详情 / 限购判断 | `seckill_spu.id`（`seckill_sku.spu_id` 存的就是它） |
| **预热 Job `SeckillInitialJob`** | **pms `spu_id`**（`spu.getSpuId()`） |

→ 二者**只在 `seckill_spu.id == pms_spu.id` 时碰巧一致**。

**生产实测证据（三重）**
1. 数据：`seckill_spu.id` = 1~6，`spu_id` = 1,2,5,6,15,20；`seckill_sku.spu_id` = 1,1,2,2,3,3,4,4,5,5,6,6 → **4 行（id 3/4/5/6）两套语义不一致**。
2. 日志（每分钟一轮）：预热到的 sku 集合 = `{1,2,5,6,26,27,35,36}`（8 个）→ **sku 11/12/13/14 从不被预热**（26/27、35/36 是数值"撞车"被顺带覆盖）。
3. Redis TTL：`mall:seckill:sku:stock:11/12/13/14` 的 **`TTL = -1`（永不失效）**，而 1/2/5/6/26/27/35/36 的 TTL ≈ 83~112 秒 → 说明前 4 个**只能**来自**不带 TTL 的 `set`** = 每天 03:30 的 `SeckillReconcileTask` 对账补建。

**⇒ 结论**：这个 bug 今天没爆，是因为**凌晨对账写的永久 key 恰好掩盖了它**。不预热时的直接后果是下单 500：`没有该商品缓存信息(可能在真空期,等下一分钟再试)`。

**修法**：`SeckillInitialJob` 改用 `seckill_spu.getId()`（与前端/限购口径统一），并订正历史数据；或明确"三值必须相等"的约定并在新增路径上强制。⚠️ 涉及秒杀可用性，需单独窗口。

**顺带（扩容时的硬约束）**：**新增秒杀必须让 `seckill_spu.id == seckill_sku.spu_id == pms_spu.id` 三者相等**；若走后台管理接口 `/seckill/manage/spu` 新增，MyBatis-Plus 会给**雪花 id** → `id ≠ spu_id` → **预热永不生效**。→ **扩容实施方案（含"哪些 Redis key 能手工写、哪些绝不能"与验证清单）见 [[商品与秒杀扩容方案]] §三**。

**✅ 修复与部署验收（2026-09-12 晚 · 与 #69 同一次变更）**

> **修法一行**：`SeckillInitialJob` 的 `findSeckillSkusBySpuId(spu.getSpuId())` → **`spu.getId()`**。
> ⚠️ **随机码键 `getRandCodeKey(spu.getSpuId())` 不动** —— 读码确认详情接口 `/seckill/spu/{spuId}` 收的就是 **pms 主键**（`SeckillSpuVO.id` 来自 pms 商品 `copyProperties`），那处**本来就是对的**，改它反而会引入新 bug。
> **验收**：升级后 Job 日志覆盖 **12/12** 个 sku（`开始将13号sku…` / `14号sku…`）；把 4 个永久 key 删掉后，Job 在 **05:59:00** 打印 `11/12/13/14号sku库存数成功预热到缓存!`（该分支就是**带 TTL 的写**）⇒ 值取自 DB、键再也不是"永久 key"。
> **证据链**：删前 Redis 值(100/80/40/25) 与 `seckill_sku.seckill_stock` 逐一相等 ⇒ 删旧键不会丢/改数据。

---

### 68. 【可逆性缺口】`sim_batch.dump_file` 从未落盘 → "造数前快照兜底"从未落实 🔴 P2（2026-09-12 正式造数时发现）

> **这不是"脚本 bug"，是"纪律没落地"**：脚本每次开局都打印"**记得在造数前做 mysqldump 并把文件名写进 `sim_batch.dump_file`**"，但**历次 7 个批次的 `dump_file` 全为 NULL**，而 `note` 里都留着脚本写的 `dump_file=待填`。

**实测事实（只读）**：`cs_mall_sim.sim_batch` 现有 **7** 行 —— `sim_20260911_1914` / `1919` / `1923` / `1925` / `1930` / `sim_20260912_1103`（AI 用户池）/ `sim_20260912_1233`（正式造数）—— **`dump_file` 一律 NULL**。

**为什么值得单独登记**：方案 **§五 第 3 步**要求"**快照先行**"（`bash /data/csmall/backup/backup-db.sh`，须 **`ecs-user`**），它的定位是**不可逆字段的兜底**。影子登记表能"**精确删掉造出来的行**"，但**删不回被累加的字段**（`pms_spu.sales`、`pms_sku.stock`）。当前这两类字段"能回滚"其实**靠巧合**：普通订单的库存扣减链路恰好是坏的（**#65**），而**秒杀链路会真扣**。

**风险点（为什么必须排在 ④ 之前）**：`--with-seckill` 会真动 `pms_sku.stock` / `pms_spu.sales` 与 Redis 预热键；脚本的"**秒杀后恢复**"只按**脚本内快照**回写 —— 一旦中途失败（容器重启 / 网络断 / 脚本被杀），**只剩 dump 能救回来**。

**做法**：① 造数 / 秒杀前先 `bash /data/csmall/backup/backup-db.sh`（`ecs-user`）；② 把**文件名**写进对应批次的 `sim_batch.dump_file`；③ 事后核对文件**存在且非空**（别只记名字）。**脚本侧可选加固**：加 `--require-dump <文件名>` 开关，未提供则**拒绝开跑**（把"提醒"变成"强制"）。

**🆕 2026-09-12 复核修正（本条的表述要更准）**：实测老机 `/data/csmall/backup/` 里**每日 02:30 的 cron dump 一直在跑**（`cs_mall_20260909~20260912_0230.sql.gz`，各约 **49~57 KB** ⇒ 库很小、dump 只需**秒级**），另有 09-11 18:56 / 19:14 / 19:19 三次**手工** dump（正是校准造数那几次）。

⇒ 缺口**不是"没有备份"**，而是：
① **造数/秒杀之前的那一次**没做（拿不到"操作前"的确切快照点）；
② **文件名从未登记**到 `sim_batch.dump_file`（7 个批次全 NULL）⇒ 事后无法把"某次造数"与"某个 dump"对上。

**执行（老机 · 以 `ecs-user`，约 1 分钟）**
```bash
bash /data/csmall/backup/backup-db.sh          # 输出 /data/csmall/backup/cs_mall_<STAMP>.sql.gz（--single-transaction，读操作）
# 再把文件名登记到批次（把 <STAMP>/<批次> 换成实际值）：
#   UPDATE cs_mall_sim.sim_batch SET dump_file='cs_mall_<STAMP>.sql.gz' WHERE batch_id='<批次>';
```
**验收**：`ls -lh /data/csmall/backup/cs_mall_<STAMP>.sql.gz` 非空即可（脚本自带"<1000B 即失败"的保护）；保留策略 7 天，磁盘现余 **40G** ✅

**✅ 2026-09-12 14:52 已执行一次（用户以 `ecs-user` 执行）**：`/data/csmall/backup/cs_mall_20260912_1452.sql.gz` —— **319 KB** · `gzip -t` 完整 ✅ · 含**全部 6 个库**（`ams/oms/pms/resource/seckill/ums`）✅（比每日 cron 那份 57 KB **大 5.6×**，因为库里已含本轮 SIM 数据）

> 📌 **为什么仍不关闭**：这次是"**临时补做**"，**规则还没固化** —— 目标形态是"**每次写操作前**先 dump，并把文件名写进**该批次**的 `sim_batch.dump_file`"。**建议的固化手段**：给脚本加 `--require-dump <文件名>` 开关（未提供则**拒绝开跑**）。
> 📌 **本次 dump 不属于任何批次**（当时没有待跑的批次）⇒ 定位是"**整库兜底快照**"；**此后由脚本自动登记**。

**✅ 2026-09-12 收口（#68 关闭）**：脚本新增 **`--require-dump <文件名>`** 快照闸门 —— **不提供就直接拒绝开跑**，提供则①解析文件名时间戳校验**新鲜度**（`--dump-max-age-min`，默认 120 分钟）②**自动写进** `sim_batch.dump_file`；只读入口（`--preflight`/`--verify`/`--compare-baseline`）与 `--clean` 不受约束；`--allow-no-dump` 可显式豁免（大声警告）。
**端到端实测（2026-09-12）**：不带 → `🔴 拒绝开跑`（附"先跑 backup-db.sh 再带文件名"两步指引）；旧包 → `快照太旧（3628 分钟前，上限 120）`；不合规名 → `文件名不合规`；合法 → `✅ 闸门通过` + 批次 `dump_file = cs_mall_20260912_1452.sql.gz`（实测写入成功 ✅，随后清理干净）

**关联**：[[Python模拟数据与数据隔离方案]] §五 第 3 步 / §2.2 数据隔离 · [[TODO中低优先级]] §67（④ 的前置）· #65（库存扣减链路失效：本次"没扣库存"的巧合来源）

### 69. 【数据正确性】秒杀把 `seckill_spu.id` 当 pms spu 用 → **给"错误商品"加销量** 🔴 P1（2026-09-12 实测 + 读码双证）

**现象（同晚 3 次秒杀真跑，两次命中）**：秒杀**成功**、DB 三值看着都"回位"了，但**全库 `SUM(sales)` 每次都多 1** —— 差异落在**别的商品**上：

| 时间 | 目标 | 被 +1 的行（错） |
|---|---|---|
| 13:30（批次 1329） | sku 14 → pms **spu 6** | `pms_spu.sales[4]` 1 → 2 |
| 13:33（批次 1333） | sku 13 → pms spu 6 | `pms_spu.sales[4]` 2 → 3 |
| 13:36（批次 1335） | sku 35/36 → pms **spu 20** | `pms_spu.sales[6]` 1 → 2 |

⇒ 被写错的行 = **"另一个命名空间里的同号 id"**，不是目标商品。

**代码根因（一行）**：`mall-seckill/mall-seckill-webapi/.../consumer/SeckillQueueConsumer.java:98`
```java
dubboSeckillSpuService.incrementSales(sku.getSpuId());   // ← sku 是 SeckillSku
```
`seckill_sku.spu_id` 存的是 **`seckill_spu.id`（秒杀表内部 id）**，不是 `pms_spu.id`（**#64** 已实测：`seckill_spu.id` = 1~6，其 `spu_id` = 1,2,5,6,15,20；`seckill_sku.spu_id` = 1,1,2,2,3,3,4,4,5,5,6,6）。而 `SeckillSkuServiceImpl:51` 的注释恰好写着"spuId 参数为 PMS 商品主键，需先映射到 seckill_spu 内部 id" —— **列表路径做了映射，消费者路径没做**。

**为什么一直没被发现（三条叠加）**：① `sales` 只是展示字段，写错行**不报错、不影响下单**；② 秒杀侧"恢复"只写**目标商品**那一行，**恰好永远碰不到被写错的那一行**；③ 脚本原 `seckill_verify` 只校验 `seckill_stock`/`stock`、**不校验 sales** ⇒ 打印"✅ 全部回位"的**假通过**。

**已做的止损（脚本侧 · 已验证）**：`seckill_snapshot` 增采**全量** `pms_spu.sales`；`seckill_restore` **按实际变化的行**回补（日志点名"**非目标 spu**"）；`seckill_verify` 把全量 sales 纳入校验 → 三次实测**都当场回补**、`--compare-baseline` **0 差异**。另：秒杀**还会真建 `oms_order`/`oms_order_item`**（每次 +1/+1、`data_source=NULL`，既不登记也不回填 ⇒ 清理必漏），脚本已补"**按订单 id 增量兜底登记**"。

**生产侧修法（待单独窗口）**：`incrementSales` 改传 **pms spu id**（`seckill_spu.spu_id`，或用 `skuId → seckill_spu.spu_id` 查一次）；⚠️ `sales` 是累计值，**历史错记的行无法自动纠正**，只能按 `success` 表重算或人工订正。

**关联**：**#64**（同一对命名空间的另一个受害者：预热 Job 从不预热 4/12 个 SKU）· [[问题解决--代码与线上不一致的静默失效]]（"两个事实来源不对齐 → 不报错"）· [[问题解决--生产造数的数据隔离与复核方法]]（**是靠基线比对抓出来的**）

**✅ 修复与部署验收（2026-09-12 晚）**

| 阶段 | 结果 |
|---|---|
| 代码 | `SeckillQueueConsumer` 反查 pms 主键后再 `incrementSales`（新增 `SeckillSpuMapper.findPmsSpuIdBySeckillId`）；顺带修掉同源的 **#64** |
| 构建/测试 | `mvn -o -B -DskipTests -pl mall-seckill/mall-seckill-webapi -am package` ✅ · `-Dtest=MessageRetryTaskTest,RedisLockUtilsTest` → **9/9 通过** ✅ |
| 部署 | 🔴 **第一次只升了老机 ⇒ A/B 不通过**：新机副本 `csmall-seckill-2` 仍是 9/9 的旧镜像（compose 里 tag 写死 `csmall/mall-seckill-replica:20260909`）→ 实测**副本消费了那条 MQ**（其日志 `05:48:38` 有 `秒杀成功记录处理完成`），销量仍写到内部 id。 ⇒ 新机 jar 替换 + `compose build mall-seckill-2` + `up -d` 后再测 |
| **A/B 证伪** | 采样"恢复窗口"内的全量 `pms_spu.sales`：**4 次动作**（含 **sku 27：内部 5 → pms 15**，**非重合 id**）→ 销量只落在**目标商品**上、脚本"**非目标 spu**"告警 **0 次**（修复前 3 次实测次次告警）；终态 `SUM(sales)` 回到 **84**、`success` **59**、残留订单 **0**、三类锁 **0** |
| ⚠️ 残留 | 历史错记的 `sales` **无法自动纠正**（`sales` 是累计值）—— 本次只把**我方测试**造成的 +1 手工还原（`pms_spu[4]` 3→1、`[6]` 2→1）；**生产历史值**若要对账，只能按 `success` 表重算或人工订正 |

> 🆕 **由此暴露的集群纪律缺口（已写进 A 册 §G G14）**：**秒杀是双实例（老机 10007 + 新机 10017，竞争同一 MQ 队列）**，而**副本镜像 tag 在 compose 里写死、两实例各有各的 jar** ⇒ **"双实例必须同版本"没有任何机制保证**。后排修复类改动必须**两台一起部署**并各自核 `docker exec … md5sum /app/app.jar`。
**关联**：[[TODO中低优先级]]（🟡 中优先级 + ⏸️ 暂缓 / 仅评估）· [[TODO已完成]]（已完成明细 + §二十 归档区）· [[文档索引]]（方案 / 评估文档登记）· [[项目上下文文档]]

**维护提示**：本文件 = **高优先级状态源**。新增条目：高优先级写入本文件登记表 A，中 / 低优先级写入 [[TODO中低优先级]]；完成后迁 [[TODO已完成]]。
