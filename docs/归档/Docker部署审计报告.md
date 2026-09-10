# CoolShark Docker 容器化部署 — 全面审计报告

> 审计日期：2026-07-27
> 审计范围：11 个后端微服务模块 + 前端 Vue 项目 + 部署配置 + 凭据管理
> **📦 归档日期：2026-09-10** —— 归档原因：**一次性基线快照**，核心结论「不能直接 Docker 部署」已完全过时（项目已 21 容器稳定运行）；8 项发现中 5 项已解决、1 项已失效。与 [[企业级生产评估报告-2026-08-15]] 同性质。
> ⚠️ **归档前已逐项实测复核（见下方对照表）—— "未收口的活待办"已全部转成 TODO，不会因归档而丢失。**

---

## 📊 归档时（2026-09-10）现状对照表

> **本表是唯一可信的"现状"来源**；下方正文保留为**当时（2026-07-27）的原始审计记录**，不要再据正文判断现状。

| # | 审计发现（2026-07-27） | 2026-09-10 实测现状 | 判定 |
|---|---|---|---|
| 1 | Gateway 6/8 条路由 `http://127.0.0.1` 在容器内全断 | ✅ **已修**：prod 路由已全部 `lb://`（唯一非 lb 的是 `http://mall-product:9010` **容器名直连**，不是 127.0.0.1）；`mall-resource` 也已补 `nacos-discovery` 依赖 | 已解决 |
| 2 | 前端 `nginx.conf` 12 条 API 路径仅 1 条被代理（+ SSE 缓冲未关） | ✅ **已修**：已有 `/ai/chat/stream` 精确匹配 + `proxy_buffering off`；`location ~ ^/(admin\|sso\|oms\|front\|seckill\|ai\|search\|user\|ums\|pms\|resource\|ams)/` 全路径代理 + `/upload/` + 静态资源直供 | 已解决 |
| 3 | 11 个微服务**没有健康检查端点** | ✅ **端点侧已解决**：11 个 webapi 模块 pom **全部引入** `spring-boot-starter-actuator`，11 个 `application.yml` **全部配置** `management.endpoints.web.exposure.include: health,info`；服务器实测 `/actuator/health` 返回 **200**<br>⏳ **但"容器级 healthcheck"仍未做**（compose 里 11 个微服务仍无 `healthcheck:`，`docker ps` 里只有 6 个中间件显示 `(healthy)`）→ **见 TODO #40（Step 1 已完成 / Step 2 待做）** | 已解决（**留一半给 #40**）|
| 4 | JWT 签名密钥硬编码在 10 个模块 | ✅ **已修**（TODO #25）：11 容器 `JWT_SECRET` 实测为 **64 位随机值且互相同一**、非默认值 | 已解决 |
| 5 | **真实 API Key 泄露在源码** | ✅ **已完全收口（2026-09-10 吊销旧 key + 轮换新值）**：<br>· DeepSeek key `sk-****…` → **未进 git 历史** ✅<br>· 当前生产 DeepSeek key `sk-****…` → **未进 git 历史**（仅在被 gitignore 的本地 `.env`）✅<br>· **硅基流动 embedding key `sk-***（已吊销 2026-09-10）…` → 已随 commit `719ff6f` push 进"公开"仓库历史 = 已公开泄露** → **已于 2026-09-10 吊销并轮换新 key → TODO #44 ✅ 已完成**<br>· `git ls-files deploy` 复核：**21 个被跟踪文件全是模板/配置（只有 `.env.example`），无真 Key** ✅ | ✅ **已收口（2026-09-10 吊销+轮换）** |
| 6 | CORS 域名硬编码 | ❌ **仍未做**：`mall-gateway-server/.../CorsConfig.java` 实测仍是 `addAllowedOrigin("http://…")` 硬编码 3 处 | 活待办 → **#22** |
| 7 | systemd JAR 文件名不一致 | ⚪ **已失效**：项目已全量 Docker 化，systemd 部署方式弃用 | 过时 |
| 8 | 前端遗留：明文密码日志 / 硬编码 IP | ✅ `console.log(password)` 实测**已清理**；⚪ 旧 IP `8.156.85.160` 仍散落在 5 份文档与 `deploy/*.env`（历史遗留，无功能影响）| 基本解决 |

