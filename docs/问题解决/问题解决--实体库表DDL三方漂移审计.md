# 问题解决 — 实体库表 DDL 三方漂移审计

> **日期**: 2026-09-11（文档整理成文；素材主体为 2026-08-04 审计 + 2026-08-03 修复 + 2026-09-11 新增漂移）
> **一句话**: 同一份数据有**三个"事实来源"**（Java 实体类 / 线上库 / 建表 DDL 文件）**谁都不自动对齐** —— 而且**它不会报错**，下一次换掉查询路径才炸。
> **性质**: **一类问题的合集** —— 讲**三方核对的方法论 + 台账 + 类别**，不重述个案修复过程
> **覆盖**: [[数据库Schema漂移审计]]（本地 MySQL **39 张表** × mall-pojo **32 个实体** × `database/` **34 个 SQL 文件** 三方对比）+ ~~**#57**~~（✅ **2026-09-12 已完成只读核实：14 处差异全部是「服务器比快照新」，0 处需要 ALTER**）+ 2026-09-11 新增 2 处漂移
> **关联（仅互链、不合并）**: [[数据库Schema漂移审计]]（**原始审计报告 = 本篇的主素材与证据源**）、[[问题解决--商品实体映射]]（**个案修复记录，分工见 §1.3**）、[[TODO文件]]（**#57 状态以此为准**）、[[商品与秒杀扩容方案]]（§1.6/§1.7 = 同类问题 `#63` 的互链对象）、[[问题解决--搜索双索引与降级分层]]（同为"架构债识别与处置"）

---

## 一、这一类问题的共性（★ 先看这张表）

**一句话本质**：**"建表 DDL 文件"不是事实来源，"线上库"才是** —— 但**代码期望**既不是前者也不是后者，它只活在**实体类的字段名里**。三方（`实体类` / `线上库` / `database/*.sql`）之间**没有任何自动校验机制**，所以**漂移是必然的，发现是偶然的**。

### 1.1 三个事实来源各自"是什么"（这是本篇的方法论地基）

| 事实来源 | 载体 | 权威性 | 怎么读 |
|---|---|---|---|
| **Java 实体类** | `mall-pojo` 的 `*Entity`/`*VO`/模型类 + `@TableName` / `@TableField` | **代码期望**（"程序以为库长什么样"） | 读注解 + 字段名（注解优先级高于字段名） |
| **线上库** | 生产 `cs_mall_*` 各库的**真实表结构** | **实际事实**（唯一真正跑的数据源） | 只读查 `information_schema.COLUMNS` / `TABLES` |
| **建表 DDL 文件** | 仓库 `database/**/*.sql` | **只是历史快照**（不是事实来源） | 读 DDL 文本 |

### 1.2 ★ 为什么"不会报错"（本类问题最值钱的一句）

| 漂移方向 | 谁会报错 | **为什么平时不报错** |
|---|---|---|
| **实体有、库没有** | `Unknown column '<x>' in 'field list'` | ✅ 因为**该项目大量走自定义 Mapper XML**（手写 SQL 只 SELECT 真实存在的列）→ 实体多出来的字段**根本不参与 SQL** → 静默 |
| **表整体缺失** | `Table '<db>.<t>' doesn't exist` | ✅ 因为对应功能**从未被调用**（休眠）→ 静默 |
| **DDL 文件过期** | 谁都不报 | ✅ 文件不参与运行时 → **永久静默**，只有人读到才发现 |

> ⇒ **结论：这类问题的暴露条件不是"代码有 bug"，而是"路径被换掉了"** —— 一旦把自定义 Mapper XML 换成 **MyBatis-Plus `BaseMapper`**（`selectById` / `insert` 会按实体全字段生成 SQL），漂移**当场变成 `Unknown column`**。这不是假设：[[问题解决--商品实体映射]] §二/§三 就是这条路径切换踩出来的坑。

### 1.3 🧭 与 [[问题解决--商品实体映射]] 的分工（**明确不重复**）

