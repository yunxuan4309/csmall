# 问题解决：csmall-resource 容器 Nacos 注册竞态 + restart 缺失

> **日期**：2026-08-03
> **现象**：csmall-resource 容器 `Exited (1)` 持续 2 天，后台文件上传不可用；已上传图片由 Nginx 静态服务，前台浏览不受影响

---

## 一、现象

- `docker ps -a` 显示 `csmall-resource  Exited (1) 2 days ago`，`RestartCount=0`
- 后台文件上传（商品图片 / 品牌 Logo / 分类图标）不可用
- 已上传图片由 Nginx 从 `/data/csmall-upload` 直接静态服务，前台商品图片展示不受影响（所以"不影响核心功能"）

## 二、排查过程

### 2.1 崩溃日志定位（`docker logs csmall-resource --tail 120`）

关键异常链：

```
Error starting ApplicationContext. To display the condition evaluation report re-run your application with 'debug' enabled.
Caused by: org.springframework.context.ApplicationContextException: Failed to start bean 'webServerStartStop'
Caused by: java.lang.reflect.UndeclaredThrowableException
  at com.alibaba.cloud.nacos.registry.NacosServiceRegistry.register
Caused by: com.alibaba.nacos.api.exception.NacosException: Client not connected, current status:STARTING
  at com.alibaba.nacos.common.remote.client.RpcClient.request
```

**结论**：Nacos 服务注册失败导致 Spring 启动中止，而非 Flyway / 数据库问题。

### 2.2 排除错误方向

| 怀疑方向 | 排除依据 |
|---------|---------|
| Flyway 迁移失败 | 日志显示 Flyway 正常连库 `cs_mall_resource`，且 V1 已是 `CREATE TABLE IF NOT EXISTS` 幂等版本 |
| 数据库连接 | HikariPool 正常启动，无连接错误 |
| 配置错误 | env 配置（`ALIYUN_SERVER_IP=nacos`、datasource 覆盖等）与正常工作的服务完全一致 |
| Nacos 未就绪 | Nacos 容器 15:56 已启动，比 resource（16:34）早 38 分钟，健康检查通过 |

### 2.3 启动时间线（日志证据）

| 时间 | 事件 |
|------|------|
| 16:37:39 | `Starting MallResourceApplication` |
| 16:40:03 | Tomcat 初始化（**WebApplicationContext 初始化耗时 138734ms ≈ 138 秒**，异常缓慢） |
| 16:42:32 | Nacos 客户端开始连接：`Try to connect to server on start up, server main port = 8848` |
| 16:43:21 | 客户端仍 `STARTING`，Spring 执行注册时抛 `Client not connected` |
| 16:43:23 | `Failed to start bean 'webServerStartStop'` → 应用退出，退出码 1 |

### 2.4 验证网络当前正常（排查时）

- 从 sso 容器测试：`nacos` DNS 解析正常（172.18.0.2）、gRPC 9848 端口通
- Nacos HTTP 健康检查：`OK`
- Nacos 中 `mall-resource` 未注册（`hosts:[]`），证实它从未成功启动过

### 2.5 发现 restart 缺失（关键）

- 服务器 compose 中 22 个服务**只有 5 个**（product/front/order/search/seckill）配置了 `restart: on-failure`
- **mall-resource 等 6 个应用服务没有 restart** → 崩溃后 Docker 一次都不重试（`RestartCount=0`），服务永久下线

## 三、根因分析

**双重问题叠加：**

1. **启动竞态（根本原因）**：07-31 整栈 + SkyWalking 同时部署，4核16G 服务器负载过高，resource 的 Nacos gRPC 客户端连接（端口 **9848** = 主端口 8848 + 1000，需完成"能力握手"）被拖慢到 50s 仍未完成（客户端处于 `STARTING`）。Spring Cloud Alibaba 的 `NacosServiceRegistry.register()` 是**快速失败**的——客户端未就绪即抛异常，导致应用启动中止。Nacos 本身早已就绪，因此这是**时间敏感型竞态**，而非配置错误。

2. **缺失 restart 策略（恶化因素）**：resource 是 11 个应用服务中唯一漏配 restart 的服务，崩溃后 Docker 不重试，直接永久下线 2 天。

**事后验证**：服务器空闲状态下重启 resource，**29 秒**启动成功、Nacos 注册干净无报错，证明配置正确、纯属负载下的竞态。

## 四、修复方案

```bash
# 1. 备份 compose（重要操作先备份）
cp /data/csmall/docker-compose.yml /data/csmall/docker-compose.yml.bak.20260803

# 2. 给 mall-resource 加 restart: unless-stopped
#    在 container_name: csmall-resource 后插入一行：
#    restart: unless-stopped

# 3. 校验 YAML 并拉起
cd /data/csmall && docker compose config --quiet && docker compose up -d mall-resource

# 4. 验证
docker ps --filter name=csmall-resource                       # Up
docker inspect csmall-resource --format "{{.HostConfig.RestartPolicy.Name}}"   # unless-stopped
curl -s http://localhost:9060/actuator/health                 # {"status":"UP"}
curl -s "http://localhost:8848/nacos/v1/ns/instance/list?serviceName=mall-resource"  # hosts 非空、healthy:true
```

## 五、为什么选 `unless-stopped` 而非 `on-failure`

| 策略 | 行为 | 本项目选择理由 |
|------|------|----------------|
| `on-failure` | 仅非零退出码时重启 | 也能解决，但宿主机重启后不自动拉起 |
| **`unless-stopped`** | 任何退出都重启 + **宿主机重启后自动拉起**；手动 `docker stop` 的不拉起 | ✅ 单机 ECS 首选：宕机/维护重启后全站自愈，且不会误拉起你主动停掉的容器 |

## 六、运维教训

1. **应用服务必须配 restart 策略**——否则一次瞬时故障就永久下线，"悄悄挂 2 天没人发现"
2. **大版本部署避免整栈同时重启**——SkyWalking 全面接入 + 11 服务并发启动是本次竞态的诱因；可分批发灰度启动
3. **restart 只解决"崩溃拉起"，不解决"运行但坏了"**——还需要健康检查探针 + 监控告警（一个 cron 每 5 分钟 curl 存活检测即可起步）
4. **Nacos 2.x 注册是快速失败的**——gRPC 握手（9848 端口）在高压下可能超时，属于已知行为，运维上靠 restart 兜底
5. **排查方法论**：先看崩溃日志（`docker logs`）再下结论——本例日志直接指向 `NacosException`，避免了在 Flyway/数据库方向浪费时间

## 七、关联文档

- `docs/阿里云ECS服务器情况.md` — 2026-08-03 修复记录（含当前已知问题更新）
- `问题解决--SkyWalking部署与兼容性修复.md` — 整栈部署背景（Alpine→Debian、Metaspace、启动变慢）
- `问题解决--AI导购模块部署.md` — Nacos 双注册 / Dubbo 端口问题（同类 Nacos 注册问题）
