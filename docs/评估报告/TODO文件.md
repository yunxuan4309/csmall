# CoolShark 项目待办事项

> **创建日期**: 2026-05-13
> **最后更新**: 2026-09-08（**第二批 #29 数据库备份完成并部署**——第二批全部完成！剩余见第三批/暂缓区；#46 nacos 卷挂载待办入第三批）
> **关联文档**: [[TODO已完成]]（已完成归档）、[[服务器巡检与待修复问题清单-2026-08-04]]、[[JVM调优方案]]、[[阿里云ECS服务器情况]]、[[Redis配置加固与哨兵模式方案]]、[[服务器内存优化方案]]、[[连接池统一HikariCP方案]]、[[Python模拟数据与AI并发测试方案]]、[[集群化与配置中心迁移方案]]、[[Sentinel能力补充计划]]、[[Redis主从切换防数据问题方案]]、[[TraceId链路日志规范方案]]、[[认证安全企业级升级方案]]

---

## 🎯 执行路线图（2026-09-07 重组，按"从止血到演进"排序）

> **用法**：明天起从头逐条评估/实现时，按下面三个批次推进；每条详细方案见正文对应编号（编号未变）。
> **原则**：第一批解决"出事会真出事"的（安全洞/资源红线）；第二批解决"用户可见 bug + 面试/演示价值"；第三批是"企业级展望"，演示项目可后置。

### ✅ 第一批：安全 + 资源止血（2026-09-07 已全部完成，明细已迁 [[TODO已完成]]）

> 第一批全部 5 项（#25 / R7 / #24 / R1~R4 / #38）已完成，明细与实战经验已迁 [[TODO已完成]] §一/§二/§四。**下一批主攻 = 🔥 第二批（下方）**。

### 🔥 第二批：正确性 + 面试/演示价值（2026-09-08：#33/#8/#36/#23/#14P0+P1/#5P0/#2+#34 全部完成并部署；仅剩运维批 #13/#29）

| 顺序 | 编号 | 事项 | 状态（2026-09-08） |
|------|------|------|-----------|
| 1 | **#33** | 双索引数据不一致 | ✅ **A2 落地 + 部署完成 + 服务器验证通过**（统一索引 + mall-search 只读降级层 + 同步模型补全 + 前端 fallback；AI 索引 19 条无下架残留、普通搜索返回 9 条、图片 URL 完整） |
| 2 | **#8** ✅ | AI 预算按北京时间结算 | ✅ 已完成 + **已部署**（TokenBudgetService 时区）→ 见 [[TODO已完成]] |
| 3 | **#36** ✅ | DLX 死信 + OrderQueueConsumer requeue 修复 | ✅ 已完成 + **已部署**（x-death 限次重试 + 订单队列 DLX + OrderDlxConsumer）→ 见 [[TODO已完成]] |
| 4 | **#13** | Nacos 开启认证 | ✅ 完成 + **已部署**（2026-09-08：认证开启 + 11 服务全客户端同步；实测 403/真 JWT/注册正常；补 nacos 数据卷防重建丢配置）→ 见 [[TODO已完成]] |
| 5 | **#14** 🟡 | Redis 主从切换防数据（五层） | ✅ P0 三层 + P1 对账已完成 + **已部署**（含 Flyway V6 order_type）；只余 P2 配置层（随 #9） |
| 6 | **#29** | 数据库定期备份 | ✅ 完成 + **已部署**（2026-09-08：backup-db.sh + cron 02:30 + 仓库留档;实测 6 库 39 表;恢复演练待做）→ 见 [[TODO已完成]] |
| 7 | **#23** ✅ | 漏触发接口补 @Validated | ✅ 已完成 + **已部署**（DTO 补规则 + 全局异常补全）→ 见 [[TODO已完成]] |
| 8 | **#5** ✅ | Sentinel 能力补齐 | ✅ **P0 完成 + 已部署**（2026-09-08：统一 Nacos 管理 flow+degrade、order/sso 加 datasource、seckill 代码规则改兜底、eager 修复 transport 懒加载、规则 JSON 入库 `deploy/docker/sentinel/`；服务器实测限流生效 30 并发 20×429）；P1 热点/P2 集群未做 → 见 [[TODO已完成]] |
| 9 | **#2 + #34** | AI 接口限流 + 并发闸门 | ✅ **完成 + 已部署**（2026-09-08：Sentinel 3 组规则 + Semaphore 并发闸门 + 每用户频控；实测 30 并发 → 10×200+20×429、频控 15 连打全 429）→ 见 [[TODO已完成]] |

> **部署状态（2026-09-08）**：✅ **第二批已全量部署上线**——11 个微服务 jar 全量重建（common/pojo 连带）+ Flyway V6 + 前端 dist + ES 索引清理全部完成，21 容器 Up、Nacos 全注册。**部署中额外发现并修复 mall-ai 既有 bug**：AI 重排偶发降级/超时，真因 = reasoning 模型过度思考致 content 截断（踩坑记录见 [[TODO第二批实现与原理]] §5.4.5 边界表 #21），解法 = JSON 任务用 `deepseek-chat`、SSE 对话保留 `v4-flash`。**第二批剩余（2026-09-08 晚）**：#5 ✅ 已完成并部署（Nacos 规则 + 3 容器重建 + 实测 429 生效）；剩余待做：#13/#29/#2+#34。

### 🟢 第三批：企业级演进 + 学习（演示项目可后置，按兴趣/时间取用）

| 编号 | 事项 | 定位 |
|------|------|------|
| #4 | 秒杀集群化 + 配置中心（单机 2 实例演示级） | 面试高价值，需先做 R7 腾内存 |
| #14-P1 | Redis 主从防数据 - P1 对账任务 | ✅ 已完成（2026-09-07，运行期轻量+凌晨全量，以 DB 为准）→ 见 [[TODO已完成]] §六 |
| #14-P2 | Redis 主从防数据 - P2 配置层（min-replicas-to-write 1，随 #9 主从哨兵） | 搭 #9 的车，避免遗漏 |
| #9 | Redis 主从 + 哨兵实验 | 学习 HA，方案已定稿 |
| #15 | K8s 实操（k3s） | 学习用，需评估新服务器（见 TODO 顶部咨询结论） |
| #39 | 镜像仓库 + CI/CD 流水线 | 企业级交付 |
| #40 | 微服务 actuator healthcheck | 可观测基础课 |
| #41 | 日志聚合 Loki | 可观测闭环 |
| #30 | Prometheus + 告警 | 根治静默故障 |
| #16/#22/#26 | TraceId 落日志 / CORS 收敛 / 网络隔离 | 企业级细节 |
| #35 | 统一 Jackson（替换 fastjson） | 安全 + 规范 |
| **#45** | **统一 Dubbo 应用名规范**（front/search/ams 仍撞名但无 provider） | 规范项：3 模块 `dubbo.application.name` = spring 名，但**不暴露 @DubboService**（无 20880 实例，lb:// 实测安全）→ 本次不改（避免回归面）；统一为 `*-dubbo` 后缀防未来加 provider 时踩坑 |
| **#46** | **nacos 数据卷挂载重启**（2026-09-08 记录，低优先） | 运维项：compose 已加 `nacos_data:/home/nacos/data` 卷（本地已提交），但**服务器 nacos 容器仍是无卷状态**（derby 582M 在容器可写层，重建即丢）。待执行：备份 derby → `docker volume create nacos_data` → 临时容器中转拷数据 → 重建 nacos 挂卷 → 验证认证/规则/登录仍在。执行指令在对话记录（或按 #13 部署清单 §补充）。⚠️ **下次任何动 nacos 的操作前必须优先做这个** |

### ⏸️ 明确暂缓/仅评估（不实现，面试讲认知即可）

| 编号 | 事项 | 说明 |
|------|------|------|
| #10/#11/#12/#18/#21/#27 | Lua/ZSET/Redis集群/购物车缓存/BFF补强/KMS | 均为"未来商业化/规模化"评估，当前不做 |
| #3/#19/#20 | 秒杀活动管理/价格库存校验/死权限码 | P2，演示场景影响小，面试讲清即可 |
| #42 | 跨机"真集群"演示（Redis HA+秒杀双实例） | 仅评估：面试真集群演示，优先级低待实际需要 |
| #7 | HTTPS/SSL | 阻塞链=域名+ICP备案+预算，外部条件限制 |
| 低区 #2/#3/#4/#5/#6 | 图片打包/RedisBloom/微信支付/支付宝/压测演示 | 见低优先级区原文 |

---

## ⏸️ 已确认问题（演示项目暂缓修复，2026-08-21 巡检确认）

> **背景**：2026-08-21 对生产服务器（8.156.77.197）做 Redis 专项巡检 + 配置事实核查。以下问题全部经服务器实测确认（docker inspect / redis-cli CONFIG GET / 与本地 `deploy/docker/docker-compose.yml` 比对）。**当前为演示项目，暂不修复**，待有空时按方案处理。

### R5. 【连接池】文档称"已全面替换 HikariCP"，实际生产 6 个模块仍是 Druid 🟡

**现状（已核实）**：`mall-sso`（3 个数据源）/`mall-product`/`mall-order`/`mall-seckill`/`mall-ums`/`mall-ams` 的 `application-prod.yml` 均为 `type: com.alibaba.druid.pool.DruidDataSource`，且各 webapi pom 显式引入 druid 1.2.24（`mall-ums-service`/`mall-product-service` 的 pom 反而排除 HikariCP）；仅 `mall-resource` 无 druid 依赖（用 Spring Boot 默认 HikariCP）。

**矛盾点**：`docs/项目上下文文档.md` §6.1(8)/§8.12 记载"Druid 曾致上传线程挂死、已全面替换为 HikariCP"——与事实不符，文档需更正。

**影响**：文档与事实脱节，若 Druid 历史问题（上传线程挂死）在生产复现，排查方向会被误导。

**解决方案**（二选一，建议先 a 后 b）：
- a) **✅ 已执行（2026-08-17）**：更正文档——`docs/项目上下文文档.md` §6.1(8)/§8.12 已如实记录"仅 mall-resource 切换 HikariCP，其余 6 服务仍 Druid 稳定运行"；`docs/面试准备/03-数据库设计.md` Q7 已同步更新
- b) **⏸️ 真正切换（暂缓）**：6 个模块 prod yml 删除 `type:` 行（回落 Spring Boot 默认 HikariCP）+ webapi pom 移除 druid 依赖 + service pom 移除 HikariCP exclusion → 需全量回归 + 重启窗口，演示项目暂缓

📄 **详细执行方案（含 Step 0~7 步骤 / HikariCP exclusion 陷阱 / SSO 三数据源注意事项 / 回滚方案 / 监控说明）见 [[连接池统一HikariCP方案]]**

### R6. 【文档勘误】Sentinel 端口映射描述错误 🟢

**现状（已核实）**：compose 为 `8090:8858` + `JAVA_OPTS="-Dserver.port=8858"`，服务器实际 `0.0.0.0:8090->8858/tcp`（容器内 8858）；`docs/运维/阿里云ECS服务器情况.md` §4.1 表格误写 "8090→8080"。

