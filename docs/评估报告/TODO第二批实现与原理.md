# TODO 第二批实现与原理（面试深挖应对）

> **创建日期**: 2026-09-07
> **状态**: 🟡 第二批"正确性 + 面试/演示价值"——**代码批全部完成（2026-09-08）+ ✅ 已全量部署服务器（2026-09-08 中午维护窗口，11 模块新 jar + Flyway V6 + ES 索引清理；晚间追加 #5 P0 部署）**：#8（AI 预算时区）、#23（DTO 校验+全局异常补全）、#36（requeue 限次 + 订单 DLX）、#14（P0 三层 + order_type 治本 + 方案Y + P1 对账任务）、#33（双索引统一 A2：统一索引 + mall-search 只读降级层）、**#5（Sentinel P0：统一 Nacos 规则管理 + eager 修复 transport 懒加载，实测限流生效）**。部署明细见 [[第二批部署执行清单-2026-09-08]]、[[Sentinel部署执行清单-2026-09-08]] 与 [[TODO已完成]]。剩余待做：#13 / #29 / #2+#34、#14 余 P2、#5 余 P1/P2。
> **用途**: 面试深挖应对 —— 每条都含「原理 → 本项目实现 → 代码实证 → 遇到的问题/疑惑 → 面试话术」
> **关联**: [[TODO文件]] 第二批（#33 / #8 / #36 / #13 / #29 / #23 / #5 / #2+#34）、[[TODO第一批实现与原理]]（第一批执行 + §九 实战经验写法参考）、[[搜索双索引统一与一致性评估]]（#33 完整评估 + A1/A2 原文 + 复核证据）

---

## 〇、第二批全景（先记住这张表）

| 顺序 | 编号 | 事项 | 本质 | 状态（2026-09-08） |
|---|---|---|---|---|
| 1 | **#33** | 双索引数据不一致 | 架构债（用户可见 bug 已不成立，见 §五 复核） | ✅ **A2 落地 + 部署 + 服务器验证通过**（统一索引 + mall-search 只读降级层；AI 索引 19 条） |
| 2 | **#8** | AI 预算按北京时间结算 | 唯一线上代码 bug | ✅ **已完成 + 已部署**（~10 行，TokenBudgetService 时区） |
| 3 | **#36** | DLX 死信 + requeue 修复 | MQ 可靠性 | ✅ **已完成 + 已部署**（requeue 限 3 次 + 订单 DLX + OrderDlxConsumer） |
| 4 | **#13** | Nacos 开启认证 | 安全 | ⏳ 待做（需维护窗口原子切换） |
| 5 | **#14** | Redis 主从切换防数据 | 消费者可靠性 + Redis 一致性 | ✅ **P0+P1 已完成 + 已部署**（第3层落库失败不静默 + order_type 治本 + 方案Y + 对账任务）；P2 归第三批（#9） |
| 6 | **#29** | 数据库定期备份 | 运维底线 | ⏳ 待做（需 ecs-user 配 cron） |
| 7 | **#23** | 漏触发接口补 @Validated | 校验静默失效 | ✅ **已完成 + 已部署**（含审计修正 + 全局异常处理器补全） |
| 8 | **#5** | Sentinel 能力补齐 | 面试价值 | ✅ **P0 已完成 + 已部署**（2026-09-08 晚：统一 Nacos 管理 + eager 修复懒加载，实测 429 生效）；P1 热点/P2 集群待做（见 §六） |
| 9 | **#2+#34** | AI 接口限流 + 并发闸门 | AI 承载 | ⏳ 待做（后置，改动最大） |

**执行顺序**：代码批（#8→#23→#36→#14→#33 全部完成）✅ → **部署服务器（2026-09-08 已完成）** ✅ → **#5 P0（2026-09-08 晚完成并部署）** ✅ → 剩余待做 #13/#29（运维批）→ #2+#34（设计批）。第一批已证明"先本地改 → 编译验证 → 维护窗口部署"的节奏有效。

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

## 三、#36 MQ requeue 修复 + DLX（已完成，含实战问题）

> 详细 MQ 全链路现状分析与阶段规划见本系列配套分析（TODO 第二批讨论记录）。本节记录**已完成的两步**（requeue 修复 + 订单 DLX）与实战踩坑。

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

"订单库存扣减消费者原本是 `basicNack(requeue=true)`——毒消息会无限重试挂单。我没用一刀切的 requeue=false（会误丢瞬时故障），而是读 RabbitMQ 的 **x-death 头做限次重试**：前 3 次失败保留重试机会，之后 requeue=false 进死信。第二步我补了 **DLX**：order_queue 声明 dead-letter 指向死信交换机，新增 OrderDlxConsumer 记录死信原因 + 告警——形成『限次重试 → 死信留痕 → 人工补偿』的闭环。过程中发现订单模块的可靠性配置是空白的——无发送确认、无 retry（自定义容器工厂绕过了 Boot 默认装配），与秒杀链路（本地消息表 + MessageRetryTask）形成鲜明对比。"

### 3.5 DLX 实现（订单队列死信）

**设计**（Spring AMQP 声明式）：
```java
// OrderQueueConfig —— 业务队列声明死信参数
@Bean
public Queue orderQueue() {
    return QueueBuilder.durable(ORDER_QUEUE)
            .deadLetterExchange(ORDER_DLX_EX)   // order_ex_dlx
            .deadLetterRoutingKey(ORDER_DLX_RK)  // order_dlx_rk
            .build();
}
// + DLX 三件套：order_ex_dlx 交换机、order_queue_dlx 队列(durable)、绑定
```

**死信消费者** `OrderDlxConsumer`（新增）：
- 监听 `order_queue_dlx`，按**原始 String 收消息体**（不反序列化业务类型，兼容不同版本）
- 读 x-death 头提取死信原因 + 输出 `【MQ死信告警】` ERROR 日志（供人工补偿）
- ack 消费防死信堆积；未来可扩展：落库死信表 / 外部告警（TODO #30）

**死信链路闭环**：
```
OrderQueueConsumer 重试 3 次耗尽 → basicNack(requeue=false)
  → RabbitMQ reject → order_queue 声明了 DLX → order_ex_dlx
  → order_queue_dlx → OrderDlxConsumer 记录原因 + 告警
```

### 3.6 实战问题：启动报错 406 PRECONDITION_FAILED（⭐ 本地复现 + 修复）

**现象**：DLX 代码改完后本地启动 mall-order，报：
```
Caused by: ... PRECONDITION_FAILED - inequivalent arg 'x-dead-letter-exchange'
for queue 'order_queue' in vhost '/': received the value 'order_ex_dlx'
of type 'longstr' but current is none
→ Failed to start bean '...RabbitListenerEndpointRegistry' → Application run failed
```

**根因（关键认知）**：**RabbitMQ 队列参数在队列创建后不可变**。
- 本地 RabbitMQ 早已存在旧 `order_queue`（无死信参数，arguments 仅 x-queue-type）
- 新代码声明"同名的、带 x-dead-letter-exchange 的队列" → RabbitMQ 检测参数不匹配 → **406 PRECONDITION_FAILED**
- **Spring AMQP 声明不会更新已存在队列**——它只是发 queue.declare，由 Broker 校验一致性

