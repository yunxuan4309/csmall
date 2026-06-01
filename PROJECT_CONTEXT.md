# CoolShark 微服务电商平台 - 项目上下文文档

> 最后更新：2026-05-15
> 用途：为 AI 助手提供项目上下文，确保后续对话能快速理解项目全貌

---

## 一、项目背景

CoolShark（酷鲨）是一个基于 Spring Cloud Alibaba 的微服务电商平台，面向 C 端用户提供商品浏览、搜索、购物车、下单、支付、秒杀等完整购物流程。后台管理系统提供商品管理、订单管理、用户管理等功能。

**部署状态**：后端已部署到阿里云 ECS，通过 Nginx 反向代理对外提供 HTTP 服务。域名 coolshark-shop.cn 待 ICP 备案。

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
| RabbitMQ | 3.13.7 (生产) | 消息队列 |
| Elasticsearch | 7.17.29 (生产，含 IK 分词器) | 全文搜索 |
| Druid | 1.2.24 | 数据库连接池 |
| Knife4j | 4.5.0 | API 文档 |
| Nginx | (生产) | 反向代理 |

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
| mall-resource | 9060 | cs_mall_resource | 文件上传/图片管理/用户上传记录 |
| mall-ai | 10010 | 无 (ES + Dubbo 消费) | AI 智能导购：商品对比、RAG 问答、多轮对话 |

### 弃用模块

| 模块 | 状态 | 说明 |
|---|---|---|
| mall-leaf | 已弃用 | 分布式 ID 生成改用 MyBatis-Plus IdWorker，详见 `mall-leaf/DEPRECATED.md` |

### 基础库模块

| 模块 | 职责 |
|---|---|
| mall-common | 公共组件：统一异常、响应封装、JWT 工具、Redis 配置、Seata Dubbo Filter 等 |
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
| cs_mall_pms | 商品图片/品牌Logo/分类图标 | `init-test-data.sql`（第21-25节） |

> **图片存储策略**：数据库中只存纯相对路径（如 `spu_1_1.jpg`），由服务层 `ImageUrlPrefixHelper` 根据当前环境的 `resource-host` 配置自动拼接完整 URL。详见[TODO文件.md](./TODO文件.md) 第2项。

---

## 五、生产环境信息

### 服务器

| 配置项 | 值 |
|---|---|
| 云厂商 | 阿里云 |
| 规格 | 4核16G 经济型e |
| 地域 | 成都 |
| 操作系统 | Alibaba Cloud Linux 3 (RHEL 8 兼容) |
| 公网 IP | 8.156.85.160 |
| 内网 IP | 172.29.193.239 |
| 登录用户 | ecs-user (sudo to root) |

### 中间件部署

所有中间件部署在 `/data` 目录下，地址统一通过 `my.server.addr` 变量管理。

| 中间件 | 端口 | 生产部署方式 | 版本 |
|---|---|---|---|
| MySQL | 3306 | systemd | 8.0 |
| Redis | 6379 | systemd，已设密码 | 7.x |
| Nacos | 8848 | nohup standalone | 2.5.2 |
| RabbitMQ | 5672 / 15672 | systemd | 3.13.7 + Erlang 26 |
| Seata | 7091(控制台) / 8091(RPC) | nohup | Apache 2.1.0 |
| Elasticsearch | 9200 | systemd，含 IK 分词器 | 7.17.29 |
| Nginx | 80 | yum + systemd | - |

**生产环境**：`my.server.addr: 127.0.0.1`（单机部署，所有组件在同一 ECS 上）

### 敏感信息（通过环境变量注入，不写入代码仓库）

| 环境变量 | 用途 | 默认值 |
|---|---|---|
| `MYSQL_USERNAME` | 数据库用户名 | root |
| `MYSQL_PASSWORD` | 数据库密码 | root |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `RABBITMQ_USERNAME` | RabbitMQ 用户名 | guest |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 | guest |
| `ALIYUN_SERVER_IP` | 服务器地址 | 127.0.0.1 |
| `AI_API_KEY` | AI API 密钥（DeepSeek V4） | — |
| `AI_API_BASE_URL` | AI API 基础地址 | https://api.deepseek.com |
| `RESOURCE_HOST` | 文件资源 URL | http://127.0.0.1:9060/ |

