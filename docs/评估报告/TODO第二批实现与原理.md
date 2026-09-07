# TODO 第二批实现与原理（面试深挖应对）

> **创建日期**: 2026-09-07
> **状态**: 🟡 进行中——第二批"正确性 + 面试/演示价值"共 8 项，已完成 **#8（AI 预算时区）**、**#23（DTO 校验）**、**#36 第一步（MQ requeue 修复）**；其余见正文各节状态。
> **用途**: 面试深挖应对 —— 每条都含「原理 → 本项目实现 → 代码实证 → 遇到的问题/疑惑 → 面试话术」
> **关联**: [[TODO文件]] 第二批（#33 / #8 / #36 / #13 / #29 / #23 / #5 / #2+#34）、[[TODO第一批实现与原理]]（第一批执行 + §九 实战经验写法参考）

---

## 〇、第二批全景（先记住这张表）

| 顺序 | 编号 | 事项 | 本质 | 状态（2026-09-07） |
|---|---|---|---|---|
| 1 | **#33** | 双索引数据不一致 | 用户可见 bug | ⏳ 待做（需方案取舍：快修 vs 重构统一索引） |
| 2 | **#8** | AI 预算按北京时间结算 | 唯一线上代码 bug | ✅ **已完成**（~10 行，TokenBudgetService 时区） |
| 3 | **#36** | DLX 死信 + requeue 修复 | MQ 可靠性 | 🟡 **第一步已完成**（requeue 限 3 次）；DLX 待做 |
| 4 | **#13** | Nacos 开启认证 | 安全 | ⏳ 待做（需维护窗口原子切换） |
| 5 | **#29** | 数据库定期备份 | 运维底线 | ⏳ 待做（需 ecs-user 配 cron） |
| 6 | **#23** | 漏触发接口补 @Validated | 校验静默失效 | ✅ **已完成**（含审计修正 + 全局异常处理器补全） |
| 7 | **#5** | Sentinel 能力补齐 | 面试价值 | ⏳ 待做（P0 规则可随时，P1 热点需改造） |
| 8 | **#2+#34** | AI 接口限流 + 并发闸门 | AI 承载 | ⏳ 待做（后置，改动最大） |

**执行顺序**：代码批（#8→#23→#36 第一步）→ 运维批（#29/#13）→ 设计批（#33/#5/#2+#34）。第一批已证明"先本地改 → 编译验证 → 维护窗口部署"的节奏有效。

---

## 一、#8 AI 预算按北京时间结算（面试重点：时区 bug 的经典案例）

### 1.1 问题本质（为什么"8:00 重置"）

`TokenBudgetService` 用 **JVM 默认时区**计算日期与 TTL：
```java
// 修复前 —— 容器 JVM 时区 = UTC
private String buildKey() { return KEY_PREFIX + LocalDate.now(); }
private long getSecondsUntilMidnight() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
    ...
}
```

**服务器容器时区是 UTC**（Docker 默认）→ `LocalDate.now()` 拿到的是 **UTC 日期**：
- UTC 的"一天"从 UTC 0 点开始 = **北京时间早上 8:00**
- 所以每日预算 key（`ai:daily_cost:<UTC日期>`）在北京时间 8:00 切换 → **预算 8:00 重置而非零点**

### 1.2 修复（~10 行，固定业务时区）

```java
// 修复后 —— 显式指定业务时区（北京时间），与服务器/容器时区解耦
private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
private String buildKey() { return KEY_PREFIX + LocalDate.now(ZONE); }
private long getSecondsUntilMidnight() {
    ZonedDateTime now = ZonedDateTime.now(ZONE);
    ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(ZONE);
    return Duration.between(now, midnight).getSeconds();
}
```

### 1.3 疑惑点与经验（面试深挖）

| 疑惑/问题 | 结论 |
|---|---|
| 为什么之前没发现？ | AI 模块 0 调用、预算从未打满 → 重置时间 bug 无感知（**没触发 = 不等于没 bug**） |
| `LocalDate.now()` vs `LocalDate.now(ZONE)` | 前者用 JVM 默认时区（容器 UTC 不可控）；后者显式指定业务时区（**"时间"必须带时区语义，不能依赖运行环境**） |
| TTL 用 `ZonedDateTime` 算为什么对？ | `atStartOfDay(ZONE)` 保证"次日零点"是北京时间零点；Redis TTL 是秒数，`Duration.between` 已换算 |
| 修复后旧 key 残留？ | 北京时间零点切 key 后，旧 UTC key 残留（TTL 已设会自灭）——影响可忽略 |
| 企业规范 | 所有"按天/按小时"的业务 key，日期计算**必须显式传 ZoneId**；更规范是服务统一 `spring.jackson.time-zone` + 数据库存 UTC/时间戳，展示层转本地 |

