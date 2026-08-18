# 问题排查与解决方案记录

> 本文档记录 Android 端"登录 → 拍照上传 → 历史记录"功能实现过程中遇到的问题及解决方案。

---

## 目录

- [一、503 Service Unavailable — Gateway 路由问题](#一503-service-unavailable--gateway-路由问题)
- [二、编译错误 — 缺少依赖和语法问题](#二编译错误--缺少依赖和语法问题)
- [三、Nginx 配置问题](#三nginx-配置问题)
- [四、SQL 执行失败 — 密码特殊字符问题](#四sql-执行失败--密码特殊字符问题)

---

## 一、503 Service Unavailable — Gateway 路由问题

### 现象

Android App 拍照上传时返回 `503 Service Unavailable`，但 mall-resource 服务本身正常运行（`systemctl status mall-resource` 显示 active）。

### 排查过程

1. **检查 mall-resource 进程**：运行正常，占用 211.6M 内存
2. **检查 Nacos 注册中心**：`curl /nacos/v1/ns/instance/list?serviceName=mall-resource` 返回 `"hosts":[]`，说明 mall-resource **未注册到 Nacos**
3. **检查 mall-resource 日志**：没有任何 Nacos 相关输出，确认 pom.xml 中没有 Nacos 发现依赖
4. **检查 Gateway 配置**：`application-prod.yml` 中 mall-resource 路由配置为 `uri: lb://mall-resource`，这要求服务必须在 Nacos 中注册

### 根因

mall-resource 是独立服务，pom.xml **没有**引入 `spring-cloud-starter-alibaba-nacos-discovery` 依赖，不会向 Nacos 注册。而 Gateway 使用 `lb://`（LoadBalance）前缀路由，依赖 Nacos 服务发现，找不到目标地址导致 503。

### 解决方案

将 Gateway 中 mall-resource 的路由从 `lb://` 改为直接地址：

```yaml
# 修改前
uri: lb://mall-resource

# 修改后
uri: http://127.0.0.1:9060
```

**涉及文件**：`mall-gateway-server/src/main/resources/application-prod.yml`

**部署步骤**：重新打包 gateway → 上传 JAR 到服务器 → `sudo systemctl restart mall-gateway`

### 经验总结

- 只有注册到 Nacos 的服务才能使用 `lb://` 路由
- 独立服务（无 Nacos 依赖）必须使用 `http://ip:port` 直连路由
- 排查思路：先确认目标服务是否在 Nacos 注册列表中，再检查 Gateway 路由配置

---

## 二、编译错误 — 缺少依赖和语法问题

### 2.1 缺少 MyBatis-Plus 相关依赖

#### 现象

编译 mall-resource 时找不到 `BaseMapper`、`IPage` 等 MyBatis-Plus 类。

#### 根因

mall-resource 原为纯文件上传服务，pom.xml 中没有 MyBatis-Plus 和 MySQL 相关依赖。

#### 解决方案

在 `mall-resource/pom.xml` 中添加以下依赖：

- `mysql-connector-java` — MySQL 驱动
- `druid` — 数据源连接池
- `mybatis-plus-spring-boot3-starter` — MyBatis-Plus 核心
- `mybatis-plus-jsqlparser` — MyBatis-Plus 分页解析器

### 2.2 缺少 jsqlparser 导致分页拦截器失败

#### 现象

添加 MyBatis-Plus 后编译成功，但启动时 `PaginationInnerInterceptor` 报错。

#### 根因

`mybatis-plus-spring-boot3-starter` 不包含 jsqlparser，而 `PaginationInnerInterceptor` 需要它解析 SQL。

#### 解决方案

额外添加 `mybatis-plus-jsqlparser` 依赖。

### 2.3 类结尾缺少花括号

#### 现象

`FileUploadController.java` 编译报错 `expected '}'`。

#### 根因

使用 Edit 工具替换代码时，替换文本末尾缺少了类结尾的 `}`。

#### 解决方案

补上缺失的 `}`。

### 2.4 int 不能转为 Long

#### 现象

`UserUploadController.java` 编译报类型不匹配。

#### 根因

`setTotal((int) recordPage.getTotal())` 中 `(int)` 无法自动装箱为 `Long`。

#### 解决方案

改用 `JsonPage.restPage(recordPage)` 统一转换。

---

## 三、Nginx 配置问题

### 现象

上传请求返回 405 错误。

### 根因

Nginx 配置中没有 `/upload/` 路径的反向代理规则，上传请求未被转发到 Gateway。

### 解决方案

在 Nginx 配置中添加 `/upload/` location 块：

```nginx
location /upload/ {
    proxy_pass http://127.0.0.1:10087;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    client_max_body_size 10m;
}
```

### 遇到的 Nginx 语法错误

| 错误 | 原因 | 解决方法 |
|------|------|----------|
| `location "/upload/" is outside location "/ums/"` | 在已有的 location 块内嵌套了新 location | 删除已有大括号，并列书写 |
| `unknown directive "index.html"` | `index` 指令后缺少第二个参数 | 改为 `index index.html;` |
| `try_files $uri $uri/ /index.html;ndex index.html;` | 粘贴时夹带了多余字符 | 完整重写该行 |

---

## 四、SQL 执行失败 — 密码特殊字符问题

### 现象

执行 SQL 文件时报错：`-bash: !Root: event not found`

### 根因

MySQL 密码 `Csmall2026!Root` 包含 `!` 字符，在 Bash 中被解释为历史扩展。

### 解决方案

密码用单引号包裹，防止 Bash 解释：

```bash
mysql -u root -p'Csmall2026!Root' < /tmp/08-resource-upload-record.sql
```

---

## 五、涉及文件清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `database/08-resource-upload-record.sql` | 创建 cs_mall_resource 库和 res_upload_record 表 |
| `mall-resource/.../entity/UploadRecord.java` | MyBatis-Plus 实体类 |
| `mall-resource/.../mapper/UploadRecordMapper.java` | MyBatis-Plus Mapper |
| `mall-resource/.../service/IUploadRecordService.java` | 上传记录服务接口 |
| `mall-resource/.../service/impl/UploadRecordServiceImpl.java` | 上传记录服务实现 |
| `mall-resource/.../controller/UserUploadController.java` | 用户上传记录查询接口 |
| `mall-resource/.../config/MybatisPlusConfig.java` | MyBatis-Plus 配置（MapperScan + 分页拦截器） |
| `mall-resource/.../vo/UploadRecordVO.java` | 上传记录 VO |
| `docs/android-dev-guide.md` | Android 完整开发指南 |
| `docs/android-login-camera-guide.md` | Android 登录与拍照上传指南 |

### 修改文件

| 文件 | 修改内容 |
|------|----------|
| `mall-resource/pom.xml` | 添加 MySQL/MyBatis-Plus/Druid 依赖 |
| `mall-resource/application.yml` | 添加 MyBatis-Plus 配置 |
| `mall-resource/application-dev.yml` | 添加开发环境数据源 |
| `mall-resource/application-test.yml` | 添加测试环境数据源 |
| `mall-resource/application-prod.yml` | 添加生产环境数据源配置 |
| `mall-resource/FileUploadController.java` | 添加上传记录保存逻辑、JWT 用户提取 |
| `mall-gateway-server/application-prod.yml` | 修复 mall-resource 路由 `lb://` → `http://` |
| `PROJECT_CONTEXT.md` | 更新 mall-resource 数据库信息 |
| `database/README.md` | 添加新 SQL 文件说明 |
| `docs/平台基础信息.md` | 添加 cs_mall_resource 数据库记录 |
| `模块启动顺序.md` | 更新 mall-resource 依赖信息 |
