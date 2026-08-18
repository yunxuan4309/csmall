# 项目引入问题汇总

> 时间：2026-04 ~ 2026-05
> 内容：项目初始导入及 Maven 多模块依赖问题

---

## 一、Maven 父子模块依赖问题

### 1.1 问题描述

- 根 pom.xml 与子模块的 `groupId` 不一致
- 子模块缺少 `<relativePath>` 配置
- 部分依赖版本缺失
- webapi 模块依赖 service 模块版本为 `1.0.0`，但 service 模块继承父模块版本 `0.0.1-SNAPSHOT`
- 子模块直接声明了具体版本号，绕过了父 POM 的统一管理

### 1.2 解决方案

- 统一 groupId 为 `com.cooxiao.mall`
- 为所有子模块添加 `<relativePath>../pom.xml</relativePath>`
- 在根 pom.xml 补充依赖版本：`seata-spring-boot-starter: 1.7.1`、`spring-cloud-starter-dubbo: 2021.1`
- 统一所有 service 模块版本为 `1.0.0`
- 子模块引用依赖时不再指定 version 标签，继承父 POM 定义

---

## 二、Spring Boot 3.x 兼容性问题（初始导入阶段）

### 2.1 Java EE → Jakarta EE 包名变更

| 旧版 | 新版 |
|------|------|
| `javax.servlet.*` | `jakarta.servlet.*` |
| `javax.annotation.PostConstruct` | `jakarta.annotation.PostConstruct` |
| `javax.validation.*` | `jakarta.validation.*` |

涉及 8 个模块的 SSOFilter、MyAccessDeniedHandler、MyAuthenticationEntryPoint、Controller、AutoUpdateTimeInterceptorConfiguration、LoginUtils 等文件。

### 2.2 Spring Security 6.x 配置问题

`WebSecurityConfigurerAdapter` 在 Spring Security 6.x 中已被移除，需重写为 `SecurityFilterChain` Bean。

### 2.3 包路径变更

`mall-common` 模块的类从 `com.cooxiao.mall.common.pojo.domain` 迁移到 `com.cooxiao.mall.common.domain`。

---

## 三、库模块打包问题

### 3.1 问题描述

库模块继承父 POM 的 `spring-boot-maven-plugin`，执行 repackage 时找不到 main class。

### 3.2 解决方案

为以下库模块添加跳过 repackage 配置：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <configuration>
                <skip>true</skip>
            </configuration>
        </plugin>
    </plugins>
</build>
```

涉及模块：mall-common、mall-pojo、mall-ams-service、mall-front-service、mall-order-service、mall-product-service、mall-search-service、mall-seckill-service、mall-ums-service

---

## 四、其他小问题

### 4.1 工具类包迁移

```java
// 旧
import org.apache.commons.lang.StringUtils;
import com.alibaba.nacos.client.naming.utils.RandomUtils;

// 新
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.RandomUtils;
```

### 4.2 RandomUtils 方法签名变更

```java
// 旧
RandomUtils.nextInt(10000)

// 新
RandomUtils.nextInt(0, 10000)
```

> 注：后续第二轮更迭中，`RandomUtils` 进一步替换为 `ThreadLocalRandom.current().nextInt(10000)`。

### 4.3 leaf-server 模块问题

`maven-dependency-plugin` 的 `copy-dependencies` 在 `compile` 阶段执行失败，将执行阶段从 `compile` 改为 `package`。

### 4.4 删除过时代码

删除使用旧版 MyBatis Plus API 的 `CodeGenerator.java` 文件。

---

## 五、预防措施

1. 统一通过父 POM 的 `dependencyManagement` 进行版本管理
2. 子模块引用依赖时不指定 version 标签
3. 定期运行 `mvn versions:display-dependency-updates` 检查过期依赖
4. 建立团队协作规范，版本变更须同步更新所有相关模块
