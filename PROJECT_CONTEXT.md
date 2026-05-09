# CoolShark 微服务电商平台 - 项目上下文文档

> 最后更新：2026-05-09
> 用途：为 AI 助手提供项目上下文，确保后续对话能快速理解项目全貌

---

## 一、项目背景

CoolShark（酷鲨）是一个基于 Spring Cloud Alibaba 的微服务电商平台，面向 C 端用户提供商品浏览、搜索、购物车、下单、支付、秒杀等完整购物流程。后台管理系统提供商品管理、订单管理、用户管理等功能。

**部署计划**：项目将在阿里云服务器部署，购买域名实现 HTTP 访问。

---

## 二、技术栈

| 技术 | 版本 | 用途 |
|---|---|---|
| Java | 21 | 开发语言 |
| Spring Boot | 3.2.5 | 基础框架 |
| Spring Cloud | 2023.0.3 | 微服务框架 |
| Spring Cloud Alibaba | 2023.0.1.2 | Nacos/Sentinel/Seata 集成 |
| Apache Dubbo | 3.3.2 | RPC 框架（替代 Feign） |
| MyBatis-Plus | 3.5.9 | ORM 框架 |
| Seata | 2.1.0 (Apache) | 分布式事务 |
| Sentinel | (由 BOM 管理) | 流量控制 |
| Nacos | (由 BOM 管理) | 注册中心 + 配置中心 |
| MySQL | 8.0 | 关系型数据库 |
| Redis | 3.2 (开发) / 7.x (生产) | 缓存 |
| RabbitMQ | 3.x | 消息队列 |
| Elasticsearch | 7.x/8.x | 全文搜索 |
| Druid | 1.2.24 | 数据库连接池 |
| Knife4j | 4.5.0 | API 文档 |

---

## 三、模块架构

### 服务模块

| 模块 | 端口 | 数据库 | 职责 |
|---|---|---|---|
| mall-gateway-server | 10087 | 无 | API 网关，路由转发，认证 |
| mall-sso | 10009 | cs_mall_ams + cs_mall_ums | 统一登录认证 |
| mall-product | 9010 | cs_mall_pms | 商品管理 (SPU/SKU/品牌/分类) |
| mall-front | 10004 | 无 (Dubbo 消费) | 前台商品展示 |
| mall-search | 10008 | ES | 商品搜索 |
| mall-order | 10005 | cs_mall_oms | 购物车/订单/支付 |
| mall-seckill | 10007 | cs_mall_seckill | 秒杀活动 |
| mall-ums | 10006 | cs_mall_ums | 用户管理/地址/积分 |
| mall-ams | 10003 | cs_mall_ams | 后台管理员/角色/权限 |
| mall-resource | 9060 | 无 | 文件上传/图片管理 |
| mall-leaf | 9090 | 无 | 分布式 ID 生成 (雪花算法) |

### 基础库模块

| 模块 | 职责 |
|---|---|
| mall-common | 公共组件：统一异常、响应封装、JWT 工具、Redis 配置等 |
| mall-pojo | 数据模型：实体类、DTO、VO |

---

## 四、数据库清单

| 数据库 | 用途 | 初始化脚本 |
|---|---|---|
| cs_mall_pms | 商品库 | 01-pms-product.sql |
| cs_mall_ams | 管理员库 | 02-ams-admin.sql |
| cs_mall_oms | 订单库 | 03-oms-order.sql |
| cs_mall_ums | 用户库 | 04-ums-user.sql |
| cs_mall_seckill | 秒杀库 | 05-seckill.sql |
| (各业务库) | Seata undo_log | 06-seata-undo-log.sql |
| cs_mall_seckill | 秒杀测试数据 | 07-seckill-test-data.sql |

---

## 五、中间件配置

所有中间件地址统一通过 `my.server.addr` 变量管理，一处修改全局生效。

| 中间件 | 端口 | 配置路径 |
|---|---|---|
| MySQL | 3306 | `spring.datasource.url` |
| Redis | 6379 | `spring.redis.host` |
| Nacos | 8848 | `spring.cloud.nacos.discovery.server-addr` / `dubbo.registry.address` |
| RabbitMQ | 5672 | `spring.rabbitmq.host` |
| Seata | 8091 | `seata.service.grouplist.default` |
| Elasticsearch | 9200 | `spring.elasticsearch.rest.uris` |
| Sentinel Dashboard | 8080 | `spring.cloud.sentinel.transport.dashboard` |

**开发环境**：`my.server.addr: localhost`
**生产环境**：`my.server.addr: <阿里云服务器IP>`

---

## 六、Git 配置

| 配置项 | 值 |
|---|---|
| 远程仓库 | https://github.com/yunxuan4309/csmall.git |
| 主分支 | master |
| 提交规范 | `feat(模块): 描述` / `fix(模块): 描述` / `docs(模块): 描述` |

---

## 七、关键设计决策

1. **RPC 选型**：使用 Dubbo 替代 Feign，性能更优
2. **分布式事务**：Seata AT 模式，@GlobalTransactional 注解
3. **秒杀架构**：Redis 预扣库存 + RabbitMQ 异步落库 + 防缓存穿透
4. **ID 生成**：MyBatis-Plus ASSIGN_ID (雪花算法)，自定义 XML 需手动调用 IdWorker.getId()
5. **认证方式**：SSO 统一登录 + JWT Token + Spring Security
6. **API 文档**：Knife4j (OpenAPI 3)，各模块独立提供文档

---

## 八、开发环境信息

| 配置项 | 值 |
|---|---|
| 操作系统 | Windows 11 Home |
| IDE | IntelliJ IDEA |
| Redis 客户端 | Another Redis Desktop Manager |
| MQ 管理 | RabbitMQ Management (http://localhost:15672) |
| 本地 Redis 版本 | 3.2.100 (Windows)，不支持 RedisBloom |
| Docker Desktop | 已安装但 WSL2 未就绪，暂不可用 |
| Nacos | 本地运行 |

---

## 九、待办事项

- [ ] 解决 WSL2 环境问题，使 Docker Desktop 可用
- [ ] 部署到阿里云 Linux 服务器
- [ ] 安装 RedisBloom 模块，切换秒杀模块布隆过滤器实现
- [ ] 购买域名，配置 HTTP/HTTPS 访问
- [ ] 生产环境配置 (application-prod.yml)
