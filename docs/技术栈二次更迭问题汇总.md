# 技术栈二次更迭问题汇总

> 时间：2026-05-09 ~ 2026-05-10
> 内容：JDK 17 → OpenJDK 21 升级、Seata 1.x → Apache Seata 2.1.0 迁移、弃用 mall-leaf 模块、JDK 21 运行时兼容性修复

---

## 一、JDK 17 → 21 升级

### 1.1 Maven 编译报错"不支持发行版本 21"

**原因**：IDEA 内置 Maven 使用的是 JDK 17，忽略系统 JAVA_HOME

**解决**：
- 临时：命令行 `set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11.10-hotspot" && mvn compile`
- 永久：IDEA → Settings → Build → Maven → Importing/Runner → JDK 改为 21

### 1.2 JJWT 0.9.x 不兼容 JDK 21

**原因**：JJWT 0.9.x 依赖 `javax.xml.bind`，JDK 11+ 已移除该包

**解决**：升级到 JJWT 0.12.6，API 有 breaking changes：

| 0.9.x | 0.12.6 |
|---|---|
| `setClaims()` | `.claims()` |
| `setExpiration()` | `.expiration()` |
| `signWith(key, algo)` | `.signWith(key, Jwts.SIG.HS512)` |
| `Jwts.parser().setSigningKey().parseClaimsJws().getBody()` | `Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload()` |

依赖从单个 `jjwt` 改为三个：`jjwt-api` + `jjwt-impl`(runtime) + `jjwt-jackson`(runtime)，同时移除 `javax.xml.bind:jaxb-api`

### 1.3 MyBatis-Plus Boot 2 Starter 不兼容 Spring Boot 3

**解决**：3 个模块的 `mybatis-plus-boot-starter` → `mybatis-plus-spring-boot3-starter`
- mall-order-webapi
- mall-ums-webapi
- mall-seckill-webapi

### 1.4 Spring Security 6.x API 变更

**问题a**：`@EnableGlobalMethodSecurity` 已移除
**解决**：`@EnableGlobalMethodSecurity(prePostEnabled = true)` → `@EnableMethodSecurity(prePostEnabled = true)`

**问题b**：`setAllowedOrigins("*")` 已废弃
**解决**：`setAllowedOrigins(Arrays.asList("*"))` → `setAllowedOriginPatterns(Arrays.asList("*"))`（6个模块）

### 1.5 MediaType.APPLICATION_JSON_UTF8 已废弃

**解决**：`JacksonConfig.java` 中移除 `MediaType.APPLICATION_JSON_UTF8`，`APPLICATION_JSON` 已覆盖

### 1.6 虚拟线程配置

10 个 `application-test.yml` 添加：
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

---

## 二、弃用 mall-leaf 模块，迁移至 MyBatis-Plus IdWorker

### 2.1 问题背景

mall-leaf 模块基于 JDK 8 + Spring Boot 2.5.4，与项目其他模块（JDK 21 + Spring Boot 3.2.5）存在代差，无法统一构建。美团 Leaf 自 2020 年起已停止维护，且项目已使用 MyBatis-Plus `IdType.ASSIGN_ID` 作为主要 ID 策略。

### 2.2 发现的问题

**Leaf 调用方式不一致**：

| 模块 | 方法名 | 端口 | 是否实际调用 |
|------|--------|------|------------|
| mall-order | `getDistributeId()` | 9090 | 是（2处） |
| mall-product | `getDistributeId()` | 9090 | 是（2处） |
| mall-ums | `getDistributeId()` | 9090 | 是（1处） |
| mall-ams | `generatId()`（拼写错误） | **8080**（错误） | 是（1处） |
| mall-front | `generatId()` | **8080**（错误） | 否（死代码） |
| mall-search | `getDistributeId()` | 9090 | 否（死代码） |

**自定义 XML 插入绕过 MyBatis-Plus ID 生成**：6 个实体类均声明了 `@TableId(type = IdType.ASSIGN_ID)`，但自定义 XML Mapper 的 insert 方法显式包含 `#{id}`，导致 ASSIGN_ID 注解实际无效。

### 2.3 解决方案：`IdWorker.getId()`

| 原调用 | 替换为 |
|--------|--------|
| `IdGeneratorUtils.getDistributeId("order")` | `IdWorker.getId()` |
| `IdGeneratorUtils.getDistributeId("order_item")` | `IdWorker.getId()` |
| `IdGeneratorUtils.getDistributeId(Key.SPU)` | `IdWorker.getId()` |
| `IdGeneratorUtils.getDistributeId(Key.SKU)` | `IdWorker.getId()` |
| `IdGeneratorUtils.getDistributeId("user")` | `IdWorker.getId()` |
| `IdGeneratorUtils.generatId("admin")` | `IdWorker.getId()` |

具体改动：
1. 4 个 Service 文件：替换 `IdGeneratorUtils` 调用为 `IdWorker.getId()`
2. 删除 6 个 `IdGeneratorUtils.java`
3. 根 pom.xml：注释掉 `<module>mall-leaf</module>` 和 mall-leaf 依赖管理
4. 新增 `mall-leaf/DEPRECATED.md`

### 2.4 方案优势与劣势

优势：消除外部依赖、统一技术栈、进程内生成 ID 更快、修复端口配置错误、降低运维成本

劣势：ID 不再趋势递增（但雪花算法是趋势递增的）、Long 精度问题（已通过 `JacksonConfiguration` 全局配置 Long→String 解决）

---

## 三、Seata 1.x → Apache Seata 2.1.0 迁移

### 3.1 版本选择

