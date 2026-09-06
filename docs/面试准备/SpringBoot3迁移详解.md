# Spring Boot 3 迁移详解（javax→jakarta / Security 6 / 连锁兼容）

> **用途**：10-问题解决 Q6 的详情文档（面试完整版）——"javax→jakarta 生态迁移遇到什么问题、怎么解决、迁移了哪些内容"。
> **关联**：[[10-问题解决]] Q6、原始记录 [[问题解决--SpringBoot2升级3]]、[[问题解决--JDK21与Seata迁移]]

---

## 一、javax → jakarta 迁移（第一关：编译报错）

### 现象
```
Cannot resolve symbol 'ServletException'
Cannot resolve symbol 'HttpServletRequest'
（全项目编译炸，8 个模块 25+ 文件）
```

### 根因（不是 Spring 自己改的）
Oracle 把 Java EE 捐给 Eclipse 基金会改名 **Jakarta EE**（2017）——javax.* 命名空间留给 Java SE 扩展，新版本 API 用 jakarta.*。**Spring Boot 3 / Spring Framework 6 基于 Jakarta EE 9+** → 全项目坐标迁移。

### 具体迁移内容（三类）

| 包 | 旧 javax.* | 新 jakarta.* | 项目角色 |
|----|-----------|-------------|---------|
| servlet | javax.servlet.Filter/FilterChain/ServletException/http.* | jakarta.servlet.* | **SSOFilter（8 模块）**、MyAccessDeniedHandler、MyAuthenticationEntryPoint |
| validation | javax.validation.@NotNull/@Pattern/@Valid | jakarta.validation.* | 所有 DTO 校验注解 |
| annotation | javax.annotation.@PostConstruct/@PreDestroy | jakarta.annotation.* | 初始化/销毁回调 |

### 解决方式
① IDE 全局替换 javax. → jakarta.（8 模块）② 编译报错驱动手动兜底（25+ 文件）③ 涉及每模块 SSOFilter + security handler + 工具类

---

## 二、连带大坑（编译错只是第一关）

### A. Spring Security 6（范式级）

| 旧 5.x | 新 6.x |
|--------|--------|
| extends WebSecurityConfigurerAdapter | **SecurityFilterChain Bean**（组合式） |
| http.csrf().disable() | http.csrf(csrf -> csrf.disable())（Lambda DSL） |
| authorizeRequests().antMatchers() | authorizeHttpRequests().requestMatchers() |
| @EnableGlobalMethodSecurity | @EnableMethodSecurity |
| setAllowedOrigins("*") | setAllowedOriginPatterns("*")（6 模块 CORS） |

真坑：配置类新旧混留编译错；CORS 运行时才炸。

### B. 第三方库连锁（最大工作量）

| 库 | 问题 | 解决 |
|----|------|------|
| MyBatis-Plus | boot-starter 不兼容 Boot3 | mybatis-plus-spring-boot3-starter + mybatis-plus-jsqlparser（3.5.9 分页独立） |
| | IPage\<Model\>→IPage\<VO\> 转换 | stream + convertToVO + new Page（7 Service） |
| | update(entity)/selectCount int | updateById / long |
| | AutoGenerator | FastAutoGenerator |
| | @TableName 缺失（Spu→pms_spu） | 25 实体补注解 |
| | is_ 字段（deleted→is_deleted） | @TableField |
| Knife4j(Gateway) | 注入 RouteLocator 循环依赖 | ObjectProvider 构造器注入 + getIfAvailable |
| JJWT 0.9→0.12.6 | javax.xml.bind JDK11+ 移除；HS512 密钥 <64B WeakKeyException；token 前导空格 | 三依赖拆分 + 新 API + 9 模块 69B 密钥 + **12 处 .trim()**（Bearer 后空格→"登录过期"） |
| commons-lang | lang → lang3 | import |
| Nacos RandomUtils | 不可用 | ThreadLocalRandom（秒杀 3 文件） |
| MediaType.APPLICATION_JSON_UTF8 | 废弃 | APPLICATION_JSON（RFC 8259） |
| Long 精度（雪花 19 位） | JS 丢精度 | JacksonConfiguration 全局 Long→String |
| CsmallAuthenticationInfo | import 路径错 | 8 模块 12 文件 |

### C. 同期 JDK 17→21（顺带）

```
Maven "不支持发行版本 21" → IDEA Runner JDK 改 21
虚拟线程:10 个 test yml 加 spring.threads.virtual.enabled=true（生产未开!）
Seata 1.x → Apache 2.1.0:io.seata → org.apache.seata
  + dubbo-filter-seata NPE bug → 自定义 Consumer/Provider Filter（SPI）
mall-leaf 弃用(JDK8/死代码)→ MyBatis-Plus IdWorker.getId()
```

---

## 三、JDK 21 虚拟线程（追问点）

### 是什么
- 虚拟线程（JEP 444，JDK21 正式）：**轻量级线程，百万级**，JVM 调度而非 OS 线程 1:1
- 解决痛点：平台线程贵（~1MB 栈/个），**IO 密集场景**（Web 请求/DB/RPC 等待）线程池大小难调——阻塞 IO 时虚拟线程自动让出载体线程
- 对比：WebFlux 响应式（异步代码复杂）vs 虚拟线程（**同步代码 + 虚拟线程**，简单）

