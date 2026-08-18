# 管理员登录 BCrypt 兼容性问题 — 排查与解决

> 日期：2026-08-01
> 服务器：阿里云 ECS（Debian Docker 容器）
> 现象：管理员 admin 登录返回"登录失败！用户名密码错误"，密码确认为 123456
> 本地 Windows 开发环境正常

---

## 一、排查过程

### 1.1 第一次方向：数据源 URL（误判）

SSO 有双数据源：`spring.datasource.admin.*` 和 `spring.datasource.user.*`。

docker-compose 原配置：
```yaml
SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/cs_mall_ams?...
SPRING_DATASOURCE_USER_JDBC_URL: jdbc:mysql://mysql:3306/cs_mall_ums?...
```

Spring Boot 环境变量映射：
- `SPRING_DATASOURCE_USER_JDBC_URL` → `spring.datasource.user.jdbc-url` ✅
- `SPRING_DATASOURCE_URL` → `spring.datasource.url`（主数据源）
- `SPRING_DATASOURCE_ADMIN_JDBC_URL` → `spring.datasource.admin.jdbc-url` **未设置！**

admin 数据源回退读 YML 中的 `${my.server.addr}:3306`，而 `ALIYUN_SERVER_IP=nacos`，实际连接 `nacos:3306` → Connection refused → 500 错误。

**修复**：docker-compose 改 `SPRING_DATASOURCE_URL` → `SPRING_DATASOURCE_ADMIN_JDBC_URL`。

### 1.2 第二次方向：SkyWalking MySQL 插件（误判）

以为是 SkyWalking Agent 拦截 JDBC 连接导致 Druid 连接池初始化超时。禁用后问题依旧。

### 1.3 第三次方向：BCrypt 哈希版本不兼容（根因）

数据库密码哈希：`$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJfYR5sILt8BPLu5mZFBqg5RE0S`（测试数据从 Windows JDK 生成）。

用户名密码正确（日志确认），但 `BCryptPasswordEncoder.matches()` 始终返回 false。

尝试 `{noop}123456` 也失败（说明不是 DelegatingPasswordEncoder）。

最终用服务器容器自带的 Python bcrypt 生成新哈希 `$2b$10$...` → 验证通过。

## 二、根因

**Windows JDK BCrypt `$2a$` 哈希与 Debian JDK 21 BCrypt 实现不兼容。**

Spring Security Crypto 的 BCrypt 实现依赖底层 `javax.crypto` 和 `java.security`，不同 JDK 发行版的内置安全 Provider（如 SunJCE、BC 等）在处理 `$2a$` 版本的 BCrypt 时行为可能不一致。

`$2b$`（2014 年 BCrypt 修订版，修复了 `$2a$` 在处理包含 null 字节的密码时的 bug）在两个平台上行为一致，因此可用。

## 三、解决

在目标平台（Debian Docker）上重新生成密码哈希：

```bash
HASH=$(docker exec csmall-sso python3 -c "
import bcrypt
print(bcrypt.hashpw(b'123456', bcrypt.gensalt(rounds=10)).decode())
")
docker exec csmall-mysql mysql -uroot -proot -e "UPDATE cs_mall_ams.ams_admin SET password='${HASH}' WHERE username='admin'"
docker compose restart mall-sso
```

## 四、经验

1. **BCrypt 哈希应在目标部署平台生成**，不要跨平台复制
2. 测试数据中的 `$2a$` 哈希在 Windows JDK 上可验证，在 Debian JDK 上可能失败
3. 优先使用 `$2b$` 版本的 BCrypt（Spring Security 可通过 `BCryptPasswordEncoder(BCryptVersion.$2B)` 指定）
4. `DelegatingPasswordEncoder` 比裸 `BCryptPasswordEncoder` 更灵活，可以通过 `{bcrypt}`/`{noop}` 等前缀切换编码器