- Seata 2.6.0 要求 JDK 25+，不适用
- Seata 2.1.0 通过 110+ JDK 21 兼容性测试，是当前最佳选择

### 3.2 包名变更

**groupId**：`io.seata` → `org.apache.seata`
**import**：`import io.seata.*` → `import org.apache.seata.*`

涉及文件：3 个 pom.xml + 3 个 Java 文件的 import 语句

### 3.3 IDEA 运行 Seata Server

直接使用 `seata-server.bat` 启动，注册中心从 `file` 改为 `nacos`

---

## 四、JDK 21 运行时兼容性修复

### 4.1 JWT 密钥长度不足导致登录失败

**现象**：
```
io.jsonwebtoken.security.WeakKeyException: The specified key byte array is 64 bits
which is not secure enough for any JWT HMAC-SHA algorithm.
```

**根因**：JJWT 0.12.x 严格校验 HMAC 密钥长度，HS512 要求密钥 ≥ 64 字节，而 `jwt.secret: mySecret` 仅 8 字节。

**修复**：全部 9 个模块的 `jwt.secret` 替换为 69 字节新密钥

### 4.2 SSOFilter 提取 Token 带前导空格导致验证失败

**现象**：登录成功后立即显示"登录过期"，Token 前多了一个空格。

**根因**：`authHeader.substring(jwtTokenHead.length())` 截取 `"Bearer"` 后得到 `" eyJ..."`，JJWT 0.12.x 严格校验导致解析失败。

**修复**：在所有 `substring(tokenHead.length())` 后添加 `.trim()`，共 12 处：

| 文件 | 说明 |
|------|------|
| 8 个 SSOFilter | 从 Authorization 头提取 token |
| AdminSSOServiceImpl.java:93 | 登出时提取 token |
| UserSSOServiceImpl.java:86 | 登出时提取 token |
| UserInfoServiceImpl.java:30 | 获取用户信息时提取 token |
| UserController.java:90 | 修改密码时提取 token |

### 4.3 Seata + Dubbo 集成 NPE（branchType is null）

**现象**：
```
Cannot invoke "org.apache.seata.core.model.BranchType.name()" because "branchType" is null
```

**根因**：Apache Seata 2.1.0 的已知 bug（GitHub Issue #6815）。`dubbo-filter-seata:1.0.2` 在消费者端无条件调用 `RootContext.getBranchType()`，当不在事务中时返回 null，桥接类 `convertIoSeata()` 直接调用 `branchType.name()` 导致 NPE。

**修复**：
1. 根 pom.xml 的 `seata-spring-boot-starter` 声明中排除 `dubbo-filter-seata:1.0.2`
2. 创建自定义 Dubbo Filter 替代，正确处理 null 情况：
   - `SeataTransactionConsumerFilter`：仅在 xid 非空时传播，branchType 做 null 检查
   - `SeataTransactionProviderFilter`：绑定/解绑 XID 和 branchType
3. 通过 Dubbo SPI 注册（`META-INF/dubbo/org.apache.dubbo.rpc.Filter`）

关键代码对比：
```java
// 旧版（有 bug）- 无条件调用 getBranchType()
BranchType branchType = RootContext.getBranchType(); // NPE when not in tx
if (xid != null) { ... }

// 新版（修复）- 先检查 xid，再安全获取 branchType
String xid = RootContext.getXID();
if (xid != null) {
    invocation.setAttachment("TX_XID", xid);
    BranchType branchType = RootContext.getBranchType();
    if (branchType != null) {
        invocation.setAttachment("TX_BRANCH_TYPE", branchType.name());
    }
}
```

涉及文件：
- `pom.xml`（根）：排除 dubbo-filter-seata
- `mall-common/pom.xml`：新增 dubbo 和 seata-all 的 provided 依赖
- `SeataTransactionConsumerFilter.java`（新增）
- `SeataTransactionProviderFilter.java`（新增）
- `org.apache.dubbo.rpc.Filter`（新增）

---

## 五、修改文件汇总

| 修改类型 | 文件 | 说明 |
|---------|------|------|
| 升级 | 根 pom.xml | JJWT 0.12.6 依赖管理、seata-spring-boot-starter 排除 dubbo-filter-seata |
| 升级 | 3 个模块 pom.xml | mybatis-plus-boot-starter → mybatis-plus-spring-boot3-starter |
| 升级 | 9 个模块 application.yml | JWT secret 密钥替换 |
| 修复 | 8 个 SSOFilter.java | token 提取添加 .trim() |
| 修复 | AdminSSOServiceImpl.java | logout token 提取添加 .trim() |
| 修复 | UserSSOServiceImpl.java | logout token 提取添加 .trim() |
| 修复 | UserInfoServiceImpl.java | 用户信息 token 提取添加 .trim() |
| 修复 | UserController.java (mall-ums) | 修改密码 token 提取添加 .trim() |
| 修复 | 6 个模块 setAllowedOrigins | → setAllowedOriginPatterns |
| 替换 | 4 个 Service 文件 | IdGeneratorUtils → IdWorker.getId() |
| 删除 | 6 个 IdGeneratorUtils.java | 不再需要 |
| 新增 | SeataTransactionConsumerFilter.java | 自定义 Seata Dubbo 消费者端过滤器 |
| 新增 | SeataTransactionProviderFilter.java | 自定义 Seata Dubbo 提供者端过滤器 |
| 新增 | org.apache.dubbo.rpc.Filter | Dubbo SPI 注册文件 |
| 新增 | mall-leaf/DEPRECATED.md | 弃用说明文档 |