**修复**：删除旧队列（0 积压零风险）→ 重启应用 → Spring 自动重建带 DLX 的队列。
```bash
# 管理 API 删除（本地 guest/guest）
curl -u guest:guest -X DELETE http://127.0.0.1:15672/api/queues/%2F/order_queue
```

**经验教训（面试可讲）**：
1. **MQ 队列声明是一次性的**——改队列参数（DLX/TTL/durable）必须删队列重建，不像代码可热更新
2. **部署前必查积压**：删队列会丢消息——本项目 order_queue 演示期 0 积压零风险；生产必须先确认积压/先迁移
3. 同理适用于：交换机/绑定参数变更、TTL 调整——**Broker 端资源不可变**是 RabbitMQ 设计约束
4. 本问题在我们自己的代码注释里已预警（"需先删旧队列"），但**本地启动时还是踩了**——预警写进注释 ≠ 执行时记得，最好部署清单化

---

## 四、#14 Redis 主从切换防数据（⭐ 本次重头：P0 三层 + order_type 治本 + 方案Y + P1 对账）

> **背景**：Redis 主从复制异步 → 主挂瞬间丢最后几笔写（库存 DECR/购买标记/幂等锁）。代码层无法 100% 消灭（本质），目标是让丢失**无害化**。本批完成 P0 全部三层 + P1 对账，P2 配置层归第三批（随 #9）。

### 4.1 模型认知（为什么 P0 第一版方案放弃——⭐ 先讲清楚再讲实现）

**秒杀链路**：`Redis 预扣（DECR 闸门，最多放行 N 单）→ MQ 异步 DB 条件扣减（seckill_stock>=qty，逐单）`。

**曾计划 P0"付款前查 DB 库存"**——推演发现**语义缺陷**：
- **缺陷1（误拦已成交）**：库存一致时（Redis=DB=N），放行 N 单 MQ 全部扣成功，**最后一件成交后 DB=0 是正常结果**，其用户付款时查剩余库存会被**误拦**。查"剩余库存"无法区分"本单货已被 MQ 扣掉（正常）"与"本单货没扣上（异常）"。
- **缺陷2（防不了真问题）**：要防的"Redis 多放导致超卖"，DB 条件扣减已兜底（rows==0 即不成交）；查剩余库存既不拦"该拦的"，又误拦"不该拦的"。

> **结论**：秒杀成交资格在 **Redis 预扣时已确定**，DB 只是记账（闸门/账本模型）。"付款前查剩余库存"不可正确实现 → **改为"查本单是否成交"**（方案Y）。

### 4.2 P0 第3层：SeckillQueueConsumer 落库失败不静默 ✅

**原问题**：`SeckillQueueConsumer` 库存不足时 `basicAck` **静默丢弃**——用户可能已付款但 success 记录缺失 = 订单悬挂无痕。

**修复**（三兜底，与 #36 同款 x-death 方案）：
1. **失败留痕**：rows==0 时 `log.error【秒杀落库留痕】`（含 skuId/orderSn/quantity/requeue 次数）
2. **已付款告警**：新增 `IOmsOrderService.getOrderStateBySn` Dubbo 查询订单状态，若 `state==3`（已支付）→ `【秒杀落库告警】⚠️ 需人工介入`；否则 warn 可后续重试
3. **限次重试**：读 x-death 头，`basicNack(requeue=false, requeueCount < MAX_REQUEUE=3)`——前 3 次瞬时故障有重试机会，达上限丢弃留痕

```java
if (rows == 0) {
    log.error("【秒杀落库留痕】DB库存扣减失败, skuId={}, 订单号={}, quantity={}, requeue {}/{}", ...);
    Integer state = dubboOrderService.getOrderStateBySn(success.getOrderSn());
    if (state != null && state == 3) { log.error("【秒杀落库告警】⚠️ 已付款订单落库失败，需人工介入！..."); }
    channel.basicNack(deliveryTag, false, requeueCount < MAX_REQUEUE);
    return;
}
```

### 4.3 order_type 标识（治本前置，⭐ 防伪造的关键）✅

**问题根源**：`markSeckillPurchased` / `clearSeckillOrdered`（支付/取消时写/清 reseckill 购买标记）**无条件执行**——普通订单也被误当秒杀单写 reseckill 标记（普通单不该影响秒杀限购）。

**修复**：`oms_order` 加 `order_type` 列（**Flyway V6**，历史数据默认 0=普通）：
- 秒杀入口 `SeckillServiceImpl` 置 `orderType=1`
- 普通入口 `OmsOrderController.addOrder` **强制 `orderType=0`**（防前端伪造秒杀单）
- `markSeckillPurchased` / `clearSeckillOrdered` **仅秒杀单执行**（`isSeckillOrder(order)` 判断）

> 💡 这也是方案Y的**前置**——只有明确标识秒杀单，才能在支付前对秒杀单校验成交状态。

### 4.4 方案Y：支付前校验"本单成交状态"（⭐ 替代放弃的"查库存"）✅

**新增 Dubbo 链路**：order 支付前查 `success` 表**本单是否已落库**（非查剩余库存）：

```java
// seckill 端新增对外服务
public interface IForOrderSeckillRecordService {
    boolean isSeckillSuccessRecorded(String orderSn);  // 查 success 表 order_sn 是否有记录
}

// order 端 payOrder 3.5 步：秒杀单(orderType=1)支付前校验
if (isSeckillOrder(order)) {
    validateSeckillRecordBeforePay(order);  // 未落库则拦截支付，Dubbo 异常保守放行
}
```

**为什么查"本单"而非"剩余库存"**：
- 成交单必有 success 记录 → **不误拦**已成交的最后一件（缺陷1 解决）
- 未落库单（Redis 放行但 DB 扣减失败 rows==0）→ 拦截支付（缺陷2 解决）

### 4.5 本地实测验证（⭐ 全链路证据）

| 验证项 | 结果 |
|---|---|
| iPad Pro 秒杀+支付全链路 | 订单 `order_type=1`、`state=3`(已支付)、`success` 有记录、Redis `reseckill:13:1` 标记已加 → **方案Y + reseckill 都正常** |
| 普通购买守卫生效 | order_type 强制 0，普通单不写 reseckill 标记 |

### 4.6 P1 对账任务：Redis vs DB 定时比对修正漂移 ✅

**问题**：Redis（闸门）与 DB（账本）异步，任何一侧失败都会漂移——Redis 偏大（扣少了→可能超卖）/ Redis 偏小（多放行→有货不能买）。

**设计（业界分层，调研佐证）**：对账**不能**只放凌晨、也**不能**只在高峰期——社区主流是**分层**：

| 层 | 调度 | 范围 | 阈值 |
|----|------|------|------|
| **A 运行期轻量纠偏** | `@Scheduled(fixedDelay=5min)` | 只处理已预热 sku（Redis 有 key） | \|diff\|≥2 直接修；\|diff\|=1 连续 3 次同向才修（容错瞬时差） |
| **B 凌晨全量校准** | `@Scheduled(cron="0 30 3 * * ?")` | 全量所有 sku + 补建缺失 key | \|diff\|≥1 即修（低峰无并发） |