**解决方案**：更正 `docs/运维/阿里云ECS服务器情况.md` 为 "8090→8858（容器内 8858）"。TODO 文件下方 §中优先级#2 提到的 "Sentinel 控制台（8090 端口）" 指宿主机端口，无需改动。

> ✅ **第一批 #2 已于 2026-09-07 完成**：内存止血——实测 available 2.1G 的前提，先于一切扩容（明细见 [[TODO已完成]]）

### R8. 【运维】Redis 大键定期巡检（防患于未然）🟢

> **2026-08-21 新增**：实测确认当前 Redis **无大键**（21 键，最大 `token_list_.lock` 仅 9.7KB，慢日志为空），但商业化/数据量增长后需定期巡检，防止"单线程阻塞 + DEL 卡顿 + 内存不均"。

**巡检方案**（生产标准三件套）：
```bash
redis-cli --bigkeys                       # 快速体检(SCAN 渐进式,不阻塞)
redis-cli MEMORY USAGE <key>              # 逐 key 精确定位(4.0+)
redis-cli SLOWLOG GET 10                  # 慢日志=大键操作痕迹
# ⚠️ 绝不用 KEYS * 扫描(阻塞)
```
- **阈值建议**：单 key > 10MB 或单集合 > 1 万成员 → 告警（按业务定）
- **频率**：月度巡检 + 每次大版本部署前；与 R7 内存优化同节奏（可合并进同一维护窗口）
- **处理**：`UNLINK` 异步删除（禁用 DEL）/ 拆 key / TTL 兜底 / 序列化优化（java→JSON）
- **相关**：本项目 `RedisTemplate` 使用 Java 序列化（体积大、跨语言不可读），数据量上来后可换 JSON 序列化

---

## 🟡 中优先级

### 1. 【前端开发】商品图片/品牌Logo/分类图标管理界面

当前后台管理缺少：
- ❌ 相册管理页面（`pms_album` 表已有，后端 Controller 已就绪）
- ❌ 图片管理页面（`pms_picture` 表已有，后端 Controller 已就绪）
- ❌ SPU 编辑时从相册选图

| 功能 | 涉及文件 |
|------|---------|
| 相册 CRUD | 新增 `AdminAlbumList.vue` + `album.js` API |
| 图片列表/上传/删除 | 新增 `AdminPictureList.vue` + `picture.js` API |
| SPU 关联相册 | 修改 `SpuList.vue` |

---

### 2. 【AI 安全】`/ai/**` 接口接入 Sentinel 限流 ✅ 已完成并部署（2026-09-08，与 #34 合并实施）

> ✅ **2026-09-08 完成 + 已部署**：`/ai/**` 全接口防护三件套——**① Sentinel QPS 限流**（3 组资源：ai-chat=5 流式/同步对话、ai-reason=10 搜索重排/问答/对比、ai-light=30 补全/推荐/历史；Nacos mall-ai-flow-rules 统一管理，AiController 加 @SentinelResource + 专属 blockHandler 返回 429/SSE error）；**② 并发闸门**（AiConcurrencyGuard Semaphore=20，挂所有真实 LLM 调用汇聚点 DeepSeekAiClient + streamDeepSeek，闸门满抛 AiBusyException → 服务内既有降级路径兜底/局部 advice 返 429 = 繁忙永不 500）；**③ 每用户频控**（AiUserRateLimiter Redis INCR+TTL 60s/10 次，防单用户刷爆预算）。**服务器实测**：30 并发 /ai/search → 10×200 + 20×429（无 500）；每用户频控 15 连打全 429。**部署踩两坑**：pom 缺 sentinel-datasource-nacos（启动崩）+ blockHandler 签名缺原参数（500），均修复提交（bddb590）。明细见 [[AI限流与并发闸门-部署执行清单-2026-09-08]]。
>
> **2026-08-14 新增**：AI 接口审计发现所有 `/ai/**` 接口均无限流规则（无 `@SentinelResource`、`sentinel-rules.json` 无 ai 规则），单个用户/单 IP 可无限并发调用，即使有 2 元/日预算也存在并发冲超风险。

**目标**：给 AI 调用路径（重点：`/ai/chat/stream`、`/ai/chat/send`、`/ai/ask`、`/ai/compare`、`/ai/search`）加 Sentinel 限流，例如：
- 按用户或按 IP：每 60 秒最多 N 次（N 建议 10~30，需实测）
- 流式接口额外限制并发连接数（防 SSE 长连接占满线程池）

**参考实现**：
- 现有先例：`mall-sso` 的 `AdminSSOController.doLogin` 已用 `@SentinelResource(value = "adminLogin", blockHandler = "loginBlock")` + 自定义 `BlockException` 统一返回 429 JSON（见 `mall-common` 全局异常处理）
- 流控规则可通过 Sentinel 控制台（8090 端口）动态下发，或写入 `deploy/docker/sentinel-rules.json` 持久化

**涉及文件**：`AiController.java`（各端点加注解）、`deploy/docker/sentinel-rules.json`（规则）、`mall-common`（429 响应处理已有）

---

### 3. 【秒杀管理】缺少秒杀活动管理功能（后台 UI + 场次维度购买标记）

> **2026-08-26 新增**：审计发现秒杀模块**完全没有管理功能**——活动/时间窗口/价格/库存全靠手动改数据库（`seckill_spu`/`seckill_sku` 表），且**无后台管理界面**。

**现状（已核实，2026-08-26 服务器 + 代码双重确认）**：
- 6 个秒杀场次时间窗口均为 `2026-01-01 ~ 2026-12-31`（全年有效，无真实"场次"概念）
- **购买标记维度问题**：三 key（orderLock/ordered/reseckill）均按 `skuId:userId` 维度，**与场次无关**；`reseckill` 支付后**永久标记**（无 TTL，见 `OmsOrderServiceImpl.markSeckillPurchased`）→ **同一 SKU 同一用户支付后永久不能再买**，即使开新一轮秒杀
- `success` 表 `UNIQUE KEY uk_sku_user (sku_id, user_id)` 是数据库层最终防线，同样按 SKU 维度
- **业务影响**：无法支持"每轮秒杀同一商品可复购一次"的常见运营模式

**目标**（按需拆分，均可独立实施）：
1. **秒杀活动管理后台**：创建场次（时间窗口/秒杀价/库存/限购）、启停用、列表——参考现有 admin 商品管理风格
2. **场次维度购买标记**：`reseckill` key 改为含 `seckillId` 维度（如 `mall:seckill:reseckill:{seckillId}:{skuId}:{userId}`），支持"每场限购一次"
3. **success 唯一索引调整**：`uk_sku_user` → `uk_seckill_sku_user`（含场次），与 Redis 标记对齐

**涉及文件**：`mall-seckill-webapi`（SeckillController/Service + `SeckillCacheUtils`）、`mall-order-webapi`（`OmsOrderServiceImpl.markSeckillPurchased`）、前端 admin 新增秒杀管理页、`database/cs_mall_seckill/`（索引变更走 Flyway V+n）

> ⚠️ 注意：改购买标记维度会影响已产生的 success 数据（旧数据无场次），迁移时需评估（演示项目数据量小，可清理重建）。

---

### 4. 【集群+配置中心】秒杀/订单/商品三模块集群化 + 配置迁 Nacos（模拟企业级场景）

> **2026-08-26 新增（评估+计划，暂不实施）**：目标 = 三模块各生成集群（多实例）+ 几乎所有配置进 Nacos，模拟真实企业级场景。**用户已确认：仅讨论计划，不做任何操作**。

**方案决策（用户已选）**：
- 内存：先做 R7 优化（Nacos 降堆省 460M + Swap 2G）+ 副本压缩堆（-Xmx256m）
- 配置范围：非敏感全进 Nacos（数据源/Redis/MQ/Seata/限流），敏感留 .env（密码/JWT/AI Key）
- 顺序：先配置中心迁移，后集群化
- 集群形态：单机 2 实例演示级（可验证负载均衡/故障剔除/热更新，非真实 HA）
- **✅ 2026-08-26 已确认：仅秒杀模块加 1 副本（10007 + 10017），商品/订单暂不加**

**评估结论（关键）**：
- 服务器 available 仅 2.1G；**单秒杀副本（~650M）+ R7 释放（~560M）→ 加完后剩 ~2.0G ✅ 内存可行**
- 选秒杀理由：演示价值最高（秒杀压测看负载均衡 + 限流规则热更新），且"定时任务分布式锁"是面试亮点；商品/订单收益/改动比更低
- 多实例改造点：仅 seckill 有定时任务（`MessageRetryTask` 每 5s）→ 需 Redis 分布式锁；Sentinel 限流按实例统计（副本后集群 QPS 翻倍，要讲清）；Seata/MQ/Redis 天然多实例安全
- **单机集群 = 演示级 HA，机器宕机所有实例一起挂**，面试只讲"验证机制"不讲"高可用"

📄 **完整方案（内存测算/三模块价值对比/两阶段步骤/风险与回滚/执行清单）见 [[集群化与配置中心迁移方案]]**

---

### 5. 【Sentinel】能力补充计划 ✅ P0 已完成并部署（2026-09-08）；P1/P2 未做

> ✅ **2026-09-08 P0 完成 + 已部署服务器**：**审计修正**——原记载"规则持久化 Nacos + 代码双保险"与事实不符：Nacos SENTINEL_GROUP 规则全空，秒杀本地代码规则被 Nacos 空配置覆盖（日志 `source is empty`）= **秒杀限流当时实际失效**（历史覆盖坑复现，日志实证）。
>
> **实施内容**：① order/sso 加 `sentinel-datasource-nacos` + prod/test yml datasource（flow+degrade，sso 仅 flow）；② 秒杀代码规则保留为"启动瞬态 + Nacos 宕机兜底"（注释机制：Nacos 正常→以 Nacos 为准；Nacos 空→代码也被擦；Nacos 宕机→代码兜底存活）；③ **规则 JSON 入库 `deploy/docker/sentinel/`**（5 个 dataId 事实来源：seckill flow QPS10+degrade、order flow QPS20×2+degrade、sso adminLogin QPS10）；④ 删除死文件 `deploy/docker/sentinel-rules.json`（无引用/GBK 乱码/count=100 错）；⑤ **sso 补 dashboard env**（compose）+ 服务器 compose 同步（曾漏同步导致 sso 心跳连 nacos:8858 失败）；⑥ **`eager: true`**——修复 transport 懒加载（无流量时 CommandCenter 不启动，Dashboard 查不到规则/监控，实测 `Begin listening at port 8880` 在打流量后才出现）。
>
> **服务器验证（2026-09-08）**：Nacos 5 规则全部发布（curl content@file 单次编码防双重编码坑）；三容器重建后 record 日志 flow+degrade 全部 `notify-ok` 加载；实测 adminLogin 30 并发 → **10×400 + 20×429**（QPS=10 精确生效）；transport 端口 8880/8872/8870 均可达，Dashboard 可见 mall-sso/order/seckill 三应用及规则。
>
> **遗留（未做）**：P1 热点参数限流（秒杀按 spuId 差异化，随集群化评估）、P2 集群流控（随 #4）、P3 系统保护/授权（无场景）。
>
> **2026-08-26 新增**：对项目 Sentinel 使用程度全面评估（代码实证）——当前只用了"基础三件套"（秒杀 QPS=10 流控 + 规则存 Nacos + 控制台），**4 个 @SentinelResource（秒杀/新增订单/支付订单/adminLogin）只有秒杀配了规则，其余 3 个是"注解但无规则"空转**；企业级高级能力（热点限流/系统保护/授权/集群流控）均未使用。

