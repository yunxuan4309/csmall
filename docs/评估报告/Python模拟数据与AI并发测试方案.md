# Python 模拟数据 + AI 并发测试方案（规范设计版）

> **创建日期**：2026-08-26（初稿）
> **规范设计定稿**：2026-09-10
> **实施前复核**：**2026-09-11**（逐条实测 + 读码，写入 **§〇.1**；**复核 9 条 = 8 项修正 + 1 项澄清** + 固化 2 项决策）
> **状态**：🟢 **方案定稿 + 复核完成 + 可观测展示章节已补 + 标识方案已定（专用列）· 今晚开始实施**（对应 TODO **#48**）
> 🔴 **接续入口**：**§〇.2「当前进度与交接」** —— 决策、环境事实、待办清单（含谁执行）、今晚目标全在里面
> **修订链**：初稿（08-26）→ 规范设计定稿（09-10，补数据隔离层）→ **实施前复核（09-11，修正 8 项 + 1 项澄清 + 2 项决策）** → **可观测展示章节（09-11，新增 §六 + 执行顺序重排）** → 🆕 **标识方案定稿（09-11，改用专用列 `data_source` + Flyway 纪律 + 交接 §〇.2）**
> 🆕 **2026-09-11 按"最初出发点"补强**：本方案的**初衷**是"模拟客户行为 → 在 **Sentinel / SkyWalking** 上看见数据量与变化 → **录像展示**"，而原稿只写了"造数"与"压测取数"、**没把可观测展示当交付物** → 新增 **§六 可观测展示与录像 SOP**（面板清单 + 隧道命令 + **§6.2 实证**：压浏览 URL 在 Sentinel 也可见），并把 **§五 执行顺序按"可录像展示"重排**
> **目的**：① 用 Python 脚本模拟真实业务数据积累（浏览/加购/下单/秒杀），让系统看起来像运营中的电商；② 在不调用真实 DeepSeek API 的前提下，测试 AI 导购模块能扛住多少并发
> **定位**：与 JMeter 压测互补——JMeter 测性能尖峰（100 并发秒杀 + Sentinel 限流），本方案造业务数据沉淀 + 测 AI 服务端并发承载
> **关联**：[[TODO文件]]#48、[[TODO已完成]]#29（cron 备份）/ #47（恢复演练）、[[服务器内存优化方案]]（R7 已完成）、[[运维手册--密钥密码轮换]]、[[阿里云ECS服务器情况]]

---

## 〇、本次规范设计改了什么（2026-09-10）

> 初稿（2026-08-26）的设计**没有数据隔离层**，只有"用户名前缀 + 事后 DELETE"；本次逐条读码 + 服务器实测后**重写**。**原始有价值内容（80/15/5 漏斗、mock LLM 思路、指标、面试话术）全部保留**。

| # | 初稿问题 | 证据（2026-09-10 实测） | 本次修正 |
|---|---|---|---|
| ① | **清理 SQL 顺序错误 → 订单删不掉**：先 `DELETE ums_user`，再 `DELETE oms_order WHERE user_id IN (SELECT id FROM ums_user WHERE username LIKE 'test_sim_%')` —— 用户已删，子查询返回**空集** | 初稿 §2.4 原文 | 改为**逆序删除**（子表→主表）+ 登记表驱动（§2.2.5） |
| ② | **只靠前缀删不掉"不可逆污染"** | `pms_spu.sales` 已有 **81 / 1 / 1**…（累加值，**无"谁贡献多少"记录**）；`pms_sku.stock` **38 SKU / 合计 1456 / max 100（1 个已 0）**；`seckill_sku.seckill_stock` **12 条 / 合计 822 / max 150（1 个已 0）**〔库存口径 2026-09-11 复核修正，见 §〇.1 D7〕；Redis `mall:seckill:sku:stock:*` **12 个** | 新增**快照回滚兜底**（§2.2.3 / §2.2.7） |
| ③ | **Redis 清理会误伤真实数据**：`--scan --pattern 'mall:seckill:reseckill:*' \| xargs del` 删**全部**用户的购买标记 | 初稿 §2.4 | 改为**按登记的用户 id 精确删**（§2.2.6） |
| ④ | **全库 0 个外键** → 删除顺序无数据库保护，漏表即静默残留 | `information_schema` 外键数 = **0**；含 `user_id` 的表实测 **7 张** | 清理清单**精确到 9 张表**（§2.5） |
| ⑤ | **mock LLM 返回格式不足以压 SSE**：初稿 mock 只返回单个 JSON | SSE 解析实测在 **`DeepSeekAiClient.openSseStream:349`**：`line.startsWith("data: ")` 取分片、`data: [DONE]` 结束、带 `usage` 的分片就地记账（`doStream` 与 `streamChatWithTools` 共用）→ 普通 JSON 响应**收不到任何 chunk**〔原稿引用的 `ChatServiceImpl.doStreamDeepSeek` **已不存在**（全仓 grep 0 命中），2026-09-11 复核修正，见 §〇.1 D8a〕 | mock 必须**实现 SSE 流式格式**（§3.2） |
| ⑥ | **mock 部署位置错误**：初稿让容器访问 `127.0.0.1:9999` | `csmall-ai` 在容器内，`127.0.0.1` = **容器自身**，不是开发机 | mock 部署在**新机**（内网可达），用 **compose override 注入环境变量**，不改 prod yml（§3.2） |
| ⑦ | **压测脚本"放本机跑"** 与 2026-09-09 新要求**矛盾** | 老机 **5 Mbps 固定带宽** | 脚本**必须跑在新机/内网**（§3.4 / §五） |
| ⑧ | **未覆盖** 2026-09-09 补充需求：~100 req/s + 每日 12 点高峰 + cron | TODO #48 | 新增 §3.4 |
| ⑨ | 内存基线 2.1G / 无 Swap / "先做 R7" | R7（2026-09-07）**已执行**：mem_limit 全容器 + Nacos 降堆 + **Swap 2G**；2026-09-10 实测 **available 3.6G** | §四 更新为实测；执行顺序删掉"先做 R7" |

---

## 〇.1 实施前复核（2026-09-11 · 逐条实测 + 读码）

> **方法**：读码（`AiProperties` / `DeepSeekAiClient` SSE 管道 / `TokenBudgetService` / compose / Sentinel 规则 JSON）+ 两台服务器实测（MySQL `information_schema`、Redis `--scan`、`docker inspect` env、`df`/`free`、新机 python 模块与内网连通性）。
> **总评**：**第一层"造数"的隔离设计成立且优于多数演示项目**（关键事实逐条复核通过，见下方"复核为真"）；**第二层"AI 并发测试"按原稿直接跑会得到不可信数字，最坏情况压到真实 DeepSeek 付费接口**。以下 9 条（D1~D9）请逐条对照处理 —— 其中 **D8b 是"澄清"（原稿正确，我判错了）**，其余 8 条需修。

### A. 🔴 必须修（直接影响压测结论正确性）

| # | 缺陷 | 证据（2026-09-11 实测 / 读码） | 修法 |
|---|---|---|---|
| **D1** | **mock 注入的环境变量名错误 → override 静默失效，压测打真实付费 API** | 原稿 §3.2 写 `COOXIAO_AI_BASEURL` / `COOXIAO_AI_APIKEY`；实际 yml 用 **`${AI_API_KEY}` / `${AI_API_BASE_URL:https://api.deepseek.com}`**（`mall-ai/mall-ai-webapi/src/main/resources/application.yml:22-23`）；生产容器 env **实测只有 `AI_API_KEY`，没有 `AI_API_BASE_URL`**（当前 base-url 来自 jar 内 yml 默认值）；`deploy/docker/docker-compose.yml:545-571` 的 `mall-ai` **未透传该变量** | ① compose `mall-ai.environment` **新增一行** `AI_API_BASE_URL: ${AI_API_BASE_URL:-https://api.deepseek.com}`（否则 `.env` 里有值也进不了容器）；② override 注入 `AI_API_BASE_URL=http://172.29.193.240:9999` + `AI_API_KEY=sk-mock`；③ **压测前必查**：容器内 base-url 确实已变（`docker inspect` 或启动日志），否则这一轮全部作废 |
| **D2** | **Agent 已上生产，mock 未实现 `tool_calls` → 测的不是生产链路** | 生产容器 env 实测 `AI_AGENT_ENABLED=true` → `/ai/chat/stream` 走 `streamChatWithTools`，请求体带 `tools`，商品意图首轮还带 `tool_choice=required`；本文档定稿（09-10）在 Agent 上线（09-11）**之前**，§〇⑤ 只要求 mock 回 `delta.content` | **已决策：两条链路各压一遍**（§〇.1-D / §3.5）——mock 必须实现**工具轮**（按 `tools[0].function.name` 回 `tool_calls` 分片，参数按 `index` 分片拼接；第二轮回 `content`），再分别压 `AI_AGENT_ENABLED=false/true` |
| **D3** | **每用户频控 10 次/60s → 同一 token 第 11 个请求起全 429** | `AiProperties:136-139` `user-rate-limit-enabled: true` / `user-rate-limit: 10`；`AiUserRateLimiter` 60 秒窗口（同 yml `application.yml:96-97`） | 并发压测**必须用 N ≥ 阶梯峰值个不同模拟用户**（建议 120 个 token）；或该轮临时放开 `user-rate-limit` 并在文档写明"特意放开以测闸门"。§3.3 指标表已补"干扰项"列 |
| **D4** | **Sentinel `ai-chat=5 QPS` 与"测 AI 承载"直接冲突** | `deploy/docker/sentinel/mall-ai-flow-rules.json:2-10` `count=5`（Nacos `mall-ai-flow-rules` 同源，热更新） | **已决策：生产 + 临时放开限流**——Nacos 热改阈值 → 压测 → 改回，**零重建**；执行剧本见 **§3.5**（含备份/中止阈值/回滚点）。放开限流**几乎无成本风险**，因为 mock 生效后 LLM 流量根本不进真实 API |
| **D5** | **mock 用单线程 `HTTPServer` → mock 自己就是瓶颈** | 原稿 §3.2 代码 `HTTPServer(("0.0.0.0", 9999), Handler).serve_forever()` 逐个处理请求，且每片 `time.sleep(0.05)` → 20 条并发 SSE 被**串行化**，压出来的是 mock 的吞吐 | 改 `ThreadingHTTPServer`；**mock 并发能力必须 ≥ 闸门 20**（`concurrent-max: 20`），最好 ≥ 阶梯峰值；并**先单独压 mock 拿 P50/P99**，证明它不是瓶颈 |

### B. 🟠 运行环境卫生（不修就跑不起来）

| # | 项 | 实测（2026-09-11） | 修法 |
|---|---|---|---|
| **D6** | 脚本运行环境未验证 | 新机 `python3 3.12.3` ✅ / `requests 2.31.0` ✅ / **`pymysql` 未装** ❌ / **`mysql` 客户端未装** ❌；`pip3 install pymysql` 被 **PEP 668**（`externally-managed-environment`）拒绝。**好消息**：新机 → 老机 **3306 / 6379 / 10087 全通**，网关 `/actuator/health` **26ms**（私网不限速不计费）→ "内网执行位"这条通路**已实测可用** | **`python3 -m venv` + `requirements.txt`（版本钉死）**，不污染系统、可复现；DDL 与快照**不走脚本**，仍在老机用 `docker exec csmall-mysql` 执行 |
| **D7** | 造数规模与库存/限购的耦合没量化 | `pms_sku` **38 条 / 合计 stock 1456 / max 100 / 已有 1 个为 0**；`seckill_sku` **12 条 / 合计 seckill_stock 822 / max 150 / 已有 1 个为 0**。原稿"每天 1000 行为 ≈ 50 单"× 2 天 ≈ 100 单 ≈ **消耗 100~300 件 = 总量的 7%~20% → 可行** ✅ | ① 脚本**必须跳过 `stock=0` 的 SKU**；② 启动时 `SELECT SUM(stock)` **fail-fast 预检**（不够就拒绝跑）；③ 库存口径改为实测值（§2.2.1 / §〇② 已更新） |

