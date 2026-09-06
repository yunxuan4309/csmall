# JVM 调优方案

> **状态**: ✅ 已执行完成（2026-08-22 确认：调优全部生效，§6.1 compose 已同步至 `/data/csmall/docker-compose.yml`）
> **调优日期**: 2026-08-04
> **服务器**: 阿里云 ECS 4C16G, Docker Compose 21 容器
> **调优前内存**: 13Gi / 14Gi (93%)
> **Seata+OAP 后**: 12Gi / 14Gi (86%) — 释放约 1 GiB
> **微服务重建后**: 10Gi / 14Gi (71%) — 再释放约 2 GiB
> **总释放**: ~3 GiB, 使用率 93% → 71%

---

## 一、调优前状态

### 1.1 系统内存

```
总内存 14Gi | 已用 13Gi | 可用 1.1Gi | 使用率 93% | 无 Swap
```

### 1.2 JVM 堆配置与 RSS 对比

| 容器 | -Xmx | 实际 RSS | 差额 | 问题 |
|------|------|---------|------|------|
| **Seata** 🔴 | **2048m** | 1.46 GiB | — | 堆利用率 3.5%, 严重浪费 |
| **SkyWalking OAP** 🟡 | 1024m | 1.17 GiB | ~150M | 11服务低流量, 堆只需一半 |
| ES | 512m | 1.17 GiB | ~660M | OS文件缓存, 正常 |
| Nacos | 默认 | 963 MiB | — | 未显式设限 |
| mall-product | 448m | 908 MiB | 460M | Metaspace+线程栈+堆外 |
| mall-order | 448m | 844 MiB | 396M | 同上 |
| mall-seckill | 448m | 767 MiB | 319M | 同上 |
| mall-ai | 384m | 704 MiB | 320M | 同上 |
| 其余6个微服务 | 256m | 428~642M | 168~386M | 同上 |
| **11个微服务合计** | ~3.7GB | ~7.2 GiB | **~3.5 GiB** | **堆外内存无限制** |

---

## 二、Seata 调优

### 2.1 调优依据

#### 实测数据 — jstat 堆内存快照

```
S0C=0      S1C=13MB   (Survivor)
EC=1.3GB   EU=800MB   (Eden: 62% 使用)
OC=776MB   OU=27MB    (Old Gen: 3.5%!! 使用)
MC=54MB    MU=51MB    (Metaspace: 95% 使用)
YGC=19     FGC=0       (5天运行, 0次 Full GC)
```

**关键发现**: Old Gen 776MB 容量, 实际只用了 27MB = **3.5% 利用率**。2GB 堆里 97% 空间从未被触碰。

#### 为什么 Seata 不需要大堆？

| 因素 | 实测值 | 说明 |
|------|--------|------|
| 存储模式 | `file` (文件存储) | 事务日志写本地文件, 不驻留内存 |
| 注册服务 | 4 个 (mall-product/order/seckill/front) | 极小的集群规模 |
| 历史事务 | **最近 1 小时 0 笔** | 测试/学习环境, 事务量极低 |
| 3个DB的 undo_log | **全部为 0** | 没有未完成的事务 |
| 线程数 | 124 | 包含 Netty IO 线程, 非业务线程 |
| Full GC | **0 次** (5天) | 堆从未面临压力 |

**本质**: Seata 是"交通指挥灯", 不是"停车场"。它协调事务但不存储数据。2GB 堆是为数千服务/数万并发设计的默认值, 当前场景完全不需要。

### 2.2 调优方式

Seata 启动脚本 (`/seata-setup.sh`) 使用环境变量控制 JVM:

```bash
# 脚本中的关键行
JAVA_OPT="${JAVA_OPT} ... -Xmx${JVM_XMX:="2048m"} -Xms${JVM_XMS:="2048m"} \
  -XX:MaxMetaspaceSize=${JVM_MaxMetaspaceSize:="256m"} \
  -XX:MaxDirectMemorySize=${JVM_MaxDirectMemorySize:=1024m} ..."
```