### Nginx 配置

- 监听 80 端口
- `location /` → 前端静态文件 + SPA 回退
- `location ~* \.(jpg|png|gif|webp)$` → 直接从 `/data/csmall-upload/` 提供静态图片（由 Nginx 直接处理，不经过 Gateway）
- 其余 API 路径 → 反向代理到 Gateway 10087
- 配置文件：`/etc/nginx/conf.d/csmall.conf`
- 已删除默认 `default.conf` 避免冲突

### 阿里云安全组

已开放端口：SSH(22)、HTTP(80)、HTTPS(443)、ICMP。其余端口仅限本机访问。

### 服务部署

- JAR 包位置：`/data/jars/`
- 日志位置：`/data/jars/logs/`
- 启动脚本：`/data/jars/start.sh`（一键启动，含环境变量设置）
- 11 个微服务全部运行中

##服务器 /data 目录结构

| 目录 | 大小 | 所有者 | 用途 |
|------|------|--------|------|
| `/data/jars/` | 1013M | ecs-user | 微服务 JAR 包、备份、环境变量文件、日志 |
| `/data/nacos/` | 760M | root | Nacos 注册/配置中心数据 |
| `/data/seata/` | 160M | ecs-user | Seata 分布式事务数据 |
| `/data/elasticsearch/` | 41M | elasticsearch | Elasticsearch 数据目录（含 IK 分词器） |
| `/data/frontend/` | 2.4M | ecs-user | 前端静态资源 |
| `/data/csmall-upload/` | 4.0K | ecs-user | 用户上传文件存储目录 |
| `/data/sessionStore/` | 8.0K | root | 会话存储（Redis Session） |
| `/data/lost+found/` | 16K | root | 文件系统恢复目录（勿删） |

#### /data/jars/ 子目录结构

```
/data/jars/
├── *.jar                    # 微服务 JAR 包（systemd 读取位置）
├── start.sh                 # 旧版一键启动脚本（已迁移到 systemd）
├── logs/                    # 应用日志目录
├── backup_*/                # JAR 包备份目录（自动生成）
├── csmall.env               # ⚠ 共享环境变量（所有服务引用此文件）
└── *.sources.jar            # 源码包（可删除节省空间）
```

**⚠ `csmall.env` 注意事项**：此文件被所有 systemd 服务通过 `EnvironmentFile=/data/jars/csmall.env` 引用。新增环境变量后：
2. 服务器覆盖：`sudo mv /tmp/csmall.env /data/jars/csmall.env`
3. 全部重启：`sudo systemctl restart mall-*`（所有服务重启才能读取新变量）
4. 服务清单：mall-ams, mall-ums, mall-product, mall-sso, mall-order, mall-search, mall-seckill, mall-resource, mall-front, mall-ai, mall-gateway（共 11 个）

### 快速登录指令

