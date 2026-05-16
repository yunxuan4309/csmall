# 🦈 CoolShark（酷鲨）微服务电商平台

> 基于 Spring Cloud Alibaba 的全功能微服务电商平台，支持商品浏览、搜索、购物车、订单支付、秒杀及 AI 智能导购。

---

## 📋 项目概述

CoolShark 是一个面向 C 端消费者的全功能电商平台后端服务，采用 **Spring Cloud Alibaba** 微服务架构，共拆分为 11 个微服务模块。平台完整覆盖电商核心业务链路：

- **商品服务**：SPU/SKU 管理、多级分类、品牌管理
- **搜索服务**：Elasticsearch 全文检索（IK 中文分词）
- **购物车 & 订单**：购物车管理、订单创建与支付流程
- **秒杀系统**：高并发秒杀活动（Redis 预扣库存 + RabbitMQ 异步落库）
- **用户中心**：用户管理、收货地址、积分体系
- **后台管理**：管理员账户、RBAC 角色权限
- **AI 导购**：基于 RAG 架构的智能商品问答与对比（DeepSeek V4）

项目已全量部署至 **阿里云 ECS**（4 核 16G），通过 Nginx 反向代理对外提供 HTTP 服务。

---

## 🏗️ 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 开发语言 | Java | 21 |
| 基础框架 | Spring Boot | 3.2.5 |
| 微服务 | Spring Cloud / Spring Cloud Alibaba | 2023.0.3 / 2023.0.1.2 |
| RPC 框架 | Apache Dubbo | 3.3.2 |
| ORM | MyBatis-Plus | 3.5.9 |
| 分布式事务 | Apache Seata (AT 模式) | 2.1.0 |
| 注册中心 & 配置中心 | Nacos | 2.5.2 |
| 流量控制 | Sentinel | - |
| 数据库 | MySQL | 8.0 |
| 连接池 | Druid | 1.2.24 |
| 缓存 | Redis + Redisson | 7.x |
| 消息队列 | RabbitMQ | 3.13.7 |
| 搜索引擎 | Elasticsearch + IK 分词器 | 7.17.29 |
| API 文档 | Knife4j (OpenAPI 3) | 4.5.0 |
| 认证授权 | JWT + Spring Security | - |
| 部署运维 | systemd / Nginx / 阿里云 ECS | - |
| AI | DeepSeek Chat API | V4 |

---

## 🧩 模块架构

### 微服务模块（11 个）

| 模块 | 端口 | 数据库 | 职责 |
|------|------|--------|------|
| `mall-gateway-server` | 10087 | 无 | API 网关，路由转发，认证拦截 |
| `mall-sso` | 10009 | cs_mall_ams + cs_mall_ums | 统一登录认证（JWT） |
| `mall-ams` | 10003 | cs_mall_ams | 后台管理员、角色、权限管理 |
| `mall-ums` | 10006 | cs_mall_ums | 用户管理、收货地址、积分 |
| `mall-product` | 9010 | cs_mall_pms | 商品管理 (SPU/SKU/品牌/分类) |
| `mall-front` | 10004 | 无（Dubbo 消费） | 前台商品展示 |
| `mall-search` | 10008 | Elasticsearch | 商品全文搜索 |
| `mall-order` | 10005 | cs_mall_oms | 购物车、订单、支付 |
| `mall-seckill` | 10007 | cs_mall_seckill | 秒杀活动 |
| `mall-resource` | 9060 | 无 | 文件上传、图片管理 |
| `mall-ai` | 10010 | 无（ES + Dubbo 消费） | AI 智能导购 |

### 基础库模块

| 模块 | 职责 |
|------|------|
| `mall-common` | 公共组件：统一异常、响应封装、JWT 工具、Redis 配置、Seata Dubbo Filter 等 |
| `mall-pojo` | 数据模型：实体类、DTO、VO |

### 架构分层图

```
                              ┌─────────────┐
                              │   Nginx 80   │
                              │ 反向代理+静态资源 │
                              └──────┬──────┘
                                     │
                              ┌──────▼──────┐
                              │   Gateway   │
                              │   10087     │
                              └──────┬──────┘
                                     │
          ┌──────────────────────────┼──────────────────────────┐
          │              Dubbo RPC   │                          │
    ┌─────▼─────┐          ┌────────▼────────┐        ┌───────▼───────┐
    │ mall-sso  │          │   mall-front    │        │ mall-resource │
    │  10009    │          │    10004        │        │    9060       │
    └───────────┘          └─────────────────┘        └───────────────┘
          │
    ┌─────┼─────┬──────────────┬──────────────┬──────────────┐
    │     │     │              │              │              │
┌───▼──┐ ┌▼───┐ ┌▼────────┐ ┌─▼────────┐ ┌─▼────────┐ ┌───▼────┐
│mall- │ │mall│ │mall-    │ │mall-     │ │mall-     │ │mall-ai │
│ams   │ │ums │ │product  │ │order     │ │seckill   │ │ 10010  │
│10003 │ │6   │ │9010     │ │10005     │ │10007     │ │        │
└──────┘ └────┘ └─────────┘ └──────────┘ └──────────┘ └────────┘
                              │
                         ┌────▼────┐
                         │mall-   │
                         │search  │
                         │10008   │
                         │(ES)    │
                         └────────┘
```