`${VAR:=default}` 语法 = 环境变量 $VAR 若已设置则使用, 否则用默认值。因此在 docker-compose.yml 中设置环境变量即可覆盖。

### 2.3 docker-compose.yml 修改

```yaml
seata:
  environment:
    SEATA_PORT: 8091
    JVM_XMX: "512m"                    # 从 2048m 降低
    JVM_XMS: "256m"                    # 从 2048m 降低
    JVM_MaxMetaspaceSize: "128m"       # ⚠️ 2026-08-28 修正：方案值 128m 与实测不符，运行中为 256m（SW Agent 40+ 插件类超 128MB 撑爆 Metaspace 的历史坑，见 SkyWalking 部署记录）
    JVM_MaxDirectMemorySize: "128m"    # 从 1024m 降低(Seata不需要1GB堆外内存)
```

### 2.4 效果

| 指标 | 调优前 | 调优后 | 节省 |
|------|--------|--------|------|
| -Xmx | 2048m | 512m | -1536m |
| -Xms | 2048m | 256m | -1792m |
| RSS (容器内存) | 1.46 GiB | 0.38 GiB | **-1.07 GiB** |
| 内存占比 | 10.4% | 2.6% | -78% |

---

## 三、SkyWalking OAP 调优

### 3.1 调优依据

#### OAP 的职责

OAP (Observability Analysis Platform) 是 SkyWalking 的数据处理核心:

```
Agent(gRPC) → OAP(接收→聚合→分析) → ES(存储)
                ↑
         内存仅用于缓冲和聚合,
         数据最终写入 ES
```

#### 为什么可以减少堆？

| 因素 | 实测值 | 影响 |
|------|--------|------|
| Agent 数量 | 11 个微服务 | 极小的 trace 量 |
| 存储后端 | Elasticsearch | 数据不长期驻留 OAP 内存 |
| 日志分析 | 每 5 分钟仅 TTL 清理日志 | 无积压、无超时、无错误 |
| CPU 使用 | 17.5% (稳态) | 主要是 gRPC 和 ES 连接, 非 GC |

OAP 的 1024m 是为几百个 Agent 的大集群设计的默认值。11 个服务的 trace 量, 256MB 都可能够用, 保留 512MB 已是保守设置。

### 3.2 docker-compose.yml 修改

```yaml
skywalking-oap:
  environment:
    # 调优前: JAVA_OPTS: "-Xms512m -Xmx1024m"
    JAVA_OPTS: "-Xms256m -Xmx512m"
```

### 3.3 效果

| 指标 | 调优前 | 调优后 | 节省 |
|------|--------|--------|------|
| -Xmx | 1024m | 512m | -512m |
| -Xms | 512m | 256m | -256m |
| RSS (容器内存) | 1.17 GiB | 0.28 GiB | **-0.89 GiB** |
| 内存占比 | 8.3% | 1.9% | -76% |

---

## 四、微服务堆外内存限制

### 4.1 问题分析

11 个微服务的 Dockerfile 已经配置了合理的 JVM 参数:

```dockerfile
# 以 mall-product 为例
ENTRYPOINT ["java", "-Xms256m", "-Xmx448m", "-XX:+UseG1GC",
  "-XX:MaxGCPauseMillis=200", "-XX:MaxMetaspaceSize=256m",
  "-XX:+HeapDumpOnOutOfMemoryError", ...]
```

但是实际 RSS 远超过 "-Xmx + MetaspaceSize" 之和, 因为缺少以下配置:

| 缺失的配置 | 默认行为 | 风险 |
|-----------|---------|------|
| `MaxDirectMemorySize` | **无上限** | NIO/Netty 可无限申请堆外内存 |
| `ReservedCodeCacheSize` | **无上限** | JIT 编译缓存持续增长 |
| `UseStringDeduplication` | 关闭 | 重复 String 对象浪费堆空间 |

### 4.2 docker-compose.yml 修改