### C. 🟡 事实与引用漂移（不影响方案成立，但会误导实施者）

| # | 漂移 | 证据 | 修法 |
|---|---|---|---|
| **D8a** | §〇⑤ 引用 `ChatServiceImpl.doStreamDeepSeek` | **全仓 grep 0 命中**（该方法已不存在） | 改为 `DeepSeekAiClient.openSseStream`（L325-368）：`line.startsWith("data: ")` 取分片、`data: [DONE]` 结束、带 `usage` 的分片就地记账；`doStream` 与 `streamChatWithTools` 共用。**结论（mock 必须回 SSE 分片）不变** |
| **D8b** | §3.1 "全局 **2 元/天**预算" —— ⚠️ **复核澄清：原稿是对的，我第一版判错了** | **生效值 = `mall-ai/mall-ai-webapi/src/main/resources/application.yml:86` `daily-budget: 2.0`**（yml 才是生效源）。`AiProperties:104` 的 `dailyBudget = 10.0` 只是**代码兜底默认值**、被 yml 覆盖 → 二者不一致属"有默认值但被配置覆盖"的正常形态，**不是文档错误**。<br>💡 **教训（写给以后的自己）**：判"某个配置的生效值"**必须同时查 yml 与代码默认值** —— 只看 `AiProperties` 会得出"10 元"的错误结论（本次我第一版就判错了，靠 grep `daily-budget` 才发现）。<br>今日实耗 `ai:daily_cost:2026-09-11` = **0.011296 元** | §3.1 **保持"2 元/天"不变**，仅补 yml 行号与实耗佐证 |
| **D8c** | §四 内存 available 3.6G / 磁盘 49G(52%) | 实测 **available 3.4G**；磁盘 **51G(54%)、avail 44G**；**MySQL 数据目录仅 207M** | 已更新 §四，并补"**全量 6 库快照成本极低 → 快照兜底很轻**" |
| **D9** | §2.2.6 Redis 清单不完整 | 实测 `db0` 共 **25 个 key** = 12 个 `mall:seckill:sku:stock:*` + **4 个 `ai:*`** + **3 个 `spu:bloom:filter:2026-09-09/10/11`** + 6 个 `mall:seckill:spu:url:rand:code:*`；而 `mall:seckill:reseckill/ordered/orderLock:*` **当前各 0 个** | 已补全 §2.2.6；`SeckillCacheUtils` 键结构**本次未核对** → 文中标为"待核" |

> ✅ **复核为真（原稿这些关键事实全部通过，不用改）**：`cs_mall_sim` 不存在 ✅ · `test_sim_%` = 0 ✅ · 全库 **0 个外键** ✅ · 含 `user_id` 的表**正好 7 张** ✅ · `pms_spu.sales` = **81/1/1** ✅ · Redis 秒杀预热键 **12 个** ✅ · `ums_user` 110 / `oms_order` 86 / `success` 58（精确计数）✅ · 并发闸门 **20** ✅ · SSE 按 `data: ` 分片解析 ✅
> ✅ **额外正面结论（原稿没写，建议当成指标）**：`TokenBudgetService:60` 用 Redis `incr`（**原子操作**）→ **高并发下预算记账不会少记/多记**，可作为"AI 治理在高并发下仍正确"的实测结论。

### D. 两项决策（2026-09-11 用户拍板）

| 决策 | 内容 | 影响 / 落点 |
|---|---|---|
| **① 压测环境** | **生产 + 临时放开限流**（Nacos 热改 → 压 → 改回，零重建） | 保留"内网真压生产"的卖点（带宽/内存/容器编排都是真的）；执行剧本 **§3.5** |
| **② Agent 链路** | **两条链路各压一遍**：`AI_AGENT_ENABLED=false`（固定流水线，单轮 LLM）vs `true`（Agent，工具轮 + 收敛轮双轮） | 拿到"**Agent 双轮链路多付出的承载代价**"这组数字——**#32 上线后才可能获得的新素材**；前置是 mock 先支持工具轮（D2） |

---

## 〇.2 当前进度与交接（2026-09-11 晚 · **新会话/压缩上下文后从这里接续**）

> **用途**：本方案跨度长、决策多。本节是**唯一交接点** —— 决策、环境事实、待办、接续步骤全在这里。
> 接续时先读 **§〇.1（复核）+ §〇.2（本节）+ §五（执行顺序）+ §六（可观测展示）+ §2.2.9（标识设计）**。

### A. 今日已定决策（**勿重复讨论**）

| # | 决策 | 内容 |
|---|---|---|
| 1 | 造数路径 | **方案 A：直接调真实接口造数**（用户执行、AI 独立复核） |
| 2 | 商品扩容 | **商品 20→60、秒杀 6→12**；🔴 **新增商品视为「真实商品」、不打模拟标记**（编号跟随现有约定：`type_number`=`品牌缩写-型号-序号`、`bar_code`=`SKU-{spuId}-001`）→ 详见 [[商品与秒杀扩容方案]] |
| 3 | **模拟数据标识** | ✅ **用专用列 `data_source VARCHAR(16) NULL`**（`NULL`=常规/真实；`SIM`=模拟造数）—— **不再借用 `tag`/`data`**（借用写法**已撤回**，避免"一行挂两种标识"的新歧义） |
| 4 | 标识写入方式 | **造数后按登记表回填** `UPDATE … SET data_source='SIM' WHERE pk IN (登记主键)` → **零服务端代码改动** |
| 5 | **Flyway 纪律** | 🔴 **只加迁移文件、禁止手工 ALTER**（手工 ALTER + 迁移并存 → 服务重启时 `Duplicate column` → **应用起不来**） |
| 6 | 压测环境 | **生产 + 临时放开限流**（Nacos 热改、零重建）；**Agent 双链路各压一遍** |
| 7 | 展示优先级 | **可观测展示提为第 ② 步**（其中"浏览档"**无需 token**，可当天开录） |
| 8 | 造数分档 | **慢节奏造数 → 数据库沉淀**；**短时高峰 → 面板曲线**（两套流量，结论不能混着讲） |

### B. 环境事实（2026-09-11 实测，可直接用）

| 项 | 值 |
|---|---|
| 老机 / 新机 | `8.156.77.197`（私网 `172.29.193.239`，21 容器） / `47.109.70.197`（私网 `172.29.193.240`，**造数与压测执行位**） |
| **SSH 隧道（一条命令开全）** | `-L 8088:localhost:8088`（SkyWalking UI）`-L 8090:localhost:8090`（Sentinel Dashboard）`-L 8848:localhost:8848`（Nacos） |
| 造数脚本 | `deploy/scripts/sim/`（`simulate_data.py` / `init_sim_db.sql` / `requirements.txt` / `README.md`）；⚠️ **`deploy/` 被 gitignore → 改动需 `git add -f`** |
| 新机运行环境 | `python3 -m venv ~/sim-venv`（系统 pip 被 PEP 668 拦）；`pymysql` 未装需装；HTTP 走内网（新机→老机 3306/6379/10087 全通） |
| **Flyway 下一个可用版本号** | `cs_mall_ums=`**V3** · `cs_mall_oms=`**V7** · `cs_mall_seckill=`**V6** · `cs_mall_resource=`**V2**（pms 已到 V14、ams 已到 V6，本次不用） |
| Flyway 配置 | `enabled: true` + `baseline-on-migrate: true` + `baseline-version: 0`；迁移目录 `<模块>/src/main/resources/db/migration/`（**mall-resource 无 `-webapi` 后缀**） |
| 数据基线 | `cs_mall_sim` **不存在**（待建）· `test_sim_%`=**0** · 全库 **0 外键** · 在售 SKU **36** 个 / 库存约 **1456** 件 |
| 代码/分支 | `master` **ahead 3**（`6aafb1a` 交接章节 + `75f9dfb` 标识 + `0116001` §六；**未推送，等用户确认**）；⚠️ mall-ai 的"降级 + 启动自检"改动**仍未部署**（与本方案无关） |

### C. 待办清单（★ = **用户执行**，其余 AI 完成）

| # | 事项 | 谁 | 状态 |
|---|---|---|---|
| 1 | 4 个 **Flyway 迁移文件**：`ums V3` / `oms V7` / `seckill V6` / `resource V2`，各表加 `data_source` | AI 写 | ✅ **已落盘**（2026-09-11 晚；只加文件、未手工 ALTER） |
| 2 | 逐个**重启** `mall-ums` / `mall-order` / `mall-seckill` / `mall-resource` 使迁移生效（每个 ~40-60s，低峰） | ★用户 | ⏳ **待做（当前阻塞点）** |
| 3 | 脚本改「**按登记表回填 `data_source`**」+ **撤回**借用字段（`tag=SIM`、订单项 `data={"sim":…}`） | AI | ✅ **已完成**（新增 `backfill()` / `verify_backfill()`；借用字段已撤回；顺带修掉 `oms_payment_record` 漏删） |
| 4 | 执行前**只读核对**：9 张表是否已存在 `data_source`（存在则先决策，别硬跑迁移） | AI | ✅ **已完成**（实测 6 个 schema **0 个** `data_source` 列 → 迁移可安全执行） |
| 5 | 在**老机**建影子库（跑 `init_sim_db.sql`） | ★用户 | ⏳ 待做 |
| 6 | 给凭据 `export SIM_DB_PASSWORD=…` + **快照** `mysqldump` 六库（实测仅 ~207M） | ★用户 | ⏳ 待做 |
| 7 | **校准写链路**：`--days 1 --per-day 50`（约 2~3 单）→ 校准注册正则 / 加购下单字段 / 订单登记 / 拿 token | ★用户跑 · AI 复核 | ⏳ 待做 |
| 8 | **dry-run 清理演练**（清理 → 比对照基线） | ★用户 · AI 复核 | ⏳ 待做 |
| 9 | 写 `load_test.py` + 按 §6.4 三段式**录像**（浏览档可先做） | AI 写 · ★用户录 | ⏳ 待做 |
| 10 | 秒杀动作实现（`--with-seckill` 目前只做预检） | AI | ⏸️ 未实现（可选，看是否要造秒杀数据） |

> 🆕 **2026-09-11 晚 · 进度更新**：**C-1 / C-3 / C-4 已完成**（AI 侧全部做完，含自检：`py_compile` 通过 + md5 字节一致 + 结构断言 `STRUCT_CHECK_OK`）。
> 🔴 **现在轮到用户侧**：**C-2（重启 4 个服务让迁移生效）→ C-5（建影子库）→ C-6（快照）**，之后跑 C-7 校准。
> ⚠️ **顺序不可颠倒**：脚本已加"`data_source` 列缺失就 fail-fast"的门禁 → **迁移没生效前 `--preflight` 会直接拒绝执行**（这是有意的，避免造出无标识的数据）。

### D. 今晚目标

**主目标：把 §五 的 ①（校准写链路）跑通** —— 含标识回填与一次 dry-run 清理演练；
有余力再开 ② 的"浏览档"录像（不依赖 token，最快见效）。

