# 阿里云 ECS 云服务器情况

> 创建日期：2026-07-29
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

## 三、服务器目录结构

部署后（Docker Compose）目录规划：

```
/data/
├── csmall/                      # 项目根目录
│   ├── docker-compose.yml       # Docker 编排文件
│   ├── .env                     # 环境变量（从 .env.example 复制并填写真实值）
│   ├── mysql-init.sql           # 数据库初始化 SQL
│   ├── dockerfiles/             # Dockerfile（各模块构建用）
│   │   ├── mall-gateway.Dockerfile
│   │   ├── mall-sso.Dockerfile
│   │   └── ...
│   ├── jars/                    # 11 个微服务 JAR 包
│   │   ├── mall-gateway-server.jar
│   │   ├── mall-sso.jar
│   │   └── ...
│   ├── frontend/                # 前端部署
│   │   ├── Dockerfile
│   │   ├── nginx.conf
│   │   └── dist/                # Vue 构建产物
│   ├── database/                # SQL 建表脚本
│   └── volumes/                 # Docker 数据卷挂载点
│       ├── mysql/               # MySQL 数据
│       ├── redis/               # Redis 数据
│       ├── es/                  # Elasticsearch 数据
│       └── rabbitmq/            # RabbitMQ 数据
└── csmall-upload/               # 用户上传文件存储
    ├── picture/
    ├── brand-logo/
    └── category-icon/
```

---

## 四、中间件配置（Docker 容器）

| 服务 | 容器名 | 内部端口 | 外部访问 | 账号 | 密码 |
|------|--------|---------|---------|------|------|
| MySQL 8.0 | csmall-mysql | 3306 | 仅容器内 | root | `.env` 中 `MYSQL_ROOT_PASSWORD` |
| Redis 7 | csmall-redis | 6379 | 仅容器内 | — | `.env` 中 `REDIS_PASSWORD`（可选） |
| Nacos 2.5.2 | csmall-nacos | 8848 / 9848 | 仅容器内 | nacos | nacos |
| RabbitMQ 4 | csmall-rabbitmq | 5672 / 15672 | 仅容器内 | `.env` 中配置 | `.env` 中配置 |
| Elasticsearch 8.6 | csmall-es | 9200 / 9300 | 仅容器内 | — | 无密码 |
| Seata 2.1.0 | csmall-seata | 8091 / 7091 | 仅容器内 | seata | seata |
| Sentinel 1.8.6 | csmall-sentinel | 8090 | 仅容器内 | sentinel | sentinel |

### 外部访问说明

目前仅开放 HTTP(80) 端口。如需从本地访问 Nacos/Sentinel 等管理界面，可通过 SSH 隧道：

```bash
# Nacos 控制台
ssh -L 8848:localhost:8848 ecs-user@8.156.77.197

# Sentinel Dashboard
ssh -L 8090:localhost:8090 ecs-user@8.156.77.197

# RabbitMQ 管理界面
ssh -L 15672:localhost:15672 ecs-user@8.156.77.197
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
| 8091 | Seata RPC | ❌ 仅 VPC 内网 |
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

## 七、Docker 安装（待执行）

```bash
# SSH 登录后执行
sudo apt update
sudo apt install -y docker.io docker-compose-v2
sudo systemctl enable docker --now
sudo usermod -aG docker ecs-user
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