**优先级划分（用户确认后实施）**：
- 🔴 **P0 补齐 3 接口规则**（新增订单/支付订单 QPS=20、adminLogin QPS=10，Nacos 建规则即可，消除空转）——✅ 已完成
- 🟠 **P1 热点参数限流**（秒杀按 spuId 差异化：爆款 QPS=100、普通 1000，当前整接口共享 QPS=10 互相误伤）——面试价值最高，⏳ 未做
- 🟡 **P2 集群流控**（秒杀集群化后 token server 统一配额，否则双实例各自 QPS=10 总量翻倍失真）——与 #4 集群化配套，⏳ 未做
- ⚪ **P3 暂不推荐**（系统自适应保护：CPU<5%无场景；授权规则：Gateway+Security 已挡）

📄 **完整计划见 [[Sentinel能力补充计划]]；执行清单与验证见 [[Sentinel部署执行清单-2026-09-08]]**

---

### 6. 【网关】Dubbo 应用名撞名 → lb:// 混入 Dubbo 实例 ✅ 已修复（2026-09-08 全项目排查）

> ✅ **2026-09-08 完成（从 mall-product 扩展为全项目排查 + 3 模块修复）**：
> - **触发**：生产实测 bug——用户"商品列表→秒杀→商品列表→秒杀"第二次进秒杀报 **500**，gateway 日志 `invalid version format: UNSUPPORTED` + `R:172.18.0.20:20880` = lb://mall-seckill 轮询打到 Dubbo 端口（第一次 HTTP 成功、第二次 Dubbo 失败 = 轮询交替）
> - **排查方法**：gateway `lb://` 路由 × Nacos 实例列表 × `@DubboService` 扫描三向对照，揪出所有"暴露 Dubbo + 撞名"的模块
> - **修复**：3 模块 dubbo 应用名分离（prod+test 对齐）：
>   - `mall-seckill`: `mall-seckill` → `mall-seckill-dubbo`（本次 500 根因）
>   - `mall-ums`: `mall-ums` → `mall-ums-dubbo`（UserServiceImpl 暴露 Dubbo，潜伏隐患）
>   - `mall-product`: `mall-product` → `mall-product-dubbo`（原 #6 主角，曾用直连 9010 规避）
> - **无需改**：mall-ai/order 本就是 `*-dubbo`；mall-front/search/ams **撞名但无 @DubboService**（不注册 20880，Nacos 实测仅 HTTP 实例，lb:// 安全）→ 统一规范放 TODO #45（第三批）
> - **验证**：Nacos `mall-seckill`/`mall-ums`/`mall-product` 服务名下只剩 HTTP 实例，20880 移入 `*-dubbo` 名下；Dubbo 消费者按接口引用不受影响
>
> **2026-08-28 新增（Nacos 实例列表实测确认）**：`mall-product` 服务下有 **2 个实例** = `172.18.0.19:20880`（Dubbo provider，`protocol=dubbo`）+ `172.18.0.19:9010`（Spring Cloud HTTP）→ 网关 `lb://mall-product` 轮询**一半请求打到 Dubbo 端口**（非 HTTP 协议）报错。这是 prod pms 路由直连 `http://mall-product:9010` 的**真实原因**（配置注释："直连 HTTP 端口，避免 Nacos 混入 Dubbo 20880"）。

**根因**：`dubbo.application.name` 与 `spring.application.name` 撞名 → Dubbo 3.x 应用级注册把 20880 实例混进同一服务名。对比 `mall-order`：dubbo 应用名 `mall-order-dubbo`（分开）→ "mall-order" 服务下只有 HTTP 实例，lb:// 安全。

**后续（可选）**：gateway pms 路由可改回 `lb://mall-product`（去掉直连特例，统一风格）——product 改名后已无 Dubbo 混入，直连 9010 仍可用不必急改。

---

## 🟢 低优先级

### 2. 图片打包进 JAR 的问题

`mall-resource/src/main/resources/static/` 下的图片随 JAR 打包，浪费空间。生产环境已走 `/data/csmall-upload/`，可考虑移除 static 下的重复图片。

### 3. RedisBloom 模块安装

当前 Redis 无 RedisBloom 模块，秒杀用 Redis Set 替代布隆过滤器。当前数据量（6场秒杀、12SKU）影响可忽略。未来数据量大时安装。

> **2026-08-09 服务器核实**：生产 Redis 7.4.10（standalone），`module list` 为空、`BF.EXISTS` 报 unknown command → **确认未装 RedisBloom**。
> 安装方案：改用 RedisStack 镜像，或在 docker-compose 的 Redis 启动命令加 `--loadmodule ./redisbloom.so`（挂载模块文件）；装好后把 `SeckillBloomInitialJob` 的 Set 实现替换为 `bf.madd` / `bf.exists`（代码里已留 TODO）。

### 4. 【支付】微信支付模拟策略

`WechatPaySimulationStrategy implements PaymentStrategy`，遵循微信 API 契约。

### 5. 【支付】真实支付宝支付

当前 `simulated: true`（模拟支付）。待支付宝沙箱修复或商户签约后改为 `false`。

### 6. 【演示】JMeter + Sentinel + SkyWalking 联合压测 ⏸️ 已归档（2026-09-07）

> 原用途为面试演示/简历视频。**2026-09-07 决定暂不录制**（简历已改用 HTML 新版），方案文档 `秒杀压测演示指南.md`/`秒杀服务器压测方案.md`/`演示视频讲解提纲.md` 已移入 `docs/归档/`；工具脚本保留 `deploy/jmeter/` 备日后复用。若将来录制新视频，先看归档文档。

### 7. HTTPS/SSL 配置 ⏸️

```
阻塞链: HTTPS → SSL证书 → 域名 → ICP备案 → 服务器续费≥3个月
                                                    ↑
                                            卡在预算（8月29日到期）
```

> 🔥 已升入**第二批 #2**：唯一线上代码 bug，改动 ~10 行（✅ 2026-09-07 已修复，明细见 [[TODO已完成]]）

### 9. 【学习】Redis 主从 + 哨兵高可用实验

> **2026-08-17 新增**：源自面试准备 `02-秒杀高并发.md` Q10（Redis 挂了怎么办）。当前生产为单机 Redis（standalone），秒杀链路强依赖 Redis（随机码/下单锁/库存 DECR），挂了功能直接停摆；`seckill_message_retry` 重试表只兜 MQ 可靠性。计划深入学习"主从复制 + 哨兵"高可用机制并实操实验。
> **2026-08-21 更新**：✅ 方案已定稿（1 主 + 1 从 + 2 哨兵，含 R1~R4 加固），见 [[Redis配置加固与哨兵模式方案]]；服务器确认续费至年底，待维护窗口执行后移入"已完成"。

**学习目标**：
- 主从复制：主节点写、从节点异步同步数据（数据冗余）
- 哨兵：监视主节点 → 挂了自动把从提升为主 → 客户端自动重连（故障转移）
- 对照同一思想：Nacos Raft 选主、ES 主分片提升、Redis 哨兵切换

**实验方案**（本地 Docker 或服务器，学习用最小拓扑即可）：
- 最小拓扑：1 主 + 1 从 + 1 哨兵；标准拓扑：1 主 + 2 从 + 3 哨兵（防脑裂需多数派）
- 端口规划：6379（主）/ 6380（从）/ 26379（哨兵），全部仅内网，不暴露公网
- Spring Boot 3.x 改造：`spring.data.redis.sentinel.master/nodes` 指向哨兵（compose 注入 `SPRING_DATA_REDIS_SENTINEL_*`）；sso/order/seckill 等连 Redis 的服务需全量回归

**边界认知（重要）**：
- 单机搭建 = 演示级 HA：只防 Redis 进程崩溃（如被 OOM 杀），**不防整机宕机**；真实 HA 需 ≥2 台机器，或直接买阿里云托管 Redis（自带主从+哨兵）
- 主从异步复制：主挂瞬间最后几笔 DECR 可能未同步到从，靠数据库 `seckill_stock >= quantity` 条件扣减兜底，不会真超卖
- 当前服务器可用内存约 2.2G，加副本前先评估；服务器 8-29 到期，可结合续费/升级一起规划

**涉及文件**：`/data/csmall/docker-compose.yml`（新增 redis 副本/哨兵服务）、各服务 `application-prod.yml`（sentinel 连接配置）

### 10. 【评估】Redis Lua 脚本引入评估（仅评估，不实现）

> **2026-08-21 新增**：源自 `docs/项目上下文文档.md` §6.6（Redis 一致性策略分析）。当前项目未使用 Redis 事务（MULTI/EXEC）；**若未来**出现"多 key 强原子"需求，优先评估引入 Lua 脚本。**仅为评估记录，不会真的实现**，优先级低，要做也是之后再做。

**评估依据**（为什么 Lua 而非 Redis 事务）：
- Redis 事务 MULTI/EXEC 是"弱事务"：只保证命令隔离执行，**不保证回滚**（EXEC 前某命令失败，其余命令照常执行）；期间其他客户端可插入命令（需 `WATCH` 才部分缓解）
- Lua 脚本优点：整段脚本**原子执行**（期间无其他命令插入）、可写回滚逻辑、单次网络往返、Redis 官方推荐的复杂原子操作方式
- 触发场景（未来可能）：秒杀"扣库存 + 标记已购 + 写随机码"多 key 原子化；抢券/抽奖/资格预占等"读-改-写"；批量限流计数
- 约束：Redis Cluster 下 Lua 仅支持**同 slot 的 key**（跨 key 报 `CROSSSLOT`）
- 现状：所有 Redis 写入均为"单命令原子 + 锁 + 补偿"，暂无强原子需求 → **暂不实现**

**Lua 与哨兵模式共存评估**（2026-08-21 补充，修正早期"脚本与事务同样会中断"的不准确表述）：
- 切换瞬间的**连接中断**是客户端操作共性问题（普通命令/事务/Lua 都会遇到），客户端重连重试即可，非 Lua 特有
- Lua 特有的风险是**长脚本阻塞主节点 → 哨兵误判下线**（脚本执行期间不响应 PING，若超过 `down-after-milliseconds` 可能误触发故障转移）；`lua-time-limit`（默认 5s）超时后只读脚本可 `SCRIPT KILL`，写脚本无法中断
- 业界共存方案（大厂已验证，阿里云 Tair / AWS ElastiCache 均同时支持 Lua + 哨兵）：① 脚本保持毫秒级短小（第一原则，秒杀扣库存类仅几行微秒级）；② 调小 `lua-time-limit` 尽早暴露超长脚本；③ `down-after-milliseconds` 与最坏脚本时长保持数量级差距（本项目哨兵方案设 5000ms，微秒级脚本零风险）；④ **3 哨兵 + quorum=2 多数派**防单点误判；⑤ `SLOWLOG` + 慢脚本告警；⑥ 客户端重连 + 脚本幂等设计
- **结论**：Lua 与哨兵可安全共存，无本质冲突；本项目未来即使引入 Lua，也是微秒级短脚本，不构成误判风险