### E. 接续时先做这 3 件事

1. 读 **§〇.1 / §〇.2 / §五 / §六 / §2.2.9**（这五节决定"怎么做"）
2. `git log --oneline -3` + `git status` 确认工作区状态（末次提交应为"#48 交接：专用列标识 + Flyway 方案"）
3. 从 **C-1 / C-3 / C-4** 开始（AI 写迁移 + 改脚本 + 只读核对），用户并行做 **C-5 / C-6**（建影子库 + 快照）

---

## 一、方案总览（两层解耦，可独立执行）

```
┌─ 第一层：模拟数据生成（业务数据积累）────────────────────┐
│  Python 脚本 → 登录/浏览/加购/下单/支付/秒杀               │
│      ├─ 写业务表（生产库，受控）                          │
│      └─ 写「影子登记表」cs_mall_sim.sim_entity（每个实体一行）│  ← 🆕 清理与审计的唯一依据
└───────────────────────────────────────────────────────┘
┌─ 第二层：AI 并发测试（不调真实 API）─────────────────────┐
│  Python 并发 → /ai/chat/stream（SSE）                    │
│  经内网打到服务器上的 mock LLM（替代 DeepSeek）            │
└───────────────────────────────────────────────────────┘
```

> **两层可分别执行**：第一层慢节奏、低风险；第二层（压测）对带宽/内存敏感。**建议先做第一层**（不受压测资源约束）。
> **硬安全网**：第一次真正造数**之前**必须先做 `mysqldump` 全量快照（§2.2.3）。

---

## 二、第一层：模拟数据生成

### 2.1 核心原则：按真实用户行为分布（不是纯随机）

| 行为 | 占比 | 说明 |
|------|------|------|
| 浏览/搜索商品 | 80% | 只 GET 商品列表/详情/搜索，不产生订单 |
| 加入购物车 | 15% | POST 购物车，但不结算 |
| 下单+支付 | 5% | 完整流程：下单 → 支付（模拟支付模式） |
| 秒杀 | 按需 | 对齐时间窗口，限购逻辑 |

> 每天 1000 条"行为"里，只有约 50 单——**这才像真实电商**（订单转化率约 5%）。
> 面试话术："我按真实用户漏斗（浏览→加购→下单 80/15/5）模拟了 N 天数据"

### 2.2 🔴 数据隔离设计（本次新增 · 核心）

#### 2.2.1 为什么必须要有隔离层

造数会**不可逆地**改动三类东西，靠"删掉模拟用户"**救不回来**：

| 类别 | 实测现状（2026-09-10） | 造数后果 | 能否靠删用户还原 |
|---|---|---|---|
| **累加计数器** | `pms_spu.sales`：81 / 1 / 1 … | 下单/秒杀**累加** sales | ❌ **不能**（没有"谁贡献多少"的记录） |
| **真实库存** | `pms_sku.stock`：30/25/20/15；`seckill_sku.seckill_stock`：25~150（1 个已 0） | 下单扣库存 | ❌ 不能（需人工补回，易算错） |
| **Redis 状态** | `mall:seckill:sku:stock:*` **12 个**预热库存键 | 秒杀扣减预热库存 | ❌ 不能（与实际库存偏差累积） |
| 可追踪实体 | `ums_user` 110 / `oms_order` 86 / `success` 58 | 新增用户/订单/购物车/成功记录 | ✅ 能（按登记精确删） |

> **结论**：可追踪实体用"登记表精确删"，**不可逆字段交给快照整体还原**。

#### 2.2.2 方案选型

| 方案 | 做法 | 规范度 | 成本 | 评价 |
|---|---|---|---|---|
| **A. 影子库 / 影子表** | 造数写 `cs_mall_sim` 或 `*_sim` 表，服务切数据源 | ★★★★★ | 🔴 **极高**——11 个服务的表名散在 Mapper XML/注解里，要动态表名或多数据源改造，**远超造数本身** | ❌ 本项目不划算 |
| **B. 快照回滚** | dump → 造数 → 事后**整库还原** | ★★★★☆ | 🟢 低——**项目已有验证过的能力**（#29 cron 备份 + #47 独立临时容器恢复演练） | ✅ **兜底主线** |
| **C. 影子登记表** | 独立 schema `cs_mall_sim` 建 `sim_batch` / `sim_entity`，记录每个被创建实体的主键 | ★★★★☆ | 🟢 低——**不改 6 个业务库的表结构**，只加一个独立库 2 张表 | ✅ **清理主线** |

#### 2.2.3 推荐组合 = **C（清理主线）+ B（兜底）**

```
① 快照      造数前：mysqldump 6 库全量（复用 #29 脚本）→  备份号写进 sim_batch 备注
② 登记      造数时：每创建一个实体，往 cs_mall_sim.sim_entity 写一行（批次 + 库 + 表 + 主键）
③ 清理      按登记表**逆序**删（子表→主表）+ 分批 + 幂等 + dry-run 预览
④ Redis     按登记的用户 id **精确删**（禁止 pattern 全删）
⑤ 计数器    sales / stock / 秒杀预热键**不做"减回去"** → 需要绝对干净时走 ① 的**整库还原**
⑥ 校验      删完对比"基线快照"（行数 + 计数器），有差异即报告，不静默
```

> 这样既**避开了方案 A 的天坑**（不改 11 个服务的表名），又拿到了影子表的核心价值：**可审计、可重放、可精确回滚、生产表零结构变更**。
> 计数器这类"累加不可逆"字段交给快照整库还原——这正是"影子"思想在数据层的等价物：**先存真身，再在真身上动作，事后整体还原**。

#### 2.2.4 影子登记表 DDL（独立 schema，与业务库隔离）

```sql
-- 独立库：不碰 6 个业务库的任何表结构（实测 2026-09-10：cs_mall_sim 尚不存在）
CREATE DATABASE IF NOT EXISTS cs_mall_sim DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 批次：一次造数运行
CREATE TABLE IF NOT EXISTS cs_mall_sim.sim_batch (
  batch_id     VARCHAR(40)  NOT NULL COMMENT '批次号，如 sim_20260911_1530',
  started_at   DATETIME     NOT NULL,
  finished_at  DATETIME     NULL,
  days         INT          NOT NULL DEFAULT 0 COMMENT '模拟天数',
  per_day      INT          NOT NULL DEFAULT 0 COMMENT '每天行为数',
  done_actions BIGINT       NOT NULL DEFAULT 0,
  dump_file    VARCHAR(255) NULL COMMENT '造数前全量快照文件名（兜底用）',
  status       VARCHAR(16)  NOT NULL DEFAULT 'running' COMMENT 'running/finished/cleaned',
  note         VARCHAR(500) NULL,
  PRIMARY KEY (batch_id)
) ENGINE=InnoDB COMMENT='模拟数据批次（影子登记）';

-- 实体登记：每个被创建的实体一行 —— 清理的唯一依据
CREATE TABLE IF NOT EXISTS cs_mall_sim.sim_entity (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  batch_id   VARCHAR(40) NOT NULL,
  db_name    VARCHAR(64) NOT NULL COMMENT 'cs_mall_ums / cs_mall_oms / ...',
  table_name VARCHAR(64) NOT NULL,
  pk_value   VARCHAR(64) NOT NULL COMMENT '主键值（字符串存，兼容雪花 ID / 自增）',
  user_ref   VARCHAR(64) NULL     COMMENT '关联的模拟用户名，便于按用户精确清 Redis',
  created_at DATETIME    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_batch (batch_id),
  KEY idx_target (db_name, table_name),
  KEY idx_user (user_ref)
) ENGINE=InnoDB COMMENT='模拟实体登记（影子表）';
```

#### 2.2.5 清理算法（逆序 · 分批 · 幂等 · 可预览）

**表依赖顺序（子 → 父，逆序即删除顺序）**——实测表名（2026-09-10）：

```sql
-- 删除顺序（严格按此逆序；全库无外键，漏一张就静默残留）
-- 1) cs_mall_oms.oms_order_item            （按 order_id 关联，表本身无 user_id）
-- 2) cs_mall_seckill.success               （有 user_id）
-- 3) cs_mall_seckill.seckill_message_retry （有 user_id）
-- 4) cs_mall_oms.oms_payment_record        （有 user_id）
-- 5) cs_mall_oms.oms_cart                  （有 user_id）
-- 6) cs_mall_oms.oms_order                 （有 user_id）
-- 7) cs_mall_resource.res_upload_record    （有 user_id；本方案若不传图可跳过）
-- 8) cs_mall_ums.ums_login_log             （有 user_id）
-- 9) cs_mall_ums.ums_user                  （按 username LIKE 'test_sim_%'）
```

**算法要点**：

| 点 | 做法 |
|---|---|
| **驱动** | 以 `sim_entity(batch_id, db, table, pk)` 为准，**不靠 LIKE 猜** |
| **逆序** | 按上表顺序 1→9；`oms_order_item` 用登记到的 order_id 反查 |
| **分批** | 每批 `LIMIT 500`，避免长事务锁表 |
| **幂等** | 重复执行不报错（删不到就跳过）；`sim_batch.status` 置 `cleaned` |
| **dry-run** | 默认只**打印**将删除的行数（`SELECT COUNT(*)`），加 `--apply` 才真删 |
| **事务** | 单批内事务；失败即停并报告（不静默继续） |
| **顺序修正** | 🔴 绝不"先删用户再按用户查订单"（初稿的 bug，见 §〇①） |

#### 2.2.6 Redis 清理（精确删，禁止 pattern 全删）

```bash
# ❌ 禁止（会删掉所有用户的购买标记，误伤真实数据）
# redis-cli --scan --pattern 'mall:seckill:reseckill:*' | xargs redis-cli del

# ✅ 正确：先取出本批次的模拟用户 id 列表，再按 id 拼接精确 key 删除
#    键结构以 SeckillCacheUtils 为准：<prefix>:<skuId>:<userId>
#    reseckill / ordered / orderLock 三类均按 batch 登记的用户逐一 DEL
```

> 涉及键（`SeckillCacheUtils`）：`reseckill`、`ordered`、`orderLock`；另有 `mall:seckill:sku:stock:*`（**预热库存，属不可逆**，交快照兜底，见 §2.2.7）。

> 🔴 **2026-09-11 实测补全：key 全清单（原稿只列了一部分，见 §〇.1 D9）**
> 生产 Redis `db0` 实测共 **25 个 key**，按"造数会不会碰"分三类：
>
> | 类别 | key | 数量 | 造数/清理怎么处理 |
> |---|---|---|---|
> | **不可逆（快照兜底）** | `mall:seckill:sku:stock:*` | **12** | ❌ 不删（删了与真实库存更不一致）→ 需要绝对干净时整库还原 + 重新预热 |
> | **模拟用户会话（按 id 精确删）** | `ai:chat:session:<sid>` / `ai:chat:user:<uid>` / `ai:agent:action:<sid>` / `ai:daily_cost:<date>` | **4** | 前三类按登记 user id / session 精确删；`ai:daily_cost:*` **是全局记账，绝不删** |
> | **与造数无关（不要碰）** | `spu:bloom:filter:<date>` / `mall:seckill:spu:url:rand:code:*` | **3 + 6** | 造数不新增 SPU 则 bloom 无需重建；随机码映射是秒杀入口数据，**删了会坏秒杀** |
>
> 而 `mall:seckill:reseckill:*` / `ordered:*` / `orderLock:*` **实测当前各 0 个**（按需生成 / 已过期）→ 清理脚本**保留**这三类的删除逻辑，但实际大概率是 no-op。
> ⚠️ **动手前必做**：`redis-cli --scan | sort` 对一遍全清单，**不要凭本文档的名字直接 `del`**。
> ⏳ **待核**：`<prefix>:<skuId>:<userId>` 的拼接顺序引自初稿，**2026-09-11 复核未读 `SeckillCacheUtils`** —— 实施前请 grep 确认。

