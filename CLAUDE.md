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

### 3.1 已知问题（部署前必须解决）

**CRITICAL**: `.env.production` 与代码不匹配
- `.env.production` 只定义了 `VITE_API_BASE_URL=/api`
- 但 `request.js` 使用了 5 个独立变量 `VITE_API_SSO/GATEWAY/UMS/FRONT/SECKILL`
- **解决方案**: 需要统一为单实例或使用 Nginx 按路径分发

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
| 订单支付 | 完成 | 模拟支付（无真实支付） |
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

- [ ] 完善 `.env.production` 或统一 API 实例
- [ ] 添加 Nginx 配置文件到仓库
- [ ] 添加 Dockerfile 和 docker-compose.yml
- [ ] 清理敏感日志（`store/frontUser.js` 中的密码日志）
- [ ] 组件按需引入优化包体积
- [ ] 添加 ESLint/Prettier 配置
- [ ] 后台管理秒杀页面开发

---

**文档更新时间**: 2026-05-15  
**当前 Commit**: 91b7db7

---

## 九、服务器部署速查

### 9.1 服务器信息

| 配置项 | 值 |
|--------|-----|
| 公网 IP | 8.156.85.160 |
| 登录用户 | ecs-user |
| 后端仓库 | D:\java\csmall |
| 部署方式 | systemd（JAR 在 `/data/jars/`） |

### 9.2 /data 目录结构

| 目录 | 用途 |
|------|------|
| `/data/jars/` | 所有微服务 JAR 包 |
| `/data/jars/csmall.env` | **共享环境变量文件**（MySQL/Redis/RabbitMQ/AI Key 等） |
| `/data/jars/logs/` | 应用日志 |
| `/data/elasticsearch/` | ES 数据 + IK 分词器 |
| `/data/frontend/` | 前端静态文件 |
| `/data/csmall-upload/` | 用户上传的图片文件 |

### 9.3 更新环境变量文件

每次 `csmall.env` 有改动（如新增环境变量），必须同步到服务器：

```powershell
# 本地→服务器
scp D:\java\csmall\deploy\systemd\csmall.env ecs-user@8.156.85.160:/tmp/

# SSH 到服务器后
sudo mv /tmp/csmall.env /data/jars/csmall.env
sudo systemctl restart mall-gateway mall-sso mall-product mall-front mall-search mall-order mall-seckill mall-ums mall-ams mall-resource mall-ai
```

**重要**：改完 `csmall.env` 后所有服务都要重启，否则新环境变量不生效。
