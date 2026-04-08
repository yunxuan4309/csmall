# 登录功能 Bug 修复总结

## 问题概述

在测试管理员登录功能时，遇到了多个技术问题，经过排查和修复后，登录功能现已正常工作。

---

## 一、JSON 参数绑定失败

### 问题描述
Postman 发送登录请求时，有时显示"用户名不许为空"，有时显示"密码不能为空"。

### 根本原因
Controller 的 `doLogin` 方法缺少 `@RequestBody` 注解。

### 解决方案
为 `AdminLoginDTO` 和 `UserLoginDTO` 参数添加 `@RequestBody` 注解：

```java
// 修复前
public JsonResult<TokenVO> doLogin(@Valid AdminLoginDTO adminLoginDTO, HttpServletRequest request)

// 修复后
public JsonResult<TokenVO> doLogin(@Valid @RequestBody AdminLoginDTO adminLoginDTO, HttpServletRequest request)
```

### 涉及文件
- `mall-sso/src/main/java/com/cooxiao/mall/sso/controller/AdminSSOController.java`
- `mall-sso/src/main/java/com/cooxiao/mall/sso/controller/UserSSOController.java`

---

## 二、CORS 跨域配置问题

### 问题描述
跨域请求时可能出现问题。

### 根本原因
使用了已废弃的 `setAllowedOrigins("*")`，且没有设置 `allowCredentials`。

### 解决方案
修改 CORS 配置：

```java
// 修复前
configuration.setAllowedOrigins(Arrays.asList("*"));

// 修复后
configuration.setAllowedOriginPatterns(Arrays.asList("*"));
configuration.setAllowCredentials(true);
```

### 涉及文件
- `mall-sso/src/main/java/com/cooxiao/mall/sso/security/config/SSOWebSecurityConfig.java`

---

## 三、数据库密码哈希不正确

### 问题描述
密码验证失败，错误信息："登录失败！用户名密码错误"。

### 根本原因
数据库中的 BCrypt 密码哈希 `$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJfYR5sILt8BPLu5mZFBqg5RE0S` 不是 123456 的正确哈希。

### 解决方案
重新生成正确的 BCrypt 哈希并更新数据库：

```bash
# 通过接口生成哈希
GET http://localhost:10002/admin/sso/hash?password=123456

# 更新数据库
UPDATE ams_admin SET password = '新哈希值' WHERE username = 'admin';
```

### 涉及 SQL
```sql
USE cs_mall_ams;
UPDATE ams_admin SET password = '$2a$10$ec5yFOLAmmIn7oxViycEw.36u3wBCSnuhjexFAP7wj1yvQLCIw7sK' WHERE username = 'admin';
UPDATE ams_admin SET password = '$2a$10$ec5yFOLAmmIn7oxViycEw.36u3wBCSnuhjexFAP7wj1yvQLCIw7sK' WHERE username = 'liucs';
UPDATE ams_admin SET password = '$2a$10$ec5yFOLAmmIn7oxViycEw.36u3wBCSnuhjexFAP7wj1yvQLCIw7sK' WHERE username = 'wangkj';
```

---

## 四、JWT Token 生成失败

### 问题描述
错误信息：`java.lang.NoClassDefFoundError: javax/xml/bind/DatatypeConverter`

### 根本原因
Java 9+ 移除了 `javax.xml.bind` 包，但 JJWT 0.9.x 版本依赖此包。

### 解决方案
添加 JAXB API 依赖：

```xml
<!-- pom.xml -->
<dependency>
    <groupId>javax.xml.bind</groupId>
    <artifactId>jaxb-api</artifactId>
    <version>2.3.1</version>
</dependency>
```

### 涉及文件
- `pom.xml` - 添加依赖管理
- `mall-common/pom.xml` - 添加实际依赖

---

## 五、登录日志表字段不匹配

### 问题描述
错误信息：`Unknown column 'nickname' in 'field list'`

### 根本原因
Mapper XML 中使用了数据库表不存在的字段。

### 解决方案
修改 `AdminLoginLogMapper.xml`：

```xml
<!-- 修复前 -->
insert into ams_login_log (
    admin_id, username, nickname, ip_address,
    user_agent, login_time, gmt_create, gmt_modified
) values (..., #{nickname}, ..., #{gmtModified})

<!-- 修复后 -->
insert into ams_login_log (
    admin_id, username, ip_address,
    user_agent, login_time, gmt_create
) values (..., #{ip}, ...)
```

### 涉及文件
- `mall-sso/src/main/resources/mapper/admin/AdminLoginLogMapper.xml`

---

## 六、主键自增配置缺失

### 问题描述
错误信息：`Field 'id' doesn't have a default value`

### 根本原因
MyBatis Insert 语句缺少主键自增配置。

### 解决方案
在 Mapper XML 中添加 `useGeneratedKeys` 和 `keyProperty` 属性：

```xml
<insert id="insertAdminLoginLog" useGeneratedKeys="true" keyProperty="id">
```

### 涉及文件
- `mall-sso/src/main/resources/mapper/admin/AdminLoginLogMapper.xml`

---

## 修复的文件清单

| 模块 | 文件路径 | 修复内容 |
|------|----------|----------|
| mall-sso | `controller/AdminSSOController.java` | 添加 @RequestBody 注解、PasswordEncoder |
| mall-sso | `controller/UserSSOController.java` | 添加 @RequestBody 注解 |
| mall-sso | `security/config/SSOWebSecurityConfig.java` | 修复 CORS 配置 |
| mall-sso | `security/service/admin/impl/AdminSSOServiceImpl.java` | 移除不存在的字段 |
| mall-sso | `resources/mapper/admin/AdminLoginLogMapper.xml` | 修复字段映射、添加自增 |
| mall-sso | `config/JacksonConfig.java` | 新增 Jackson 配置 |
| mall-sso | `MallPassportWebApiApplication.java` | 排除不需要的自动配置 |
| mall-sso | `src/main/resources/application.yml` | 添加 Jackson 配置 |
| mall-common | `pom.xml` | 添加 JAXB API 依赖 |
| 根目录 | `pom.xml` | 添加 JAXB API 版本管理 |

---

## 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | 123456 | 超级管理员 |
| liucs | 123456 | 运营管理员 |
| wangkj | 123456 | 客服管理员 |

---

## 后续建议

1. 定期检查数据库字段与 Mapper XML 的映射是否一致
2. 使用 Flyway 或 Liquibase 管理数据库迁移
3. 添加单元测试覆盖登录流程
4. 清理调试用的临时接口（如 `/admin/sso/debug` 和 `/admin/sso/hash`）
