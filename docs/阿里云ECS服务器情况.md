# 阿里云 ECS 云服务器情况

> 创建日期：2026-07-29
> 最后更新：2026-08-03
> 用途：记录本次部署的阿里云 ECS 服务器全部配置信息

---

## 一、服务器基本信息

| 配置项 | 值 |
|--------|-----|
| 实例 ID | i-2vc8cadlyv9w8dwpmivb |
| 实例名称 | launch-advisor-20260729 |
| 实例规格 | ecs.e-c1m4.xlarge（经济型 e） |
| vCPU | 4 核 |
| 内存 | 16 GiB |
| 架构 | X86 计算 — 共享型 |
| 地域 | 西南1（成都）可用区 B |
| 操作系统 | Ubuntu 24.04 64位（安全加固） |
| 系统盘 | ESSD Entry 100 GiB（随实例释放） |
| 公网 IP | **8.156.77.197** |
| 私网 IP | 172.29.193.239 |
| 公网带宽 | 5 Mbps 固定带宽 |
| 登录用户 | ecs-user（推荐）/ root |
| 登录方式 | 自定义密码 |
| 付费类型 | 包年包月（1 个月） |
| 到期时间 | 2026-08-29 23:59:59 |
| 域名 | 无（ICP 备案需购买≥3个月） |

---

## 二、安全组配置

**安全组 ID**：sg-2vc9i0zxt8gbq18mo8ea

**专有网络**：vpc-2vcxszplmcrzxluvgdnyr

**入方向规则**：

| 端口 | 协议 | 来源 | 用途 |
|------|------|------|------|
| 22 | TCP | 0.0.0.0/0 | SSH 远程登录 |
| 80 | TCP | 0.0.0.0/0 | 前端 HTTP 访问 + API 反向代理 |

> 当前仅开放上述端口。如需 HTTPS，后续添加 443。**不要**将数据库/Redis/Nacos 等中间件端口暴露到公网。

---

## 三、服务器目录结构（实际状态）

```
/data/
├── csmall/                          # 项目根目录
│   ├── docker-compose.yml           # Docker 编排文件（22个容器）
│   ├── .env                         # 环境变量
│   ├── .env.example                 # 环境变量模板
│   ├── mysql-init.sql               # MySQL 初始化（创建6个数据库）
│   ├── setup-server.sh              # 服务器初始化脚本
│   ├── Dockerfile.frontend          # 前端 Nginx Dockerfile
│   ├── dockerfiles/                 # 11 个模块专用 Dockerfile
│   │   ├── mall-gateway.Dockerfile
│   │   ├── mall-sso.Dockerfile
│   │   ├── mall-product.Dockerfile
│   │   ├── mall-front.Dockerfile
│   │   ├── mall-order.Dockerfile
│   │   ├── mall-search.Dockerfile
│   │   ├── mall-seckill.Dockerfile
│   │   ├── mall-ums.Dockerfile
│   │   ├── mall-ams.Dockerfile
│   │   ├── mall-resource.Dockerfile
│   │   └── mall-ai.Dockerfile
│   ├── jars/                        # 11 个微服务 JAR 包
│   │   ├── mall-gateway.jar (~72MB)
│   │   ├── mall-sso.jar (~91MB)
│   │   ├── mall-product.jar (~133MB)
│   │   ├── mall-front.jar (~126MB)
│   │   ├── mall-order.jar (~171MB)
│   │   ├── mall-search.jar (~128MB)
│   │   ├── mall-seckill.jar (~144MB)
│   │   ├── mall-ums.jar (~101MB)
│   │   ├── mall-ams.jar (~101MB)
│   │   ├── mall-resource.jar (~57MB)
│   │   └── mall-ai.jar (~127MB)
│   └── frontend/                    # 前端部署
│       ├── nginx.conf               # Nginx 配置
│       └── dist/                    # Vue 构建产物
│           ├── index.html
│           └── assets/
├── csmall-upload/                   # 用户上传文件存储（Nginx 直接服务）
│   ├── *.jpg                        # 商品图片（SPU 组图）
│   └── *.png                        # 品牌 Logo / 分类图标
└── tmp/
    ├── init-test-data.sql           # 测试数据 SQL（备用）
    └── 07-seckill-test-data.sql     # 秒杀测试数据 SQL（备用）
```

**Docker 数据卷**（由 Docker 管理，位于 `/var/lib/docker/volumes/`）：

| 卷名 | 用途 |
|------|------|
| csmall_mysql_data | MySQL 8.0 数据文件 |
| csmall_redis_data | Redis 7 持久化 |
| csmall_rabbitmq_data | RabbitMQ 消息持久化 |
| csmall_es_data | Elasticsearch 索引数据 |

---