| | [[问题解决--商品实体映射]]（既有，2026-04-10） | **本篇（2026-09-11）** |
|---|---|---|
| **视角** | **"某个实体对不齐 → 怎么修"**（个案维修） | **"三方核对怎么做 + 台账 + 分类"**（方法论/制度） |
| **内容** | 逐条：`@TableName` 缺失 25 个文件、`is_` 前缀 `@TableField`、`pms_category` 字段不匹配、`ams_login_log` 表名、测试数据 | 三方口径、差异分类（3 类）、**类别级台账**、DDL 补齐 + 核对周期、生产侧待核 |
| **边界** | 个案细节（哪个文件第几行、怎么改）**一律不搬过来** | 只引用**类别级结论**；个案修复过程请直接看它 |
| **关系** | 它是**执行记录** | 它是**审计方法 + 当前状态（#57）** |

> 📌 **互链方向**：本篇 §二 每一类的"个案证据"都指向 [[问题解决--商品实体映射]] / [[数据库Schema漂移审计]]，**不复制正文**；既有文档**一字不改**（遵循"仅加互链、不合并"的文档纪律）。

---

## 二、问题 1：**实体有、库没有**（高危，靠自定义 Mapper XML 掩盖）

> 📎 主素材：[[数据库Schema漂移审计]] §三；同类个案修复：[[问题解决--商品实体映射]] §二/§四/§五

**现象**：实体类里明明白白声明了字段，数据库里没有对应列 —— 但项目跑得好好的。

**排查（怎么发现的）**：三方对比时，把**实体字段集**（含 `@TableField` 重命名后的真实列名）与 `information_schema.COLUMNS` 的**列名集**做差集 → 差集非空即命中。

**根因**：**这些表只走自定义 Mapper XML**，手写 SQL 绕开了实体字段 → 多出来的字段从未出现在 SQL 里 → 运行时零感知。

**台账（类别级实例，表名/列名原样保留）**：

| 表 | 实体有但数据库没有 | 数据库有但实体没有 | 说明 |
|----|------------------|-------------------|------|
| `pms_sku_specification` | `attribute_name`、`attribute_value`、`unit`、`sort` | `attribute_value_id` | SKU 规格表，升级 SKU 体系必用 |
| `pms_attribute` | `description`、`input_type`、`value_list`、`unit`、`allow_customize`；外键列 `template_id`（库是 `attribute_template_id`） | `type`、`values` | 属性表，模板生成 SKU 的核心 |
| `pms_attribute_template_value` | `attribute_id`；外键列 `template_id`（库是 `attribute_template_id`） | `value` | 模板可选值表 |
| `pms_picture` | `cover`、`description`、`width`、`height` | `title` | 相册图片表，相册系统启用时需修 |
| `pms_album` | `sort` | — | 相册表 |
| `ams_login_log` | `nickname`、`ip`（库是 `ip_address`）、`gmt_login`（库是 `login_time`） | — | 管理员登录日志，若改 BaseMapper 需对齐 |

> **⚠️ 修复方向（升级时）**：以实体为准，**ALTER 补列 + 对齐外键列名**（`template_id` → `attribute_template_id`）。
> **⚠️ 风险形态**：当前"不报错"**纯属路径侥幸** —— 一旦用 MyBatis-Plus `BaseMapper` 查询/插入就会报 `Unknown column`。

**代码侧取证（可指到 `文件:行`）**：
- `mall-pojo/.../product/model/SkuSpecification.java:18` `@TableName("pms_sku_specification")`、`:40 attributeName`、`:45 attributeValue`、`:50 unit`
- `mall-pojo/.../product/model/Attribute.java:32` `@TableField("attribute_template_id") private Long templateId`、`:53 inputType`、`:58 valueList`、`:73 @TableField("is_allow_customize") allowCustomize`
- `mall-pojo/.../admin/model/AdminLoginLog.java:22` `@TableName("ams_login_log")`、`:46 nickname`、`:52 ip`、`:64 gmtLogin`

**DDL 侧现状（部分已补齐，剩余仍是"库缺列"）**：
- `database/cs_mall_pms/pms_sku_specification.sql:9~13` 已含 `attribute_name` / `attribute_value` / `unit` / `sort`，并把 `attribute_value_id` 标注为"旧模型属性值id（**已废弃，可空**）"
- `database/cs_mall_pms/pms_attribute.sql:7` `attribute_template_id`、`:11 input_type`、`:12 value_list`、`:14 values`（"旧模型…**保留兼容**"）