### 项目现状（实证）
```
10 个 application-test.yml:spring.threads.virtual.enabled=true ✅ test 开
application-prod.yml:无该配置 ❌ 生产未开
```
- 诚实点：test 开了验证能力；生产保守用平台线程（虚拟线程与 Dubbo/中间件线程模型兼容要验证，Boot 3.2 只覆盖 web 容器）

### 面试怎么讲
> "JDK21 虚拟线程我配置过（test 环境 spring.threads.virtual.enabled=true）——它解决平台线程贵 + IO 密集场景线程池难调的问题，阻塞时自动让出载体线程，能用同步代码拿到接近响应式的并发。生产没开是保守选择：虚拟线程与 Dubbo/Seata 的线程模型兼容需要验证，Boot 3.2 的虚拟线程只覆盖 Web 容器层——面试讲清楚'开了验证过、生产保守、知道边界'"

### 社区/大厂现状（2026 查证，为什么不是"开个开关就上"）
- **大厂在用但谨慎**：阿里（生产安全迁移虚拟线程，强调灰度+兜底）、Netflix（两年生产复盘，踩过 deadlock）、美团/字节（配兜底检测脚本）——没有任何一家无脑开
- **框架适配进行时**：Apache Seata 官方 2026-03 wiki 还在"统一线程池适配虚拟线程"、issue #6971 把 synchronized 换 ReentrantLock（防 **pinning**——虚拟线程在 synchronized 块阻塞会钉住载体线程退化成平台线程）；社区有"Java21 + Dubbo 虚拟线程失效"实战坑；JDK 25 还在修 pinning 边角
- **判断模型（不是"官方没宣布就不用"）**：开虚拟线程 = 把整条链路（Tomcat→业务→RPC→DB）的线程都换掉，每一环要不吃 synchronized、不做平台线程假设、上下文传递正确——看**链路适配度 + 收益兑现**：我们 CPU<5% 无收益 + Boot 3.2.5 只覆盖 Tomcat 层 + Dubbo/Seata 适配中 → 收益 0 风险不小，保守正确；等流量起来 + Boot 3.3+/框架适配完，它是第一个要开的优化
- 参考：Seata wiki/issue #6971、阿里迁移实践、Netflix 复盘、Java21+Dubbo 三连坑文章

---

## 四、方法论（升级框架的正确姿势）

> ① **先列受影响面清单**（javax→jakarta + Security6 波及 8 模块 25+ 文件，提前梳理避免遗漏）
> ② **编译错误只是第一关**（JJWT 密钥长度/CORS/token 空格编译期不报、运行时才爆 → 全功能回归）
> ③ **框架升级伴随第三方库连锁升级**（MyBatis-Plus/JJWT/Nacos 客户端同步适配）
> ④ **写"旧→新"对照表**（全局替换 + 复查）

---

## 五、面试话术（完整版）

> "javax→jakarta 不是 Spring 自创，是 Java EE 捐给 Eclipse 改名 Jakarta EE 后的坐标迁移——Boot 3 基于 Jakarta EE 9+，servlet/validation/annotation 全换，8 模块 25+ 文件。但真正的坑在连带：Security 6 从 WebSecurityConfigurerAdapter 变 SecurityFilterChain + Lambda DSL；MyBatis-Plus 换 spring-boot3 starter + 分页拆 jsqlparser + @TableName 补 25 实体；JJWT 0.12 严格校验密钥导致 WeakKeyException（换 69B 密钥）+ token 前导空格导致登录过期（12 处 trim）；Knife4j 循环依赖用 ObjectProvider 解。方法论：先列受影响面、编译错只是第一关（运行时才爆的要全回归）、第三方库连锁升级、写旧→新对照表。JDK21 虚拟线程我在 test 开了验证，生产保守没开——Boot 3.2 虚拟线程只覆盖 Web 层，Dubbo 兼容要验证。"

---

## 六、版本现状与演进（2026 综合）

### 项目版本矩阵（根 pom 三重实证）
| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | **3.2.5** | 根 pom + git 历史 + 服务器 JAR 三重实证 |
| Spring Cloud | 2023.0.3 | 配套 3.2.5（阿里云镜像可用） |
| SCA | 2023.0.1.2 | 同上 |
| Java | 21 | |
| MyBatis-Plus | 3.5.9 | + jsqlparser |

**落地历史**：迁移过程目标 3.5.7 → 第四轮迁移（git commit 5ff0bd2）因阿里云镜像 + SCA 兼容**降级锁定 3.2.5**（问题解决--SpringBoot2升级3 已标注更正）

### 2026 行业现状（web 查证）
- **77% Java 开发者用 Spring Boot 3.x**——3.x 仍是绝对主流
- 主流企业 3.3~3.5（3.x 内滚动）；3.5 OSS 支持也将结束（2026）；**4.0 新出**，新项目/激进团队在迁，但生态（SCA 等）跟进慢
- **用 SCA 的团队滞留 3.2/3.3 配 SCA 2023.x 是普遍现实**（版本矩阵受生态约束）——我们的 3.2.5 不丢人，是"生态绑定"的真实认知

### 版本债 + 何时升
- 诚实：3.2.5 已过 OSS 支持期（版本债真实存在）
- 何时升：① 商业化/公网上线（安全补丁必须）② 需要新特性（虚拟线程全链路支持在 3.3+/4.0 更好）③ 中间件/SCA 适配到位
- 判断：演示项目稳定优先 + 版本债可接受；商业项目安全补丁驱动追新（Boot 3.5/4.0 + SCA 配套）