#### 2.2.7 不可逆字段的处理原则

**不做"减回去"运算**——`sales` 是累加、库存扣减还并发（补偿运算既易错又不安全）。

| 字段 | 处置 |
|---|---|
| `pms_spu.sales` | 接受偏差；演示要求"干净"时走整库还原 |
| `pms_sku.stock`（38 SKU / 合计 **1456**）/ `seckill_sku.seckill_stock`（12 条 / 合计 **822** / max 150） | 同上 |
| Redis `mall:seckill:sku:stock:*`（**12 个**） | 同上（还原后需重新预热，走 `SeckillInitialJob`） |

#### 2.2.8 造数 SOP（三段式）

```
【造数前】
 1. 确认基线干净：SELECT COUNT(*) FROM cs_mall_ums.ums_user WHERE username LIKE 'test_sim_%'  → 0（2026-09-10 实测 = 0 ✅）
 2. mysqldump 6 库全量 → 记录文件名到 sim_batch.dump_file
 3. 记录"基线快照"（行数 + 计数器）到临时表/文件，供清理后比对
 4. 确认低峰时段（避开每日 12 点高峰窗口）

【造数中】
 5. 按 80/15/5 慢节奏执行；每创建一个实体写一行 sim_entity
 6. 实时观察 docker stats / free -h

【造数后】
 7. dry-run 清理预览 → 确认影响行数
 8. 需要干净环境 → 走整库还原（复用 #47 的独立临时容器验证法先验证备份可用）
 9. 记录结果到 sim_batch（finished / cleaned）
```

### 2.2.9 🏷️ 模拟数据的标识设计（让数据"自证身份"，2026-09-11 新增）

> **为什么需要**：数据造出来后如果没有标记，它就**和真实数据长得一模一样** —— 事后无法回答"这行到底是不是造的"。
> **本项目 = 四级标识 + 一个权威**：

| 级别 | 承载字段 | 值 | 说明 |
|---|---|---|---|
| 人眼可辨 · 用户 | `ums_user.username` | `test_sim_0001` | **前缀即身份** |
| 人眼可辨 · 文案 | `ums_user.nickname` / `email` / `oms_order.contact_name` / `detailed_address` | `模拟用户0001` / `test_sim_0001@example.com`（**保留域名**，误发也发不出去） / `模拟用户1234` / `模拟地址 5 号` | 打开单据就知道是造的 |
| 人眼可辨 · 号段 | `oms_order.mobile_phone` / `seckill.success.user_phone` | **`1390000xxxx`** | **假号段**：能过手机号正则、又不撞真实号码 |
| ✅ **字段标识（新）** | **9 张表统一加 `data_source`** | **`NULL`**（常规/真实）· **`SIM`**（模拟造数） | 🔴 **2026-09-11 定稿**：专用列 = 契约（等值可查、可索引、零副作用）；**造数后按登记表回填**。⚠️ 早先"借用 `tag`/`data`"的写法**已撤回**（见下"关键设计选择"） |
| **机器权威** | **`cs_mall_sim.sim_entity`** | 批次 + 库 + 表 + 主键 + `user_ref` | **清理与审计的唯一依据** —— 字段标识只是"便于人看/便于粗筛"，**权威始终是登记表** |

#### 🔴 关键设计选择：**用专用列 `data_source`（2026-09-11 定稿）**

> **决策过程**：初版想"复用现有自由字段（`tag`/`data`）"以求**零 DDL**；**用户 2026-09-11 拍板改为专用列**，理由是**避免歧义**（`tag` 本是展示标签、`data` 本是"商品全属性 json"，借用会让同一行挂两种语义）。**借用写法已撤回。**

| 方案 | 做法 | 评价 |
|---|---|---|
| ✅ **采用：专用列 `data_source`** | 9 张表各加 `data_source VARCHAR(16) NULL COMMENT '数据来源：NULL=常规；SIM=模拟造数(#48)'`；造数后按登记表回填 `'SIM'` | 语义是**契约而非借用** · 等值可查**可索引** · 零副作用 · 可扩展（`SIM`/`LOADTEST`/`REPLAY` = 数据血缘） |
| ❌ **已撤回**：借用自由字段 | `oms_order.tag='SIM'` + `oms_order_item.data={"sim":…}` | 零 DDL、零服务端改动，但**语义借用**、且 `tag` 会在订单页显示成标签 → **两套标识反而更易混** |

##### 🔴 Flyway 纪律（**这一步做错会让服务起不来**）

| 做法 | 结果 |
|---|---|
| ❌ **手工 ALTER + 又写迁移文件** | 服务重启 → Flyway 执行该迁移 → **`Duplicate column name 'data_source'`** → **Flyway 失败 → 应用启动失败** |
| ❌ 只手工 ALTER、不写迁移 | 不报错，但**新环境建库缺列**（环境漂移）；将来补迁移时老环境**又会重复列报错** |
| ✅ **只加迁移文件（采用）** | Flyway 启动时执行一次并写入 `flyway_schema_history`（带 checksum）→ **天然幂等**；新老环境一致 |

> ⚠️ MySQL 8 **不支持** `ADD COLUMN IF NOT EXISTS` → "SQL 层幂等"这条路不通，**幂等只能靠 Flyway 的记录**；
> 因此**执行前必须只读核对"这 9 张表还没有 `data_source` 列"**（有则先决策，别硬跑）。

**4 个迁移文件（✅ 2026-09-11 已落盘；版本号已按实测 `flyway_schema_history` 核对为"下一个可用号"）**

```sql
-- mall-ums/mall-ums-webapi/src/main/resources/db/migration/V3__add_data_source_to_ums.sql
ALTER TABLE `ums_user`       ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '数据来源：NULL=常规；SIM=模拟造数(#48)';
ALTER TABLE `ums_login_log`  ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '数据来源：NULL=常规；SIM=模拟造数(#48)';

-- mall-order/mall-order-webapi/src/main/resources/db/migration/V7__add_data_source_to_oms.sql
ALTER TABLE `oms_order`           ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '…';
ALTER TABLE `oms_order_item`      ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '…';
ALTER TABLE `oms_cart`            ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '…';
ALTER TABLE `oms_payment_record`  ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '…';

-- mall-seckill/mall-seckill-webapi/src/main/resources/db/migration/V6__add_data_source_to_seckill.sql
ALTER TABLE `success`                ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '…';
ALTER TABLE `seckill_message_retry`  ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '…';

-- mall-resource/src/main/resources/db/migration/V2__add_data_source_to_res_upload_record.sql
ALTER TABLE `res_upload_record`   ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '…';
```

> ✅ **表名不带库名前缀** —— 与现有全部迁移一致（Flyway 用模块 datasource 的默认库；实测 `V5` 建的 `seckill_message_retry` 就落在 `cs_mall_seckill`）。带前缀也能跑，但会把"库名"硬编码进迁移、降低环境可移植性。
> ✅ **列追加在表末尾**（不用 `AFTER`）—— 避免依赖某个具体列名，新老环境不会因此漂移。
> ✅ **只读预检已实测通过（2026-09-11）**：`information_schema.columns` 中 6 个 schema **0 个** `data_source` 列 → 迁移可安全执行。

> **索引**：**先不加**（数据量小；"按来源反查"是运维场景而非在线查询，将来量大再加 `KEY idx_data_source (data_source)`）。
> **回滚**：`ALTER TABLE … DROP COLUMN data_source` + 删除 `flyway_schema_history` 对应行（列可空、无人读 → 回滚零影响）。
> **值谁写**：**造数脚本按登记表回填**（服务端零改动）；**按什么键回填见下「回填矩阵」**（4 张脚本写的按主键、5 张服务端写的按 `user_id`）；回填后必须校验 **`data_source='SIM'` 行数 = 登记表条数**（不等即漏标）。
> ✅ **改用专用列后，之前"`oms_cart`/`ums_login_log`/`res_upload_record` 没有合适自由字段"的限制消失了** —— **9 张表全部都有 `data_source`**。

#### 🔄 回填矩阵（9 张表谁写的 / 按什么键回填）—— 2026-09-11 只读实测后补

> 🔴 **`data_source` 不是"脚本 INSERT 时顺手写上"就完事**：9 张表里**只有 4 张是脚本直接写**的，另外 **5 张是服务端在业务链路中写的**（登录 / 支付 / 秒杀 / MQ 重试 / 上传），脚本**根本没有它们的插入点** → 必须按登记表**兜底回填**，否则这 5 张永远是 `NULL`。

| 表 | 谁写 | 回填依据 | 依据从哪来 |
|---|---|---|---|
| `cs_mall_ums.ums_user` | 脚本 | `id IN (登记 pk)` | 脚本主动登记 ✅ |
| `cs_mall_oms.oms_cart` | 脚本 | `id IN (登记 pk)` | ✅ |
| `cs_mall_oms.oms_order` | 脚本 | `id IN (登记 pk)` | ✅ |
| `cs_mall_oms.oms_order_item` | 脚本 | `id IN (登记 pk)` | ✅ |
| `cs_mall_ums.ums_login_log` | **服务端**（登录） | `user_id IN (登记 user_ref)` | 登记表 |
| `cs_mall_oms.oms_payment_record` | **服务端**（支付） | `user_id IN (登记 user_ref)` | 登记表 |
| `cs_mall_seckill.success` | **服务端**（秒杀） | `user_id IN (登记 user_ref)` | 登记表 |
| `cs_mall_seckill.seckill_message_retry` | **服务端**（MQ 重试） | `user_id IN (登记 user_ref)` | 登记表 |
| `cs_mall_resource.res_upload_record` | **服务端**（上传） | `user_id IN (登记 user_ref)` | 登记表 |

> ✅ **实测幸运点（2026-09-11 只读核对 `information_schema`）**：这 5 张"服务端写的"表**全部都有 `user_id`**（唯一没有 `user_id` 的是 `oms_order_item`，它已由脚本按主键登记）→ 所以回填**统一用 `user_id IN (登记 user_ref)`**，键可靠、**不需要 JOIN 订单**。
> 🔴 **连带修复一处清理漏洞**：`oms_payment_record` 原先在清理清单里挂的是 `pk`（按登记主键）模式，但**脚本从不登记支付记录** → 清理时会走"无登记 → 跳过" → **支付记录静默残留**（全库 0 外键，删漏不会报错）。已改为 `user`（按 `user_id`）模式，与回填口径统一。

#### 🔍 反查 SQL（标识只有配上"反查"才有用）