**涉及文件（若实施）**：`SeckillServiceImpl.java`（库存扣减 Lua 化）、`RedisBloomUtils.java`（已有 `DefaultRedisScript` 先例可参考）

### 11. 【升级计划】ZSET 延迟队列（仅计划，不实现）

> **2026-08-21 新增**：记录 MQ+Redis 联动现状与 ZSET 延迟队列升级评估。**仅为升级计划，不实现**；若未来项目商业化（面向营收/规模化），再评估实施。可用于**面试讲解**（展示系统设计深度与演进思路）。

**项目 MQ+Redis 联动现状（已代码确认）**：
- 秒杀下单：`SeckillServiceImpl` — Redis 预扣库存 `DECR`（防超卖闸门）+ ordered 标记 → RabbitMQ 异步落库（削峰填谷）
- 普通订单：`OmsOrderServiceImpl` — 支付回调写 reseckill 购买标记（Redis）+ MQ 发库存扣减消息
- **延迟/重试现状**：`MessageRetryTask` 每 5 秒 **DB 轮询** `seckill_message_retry` 表（status=0, retryCount<3）重发 MQ —— 教科书级"低配延迟队列"，演示场景够用
- 全项目 **ZSet 零使用**；无 RabbitMQ 延迟插件（x-delayed-message）；未实现"订单超时自动关单"（状态机已预留 `1=超时关闭`）

**ZSET 延迟队列方案（升级目标）**：
- 原理：`score` = 任务执行时间戳，`ZADD` 入队；后台每秒 `ZRANGEBYSCORE tasks 0 now` 拉取到期任务，`ZREM` 删除——**谁 `ZREM` 成功（返回 1）谁消费，天然防重复**
- 高级点：① `O(logN+M)` 有序范围查询，高并发优于 DB 轮询；② 单命令原子，分布式并发消费无锁；③ 精度可控到毫秒（score 可带小数）；④ RDB/AOF 持久化，重启不丢（对比内存时间轮）；⑤ 复用现有 Redis，零新组件；⑥ Redisson `RDelayedQueue` 即此实现，订单超时关单是经典案例
- 局限：拉模式非精准定时（轮询间隔 = 最大延迟）；任务全在内存（海量堆积成本高）；无 MQ 消费确认/死信语义，需 Lua 或业务代码自建

**升级触发条件（商业化时考虑）**：
- 订单超时未支付自动关闭（状态机已预留）
- 优惠券/活动过期、秒杀定时上下架
- 消息重试从 DB 轮询迁移到 ZSET（替换 `MessageRetryTask`）

> ⚠️ **2026-08-28 补充（05 Q8 竞态分析）**：做"超时自动关单"必须**同时**把支付更新改成条件更新（`UPDATE ... WHERE state=0`）——现状是 `updateOrderById` 无条件 setState(3)。关单 + 支付都靠条件更新原子互斥，先关后付自动退款，先付后关关单失败；另加对账兜底。**关单和支付改造是配套的，不能只做一边。**

**面试讲解要点**：
- "为什么用 ZSET 不用 DB 轮询"：复杂度（O(logN) vs 全表扫）/ 原子性 / 精度 / 持久化 四维对比
- "为什么不直接上 MQ 延迟插件"：ZSET 复用已有 Redis、零依赖、精度可控；RabbitMQ TTL+DLX 每档延迟一个队列、x-delayed 需装插件
- "怎么保证不重复消费"：`ZREM` 原子性 + Lua 封装"取+删"一步完成
- "怎么配合高可用"：ZSET + 哨兵（已规划）组合，与 Lua 短脚本共存无冲突（见 #10）

### 12. 【评估】Redis 集群适配 + 配置中心 + 多副本（仅评估，不实施）

> **2026-08-21 新增**：三份评估完成，**均暂不实施**，未来商业化/规模扩大时再尝试。

**评估结论摘要**：
1. **Redis 集群扩容，业务代码可适配** ✅：全项目零跨 key 操作（无 mget/pipeline/事务/Lua/集合运算），单 key 天然分布任意 slot；MOVED/ASK 重定向由 Lettuce 自动处理，业务代码无感。3 个适配点：① 未来新 key 约定 hash tag（防 CROSSSLOT）；② 客户端切换仅改 compose 注入 `SPRING_DATA_REDIS_CLUSTER_NODES`；③ 每节点 requirepass（并入 R1）
2. **配置写进 Nacos 统一管理：当前没必要** ❌：实际变更点已集中在 compose/.env 一处；变更频率极低、动态刷新无场景；引入成本（依赖/启动顺序/运维）大于收益；哨兵落地后节点对客户端透明，配置中心场景进一步缩小
3. **jar 容器多副本：当前没必要** ❌：服务器 2.2G 可用内存放不下副本（硬约束）；无流量压力（CPU<5%）；定时任务/Seata 多实例需改造；正确路径是"先基础加固 → 多机 → 再副本"

📄 **完整评估（含重定向机制/成本收益表/演进路径）见 [[Redis集群适配与配置管理评估]]**

> 🟡 **第二批 #4**
### 13. 【安全】Nacos 开启认证 ✅ 已完成并部署（2026-09-08）

> ✅ **2026-09-08 完成 + 已部署**：认证开启（NACOS_AUTH_ENABLE + 随机 TOKEN/IDENTITY）+ 管理员密码初始化 + 11 服务全客户端同步（discovery 11 + Dubbo registry 8 + Sentinel datasource 4,Seata file 模式豁免）。服务器实测：无 token=403、登录拿真 JWT、全服务注册正常、Sentinel 规则热更新正常。**部署发现历史隐患并修复**：nacos 此前无数据卷 → 重建容器 derby 数据（配置/用户/规则）全丢 → 已补 `nacos_data:/home/nacos/data` 卷 + 从仓库 JSON 重建 6 条规则。明细见 [[Nacos认证-部署执行清单-2026-09-08]]。
>
> **2026-08-26 新增**：实测 Nacos **完全无认证**（无 token 直接读配置返回 200、控制台免登录、默认账号 nacos 未改）。公网安全组只开 22/80 挡得住外部，但内网失陷后可无认证读写 Nacos + 注册假服务（服务伪装）→ 消费者被引流到攻击者机器。

**判定：值得加**（成本低 + 生产化标配 + 面试必问），**优先级中**（当前公网进不来，非紧急）。
**方案 A（最小可行认证）**：
1. compose nacos 加 `NACOS_AUTH_ENABLE=true` + `NACOS_AUTH_TOKEN`（Base64 ≥32字节随机串）+ `NACOS_AUTH_IDENTITY_KEY/VALUE`——✅ 已完成
2. 重启 nacos → 无 token 访问返回 403——✅ 已完成（实测 403）
3. **全量同步**：11 微服务 + Seata + Dubbo 全配 username/password（任一漏配 = 该服务起不来），须同一维护窗口完成——✅ 已完成（discovery/Dubbo/Sentinel datasource 三类客户端,Seata file 豁免）
4. 验证：注册/发现正常 + Sentinel 规则仍能拉取——✅ 已完成

> ⚠️ **2.4+ 关键差异**：无默认密码，开鉴权后必须先 `POST /nacos/v1/auth/users/admin` 初始化管理员密码；备选 B=8848 映射改 127.0.0.1（只挡外部，不解决内网）。
> 📄 执行清单见 [[Nacos认证-部署执行清单-2026-09-08]]

### 14. 【Redis】主从切换防数据问题（五层方案）✅ P0+P1 已完成（2026-09-07~08 已部署）；P2 余随 #9

> ✅ **2026-09-07/08 完成（P0 三层 + P1 对账均已部署）**：
> - **P0 落库失败不静默（第3层）✅**：SeckillQueueConsumer 库存不足从 basicAck 静默丢弃改为三兜底——失败留痕 + 已付款告警（新增 IOmsOrderService.getOrderStateBySn Dubbo 查询）+ x-death 限次重试（与 #36 同款）
> - **订单 order_type 标识（治本前置）✅**：oms_order 加 order_type 列（Flyway V6），秒杀入口置 1、普通入口强制 0（防伪造）；markSeckillPurchased/clearSeckillOrdered 仅秒杀单执行（修复"普通订单被误当秒杀单写 reseckill 标记"的潜在 bug）；本地普通购买实测验证守卫生效
> - **✅ P0 方案Y 支付前校验本单成交状态（success 落库）**：P0"付款前查库存"原方案放弃（语义缺陷见下）后，用**查询本单是否已写入 success 表**替代实现——秒杀单（orderType=1）支付前经新 Dubbo `IForOrderSeckillRecordService.isSeckillSuccessRecorded(orderSn)` 校验本单是否已成功落库，未落库则拦截支付（防"Redis 预扣放行但 DB 扣减失败 rows==0"的用户付了钱没货）；查本单而非剩余库存，不误拦已成交最后一件；Dubbo 异常保守放行。本地秒杀→支付全链路实测通过（order_type=1、state=3 已支付、success 有记录）
> - **P2 配置层**：随 R2 主从哨兵一起（#9，第三批）
> - **P1 对账任务 ✅（2026-09-07 完成，09-08 已部署）**：`SeckillReconcileTask` 运行期 5 分钟轻量纠偏（\|diff\|≥2 直接修、\|diff\|=1 连续 3 次才修）+ 凌晨全量校准（\|diff\|≥1 即修 + 补建缺失 key），以 DB 为唯一基准修正 Redis；本地实测修掉 sku26/sku35 漂移、12 sku 零误改。明细见 [[TODO已完成]] §六、[[秒杀对账任务实现方案]]
> - **🔧 秒杀 SPU VO 缓存一致性 bug（2026-09-07 本地实测发现并修复）**：`getSeckillSpu`（详情页）先读 Redis 缓存 `mall:seckill:spu:vo:{pmsSpuId}`，该 VO 在**改秒杀时间窗口**后不失效（TTL 约 2h）→ 详情页读到**旧窗口**误显示"秒杀已结束"，而**列表页** `listSeckillSpus` 直接查 DB（显示进行中），两页不一致。已修：`SeckillManageController` 新增/删除秒杀 SPU 时 `redisTemplate.delete(该 VO key)` 主动失效（`evictSeckillSpuVoCache`）。**注意局限**：仅"经管理端接口改窗口"会触发失效；若直接改 DB 表（如本次本地演示），仍会命中旧缓存直到 TTL 到期——根治需在 `getSeckillSpu` 读缓存时校验窗口/或改时区/缓存双写，待后续评估。

> **2026-08-26 新增**：Redis 主从复制异步 → 主挂瞬间丢最后几笔写（库存 DECR/购买标记/幂等锁可能丢）。代码层无法 100% 消灭（本质），目标是"让丢失无害化"。

**五层方案（按优先级）**：
- 🔴 **P0 付款前校验 DB 库存**：⛔ 原方案"支付时查 seckill_stock 剩余库存"**评估后放弃（2026-09-07）**——语义缺陷见下方分析结论。**已用"方案Y"替代实现：付款前校验本单是否已写入 success 表**（✅ 已完成，见上方完成列表）
- 🔴 **P0 落库失败不静默**：✅ 已完成（2026-09-07，SeckillQueueConsumer 三兜底）
- 🟠 **P1 对账任务**：定时 Redis vs DB 比对，以 DB 为准自动修正漂移 + 预热校验（待做）
- 🟡 **P2 配置层**：min-replicas-to-write 1（随 R2 主从哨兵一起，#9）

