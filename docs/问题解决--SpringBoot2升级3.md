# Spring Boot 2.x → 3.x 技术栈迁移问题汇总

> 时间：2026-05-08 ~ 2026-05-09
> 内容：Spring Boot 2.x → 3.5.7 全链路迁移（含 Spring Security 6.x、Jakarta EE 9+、MyBatis-Plus 4.x 等）

---

## 一、Spring Security 6.x 兼容性问题

### 1.1 Servlet API 包名变更 (javax → jakarta)

**问题描述**：
```
Cannot resolve symbol 'ServletException'
Cannot resolve symbol 'HttpServletRequest'
Cannot resolve symbol 'HttpServletResponse'
```

**根本原因**：
Spring Boot 3.x 基于 Jakarta EE 9+，所有 `javax.*` 包名重命名为 `jakarta.*`。

**解决方案**：
```java
// 修复前
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

// 修复后
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
```

**修改文件列表**：
- `mall-ams`: SSOFilter, MyAccessDeniedHandler, MyAuthenticationEntryPoint, ResourceWebSecurityConfiguration
- `mall-front`: SSOFilter, MyAccessDeniedHandler, MyAuthenticationEntryPoint, ResourceWebSecurityConfiguration
- `mall-order`: SSOFilter, MyAccessDeniedHandler, MyAuthenticationEntryPoint, ResourceWebSecurityConfiguration
- `mall-ums`: SSOFilter, MyAccessDeniedHandler, MyAuthenticationEntryPoint, ResourceWebSecurityConfiguration, LoginUtils, UserController
- `mall-product`: SSOFilter, MyAccessDeniedHandler, MyAuthenticationEntryPoint, ResourceWebSecurityConfiguration
- `mall-search`: SSOFilter, MyAccessDeniedHandler, MyAuthenticationEntryPoint, ResourceWebSecurityConfiguration
- `mall-seckill`: SSOFilter, MyAccessDeniedHandler, MyAuthenticationEntryPoint, ResourceWebSecurityConfiguration
- `mall-sso`: SSOFilter, MyAccessDeniedHandler, MyAuthenticationEntryPoint, SSOWebSecurityConfig, LoginUtils, AdminSSOController, UserSSOController

---

### 1.2 WebSecurityConfigurerAdapter 已移除

**问题描述**：
```
Cannot resolve class 'WebSecurityConfigurerAdapter'
```

**根本原因**：
Spring Security 6.0 完全移除了 `WebSecurityConfigurerAdapter`，推荐使用组件式配置。

**解决方案**：
```java
// 修复前
@Configuration
@EnableWebSecurity
public class ResourceWebSecurityConfiguration extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception { ... }
}

// 修复后
@Configuration
@EnableWebSecurity
public class ResourceWebSecurityConfiguration {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // ...
        return http.build();
    }
}
```

---

### 1.3 Spring Security 配置 API 变更 (Lambda DSL)

**API 变更对照表**：

| 旧 API (Spring Security 5.x) | 新 API (Spring Security 6.x) |
|---|---|
| `http.csrf().disable()` | `http.csrf(csrf -> csrf.disable())` |
| `http.cors().configurationSource(...)` | `http.cors(cors -> cors.configurationSource(...))` |
| `http.authorizeRequests()` | `http.authorizeHttpRequests()` |
| `http.authorizeRequests().antMatchers(...)` | `http.authorizeHttpRequests().requestMatchers(...)` |
| `http.sessionManagement().sessionCreationPolicy(...)` | `http.sessionManagement(session -> session.sessionCreationPolicy(...))` |

---

### 1.4 @EnableGlobalMethodSecurity 已弃用

```java
// 修复前
@EnableGlobalMethodSecurity(prePostEnabled = true)

// 修复后
@EnableMethodSecurity(prePostEnabled = true)
```

---

### 1.5 Security 配置类重复定义问题

**问题描述**：部分模块的 `ResourceWebSecurityConfiguration.java` 同时包含新版 `SecurityFilterChain` Bean 和旧版 `configure(HttpSecurity)` 方法，导致编译错误。

**解决方案**：删除旧版残留代码，统一使用 Spring Security 6.x 的配置方式。

**修改文件列表**：mall-front, mall-ams, mall-order, mall-ums, mall-product, mall-search, mall-seckill, mall-sso

---

### 1.6 CORS API 变更

```java
// 修复前（已弃用）
corsConfiguration.addAllowedOrigin("*");

// 修复后
configuration.setAllowedOrigins(Arrays.asList("*"));
// 或（Spring Security 6.x 推荐）
configuration.setAllowedOriginPatterns(Arrays.asList("*"));
```

---

## 二、MyBatis-Plus 兼容性问题

### 2.1 缺少 mybatis-plus-boot-starter 依赖

**问题描述**：`mall-ams-webapi` 模块无法使用 `BaseMapper` 等类。

**解决方案**：在 `mall-ams-webapi/pom.xml` 中添加 `mybatis-plus-boot-starter` 依赖，同时在 `mall-pojo/pom.xml` 添加注解依赖。

---

### 2.2 mall-pojo Model 类缺少 MyBatis-Plus 注解

**问题描述**：Mapper 接口继承 `BaseMapper<T>` 但 Model 类没有 `@TableName` 等注解。

**解决方案**：为所有实体类添加 `@TableName` 和 `@TableId` 注解，共修改 25+ 个文件（详见商品模块修改记录）。

---

### 2.3 MyBatis-Plus 分页类型不兼容

**问题描述**：
```
不兼容的类型: IPage<Model>无法转换为IPage<VO>
```

**解决方案**：使用 stream + `convertToVO` 模式将 `IPage<Model>` 转换为 `IPage<VO>`：