> ⚠️ **注意"文件已补齐 ≠ 线上已补齐"**：DDL 文件更新只是本地动作，**线上库是否同款 ALTER 就是 #57 要核的事**（见 §五）。

**验证（怎么确认修好了）**：不看"有没有报错"，而是**逐列比对差集为空** —— 即对每张表重新跑一次实体字段集 vs `information_schema.COLUMNS` 列名集，**差集必须为空**。

---

## 三、问题 2：**表整体缺失**（休眠表，用到即 `Table doesn't exist`）

> 📎 主素材：[[数据库Schema漂移审计]] §四；实体侧注解：[[问题解决--商品实体映射]] §三

**现象**：实体类存在、`@TableName` 也加对了，但**库里没有这张表，`database/` 里也没有建表文件**。

**排查**：把实体 `@TableName` 声明的表名集与 `information_schema.TABLES` 的表名集做差集。

**根因**：**功能没做或没启用**（休眠态），所以从未有人执行建表；而实体是"先写好占位"的。

| 实体 | 表名 | 现状 |
|------|------|------|
| `UserDetail` | `ums_user_detail` | 休眠 |
| `DeliveryAddress` | `ums_delivery_address` | 收货地址目前在订单里直接录，不走独立表 |
| `RewardPointLog` | `ums_reward_point_log` | 休眠 |
| `ChangePasswordLog` | `ums_change_password_log` | 休眠 |

**风险**：**用到即报 `Table doesn't exist`**。

**修复/处置（二选一，升级时决策）**：① **补建表**（启用功能）；② **删实体**（明确弃用）。**不做决定本身就是风险** —— 它会在某次"顺手用一下"时变成线上事故。

**验证**：4 张表名在 `information_schema.TABLES` 中**要么存在、要么对应实体已删除**（不允许"实体在、表不在"的中间态长期存在）。

**代码侧取证**：`mall-pojo/.../ums/model/UserDetail.java:22`、`DeliveryAddress.java:21`、`RewardPointLog.java:21`、`ChangePasswordLog.java:21` —— 四个 `@TableName` 都指向上述表名（实体在，表不在）。

---

## 四、问题 3：**DDL 文件 / 文档过期**（永久静默，只有人读才发现）

> 📎 主素材：[[数据库Schema漂移审计]] §二（已修复项）与 §五

**现象**：文件里写的结构/描述**不等于**线上真实结构 —— 且**谁都不会因此报错**。

| 问题 | 详情 |
|------|------|
| ~~`pms_brand.sql` 过期~~ | ✅ 已修复（2026-08-03 更新）：SQL 只有 **8 列**，**真实库 17 列** |
| ~~`README.md` 过期~~ | ✅ 已修复（2026-08-03）：**目录树 / 表数 / 注意事项**多处过期 |
| `init-test-data.sql` 文案 | 结尾提示写"**3 个测试用户**"，**实际插入 10 个** |
| `success` 表命名不规范 | 秒杀库的表没 `seckill_` 前缀，跨库易混淆 |
| undo_log 字符集 | 3 份 undo_log 是 `utf8mb3`，其他全 `utf8mb4` |
| 主键策略矛盾 | README 说"应用层生成"，但 undo_log / ams_login_log / ums_login_log 用了 `AUTO_INCREMENT`，且 `ums_login_log` 自增起点是**雪花式巨值** |

**根因**：**建表文件是"当初写下来就不再回头改"的产物**，而线上库一直在被 ALTER 推动 → **文件只会越来越旧**。它没有 CI、没有校验、没有触发点。

**处置（本次已做 + 应制度化）**：
1. **把文件补齐到与线上一致**（如 `pms_brand.sql` 8 列 → 17 列）；
2. **文件头写清口径**（如 `pms_sku_specification.sql:3` 注明"与 `SkuSpecification.java` 实体对齐"）；
3. **把过期项做成清单**（上表就是台账，未勾掉的继续挂账）。

**验证**：**随机抽样** DDL 文件与 `information_schema` 对比列数与列名；`init-test-data.sql` 这类"文案型"过期靠**读一遍并实跑计数**（INSERT 语句条数 vs 提示文字）。