```sql
-- ① ✅ 主口径：专用列（9 张表等值查询；将来量大再加 KEY idx_data_source(data_source)）
SELECT 'ums_user'            AS t, COUNT(*) AS n FROM cs_mall_ums.ums_user                WHERE data_source='SIM'
UNION ALL SELECT 'ums_login_log',      COUNT(*) FROM cs_mall_ums.ums_login_log           WHERE data_source='SIM'
UNION ALL SELECT 'oms_order',          COUNT(*) FROM cs_mall_oms.oms_order               WHERE data_source='SIM'
UNION ALL SELECT 'oms_order_item',     COUNT(*) FROM cs_mall_oms.oms_order_item          WHERE data_source='SIM'
UNION ALL SELECT 'oms_cart',           COUNT(*) FROM cs_mall_oms.oms_cart                WHERE data_source='SIM'
UNION ALL SELECT 'oms_payment_record', COUNT(*) FROM cs_mall_oms.oms_payment_record      WHERE data_source='SIM'
UNION ALL SELECT 'seckill.success',    COUNT(*) FROM cs_mall_seckill.success             WHERE data_source='SIM'
UNION ALL SELECT 'seckill_msg_retry',  COUNT(*) FROM cs_mall_seckill.seckill_message_retry WHERE data_source='SIM'
UNION ALL SELECT 'res_upload_record',  COUNT(*) FROM cs_mall_resource.res_upload_record   WHERE data_source='SIM';

-- ② 权威口径：影子登记表（① 的每一行都应能在它里面找到对应主键）
SELECT db_name, table_name, COUNT(*) AS n FROM cs_mall_sim.sim_entity
 WHERE batch_id='sim_20260911_1530' GROUP BY db_name, table_name;

-- ③ 🔴 漏标检查（**回填校验的核心**：登记了却仍是 NULL = 漏标 → 必须返回 0 行）
--    逐表 LEFT JOIN 反查；示例给 oms_order，其余 8 张同构（换库名 / 表名 / 主键列）
SELECT COUNT(*) AS 漏标行数 FROM cs_mall_oms.oms_order o
  JOIN cs_mall_sim.sim_entity r
    ON r.db_name='cs_mall_oms' AND r.table_name='oms_order'
   AND r.batch_id='sim_20260911_1530' AND r.pk_value = CAST(o.id AS CHAR)
 WHERE o.data_source IS NULL OR o.data_source <> 'SIM';

-- ④ 人眼可辨档（辅助交叉核对：应与①指向同一批行）
SELECT COUNT(*) FROM cs_mall_ums.ums_user  WHERE username LIKE 'test_sim_%';
SELECT COUNT(*) FROM cs_mall_oms.oms_order WHERE mobile_phone LIKE '1390000%';
```

> ⚠️ **本节已随定稿重写**：初版给的是"借用 `tag` / `data` / `extra_data`"的 7 条 SQL，**已随借用方案一并撤回**（否则文档自相矛盾：正文说用专用列、反查却查自由字段）。

> **边界（用户 2026-09-11 明确）**：**新增的商品数据视为真实商品** —— `pms_spu` / `pms_sku` **不打 `SIM` 标记**，编号也**跟随现有约定**（`type_number` = `品牌缩写-型号-序号`，如 `MI-14-001`；`bar_code` = `SKU-{spuId}-001`），避免被 `SIM-` 前缀暴露"这是造的"。详见 [[商品与秒杀扩容方案]]。

### 2.3 脚本骨架（含登记 + dry-run）

```python
"""
simulate_data.py — CoolShark 业务数据模拟器（规范设计版）
用法:
  python simulate_data.py --days 30 --per-day 1000            # 造数（登记每个实体）
  python simulate_data.py --clean --batch sim_20260911_1530    # 预览将清理什么（dry-run）
  python simulate_data.py --clean --batch ... --apply          # 真正删除
"""
import argparse, os, random, time, uuid, datetime, requests, pymysql

BASE        = os.environ.get("SIM_BASE", "http://172.29.193.239:10087")  # ⚠️ 内网 + Gateway；脚本必须在内网跑（5Mbps 约束，见 §3.4）
USER_PREFIX = "test_sim_"
BATCH       = "sim_" + datetime.datetime.now().strftime("%Y%m%d_%H%M")

# 🔴 凭据不落盘（2026-09-11 复核新增，见 §〇.1 D6）：密码只从环境变量取，
#    运行前 export SIM_DB_PASSWORD=...（或改用 ~/.my.cnf 权限 600）
SIM_DB = dict(host=os.environ.get("SIM_DB_HOST", "172.29.193.239"), port=3306,
              user=os.environ.get("SIM_DB_USER", "root"),
              password=os.environ["SIM_DB_PASSWORD"],
              database="cs_mall_sim", charset="utf8mb4")

def register(db, table, pk, user_ref=None):
    """影子登记：每个被创建的实体写一行 —— 清理的唯一依据"""
    with pymysql.connect(**SIM_DB) as c, c.cursor() as cur:
        cur.execute(
            "INSERT INTO sim_entity(batch_id,db_name,table_name,pk_value,user_ref,created_at)"
            " VALUES(%s,%s,%s,%s,%s,NOW())", (BATCH, db, table, str(pk), user_ref))
        c.commit()

def login(username, password="123456"):
    r = requests.post(f"{BASE}/user/sso/login", json={"username": username, "password": password})
    return r.json()["data"]["token"]

def browse(token, spu_ids):  ...        # 80%：只读，不产生业务数据
def add_cart(token, sku_id, qty):  ...  # 15%：登记 oms_cart.id
def create_order(token, sku_id, qty, contact):  # 5%：登记 oms_order.id / oms_order_item.id
    ...
def seckill(token, sku_id, rand_code):  ...     # 登记 success.id

def main(days, per_day, clean, batch, apply_):
    if clean:
        preview_or_delete(batch, apply_)   # 逆序 + 分批 + 幂等；不 apply 则只打印行数
        return
    # 建批次行 → 预置 test_sim_ 用户（登记 ums_user.id）→ 按 80/15/5 循环
    ...
```

> 🔴 **运行环境（2026-09-11 实测，见 §〇.1 D6）**：新机已有 `python3 3.12.3` + `requests 2.31.0`，但 **`pymysql` 未装、且 `pip install` 被 PEP 668 拒绝**、也**没有 `mysql` 客户端**。
> 正确姿势：`python3 -m venv ~/sim-venv && ~/sim-venv/bin/pip install -r requirements.txt`
> `requirements.txt`（**版本钉死**，避免"在我机器上能跑"）：`requests==2.31.0` / `PyMySQL==1.1.*`
> DDL 与快照**不走脚本**，仍在老机用 `docker exec csmall-mysql` 执行。
> ✅ **通路已实测**：新机 → 老机 `3306` / `6379` / `10087` **全通**，网关 `/actuator/health` **26ms**（私网，不限速不计费）。

### 2.4 关键设计点（必须遵守）

| 点 | 说明 |
|----|------|
| **可清理** | 三层保障：`test_sim_` 前缀（人眼可辨）+ **影子登记表**（机器精确）+ **快照**（兜底不可逆字段） |
| **秒杀对齐窗口** | 当前 6 场全年有效（2026-01-01~12-31），脚本随机时间即可；跨年后需先更新窗口 |
| **秒杀限购** | 同一用户同一 SKU 支付后**永久不能再买**（`reseckill` 永久标记 + `success` 唯一索引）→ 脚本要换 SKU 或换用户。⚠️ 已知局限见 TODO **#3**（场次维度购买标记） |
| **支付用模拟模式** | 项目已支持模拟支付（`simulated: true`），脚本走模拟支付即可，不碰支付宝沙箱 |
| **节奏控制** | 默认慢节奏（sleep 随机 0.5~3s），模拟真实用户；需要并发时再开多线程（§3.4） |
| **幂等** | 下单请求 contactName 用随机值，避免 `@Idempotent` Key 冲突 |
| **跑哪台** | 🔴 脚本+数据库客户端都在**新机/内网**（§3.4） |

### 2.5 清理清单（精确到表）

```sql
-- 以批次驱动（示意；实际由 sim_entity 生成 IN 列表并分批）
-- 1
DELETE FROM cs_mall_oms.oms_order_item  WHERE order_id IN (<本批 order_id 列表>);
-- 2
DELETE FROM cs_mall_seckill.success     WHERE user_id IN (<本批 user_id 列表>);
-- 3
DELETE FROM cs_mall_seckill.seckill_message_retry WHERE user_id IN (<本批 user_id 列表>);
-- 4
DELETE FROM cs_mall_oms.oms_payment_record WHERE user_id IN (<本批 user_id 列表>);
-- 5
DELETE FROM cs_mall_oms.oms_cart        WHERE user_id IN (<本批 user_id 列表>);
-- 6
DELETE FROM cs_mall_oms.oms_order       WHERE user_id IN (<本批 user_id 列表>);
-- 7
DELETE FROM cs_mall_resource.res_upload_record WHERE user_id IN (<本批 user_id 列表>);
-- 8
DELETE FROM cs_mall_ums.ums_login_log   WHERE user_id IN (<本批 user_id 列表>);
-- 9（最后）
DELETE FROM cs_mall_ums.ums_user        WHERE username LIKE 'test_sim_%';
```

> ⚠️ **演示前是否清理要想清楚**：压测卖点（100 并发 0 超卖）依赖干净数据；几万条模拟订单会让列表页变慢、秒杀"已购买"误伤。
> ⚠️ 但**"造了数据"本身就是面试素材**（"我按真实漏斗造了 N 天运营数据"）——建议**保留一份"演示数据快照"**，需要干净环境时再还原。

---

## 三、第二层：AI 并发测试

### 3.1 为什么不能直接压真实 API

- **有真实成本**：`TokenBudgetService` 全局 **2 元/天**预算（**生效值 = `application.yml:86` `daily-budget: 2.0`**；代码兜底默认 `AiProperties:104 = 10.0` 被 yml 覆盖 → 2026-09-11 复核澄清，见 §〇.1 D8b），压 1000 并发瞬间打满 → 真实使用没额度。**今日实耗佐证**：`ai:daily_cost:2026-09-11` = **0.011296 元**（日常量级）
- **速度瓶颈在 DeepSeek**：SSE 流式接口的响应速度取决于 DeepSeek 处理时间，**不是你的服务能力**——压出来的"慢"是模型慢，不是后端慢

### 3.2 正确做法：内网 mock LLM，测服务端承载

| 要素 | 正确做法 | 为什么 |
|---|---|---|
| **mock 放哪** | 部署在**新机** `172.29.193.240:9999`（内网，老机可达 —— 2026-09-11 实测新机→老机网关 `10087` **26ms**） | 容器内 `127.0.0.1` = **容器自身**；放开发机则要么走公网（5Mbps）要么访问不到 |
| **怎么接** | **compose override 文件**注入：**`AI_API_BASE_URL=http://172.29.193.240:9999`** + **`AI_API_KEY=sk-mock`**；测完 `docker compose up -d mall-ai` 恢复 | **不改 prod yml、不入库**；一次 `up -d mall-ai` 即可回滚。<br>🔴 **2026-09-11 复核（D1）：初稿的 `COOXIAO_AI_BASEURL` / `COOXIAO_AI_APIKEY` 根本不生效** —— yml 实际读 `${AI_API_BASE_URL}` / `${AI_API_KEY}`（`application.yml:22-23`）；**且 compose 里 `mall-ai` 未透传 `AI_API_BASE_URL`**（`docker-compose.yml:545-571` 实测只有 `AI_API_KEY` / `AI_AGENT_*`）→ **必须先给 compose 加一行** `AI_API_BASE_URL: ${AI_API_BASE_URL:-https://api.deepseek.com}`，否则 override 的值进不了容器。**压测前必查**容器内 base-url 已变（否则这一轮打的是真实付费 API） |
| **mock 必须实现什么** | ① **非流式**：`choices[0].message.content`（供 `doChat`/`chatWithModel`）<br>② 🔴 **流式 SSE**：`data: {...}\n\n` 分片 + 末尾 `data: [DONE]`（供 `/ai/chat/stream`）<br>③ 🔴 **Agent 工具轮（2026-09-11 新增，D2）**：`delta.tool_calls` 分片（首片带 `id`/`name`，后续片只带 `arguments`，**按 `index` 拼接**）<br>④ 带 `usage` 的末尾 chunk（`stream_options.include_usage=true`） | SSE 解析实测在 **`DeepSeekAiClient.openSseStream:349`**（`line.startsWith("data: ")`）—— **返回普通 JSON 会收不到任何 chunk**；生产 `AI_AGENT_ENABLED=true` → 不带 `tool_calls` **测的就不是生产链路**（§〇.1 D2） |
| **压什么接口** | `/ai/chat/stream`（SSE 长连接，最主要的承载瓶颈） | 见 §3.3 |