**面试话术**："我发现 AI 日预算在北京时间 8:00 重置而非零点——根因是容器 JVM 时区 UTC + `LocalDate.now()` 没带时区。修复是固定 `ZoneId.of("Asia/Shanghai")`，10 行。这类 bug 的共性是：**时间计算不能依赖运行环境时区**。"

---

## 二、#23 漏触发接口补 @Validated（面试重点：审计修正 + 全局异常补全）

### 2.1 原审计描述（2026-08-28）

全项目审计 39 个 @RequestBody 接口，5 个漏触发注解（规则在 DTO 但没触发 = 校验静默失效）：
`doRegister` / `DeliveryAddressController.addAddress/editAddress` / `AdminController.updateAdmin`

### 2.2 实施中发现的真相（⭐ 审计修正，面试讲这个比背结论强）

**代码复核发现原审计与事实部分不符**——5 个接口分属三类：

| 类 | 接口 | DTO 是否有规则 | 真相 | 处理 |
|---|---|---|---|---|
| A | `doRegister` | ✅ 有（UserRegistryDTO 满配 @NotNull/@Pattern） | **真 bug**：规则在但 @RequestBody 前漏 @Valid | ✅ 补 `@Valid` |
| B | `DeliveryAddressController.add/edit` | ❌ **无任何规则** | 不是"漏触发"，是"DTO 根本没规则" | 需先设计规则（本次做了） |
| C | `AdminController.updateAdmin` | ❌ **无规则**（import 被注释） | 同上 | 需先设计规则（本次做了） |

> 💡 **核心认知**：审计"校验失效"必须先分清 **"规则存在但没触发"（补触发注解）** vs **"规则本身不存在"（补规则）**——后者补触发注解是**空转**。原 TODO 把两类混为一谈，实施时靠逐 DTO 核查纠偏。

### 2.3 DTO 校验规则设计（业务视角：不是全必填！）

**核心理念**：按调用方语义分级——**新增类全量插入可必填；编辑类动态更新只能强制 id**。

#### ① DeliveryAddressAddDTO（新增，全量 insert）
- **必填**：contactName（@NotBlank+@Size 2-20）、provinceName/cityName/districtName（@NotBlank）、detailedAddress（@NotBlank+@Size≤100）
- **有则验格式**：mobilePhone/telephone（@Pattern）、provinceCode/cityCode/districtCode（@Pattern 6 位数字）
- **选填**：tag、streetCode/streetName、defaultAddress（Service 首个地址自动设默认）
- 新建 `DeliveryAddressRegExpression` 接口（项目 RegExpression 模式，正则参考秒杀同款）

#### ② DeliveryAddressEditDTO（编辑，动态更新）
- **只强制 id @NotNull**（由前端传，updateById 的更新目标）
- 其余字段"有则验格式"，**不 @NotBlank**——编辑走 MyBatis-Plus 动态 SQL，允许只改部分字段；照抄新增的必填会**误伤部分更新**

#### ③ AdminUpdateDTO（编辑）
- 只强制 id @NotNull；username/phone/email/长度有则验格式
- password/passwordAct：选填但**成对**（Service 判断）

#### ④ SeckillSpuAddDTO / SeckillSkuAddDTO（新增，直插 Mapper）
- 全必填：spuId/skuId @NotNull、金额 @DecimalMin("0.01")、库存 @Min(1)、seckillLimit 选填（代码兜底默认 1）

### 2.4 疑惑点（重点：DTO 校验表达不了的规则）

| 疑惑/问题 | 结论 |
|---|---|
| "手机/固话二选一"怎么验？ | **bean validation 无法表达 or 关系** → DTO 注解做不到。方案：① 两者都放宽为"有则验格式"；② **Service 层兜底"至少一个非空"**（本次采用）|
| 编辑接口为什么不能全必填？ | `editAddress` 走 `updateById`（动态 SQL 只更新非 null）→ 前端可能只改地址不碰电话 → 强制 @NotBlank 会拒绝合法部分更新 |
| `@Valid` vs `@Validated` 区别？ | ① 包不同：`jakarta.validation.Valid` vs `org.springframework.validation.annotation.Validated`；② `@Valid` 可嵌套校验（级联）；`@Validated` 支持分组校验；③ 对 @RequestBody 参数两者都触发；④ **GET 绑定的 DTO 对象参数**（如 addAdmin）需**参数级 @Validated** 或类级 @Validated（对 @RequestParam/@PathVariable 生效）|
| 校验失败抛什么异常？ | @RequestBody → `MethodArgumentNotValidException`；参数级/类级 → `ConstraintViolationException`；表单 → `BindException`。**三者都要有 handler**（见 §2.5）|