**核心逻辑**：以 DB `seckill_sku.seckill_stock` 为**唯一权威基准**，修正 Redis `mall:seckill:sku:stock:{skuId}`：
- `redisValue > dbValue`（扣少了）→ 拉回 DB（防超卖）
- `redisValue < dbValue`（多放行）→ 回补到 DB（防有货不能买）
- **容错瞬时差**：Redis 刚 DECR、MQ 未及时扣 → 合法的时间差，运行期 ±1 不即修，凌晨全量才彻底对齐

> **关键权衡（诚实声明）**：运行期回补在 MQ 积压时可能短暂让 Redis 大于真实可成交数，但最终仍被 DB `seckill_stock>=qty` 条件扣减拦下（rows==0 不成交），**不会真超卖**——只是从"拦在 Redis"退化到"拦在 DB"（DB 条件扣减仍是最终防线）。

**实现文件**：`SeckillReconcileTask`（两个 @Scheduled，复用 `reconcileAll(online)`）；配置在 `application-test.yml`（`seckill.reconcile.*`，全默认值）。

### 4.7 P1 对账本地实测（⭐ 端到端证明）

| 验证项 | 结果 |
|---|---|
| 制造临时漂移 sku35 100→102（\|diff\|=2） | 运行期对账**自动修回 100**（证明 @Scheduled 加载并调度） |
| 全量比对 12 sku | **0 个不一致**（对一致数据零误改、对漂移正确修复） |
| 既有漂移样本 sku26（149/DB=148） | 已按 DB 修正为 148 |

### 4.8 疑惑点与原理（面试深挖）

| 疑惑/问题 | 结论 |
|---|---|
| 为什么付款前查"剩余库存"是错的？ | 无法区分"本单货被 MQ 扣掉(正常)" vs "本单货没扣上(异常)"——只剩"是否已成交"可判 |
| 为什么必须有 order_type？ | 无标识时普通订单也会被当秒杀单写 reseckill 标记，污染限购；且方案Y需要识别秒杀单 |
| 对账为什么分运行期+凌晨两层？ | 只放凌晨 → 白天漂移持续伤用户（看到有货买不到/超卖）；只放高峰期 → 全量比对有并发干扰。运行期轻量纠偏 + 凌晨彻底校准 |
| 对账"以 DB 为准"为什么不超卖？ | 即使回补让 Redis 短暂偏大，DB 条件扣减 `seckill_stock>=qty` 仍是最终防线（rows==0 不成交） |
| 对账会误伤合法预扣吗？ | 运行期 \|diff\|==1 连续 3 次才修，规避 Redis 刚 DECR/MQ 未扣的瞬时差 |
| 多实例并发对账？ | 单机无此问题；秒杀集群化后需分布式锁（复用 #4 定时任务锁思路）|

### 4.9 面试话术

**主线**："#14 我处理 Redis 与 DB 库存一致性。首先我推翻了自己第一版的 P0 方案——原打算付款前查 DB 剩余库存，但推演发现它无法区分『本单货已被 MQ 扣掉』和『本单货没扣上』，会误拦已成交的最后一件。正确做法是查『本单是否已写入 success 表』（方案Y）——成交单必有记录，不误拦；未落库单拦截支付，防付钱没货。同时我补了 order_type 列（Flyway V6）标识秒杀/普通单，让 markSeckillPurchased 只在秒杀单执行、普通单强制 0 防伪造。第3层我把 SeckillQueueConsumer 的静默丢弃改成三兜底：失败留痕 + 已付款告警 + x-death 限次重试。最后是 P1 对账任务——业界分两层：运行期每 5 分钟轻量纠偏（容错瞬时差），凌晨全量校准（以 DB 为唯一基准彻底对齐）。我调研确认这是社区主流设计，不是拍脑袋。所有 P0 + P1 已本地端到端实测通过（对账确实把临时漂移修回 DB 值，12 个 sku 零误改）。"

---

## 五、#33 搜索双索引统一（⭐ 三人讨论 + 方案 A2 认定，2026-09-08）

### 5.1 问题重述与复核（先打碎 TODO 原记载）

TODO #33 原记载（2026-09-02）："两个搜索各维护一个 ES 索引 + 各一套同步链路——AI 索引 20 > search 索引 18，**普通搜索查不到 2 条新商品 = 用户可见 bug**"。

**复核修正（2026-09-08，代码 + 服务器双重实测）——三条铁证推翻原判断**：

| 证据 | 内容 |
|------|------|
| ① 前端早已全走 AI 搜索 | `ProductList.vue`/顶部搜索框/建议/相关推荐全部调 `/ai/**`（mall-ai）；`search.js` 无一个 `/search/**` 调用；前端无 searchHttp axios 实例 |
| ② mall-search 零真实流量 | nginx 24h `/api/search` = **0 条**（同窗口全是扫描器 `/api/phpinfo`）；gateway 全量日志含 `/search` 仅 1 条 = 十六进制注入探测（扫描器噪音）；`/search/query?keyword=0xe6...` 非正常浏览器行为 |
| ③ mall-search 是孤立遗留 | 反向依赖零（仅根 pom 列为 module）、跨模块 import 零、**不提供任何 Dubbo 服务**（`ISearchService` 是普通 Spring 接口）、`SearchRemoteServiceImpl` 标 `@Deprecated` |

**真实遗留问题（比 TODO 记载更深）**：
- **双索引双链路冗余**：product 变更只通知 mall-ai（`ISpuSyncService` 唯一实现），mall-search 从不被通知 → index2 缺 11/17
- **同步模型缺陷**：只 upsert 不 delete + 删除/下架/审核不触发同步 + 写入不过滤业务状态 → **已下架商品 id=18 残留在 AI 索引**（DB 19 条上架，AI 索引却 20 条）——这才是当前真实存在的"搜索正确性 bug"

### 5.2 方案演进与讨论过程（⭐ 面试可讲的全过程）

**讨论参与者**：用户（项目 owner）+ 用户同学（旁观/终票）+ AI（同级票）。**规则**：三票各投，独立判断。

| 轮次 | 讨论内容 | 结论/票 |
|------|---------|--------|
| 第一轮 | 用户问"为什么除去 search 模块？搜索降级的时候不使用吗？" | 引出核心认知：**mall-ai 内部 `esKeywordSearch` 降级 ≠ 进程级降级**——降级代码和 mall-ai 同进程，进程 OOM/崩溃时降级也不存在；只有独立进程（mall-search）才是真降级 |
| 第二轮 | AI 全面复核 mall-search 家底 + A1 vs A2 比较 | 发现反直觉事实：**A2"保留"改动反而比 A1"退役"大**（重写 SearchServiceImpl vs 纯删除）；当时 AI 倾向 A1（僵尸清理论） |
| 第三轮 | 用户三连问：①AI 有能力承担搜索吗？②合并进 mall-ai 违背微服务思想吗？③未来升 Spring AI 有影响吗？ | ①mall-ai 检索能力齐全但缺"浏览式列表"形态（search() 固定 AI 重排 Top-5）；②把搜索并入 mall-ai 违背**单一职责/故障隔离/独立扩展**（搜索高频稳定 vs AI 慢贵外依赖）；③Spring AI 只改 mall-ai 内部 → **搜索独立则升级零波及，搜索并入则跟着搬家** |
| 第四轮 | 投票 | 用户投 A2；AI 投 A2（理由见 §5.3）；**同学终票 → A2** → 最终认定 |

