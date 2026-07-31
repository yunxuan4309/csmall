# SkyWalking 部署与兼容性修复 — 全链路问题排查

> 日期：2026-07-31 ~ 2026-08-01
> 服务器：阿里云 ECS 4C16G，Docker Compose
> 涉及：22 个容器（新增 2 个 SkyWalking + 11 服务加 Agent）

---

## 一、部署架构

```
微服务 --(gRPC:11800)--> OAP Server --(HTTP:9200)--> Elasticsearch
                                                       |
浏览器 <--(HTTP:8088)-- OAP UI <--(HTTP:12800)---------┘
```

新增 2 个容器 + 11 个服务挂载 Agent：
```yaml
skywalking-oap:
  image: apache/skywalking-oap-server:9.7.0
  environment:
    SW_STORAGE: elasticsearch
    SW_STORAGE_ES_CLUSTER_NODES: elasticsearch:9200
    JAVA_OPTS: "-Xms512m -Xmx1024m"

skywalking-ui:
  image: apache/skywalking-ui:9.7.0
  ports: "8088:8080"
```

每服务加 Agent 参数：
```
-javaagent:/skywalking-agent/skywalking-agent.jar
-DSW_AGENT_NAME=mall-xxx
-DSW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap:11800
```

Agent 通过 `/data/csmall/skywalking-agent:/skywalking-agent:ro` 卷挂载。

---

## 二、问题一：Alpine musl 与 MD5 算法不兼容

**现象**：部署后所有服务崩溃，Nacos 客户端报错 `NoSuchAlgorithmException: MessageDigest get MD5 instance error`。

**根因**：`eclipse-temurin:21-jre-alpine` 使用 musl libc（非 glibc），SkyWalking Agent 中部分加密算法（MD5）与 musl 的 `MessageDigest` 实现不兼容。连 Nacos 配置中心校验都因为 MD5 失败而罢工。

**修复**：11 个 Dockerfile 基础镜像从 `eclipse-temurin:21-jre-alpine` 改为 `eclipse-temurin:21-jre`（Debian）。副作用：镜像体积增加 ~50MB/服务，启动时间从 ~30s 增加到 ~9 分钟。

---

## 三、问题二：Agent 日志目录只读

**现象**：服务启动时报 `java.io.FileNotFoundException: /skywalking-agent/logs/skywalking-api.log (Read-only file system)`。

**根因**：Agent 卷挂载为 `:ro`（只读），Agent 默认在自身目录下写日志。

**修复**：所有 Dockerfile ENTRYPOINT 加 `-DSW_LOGGING_DIR=/tmp`，将 Agent 日志重定向到容器内可写的 `/tmp`。

---

## 四、问题三：Metaspace 溢出

**现象**：多个服务崩溃，日志显示 `java.lang.OutOfMemoryError: Metaspace`。

**根因**：JVM 调优时设了 `-XX:MaxMetaspaceSize=128m`。SkyWalking Agent 加载了大量插件类（activations 目录下 40+ 个插件 jar），加上 Spring Boot + Dubbo + Seata + MyBatis-Plus 等框架的类元数据，总 Metaspace 需求超过 128MB。

**修复**：`MaxMetaspaceSize` 从 128m 提升到 256m。Agent 额外占用 ~80MB Metaspace（40+ 插件 × ~2MB 每插件类元数据）。

---

## 五、问题四：Seata 依赖误删导致 Dubbo 调用失败

**现象**：商品列表加载时报 `NoClassDefFoundError: org/apache/seata/core/context/RootContext`。

**根因**：审计"形同虚设"时从 mall-front、mall-search、mall-ai 移除了 Seata 依赖。但 `mall-common` 中的 `SeataTransactionConsumerFilter`（Dubbo 消费者过滤器）引用了 `org.apache.seata.core.context.RootContext`。这三个服务虽不使用 `@GlobalTransactional`，但作为 Dubbo 消费端调用 mall-product 时，JVM 加载过滤器类失败。

**教训**：Seata 对 Dubbo 消费者是**类级依赖**（过滤器 import 需要），不是功能级依赖（不使用 `@GlobalTransactional`）。Java 类加载器在解析类时强制执行所有 import 依赖。

**修复**：恢复 mall-front、mall-search、mall-ai 三个服务的 Seata POM 依赖和 YML 配置。mall-ai 额外保持 `seata.enabled: false`（类可用但不启用）。

---

## 六、SSH 隧道访问

SkyWalking UI 未开放公网端口，通过 SSH 隧道访问：

```powershell
ssh -L 18088:localhost:8088 ecs-user@8.156.77.197
# 浏览器打开 http://localhost:18088
```

---

## 七、部署影响评估

| 指标 | 调优前 | 调优后 |
|------|--------|--------|
| 容器数 | 20 | 22 |
| 镜像基础 | Alpine | Debian |
| 服务启动时间 | ~30s | ~9min |
| 总内存 | ~6.5 GB | ~8.5 GB |
| 监控覆盖 | 无 | 11 服务全链路追踪 |
| 兼容性 | Alpine musl 无问题 | glibc 全兼容 |

## 八、经验总结

1. **Alpine + Java Agent = 风险组合**：musl libc 与 Java 安全算法经常不兼容，生产环境用 Debian/Ubuntu 基础镜像
2. **MaxMetaspaceSize 要留余量**：引入 APM Agent 后额外 +80MB Metaspace，128MB 不够
3. **Agent 日志路径要可写**：Agent 挂载卷建议不设 `:ro`，或将日志指向 `/tmp`
4. **Dubbo 消费者必须保留 Seata 类**：即使不写事务，filter 加载也需要 Seata 在 classpath
5. **Debian 镜像启动慢 10 倍**：对开发体验影响大，但对生产部署可接受