---

## 🚀 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0
- Redis 7.x
- Nacos 2.5.2
- RabbitMQ 3.13.x（可选，秒杀功能需要）
- Elasticsearch 7.17.x（可选，搜索/AI功能需要）

### 启动步骤

```bash
# 1. 启动中间件（MySQL、Redis、Nacos、RabbitMQ、ES）

# 2. 克隆项目
git clone https://github.com/yunxuan4309/csmall.git
cd csmall

# 3. 编译打包
mvn clean package -DskipTests

# 4. 按顺序启动微服务
# 顺序：提供者 → 消费者 → Gateway
java -jar mall-ams/mall-ams-webapi/target/mall-ams-webapi-*.jar
java -jar mall-ums/mall-ums-webapi/target/mall-ums-webapi-*.jar
java -jar mall-product/mall-product-webapi/target/mall-product-webapi-*.jar
java -jar mall-sso/target/mall-sso-*.jar
java -jar mall-order/mall-order-webapi/target/mall-order-webapi-*.jar
java -jar mall-search/mall-search-webapi/target/mall-search-webapi-*.jar
java -jar mall-seckill/mall-seckill-webapi/target/mall-seckill-webapi-*.jar
java -jar mall-resource/target/mall-resource-*.jar
java -jar mall-front/mall-front-webapi/target/mall-front-webapi-*.jar
java -jar mall-ai/mall-ai-webapi/target/mall-ai-webapi-*.jar
java -jar mall-gateway-server/target/mall-gateway-server-*.jar
```

---

## 🔑 关键设计

### 秒杀系统

采用 **Redis 预扣库存 + RabbitMQ 异步落库** 架构：
1. 用户发起秒杀请求 → Gateway 鉴权拦截
2. Redis 原子操作预扣库存（`decrement`），保证不超卖
3. 发送秒杀消息到 RabbitMQ，立即返回"抢购中"状态
4. RabbitMQ 消费者手动 ACK，异步写入数据库订单
5. 失败重试机制：消费端最多重试 3 次，间隔 1 秒
6. 防缓存穿透：Redis Set 结构做用户去重（待升级为 RedisBloom 布隆过滤器）

### 分布式事务

在跨库事务场景（如订单创建涉及订单库 + 商品库 + 用户库）使用 **Seata AT 模式**：

- `@GlobalTransactional` 注解声明全局事务
- 自定义 Dubbo Filter 修复官方 `dubbo-filter-seata` NPE 缺陷
- AT 模式通过拦截 SQL 生成回滚镜像，对业务代码侵入小

### AI 智能导购

基于 **RAG（检索增强生成）** 架构：

1. 用户输入自然语言问题
2. ES 检索商品库（IK 分词器中文分词）
3. 检索结果 + 对话历史注入 DeepSeek V4 上下文
4. 大模型生成个性化回答，支持多轮对话和商品对比
5. Redis 存储会话历史，日预算上限控制 API 调用量

### 图片 URL 架构

- **数据库**：只存纯相对路径（如 `spu_1_1.jpg`），不存完整 URL
- **服务层**：`ImageUrlPrefixHelper` 工具类按环境自动拼接前缀
- **生产环境**：Nginx 直接通过文件系统提供静态图片，不经过 Java 服务栈

---

## 📦 部署

### 生产环境

| 配置项 | 值 |
|--------|-----|
| 云厂商 | 阿里云 ECS（成都） |
| 规格 | 4 核 16G 经济型 e |
| 操作系统 | Alibaba Cloud Linux 3 |
| 部署方式 | systemd 托管（11 个微服务） |
| 反向代理 | Nginx 80 端口 |
| 敏感信息 | 统一 `csmall.env` 环境变量注入 |

### 启动顺序

```
1. MySQL → Redis → Nacos → Elasticsearch → RabbitMQ → Seata
2. Dubbo 提供者：mall-ams → mall-ums → mall-product
3. Dubbo 消费者：mall-sso → mall-order → mall-search → mall-seckill → mall-resource → mall-front → mall-ai
4. Gateway（最后启动）
```

---

## 📐 开发规范

- **提交格式**：`feat(模块): 描述` / `fix(模块): 描述` / `docs: 描述`
- **API 响应格式**：统一 `{ state: 200, message: "ok", data: {...} }`
- **Long 精度处理**：全局 Jackson 配置 Long→String 序列化，避免 JS 精度丢失

---

## 📄 许可证

Apache License 2.0
