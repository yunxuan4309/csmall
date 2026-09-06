# 连接池统一 HikariCP 方案

> **创建日期**：2026-08-17
> **状态**：📝 方案已定稿，**暂缓执行**（演示项目不冒重启风险）
> **背景**：`docs/评估报告/TODO文件.md` R5 已核实——文档曾误记"已全面替换 HikariCP"，实际仅 mall-resource 使用 HikariCP，其余 6 个服务（product/order/seckill/ums/ams/sso）生产仍为 Druid 1.2.24。本文档为"真正切换"的详细执行方案。

---

## 一、现状（2026-08-17 从服务器 JAR 核实）

| 服务 | prod yml 连接池 | JAR 内依赖 | 备注 |
|------|----------------|-----------|------|
| mall-resource | HikariCP（默认） | 仅 HikariCP-5.0.1 | ✅ 已切换（2026-06 事故后） |
| mall-product | DruidDataSource | druid-1.2.24 + HikariCP-5.0.1 | ⚠️ 用 Druid，HikariCP 冗余打包 |
| mall-order | DruidDataSource | druid-1.2.24 + HikariCP-5.0.1 | ⚠️ 同上 |
| mall-seckill | DruidDataSource | druid-1.2.24 + HikariCP-5.0.1 | ⚠️ 同上 |
| mall-ums | DruidDataSource | druid-1.2.24 + HikariCP-5.0.1 | ⚠️ 同上 |
| mall-ams | DruidDataSource | druid-1.2.24 + HikariCP-5.0.1 | ⚠️ 同上 |
| mall-sso | DruidDataSource ×3 | druid-1.2.24 + HikariCP-5.0.1 | ⚠️ 三数据源（ams/ums/order） |

**为什么 HikariCP 也在 JAR 里**：Spring Boot 默认数据源即 HikariCP，即使指定 Druid 也会打包，只是不启用。

---

## 二、为什么统一（动机）

1. **技术栈统一**：7 个服务一套连接池，降低心智负担
2. **事故教训**：Druid `max-wait=0` 无限等待曾致上传线程挂死（`docs/问题解决/问题解决--安卓图片上传.md`），HikariCP 无此默认值问题
3. **少一个依赖**：Druid 的监控/防注入能力已被 SkyWalking + MyBatis-Plus 覆盖（详见"监控说明"）
4. **Spring Boot 默认**：删掉 `type:` 行即回落 HikariCP，配置最简单

## 三、为什么不现在做（暂缓原因）

1. **Druid 在 6 个服务已稳定运行 2 周+**，无故障迹象
2. **切换需全量回归 + 重启窗口**：6 个模块重新打包部署，秒杀/订单等核心链路重启有风险
3. **演示/学习项目**：收益（技术栈统一）小于风险（改挂影响面试演示）

---

## 四、执行步骤（择日实施）

> ⚠️ 按依赖顺序重启：**先基础设施无关的服务，后依赖方**；建议按「product → order → seckill → ums → ams → sso → gateway」顺序逐个切换，每个验证通过再切下一个。

### Step 0 — 备份

```bash
# 服务器上备份（ai-deepseek 可执行 docker 操作）
cp /data/csmall/docker-compose.yml /data/csmall/docker-compose.yml.bak.$(date +%Y%m%d)
```

### Step 1 — 修改 6 个模块的 prod yml（删 `type:` 行）

各模块 `src/main/resources/application-prod.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://...
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    # 删除下面这一行 → 回落 Spring Boot 默认 HikariCP
    # type: com.alibaba.druid.pool.DruidDataSource
    hikari:                                    # ← 新增 HikariCP 参数（对齐 mall-resource 的成熟配置）
      connection-timeout: 10000                # 10s 拿不到连接快速失败（防 Druid max-wait=0 挂死重演）
      maximum-pool-size: 20
      minimum-idle: 5
      max-lifetime: 300000                     # 5min，小于 MySQL wait_timeout(8h)
      idle-timeout: 300000
```

涉及文件：
- `mall-product/mall-product-webapi/src/main/resources/application-prod.yml`
- `mall-order/mall-order-webapi/src/main/resources/application-prod.yml`
- `mall-seckill/mall-seckill-webapi/src/main/resources/application-prod.yml`
- `mall-ums/mall-ums-webapi/src/main/resources/application-prod.yml`
- `mall-ams/mall-ams-webapi/src/main/resources/application-prod.yml`
- `mall-sso/src/main/resources/application-prod.yml`（⚠️ 三处数据源，逐个删 type 行）

### Step 2 — 移除 webapi pom 的 druid 依赖