> **关联（归档时）**：[[TODO文件]] #40（healthcheck Step 2）/ **#44（✅ 2026-09-10 已完成：旧 embedding key 已吊销 + 轮换新值；衍生 #59 余额不足）** / #22（CORS）；[[问题解决--容器构建与编排卫生]]（容器编排类问题合集）；[[企业级生产评估报告-2026-08-15]]（同期同性质的归档件）。

---

## 一、总体结论

**当前状态：不能直接 Docker 部署。** 项目按 systemd 裸机部署设计，缺少 Dockerfile、docker-compose.yml、健康检查端点，且 Gateway 路由和前端 Nginx 配置在容器环境下存在致命问题。

**代码质量评估**：良好。环境变量驱动、优雅关闭、日志写 stdout 等方面已具备容器化基础。

---

## 二、阻塞级问题（部署前必须解决）

### 🔴 问题 1：Gateway 路由 — 6/8 条在 Docker 中全断

**文件**：`mall-gateway-server/src/main/resources/application-prod.yml`

**根因**：6 条路由使用 `http://127.0.0.1:PORT` 直连。Docker 容器内 `127.0.0.1` 指向容器自身而非目标服务，请求 Connection Refused。

| 路由 ID | 当前 URI | Docker 兼容？ |
|---------|---------|--------------|
| `mall-front` | `http://127.0.0.1:10004` | ❌ 断 |
| `mall-order` | `lb://mall-order` | ✅ 正常 |
| `mall-search` | `http://127.0.0.1:10008` | ❌ 断 |
| `mall-sso` | `lb://mall-sso` | ✅ 正常 |
| `mall-resource` | `http://127.0.0.1:9060` | ❌ 断 |
| `mall-seckill` | `http://127.0.0.1:10007` | ❌ 断 |
| `mall-ai` | `http://127.0.0.1:10010` | ❌ 断 |
| `mall-ums-register` | `http://127.0.0.1:10006` | ❌ 断 |

**解决**：6 条路由全部改为 `lb://` 格式，与 `application-test.yml` 对齐：
- `http://127.0.0.1:10004` → `lb://mall-front`
- `http://127.0.0.1:10008` → `lb://mall-search`
- `http://127.0.0.1:9060` → `lb://mall-resource`
- `http://127.0.0.1:10007` → `lb://mall-seckill`
- `http://127.0.0.1:10010` → `lb://mall-ai`
- `http://127.0.0.1:10006` → `lb://mall-ums`

**额外依赖**：`mall-resource` 当前没有 Nacos 服务发现依赖，需在 pom.xml 中添加 `spring-cloud-starter-alibaba-nacos-discovery`。

Gateway 已有 `spring-cloud-starter-loadbalancer` 依赖，无需额外引入。

---

### 🔴 问题 2：前端 nginx.conf — 12 条 API 路径仅 1 条被代理

**文件**：`D:\Vue-Workspace\csmall-vue\nginx.conf`

**根因**：nginx 只配置了 `/api/` 和 `/upload/` 两个代理，但前端实际请求使用 12 个不同路径前缀。所有未匹配路径命中 `location / { try_files ... /index.html; }`，API 请求返回 HTML 页面。