**为什么投票从"倾向 A1"翻转为"A2"（AI 视角的自我修正，面试加分点）**：
1. 二轮评估把"零流量/改动量/省资源"权重放太高——但那是在回答"要不要养僵尸"
2. 三轮追问揭示**决策的本质**：不是"养不养僵尸"，而是"**搜索职责该不该独立**"。mall-search 不是没用的服务，而是"基础搜索能力唯一现成的独立载体"
3. 搜索（高频/稳定/毫秒级/不依赖外部）与 AI（慢/贵/绑 LLM/预算上限）在故障域、成本模型、扩展性上完全不同——微服务化恰恰要求用服务边界隔离这种差异
4. **面试叙事自洽是硬约束**（面试文档 Q7 已定稿"普通搜索是召回层/地基，AI 是重排层/增强，分层不替代"）→ 代码架构必须长成文档说的样子
5. A2 代价可控：mall-search 只读同一索引、一段 multi_match、字段就几个；内存 available 3.8G 承受得起 768M

### 5.3 最终认定：方案 A2（含 A1 原文去向说明）

**认定结论**：
```
统一数据源（cool_shark_mall_ai 单一索引 + 单写链路）
    │
    ├── mall-ai（独立进程）── 语义搜索：/ai/search + suggest + related + AI 重排
    │
    └── mall-search（独立进程，改造为只读降级层）── 普通搜索：GET /search
         纯 ES multi_match 召回（毫秒级、零 AI、进程级故障隔离）
    │
Web 前端：正常走 /ai/search → AI 失败/超时 → fallback 调 /search
```

**A1（彻底退役）原文保留**：完整 A1 方案/步骤/优缺点保留在 [[搜索双索引统一与一致性评估]]（§三 方案 A1），作为**讨论过程证明**——面试被问"你考虑过退役吗"时能展示完整决策树，而非只讲结论。

**为什么不是"删 search"而是"改 search"（回答用户问题）**：
- mall-search **不需要删，需要"换血"**——它的问题不是"存在"，而是"自建了第二份会漂移的索引"
- 改造后它从"自建 index2 的写入者"变成"只读统一索引的查询者" → **无写链路 = 无漂移**，同步链路天然收敛为 product→Dubbo→mall-ai 一条
- 保留独立进程 = 保留"AI 挂了还能搜"的进程级故障隔离（mall-ai 内部降级覆盖不了进程本身崩溃）

### 5.4 A2 涉及的 search 模块改造点（✅ 需要改，且是"重写查询层"而非"删服务"）

**mall-search 模块必改清单（实施时对照）**：

| # | 改动 | 文件 | 说明 |
|---|------|------|------|
| 1 | **重写 `SearchServiceImpl`** 为原生 `ElasticsearchClient` 普通召回 | `SearchServiceImpl.java` | multi_match：name/title/description/categoryName/brandName/tags（与 mall-ai `esKeywordSearch` 同字段），**不加 semanticText boost、不做 AI 重排**；分页返回 `SearchResultVO`（mall-pojo `ai.vo`） |
| 2 | **停用自建索引写入** | `SpuForElasticRepository`/`loadSpuByPage`/`/search/sync` | 不再 saveAll、不再全量同步；旧代码注释"已废弃：降级只读模式"或 git 历史保留 |
| 3 | **Controller 收敛** | `SearchController.java` | 保留 `GET /search`（新结构）；移除/停用 `/search/sync`、`/search/byLogstash` 死代码 |
| 4 | **安全配置微调** | `ResourceWebSecurityConfiguration.java` | `/search` 保持 authenticated（与 `/ai/search` 一致，前端 fallback 带 token）；`/search/sync` permitAll 移除 |
| 5 | 死代码清理（可选） | `SpuEntity`/`SpuEntityRepository`/`SearchRemoteServiceImpl`/`TestSearch` | 归档或删除 |

**mall-search-service 侧**：`ISearchService` 接口方法调整（`search` 返回类型改 `SearchResultVO`；`loadSpuByPage` 系列删除）。✅ 已确认 `SearchResultVO` 在 `mall-pojo/com/cooxiao/mall/pojo/ai/vo/`，mall-search 已依赖 mall-pojo，**无跨模块依赖问题**。

**配套改动（不只是 search 模块）**：mall-ai `VectorSyncServiceImpl`（deleteSpu + syncSpu 状态校验）、mall-product `SpuServiceImpl`（deleteById/updatePublishedById/passCheck 补触发）、前端 `ProductList.vue`（fallback ~20 行）、gateway 三环境路由保留、服务器索引清理（删空 index + 重建 AI 索引剔除 18 + 删 index2）。

> 📄 完整评估（含 A1/A2 全文、复核证据、执行清单、风险回滚、双版本面试话术）见 [[搜索双索引统一与一致性评估]]。

### 5.4.5 ✅ 实施记录（2026-09-08 本地代码完成 + 编译/自检通过，待部署）

**改动文件清单**：

| 模块 | 文件 | 改动 |
|------|------|------|
| mall-ai | `VectorSyncServiceImpl.java` | **同步模型补全**：① `syncSpu` 增加业务状态校验——仅 `checked=1 && published=1 && deleted=0` 写入，否则 `deleteSpu`；② 查询异常区分"业务 NOT_FOUND（可删）vs 基础设施故障（Dubbo 超时，保守放行不误删）"；③ 新增 `deleteSpu`（幂等）；④ `syncAll` 末尾 `cleanupInvalidDocs` 全量清洗残留（含 DB 无有效商品时仅清空逻辑） |
| mall-product | `SpuServiceImpl.java` | ① `passCheck`（审核通过）补 `notifySpuSync`；② `deleteById`（逻辑删）补触发；③ `updatePublishedById`（上架/下架）补触发；统一走新增 `notifySpuSync`（try-catch 不阻断主流程，provider 端按 DB 最新状态决策 upsert/delete） |
| mall-search | `ISearchService.java`（service） | 接口精简为 `search(keyword,page,pageSize) → SearchResultVO`；删除 `searchByLogStash`/`loadSpuByPage*` |
| mall-search | `SearchServiceImpl.java`（webapi） | **重写为只读统一索引普通召回**：原生 `ElasticsearchClient` 查 `cool_shark_mall_ai`，multi_match（name^5/title^4/description^2/brandName/categoryName/tags，不加 semanticText、不做 AI 重排），分页归一化（page<1→1、pageSize 默认 10 上限 100、keyword 空白返回空），异常兜底返回空（降级通道不 500），返回与 AI 同构的 `SearchResultVO` |
| mall-search | `SearchController.java` | 只保留 `GET /search`；移除 `/search/sync`、`/search/byLogstash` |
| mall-search | `ResourceWebSecurityConfiguration.java` | 移除 `/search/sync` permitAll；`/search` 保持 authenticated（与 `/ai/search` 一致） |
| mall-search | pom.xml | 移除 `mall-product-service` 依赖（不再 Dubbo 拉商品建索引）|
| mall-search | application-prod/test.yml | 补 `custom.file-upload.resource-host`（普通搜索返回图片需拼完整 URL）|
| deploy/docker | docker-compose.yml | mall-search 服务补 `CUSTOM_FILE_UPLOAD_RESOURCE_HOST` env |
| 前端 | `search.js` | 新增 `searchProductsFallback()`（GET /search）；`searchProducts` 支持 silent |
| 前端 | `request.js` | 拦截器支持 `config.silent`（降级请求不弹全局 toast）；错误对象补 `errType`（timeout/network/http）+ `status` 元数据（供"慢≠挂"精准判定）|
| 前端 | `ProductList.vue` | AI 搜索失败 → **仅确定性故障降级**（5xx/网络错误；4xx 不静默降级）+ UI 区分提示（"AI 响应超时"/"AI 搜索暂不可用"）；`isFallbackSearch`/`fallbackReasonText` 状态；缓存含降级标记；**请求序号竞态守卫**（防慢请求晚到覆盖新结果）|
| mall-search | `SearchRemoteServiceImpl`/`SpuEntityRepository`/`SpuForElasticRepository`/`TestSearch` | **git rm 删除**（死代码，历史可恢复）|