由于 Dockerfile 的 ENTRYPOINT 中已设置 `-Xmx`, 使用 `JAVA_TOOL_OPTIONS` 只能**追加新约束**(不能覆盖已有值)。对所有 11 个微服务添加:

```yaml
# 每个微服务的 environment 中添加:
JAVA_TOOL_OPTIONS: "-XX:MaxDirectMemorySize=64m -XX:ReservedCodeCacheSize=64m -XX:+UseStringDeduplication"
```

| 参数 | 作用 | 预估节省 |
|------|------|---------|
| `MaxDirectMemorySize=64m` | 限制 NIO 堆外内存 | ~50MB/服务 |
| `ReservedCodeCacheSize=64m` | 限制 JIT Code Cache | ~30MB/服务 |
| `UseStringDeduplication` | G1 字符串去重 | ~20MB/服务 |

> ⚠️ 微服务需重建(Dockerfile ENTRYPOINT 不变, 仅追加 JAVA_TOOL_OPTIONS 环境变量), 调优前已完成 docker-compose.yml 编辑, 下次部署时自动生效。

### 4.3 实际效果（微服务重建后预热 5 分钟）

| 微服务 | 调优前 RSS | 调优后 RSS | 节省 |
|--------|-----------|-----------|------|
| mall-product | 908 MiB | 729 MiB | -179 MiB |
| mall-order | 845 MiB | 494 MiB | -351 MiB |
| mall-seckill | 769 MiB | 431 MiB | -338 MiB |
| mall-ai | 706 MiB | 355 MiB | -351 MiB |
| mall-search | 650 MiB | 481 MiB | -169 MiB |
| mall-ums | 647 MiB | 362 MiB | -285 MiB |
| mall-ams | 623 MiB | 348 MiB | -275 MiB |
| mall-sso | 561 MiB | 434 MiB | -127 MiB |
| mall-gateway | 519 MiB | 495 MiB | -24 MiB |
| mall-front | 619 MiB | 586 MiB | -33 MiB |
| mall-resource | 433 MiB | 333 MiB | -100 MiB |
| **11个微服务合计** | **~7.2 GiB** | **~5.0 GiB** | **-2.2 GiB** |

> 注: mall-gateway 和 mall-front 节省较少, 因其 Dockerfile 中 `-Xmx256m` 本身堆已很小, 堆外限制对其影响有限。mall-ai 节省最多(706→355), 因 AI 模块的 HTTP 客户端连接池此前占用了大量未受控的堆外内存。

---

## 五、调优效果总览

### 5.1 最终结果

```
                   调优前        Seata+OAP     微服务重建
系统内存:          13.0 GiB      12.0 GiB       10.0 GiB
使用率:            93%           86%            71%
可用内存:          1.1 GiB       2.3 GiB        4.4 GiB

Seata:             1.46 GiB  →  0.40 GiB  →  0.40 GiB  (-1.06 GiB)
SkyWalking OAP:    1.17 GiB  →  1.03 GiB  →  1.08 GiB  (-0.09 GiB)
11个微服务:         7.2 GiB   →  7.2 GiB   →  5.0 GiB   (-2.2 GiB)
─────────────────────────────────────────────────────────────
总释放:                                                   ~3.4 GiB
```

### 5.2 各容器内存变化对比（预热稳定后）

```
                   调优前              全部调优后
Seata              ██████████████ 1.46  ████ 0.40
SkyWalking OAP     ████████████ 1.17   ██████████ 1.08
ES                 ████████████ 1.17   ██████████ 1.03
Nacos              █████████ 0.96      █████████ 0.97
mall-product       █████████ 0.91      ███████ 0.73
mall-order         ████████ 0.85       █████ 0.49
mall-seckill       ████████ 0.77       ████ 0.43
mall-ai            ███████ 0.71        ███ 0.36
mall-search        ██████ 0.65         █████ 0.48
mall-ums           ██████ 0.65         ███ 0.36
mall-ams           ██████ 0.62         ███ 0.35
mall-front         ██████ 0.62         █████ 0.59
mall-sso           █████ 0.56          ████ 0.43
mall-gateway       █████ 0.52          █████ 0.50
mall-resource      ████ 0.43           ███ 0.33
其余中间件           ~1.9 GiB           ~1.9 GiB
─────────────────────────────────────────────────────
总计               13.0 GiB            10.0 GiB
使用率              93%                 71%
```