> **⛔ P0 付款前查库存 —— 分析后放弃（2026-09-07，重要设计结论）**
>
> 曾计划：支付前 order 通过新 Dubbo 服务查 seckill_stock 剩余库存，不足则拦。**实现前推演发现语义缺陷**：
>
> **时序**：Redis 预扣（下单闸门，最多放行 N 单）→ DB 条件扣减（MQ 逐单扣，`seckill_stock>=qty`，每单一条消息）。
>
> **缺陷 1（误拦已成交）**：库存一致时（Redis=DB=N），放行 N 单 MQ 全部扣成功——**最后一件成交后 DB=0 是正常结果**，其用户付款时查剩余库存 0 < 1 会被**误拦**。付款前查"剩余库存"无法区分"本单的货已被 MQ 扣掉（正常）"与"本单货没扣上（异常）"。
>
> **缺陷 2（防不了真问题）**：真正要防的"Redis 多放导致的超卖"，DB 条件扣减已兜底（`rows==0` 即本单不成交），第 3 层失败留痕+告警已覆盖——付款前查剩余库存既不拦"该拦的"（无归属标记），又误拦"不该拦的"（已成交）。
>
> **结论**：秒杀成交资格在 **Redis 预扣时已确定**，DB 只是记账（闸门/账本模型）。"付款前查剩余库存"在逐单扣减模型下**不可正确实现**——正确防线 = Redis 预扣闸门 + DB 条件扣减兜底 + 第3层落库失败处理（均已做）。**该子项关闭，不再实现**（若未来改"下单即同步扣 DB"模型才需重估）。

> 架构层已做（Redis=闸门/DB=账本，条件扣减兜底）；配置层依托 R1~R4。
> 📄 **完整方案（五层详解/代码示例/实施清单/风险回滚）见 [[Redis主从切换防数据问题方案]]**（注：其中第 2 层"付款前查库存"示意代码经推演有误，实际以本条目结论为准）

### 15. 【学习】K8s 实操实验（k3s 方案备用，2026-08-26 评估，暂不部署）

> **2026-08-26 新增**：K8s 架构已理解（控制平面/etcd/节点），**当前单机 4C16G 不部署**（场景不匹配 + 内存剩 2G），但 k3s 轻量方案已备好，未来学习实操/迁移演练时用。

- **现状**：服务器纯 Docker Compose（21 容器），无 k8s/etcd/zookeeper 进程（2026-08-22 实测）
- **选型**：k3s（~500MB，一个二进制搞定控制平面+节点，自带 etcd 替代，可加节点变集群）> minikube（学习 demo）> kubeadm（生产多机）
- **安装**：`curl -sfL https://get.k3s.io | sh -`
- **面试价值**：能讲清"控制平面（API Server/etcd/Scheduler/Controller）+ 工作节点（kubelet/kube-proxy）+ etcd 为什么难维护 + 为什么单机用 Compose 是对的"
- **时机**：学习 K8s 实操 / 模拟多机集群实验 / 迁移演练时启用

> 📄 **完整方案（k3s 安装步骤/架构名词/诚实边界/面试话术）见 [[Kubernetes与ZooKeeper评估]] 五、k3s 轻量部署方案**

### 16. 【日志】TraceId 链路日志规范：SW logback 集成（2026-08-28 评估，待实施）

> **2026-08-28 新增**：应用层 traceId 只覆盖 HTTP 链路——定时任务（SeckillInitialJob/SeckillBloomInitialJob/MessageRetryTask）、MQ 消费者（SeckillQueueConsumer/OrderQueueConsumer）、Dubbo provider 线程日志 `[]` 空（MDC 线程绑定 + X-Trace-Id 只走 HTTP 头）。SW agent 已全量接入（9.4.0），**traceId 一直在生成，只是没写进日志**。

**方案（只做 A，B/C/D 不做）**：
- 🔴 **A. SW logback 集成（推荐，~2 小时）**：mall-common 加 `apm-toolkit-logback-1.x` 依赖 + `logback-spring.xml`（`TraceIdMDCPatternLogbackLayout`，pattern 加 `%X{tid}`）→ 一处配置 12 个应用全部生效，覆盖 HTTP/Dubbo/MQ/定时任务全场景，且能和 SW UI 对号
- ⚪ B. Dubbo attachment 传播（~0.5 天）/ C. MQ 消息头（~0.5 天）/ D. 定时任务前缀（~2 小时）——均与 A 重复，**不做**
- 全做约 1.5 人天，演示项目不必要

📄 **完整方案（现状盘点/方案对比/实施步骤/风险回滚/面试价值）见 [[TraceId链路日志规范方案]]**

### 17. 【安全】认证安全企业级升级（2026-08-28 评估，待实施）

> **2026-08-28 新增（源自 05/06 面试准备讨论）**：BCrypt 密码存储与企业一致，但"**哈希只是第一层**"——企业还有 TLS / 登录限流锁定 / MFA / 密钥轮换 / 风控。现状：HTTPS 未上（无域名）、**无按账号/IP 登录限流**、无密码策略、无 MFA、BCrypt cost 默认 10、JWT 密钥明文无轮换。

**建议（只做 P0，其余讲认知）**：
- 🔴 **P0 登录限流**（Redis 失败计数，5 次/分钟锁 15 分钟，复用 @Idempotent AOP 经验，~0.5 天）
- 🔴 **P0 密码策略**（复杂度 + 弱密码黑名单，~0.5 天）
- 🔴 **P0 DelegatingPasswordEncoder 化**（{bcrypt} 前缀多算法并存，为迁移铺路，~0.5 天）
- 🟠 P1：HTTPS（依赖域名/备案阻塞链）/ Argon2id 切换 / JWT 密钥版本化
- 🟡 P2：MFA / 风控告警 / 会话管理——演示项目不做，面试讲认知即可

📄 **完整方案（现状盘点/差距清单/优先级建议/实施顺序/面试关系）见 [[认证安全企业级升级方案]]**

### 18. 【缓存】购物车 Redis 缓存演进（游客购物车，2026-08-28 评估，暂不实施）

> **2026-08-28 新增（源自 05-业务逻辑 Q4 评估）**：当前购物车纯 MySQL（oms_cart，按 user_id 分片，索引查询毫秒级）。**结论：现在不做 Redis 缓存**——购物车是"写多 + 不可丢 + 用户天然分片"的数据，缓存收益为零风险不为零（双写一致性 / 列表价与结算价不一致 / Redis 丢数据 = 用户体验事故）。

**演进路径（量级上来再实施）**：
1. **游客购物车 Redis**（收益最大）：key=`cart:guest:{deviceId}`，TTL 7 天（游客数据本就该过期），登录后合并进 DB——Redis 购物车最有价值的场景
2. **已登录购物车**：保持 DB 为主 + Redis 缓存读（收益有限，可选）
3. **红线**：绝不用 Redis 当购物车唯一存储（丢数据不可接受）

**面试价值**：能讲清"缓存适合读多写少可丢失（商品详情），购物车不适合纯缓存"的选型逻辑

### 19. 【订单】价格/库存服务端校验补强（2026-08-28 评估，待实施）

> **2026-08-28 新增（源自 05-业务逻辑 Q4 快照讨论）**：两个诚实点——① **订单价来自前端传入**（OrderItemAddDTO，BeanUtils copy），无服务端价格重算 → 价格篡改风险 + 商品改价后订单仍按旧价（商家亏）；② **普通订单库存扣减是异步 MQ**（下单不校验 → OrderQueueConsumer 扣减失败 basicNack **无限重试挂单**），秒杀才有前置预扣（Redis DECR + DB 条件扣减）。

**方案（P1，演示项目暂缓）**：
1. **结算价服务端重算**：下单接口按 skuId 批量查当前价（Dubbo 商品服务），以服务端价为准覆盖前端传入价；价格变化提示"价格已更新"
2. **库存前置校验**：普通订单下单前校验库存（或乐观预扣），不足直接返回"库存不足"——不做"下单成功再异步失败"的挂单
3. 可选（P2）：**购物车列表实时价覆盖**——listCartByPage 查完快照 → 收集 skuId → 一次 Dubbo 批量查当前价覆盖展示 → 失败回退快照（~20 行 + 批量查价接口，拼多多式体验；演示项目优先级低，先做 1 再考虑）
4. 可选：购物车列表已下架商品标灰/清理

**面试价值**：能讲清"快照只用于展示、结算回服务端实时数据"的铁律

### 20. 【权限】死权限码清理（2026-08-28 评估，待实施）

> **2026-08-28 新增（源自 05-业务逻辑 Q6 审计）**：ams_permission 表存在两种格式权限码——URL 式（`/ams/admin/read`，被 104 处 @PreAuthorize 使用）+ 冒号式（`pms:manage`、`oms:order:list` 等，**代码 0 引用、前端无权限判断 = 死数据**）。管理员登录时两种都被塞进 JWT。

**方案（P2，演示项目暂缓）**：
1. 先查清 ams_permission：URL 式保留，冒号式列出待删清单
2. Flyway V+n 幂等删除冒号式权限码 + 清 role_permission 关联
3. 重启 → 新登录管理员 token authorities 只剩 URL 式
4. 验证：后台管理各接口权限正常

**面试价值**：能讲"审计发现死权限码，清理统一，不是迁移"的审计能力

### 21. 【BFF】mall-front 企业级补强（2026-08-28 评估，暂不实施）

> **2026-08-28 新增（源自 05-业务逻辑 Q7）**：mall-front 是轻量 BFF（只读 + 无 DB + Dubbo 聚合），三个企业级差距——① **无缓存层**（商品详情是读多写少热点，企业 BFF 配 Redis Cache Aside；你无商品缓存，18 条商品毫秒级）；② **串行 Dubbo 调用**（组装详情 VO 依次查 SPU→SKU→分类→属性，企业用 CompletableFuture 并行 / 聚合 RPC）；③ **单实例无独立扩容**（企业 BFF 无状态按流量弹性扩，你单机内存不足场景不匹配）。

**方案（量级到了再实施）**：
1. **商品缓存**：详情/列表 Redis Cache Aside（key=商品ID，TTL 5-10 分钟），商品变更主动失效（复用 ISpuSyncService 同步 ES 的思路同步缓存）
2. **聚合并发**：详情 VO 组装改 CompletableFuture 并行 Dubbo 调用（延迟从 4 次之和 → 最慢一次）
3. **独立扩容**：多机/流量上来后 mall-front 单独多实例（无状态天然可扩）

**面试价值**：能讲清"BFF 三件套（缓存/并行/扩容）我全知道，量级到了再上"

### 22. 【CORS】服务端 CORS 收敛到网关（2026-08-28 评估，待实施）

> **2026-08-28 新增（源自 06-安全设计 Q5 审计）**：CORS 全景——网关 CorsConfig 显式白名单 ✅；但 5 个服务（ums/search/product/resource/seckill）WebMvcConfiguration 有 `allowedOriginPatterns("*")` 宽松通配（dev 直连需要，但**没限定 profile → 生产也生效 = 多余且宽松**）；mall-ai 自带 CORS（SSE dev 直连 10010 绕过网关，vite.config 注释实证）；order 已是范例（已注释并写明"由网关统一处理"）；ai 路由 DedupeResponseHeader 是重复头补丁。