## 四、Docker 容器清单（实际运行状态，共 22 个容器）

### 4.1 中间件（10 个）

| 服务 | 容器名 | 镜像 | 端口映射 | 账号/密码 | 健康检查 |
|------|--------|------|---------|-----------|---------|
| MySQL 8.0 | csmall-mysql | mysql:8.0 | 3306→3306 | root / `.env` 中配置 | ✅ mysqladmin ping |
| Redis 7 | csmall-redis | redis:7-alpine | 6379→6379 | 无密码 | ✅ redis-cli ping |
| Nacos 2.5.2 | csmall-nacos | nacos/nacos-server:v2.5.2 | 8848,9848 | nacos/nacos | ✅ curl health |
| RabbitMQ 4 | csmall-rabbitmq | rabbitmq:4-management-alpine | 5672,15672 | guest/guest | ✅ rabbitmqctl status |
| Elasticsearch 8.6 | csmall-es | elasticsearch:8.6.0 | 9200,9300 | 无密码 | ✅ cluster health |
| Seata 2.1.0 | csmall-seata | apache/seata-server:2.1.0 | 8091,7091 | seata/seata | ✅ wget console |
| Sentinel 1.8.6 | csmall-sentinel | bladex/sentinel-dashboard:1.8.6 | 8090→8080 | sentinel/sentinel | ❌ 无 |

> **注意**：Seata 使用 `apache/seata-server`（非 `seataio/seata-server`），后者在国内镜像源不可用。

### 4.2 微服务（11 个）

| 服务 | 容器名 | 端口 | 数据库 | 依赖中间件 |
|------|--------|------|--------|-----------|
| mall-gateway | csmall-gateway | 10087 | 无 | Nacos |
| mall-sso | csmall-sso | 10009 | cs_mall_ams + cs_mall_ums | MySQL、Nacos |
| mall-product | csmall-product | 9010 | cs_mall_pms | MySQL、Nacos、Seata |
| mall-front | csmall-front | 10004 | 无（Dubbo 消费） | Nacos、Seata |
| mall-order | csmall-order | 10005 | cs_mall_oms | MySQL、Nacos、RabbitMQ、Seata |
| mall-search | csmall-search | 10008 | ES | Nacos、ES、Seata |
| mall-seckill | csmall-seckill | 10007 | cs_mall_seckill | MySQL、Nacos、RabbitMQ、Seata |
| mall-ums | csmall-ums | 10006 | cs_mall_ums | MySQL、Nacos |
| mall-ams | csmall-ams | 10003 | cs_mall_ams | MySQL、Nacos |
| mall-resource | csmall-resource | 9060 | cs_mall_resource | MySQL、Nacos |
| mall-ai | csmall-ai | 10010 | ES | Nacos、ES |

### 4.3 前端（1 个）

| 服务 | 容器名 | 端口 | 说明 |
|------|--------|------|------|
| Nginx | csmall-frontend | 80 | SPA 静态文件 + API 反向代理 |

### 4.4 链路追踪（新增）

| 服务 | 容器名 | 端口 | 说明 |
|------|--------|------|------|
| SkyWalking OAP | csmall-skywalking-oap | 11800,12800 | 接收 Agent 数据，存储到 ES |
| SkyWalking UI | csmall-skywalking-ui | 8088→8080 | 链路追踪可视化看板 |

### 外部访问说明

目前仅开放 HTTP(80) 端口。如需从本地访问 Nacos/Sentinel/SkyWalking 等管理界面，可通过 SSH 隧道：

```bash
# Nacos 控制台
ssh -L 8848:localhost:8848 ecs-user@8.156.77.197

# Sentinel Dashboard
ssh -L 8090:localhost:8090 ecs-user@8.156.77.197

# RabbitMQ 管理界面
ssh -L 15672:localhost:15672 ecs-user@8.156.77.197

# SkyWalking UI
ssh -L 8088:localhost:8088 ecs-user@8.156.77.197
```

---

## 五、端口汇总

| 端口 | 服务 | 公网可达？ |
|------|------|-----------|
| 22 | SSH | ✅ |
| 80 | Nginx（前端 + API 代理） | ✅ |
| 3306 | MySQL | ❌ 仅 VPC 内网 |
| 5672 | RabbitMQ AMQP | ❌ 仅 VPC 内网 |
| 6379 | Redis | ❌ 仅 VPC 内网 |
| 8848 | Nacos | ❌ 仅 VPC 内网 |
| 9200 | Elasticsearch HTTP | ❌ 仅 VPC 内网 |
| 15672 | RabbitMQ 管理界面 | ❌ 仅 VPC 内网 |
| 8090 | Sentinel Dashboard | ❌ 仅 VPC 内网 |
| 8088 | SkyWalking UI | ❌ 仅 VPC 内网 |
| 8091 | Seata RPC | ❌ 仅 VPC 内网 |
| 11800 | SkyWalking OAP gRPC | ❌ 仅 VPC 内网 |
| 12800 | SkyWalking OAP HTTP | ❌ 仅 VPC 内网 |
| 10087 | Gateway（内部） | ❌ 仅 VPC 内网 |
| 10003~10010 | 各微服务 | ❌ 仅 VPC 内网 |