---

## 五、2026-09-11 新增的 2 处漂移（**同属 #57 范围** · ✅ **2026-09-12 均已追到根因：两处都是 `database/` 导出快照滞后，生产无漂移**）

> 📌 来自 [[TODO文件]] **#57**（2026-09-11 登记，P3）

| # | 漂移 | 事实 | 状态 |
|---|------|------|------|
| ① | `cs_mall_seckill.seckill_message_retry` | **生产存在（79 行）**，但 `database/` 目录里**没有对应 DDL 文件** | ✅ **2026-09-12 追到根因**：由 **Flyway `V5__seckill_message_retry.sql`** 创建（`V6` 再加 `data_source`，与线上 11 列逐一对上）⇒ **不是漂移，是 `database/` 快照滞后**（环境重建走迁移，表**不会**消失）；遗留**可选**：补该导出文件 |
| ② | 生产 `ams_permission` | **多出一列 `value`（全 NULL）**，而 DDL 里**没有该列** | ✅ **2026-09-12 追到根因（方向与原判断相反）**：`Permission.java:38` **实体本来就声明了 `value`**、线上也有 ⇒ 是 **快照缺列**，**不是"生产多列"**；`insertPermission` 不写它 ⇒ 15 行全 NULL 合理；**保留不动**（删了反而与实体冲突） |

**为什么值得单列**：这两处**方向正好相反**，把本类问题的两种极端都占了 ——
- ① 是**"线上有、仓库没有"**：这张表是**本地消息表**（秒杀 `MessageRetryTask` 每 5 秒 DB 轮询重发），**说明它是在线上"长"出来的**，仓库完全不知道它的结构 → ~~**一旦环境重建（新建库/容灾恢复），这张表会凭空消失**，而重试机制会静默失效。~~ ⚠️ **2026-09-12 更正（③）**：该表由 Flyway `V5__seckill_message_retry.sql` 创建（`V6` 加 `data_source`）⇒ **环境重建会走迁移、表不会消失**，重试机制不会静默失效 —— 上面的推演作废。
- ② 是**"线上多、DDL 没有"**：一列**全 NULL** 说明**没有任何代码在写它** → 疑似历史遗留 ALTER 或早期版本残留 → **删不删需要先追根因**，不能顺手 DROP。

**DDL 侧取证**：`database/cs_mall_ams/ams_permission.sql:4~20` 结构为 `id/name/code/type/parent_id/url/method/icon/sort/description/gmt_create/gmt_modified` —— **确实没有 `value` 列**（与 ② 一致）。

**处置**：**不做任何变更**，先并入 #57 的只读核对（见下节）→ 拿到全量差异后再"**一次一处**"生成 ALTER（遵守"抢修/变更不夹带"纪律）。

---

## 六、当前状态与待办：**✅ 服务器已只读核实完成（#57 关闭：0 处需 ALTER）**

### 6.1 状态（两层，别混为一谈）

| 层面 | 状态 | 依据 |
|---|---|---|
| **本地库** | ✅ **已全部修复**（企业级升级过程中已补列，2026-08-03） | [[数据库Schema漂移审计]] 文件头"最新状态" |
| **服务器库** | ✅ **已于 2026-09-12 只读核实：14 处差异全部是「服务器比快照新」，0 处需要 ALTER** | [[TODO文件]] **#57**（已关闭） |

**已修复的本地项（服务器需同款的候选清单）**：
- `pms_attribute_template` 补 3 列（`pinyin` / `keywords` / `sort`）+ 更新 `database/cs_mall_pms/pms_attribute_template.sql`
- 4 张关联表补 `gmt_modified`：`pms_brand_category` / `pms_category_attribute_template` / `ams_role_permission` / `ams_admin_role`
- `pms_brand.sql` 更新为完整结构、`database/README.md` 修正

### 6.2 ⭐ "下次怎么核"（把待办变成可执行动作）

**原则：先只读核对，差异才生成 ALTER。** 不做"猜着 ALTER"，也不做"顺手全量对齐"。