**方案（P2）**：
1. 5 服务 WebMvcConfiguration 的 CORS 加 `@Profile("dev")` 限定（不是删——dev 直连还要用）
2. mall-ai CORS 同样收窄 dev-only
3. 网关显式白名单不动
4. ai 路由 DedupeResponseHeader 可删（重复源消失，可选保留兜底）
5. 验证：生产 curl -i 响应头只有一个 Access-Control-Allow-Origin；dev 跨域 + ai SSE 回归

**面试价值**：能讲清"CORS 是浏览器机制，生产收敛到网关、dev 直连才需要服务端 CORS"

> 🟡 **第二批 #6**（✅ doRegister 已修，2026-09-07）
### 23. 【校验】漏触发接口补 @Validated ✅ 部分完成（2026-09-07，仅 doRegister）

> ✅ **2026-09-07 实施修正**：代码复核发现原审计描述与事实**部分不符**——5 个接口里只有 `UserController.doRegister` 的 DTO（UserRegistryDTO）**真有校验规则但没触发**（@RequestBody 前漏 `@Valid`），属真实 bug，**已修复**（补 `@Valid`，遵循项目惯例与同文件 renewPassword 一致）。
> ✅ **2026-09-07 第二批补充实施（B/C 类 5 个 DTO 补规则 + 全局异常补全）**：
> - `DeliveryAddressAddDTO`：联系人/省市区名/详细地址 @NotBlank，手机/固话/地区码有则验格式；**"手机/固话二选一"在 Service 层兜底**（DTO 无法表达 or）
> - `DeliveryAddressEditDTO`：仅 id @NotNull（编辑走动态更新，允许部分修改），其余有则验格式
> - `AdminUpdateDTO`：仅 id @NotNull，其余有则验格式；顺带修复 `AdminServiceImpl.updateAdmin` **无条件 encode(null) → 不传密码更新即 500** 的 bug（改为密码成对且非空才加密）
> - `SeckillSpuAddDTO`/`SeckillSkuAddDTO`：全必填(@NotNull/@DecimalMin/@Min)（秒杀管理接口直插 Mapper，无 Service 校验层）
> - `AdminAddDTO`：恢复校验（username/password/phone/email @NotBlank/@Pattern，原 4 处 @NotNull 被注释）；addAdmin 补类级+参数级 @Validated（GET 绑定 DTO 需参数级触发）
> - **⭐ 全局异常处理器补全**：`mall-common GlobalControllerExceptionHandler` 缺 `MethodArgumentNotValidException`/`ConstraintViolationException` handler → 校验失败落 Throwable 返回 500 而非 400，已补两个 handler（这是校验"真生效"的关键一环）
> - Controller 触发注解：`DeliveryAddressController.addAddress/editAddress`、`AdminController.updateAdmin/addAdmin` 补 @Validated（ums/ams）
> - 已编译验证（mall-common/mall-pojo/mall-ums/mall-ams/mall-seckill）
> ⚠️ **延伸发现（已核实非线上风险）**：前端 `admin.js`（REST 风格 add/update/delete）是**废弃残留无人引用**，真接口在 `sso.js`+AdminController；AdminAddDTO 后端接口暂无前端页面接入。
> ✅ **2026-09-07 已彻底核查 + 标记废弃**：admin.js 全项目零 import/零调用（6 方法全无引用，仅 api/index.js re-export 而 index.js 本身也无人用）；调用路由在后端均不存在。已在 `src/api/admin.js` 头部加废弃标注 + `src/api/index.js` re-export 行加 TODO 提示（前端仓库，待提交）。
> 回归说明：doRegister 补 @Valid 后，传非法值将触发 400（依赖新增的 MethodArgumentNotValidException handler），已编译验证。

> **2026-08-28 新增（源自 06-安全设计 Q6 审计）**：全项目审计 39 个 @RequestBody 接口，**5 个漏了 @Validated 触发开关**（规则在 DTO 但没触发 = 校验静默失效）：`UserController.doRegister`（**注册最严重**——UserRegistryDTO 的 @NotNull/@Pattern 全失效，非法数据可入库）、`DeliveryAddressController.addAddress/editAddress`（地址增改）、`AdminController.updateAdmin`（管理员更新）。`PaymentCallbackController.wechatNotify`（String body）无需 DTO 校验，可豁免。

**方案（P1）**：
1. 5 个接口（注册/地址增改/管理员更新）方法参数补 `@Validated`（注册最优先）——✅ doRegister 已完成；其余 4 个 DTO 无规则，需先补规则
2. 回归：传非法值断言返回 400（注册接口重点验证用户名/邮箱/手机号格式）——待服务器回归
3. 可选：AOP 统一给 @RequestBody 加校验，从根上消灭漏触发

**面试价值**：能讲"注解校验会静默失效（规则与触发分离），我审计出 5 个漏触发接口"——**实施中进一步发现 4 个是 DTO 本身无规则（比漏触发更早的问题），修正了原审计结论**

### 26. 【网络】容器网络隔离：应用网/数据网分网段（2026-08-28 记录，待实施）

> **2026-08-28 记录（源自 07-部署运维 Q3）**：csmall-net 是**单网段大锅烩**（21 容器全挂一个 bridge 网络），无网络隔离。企业做法：**应用网/数据网分网段**——应用（gateway/front/order...）在应用网，数据库（mysql/redis/es）在数据网，**数据网只允许应用网访问** → 网络隔离 = 纵深防御（比端口映射收窄更进一步）。

**方案（P2，演示项目暂缓）**：
1. compose 定义两个网络：app-net（应用服务）/ data-net（数据库 + 中间件）
2. 应用服务挂 app-net + 仅访问 data-net；数据库只挂 data-net
3. 中间件端口映射收窄 127.0.0.1（配合 TODO #24）
4. 验证：应用正常访问数据库、数据库不被外部网络访问

**面试价值**：能讲"容器网络也要纵深防御——应用网/数据网分网段"

### 27. 【安全】KMS 密钥管理（仅认知记录，不实施）

> **2026-08-28 记录（源自 07 Q5）**：企业敏感机密用 KMS/Vault/Secrets Manager（加密存储 + 审计 + 自动轮换），用法 = 存机密 → 授权 → 应用解密调用（最常用"加密后存配置"）。**本项目不上**——单机单团队 .env 够用；优先级排在 JWT 密钥随机化（#25）/ MySQL 强密码（#24）/ Nacos 认证（#13）之后。面试讲认知即可。

### 28. 【运维】容器 JRE 无诊断工具（换 JDK 镜像 / 装 Arthas / JFR，2026-08-28 实测，待实施）

> **2026-08-28 实测（源自 08-JVM Q5）**：生产容器 `eclipse-temurin:21-jre-alpine` 是 **JRE**——bin 里只有 java/jfr/keytool，**无 jstack/jmap/jstat/jcmd/jinfo/jps**。当前生产遇到 CPU 暴涨 / Full GC 想抓线程栈、堆转储**抓不了**（只有 OOM 自动 dump + JFR 可用）。

**方案（P2，三选一或组合）**：
1. **镜像换 JDK**：temurin:21-jdk-alpine（有全套工具，代价镜像大 ~300MB）
2. **装 Arthas**（阿里开源，JRE 可 attach）：生产在线诊断 dashboard/thread/stack，免重启
3. **JFR 常开**：启动参数 `-XX:StartFlightRecording=...`（JDK 自带，出事回放 CPU/GC/内存事件）

**面试价值**：能讲"我实测发现生产容器是 JRE 抓不了 jstack，改进方案是 JDK 镜像/Arthas/JFR"——比背流程高一个段位的诚实审计

> 🟡 **第二批 #5**
### 29. 【备份】数据库定期备份 ✅ 已完成（2026-09-08）

> ✅ **2026-09-08 完成**：备份脚本 `/data/csmall/backup/backup-db.sh`（docker exec mysqldump 6 库全量 + gzip + 保留 7 天）+ cron 每日 02:30 + 仓库留档 `deploy/backup-db.sh`。实测备份 49KB/6 库/39 表,内容完整性验证通过。**恢复演练**（TODO 铁律）待做：导临时库验证可用性。
>
> **2026-08-28 记录（源自 07 Q9）**：07 Q9 提到"定期 mysqldump（可改进项）"但 TODO 从未记录——**文档缺口补上**。当前数据库数据在 Docker 卷（csmall_mysql_data），无定时备份；数据是"命"，备份是运维底线。

**方案（P2）**：
1. cron 定时 mysqldump（每日全量 6 库）+ 保留策略（保留 7 天/循环覆盖）
2. 备份文件落 `/data/csmall/backup/`（或对象存储）
3. **定期恢复演练**（备份没验证过 = 没有备份）
4. 与 R8 巡检同节奏（月检 + 大版本前）

**面试价值**：能讲"备份要有恢复演练，没验证过的备份等于没有"的运维底线

### 30. 【监控】Prometheus + Alertmanager 主动告警（2026-08-28 记录，待实施）

> **2026-08-28 记录（源自 07 Q10）**：07 Q10 提到"改进方向是 Prometheus + Alertmanager"但 TODO 从未记录——**文档缺口补上**。**"resource 挂 2 天才发现"的根治手段**：restart 只能拉起崩溃，静默挂/循环崩靠人工发现——主动告警是唯一根治。

**方案（P2，运维最短板）**：
1. Prometheus 采集：容器指标（cAdvisor/Docker exporter）+ 服务指标（actuator）+ JVM 指标
2. 告警规则：容器挂 / CPU 高 / 内存高 / 接口错误率 / 磁盘
3. 通知：Alertmanager → 邮件 / 钉钉 webhook
4. 与 R8 巡检、Q8/Q10 教训呼应

**面试价值**：能讲"被动监控（SkyWalking/Sentinel/docker）和主动告警（Prometheus）的区别，告警是根治静默故障"

---

### 31. 【AI】评估生产开启向量检索 embedding-enabled: true（2026-09-02 记录，待决策）

> **2026-09-02 新增（源自 09-AI模块 Q3 服务器复核）**：生产 `embedding-enabled: false`（ES 全文检索），向量检索 = 完整代码 + test 验证 + 预留开关。**用户倾向开启（BGE-M3 免费），待后续考虑**。

**现状（服务器 + 代码实证）**：
- prod yml `embedding-enabled: false` + 注释"生产默认关闭，按需开启"；test 环境 true
- ES cool_shark_mall_ai 索引实测**无 semanticVector 字段**（全文模式建的索引）
- .env 已有 EMBEDDING_API_KEY（硅基流动）；AiProperties/EsIndexInitializer/VectorSyncServiceImpl/RagServiceImpl 代码全就绪

**当时关闭的理由**：① 20 条商品 IK 毫秒级且准，语义优势兑现不了 = 收益 0 ② 向量化依赖硅基流动外部 API = 多一个故障点 ③ 稳定优先（sync-auto-on-startup 要部署即用）④ 全量重同步几千条会限流耗时