```python
# mock_llm.py（骨架 v2 · 2026-09-11 复核后修订）
#   🔴 D5：必须 ThreadingHTTPServer —— 单线程 HTTPServer 会把并发 SSE 串行化，压到的是 mock 不是服务端
#   🔴 D2：必须实现 tool_calls —— 生产 AI_AGENT_ENABLED=true，Agent 首轮带 tools（商品意图还带 tool_choice=required）
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json, time

CONTENT = "这是一条模拟的 AI 导购回复，用于并发测试。"

def sse(w, obj):
    w.write(f"data: {json.dumps(obj, ensure_ascii=False)}\n\n".encode())
    w.flush()

def tool_call_round(w, name, args):
    """工具轮：按实测契约分片（首片带 id/name，后续片只带 arguments）"""
    sse(w, {"choices": [{"delta": {"tool_calls": [
        {"index": 0, "id": "call_sim_1", "type": "function",
         "function": {"name": name, "arguments": ""}}]}}]})
    for p in [args[i:i+6] for i in range(0, len(args), 6)]:      # 拆片，模拟真实流（补册实验 K：实测 19 片）
        sse(w, {"choices": [{"delta": {"tool_calls": [
            {"index": 0, "function": {"arguments": p}}]}}]})
        time.sleep(0.005)
    sse(w, {"choices": [{"delta": {}, "finish_reason": "tool_calls"}]})
    sse(w, {"choices": [], "usage": {"prompt_tokens": 50, "completion_tokens": 30}})
    w.write(b"data: [DONE]\n\n"); w.flush()

def content_round(w):
    """收敛轮：逐字回 content"""
    for piece in [CONTENT[i:i+6] for i in range(0, len(CONTENT), 6)]:
        sse(w, {"choices": [{"delta": {"content": piece}}]})
        time.sleep(0.05)                                        # 模拟逐字输出
    sse(w, {"choices": [], "usage": {"prompt_tokens": 50, "completion_tokens": 30}})
    w.write(b"data: [DONE]\n\n"); w.flush()

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"        # 长连接：SSE 必须，否则连接复用行为不真实
    def log_message(self, *a):
        pass                             # 压测时不要刷日志（刷日志本身会拖慢 mock）

    def do_POST(self):
        n = int(self.headers.get("Content-Length", 0))
        req = json.loads(self.rfile.read(n) or b"{}")
        tools = req.get("tools") or []
        # 已经调过工具 → 本轮必须收敛（复刻生产"第 1 轮调工具 → 第 2 轮收敛"）
        used = any(m.get("role") == "assistant" and m.get("tool_calls") for m in req.get("messages", []))
        if req.get("stream"):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Connection", "keep-alive")
            self.end_headers()
            if tools and not used:
                tool_call_round(self.wfile, tools[0]["function"]["name"], '{"keyword": "手机"}')
            else:
                content_round(self.wfile)
            return
        time.sleep(0.3)
        body = json.dumps({"choices": [{"message": {"content": CONTENT}, "finish_reason": "stop"}],
                           "usage": {"prompt_tokens": 50, "completion_tokens": 30}}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers(); self.wfile.write(body)

# 🔴 线程化 + 端口：mock 并发能力必须 ≥ 闸门 20（concurrent-max: 20），最好 ≥ 阶梯峰值
ThreadingHTTPServer(("0.0.0.0", 9999), Handler).serve_forever()
```

> 🔴 **mock 的三条硬性要求（2026-09-11 新增，见 §〇.1 D5）**
> 1. **必须线程化**（`ThreadingHTTPServer` / `socketserver.ThreadingMixIn`）—— 单线程版本会把 20 条并发 SSE **串行化**，压出来的是 mock 的吞吐；
> 2. **并发能力 ≥ 20（闸门值），最好 ≥ 阶梯峰值 100**；
> 3. **先单独压 mock**（固定并发打 9999）拿到 P50/P99 与吞吐，**证明 mock 不是瓶颈之后**再压 mall-ai —— 否则压测数字不可信。
>
> ⚠️ 本骨架是**设计稿**：`tool_calls` 分片契约以 [[TODO第三批实现与原理-1]] §3.1 **实验 K（实测 19 片）** 为准，实施时先用真实请求对齐一次再压。

**方案 B（备选）**：不走接口，脚本直接写 `ai:chat:session:*` 到 Redis——只测存储层，**价值低**，不推荐作为主路径。

### 3.3 要测的指标（面试可讲）

| 指标 | 意义 |
|------|------|
| 并发 SSE 连接数上限 | Tomcat 默认 200 线程，SSE 长连接占线程 → 线程池是主要瓶颈 |
| **并发闸门命中率** | `AiConcurrencyGuard` 上限 **20**（实测 `application.yml` `concurrent-max: 20` + `AiProperties:130`），超出走降级（`AiBusyException`）→ 这是**真实保护点**，要量化"多少并发开始降级" |
| 🔴 **每用户频控（干扰项，必须先排除）** | `user-rate-limit: 10` / 60s（`AiProperties:136-139` 实测开启）→ **同一 token 第 11 个请求起全部 429**，测到的是频控不是闸门。压测**必须用 N ≥ 阶梯峰值个不同模拟用户**（§〇.1 D3） |
| 🔴 **Sentinel 入口限流（干扰项）** | `ai-chat=5 QPS`（`deploy/docker/sentinel/mall-ai-flow-rules.json`）→ 处理剧本见 **§3.5** |
| 单请求内存增量 | SSE 流式输出 + 会话 JSON 在内存中累积 |
| Redis 写入 QPS | `SessionManager.save` 每次对话写 Redis（24h TTL） |
| **预算记账正确性（新增，正面结论）** | `TokenBudgetService:60` 用 Redis `incr`（**原子**）→ 高并发下不会少记/多记，可作"AI 治理在高并发下仍正确"的实测结论 |
| 内存峰值 | 见 §四 |

> ⚠️ **指标顺序原则（2026-09-11 补）**：**先证伪干扰项（Sentinel 5 QPS / 每用户频控 10）→ 再测真实承载**。否则量到的"降级点"是限流器的，不是并发闸门的。

### 3.4 🆕 真实并发模拟（2026-09-09 用户补充需求）

**目标**：真实并发 ~100 req/s + **每日 12 点高峰窗口**（cron 定时触发）。

**三条硬约束（必须遵守）**：

| 约束 | 原因 | 做法 |
|---|---|---|
| 🔴 **脚本跑在新机/内网** | 老机 **5 Mbps 固定带宽**，本机压自己 = 自压自伤，数据无意义 | 脚本部署 `172.29.193.240`，打老机**内网 IP** `172.29.193.239`（不限速不计费）；**禁止走公网 IP**。〔2026-09-11 实测该通路可用：3306/6379/10087 全通、网关 26ms〕 |
| 🔴 **负载画像要避开限流** | Sentinel 已拦：秒杀 QPS=**10**、`ai-chat`=**5** / `ai-reason`=**10** / `ai-light`=**30**（规则实测见 `deploy/docker/sentinel/mall-ai-flow-rules.json`）→ 压这些接口只会得到 429，测不到服务能力 | **承载指标**：主压**浏览 / 加购 / 普通下单**（无限流规则）；**限流指标**：秒杀/AI 单独验证（期望 429），不作为承载数字 |
| 🔴 **阶梯加压 + 盯资源** | 老机 4C16G、available **3.4G**（2026-09-11 实测） | **20 → 50 → 100** 阶梯；每档盯 `docker stats` + `free -h`；**命中中止阈值立即停**（阈值见 §3.5） |

**与"造数"的关系**：造数是**慢节奏、长时间**沉淀数据；高峰压测是**短时高压**验证承载。**两者用同一套脚本的不同模式**，但**不要同时跑**。
> ⚠️ **一处内部矛盾已澄清（2026-09-11）**：§2.2.8 要求造数"避开 12 点高峰"，而本节要求 cron 在 12 点触发高峰 —— 二者**不是同一件事**：**造数是长时间低频**（必须避峰），**高峰压测是受控短时演练**（刻意选高峰，且只压无业务意义的浏览接口）。规则是：**造数避峰、压测选峰、两者不同时**。

### 3.5 🆕 生产压测执行剧本（2026-09-11 用户决策：生产 + 临时放开限流 + 双链路各压一遍）

> **为什么选"生产"而不是"本地起 mall-ai"**：竞品价值恰恰在**真实的 4C16G / 容器编排 / 真实中间件 / 内网拓扑**；本地压本地拿不到这些结论。
> **为什么放开限流是安全的**：**mock 生效后 LLM 流量根本不进真实 API**（base-url 已指向新机 mock）→ 放开 `ai-chat` 阈值**几乎没有成本风险**，这是本决策成立的关键前提。**D1 没做对，这条就不成立。**

**前置（缺一不可，全部有验证方式）**

| # | 动作 | 验证方式 |
|---|---|---|
| 1 | compose 给 `mall-ai` 加 `AI_API_BASE_URL` 透传 + override 指向 mock（`172.29.193.240:9999`） | `docker inspect csmall-ai` 看到 `AI_API_BASE_URL=http://172.29.193.240:9999`；**且 mock 侧计数真的涨了** |
| 2 | **备份 Nacos 规则原文**（`mall-ai-flow-rules`）到文件 + 记 md5 | `curl ".../configs?dataId=mall-ai-flow-rules&group=SENTINEL_GROUP"` 输出存盘 + `md5sum` |
| 3 | 构造 **N 个模拟用户 token**（N ≥ 阶梯峰值，建议 120） | 脚本预登录 → token 列表落文件 + 抽查 3 个能 200 |
| 4 | mock 已按 §3.2 v2（`ThreadingHTTPServer` + 工具轮） | **单独压 mock** 拿 P50/P99 与吞吐，证明 mock 不是瓶颈 |
| 5 | 记录基线 `free -h` / `docker stats --no-stream` | 落文件（对比用） |

**执行（每档停 3~5 分钟，不要一跳到底）**

```
① Nacos 热改 ai-chat 阈值 5 → 200（只改这一条）→ 立即 GET 复核已生效（热更新，零重建）
② 阶梯 20 → 50 → 100：
   a. 先 AI_AGENT_ENABLED=false（固定流水线，单轮 LLM）
   b. 再 AI_AGENT_ENABLED=true （Agent，工具轮 + 收敛轮 = 双轮）
   每档盯：docker stats / free -h / mock 侧并发计数 / mall-ai 日志的 429 与异常
   中止阈值（任一命中立即停）：available < 1.5G ｜ 5xx+429 占比 > 1% ｜ 单档 p99 > 10s
③ 恢复：Nacos 改回 count=5 → GET 复核 → 抽查一次真实请求确认 429 行为回归
④ 恢复 mall-ai 环境变量（移除 override）→ recreate → 验证 base-url 已回到 api.deepseek.com
```