### 2.5 连带发现：全局异常处理器缺口（⭐ 本次最关键的补全）

**审计发现项目全局异常处理器只处理了 `BindException`（旧表单场景），`MethodArgumentNotValidException` 和 `ConstraintViolationException` 没有 handler** → 落到 `Throwable` → **校验失败返回 500 而非 400**！

**这意味着**：前面给 doRegister 等补的 @Valid，若不加这个 handler，校验失败会显示成"服务器错误"——**校验修复不完整**。

**补全**（`mall-common GlobalControllerExceptionHandler` 新增两个方法）：
```java
@ExceptionHandler(MethodArgumentNotValidException.class)  // @RequestBody + @Valid 失败
// 取 bindingResult.getFieldError().getDefaultMessage() → JsonResult.failed(BAD_REQUEST, msg)

@ExceptionHandler(ConstraintViolationException.class)     // 参数级/类级校验失败
// 取 constraintViolations.stream().findFirst().getMessage() → JsonResult.failed(BAD_REQUEST, msg)
```

### 2.6 治理清单（全项目 39 @RequestBody 全覆盖审计结果）

| 类 | 数量 | 明细 | 处理 |
|---|---|---|---|
| A 真 bug（有规则无触发） | 1 | doRegister | ✅ 补 @Valid |
| B 无触发 + DTO 无规则 | 3 | add/edit 地址、updateAdmin | ✅ 本次补规则 + 触发 |
| C 有触发 + DTO 无规则（注解空转） | 2 | addSeckillSpu/addSeckillSku | ✅ 本次补规则 |
| D 正常（触发+规则都有） | 31 | AI/product/order/sso 等 | ✅ 复核 |
| E String body 豁免 | 1 | wechatNotify | ✅ |

**延伸发现（记 TODO，未做）**：`AdminController.addAdmin` 是 **GET + DTO 绑定**（非 @RequestBody），其 `AdminAddDTO` 校验注解同样全被注释；本次已恢复其校验（@NotBlank/@Pattern）+ 类级/参数级 @Validated。另核实**前端 admin.js 是废弃残留**（无人引用，真接口在 sso.js），契约不一致不影响线上，已记录。

### 2.7 面试话术

**主线**："我做了一轮全项目校验审计，发现三类问题：① doRegister 的 DTO 有规则但漏 @Valid（真 bug）；② 4 个 DTO 是'根本没规则'——原审计把'漏触发'和'没规则'混为一谈，我逐 DTO 核查纠偏；③ **更隐蔽的是全局异常处理器没接 MethodArgumentNotValidException，导致校验失败返回 500 而不是 400**——补 @Valid 只是第一步，异常处理不补全，校验还是'假生效'。规则设计上我也踩了认知坑：DTO 校验表达不了'手机/固话二选一'这种 or 规则（需 Service 层兜底），编辑接口也不能照抄新增的全必填（会破坏动态更新的部分修改）。"

---

## 三、#36 MQ requeue 修复 + DLX（第一步完成，含全链路现状）

> 详细 MQ 全链路现状分析与阶段规划见本系列配套分析（TODO 第二批讨论记录）。本节记录**已实施的第一步**与原理。

### 3.1 问题本质

`OrderQueueConsumer` 失败处理是 `basicNack(deliveryTag, false, true)`——**requeue=true 无限重试**：
- 库存永久不足 / 商品下架 / 消息格式坏 → 每次消费都失败 → **无限 requeue 循环** → 消息永远卡在队列头部，阻塞后续消息 = **毒消息挂单**

### 3.2 修复设计（为什么用 x-death 而不是直接 requeue=false）

**方案对比**：
- ❌ 直接 `requeue=false`：一刀切丢弃——**瞬时故障**（Dubbo 抖动/DB 闪断）也会误丢，订单库存扣减丢失无感知
- ✅ **x-death 限次重试**：RabbitMQ 在每次 requeue 后自动在 header 附加 `x-death`（含 reason=requeued、count=次数）→ 消费前读 count，**前 3 次失败仍 requeue（瞬时故障有机会），第 3 次后 requeue=false（丢弃留痕）**