```java
IPage<Model> result = mapper.selectPage(pageParam, wrapper);
List<VO> voList = result.getRecords().stream()
        .map(this::convertToVO)
        .collect(Collectors.toList());
IPage<VO> pageVO = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
pageVO.setRecords(voList);
return JsonPage.restPage(pageVO);
```

涉及文件：`AlbumServiceImpl`, `AttributeTemplateServiceImpl`, `BrandServiceImpl`, `CategoryServiceImpl`, `PictureServiceImpl`, `SkuServiceImpl`, `ForFrontSpuServiceImpl`

---

### 2.4 Mapper 方法缺失

**问题描述**：`BrandMapper` 缺少 `updateFullInfoById()` 方法声明。

**解决方案**：在 Mapper 接口中添加方法声明，或使用 `LambdaQueryWrapper.selectPage()` 替代自定义分页方法。

---

### 2.5 update() 方法不存在

**问题描述**：`BaseMapper` 没有 `update(T entity)` 方法。

**解决方案**：`mapper.update(entity)` → `mapper.updateById(entity)`

涉及文件：`AlbumServiceImpl`, `AttributeServiceImpl`, `AttributeTemplateServiceImpl`

---

### 2.6 类型转换兼容性

**问题描述**：`selectCount()` 返回 `long`，不是 `int`。

**解决方案**：`int count` → `long count`（`DeliveryAddressServiceImpl.java`）

---

## 三、MyBatis Plus Generator API 变更 (3.x → 4.x)

### 3.1 naming() 和 columnNaming() 方法位置变更

```java
// 修复前（3.x 写法）
builder.naming(NamingStrategy.underline_to_camel)
       .columnNaming(NamingStrategy.underline_to_camel)
       .addInclude(...)
       .entityBuilder() ...

// 修复后（4.x 写法）
builder.addInclude(...)
       .entityBuilder()
           .naming(NamingStrategy.underline_to_camel)
           .columnNaming(NamingStrategy.underline_to_camel) ...
```

### 3.2 formatName() 方法已移除

移除 `formatName()` 调用，包路径通过 `packageConfig` 配置。

### 3.3 AutoGenerator → FastAutoGenerator

`AutoGenerator` 构造函数变为私有，使用 `FastAutoGenerator.create()` 替代。

---

## 四、类导入路径错误

### 4.1 CsmallAuthenticationInfo 导入路径错误

```java
// 修复前
import com.cooxiao.mall.common.pojo.domain.CsmallAuthenticationInfo;

// 修复后
import com.cooxiao.mall.common.domain.CsmallAuthenticationInfo;
```

修改文件：8 个模块的 SSOFilter、SecurityContextUtils、ServiceImpl 等共 12 个文件。

---

## 五、其他兼容性问题

### 5.1 Apache Commons Lang 包名变更

```java
// 修复前
import org.apache.commons.lang.StringUtils;

// 修复后
import org.apache.commons.lang3.StringUtils;
```

### 5.2 Nacos RandomUtils 替换

```java
// 修复前
import com.alibaba.nacos.client.naming.utils.RandomUtils;
RandomUtils.nextInt(10000)

// 修复后
import java.util.concurrent.ThreadLocalRandom;
ThreadLocalRandom.current().nextInt(10000)
```

涉及文件：`SeckillSkuServiceImpl`, `SeckillInitialJob`, `SeckillSpuServiceImpl`

### 5.3 MediaType.APPLICATION_JSON_UTF8 已废弃

RFC 8259 规定 JSON 默认 UTF-8，`JacksonConfig.java` 中移除 `MediaType.APPLICATION_JSON_UTF8`。

### 5.4 JJWT 版本号硬编码

子模块中 JJWT 依赖硬编码 `0.9.0` 版本号，与父 POM 不一致。移除硬编码版本号，继承父 POM 版本管理。

---

## 六、Gateway + Knife4j 循环依赖

### 6.1 问题描述

```
The dependencies of some of the beans form a cycle:
knife4jSwaggerProvider → cachedCompositeRouteLocator → routeDefinitionRouteLocator →
modifyRequestBodyGatewayFilterFactory → EnableWebFluxConfiguration → knife4jSwaggerProvider
```

### 6.2 根本原因

`Knife4jSwaggerProvider` 使用 `@Autowired` 直接注入 `RouteLocator`，形成循环依赖。

### 6.3 解决方案

使用 `ObjectProvider<RouteLocator>` 构造器注入替代字段注入：

```java
// 修复前
@Autowired
private RouteLocator routeLocator;

// 修复后
private final ObjectProvider<RouteLocator> routeLocatorProvider;

public Knife4jSwaggerProvider(ObjectProvider<RouteLocator> routeLocatorProvider) {
    this.routeLocatorProvider = routeLocatorProvider;
}
```

获取路由时添加空值检查：`routeLocatorProvider.getIfAvailable()`

修改文件：`mall-gateway-server/Knife4jSwaggerProvider.java`

---

## 七、问题类型统计

| 问题类型 | 涉及模块数 | 修复文件数 |
|----------|-----------|-----------|
| javax → jakarta 迁移 | 8 | 25+ |
| Security 配置重构 | 8 | 8 |
| MyBatis-Plus 分页类型转换 | 1 | 7 |
| Mapper 方法缺失/替换 | 1 | 4 |
| 类导入路径错误 | 8 | 12 |
| Gateway 循环依赖 | 1 | 1 |
| 第三方库 API 变更 | 3 | 5 |
| MyBatis Generator API | 1 | 1 |

---

## 八、关键版本信息

| 组件 | 版本 |
|---|---|
| Spring Boot | 3.5.7 |
| Spring Security | 6.5.x |
| MyBatis Plus | 3.5.7 |
| MyBatis Plus Generator | 3.5.7 |
| Java | 21 |
| Jakarta EE | 9+ |