**前端实际使用的 API 路径（从 src/api/*.js 中提取）**：

| 路径前缀 | 用途 | 当前被代理？ |
|----------|------|-------------|
| `/admin/` | 管理员登录、后台管理 | ❌ |
| `/sso/` | SSO 单点登录 | ❌ |
| `/oms/` | 订单、购物车、支付 | ❌ |
| `/front/` | 前台商品展示 | ❌ |
| `/seckill/` | 秒杀活动 | ❌ |
| `/ai/` | AI 导购（含 SSE 流式） | ❌ |
| `/search/` | 商品搜索 | ❌ |
| `/user/` | 用户信息 | ❌ |
| `/ums/` | 用户管理 | ❌ |
| `/pms/` | 商品管理（后台） | ❌ |
| `/resource/` | 文件资源 | ❌ |
| `/upload/` | 文件上传 | ✅ |

**SSE 特殊问题**：`/ai/chat/stream` 是服务端推送流式端点。Nginx 默认缓冲响应，必须对该路径设置 `proxy_buffering off;` + `proxy_cache off;`，否则 SSE 逐字输出退化为一次性加载。

**推荐 nginx 配置**：

```nginx
server {
    listen 80;
    server_name localhost;

    # SSE 流式路径：必须关闭缓冲（放在通用 /ai/ 之前精确匹配）
    location = /ai/chat/stream {
        proxy_pass http://gateway:10087;
        proxy_buffering off;
        proxy_cache off;
        proxy_set_header Connection '';
        proxy_http_version 1.1;
        chunked_transfer_encoding on;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 其余 API：统一代理到 Gateway（Gateway 内部按路径路由）
    location ~ ^/(admin|sso|oms|front|seckill|ai|search|user|ums|pms|resource)/ {
        proxy_pass http://gateway:10087;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /upload/ {
        proxy_pass http://gateway:10087;
        client_max_body_size 10m;
    }

    # 静态资源：图片由 Nginx 直接从挂载卷提供
    location ~* \.(jpg|jpeg|png|gif|webp|svg|ico)$ {
        root /data/csmall-upload;
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    # SPA 回退
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }
}
```

---

## 三、高优先级问题（上线前必须解决）

### 🟡 问题 3：11 个微服务全部没有健康检查端点

**根因**：`spring-boot-starter-actuator` 在父 POM 的 `<dependencyManagement>` 中声明，但**零个子模块引入**。所有模块无 `management.endpoints` 配置。

**影响**：
- `docker-compose.yml` 中 `depends_on` 无法使用 `condition: service_healthy`
- 容器启动后无法判断服务是否就绪（Nacos 注册完成？DB 连接成功？）
- K8s liveness/readiness probe 无法配置

**解决**：
1. 每个 webapi 模块的 `pom.xml` 添加：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```
2. 每个模块的 `application-prod.yml` 添加：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized
```

**日志状态（好消息）**：所有 11 个模块已只写 stdout（`logging.pattern.console` 配置完善，MyBatis 使用 `StdOutImpl`）。无 logback XML 文件，无 `logging.file` 配置。Docker 日志驱动可直接收集，无需修改。

---

### 🟡 问题 4：JWT 签名密钥硬编码在 10 个模块中

**根因**：`CooxiaoMall2026JwtSecretKeyForHs512AlgorithmMustBeAtLeast64BytesLong` 这个相同的密钥字符串硬编码在 10 个模块的 `application.yml` 中，不通过环境变量读取。

**影响**：
- 源码泄露 → JWT 可被任意伪造
- 换密钥需改 10 个文件，部署时容易遗漏
- 不符合 12-Factor App 的配置管理原则

**解决**：所有模块的 `application.yml` 中改为：
```yaml
jwt:
  secret: ${JWT_SECRET:CooxiaoMall2026JwtSecretKeyForHs512AlgorithmMustBeAtLeast64BytesLong}
```
Docker 中通过环境变量注入随机强密钥。

---

### 🟡 问题 5：真实 API Key 泄露在源码中

**位置**：
- `mall-ai/mall-ai-webapi/src/main/resources/application-test.yml` — DeepSeek API Key (`sk-****...`) 和硅基流动 Key (`sk-***（已吊销 2026-09-10）...`)
- `deploy/systemd/csmall.env` — MySQL/Redis/RabbitMQ 密码 + DeepSeek Key

**风险**：虽被 `.gitignore` 排除，但 Key 存在于本地文件系统和 Git 历史中。

**解决**：
1. 在 DeepSeek 和硅基流动后台**撤销当前 Key**，重新生成
2. `application-test.yml` 改为 `${AI_API_KEY:sk-placeholder}`
3. 本地开发通过 IDEA 环境变量注入真实 Key
4. 创建 `.env.example` 模板（不含真实值）供部署参考

---

## 四、中低优先级问题

### 🟢 问题 6：CORS 域名硬编码

**文件**：`mall-gateway-server/.../config/CorsConfig.java`

**根因**：`addAllowedOrigin` 写死 5 个域名，不通过配置文件读取。

**解决**：改为从配置读取 `@Value("${cors.allowed-origins}")`，生产换域名时无需重新编译。

---

### 🟢 问题 7：systemd JAR 文件名不一致

**文件**：`deploy/systemd/mall-product.service`、`mall-resource.service`

**根因**：引用 `-1.0.0.jar`，但父 POM 版本是 `0.0.1-SNAPSHOT`。

**影响**：systemd 部署会找不到 JAR。Docker 部署不受影响（Dockerfile 自行控制 JAR 路径）。

---

### 🟢 问题 8：前端遗留问题

| 问题 | 严重度 | 说明 |
|------|--------|------|
| `frontUser.js` 打印明文密码日志 | 中 | 第 17-18 行 `console.log(password)`，生产需删除 |
| `.env.production` 硬编码 IP | 低 | `http://8.156.85.160` 应改为域名 |
| Element Plus 全量导入 | 低 | 未配置按需导入，包体积偏大 |
| 无 ESLint/Prettier | 低 | 代码风格未统一 |

---

## 五、部署前操作清单

```
阻塞（必须）：
□ 1. Gateway application-prod.yml: 6 条路由 http://127.0.0.1 → lb://
□ 2. mall-resource 添加 Nacos 服务发现依赖
□ 3. 前端 nginx.conf 重写：补充全部 API 路径代理 + SSE 关闭缓冲
□ 4. 创建 11 个 Dockerfile + 根级 docker-compose.yml

高优（上线前）：
□ 5. 11 个 webapi pom.xml 添加 actuator 依赖
□ 6. 所有 application-prod.yml 添加 management.endpoints 配置
□ 7. 10 个 application.yml 中 JWT secret 改为 ${JWT_SECRET:...}
□ 8. 撤销并轮换源码中泄露的 API Key
□ 9. 创建 .env.example 模板文件

中低优（可渐近）：
□ 10. CORS 域名改为配置文件驱动
□ 11. 清理前端调试日志
□ 12. 前端 .env.production 改为域名
```

---

## 六、环境变量清单（供 Docker .env 文件参考）

| 变量 | 用途 | 模块 |
|------|------|------|
| `ALIYUN_SERVER_IP` | Nacos/基础服务地址 | 全部 11 个 |
| `MYSQL_USERNAME` | 数据库用户名 | sso, ams, ums, product, seckill, order, resource |
| `MYSQL_PASSWORD` | 数据库密码 | 同上 |
| `REDIS_PASSWORD` | Redis 密码 | 全部 11 个 |
| `RABBITMQ_USERNAME` | RabbitMQ 用户名 | seckill |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 | seckill |
| `JWT_SECRET` | JWT 签名密钥（建议新增） | 除 gateway 外的 10 个 |
| `AI_API_KEY` | DeepSeek API Key | ai |
| `AI_API_BASE_URL` | DeepSeek API 地址 | ai |
| `EMBEDDING_API_KEY` | 硅基流动 Embedding Key | ai |
| `RESOURCE_HOST` | 文件资源访问 URL | product, ai, resource |
| `ALIPAY_APP_ID` | 支付宝 APPID | order |
| `ALIPAY_PRIVATE_KEY` | 支付宝应用私钥 | order |
| `ALIPAY_PUBLIC_KEY` | 支付宝公钥 | order |
| `ALIPAY_NOTIFY_URL` | 支付宝异步通知地址 | order |
| `ALIPAY_RETURN_URL` | 支付宝同步跳转地址 | order |

---

> **关联文档**：`项目上下文文档.md`、`部署注意文档.md`、`TODO文件.md`