```bash
# 1. SSH 登录到云服务器
ssh ecs-user@8.156.85.160

# 2. 配置免密码 sudo（首次执行一次即可）
echo 'ecs-user ALL=(ALL) NOPASSWD: ALL' | sudo tee /etc/sudoers.d/ecs-user

# 3. 免密码切换到 root
sudo su -

# 4. 完整扫盘查看 /data 目录结构
sudo ls -la /data && echo "==========" && sudo du -sh /data/*

# 5. 一键重启所有微服务
systemctl restart mall-gateway mall-sso mall-product mall-front mall-search mall-order mall-seckill mall-ums mall-ams mall-resource mall-ai

# 6. 查看所有服务状态
systemctl status mall-gateway mall-sso mall-product mall-front mall-search mall-order mall-seckill mall-ums mall-ams mall-resource mall-ai --no-pager

# 7. 检查端口响应
for port in 10010 10009 9010 10004 10008 10005 10007 10006 10003 9060 10087; do curl -s -o /dev/null -w "$port: %{http_code}\n" http://localhost:$port/ --connect-timeout 2; done
```
打包:本地执行:cd d:\java\csmall
mvn clean package -DskipTests
一次性上传
cmd:scp d:\java\csmall\mall-gateway-server\target\mall-gateway-server-*.jar ecs-user@8.156.85.160:/data/jars/ && scp d:\java\csmall\mall-sso\target\mall-sso-*.jar ecs-user@8.156.85.160:/data/jars/ && scp d:\java\csmall\mall-product\mall-product-webapi\target\mall-product-webapi-*.jar ecs-user@8.156.85.160:/data/jars/ && scp d:\java\csmall\mall-front\mall-front-webapi\target\mall-front-webapi-*.jar ecs-user@8.156.85.160:/data/jars/ && scp d:\java\csmall\mall-search\mall-search-webapi\target\mall-search-webapi-*.jar ecs-user@8.156.85.160:/data/jars/ && scp d:\java\csmall\mall-order\mall-order-webapi\target\mall-order-webapi-*.jar ecs-user@8.156.85.160:/data/jars/ && scp d:\java\csmall\mall-seckill\mall-seckill-webapi\target\mall-seckill-webapi-*.jar ecs-user@8.156.85.160:/data/jars/ && scp d:\java\csmall\mall-ums\mall-ums-webapi\target\mall-ums-webapi-*.jar ecs-user@8.156.85.160:/data/jars/ && scp d:\java\csmall\mall-ams\mall-ams-webapi\target\mall-ams-webapi-*.jar ecs-user@8.156.85.160:/data/jars/ && scp d:\java\csmall\mall-resource\target\mall-resource-*.jar ecs-user@8.156.85.160:/data/jars/ && scp d:\java\csmall\mall-ai\mall-ai-webapi\target\mall-ai-webapi-*.jar ecs-user@8.156.85.160:/data/jars/ && scp d:\java\csmall\deploy\systemd\mall-ai.service ecs-user@8.156.85.160:/tmp/ && scp d:\java\csmall\deploy\systemd\csmall.env ecs-user@8.156.85.160:/tmp/

### 启动顺序

1. MySQL → Redis → Nacos → Elasticsearch → RabbitMQ → Seata
2. Dubbo 提供者：mall-ams, mall-ums, mall-product
3. Dubbo 消费者：mall-sso, mall-order, mall-search, mall-seckill, mall-resource, mall-front, mall-ai
4. Gateway（最后启动）

---

## 六、已知生产环境注意事项

1. **CORS 限制**：Gateway `CorsConfig.java` 目前只允许 `localhost:5173`，需添加生产域名/IP
2. **ICP 备案**：域名 coolshark-shop.cn 待备案（需服务器续费 3 个月以上），暂用 IP 访问
3. **HTTPS**：备案后需在 Nginx 配置 SSL 证书
4. **ES IK 分词器**：本地开发环境也需安装 IK 插件（4 个字段使用 `ik_max_word`）
5. **RedisBloom**：生产环境需安装 RedisBloom 模块，切换秒杀布隆过滤器实现（代码中有 TODO 标记）
6. **重启恢复**：服务器重启后需手动按顺序启动中间件和微服务，暂未配置 systemd 自启

---

## 七、Git 配置

| 配置项 | 值 |
|---|---|
| 远程仓库 | https://github.com/yunxuan4309/csmall.git |
| 主分支 | master |
| 提交规范 | `feat(模块): 描述` / `fix(模块): 描述` / `docs(模块): 描述` |

### Git 中文乱码修复（必须配置）

Windows 默认使用 GBK 编码，会导致中文文件名和提交日志乱码。需执行以下全局配置：

```bash
# 修复中文文件名显示为 \xxx\xxx 的问题
git config --global core.quotepath false
# 统一提交日志编码为 UTF-8
git config --global i18n.commitEncoding utf-8
# 统一日志输出编码为 UTF-8
git config --global i18n.logOutputEncoding utf-8
# GUI 编码
git config --global gui.encoding utf-8
```

