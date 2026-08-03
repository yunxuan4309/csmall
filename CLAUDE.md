# csmall-vue 项目上下文文档

> 本文档为 AI 助手提供项目背景、技术栈和部署信息，帮助快速理解项目结构。

---

## 一、项目概述

**项目名称**: csmall-vue  
**类型**: 微服务电商平台前端  
**技术栈**: Vue 3 + Vite + Element Plus + Pinia + Vue Router  
**目标部署**: 云服务器 + 域名 + HTTPS

**后端架构**: 多微服务架构（Spring Cloud Gateway + Nacos + Seata）

| 服务 | 端口 | 说明 |
|------|------|------|
| SSO | 10009 | 单点登录服务 |
| Gateway | 10087 | API 网关 |
| Admin | 10002 | 后台管理服务 |
| UMS | 10006 | 用户管理服务 |
| Front | 10004 | 前台商品服务 |
| Seckill | 10007 | 秒杀服务 |

---

## 二、开发环境配置

### 2.1 环境变量

**文件**: `.env.development`
```
VITE_API_SSO=http://localhost:10009
VITE_API_GATEWAY=http://localhost:10087
VITE_API_ADMIN=http://localhost:10002
VITE_API_UMS=http://localhost:10006
VITE_API_FRONT=http://localhost:10004
VITE_API_SECKILL=http://localhost:10007
```

**文件**: `.env`（通用配置）
```
VITE_TOKEN_KEY=mall_token
VITE_TOKEN_PREFIX=Bearer
VITE_TOKEN_EXPIRATION=604800
```

### 2.2 启动命令

```bash
npm install
npm run dev          # 开发服务器 http://localhost:5173
npm run build        # 生产构建
npm run preview      # 预览生产构建
```

---

## 三、生产环境部署规划

### 3.1 已解决：`.env.production` 与代码匹配

> **2026-07-26 已修复**：`VITE_API_BASE_URL` 与 5 个独立变量的不匹配问题已解决。`.env.production` 现为多变量配置，6 个 `VITE_API_*` 均指向同一服务器地址，由 Nginx 按路径前缀分发到 Gateway。完整说明见后端 `docs/项目上下文文档.md`。

### 3.2 建议的 Nginx 配置

```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    # 前端静态文件
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;  # History 模式支持
    }
    
    # API 反向代理
    location /api/sso/ {
        proxy_pass http://sso-service:10009/;
    }
    location /api/gateway/ {
        proxy_pass http://gateway:10087/;
    }
    location /api/ums/ {
        proxy_pass http://ums-service:10006/;
    }
    location /api/front/ {
        proxy_pass http://front-service:10004/;
    }
    location /api/seckill/ {
        proxy_pass http://seckill-service:10007/;
    }
}
```

### 3.3 部署步骤

1. **云服务器准备**
   - 购买云服务器（推荐 2核4G 以上）
   - 安装 Docker + Docker Compose
   - 开放端口：80, 443

2. **域名配置**
   - 购买域名并备案（国内服务器需要）
   - 配置 DNS A 记录指向服务器 IP
   - 申请 SSL 证书（Let's Encrypt 或云厂商）

3. **构建部署**
   ```bash
   # 本地构建
   npm run build
   
   # 上传到服务器（使用 scp 或 rsync）
   scp -r dist/ root@your-server:/usr/share/nginx/html/
   ```

4. **Docker 部署（推荐）**
   ```dockerfile
   # Dockerfile
   FROM nginx:alpine
   COPY dist/ /usr/share/nginx/html/
   COPY nginx.conf /etc/nginx/conf.d/default.conf
   EXPOSE 80
   ```

---

## 四、项目结构

```
csmall-vue/
├── src/
│   ├── api/              # API 接口封装
│   │   ├── request.js    # Axios 实例配置（5个微服务实例）
│   │   ├── seckill.js    # 秒杀相关 API
│   │   ├── order.js      # 订单相关 API
│   │   └── ...
│   ├── views/
│   │   ├── front/        # 前台页面
│   │   │   ├── seckill/  # 秒杀列表/详情
│   │   │   ├── order/    # 订单相关
│   │   │   └── product/  # 商品展示
│   │   └── admin/        # 后台管理
│   ├── store/            # Pinia 状态管理
│   ├── router/           # Vue Router 配置
│   └── main.js           # 入口文件
├── .env.development      # 开发环境变量
├── .env.production       # 生产环境变量（需完善）
├── vite.config.js        # Vite 配置（极简）
└── package.json
```