**验证情况**：
- ✅ `mvn test-compile`：mall-search / mall-ai / mall-product 全通过（含删除文件后的 test 目录）
- ✅ 前端：`node --check` search.js/request.js + `@vue/compiler-sfc` 编译 ProductList.vue 通过（沙箱限制 vite build 的 esbuild spawn，改走 SFC 直检）
- ✅ 降级判定逻辑单测：6/6 通过（真挂 503/500/连接拒绝 → 降级；慢=30s 超时 → 降级带提示；400/401 → 不静默降级）
- ✅ 自检 2 轮（详见下方"边界与兜底复查"）
- ✅ **本地端到端验证（2026-09-08）**：AI 主链路（意图提取→查询扩展→ES 召回→AI 重排）全通（修复 mall-ai 既有问题后，见下方"本地测试暴露的既有问题"）；**停 mall-ai 后前端秒级降级到普通搜索并返回商品**（#33 核心验收通过）

**本地测试暴露的既有问题（与 #33 无关但阻塞验证，已一并修复）**：

| # | 问题 | 根因 | 修复 |
|---|------|------|------|
| ① | DeepSeek 401 | **IDEA Run Configuration 残留旧 AI_API_KEY**（优先级最高覆盖一切） | 删除 IDE 旧 key → 回落系统 `DEEPSEEK_API_KEY`；yml 改嵌套占位符 `${AI_API_KEY:${DEEPSEEK_API_KEY:...}}` 双兼容 |
| ② | AI 重排 Read timed out | `deepseek-v4-flash` 是 reasoning 模型（含 reasoning_content）生成 15-30s，`cooxiao.ai.timeout` 原 15s 不够 | timeout → 60s（application.yml，prod 的 cooxiao.ai 段未覆盖自动继承） |
| ③ | AI 重排 `aiJson is null` | 重排走普通 `chat()` 无 `response_format: json_object`，reasoning 模型自由发挥返回非纯 JSON | 改 `chatWithModel(jsonMode=true)` + markdown 清理（与意图提取同款） |
| ④ | 关 AI 后前端弹"服务器错误" | suggest/related 辅助请求失败未 silent，拦截器先弹全局 toast | 两请求补 `silent: true`（调用方已有兜底）|

**边界与兜底复查记录（2 轮，实施后逐项核查）**：