如果 IDEA 终端中文仍然乱码，还需：
1. IDEA → Settings → Editor → File Encodings → 全部改为 UTF-8
2. IDEA → Help → Edit Custom VM Options → 添加 `-Dfile.encoding=UTF-8`
3. 重启 IDEA

---

## 八、关键设计决策

1. **RPC 选型**：使用 Dubbo 替代 Feign，性能更优
2. **分布式事务**：Seata AT 模式，@GlobalTransactional 注解
3. **秒杀架构**：Redis 预扣库存 + RabbitMQ 异步落库 + 防缓存穿透（当前用 Redis Set 替代布隆过滤器）
4. **ID 生成**：MyBatis-Plus IdWorker (雪花算法)，自定义 XML 需手动调用 `IdWorker.getId()`（已弃用 mall-leaf）
5. **认证方式**：SSO 统一登录 + JWT Token + Spring Security
6. **API 文档**：Knife4j (OpenAPI 3)，生产环境 `knife4j.production: true` 屏蔽
7. **Long 精度**：全局 Jackson 配置 Long→String 序列化，避免 JS 精度丢失
8. **Seata Dubbo Filter**：自定义实现替代官方 bug 版本（`dubbo-filter-seata:1.0.2` 的 branchType NPE）
9. **图片 URL 架构**：数据库只存相对路径，服务层 `ImageUrlPrefixHelper` (`mall-product/.../utils/`) 根据环境配置 `resource-host` 动态拼接完整 URL。开发/测试 → `http://localhost:9060/`，生产 → `http://8.156.85.160/`。已上传的图片（完整 URL）不受影响，工具类自动跳过 `http://` 开头的路径。
10. **生产环境图片服务**：Nginx 直接服务静态图片文件（`~* \.(jpg|png|gif|webp)$` → `/data/csmall-upload/`），不经过 Java 服务栈，性能更优。
11. **AI 智能导购（mall-ai）**：RAG（检索增强生成）架构，ES IK 分词全文检索 + DeepSeek V4 Chat 生成回答。支持多轮对话（Redis 存储会话）、商品对比、日预算控制（10元/天）。向量语义检索作为预留扩展（DeepSeek V4 暂无 embedding API）。通过 Dubbo `ISpuSyncService` 与 mall-product 联动，商品变更自动同步到 ES。

---

## 九、开发环境信息

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

## 十、待办事项

- [x] 部署到阿里云 Linux 服务器
- [x] 服务器环境搭建（JDK/MySQL/Redis/Nacos/ES/RabbitMQ/Seata/Nginx）
- [x] 后端服务部署上线（10 个微服务全部运行）
- [x] 生产环境配置 (application-prod.yml)
- [x] 购买域名（coolshark-shop.cn，待备案）
- [ ] 域名 ICP 备案（需服务器续费 3 个月以上）
- [ ] HTTPS/SSL 配置（备案后 Nginx 配置证书）
- [x] 商品图片资源整合（38张SPU图+7个品牌Logo+9个分类图标）
- [x] 图片 URL 硬编码问题修复（`ImageUrlPrefixHelper` + 相对路径存储）
- [x] Nginx 配置静态图片文件服务（直接文件系统提供，不经过 Gateway）
- [x] 生产环境图片部署完成（图片上传至 `/data/csmall-upload/`）
- [x] 数据库图片数据初始化（SPU组图/品牌Logo/分类图标）
- [x] AI 智能导购模块（mall-ai）开发完成并上线（Phase 1-3），Phase 4（智能搜索增强）待开发
- [ ] Gateway CORS 配置添加生产域名
- [ ] 前端联调部署
- [ ] 安装 RedisBloom 模块，切换秒杀布隆过滤器实现
- [ ] 解决 WSL2 环境问题，使 Docker Desktop 可用
- [ ] `pms_spu_detail.content` 富文本中的 `<img>` 相对路径需服务层处理（可选）
- [ ] 详见 [`TODO文件.md`](./TODO文件.md) 中的各项待办
