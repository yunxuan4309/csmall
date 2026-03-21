# mall-common 模块说明

## 概述

mall-common 是 csmall 电商平台项目的公共组件模块，为整个微服务架构提供通用的工具类、常量定义、异常处理、响应封装等共享资源。该模块旨在减少代码重复，提高代码复用性，并确保各个微服务模块之间的统一性和一致性。

## 功能特性

### 1. 通用响应封装

- **JsonResult**: 通用响应对象，用于封装HTTP请求的响应结果
- **ResponseCode**: 错误代码枚举，定义了标准的业务状态码
- **JsonPage**: 数据分页类，整合PageHelper分页插件的分页信息

### 2. 异常处理机制

- **CoolSharkServiceException**: 业务异常基类，统一处理业务逻辑异常
- **GlobalControllerExceptionHandler**: 全局异常处理器，统一捕获和处理控制器层异常
- **BindExceptionHandler**: 绑定异常处理器，处理参数校验异常

### 3. JWT 认证工具

- **JwtTokenUtils**: JWT工具类，提供令牌生成、解析、验证等功能
  - 支持自定义载荷信息
  - 提供令牌过期时间管理
  - 支持令牌刷新功能
  - 使用HS512加密算法

### 4. 数据传输对象

- **CsmallAuthenticationInfo**: 自定义认证框架数据封装，用于存储用户认证信息

### 5. 配置与常量

- **PrefixConfiguration**: 缓存键前缀配置，定义了各种业务数据的缓存键命名规范
- **RegExpressions**: 正则表达式接口，定义了常用验证规则

### 6. 通用配置

- **MallCommonConfiguration**: 当前模块的配置类，启用组件扫描

## 包结构说明

```
com.mygroup.mallcommon
├── config          # 配置类
│   ├── MallCommonConfiguration  # 模块配置
│   └── PrefixConfiguration      # 缓存键前缀配置
├── domain          # 数据对象
│   └── CsmallAuthenticationInfo # 认证信息封装
├── exception       # 异常处理
│   ├── CoolSharkServiceException        # 业务异常基类
│   └── handler
│       └── GlobalControllerExceptionHandler  # 全局异常处理器
├── restful         # RESTful API相关
│   ├── JsonResult     # 通用响应对象
│   ├── ResponseCode   # 响应状态码
│   └── JsonPage       # 分页数据封装
├── utils           # 工具类
│   └── JwtTokenUtils  # JWT工具类
└── validation      # 参数验证
    ├── RegExpressions  # 正则表达式
    └── handler
        └── BindExceptionHandler  # 参数绑定异常处理器
```

## 依赖说明

mall-common 模块依赖以下核心技术组件：

- **Spring Boot Web**: 提供Web应用支持
- **PageHelper**: MyBatis分页插件
- **Knife4j**: API文档生成工具
- **Lombok**: 代码简化工具
- **JJWT**: JWT令牌处理
- **FastJSON**: JSON序列化/反序列化
- **Swagger**: API规范支持

## 使用指南

### 1. 在其他模块中引入

```xml
<dependency>
    <groupId>com.mygroup</groupId>
    <artifactId>mall-common</artifactId>
    <version>${mall-common.version}</version>
</dependency>
```

### 2. 响应结果使用

```java
@RestController
public class SampleController {
    
    @GetMapping("/sample")
    public JsonResult<String> sample() {
        return JsonResult.ok("Success");
    }
    
    @GetMapping("/error")
    public JsonResult<Void> error() {
        return JsonResult.failed(ResponseCode.BAD_REQUEST, "Bad Request");
    }
}
```

### 3. 异常处理

在业务模块中抛出 `CoolSharkServiceException`，将由全局异常处理器统一处理。

### 4. JWT 工具使用

```java
@Autowired
private JwtTokenUtils jwtTokenUtils;

// 生成令牌
String token = jwtTokenUtils.generateToken(userInfo);

// 解析令牌
CsmallAuthenticationInfo userInfo = jwtTokenUtils.getUserInfo(token);
```

## 注意事项

1. **模块职责**: mall-common 仅包含可复用的公共组件，不包含业务逻辑实现
2. **包名规范**: 所有代码必须使用 `com.mygroup.mallcommon` 作为根包名
3. **版本管理**: 依赖版本由父POM统一管理，子模块无需指定版本号
4. **向后兼容**: 修改公共组件时需考虑向后兼容性
5. **线程安全**: 提供的工具类应保证线程安全性

## 开发规范

- 所有公共类需提供完整的JavaDoc文档
- 异常处理需遵循统一的错误码规范
- 常量定义需使用接口或类进行组织
- 工具类方法需是无副作用的纯函数
- 代码风格遵循阿里巴巴Java开发手册

## 维护说明

mall-common 模块由平台架构组负责维护，任何修改都需要经过严格的评审和测试，确保不影响依赖该模块的其他微服务。