**回滚点（两个，互相独立）**：① Nacos 规则原文（第 2 步落盘）；② `AI_API_BASE_URL` / `AI_AGENT_ENABLED` 改回 + `docker compose up -d mall-ai`（~45s）。
**顺序的意义**：先压**流水线（单轮）**再压 **Agent（双轮）**，两组的吞吐比值就是"**Agent 这条双轮链路额外付出的承载代价**"——这是 #32 上线后才可能拿到的素材。
**成果回填**：每档结果写进 `sim_batch.note` 或独立的"压测结果表"（指标 → 数字 → 结论），否则一个月后只剩"压过"两个字。

---

## 四、服务器资源评估（2026-09-11 实测更新）

| 指标 | 实测值 | 状态 |
|---|---|---|
| 总内存 | **15.0 GiB**（`free -m` total 15070） | — |
| available | **3.4 GiB**（2026-09-11 13:32；09-10 为 3.6 GiB；R7 前为 2.1 GiB） | 🟢 可用（R7 已生效） |
| Swap | **2.0 GiB**（R7 前为 0） | 🟢 有缓冲垫 |
| 容器 mem_limit | **全 21 容器已设**（R7）；`docker ps` 实测 **21 个容器 Up** | 🟢 单容器失控不会吃满宿主 |
| 磁盘 | 99G / 已用 **51G（54%）**、**avail 44G**（09-10 为 49G/52%） | 🟢 充裕（造数前先看增长） |
| **MySQL 数据目录** | **仅 207M**（`docker exec csmall-mysql du -sh /var/lib/mysql`） | 🟢 **6 库全量 `mysqldump` 成本极低 →"快照兜底"这条路很轻**，可放心每次造数前都做 |
| 带宽 | **5 Mbps 固定** | 🔴 压测必须走内网 |
| **新机（压测执行位）** | python3 3.12.3 / mem total 7.5G、available **6.2G** / 磁盘 40G 用 4.4G | 🟢 余量充足；→ 老机内网 3306/6379/10087 **全通**，网关 **26ms** |

**结论（对照初稿评估）**：

| 场景 | 可行性 | 说明 |
|---|---|---|
| 模拟数据生成（慢节奏，1 请求/秒级） | ✅ 可行 | 负载低；造数时关注磁盘与表增长。**库存预算已量化**：38 SKU 合计 1456 件，100 单仅占 7%~20%（§〇.1 D7） |
| AI 并发 20（闸门上限） | ✅ 可行 | 闸门=20，超出即降级——**这本身就是可讲的指标** |
| AI 并发 50~100 | ⚠️ 需阶梯试探 | 先确认 `free -h` **available ≥ 1.5G**（中止阈值）；必要时临时停 SkyWalking（省 ~1.5G），测完恢复 |

> 初稿的"内存大头表"（Nacos 980M / product 945M / OAP 1.1G / ES 1.1G）为 **2026-08-26 快照**；R7 已对 Nacos 降堆并给全部容器加 mem_limit，实际占用已变化，**以现场 `docker stats` 为准**。

---

## 五、执行顺序（2026-09-11 **按"可录像展示"重排**）

> **重排原则**：**先拿到"能在面板上看见、能录像"的成果**，再补"数据沉淀"，最后才做投入最大的 AI 并发。
> 🔴 **一条硬依赖**：要展示"**限流生效（block）**"必须压**已埋点的写接口**（`新增订单` / `秒杀订单提交`）→ 需要 **token + 合法下单/秒杀参数** → 而这正是"造数写链路校准"要产出的东西。**所以 ① 必须在 ② 之前。**
> 🟢 **另一条好消息（§6.2 实证）**：只想展示"**流量变化（pass）**"的话，**压浏览 URL 即可、不需要 token** → 这一档可以**当天就录**。

```
【① 校准写链路（~1h · 必做第一步，也是 ② 限流档的前置）】
 1. 老机建影子库：执行 deploy/scripts/sim/init_sim_db.sql
 2. 给凭据：export SIM_DB_PASSWORD=…（脚本只从环境变量取，不落盘）
 3. 快照先行：mysqldump 六库全量（实测仅 ~207M，成本极低）
 4. 小规模试跑：--days 1 --per-day 50（约 2~3 单），只校准 4 件事：
      · 注册接口的用户名/密码正则能否过
      · 加购 / 下单的字段是否被接受（金额、data、mainPicture）
      · 订单 id / 订单项 id 能否正确登记（清理全靠它）
      · 登录能否拿到可用 token（② 的限流档要用）
    → 通过后 dry-run 清理一次，确认"造得出、也删得干净"
 ★ 产出：一条可复用的「登录 → 加购 → 下单 → 支付」链路 + 可用 token

【② 可观测展示与录像（~半天 · ★ 本方案初衷的交付物）】
 5. 写 load_test.py：先做"浏览档"（**无需 token**）→ 20 → 50 → 100 阶梯
 6. 开 SSH 隧道（§6.1）→ 按 §6.4 三段式录：静默 → 加压（SkyWalking 曲线 + 拓扑）→ 收尾
 7. 用 ① 的 token 加"**限流档**"：压 新增订单 / 秒杀订单提交 过 QPS(20 / 10)
    → 录 Sentinel 的 **block 曲线**
 ★ 产出：可展示的视频素材（SkyWalking 调用量/拓扑/Trace + Sentinel pass/block）

【③ 扩商品 20→60（可选，但建议放在 ④ 之前）】
 8. 按 [[商品与秒杀扩容方案]] 方案 A 执行（直接写库 → 重启 mall-ai 全量同步）
 理由：库存池 1456 件 → 数千件，**造数规模不再受卡**；录屏时商品卡片也更丰富

【④ 正式造数（1~2 天 · 数据沉淀）】
 9. 慢节奏造数（每天 1000 行为）→ 检查数据沉淀 + 登记表行数对得上
10. dry-run 清理预览 → 演练一次"清理 → 比对照基线"

【⑤ AI 并发压测（~1 天 · 投入最大，放最后）】
11. mock LLM 落文件并测通（ThreadingHTTPServer + tool_calls 工具轮）
12. compose 加 AI_API_BASE_URL 透传（§〇.1 D1）+ override 指向 mock
13. 备份 Nacos 规则（落盘 + md5）→ 热改 ai-chat 阈值 → 构造 N ≥ 120 个模拟用户 token
14. 阶梯 20 → 50 → 100：先 AI_AGENT_ENABLED=false（流水线单轮），再 true（Agent 双轮）
    每档盯 docker stats / free -h；命中中止阈值（available<1.5G / 5xx+429>1% / p99>10s）立即停
15. 测完：Nacos 改回 + GET 复核 → 移除 override → recreate → 验证 base-url 已回真实地址
 ★ 产出：Agent（双轮）vs 固定流水线（单轮）的承载对比

【收尾】
16. 结果回填：sim_batch.note 或独立"压测结果表"（指标 → 数字 → 结论）
17. 决定：保留"演示数据" / 或整库还原到快照（复用 #47 独立容器先验证备份）
```

> ~~初稿的"步骤 2：服务器内存优化 R7，找 ecs-user 执行"~~ —— **R7 已于 2026-09-07 执行完毕，该步删除**。
> **改动史**：2026-09-11 首轮补入 venv / mock 地址必查 / Nacos 备份 / 中止阈值 / 结果回填（对应 §〇.1 D1~D9）。
> 🆕 **2026-09-11 二次重排**：把「**可观测展示与录像**」提为 **第 ② 步**（它才是本方案的**初衷交付物**，且 §6.2 实证表明**浏览档无需 token、可当天开录**），原「AI 并发」降为 **第 ⑤ 步**（投入最大、且 `ai-chat` 只有 5 QPS，展示直观性不如订单/秒杀）。

---

## 六、可观测展示与录像 SOP（2026-09-11 新增 · ★ 对应本方案的"最初出发点"）

> **为什么单列一章**：本方案最初的出发点其实是 ——
> **"用 Python 模拟客户行为 → 在 Sentinel / SkyWalking 上看到数据量与变化 → 录制视频 / 演示"**。
> 但原稿只写了"造数据"与"压测取数"，**没有把「可观测展示」当成交付物**。本章把它补上。
>
> 🔴 **必须先纠正的一个认知（否则白干）**：**慢节奏造数在面板上几乎看不见** —— §2 的造数是 0.5~3 秒一个动作 ≈ **0.5 QPS**，SkyWalking 上就是一条贴地的平线，Sentinel 上基本是 0。
> **"数据量"与"变化"是两件事、要两套流量**：
> **造数（慢节奏）→ 数据沉淀（进数据库）**；**短时高峰（并发）→ 面板曲线（进 Sentinel / SkyWalking）**。

### 6.1 面板清单与访问方式（端口为 2026-09-11 实测）

| 面板 | 容器 / 端口映射 | 隧道后地址 | 展示时看什么 |
|---|---|---|---|
| **SkyWalking UI** | `csmall-skywalking-ui` / `0.0.0.0:8088→8080` | http://localhost:8088 | ① 服务列表与**调用量曲线**（Service → Load）② **拓扑图**（压测时链路会亮起来）③ **Trace 明细**（点开每一笔的真实链路与耗时）④ JVM / GC |
| **Sentinel Dashboard** | `csmall-sentinel` / `0.0.0.0:8090→8858` | http://localhost:8090 | ① **实时监控**里按资源的 **pass QPS 曲线** ② 触发限流时的 **block** ③ 簇点链路 |
| Nacos（规则事实来源） | `csmall-nacos` / `8848` | http://localhost:8848 | `mall-*-flow-rules` 规则原文 |
| SkyWalking OAP API（可选） | `csmall-skywalking-oap` / `12800` | — | 脚本化取数（GraphQL `getAllServices` 等） |

```bash
# 一条命令开全（本机执行；key 路径见 CLAUDE.md §3.2）
ssh -i "%USERPROFILE%\AppData\Local\csmall-ssh\ai-deepseek_key" `
    -L 8088:localhost:8088 -L 8090:localhost:8090 -L 8848:localhost:8848 `
    ai-deepseek@8.156.77.197
```

### 6.2 ✅ 实证：压**浏览**接口能不能在 Sentinel 看到？——**能**（本轮专门验证的结论）

> 这个问题原稿没写、也不敢猜，**本轮去实证了**；结论对展示设计影响很大。

| 证据 | 内容 |
|---|---|
| 依赖 | 生产 jar 的 `BOOT-INF/lib/` 内实测存在 **`sentinel-spring-webmvc-v6x-adapter-1.8.8.jar`**（另有 webflux / reactor 适配器） |
| **启动日志（决定性）** | `c.a.c.s.SentinelWebMvcConfigurer - [Sentinel Starter] register SentinelWebInterceptor with urlPatterns: [/**]` |
| **结论** | **所有 URL 都被埋成 Sentinel 资源** → 压 `/front/spu/list/all`、`/front/spu/{id}` 这类**纯浏览接口**（**不需要登录 token**）就能在 Dashboard 看到该 URL 的 **pass QPS 曲线** |

🔴 **但必须分清"两档展示"**（这是最容易混的地方）：

