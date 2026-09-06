# TraceId 链路日志规范方案

> **状态**: 评估 + 计划（2026-08-28），**待实施**（演示项目优先级低，用户确认后处理）
> **背景**: 应用层 traceId（X-Trace-Id）只覆盖 HTTP 链路；定时任务 / MQ 消费者 / Dubbo provider 线程的日志 `[]` 空。SW agent 已全量接入（9.4.0），**traceId 其实一直在生成，只是没写进日志**。

---

## 一、现状盘点（代码 + 服务器实证）

| 场景 | 位置 | 日志 traceId |
|------|------|-------------|
| HTTP 请求链路 | 网关生成 → mall-common TraceIdFilter → MDC | ✅ 有（front 日志 `[7b4a2dbf]` 实测）|
| Quartz 定时任务 | SeckillInitialJob / SeckillBloomInitialJob | ❌ `[]` |
| @Scheduled | MessageRetryTask（5s 重试补发）| ❌ `[]` |
| MQ 消费者 | SeckillQueueConsumer / OrderQueueConsumer | ❌ `[]` |
| Dubbo provider 线程 | product 的 7 个接口等 | ❌ `[]` |
| 网关自己 | TraceIdGlobalFilter 只塞 header 不写 MDC | ❌ `[]` |

**根因**: MDC 是线程绑定的；X-Trace-Id 只通过 HTTP 请求头传播；MQ / Dubbo / 定时任务链路没有携带。

---

## 二、方案对比

| 方案 | 内容 | 工作量 | 覆盖范围 | 评价 |
|------|------|--------|---------|------|
| **A. SW logback 集成**（推荐） | mall-common 加 `apm-toolkit-logback-1.x` + `logback-spring.xml` 用 `TraceIdMDCPatternLogbackLayout`，pattern 加 `%X{tid}` | **~2 小时** | **全部场景**（SW 有 Dubbo/MQ/Quartz 插件，全链路都生成 traceId） | 性价比最高；一处配置全部服务生效 |
| B. Dubbo attachment 传播 | Consumer 塞 X-Trace-Id 附件 + Provider 读进 MDC（复用 Seata XID filter 经验） | ~0.5 天 | 仅 Dubbo 链路 | 与 A 重复（A 已覆盖）|
| C. MQ 消息头带 traceId | 生产者 2 处塞 header + 消费者 2 处读 | ~0.5 天 | 仅 MQ 消费 | 与 A 重复 |
| D. 定时任务固定前缀 | 任务开始 MDC.put(任务名+时间戳) | ~2 小时 | 仅定时任务 | 与 A 重复，且 A 更好（有真实 traceId）|
| E. 全做 | A+B+C+D | ~1.5 人天 | 全覆盖 | 演示项目不必要 |

**结论：只做方案 A（~2 小时），B/C/D 不做**——A 已覆盖全部场景，且 SW 的 traceId 比自造的更权威（能和 SW UI 对号）。

---

## 三、方案 A 实施步骤

### Step 1 — mall-common pom 加依赖

```xml
<!-- SkyWalking traceId 写进日志（logback 1.x 集成） -->
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-logback-1.x</artifactId>
    <version>9.4.0</version>   <!-- 与 agent 版本对齐（服务器实测 9.4.0）-->
</dependency>
```

### Step 2 — mall-common resources 加 `logback-spring.xml`

所有依赖 mall-common 的服务自动生效（含网关，共 12 个应用）。pattern 保留应用层 `%X{traceId}`，新增 SW 层 `%X{tid}`：

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.TraceIdMDCPatternLogbackLayout">
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{traceId}] [%X{tid}] %-5level %logger{36} - %msg%n</pattern>
            </layout>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

> ⚠️ 现有各服务 `application.yml` 里的 `logging.pattern.console` 会被 logback-spring.xml 覆盖（Spring Boot 优先用配置文件）——实施时**实测确认**覆盖行为，若未覆盖则删除各服务的 pattern 行。

### Step 3 — 重启全部服务（同一维护窗口，与 R7 内存优化等合并）

### Step 4 — 验证

```bash
# HTTP 链路：应同时出现 [traceId] 和 [tid]
docker logs csmall-front | tail -5

# 定时任务：seckill 的 SeckillInitialJob 日志 [tid] 应非空
docker logs csmall-seckill | grep SeckillInitialJob | tail -3

# Dubbo 链路：product 日志应有 [tid]
docker logs csmall-product | tail -3

# 对号：SW UI 里随机取一个 traceId → 服务器 grep 日志能命中
docker logs csmall-front | grep <sw-trace-id>
```

---

## 四、风险与回滚

| 风险 | 说明 | 应对 |
|------|------|------|
| toolkit 版本与 agent 不匹配 | 9.x 内一般兼容 | 尽量与 agent 版本一致（9.4.0）|
| 本地开发没挂 agent | tid 为空 | 日志多一个空占位符，无副作用 |
| 日志行变长 | 多一个 ID 列 | 可接受 |
| logback-spring.xml 覆盖现有 pattern | 行为需实测 | Step 2 备注 |

**回滚**：删 mall-common 的 logback-spring.xml + 移除依赖 → 重启即可（回到 logging.pattern 方式），零数据风险。

---

## 五、面试价值

- **"日志和链路追踪对不上号"是很多项目的通病**，主动讲 = 有全局观
- 能讲清三件事：
  1. 应用层 traceId vs SW traceId 是两套机制（一个管日志检索、一个管拓扑耗时）
  2. MDC 线程绑定 → 为什么定时任务/MQ/Dubbo 线程日志没 traceId
  3. SW toolkit 原理：`TraceIdMDCPatternLogbackLayout` 把 sw8 traceId 注入 MDC key `tid`
- **诚实边界**：演示项目"先不做"（2 小时随时可做）——面试时主动说出这个待改进点，比藏着好