**开启评估（用户倾向：免费值得开）**：
1. prod yml 改 `embedding-enabled: true` → 重启
2. 重建/补索引：EsIndexInitializer 需建含 dense_vector(1024) 的 mapping（当前索引无向量字段）
3. sync-auto-on-startup 全量向量化（20 条，免费）
4. 验证：/ai/search 语义召回（"学生党性价比"等）+ ES 出现 semanticVector 字段
5. 风险：硅基流动 API 依赖——挂了向量化失败，**⚠️ 开启前必查：RagServiceImpl 是否有全文降级逻辑（无则先补），否则搜索直接报错**

**面试价值**：开 = 完整 RAG 链路真实运行；关 = 讲"按需开"的工程判断——两者都可讲；决策点 = 外部 API 稳定性能否接受

---

### 32. 【AI】AI 导购升级 Agent（Function Calling + ReAct）（2026-09-02 评估，仅计划不执行）

> **2026-09-02 新增（源自 09 Q0/Q12 讨论）**：当前 AI 导购 = "LLM 解析意图 + 代码写死执行"（无工具调用/无循环/无动作决策权）；升级 Agent = 技术演示增强 + 面试素材（生产 0 调用，业务收益为零）。

**关键结论**：
- **值得做但定位"演示增强"**：面试价值高（Function Calling + ReAct 实战）、学习价值高、业务价值低
- DeepSeek 支持 tools 参数（function calling），现有 intentSearch 可直接声明为第一个工具
- **边界是核心**：只读工具自动执行 / 写操作用户确认（human-in-the-loop）/ 参数 schema 约束 / 轮数上限 3 / 预算复用（2 元/天）/ 动作审计 / 失败降级回 RAG
- **工作量**：P0 最小 Function Calling ≈ 0.5~1 天（DeepSeekAiClient tools 支持 + search_products 工具 + ChatServiceImpl 循环）；P1 完整单 Agent ≈ 2~3 天（compare/get_stock 工具 + 3 轮 ReAct + 审计 + 降级）；P2 框架化可选（Spring AI 重构）

📄 **完整方案（现状/差距/场景工具集/9 条边界/分阶段到文件级/成本风险/面试话术/执行清单）见 [[AI导购Agent升级方案]]**

---

> 🟡 **第二批 #1**：用户可见 bug（search 缺 2 条新商品）✅ 已修复（2026-09-08 A2 上线）
### 33. 【搜索】双索引数据不一致（mall-search 与 mall-ai 各维护一个 ES 索引）✅ 已完成（2026-09-08，代码+本地验证+已部署）

> ✅ **2026-09-08 完成（方案 A2）+ 当天中午部署上线**：统一单一索引 + mall-search 改造为**只读普通搜索降级层** + 同步模型补全 + 前端 fallback。本地实测：AI 主链路（意图/扩展/重排）全通；`docker stop mall-ai` 后前端秒级降级到普通搜索。服务器验证（2026-09-08）：AI 索引 `/ai/syncAll` 重建后 **19 条**（剔除已下架 18）、空索引 `cool_shark_mall_index` 与旧 `cool_shark_mall_index2` 已删除、普通搜索 `/search` 返回正常。明细见 [[TODO第二批实现与原理]] §五、[[搜索双索引统一与一致性评估]]。

> **2026-09-02 新增（源自 09 Q7 复核，服务器实测确认）**：普通搜索和 AI 搜索**各维护一个 ES 索引 + 各一套同步链路**——数据同源（pms_spu，都经 Dubbo IForFrontSpuService 拉取），但**数据不一致实锤**。

**现状（2026-09-02 服务器实测）**：
- ES 三个索引：`cool_shark_mall_index`（**0 条**，空/旧索引遗留）、`cool_shark_mall_index2`（**18 条**，mall-search 用）、`cool_shark_mall_ai`（**20 条**，mall-ai 用）
- **不一致实锤：AI 索引 20 > search 索引 18**——2 条新商品只进了 AI 索引，普通搜索查不到新商品（两条同步链路各自为政）
- mall-search 同步：手动 `/search/sync` → Dubbo 分页拉 DB → Spring Data ES 批量写（SearchServiceImpl.loadSpuByPage，pageSize 注释还写 2）
- mall-ai 同步：启动自动 sync-auto-on-startup + `/ai/syncAll`（VectorSyncServiceImpl）

**影响**：① 同一批商品重复存储两份 + 旧索引垃圾（index 空索引）② 两套同步链路都要维护，漏同步 = 普通搜索缺新商品（已发生）③ 面试若被问"两个索引怎么保证一致"无解——当前不一致

**方案（P2，演示项目暂缓）**：
1. **统一单一索引**：mall-search 与 mall-ai 共用同一个 ES 索引（如保留 cool_shark_mall_ai 或重建 index2）
2. mall-search 只做**召回层**（过滤/分页/排序），mall-ai 在召回基础上做意图重排（AI 不自己建数据）≈ 淘宝架构
3. 清理空索引 cool_shark_mall_index（0 条）
4. 同步收敛到一条链路（Dubbo 变更通知 + 全量启动同步）

**面试价值**：能主动讲"我发现两个搜索各维护索引、数据不一致（18 vs 20），演进方向是统一索引 + AI 只做重排层"——比被面试官问出来强

---

### 34. 【AI】AI 模块高并发应对 ✅ 已完成并部署（2026-09-08，与 #2 合并实施）

> ✅ **2026-09-08 完成 + 已部署（与 #2 合并）**：Sentinel 入口限流(3组) ✅ + **并发闸门 Semaphore=20** ✅（核心：防少量慢请求占满 Tomcat；挂 LLM 调用汇聚点，超限→既有降级路径=纯ES/busy/SSE error，非 500）+ 每用户频控 60s/10 ✅。**未做**（按范围确认）：问答 Redis 缓存（#34 层 3）、多实例扩容（层 6，随 #4）。服务器实测限流/频控生效（30 并发 20×429）。执行清单见 [[AI限流与并发闸门-部署执行清单-2026-09-08]]。
>
> **2026-09-02 新增（源自 09 Q9）**：AI 问答高并发与秒杀本质不同——秒杀是大量快请求（限流削峰可控），AI 是**少量慢请求占 Tomcat 线程 + 外部 LLM API 配额有限**。两个铁约束：① 每个 SSE 请求秒级 + 挂一个线程（少量用户就能占满线程池，拖垮其他接口）② LLM API 外部共享资源（QPS 配额 + token 收费），不能无限调。