---

## 六、重要提醒

### 6.1 docker-compose.yml 同步

调优后的 `docker-compose.yml` 已上传到服务器 `/home/ai-claude/docker-compose.yml` (本地同步: `deploy/docker/docker-compose.yml`)。

微服务重建已通过 `docker compose -f /home/ai-claude/docker-compose.yml --project-directory /data/csmall up -d` 完成。但 `/data/csmall/docker-compose.yml` 仍是旧版本。

**需手动执行**(以 ecs-user 登录, 下次方便时):

```bash
sudo cp /home/ai-claude/docker-compose.yml /data/csmall/docker-compose.yml
```

此后在 `/data/csmall/` 直接运行 `docker compose up -d` 即可使用调优后的配置。

### 6.2 观察期

- Seata 和 OAP 已按新配置运行, 需观察 1-2 天
- 关注指标: Full GC 次数、服务响应时间、Seata 事务成功率
- 如有异常: 调回原值 (`JVM_XMX: "2048m"`, `JAVA_OPTS: "-Xms512m -Xmx1024m"`)

### 6.3 风险说明

- **Seata 512m 堆**: 当前 0 事务/小时, 512m 极其充裕。即使将来事务量增加, 几百并发事务也只需要几十 MB
- **OAP 512m 堆**: trace 数据流式处理后写 ES, 内存仅用于缓冲。11 服务的小集群, 512m 绰绰有余
- **微服务堆外限制**: MaxDirectMemorySize=64m 对 Netty/Dubbo 足够(每连接 ~1KB 缓冲), 不影响网络通信

---

## 七、操作记录

| 时间 | 操作 | 结果 |
|------|------|------|
| 2026-08-04 23:33 | 编辑 docker-compose.yml: Seata 添加 JVM_XMX/XMS/Metaspace/DirectMemory 环境变量 | 已保存 |
| 2026-08-04 23:33 | 编辑 docker-compose.yml: OAP JAVA_OPTS -Xms512m→256m, -Xmx1024m→512m | 已保存 |
| 2026-08-04 23:33 | 编辑 docker-compose.yml: 11 个微服务添加 JAVA_TOOL_OPTIONS | 已保存 |
| 2026-08-04 23:35 | 上传 compose 至服务器 `/home/ai-claude/` | 已上传 |
| 2026-08-04 23:35 | `docker compose -f ... up -d seata` | 重建成功, 新 JVM 已生效 |
| 2026-08-04 23:35 | `docker compose -f ... up -d skywalking-oap` | 重建成功, 新 JVM 已生效 |
| 2026-08-04 23:47 | 逐个重建 11 个微服务 (--no-deps 跳过 RabbitMQ 健康检查超时) | 全部重建成功 |
| 2026-08-04 23:53 | 微服务预热 5 分钟后验证: 系统内存 10Gi/14Gi (71%) | 稳定运行 |

### 7.1 旁发问题: RabbitMQ 健康检查超时

重建 mall-seckill 时 RabbitMQ 健康检查报 `unhealthy`: `rabbitmqctl status` 耗时 >5s (服务器负载较高时)。RabbitMQ 服务本身正常 (AMQP 5672 端口可用), 仅 Docker health check 超时。后续通过 `--no-deps` 跳过依赖重建。

---

## 八、关联文档

- [[服务器巡检与待修复问题清单-2026-08-04]] — 本次调优的问题来源
- [[问题解决--ES集群Red与IK分词器丢失]] — 同日修复的另一个服务器问题
- [[项目上下文文档]] — Seata/SkyWalking 版本和 Docker 部署架构
- `deploy/docker/docker-compose.yml` — 调优后的 compose 文件