| # | 边界/风险点 | 结论/处理 |
|---|------------|----------|
| 1 | `syncSpu` 查询异常 = 一律 delete？ | ❌ 修正：**Dubbo 超时等基础设施故障会误删正常商品** → 遍历 cause 链识别 `CoolSharkServiceException(NOT_FOUND)` 才删，网络故障保守放行 |
| 2 | `syncAll` DB 无有效商品时 | 原提前 return 会跳过清理 → 改为"跳过 upsert 但执行 cleanup（清空 ES）" |
| 3 | `cleanupInvalidDocs` 单条坏 id | 单独 try-catch 跳过（不中断整个清洗循环）|
| 4 | keyword 空白 | 返回空结果，**不执行 matchAll**（普通搜索必须有词，无词浏览走前端分类接口）|
| 5 | page/pageSize 越界 | page<1→1；pageSize<1→10、>100→100 |
| 6 | 命中文档缺 spuId | 用 `_id`（=spuId）兜底；非数字则丢弃并 warn |
| 7 | pictures 解析失败 | 原样返回 + warn（不抛 500）|
| 8 | ES 查询异常（mall-search）| 返回空结果 + error 日志（降级通道有响应优于 500）|
| 9 | AI 搜索失败前端先弹错误 toast？ | ❌ 修正：AI 请求改 `silent:true`——**降级是预期路径不该报错**；仅 AI+fallback 都失败才由外层 catch 提示 |
| 10 | fallback 请求自身失败 | silent 不重复弹窗，抛给外层统一提示 |
| 11 | 401（未登录）| 两通道都 authenticated，行为一致；401 仍清 token 跳登录（拦截器既有逻辑）|
| 12 | 前端缓存降级标记 | `searchCache` 存 `isFallback`，恢复时还原 UI 提示 |
| 13 | 分类浏览误触 fallback？ | fallback 只在 `searchKeyword` 非空分支内，分类浏览不触发 |
| 14 | 图片 URL | 普通搜索拼 `resource-host` 完整 URL，前端 `getFirstImage` http 开头直用 ✓ 与 AI 通道一致 |
| 15 | mall-pojo 孤儿实体 `SpuEntity`/`SpuForElastic` | 零引用、主类不扫 pojo 包、无 repository → **无自动建索引风险**，保留不动（避免动公共模块）|
| 16 | ⭐ **"AI 慢" 被误判为 "AI 挂"（用户提出的关键边界）** | ❌ 原实现 catch 所有错误一律降级 → 修正为**仅"确定性故障信号"降级**：HTTP 5xx（503=不可达/500=后端超时失败）、网络错误（连接拒绝=真挂）→ 降级；axios 已按 30s 长超时等待（>后端 10s LLM 超时），**正常慢响应（5-15s）根本不会触发 catch**；能撑到 30s 超时说明确实异常 → 降级并提示"AI 响应超时"而非静默切换；4xx（400/401）不静默降级，交外层统一提示防掩盖真问题 |
| 17 | 错误类型信息在拦截器丢失 | ❌ 原 `new Error(msg)` 丢类型 → request.js 补 `errType`（timeout/network/http）+ `status` 元数据，调用方据此精准判定 |
| 18 | AI 慢请求 + fallback 快请求竞态 | ❌ 快速翻页/切词时旧慢请求可能晚到覆盖新结果 → `fetchProductList` 加**请求序号守卫**（fetchSeq），过期请求不写回、不报错、不清 loading |
| 19 | ⭐ **本地测试发现：DeepSeek 401 根因 = IDEA Run Configuration 里残留旧 AI_API_KEY**（2026-09-08） | 完整排查链：前端搜"学生党手机"空 → mall-ai 日志 401 → 加临时脱敏日志定位实际 key = `sk-8f73a...`（**既非 .env 的 sk-de931f、也非系统 DEEPSEEK_API_KEY 的 sk-80a，是第三个 key**）→ 真凶 = **IDEA Run Configuration Environment variables 里以前手动填的旧 AI_API_KEY**（IDE 环境变量优先级最高，覆盖系统变量和 .env，重启 IDEA 也不变）。**修复**：删除 IDE 里的旧 AI_API_KEY → 回落系统 `DEEPSEEK_API_KEY`(sk-80a) 生效。**配套改动**：① application.yml/test.yml api-key 改嵌套占位符 `${AI_API_KEY:${DEEPSEEK_API_KEY:sk-placeholder}}`（生产读 AI_API_KEY、本地读 DEEPSEEK_API_KEY 双兼容）；② 移除临时诊断日志。**经验**：① 环境变量优先级：**IDE Run Configuration > 系统/用户变量 > spring.config.import(.env) > yml 占位符默认值**——旧 key 残留最难排查，优先检查 IDE 配置；② 排查顺序：先 curl 验证 key+model（deepseek-v4-flash 200、deepseek-chat 400）排除 key，再加临时日志看运行时实际 key，别猜 |
| 20 | ~~AI 重排 Read timed out：timeout 太短~~（2026-09-08，**已被 #21 取代**） | 早期表象是 18s 超时 → 曾调 `cooxiao.ai.timeout 15s→60s`。但后续深挖发现**超时只是表象，真因是 reasoning 模型过度思考（见 #21）**——本条保留作为"排查过程中的中间结论"教训：**表象（超时）≠ 根因（模型行为），调超时只是延缓症状** |
| 21 | ⭐⭐ **AI 重排偶发降级/超时真因：reasoning 模型"思考到预算耗尽才输出"→ content 截断/挤空**（2026-09-08 完整排查，最值得讲） | **完整因果链**（全部实测）：① `deepseek-v4-flash` 是 reasoning 模型，响应 = `reasoning_content`(思考) + `content`(答案)；② 它对重排/意图提取这类 JSON 任务**过度思考**，且思考量**随 max_tokens 水涨船高**（实测：max_tokens=1000→想满1000；=4000→想满4000；=2000→想~1630）；③ 思考占满预算 → `content` 为空或被截断（日志铁证 `reasoning_tokens=4000=max_tokens`）→ `JSON.parseObject` 得 null/截断报错 → 降级；④ 前端 30s 超时 = "AI 响应超时"提示。**尝试过的弯路**：a. timeout 15→60s（只缓症状）b. max_tokens 2000→4000（**更糟**——给它更多预算它想更久）c. 提示词"不要思考" + max_tokens 3000（reasoning 收敛到几百，但**仍偶发截断**，content 被 cut 在字符串中间）。**正解 = 模型分工**：JSON 结构化任务（重排/意图提取/查询扩展）用**非推理模型 `deepseek-chat`**——实测 0.5-0.8s 稳定返回完整 JSON、无 reasoning、永不截断；真正需要深度推理的 **SSE 流式对话保留 `deepseek-v4-flash`**。**改动**：① application.yml `chat-model: deepseek-v4-flash → deepseek-chat` ② ChatServiceImpl 意图提取硬编码 `v4-flash → deepseek-chat`（SSE 对话 :353 保持 v4-flash）③ max-tokens 3000。**经验**：① reasoning 模型适合"深度推理问答"，**不适合"快+稳+结构化输出"**——选模型先想任务类型；② reasoning 量随预算膨胀是设计特性，不能靠加预算解决；③ 排查"偶发失败"要连测多次看统计，单次成功会误导；④ 直连 API 测（绕过应用）能快速二分"模型问题 vs 应用问题" |

**⚠️ 部署待办（服务器维护窗口，与第二批部署一起）**：
1. 重建 AI 索引：`/ai/syncAll`（新逻辑自动剔除已下架 18）→ 验证 19 条
2. 删除空索引 `cool_shark_mall_index`、旧索引 `cool_shark_mall_index2`
3. 部署 mall-search/mall-ai/mall-product 新 jar（含 mall-search 的 resource-host env）
4. 降级演练：`docker stop csmall-ai` → 前端搜索 fallback 出结果 → start 恢复

### 5.5 面试话术（#33 完整故事线）

**主线**："我发现项目有两个搜索——mall-search 和 mall-ai 各维护一个 ES 索引、各一套同步链路，数据漂移（实测 AI 20 条 vs search 18 条，且都残留一条已下架商品）。但进一步复核发现三个反直觉事实：①Web 前端早已 100% 走 AI 搜索，mall-search 零真实流量（nginx 24h 0 请求，只有扫描器噪音）；②mall-search 是孤立遗留——零反向依赖、零跨模块 import、不提供任何 Dubbo 服务；③真正的问题是同步模型：只 upsert 不 delete、删除/下架/审核不触发同步、写入不过滤业务状态，所以已下架商品残留在索引能被搜到。

我给了两个方案：A1 彻底退役（减法，省一个容器）；A2 保留为**只读普通搜索降级层**（统一到单一索引，mall-search 不再自建数据，只做纯 ES 召回）。我和同学、AI 三方讨论后选 A2，理由三条：第一，搜索（高频稳定毫秒级）和 AI（慢、贵、绑 LLM、有预算上限）在故障域上必须用服务边界隔离——**进程级降级才是真降级**，mall-ai 内部的降级代码在进程崩溃时也不存在；第二，代码架构要长成面试能讲的样子——普通搜索是召回地基、AI 是增强重排，分层不替代；第三，未来升 Spring AI 只动 mall-ai 内部，搜索独立则升级零波及。A1 方案我保留着没删——这是决策树的完整证据。"

**被追问"mall-search 不是没人用吗，留着干嘛"**："没人用 ≠ 没价值。它现在是'能力悬空'——前端浏览走 DB、搜索走 AI，唯独缺一个'不依赖 LLM 的普通搜索层'。A2 是把它从僵尸改造成这个缺失的地基层；如果我直接删了，AI 一挂搜索就全挂，那才是真问题。"

**被追问"这不就是过度设计吗"**："演示项目确实没有 SLA，但架构形态是面试要讲的——我选择的是**职责正确的形态 + 可控的代价**（只读一段 multi_match，768M 内存），而不是为了省事把搜索揉进 AI 模块制造耦合。取舍我讲得清：商业项目我会用独立搜索网关 + 缓存，这里保留独立进程是它的最小投影。"

**被追问"降级触发条件是什么？AI 慢也会降级吗？"（⭐ 关键边界）**："**慢 ≠ 挂**——LLM 语义重排要数秒~十几秒，这是正常等待，绝不能因响应慢就降级。我的降级只认**确定性故障信号**：①HTTP 5xx（网关 503=AI 服务不可达、后端 500=AI 内部超时失败）；②网络层错误（连接拒绝=进程真挂）。axios 对 AI 请求给了 30s 长超时——远大于后端 10s 的 LLM 超时，所以正常慢响应根本不会触发降级；真能撑到 30s 超时的，说明 AI 响应异常卡死，此时降级并明确提示'AI 响应超时'，而不是静默切换。4xx（参数错/未登录）我也不静默降级——那会掩盖真实问题。实现上我给请求拦截器补了错误类型元数据（errType/status），前端按类型判定，并加了请求序号守卫防慢请求竞态覆盖。"