```
① 只读清单：以 [[数据库Schema漂移审计]] §三/§四/§七 的对比清单为输入
② 只读查询：查 information_schema（COLUMNS / TABLES），不碰任何数据
      - 每个 (库, 表) → 取列名集，与实体字段集比差集
      - 每个 (库, 表) → 判存在性（对上 4 张 ums 休眠表）
      - ✅ **已核完（结论见 §五）**：`seckill_message_retry` 由 Flyway V5 建、`ams_permission.value` 实体本就声明 —— 实测 14 处差异全部是「服务器比快照新」，**0 处需要 ALTER**
③ 差异表：逐条列 (库, 表, 列, 方向, 是否高危)，**高危优先**（影响 BaseMapper 的列）
④ 生成 ALTER：只对"实体有、库没有"的方向生成 ADD COLUMN / 外键列重命名
⑤ 低峰执行：用户执行（AI 无写权限），一次一处、可独立回滚
⑥ 回填：核对完回填 [[数据库Schema漂移审计]] 与 [[TODO文件]] #57 两处状态
```

> **为什么要"只读优先"**：线上库**也可能比报告更新**（比如 `pms_attribute_template` 早就补过了）—— 直接按报告 ALTER 会撞 `Duplicate column name`。**报告是 2026-08-04 的快照，不是线上现状。**

---

## 七、跨问题的共性认知与踩坑清单

| 认知 / 坑 | 说明 |
|---|---|
| ⭐ **建表脚本 ≠ 事实来源，线上库才是** | `database/*.sql` 只是历史快照；"文件里没有"不代表"线上没有"（`seckill_message_retry` 就是反例） |
| ⭐ **线上库也没有版本历史** | 它是"**当前状态的堆叠**"，不是可回溯的变更流水 → **结论必须落在"DDL 文件补齐 + 核对周期"**，不能指望库自证 |
| ⭐ **本类问题与 `#63` 同属"代码与线上不一致"** | [[商品与秒杀扩容方案]] §1.6/§1.7：ES `cool_shark_mall_ai` 的 mapping 与代码期望完全不同（**IK 未生效 / 品牌过滤恒 0 / 补全恒空 / 无 `semanticVector`**），启动日志却是 `ES索引 [cool_shark_mall_ai] 已存在` → **永不自愈** |
| ⭐ **共同特征：不报错、不自愈、只在特定路径上暴露** | ES 侧：查询打错字段形态才暴露（`term brandName`=0，`term brandName.keyword`=4）；MySQL 侧：换成 `BaseMapper` 才暴露 `Unknown column` |
| ⭐ **⇒ 必须有"定期核对机制"，而不是靠报错发现** | 报错发现 = 已经打到线上了；本类问题**报错时通常已经是生产事故** |
| ⚠️ **@TableField 会"掩盖"漂移** | 实体字段名 ≠ 列名时，`@TableField("attribute_template_id")` 让**代码侧看起来是对的**，但**DDL/库侧仍是错的** → 对比时必须用"注解后的真实列名"，否则差集算错 |
| ⚠️ **"文件已改" ≠ "线上已改"** | 2026-08-03 改的是**本地库 + 文件**；服务器 ALTER 是**另一件事**（#57）→ 两层状态必须分开记 |
| ⚠️ **休眠表不做决策就是风险** | 4 张 ums 表"补建 or 删实体"**必须选一个**，否则永远悬着 |
| ⚠️ **`init-test-data.sql` 的文案型过期无法自动发现** | 提示写"3 个测试用户"、实际插 10 个 → 只能靠人读；**同类：README 的目录树/表数** |
| 🔑 **验收命令本身也要被验收** | [[商品与秒杀扩容方案]] §1.7 的血泪：`grep -c ik_max_word` 期望 4 是错的（`grep -c` 数行数、mapping 是单行 JSON；实际出现 5 次）→ **核对脚本本身也会撒谎**，关键判据要用确定性证据（如 `_analyze`/`information_schema` 实际列） |

---

## 八、面试综述话术（贯穿本类）