```java
private static final int MAX_REQUEUE = 3;

public void process(String messageJson, Channel channel,
        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
        @Header(name = "x-death", required = false) List<Map<String, Object>> xDeath) {
    int requeueCount = countRequeue(xDeath);   // 读 x-death 中 reason=requeued 的 count
    ...
    channel.basicNack(deliveryTag, false, requeueCount < MAX_REQUEUE);
}

private int countRequeue(List<Map<String, Object>> xDeath) {
    // 遍历 x-death：reason=="requeued" 的 count 取最大 = 已重试次数
}
```

### 3.3 疑惑点与原理（面试深挖）

| 疑惑/问题 | 结论 |
|---|---|
| x-death 是什么？ | RabbitMQ 在消息被 requeue/reject/dead-letter 后附加的**投递历史头**，每次 requeue 更新 count；可用于限次重试、死信分析 |
| 为什么不配 Spring retry？ | 订单模块**无 yml listener 配置**且自定义了 `rabbitListenerContainerFactory`（只 setMessageConverter，无 retry advice）——Spring retry 的 `RetryInterceptor` 不在链上，配了也不生效（认知坑：**自定义容器工厂会覆盖 Boot 默认的 retry 装配**）|
| manual ack + retry 能共存吗？ | 秒杀模块配了 manual + retry(max-attempts=3)，但 manual ack 语义下 retry advice 与手动确认交互**存疑（可能双重重试）**——待实测确认（TODO #36 记录）|
| 为什么订单不直接学秒杀配 retry？ | 秒杀有本地消息表（`seckill_message_retry` + `MessageRetryTask` 每 5s 重发）兜底；订单**没有发送确认、没有重试机制**——两条链路可靠性设计不一致（订单弱）|
| requeue 耗尽后消息去哪？ | 当前 requeue=false = **丢弃**（有 ERROR 日志留痕）；企业级应有 **DLX 死信队列**承接 → 记录原因/告警/人工补偿（TODO #36 剩余部分）|

### 3.4 面试话术

"订单库存扣减消费者原本是 `basicNack(requeue=true)`——毒消息会无限重试挂单。我没用一刀切的 requeue=false（会误丢瞬时故障），而是读 RabbitMQ 的 **x-death 头做限次重试**：前 3 次失败保留重试机会，之后丢弃并留 ERROR 日志。过程中发现订单模块的可靠性配置是空白的——无发送确认、无 retry（自定义容器工厂绕过了 Boot 默认装配）、无 DLX，与秒杀链路（本地消息表 + MessageRetryTask）形成鲜明对比。"

---

## 四、第二批剩余项速览（待实施，含对应方案文档）

| 编号 | 事项 | 方案文档 | 关键难点 |
|---|---|---|---|
| #33 | 双索引不一致 | 无（TODO 已给方向） | 方案取舍：快修（补 2 条）vs 重构（统一索引+向量字段差异）|
| #13 | Nacos 认证 | [[集群化与配置中心迁移方案]] §A0 | 11 服务+Seata+Dubbo 全配账号，原子切换 |
| #29 | 数据库备份 | 无（TODO 已给命令） | 需 ecs-user 配 cron |
| #5 | Sentinel 补齐 | [[Sentinel能力补充计划]] | P0 规则随时 / P1 热点参数改造 |
| #2+#34 | AI 限流+并发闸门 | 无（TODO 已给层次） | 并发闸门 Semaphore 设计，改动最大 |

---

## 五、第二批通用面试话术（贯穿主线）

**主线叙事**："第二批我按'收益/成本/独立性'排序做了代码批：#8 修了 AI 预算 8:00 重置的时区 bug（10 行）；#23 做了一轮校验审计——过程中修正了原审计'漏触发 vs 没规则'的混淆，补了 5 个 DTO 规则 + 类级/参数级 @Validated，还发现并补全了全局异常处理器对 MethodArgumentNotValidException 的缺失（否则校验失败会返回 500 而不是 400）；#36 把订单消费者的无限 requeue 改成 x-death 限次重试。三条线都踩了认知坑：时区不能依赖环境、DTO 校验有表达边界（or/跨字段）、自定义容器工厂会绕过 Spring retry。"

**被追问"为什么不等公司方案"时**：个人项目我是 owner，但每个决策对齐企业做法（DLX/发送确认/kid 轮换/审计先行），说明知道生产标准与当前取舍。

---

**维护提示**: 本文件随第二批逐项实施持续补充；完成一项更新头部状态并回填细节。与 [[TODO文件]] 保持一致（TODO 是状态源，本文件是"原理+疑惑+话术"深挖）。