---

## 六、SSH 登录方式

```bash
# 使用 ecs-user 登录（推荐）
ssh ecs-user@8.156.77.197

# 使用 root 登录
ssh root@8.156.77.197
```

---

## 七、Docker 安装（已完成）

```bash
# 已执行命令（2026-07-29）
sudo apt update
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable docker --now
sudo usermod -aG docker ecs-user
```

**镜像加速配置**（`/etc/docker/daemon.json`）：

```json
{
  "registry-mirrors": [
    "https://dockerpull.org",
    "https://docker.1ms.run"
  ]
}
```

---

## 八、部署后访问地址

| 服务 | URL |
|------|-----|
| 前端首页 | http://8.156.77.197 |
| Knife4j API 文档 | 暂不对外暴露 |
| Nacos 控制台 | `ssh -L 8848:localhost:8848 ecs-user@8.156.77.197` 后访问 http://localhost:8848 |
| Sentinel Dashboard | `ssh -L 8090:localhost:8090 ecs-user@8.156.77.197` 后访问 http://localhost:8090 |
| RabbitMQ 管理 | `ssh -L 15672:localhost:15672 ecs-user@8.156.77.197` 后访问 http://localhost:15672 |
| SkyWalking UI | `ssh -L 8088:localhost:8088 ecs-user@8.156.77.197` 后访问 http://localhost:8088 |

---

## 九、最近部署记录

### 2026-08-01 晚间部署

**部署范围**: 6 个模块 + 前端 + docker-compose

| 模块 | 容器 | 改动 |
|------|------|------|
| mall-gateway-server | csmall-gateway | 新增 `/pms/**` 路由，修复 Nacos 双注册导致 500 |
| mall-order-webapi | csmall-order | 秒杀已购买标记重构（markSeckillPurchased / clearSeckillOrdered） |
| mall-product-webapi | csmall-product | SpuMapper/Mapper 参数重构（spuQuery），Controller 新增搜索参数 |
| mall-seckill-webapi | csmall-seckill | 三把 Redis key 分离 + SeckillFallback 错误前缀移除 |
| mall-sso | csmall-sso | Dashboard SQL gmt_create→gmt_pay + 新增 order 数据源 |
| frontend（nginx） | csmall-frontend | Admin SPA 刷新保护 + /api/ams/ 前缀路由 + Dashboard Accept 头区分 |
| docker-compose.yml | - | SSO 新增 SPRING_DATASOURCE_ORDER_JDBC_URL |

**数据库变更**:
- `cs_mall_ams.ams_permission` — 新增 `value` 列；插入 5 条缺失权限
- `cs_mall_pms.pms_brand` — 新增 `pinyin`、`sales`、`enable` 等 8 列
- `cs_mall_seckill.success` / `seckill_message_retry` — 清理测试残留数据

**Redis 变更**:
- 清理 `mall:seckill:reseckill:*`、`mall:seckill:order:lock:*` 残留 key

### 2026-08-03 resource 容器修复

**问题**: csmall-resource 容器 2 天前 Exited (1)。

**根因**: 07-31 整栈 + SkyWalking 同时部署、服务器负载过高，resource 启动时 Nacos gRPC 客户端 50s 未连上（STARTING 状态）→ 服务注册抛 `NacosException: Client not connected` → Spring 启动中止。而 mall-resource 是 11 个应用服务中**唯一漏配 `restart` 策略**的，Docker 一次都不重试，因此一直 Exited。

**修复**: 给 `docker-compose.yml` 的 mall-resource 加 `restart: unless-stopped`，`docker compose up -d mall-resource` 拉起。验证：Nacos 注册成功（172.18.0.22:9060 healthy）、actuator `UP`、启动仅约 30s。备份：`docker-compose.yml.bak.20260803`。

> **运维教训**: ① 应用服务必须配 restart 策略，否则一次瞬时故障就永久下线；② restart 只解决"崩溃拉起"，仍需监控告警防"静默挂 2 天"；③ 大版本部署时避免整栈同时重启。

### 当前已知问题

| 问题 | 严重度 | 状态 |
|------|--------|------|
| 域名 ICP 备案 | 低 | 待服务器续费 3 个月以上 |
| HTTPS/SSL 配置 | 低 | 待备案后配置 |