**应对层次（从便宜到贵）**：
1. 🔴 **Sentinel 入口限流**：QPS 限流 /ai/**（与 TODO #2 合并——ai 接口现在无限流）
2. 🔴 **并发闸门**：Semaphore 限制"同时进行的 LLM 调用数"（如 20），超出 429"AI 繁忙"——**不设闸门，Tomcat 线程被 AI 占满，其他接口全挂**
3. 🟠 **问答缓存**：常见问答 Redis 缓存 2min TTL（IoT AssistantService 已有 2min 缓存先例可复制）→ 重复问题不调 LLM
4. 🟠 **降级链路**：LLM 满/挂 → 降级纯 ES 检索（直接给商品列表不生成）→ 保证"有响应"而非"卡死"（与 TODO #31 向量降级同一思想）
5. 🟡 **每用户频控**：Redis 计数（如 10 次/分钟），防单用户刷爆预算
6. 🟡 **多实例扩容**：会话在 Redis = 无状态 → 多实例分压 SSE 长连接（内存允许时，配合 TODO #4）

**项目现状**：SSE 异步 ✅ / 每日预算 TokenBudget ✅ / 无状态会话 ✅；Sentinel ai 限流 ❌ / 并发闸门 ❌ / 问答缓存 ❌ / 降级 ❌

**面试价值**：能讲清"AI 高并发 ≠ 秒杀高并发——核心是并发闸门防线程占满 + 缓存降 LLM 调用 + 降级保可用 + 无状态扩容"，AI 上生产前必修课

---

### 35. 【JSON】统一 Jackson，替换 fastjson（2026-09-03 记录，待实施）

> **2026-09-03 新增（源自 10 Q6 讨论，用户拍板）**：项目 JSON 混用——**Web 层默认 Jackson（Spring 集成），业务手写解析用 fastjson**。决定统一 Jackson。

**现状（代码实证）**：
- fastjson 2.0.43（根 pom）显式依赖 8 模块：mall-ai（DeepSeekAiClient/SiliconFlowEmbeddingClient/SessionManager/ChatServiceImpl/PreferenceExtractor/SearchPipeline）、mall-order（mq/支付宝拼参）、mall-seckill（MessageRetryTask/SeckillServiceImpl）、mall-common（IdempotentAspect/JwtTokenUtils）、mall-product（ImageUrlPrefixHelper）、mall-gateway、mall-sso、mall-front
- Jackson 已在：Web 层序列化（@JsonFormat/@JsonSerialize 全 VO）、JacksonConfiguration（Long→String 精度 + jsr310）、security 401/403 ObjectMapper、ListConvertUtils、jjwt-jackson
- 混用点：手写解析 JSON.parseObject/JSONObject/JSONArray（几十处）

**为什么统一 Jackson**：fastjson 1.x 有反序列化 RCE 连环漏洞史（2020-2022，1.2.24/1.2.68 多版本爆洞）；2.x 重写已修但团队规范常统一；Jackson 与 Spring Boot 深度集成（消息转换器/注解/配置中心化）+ 安全生态

**方案（P2，演示项目暂缓）**：
1. 手写解析替换清单：`JSON.parseObject/JSONObject/JSONArray` → ObjectMapper（readValue/writeValueAsString），JSON.toJSONString → objectMapper.writeValueAsString
2. mall-common 提供统一 ObjectMapper Bean（已有 JacksonConfiguration 可注入）
3. 依赖收敛：逐步移除各模块 fastjson 依赖（根 pom 保留管理或删）
4. 回归重点：AI 对话（DeepSeek 请求拼装/会话 JSON）、MQ 消息序列化（OrderQueueConsumer/MessageRetryTask）、支付宝支付 bizContent 拼参、JWT、幂等 AOP 参数 MD5

**面试价值**：能讲 JSON 库选型（Jackson vs fastjson 分工 + fastjson 1.x 漏洞史 + 为何统一 Jackson）

---

### 37. 【数据】测试账号密码哈希验证清理（liucs/wangkj + 造数规范）（2026-09-03 记录，待验证）

> **2026-09-03 新增（源自 10-Q11 BCrypt 归因纠错实验）**：服务器实验证明 admin 登录失败的"跨平台 $2a/$2b"归因错误——真因是**测试数据哈希与声称密码不匹配**。实验时发现 **liucs/wangkj 用户仍存旧 `$2a$10$N.zmdr...` 哈希**（与 admin 当初失败的同一个），若其密码声称 123456 同样登录失败。

**待验证/处理**：
1. 用 bcrypt 验证 liucs/wangkj 的哈希到底匹配什么密码（或确认它们实际用什么密码登录）
2. 若密码与测试预期不符 → 重置为正确哈希（目标平台生成 $2b）
3. 顺带排查其他表是否有"手填未验证哈希"的账号
4. **固化规范**：造测试数据的密码哈希必须 `bcrypt.checkpw(密码, 哈希)` 验证过再入库，禁止手工复制哈希（三用户同哈希 = 复制粘贴特征）

**面试价值**：能讲"我推翻了自己文档里的错误归因（$2a 跨平台 → 哈希与密码不匹配），并排查出同类测试数据问题（liucs/wangkj）"

---

> 🟢 **第三批**
### 39. 【CI/CD】镜像仓库 + 版本 tag + 构建流水线（2026-09-03 评估，待实施）

> **2026-09-03 新增（源自容器化部署企业级差距评估）**：当前构建部署全手动（本地 `mvn package` → scp jar → compose build），**无镜像仓库、无版本 tag、无 CI/CD 流水线、无镜像安全扫描**——"已完成表 #9"曾笼统记过 CI/CD，实际仓库无任何流水线配置（.github/workflows / Jenkinsfile 均不存在），部署不可复现、不可回滚。

**方案（P2，面试价值最高的规模化一层）**：
1. **镜像仓库 + tag**：构建产物推仓库（Docker Hub / 阿里云 ACR），`image: mall-sso:20260903-<sha>` 版本化 → 回滚 = 改 tag 重新拉起，不再"只有一个 jar 覆盖即丢"
2. **CI/CD 流水线**（GitHub Actions 即可，仓库已托管 GitHub）：push → 自动 mvn package → docker build → push 镜像 → 服务器 pull 重启（演示项目可只做到镜像构建推送，部署半自动）
3. **镜像安全扫描**：Trivy 扫基础镜像/依赖 CVE（可并入流水线）
4. 与 TODO #4（集群化）无冲突——先有版本化镜像，才有资格谈多副本灰度

**面试价值**：能讲"部署不可复现是隐患——我评估了镜像仓库+tag+CI 流水线，明白回滚 = 换 tag 而不是重传 jar"——把"单机 Compose"与"企业级交付"之间的差距讲清

---

### 40. 【部署】微服务自身 healthcheck 缺失（2026-09-03 审计，待实施）

> **2026-09-03 新增（源自容器化部署企业级差距评估）**：compose 中**中间件全带 healthcheck**（mysqladmin ping/redis-cli/curl），但 **11 个微服务均无 healthcheck**、无 `/actuator/health` 暴露 → `restart: on-failure` 只能拉起"进程崩溃"，**服务起但内部不健康（连不上 Nacos/DB/Redis）时不会被重启**，Docker 认为"活着"。

**方案（P2）**：
1. 引入 `spring-boot-starter-actuator`（mall-common 一处依赖，全部生效）+ 暴露 `/actuator/health`
2. compose 每个微服务加 `healthcheck: curl -f http://localhost:<port>/actuator/health`（依赖方可用 `depends_on: condition: service_healthy` 做服务级就绪等待，替代现在的"只等中间件"）
3. 注意：actuator 端点收窄（只开 health，避免暴露 env/beans 等敏感端点，呼应安全审计）

**面试价值**：能讲"进程活着 ≠ 服务健康——我补了 actuator healthcheck，让依赖方等服务真正就绪"——容器化可观测基础课

---

> 🟢 **第三批**
### 41. 【日志】日志聚合集中采集（Loki/ELK）（2026-09-03 评估，待实施）

> **2026-09-03 新增（源自容器化部署企业级差距评估）**：当前日志全部 stdout → `docker logs` 逐容器查看，**无集中采集/检索/关联**——排查跨服务问题（订单→支付→MQ）要 ssh 逐个容器翻日志。SkyWalking 有 trace 但无日志正文聚合（TODO #16 是应用层 traceId 落日志，与日志采集是两回事）。

**方案（P2，运维短板补齐，与 TODO #30 告警配套）**：
1. **Loki + Promtail**（轻量，单机 4C16G 友好，与 Prometheus 同生态）：Promtail 采集各容器 stdout → Loki 存储 → Grafana 按服务/时间/traceId 检索
2. 或 ELK（重，演示项目不推荐）
3. 保留策略：演示项目 7 天足够
4. 面试演示：出问题时"一条 traceId 捞出全链路日志"

**面试价值**：能讲"日志是最后一道可观测——我有 trace（SW）、有指标（计划 Prometheus）、缺日志聚合（Loki），补上是三件套闭环"

---

### 42. 【评估】跨机"真集群"演示方案（Redis HA + 秒杀双实例，2026-09-07 评估，优先级低仅评估）

> **2026-09-07 新增（面试"真集群"演示需求评估）**：目标不是全量容器集群（老机内存/工作量不允许），而是给**价值最高的 1-2 个**做跨机集群验证机制。**结论已定，暂不实施，待实际需要时按此推进。**

**选型结论（为什么是这两个）**：
| 候选 | 面试价值 | 全站依赖 | 跨机成本 | 结论 |
|------|---------|---------|---------|------|
| **Redis** | 极高（"挂了怎么办"必问） | 全站（秒杀/登录/幂等） | 低（主从+哨兵） | ✅ 选 |
| **秒杀服务** | 极高（高并发/负载均衡） | 核心业务 | 中 | ✅ 选 |
| Nacos | 高（raft） | 全站 | 高（**2 台做不了 3 节点 raft 多数派**） | ❌ |
| 商品/订单/Gateway | 中 | — | — | ❌ 优先级低 |

**关键判断：k3s 不是必须的**——1-2 个服务跨机双实例用 compose/手动 docker 即可跑通：
- Redis HA：老机主 + 新机从 + 哨兵（kill 主容器 → 哨兵自动切 → 服务无感）
- 秒杀双实例：两个实例都注册老机 Nacos → Gateway `lb://` 自动轮询 → 停一个 Nacos 自动剔除
- k3s 仅作为第三阶段"编排概念"锦上添花，不是双实例的前提

**硬件评估**：新服务器 **2核8G 同地域同 VPC**（成都，内网互通）足够——Redis 从 ~100M + 秒杀副本(-Xmx256m) ~500M，8G 富余。老机需先做 R7 腾内存（前提铁律）。

**分阶段（每步可独立演示/回滚/不碰其他容器）**：
1. A：跨机 Redis 主从+哨兵 → kill 主演示切换（~半天）
2. B：秒杀副本跑新机 → 压测看负载均衡 + 停实例看剔除（~1 天）
3. C（可选）：新机 k3s server 学编排概念，暂不动业务
- ⚠️ 秒杀双实例真实改造点：`MessageRetryTask` 定时任务要加 Redis 分布式锁防双跑（本身是面试加分细节）

**诚实边界（面试别吹过头）**：2 台小机器 = 演示级 HA（防单机宕机，不防地域灾难）；只集群 Redis+秒杀，其他 19 容器仍是 compose 单实例——话术："选依赖最广的中间件+并发最高的业务验证机制，不是全量迁移"。

**面试价值**：跨机 Redis 真 HA（物理隔离 ≠ 单机哨兵）+ 秒杀多实例真实负载均衡/故障剔除/定时任务分布式锁——TODO #9/#4/#15 从"单机演示"升级为"跨机真实集群"。

---

### 43. 【整洁】合并重复的 BindException 全局异常处理器（2026-09-07 记录，低优先级）

> **2026-09-07 记录（源自 #23 校验审计）**：项目存在**两个 @RestControllerAdvice 都声明了逻辑相同的 `BindException` handler**：
> - `GlobalControllerExceptionHandler`（mall-common/.../exception/handler/）
> - `BindExceptionHandler`（mall-common/.../validation/handler/）
>
> **冗余但不冲突**：Spring 对跨 advice 的同异常声明按注册顺序取第一个匹配，不 Ambiguous（生产旧 jar 双 advice 共存运行多时无歧义日志实证）。
>
> **合并方案（低优先级，整洁项）**：
> 1. 把 `BindExceptionHandler.handleBindException` 并入 `GlobalControllerExceptionHandler`（逻辑已相同，保留一份即可）
> 2. 删除旧 advice `BindExceptionHandler.java`
> 3. 回归：表单绑定（@ModelAttribute）/ @RequestBody 校验失败仍返回 400
>
> **面试价值**：能讲"我在校验审计中发现两个全局 advice 冗余声明同一异常 handler——跨 advice 不冲突（按序取一），但整洁上应合并"，展示对 Spring 异常解析机制的准确认知。

---

### 44. 【安全】吊销已泄露的硅基流动 Embedding Key（2026-09-08 记录，待处理）🔴

> **2026-09-08 发现**：`mall-ai-webapi/src/main/resources/application-test.yml` 曾硬编码硅基流动 embedding key（`sk-pffsuu...`），该文件已被 commit **719ff6f** 提交并 **push 到公开 GitHub 仓库**——**key 已公开泄露**（即使本地已改占位符，历史提交里仍可查到）。

**处理（尽快）**：
1. 登录硅基流动控制台（siliconflow.cn）→ API 密钥管理 → **吊销/删除** `sk-pffsuu...` 开头的旧 key
2. 生成新 key → 配到本地环境变量 `EMBEDDING_API_KEY`（.env 不入库 / IDE Run Configuration）
3. 服务器 `.env` 的 `EMBEDDING_API_KEY` 同步换新值 → 重启 mall-ai
4. 验证：本地 `embedding-enabled: true` 向量检索正常（/ai/syncAll 能向量化）

> **教训**：API key 绝不硬编码进 yml/代码；test 环境也要用占位符 + 环境变量注入。已改占位符见 commit c0d7209。

---

### 45. 【规范】统一 Dubbo 应用名（front/search/ams 撞名但无 provider，2026-09-08 记录，第三批）

> **2026-09-08 记录（源自 #6 全项目排查）**：修复 seckill/ums/product 撞名时，发现 **mall-front / mall-search / mall-ams** 的 `dubbo.application.name` 与 `spring.application.name` 相同（撞名），但三者**均无 @DubboService 暴露**（不注册 20880 provider 实例）→ Nacos 实测仅 HTTP 实例、gateway `lb://` 安全，**无实际风险，本次不改**（避免无谓回归面）。

**统一规范（第三批，未来顺手做）**：三个模块 dubbo 名加 `-dubbo` 后缀（prod/test 对齐），与 order/ai/seckill/ums/product 一致——**防未来给这些模块加 Dubbo provider 时重新踩 #6 坑**（加 provider 瞬间 20880 混入现有服务名，lb:// 立刻 500）。

**涉及文件**：mall-front-webapi / mall-search-webapi / mall-ams-webapi 的 application-{prod,test}.yml（各 2 处 dubbo.application.name）。

**面试价值**：能讲"我排查 #6 时发现 3 个模块撞名但无 provider——当时没风险所以没动，但记了规范项防未来加 provider 时踩坑"，展示"按风险分级处理 + 前瞻性记录"。

---

## ✅ 已完成归档

> **已完成（第一批 + 历史 + 第二批 #8/#36/#23/#14 P0+P1/#33/#5）已整体迁至 [[TODO已完成]]**（含第一批明细表、第一批之前的历史归档、及所有正文标"✅ 已完成"的条目）。第二批代码批已完成并**已部署服务器（2026-09-08）**，部署明细见 [[第二批部署执行清单-2026-09-08]]、[[Sentinel部署执行清单-2026-09-08]]。本文件只保留未完成 / 部分完成 / 暂缓 / 评估项。

---

**维护提示**: 新增待办时按优先级放入对应区块（🔴高/🟡中/🟢绿），完成后移入 [[TODO已完成]] 末尾。