**诚实边界**："mall-search 现在数据是错的（缺商品、含下架残留），直接当降级通道用比不用更糟——所以 A2 的前提是同步模型补全（§5.4 配套改动），不是'保留现状'。"

---

## 六、第二批剩余项速览（待实施，含对应方案文档）

> #33 / #8 / #36 / #23 / #14(P0+P1) / **#5(P0)** 已完成（见 §一~§五、§六.5），仅剩以下：

| 编号 | 事项 | 方案文档 | 关键难点 | 状态 |
|---|---|---|---|---|
| #13 | Nacos 认证 | [[集群化与配置中心迁移方案]] §A0 | 11 服务+Seata+Dubbo 全配账号，原子切换 | ⏳ 待做 |
| #29 | 数据库备份 | 无（TODO 已给命令） | 需 ecs-user 配 cron | ⏳ 待做 |
| #5 余 P1 | Sentinel 热点参数限流 | [[Sentinel能力补充计划]] | 秒杀按 spuId 差异化（ParamFlowRule + 秒杀接口改造） | ⏳ 待做（随集群化评估） |
| #5 余 P2 | Sentinel 集群流控 | [[Sentinel能力补充计划]] | token server 统一配额（随 #4 集群化） | ⏳ 待做 |
| #2+#34 | AI 限流+并发闸门 | 无（TODO 已给层次） | 并发闸门 Semaphore 设计，改动最大 | ⏳ 待做 |

---

## 七、#5 Sentinel 能力补齐 P0（已完成：统一 Nacos 规则管理 + eager 修复）

> 本节记录 #5 P0 的**审计修正、实施、部署踩坑与面试话术**（P1/P2 见 §六 剩余项）。

### 7.1 审计修正：原"规则持久化 Nacos + 代码双保险"记载与事实不符（⭐ 面试开场）

**原记载**（方案文档/面试文档）："秒杀 QPS=10 = Nacos + 代码双保险，重启不丢"。

**实测打碎**（2026-09-08 服务器 + 日志双重证据）：

| 证据 | 内容 |
|---|---|
| ① Nacos 规则全空 | SENTINEL_GROUP / DEFAULT_GROUP 下 `mall-seckill-flow-rules` 等 dataId 均 `config data not exist` |
| ② 秒杀限流实际失效 | seckill 日志：代码规则加载（`QPS=10`）后，Nacos datasource 异步拉空 → `converter can not convert rules because source is empty` → **空规则整体替换本地规则** |
| ③ 3 个注解空转 | order（新增订单/支付订单）、sso（adminLogin）有 @SentinelResource 但无 datasource、无规则 |
| ④ degrade 空转 | seckill prod 配了 degrade datasource，Nacos 无 degrade 规则 |

**机制认知（覆盖坑）**：Sentinel 规则容器 FlowRuleManager 是**全局唯一 + 整体替换**——本地代码 loadRules（@PostConstruct 先执行）和 Nacos datasource（异步初始化后执行）都只是"往黑板上写"，**谁后写谁生效，不是叠加**。配了 datasource 后权威源 = Nacos：Nacos 空 → 擦空黑板（本地 QPS=10 消失）；Nacos 有值 → 加载生效。**代码规则兜底对"Nacos 空配置"无效，只对"Nacos 宕机/不可达"有效**（拉取失败不推送 → 本地存活）。

### 7.2 实施方案（为什么选 Nacos 统一管理而非代码 loadRules）

| 方案 | 结论 |
|---|---|
| 纯代码 loadRules（order/sso 各加规则类） | ❌ 与已配 datasource 的 seckill 行为不一致；Nacos 空仍会擦除；且用户历史踩过覆盖坑（面试文档 Q2 实证） |
| **Nacos 统一管理**（本次采用） | ✅ 权威源唯一、控制台/Nacos 热更新、重启自动恢复；代码规则保留为"宕机兜底"不删（双保险语义修正为"Nacos 运行期权威 + 代码宕机兜底"） |

### 7.3 实施清单（2026-09-08）

| 改动 | 文件 | 说明 |
|---|---|---|
| 加依赖 | order/sso pom | `sentinel-datasource-nacos` |
| 配 datasource | order/sso prod+test yml | flow+degrade → Nacos（sso 仅 flow：登录失败是业务异常，degrade 会误伤） |
| 规则事实来源入库 | `deploy/docker/sentinel/*.json`（5 个） | seckill flow QPS10+degrade、order flow QPS20×2+degrade、sso flow QPS10；`git add -f`（deploy/ 被 gitignore，与 compose/redis-conf 同策略） |
| 代码规则改兜底 | seckill `SentinelFlowRuleConfig` | 保留 + 注释机制（启动瞬态 + Nacos 宕机兜底） |
| 补 dashboard env | compose mall-sso | `SPRING_CLOUD_SENTINEL_TRANSPORT_DASHBOARD: sentinel:8858` |
| 删死文件 | `deploy/docker/sentinel-rules.json` | 无引用、GBK 乱码、count=100 与实际 10 不符 |

### 7.4 部署踩坑记录（⭐ 面试最有价值的三连坑）

| # | 坑 | 现象 → 根因 → 解法 |
|---|---|---|
| ① | **compose 未同步** | sso 心跳 `Connection refused`（host='nacos:8858'）→ 本地 compose 加了 sso dashboard env 但服务器 compose 是旧的 → 同步 compose + 重建 sso |
| ② | **双重 URL 编码** | Nacos 规则内容是 `%5B%0A...`（URL 编码串）→ python `quote()` + curl `--data-urlencode` 各编一次 → 改用 curl `content@file`（读文件自动编码一次） |
| ③ | **transport 懒加载** | Dashboard 看不到规则/监控，但规则实际生效（30 并发 20×429）→ SCA `spring.cloud.sentinel.eager` 默认 false，CommandCenter 等**首次流量**才启动（sso 日志：打流量后才出现 `Begin listening at port 8880`）→ prod yml 补 `eager: true`，启动即初始化 |

### 7.5 面试话术

**主线**："#5 Sentinel 我做了 P0：先审计发现原文档'规则持久化 Nacos + 代码双保险'是假的——Nacos 规则全空，秒杀本地代码规则被 Nacos 空配置覆盖（日志 source is empty），**限流实际失效**。这是 Sentinel 的覆盖坑：FlowRuleManager 整体替换非叠加，配了 datasource 后权威源就是 Nacos，代码 loadRules 兜底只对 Nacos 宕机有效、对空配置无效。我把三个模块统一到 Nacos 管理（建 5 条 flow+degrade 规则、order/sso 接 datasource、规则 JSON 入库作为事实来源），秒杀代码规则保留为宕机兜底。部署时踩了三个坑：compose 漏同步导致 sso 心跳连错地址、Nacos 规则双重 URL 编码、以及最隐蔽的——**Sentinel transport 默认懒加载**，没流量时 CommandCenter 不启动，Dashboard 看起来'没规则'但限流实际生效（我用 30 并发压出 20 个 429 证明规则在工作），最后用 `eager: true` 让 transport 启动即初始化。"