各 webapi `pom.xml` 删除：
```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>druid</artifactId>
</dependency>
```

涉及：`mall-product/mall-product-webapi/pom.xml`、`mall-order/mall-order-webapi/pom.xml`、`mall-seckill/mall-seckill-webapi/pom.xml`、`mall-ums/mall-ums-webapi/pom.xml`、`mall-ams/pom.xml`、`mall-sso/pom.xml`

### Step 3 — 检查 service 模块的 HikariCP exclusion

> **2026-08-17 核实**：`mall-ums-service` / `mall-product-service` 的 pom 有 `<exclusions>` 排除 HikariCP。切换后**必须移除 exclusion**，否则 HikariCP 被排除 → 连接池缺失 → 启动失败。

```xml
<!-- 找到并删除类似这样的 exclusion -->
<exclusions>
    <exclusion>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
    </exclusion>
</exclusions>
```

### Step 4 — 本地构建 + 验证

```bash
mvn clean package -DskipTests
# 本地 IDEA 启动任一服务，确认日志出现：
# "HikariPool-1 - Start completed." （而非 Druid 相关日志）
```

### Step 5 — 逐个部署 + 服务器验证

```bash
# 每个服务切换后：
scp jars/mall-xxx.jar ai-deepseek@8.156.77.197:/data/csmall/jars/
ssh ai-deepseek@8.156.77.197 "cd /data/csmall && docker compose up -d mall-xxx"
```

验证清单（每个服务）：
```bash
docker logs csmall-xxx --since 5m | grep -iE "HikariPool|Start completed|ERROR"   # 连接池启动成功、无 ERROR
curl -s http://127.0.0.1:<端口>/actuator/health                                    # UP
# 功能抽测：登录/列表/下单/秒杀 各调一遍
```

### Step 6 — 全链路回归

- 前台：注册/登录 → 浏览商品 → 加购物车 → 下单 → 支付 → 秒杀
- 后台：admin 登录 → 仪表盘 → 商品管理
- AI：对话/搜索（走 ES + Redis 链路）

### Step 7 — 更新文档

- `docs/项目上下文文档.md` §6.1(8)/§8.12：改为"已统一 HikariCP"
- `docs/评估报告/TODO文件.md` R5：标记已解决
- `docs/面试准备/03-数据库设计.md` Q7：更新为统一后现状

---

## 五、监控说明（为什么不需要 Druid 面板）

| 监控需求 | 方案 | 状态 |
|---------|------|------|
| 链路级（接口耗时/SQL 慢查询/调用链） | **SkyWalking**（Agent 无侵入，OAP + UI 8088） | ✅ 已接入 |
| 连接池水位（活跃连接/池状态） | **HikariCP actuator 指标**（`management.endpoints.web.exposure.include: health,info` 已开，可扩展 `metrics`） | ✅ 可用 |
| SQL 防注入 | MyBatis-Plus 参数化查询（预编译）天然防注入 | ✅ 内置 |

> **结论**：Druid 自带监控面板（StatViewServlet/SQL 统计）与 SkyWalking **职责重叠**——SkyWalking 管链路、Druid 面板管连接池。选 HikariCP 后连接池水位通过 actuator 指标看，链路通过 SkyWalking 看，二者互补、无需 Druid。

---

## 六、回滚方案

若某服务切换后异常：
```bash
# 1. 恢复旧 JAR（git 回退该模块 pom/yml 改动 → 重新构建）
# 2. 重新部署
scp jars/mall-xxx.jar（旧版） ai-deepseek@8.156.77.197:/data/csmall/jars/
docker compose up -d mall-xxx
```
> 回滚粒度：单服务独立回滚，不影响其他已切换服务。

---

## 七、风险与注意事项

1. **HikariCP exclusion 陷阱**：`mall-ums-service`/`mall-product-service` 曾排除 HikariCP，切换前必须移除（Step 3），否则启动失败
2. **SSO 三数据源**：三处 `type:` 都要删，且三个 HikariPool 会各自建池（连接数 ×3，注意 `maximum-pool-size` 别设太大）
3. **连接参数对齐**：HikariCP 无 `max-wait` 概念（用 `connection-timeout`），配置迁移时别照抄 Druid 参数名
4. **重启顺序**：按依赖顺序逐个切换，避免全部同时重启（历史教训：整栈同时重启曾致 resource 容器挂 2 天）

---

**关联文档**：`docs/评估报告/TODO文件.md`（R5 事实核查）、`docs/问题解决/问题解决--安卓图片上传.md`（Druid 事故）、`docs/项目上下文文档.md`（现状记录）