| 想展示 | 要压什么 | 需要 token？ |
|---|---|---|
| **流量变化（pass 曲线）** | **浏览类 URL 即可**（`/front/spu/list/all`、`/front/spu/{id}`） | ❌ **不需要** |
| **限流生效（block 曲线）** | **已埋点 + 有规则的写接口**：`秒杀订单提交`(QPS **10**) / `新增订单`(**20**) / `支付订单`(**20**)；AI 侧 `ai-chat`(**5**) / `ai-reason`(**10**) / `ai-light`(**30**) | ✅ **需要**（token + 合法下单/秒杀参数） |

> ⇒ **展示可以分级做**：**先做"无需 token 的浏览档"**（当天即可录像），**再加"需要 token 的限流档"**（依赖 §五 步骤 ① 校准出的链路与 token）。
> ⚠️ **规则资源名不是 URL** —— 上面 6 条规则的 `resource` 是**注解埋点名**（`@SentinelResource`），与 URL 资源是**两套并存**；改规则要改 Nacos 里的 `mall-*-flow-rules`。

### 6.3 SkyWalking 侧已具备的条件（实测）

| 项 | 证据 |
|---|---|
| agent 已挂 | 容器 ENTRYPOINT 内含 `-javaagent:/skywalking-agent/skywalking-agent.jar -DSW_AGENT_NAME=mall-front` —— **agent 烘在镜像里**（不是靠 `JAVA_TOOL_OPTIONS`） |
| 已注册服务 | **12 个业务服务**：`mall-front / mall-gateway / mall-order / mall-seckill / mall-seckill-2 / mall-product / mall-search / mall-sso / mall-ums / mall-resource / mall-ams / mall-ai`（另有 Redis / MySQL 作为依赖节点） |
| 已在持续采集 | `sw_metrics-all-20260911` **260351** 条 · `…0910` **608104** 条 · `…0909` **546781** 条 |
| 链路必然可见 | 压 `/front/**` 会经过 **gateway → front →（Dubbo / ES / MySQL）**，**拓扑与 Trace 都会亮** |

### 6.4 🎬 "平地起峰"三段式录制脚本（录像的关键）

> 曲线要**有对比**才好看 —— 先有一段静默，"起来"才看得见。

```
① 静默基线（录 30~60s）：不压任何流量，录下面板贴地的平线
② 阶梯加压（录 2~3min）：20 → 50 → 100 并发（= §3.4 的阶梯）
                        盯 SkyWalking Load 曲线抬升 + 拓扑亮起；Sentinel 对应资源 pass QPS 抬升
③ 撞限流（录 30~60s）  ：并发打 `秒杀订单提交` / `新增订单`，压过 10 / 20 QPS
                        → Sentinel 出现 block 曲线（这才是"限流生效"的展示）
④ 收尾（录 30s）      ：停压，录曲线回落 + 打开 Trace 看那一批请求的链路与耗时
```

**录像机位建议**：三个窗口并排 —— **SkyWalking Load 曲线** + **Sentinel 实时监控** + **脚本滚动日志**（能看到 RPS / 成功率 / 耗时），观众才会信这是真打的。

### 6.5 ⚠️ 展示前必须知道的 4 个边界

1. **慢节奏造数 ≠ 面板可见**：要曲线就必须有并发（§2 与 §6.4 是**两套流量**，别混着讲）
2. **限流档要"打得进业务"**：`新增订单` 需要合法地址 / 金额 / 联系人与 token；`秒杀订单提交` 还需要 **randCode** 且在场次窗口内 → **依赖写链路校准（§五 步骤 ①）**
3. **别把"演示"当"承载测试"**：撞限流是为了展示保护生效，**不是**测承载能力；两者结论不同、**不能混着说**（§3.3 已强调"先证伪干扰项再测承载"）
4. **成本**：浏览档**不碰 AI → 零 token 成本**；AI 档才需要 mock LLM（推荐）或真实额度

### 6.6 讲解话术（配合录像）

> "我造数据分两种流量：**一种慢节奏灌数据**，让数据库看起来像在运营；**另一种短时高峰**，专门验证系统的承载与保护。
> 演示时我先静默一分钟录一条平线，再阶梯加压 —— 你们能看到 **SkyWalking 的服务调用量和拓扑实时亮起来**，Trace 里能点开每一笔真实链路的耗时；再加压打到**秒杀下单**，**Sentinel 的 block 曲线就起来了**，这就是限流在保护订单服务。
> 一次演示同时讲清了两件事：'数据怎么来' 和 '系统扛不扛得住、什么时候会拦'。"

---

## 七、纪律与安全清单（实施时逐条打勾）

- [ ] **默认 dry-run**：清理必须 `--apply` 才真删；先看行数预览
- [ ] **快照先行**：第一次造数前必须有当日/即时 `mysqldump`（6 库全量，实测仅 ~207M）
- [ ] **低峰执行**：避开每日 12 点高峰窗口与维护窗口（**高峰压测是另一件事**，见 §3.4 说明）
- [ ] **基线确认**：跑前 `test_sim_%` = 0；跑后登记表行数 = 实际新增实体数
- [ ] 🆕 **fail-fast 预检**：`cs_mall_sim` 存在 / 前缀=0 / 快照文件在 / `SUM(pms_sku.stock)` 够本批消耗 → **任一不过直接退出**
- [ ] 🆕 **凭据不落盘**：DB 密码走环境变量或 `~/.my.cnf`(600)，脚本内不写明文
- [ ] 🆕 **回填矩阵**：9 张表按"脚本写的按主键 / 服务端写的按 `user_id`"回填 `data_source='SIM'`（§2.2.9）
- [ ] 🆕 **回填校验**：`data_source='SIM'` 行数 **=** 登记表条数；漏标检查 SQL 返回 **0 行**（§2.2.9 ③）
- [ ] **禁止 pattern 删 Redis**：只按登记用户 id 精确删；**动手前先 `--scan | sort` 对一遍全清单**（§2.2.6）
- [ ] **清理顺序**：严格 9 张表逆序；分批 500；单批事务
- [ ] **不可逆字段不硬算**：`sales`/`stock`/预热键 交给快照还原，不做补偿运算
- [ ] **清理后校验**：行数对比基线；有差异**报告而非静默**
- [ ] **压测走内网**：脚本在新机；禁止公网 IP；禁止老机自压
- [ ] 🆕 **mock 先自测**：单独压 mock 拿 P50/P99 与吞吐，证明 mock 不是瓶颈后再压服务端
- [ ] 🆕 **mock 地址必验证**：压测前确认容器内 `AI_API_BASE_URL` 已是 mock（**否则打的是真实付费 API**）
- [ ] 🆕 **mock 必须线程化**：`ThreadingHTTPServer`，并发能力 ≥ 闸门 20（最好 ≥ 阶梯峰值）
- [ ] 🆕 **并发用多用户**：≥ 峰值并发个不同模拟用户，绕开每用户频控 10/60s
- [ ] 🆕 **Nacos 规则先备份**：`mall-ai-flow-rules` 原文落盘 + 记 md5；改完立即 GET 复核；测完改回
- [ ] 🆕 **中止阈值**：available < 1.5G ｜ 5xx+429 占比 > 1% ｜ 单档 p99 > 10s → 立即停
- [ ] **阶梯加压**：20 → 50 → 100；每档看 `docker stats` + `free -h`
- [ ] 🆕 **结果回填**：每档指标 → 数字 → 结论写进 `sim_batch.note` 或结果表（不然只剩"压过"两个字）
- [ ] **测完恢复**：移除 override、重建 mall-ai、恢复 SkyWalking（若停）
- [ ] **`cs_mall_sim` 不入业务库迁移**：它是**运维/测试辅助库**，不进任何服务的数据源
- [ ] 🆕 **录像拍"平地起峰"**：必须先录 30~60s **静默基线**再加压，否则曲线没有对比（§6.4）
- [ ] 🆕 **两档流量分开说**：**展示档**（短时高峰 → 面板曲线）与**承载档**（§3.4 阶梯 → 闸门/降级点）**结论不同，不能混着讲**（§6.5）
- [ ] 🆕 **限流档要打得进业务**：压 `新增订单`/`秒杀订单提交` 需要 token + 合法参数 + randCode（§6.2 / §五 ①）
- [ ] 🆕 **隧道端口**：`-L 8088`（SkyWalking UI）/ `-L 8090`（Sentinel）/ `-L 8848`（Nacos），三个面板并排录（§6.1）

---

## 八、面试话术（可直接用）

> "造演示数据这事儿，我没有直接往生产库里灌——那会污染三个**不可逆**的东西：商品销量、真实库存、还有 Redis 的秒杀预热库存，事后删用户根本救不回来。我做了一套**规范隔离**：造数前先全量快照；造数时每创建一个实体就往一个**独立影子登记库** `cs_mall_sim` 写一行，清理完全由登记表驱动——**逆序删、分批、幂等、默认 dry-run**；Redis 也是按登记的模拟用户 id **精确删**，不是按 pattern 全删（那会误伤真实用户的购买标记）。至于销量/库存这种累加值我不做'减回去'的补偿运算，容易算错还不安全，直接走快照整库还原兜底。
> 至于"怎么认出哪些是造的"，我**没有去蹭订单上现成的 `tag` 字段**——那会让同一行同时挂两种语义（展示标签 + 数据来源），反而更容易混。我**给 9 张表加了专用的 `data_source` 列**：`NULL` = 常规数据、`SIM` = 模拟造数，等值可查、能索引、将来还能扩展成 `LOADTEST`/`REPLAY` 这种数据血缘。值不是我手写的，**由造数脚本按影子登记表回填**——而且 9 张表里有 5 张是服务端在链路里写的（登录日志、支付记录、秒杀成功…），**脚本根本没有插入点，只能靠登记表兜底回填**，这一步不做它们就永远是空的；服务端代码一行都没改。
> 压测我也是把脚本放在**内网另一台机器**上打的——生产带宽只有 5Mbps，本机压自己是自压自伤，数据没意义。另外我**没有把"限流触发"当成承载指标**：承载测试要回答的是"系统自己什么时候扛不住"，而入口那道 Sentinel 阈值会先把请求挡掉、把结论污染成"限流很有效"——所以我把阈值**临时放开**（Nacos 热改、测完改回、零重建）先排除这道干扰；限流本身我另外单独做了一组"故意撞闸门"的验证，用来展示保护确实生效。**这两件事结论不同，不能混着说。**"

> "AI 并发这条路我踩过坑才想明白：**同一台服务上其实叠了三道保护，不先把干扰项证伪，压出来的数字是假的**——入口 Sentinel `ai-chat` 只有 5 QPS，还有每用户 60 秒 10 次的频控，再往里才是并发闸门 20。所以我先临时把入口阈值放开（**Nacos 热改，测完改回，零重建**），再用**一批不同的模拟用户**绕开频控，才真正量到"第几个并发开始降级"。压的时候也没打真实模型——内网放了个 mock LLM，用 compose override 临时把 base-url 指过去，**不改生产配置、一次 recreate 就能回滚**；而正因为走了 mock，放开限流几乎没有成本风险。最后还有个意外收获：Agent 那条链路每题要调两次模型（先调工具、再收敛），我对着固定流水线又各压了一遍，量出了**这条双轮链路多付出的那部分承载代价**。"

---

**关联文档**：[[TODO文件]]#48 / #3（场次维度购买标记）/ #31（向量检索）、[[TODO已完成]]#29（cron 备份）/ #47（恢复演练）、[[AI导购Agent升级方案]]（#32，Agent 可消费本方案造的丰富商品数据）、[[服务器内存优化方案]]（R7，已归档）、[[阿里云ECS服务器情况]]、[[TODO第三批实现与原理]]（两台服务器与内网拓扑）