**被追问"为什么 Dashboard 空但限流生效？"**："Sentinel 规则有两条独立链路：规则数据在应用内存（从 Nacos datasource 加载），Dashboard 展示需要反向连应用的 transport 端口（CommandCenter）拉取。SCA 的 transport 默认懒加载——首次流量才启动 CommandCenter。所以'规则生效'（SphU.entry 检查 FlowRuleManager）和'Dashboard 可见'（CommandCenter 监听）是两回事。生产必须 `eager: true`，否则没流量的服务在 Dashboard 永远查无此人。"

**被追问"为什么 sso 不配 degrade？"**："adminLogin 失败是业务异常（密码错），degrade 的异常比例熔断会把正常业务失败当故障触发熔断——登录接口只配 QPS 限流防爆破，不配降级。order/seckill 配的是慢调用比例熔断（RT>2s 比例 0.5 → 熔断 10s），因为订单/秒杀链路的故障形态是下游变慢而非异常率升高。"

---

## 七·五、突发小插曲：秒杀页二次进入 500（Dubbo 应用名撞名，TODO #6 全项目修复）

> **2026-09-08 晚，部署 #5 后用户实测发现**：商品列表 → 秒杀 → 商品列表 → 秒杀，**第二次进秒杀报 500**（浏览器先 404 后 500，`seckill/spu/list` 持续失败）。属于第二批部署后的**回归/隐藏 bug 排查**，快速定位并修复。

### 现象与排查链

| 步骤 | 证据 | 结论 |
|---|---|---|
| ① 看 seckill 日志 | 无 ERROR、无业务异常 | 不是应用层代码问题 |
| ② 看 gateway 日志 | **`500 Server Error for HTTP GET /seckill/spu/list` + `java.lang.IllegalArgumentException: invalid version format: UNSUPPORTED` + `R:172.18.0.20:20880`** | gateway 把请求转发到了 **Dubbo 端口 20880**(非 HTTP 协议) |
| ③ 查 Nacos 实例 | `mall-seckill` 服务下有 **2 实例: 10007(HTTP) + 20880(Dubbo)** | gateway `lb://mall-seckill` 轮询,一半请求打到 Dubbo 端口 |
| ④ 对照代码 | seckill `dubbo.application.name` = `mall-seckill` = spring 名(**撞名**);order 用 `mall-order-dubbo`(分开,安全) | **根因 = TODO #6 同类问题,发生在 mall-seckill** |

**"第一次成功、第二次 500"的机理**：gateway 的 `lb://` 是**轮询负载均衡**——第 1 次请求路由到 10007(HTTP 成功),第 2 次轮到 20880(Dubbo,非 HTTP 协议握手失败 → 500)。不是"第二次才出问题",是**交替命中**时用户恰好观察到失败。

### 全项目排查(不止修一个)

gateway `lb://` 路由 × Nacos 实例列表 × `@DubboService` 扫描三向对照,揪出全部"暴露 Dubbo + 撞名"模块:

| 模块 | 撞名? | 暴露 @DubboService? | 处理 |
|---|---|---|---|
| mall-seckill | ⚠️ | ✅ (ForOrderSeckillRecord) | ✅ 改 `mall-seckill-dubbo` |
| mall-ums | ⚠️ | ✅ (UserServiceImpl) | ✅ 改 `mall-ums-dubbo`(潜伏隐患,一并修) |
| mall-product | ⚠️ | ✅ (7 个 service) | ✅ 改 `mall-product-dubbo`(原 #6 主角,曾用直连 9010 规避) |
| mall-ai / mall-order | 已分开 | ✅ | 不用动(本就是 `*-dubbo`) |
| mall-front/search/ams | ⚠️ | ❌ **无 provider** | **不用改**(不注册 20880,Nacos 实测仅 HTTP 实例,lb:// 安全)→ 统一规范入 TODO #45(第三批) |

### 为什么改注册名不影响调用方(关键认知)

Dubbo 消费者按**接口**引用(`providers:com.cooxiao.mall.product.*` 接口级注册),**不依赖应用名**——order/ai 早就是 `*-dubbo` 独立名且全链路正常,是现成验证。改名只影响 Nacos 里"服务名 → 实例"的组织:HTTP 实例留在 spring 名下(供 gateway lb://),Dubbo 20880 移到 `*-dubbo` 名下(供接口级消费发现)。

### 面试话术

"部署后用户反馈秒杀页第二次进入 500——排查 gateway 日志发现 `invalid version format: UNSUPPORTED` 且目标是 **20880 端口**,立刻明白是 `lb://` 轮询把请求打到了 Dubbo 端口。根因是 **Dubbo 应用名与 Spring 应用名撞名**,Dubbo 3.x 应用级注册把 20880 混进服务名。我做的不只是修 seckill,而是**全项目排查**:gateway lb:// 路由 × Nacos 实例 × @DubboService 三向对照,发现 ums 也撞名且有 provider(潜伏隐患)一并修,product 是历史遗留(曾用直连规避)也根治;front/search/ams 虽撞名但无 provider 不构成风险,记为规范项防未来踩坑。这个排查思路——'不只是修当前故障,而是按故障模式全量清查'——比单点修复更有价值。"

---

## 八、第二批通用面试话术（贯穿主线）

**主线叙事**："第二批我按'收益/成本/独立性'排序做了代码批：#8 修了 AI 预算 8:00 重置的时区 bug（10 行）；#23 做了一轮校验审计——过程中修正了原审计'漏触发 vs 没规则'的混淆，补了 5 个 DTO 规则 + 类级/参数级 @Validated，还发现并补全了全局异常处理器对 MethodArgumentNotValidException 的缺失（否则校验失败会返回 500 而不是 400）；#36 把订单消费者的无限 requeue 改成 x-death 限次重试，并补了 DLX 死信链路——期间踩了 RabbitMQ 队列参数不可变（406 PRECONDITION_FAILED）的坑；#14 处理 Redis 与 DB 库存一致性——推翻了自己第一版'付款前查库存'方案（语义缺陷），改为方案Y查'本单成交'，补 order_type 治本，第3层改静默丢弃为三兜底，最后落地 P1 对账任务（运行期轻量 + 凌晨全量）；#5 审计发现 Sentinel 规则实际全空、秒杀限流失效，统一到 Nacos 管理并修复 transport 懒加载。这些线都踩了认知坑：时区不能依赖环境、DTO 校验有表达边界（or/跨字段）、自定义容器工厂会绕过 Spring retry、MQ 队列声明是一次性的、'查剩余库存'不可区分本单归属、规则权威源只能有一个、transport 懒加载≠规则不生效。"

**被追问"为什么不等公司方案"时**：个人项目我是 owner，但每个决策对齐企业做法（DLX/发送确认/kid 轮换/审计先行/对账分层），说明知道生产标准与当前取舍。

---

**维护提示**: 本文件随第二批逐项实施持续补充；完成一项更新头部状态并回填细节。与 [[TODO文件]] 保持一致（TODO 是状态源，本文件是"原理+疑惑+话术"深挖）。