---

## 五、关键功能状态

| 功能 | 状态 | 备注 |
|------|------|------|
| 用户登录/注册 | 完成 | JWT Token 认证 |
| 商品列表/详情 | 完成 | 自动选中首个分类 |
| 购物车 | 完成 | 增删改查 |
| 订单创建 | 完成 | 省市区级联选择 |
| 订单支付 | 完成 | 支付宝沙箱 + 模拟支付双模式 |
| 秒杀列表/详情 | 完成 | 倒计时、SKU选择 |
| 秒杀下单 | 完成 | 随机码机制、限购 |
| 后台管理 | 部分 | 秒杀管理 API 已添加，缺 UI |

---

## 六、Git 配置

**远程仓库**: `https://github.com/yunxuan4309/csmall-vue.git`

**常用命令**:
```bash
git add -A
git commit -m "feat: xxx"
git push origin master
```

---

## 七、注意事项（给 AI 助手）

1. **API 响应格式**: 后端统一返回 `{ state: 200, message: "ok", data: {...} }`
2. **分页字段**: 不同服务可能使用 `list` 或 `records`，注意兼容
3. **Token 存储**: `localStorage.getItem('mall_token')`
4. **History 模式**: 路由使用 `createWebHistory`，需要 Nginx 配置回退
5. **空字符串问题**: 可选字段传空字符串可能触发后端校验，应传 `null`
6. **Element Plus 版本**: 2.13.6，注意 `label` 已弃用，改用 `value`

---

## 八、待办清单

- [x] 完善 `.env.production`（已改为多变量配置，2026-07-26）
- [x] 添加 Nginx 配置文件到仓库（`nginx.conf`）
- [x] 添加 Dockerfile 和 docker-compose.yml（`deploy/docker/`）
- [ ] 清理敏感日志（`store/frontUser.js` 中的密码日志）
- [ ] 组件按需引入优化包体积
- [ ] 添加 ESLint/Prettier 配置
- [ ] 后台管理秒杀页面开发

---

**文档更新时间**: 2026-05-15  
**当前 Commit**: 91b7db7

---

## 九、服务器部署速查

> 本仓库早期曾用 systemd 部署（旧 IP `8.156.85.160`，`/data/jars/`），**现已迁移为 Docker Compose 部署**。以下为当前（2026-08）实际状态，完整信息见 `docs/阿里云ECS服务器情况.md` 与 `docs/项目上下文文档.md`。

### 9.1 服务器信息

| 配置项 | 值 |
|--------|-----|
| 公网 IP | 8.156.77.197 |
| 登录用户 | ecs-user |
| 后端仓库 | D:\java\csmall |
| 前端仓库 | D:\Vue-Workspace\csmall-vue |
| 部署方式 | Docker Compose（22 容器），项目根目录 `/data/csmall/` |

### 9.2 /data 目录结构

| 目录 | 用途 |
|------|------|
| `/data/csmall/` | 项目根目录（docker-compose.yml、.env、jars/、frontend/） |
| `/data/csmall/.env` | **共享环境变量文件**（MySQL/Redis/RabbitMQ/AI Key 等） |
| `/data/csmall/jars/` | 11 个微服务 JAR 包 |
| `/data/csmall/frontend/` | 前端静态文件（dist/） |
| `/data/csmall-upload/` | 用户上传的图片文件（Nginx 直接静态服务） |

### 9.3 更新环境变量文件

每次 `/data/csmall/.env` 有改动（如新增环境变量），必须同步到服务器：

```bash
# 本地→服务器（以 deploy/docker/.env.example 为模板填写）
scp D:\java\csmall\deploy\docker\.env ecs-user@8.156.77.197:/tmp/.env

# SSH 到服务器后
sudo mv /tmp/.env /data/csmall/.env
cd /data/csmall && docker compose up -d     # 生效新环境变量并重启受影响容器
```

**重要**：改完 `.env` 后相关服务都要重启，否则新环境变量不生效。
