# JVM 调优方案

> 日期：2026-07-31
> 服务器：阿里云 ECS 4C16G，Docker Compose 20 容器
> 基准数据来源：`docker stats --no-stream` 实际运行内存快照

---

## 一、现状与问题

### 1.1 当前配置

11 个微服务 Dockerfile 统一使用：

```dockerfile
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]
```

**问题**：只有最大堆限制，缺失关键参数：
- 无 `-Xms`（初始堆）→ JVM 启动后频繁向 OS 申请内存，触发 GC
- 无 GC 策略 → 默认 Serial GC（单线程），4 核 CPU 大部分闲置
- 无 `MaxMetaspaceSize` → 类元数据可无限增长，Spring Boot 类多时轻松破 120MB
- 无 OOM 自动 dump → 出问题无法排查

### 1.2 实际内存远超堆限制

`docker stats --no-stream` 数据（2026-07-31 运行态）：

| 服务 | 容器内存 | -Xmx | 差额 | 说明 |
|------|---------|------|------|------|
| mall-resource | 238 MB | 256m | 正常 | 只做文件上传，极轻量 |
| mall-gateway | 334 MB | 256m | +78M | Netty NIO + WebFlux 堆外内存 |
| mall-ams | 326 MB | 256m | +70M | 后台管理，低并发 |
| mall-ums | 350 MB | 256m | +94M | 用户管理，Spring Security |
| mall-sso | 341 MB | 256m | +85M | JWT 解析 + 双数据源 |
| mall-search | 357 MB | 256m | +101M | ES Client 连接池 |
| mall-front | 439 MB | 256m | +183M | Dubbo Consumer 连接池 |
| mall-ai | 405 MB | 256m | +149M | ES Client + DeepSeek HTTP |
| mall-product | 462 MB | 256m | +206M | Dubbo Provider + Seata + MyBatis |
| mall-order | 461 MB | 256m | +205M | RabbitMQ + Seata + Alipay SDK |
| mall-seckill | 531 MB | 256m | +275M | Redis 缓存 + RabbitMQ + Sentinel |

差额来源：Metaspace（类元数据）+ 线程栈 + NIO Direct Buffer + JVM Native + Docker 容器开销。

### 1.3 256MB 堆太小的问题

Spring Boot 3.2 + Dubbo 3.3 + Seata + Sentinel 的应用，启动时就需要 ~150MB 堆（Bean 创建、AOP 代理、连接池初始化）。`-Xmx256m` 意味着：
- 可用堆仅 256MB，启动后只剩 ~100MB 余量
- 每次 GC 后堆几乎满，触发频繁 GC（每分钟几十次 Young GC）
- GC 使用 Serial（单线程），暂停时应用卡顿
- 堆外开销反超堆本身，总内存远超预期

---

## 二、调优依据

### 2.1 GC 选择：G1GC

| GC | 适用场景 | 本项目 |
|----|---------|--------|
| Serial | 单核、<100MB 堆 | 不符合 |
| Parallel | 吞吐优先、批处理 | 不符合 |
| G1 | 多核、200MB~4GB 堆、低延迟 | **最佳匹配** |
| ZGC | 超大堆、亚毫秒暂停 | 当前不需要 |

选择 G1GC 的理由：
- 服务器 4 核，G1 可并行 GC，利用多核
- 堆大小 256~448MB，G1 在此区间表现最优
- 目标暂停 `-XX:MaxGCPauseMillis=200`，对 Web 请求影响极小
- 自适应分区，减少 Full GC 概率

### 2.2 堆大小分档依据

按 `docker stats` 实际内存反推最佳堆：

```
最佳堆大小 ≈ 容器内存 − Metaspace(100M) − 线程(80M) − NIO(30M) − Native(30M) − Docker overhead(20M)
            ≈ 容器内存 − 260MB
```

| 档位 | 服务 | 推导堆大小 | 取整 |
|------|------|-----------|------|
| 轻量 | resource, gateway, ams, ums, sso, search | 238~357 − 260 = 0~97M → 需要至少 192MB 给 Spring Boot | 128~256m |
| 中量 | front, ai | 405~439 − 260 = 145~179M → 需要至少 256MB | 192~384m |
| 重量 | product, order, seckill | 462~531 − 260 = 202~271M → 需要至少 320MB | 256~448m |

### 2.3 `-Xms` 设置

`-Xms` 设为最终堆的 50%~60%，平衡启动速度与运行效率：
- 设置过低：启动后频繁扩容，触发多次 GC
- 设置过高：启动变慢（需要一次性分配大堆）
- 设 50%：启动快，运行中逐步扩到 -Xmx，扩堆时触发一次 GC（可接受）

### 2.4 `MaxMetaspaceSize=128m`

Spring Boot 3.2 + Dubbo + Seata + Sentinel 的类数量约 15,000~20,000 个，Metaspace 通常在 80~120MB。设 128MB 上限防止 ClassLoader 泄漏时元数据空间无限增长。

### 2.5 OOM HeapDump

`-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp` — 出 OOM 时自动 dump 到 `/tmp`，排查内存泄漏必需。

---

## 三、调优方案

### 3.1 统一 JVM 参数（所有服务）

```bash
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:MaxMetaspaceSize=128m
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp
```

### 3.2 分档堆参数

**轻量档**（resource, gateway, ams, ums, sso, search）：
```
-Xms128m -Xmx256m
```
预估容器内存：~350MB

**中量档**（front, ai）：
```
-Xms192m -Xmx384m
```
预估容器内存：~480MB

**重量档**（product, order, seckill）：
```
-Xms256m -Xmx448m
```
预估容器内存：~550MB

### 3.3 预期效果

| 指标 | 调优前 | 调优后 |
|------|--------|--------|
| 堆最大 | 256MB(全部) | 256~448MB(按需) |
| GC 算法 | Serial | G1（并行） |
| Young GC 频率 | ~20次/分钟 | ~5次/分钟 |
| GC 暂停 | 50~200ms | 10~50ms |
| Metaspace 上限 | 无限制 | 128MB |
| OOM 自动 dump | 无 | 有 |
| 总内存 | ~6.5 GB | ~4.5 GB |