> "我做过一次**三方漂移审计**：把 **Java 实体类**、**线上库**、**仓库建表 DDL 文件**放在一起对 —— **39 张表 × 32 个实体 × 34 个 SQL 文件**。核心发现不是某张表错了，而是**这三方谁都不自动对齐，而且它不会报错**。
>
> 我按**口径**拆：实体侧看注解后的真实列名（`@TableField` 会覆盖字段名），库侧查 `information_schema.COLUMNS`/`TABLES`，DDL 侧读文本；然后做**差集**，把差异归成三类 ——
> ① **实体有、库没有**（`pms_sku_specification` 的 `attribute_name`/`attribute_value`/`unit`/`sort`、`ams_login_log` 的 `ip` vs 库的 `ip_address`）→ **平时不报错是因为全走自定义 Mapper XML**，手写 SQL 根本不带那些字段；**一换成 MyBatis-Plus `BaseMapper` 就 `Unknown column`**，这就是路径侥幸。
> ② **表整体缺失**（`ums_user_detail` / `ums_delivery_address` / `ums_reward_point_log` / `ums_change_password_log` 四张休眠表）→ **用到即 `Table doesn't exist`**，必须做"补建 or 删实体"的决策。
> ③ **DDL 文件/文档过期**（`pms_brand.sql` 只有 8 列而真实库 17 列、`README.md` 目录树过期、`init-test-data.sql` 文案与实际不符）→ **永久静默，只能靠人读**。
>
> 最重要的结论有三条：**第一，建表脚本不是事实来源，线上库才是**；**第二，但线上库也没有版本历史** —— 它只是当前状态的堆叠，所以结论必须落在"**DDL 文件补齐 + 固定核对周期**"，而不是指望库自证；**第三，这类问题跟 `#63`（线上 ES mapping 与代码期望不符：IK 没生效、品牌过滤恒 0、补全恒空）是同一类** —— **共同特征是不报错、不自愈、只在特定路径上暴露**，ES 那边是"查询打错字段形态才暴露"，MySQL 这边是"换掉查询路径才暴露"。所以**必须有定期核对机制，而不是靠报错发现** —— 报错发现意味着已经打到线上了。
>
> 落地节奏上我也很克制：**本地已修复**（企业级升级时补的列），但**服务器是否已执行同款 ALTER 我没敢假设** —— 登记成 **#57**，做法是**先在 `information_schema` 上做一次只读核对**，**有差异才生成 ALTER**，低峰由用户执行、一次一处可独立回滚。因为报告是 2026-08-04 的快照，**线上可能比报告更新**，直接照报告 ALTER 会撞 `Duplicate column name`。后面还新发现两处互补的漂移：`seckill_message_retry` **生产有（79 行）但仓库没 DDL**（环境重建会凭空消失），`ams_permission` **生产多一列 `value`（全 NULL）但 DDL 没有**（没有代码在写，疑似历史残留）—— 两处我都**只登记、不变更**，先追根因再动手。"

---

## 九、关联文档

- **原始审计（主素材/证据源）**：[[数据库Schema漂移审计]] —— 39 表 × 32 实体 × 34 SQL 对比、§三高危清单、§七待执行 ALTER
- **个案修复记录（分工：它讲"怎么修"，本篇讲"怎么核"）**：[[问题解决--商品实体映射]] —— `@TableName`/`@TableField`/`pms_category`/测试数据
- **状态源与待办**：[[TODO文件]] **#57**（✅ 已关闭：只读核对完成，0 处需 ALTER）；已完成明细见 [[TODO已完成]] §18.1（#63）
- **同类问题（代码与线上不一致 · ES 侧）**：[[商品与秒杀扩容方案]] §1.6 二次严格复核 / §1.7 修复执行结果（**#63**）
- **同类方法论（"架构债的识别与处置"）**：[[问题解决--搜索双索引与降级分层]]
- **同族（"代码与线上不一致"这一类）**：[[问题解决--代码与线上不一致的静默失效]] —— ES 索引侧的同类问题；**共性是"不报错、不自愈、只在特定路径上暴露"**，所以两者都落在"**必须有定期核对机制**"
- **升级规划**：`docs/归档/企业级商品管理升级计划.md`；部署侧 ALTER 指引：`docs/归档/本次修改部署指南--2026-08-04.md`
- **项目上下文**：`docs/项目上下文文档.md` 8.4 节（实体映射遗留差异）
