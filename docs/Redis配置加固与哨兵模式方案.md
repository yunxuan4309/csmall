# Redis 配置加固与哨兵模式方案

> **状态**: ✅ **基础加固（R1~R4）已于 2026-09-07 执行完毕**（requirepass/AOF/maxmemory/自定义 conf 全部生效，见 `docs/评估报告/TODO文件.md` 已完成表与 `docs/评估报告/TODO第一批实现与原理.md` §九）；**哨兵模式部分（主从 + 哨兵拓扑）仍未执行**，属 TODO #9/#42 范畴，待后续维护窗口。
> **背景**: 2026-08-21 生产 Redis 专项巡检确认 R1~R4 问题（见 `docs/评估报告/TODO文件.md`），经评估（服务器确认续费至 2026 年底）决定：**基础加固 + 哨兵高可用一次维护窗口完成**。
> **原则**: 记录方案；R1~R4 加固部分已执行（2026-09-07），哨兵部分执行前需用户确认 + 备份。

---

## 一、现状与目标

### 1.1 现状问题（已实测确认）

| 编号 | 问题 | 实测证据 |
|------|------|---------|
| R1 | Redis **无密码**，密码链路三处脱节 | `requirepass` 空；compose redis 无 environment；11 微服务无 `SPRING_DATA_REDIS_PASSWORD` |
| R2 | **无 AOF**，仅 RDB 快照，重启丢 60s~15min 数据 | `appendonly no` |
| R3 | **无内存上限** + `noeviction`，写满后拒绝写入 | `maxmemory 0` |
| R4 | 无自定义 redis.conf，所有调优重启即失 | 裸 `redis-server` 启动 |

### 1.2 目标拓扑（1 主 + 1 从 + 2 哨兵）

```
                        ┌────────────────────────────┐
   微服务 (11个) ──────► │  哨兵集群 (redis-sentinel-1/2) │  发现当前主节点
   Lettuce 客户端        │  端口 26379 ×2              │
                        └───────────┬────────────────┘
                                    │ 监控 + 故障转移
                        ┌───────────▼────────────────┐
                        │  redis-master (6379)        │  ← 写
                        │  redis-replica (6380)       │  ← 读复制（异步）
                        └────────────────────────────┘
```

- **为什么 1 从 2 哨兵**：演示场景内存友好；2 个哨兵可讲解 `quorum`/`majority` 概念。哨兵判定主下线需 `quorum`（设 1），触发故障转移还需 `majority`（2 哨兵 = 2）。注：2 哨兵时若挂掉 1 个哨兵，失去仲裁能力（不再切换，但主从复制不受影响）——演示可接受。
- **为什么不 3 哨兵**：多 1 个容器 + 讲解成本，演示项目不需要。

---

## 二、配置文件（部署时生成，存放于服务器 `/data/csmall/redis/`）

> 所有 conf 由部署脚本从模板 + `.env` 生成，避免手抄不一致。
> ⚠️ **关键坑**：Redis 主从/哨兵运行时会**自动 REWRITE 自身 conf**（记录角色、`replicaof`、发现的 master 地址），因此挂载**必须可写（不加 `:ro`）**；初始 conf 由部署脚本生成，运行时 Redis 自行维护。

### 2.1 `redis-master.conf`

```
bind 0.0.0.0
port 6379
requirepass <REDIS_PASSWORD>          # 由部署脚本替换 .env 值
masterauth <REDIS_PASSWORD>           # 从节点/哨兵连接本机认证
maxmemory 256mb
maxmemory-policy volatile-lru         # 只淘汰带 TTL 的键，保护永久购买标记 reseckill
appendonly yes
appendfsync everysec
dir /data
```

### 2.2 `redis-replica.conf`

```
bind 0.0.0.0
port 6380
requirepass <REDIS_PASSWORD>
masterauth <REDIS_PASSWORD>
maxmemory 256mb
maxmemory-policy volatile-lru
appendonly yes                        # 从节点也要 AOF：提升为主后直接可持久化
appendfsync everysec
dir /data
# replicaof 初始由部署脚本写入，运行后 Redis 自动维护
```

### 2.3 `sentinel1.conf` / `sentinel2.conf`（内容相同）

```
port 26379
sentinel monitor mymaster redis-master 6379 1
sentinel auth-pass mymaster <REDIS_PASSWORD>
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 15000
sentinel parallel-syncs mymaster 1
```

> 参数说明：`down-after-milliseconds 5000`（判定主下线阈值）、`failover-timeout 15000`、`parallel-syncs 1`（同时只允许 1 个从同步，防复制风暴）。

---

## 三、docker-compose.yml 改动（评审稿，未应用）

### 3.1 redis 服务改造 + 新增 3 个服务

```yaml
  # ==================== 缓存（主从 + 哨兵） ====================
  redis:
    image: redis:7-alpine
    container_name: csmall-redis
    command: ["redis-server", "/usr/local/etc/redis/redis.conf"]
    ports:
      - "127.0.0.1:6379:6379"        # 收紧为宿主机回环，防网卡直接暴露
    volumes:
      - redis_data:/data
      - ./redis/redis-master.conf:/usr/local/etc/redis/redis.conf
    mem_limit: 512m
    healthcheck:
      test: ["CMD-SHELL", "redis-cli -a \"$$REDIS_PASSWORD\" ping | grep PONG"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks: [csmall-net]

  redis-replica:
    image: redis:7-alpine
    container_name: csmall-redis-replica
    command: ["redis-server", "/usr/local/etc/redis/redis.conf"]
    ports:
      - "127.0.0.1:6380:6380"
    volumes:
      - redis_replica_data:/data
      - ./redis/redis-replica.conf:/usr/local/etc/redis/redis.conf
    mem_limit: 512m
    depends_on: [redis]
    networks: [csmall-net]

  redis-sentinel-1:
    image: redis:7-alpine
    container_name: csmall-redis-sentinel-1
    command: ["redis-sentinel", "/usr/local/etc/redis/sentinel.conf"]
    volumes:
      - ./redis/sentinel1.conf:/usr/local/etc/redis/sentinel.conf
    mem_limit: 128m
    depends_on: [redis, redis-replica]
    networks: [csmall-net]

  redis-sentinel-2:
    image: redis:7-alpine
    container_name: csmall-redis-sentinel-2
    command: ["redis-sentinel", "/usr/local/etc/redis/sentinel.conf"]
    volumes:
      - ./redis/sentinel2.conf:/usr/local/etc/redis/sentinel.conf
    mem_limit: 128m
    depends_on: [redis, redis-replica]
    networks: [csmall-net]
```

> ⚠️ 挂载**故意不加 `:ro`**：Redis 运行时 REWRITE 自身 conf（角色/`replicaof`/哨兵状态），只读会导致切换失败或"脑裂"。

### 3.2 volumes 新增

```yaml
volumes:
  mysql_data:
  redis_data:
  redis_replica_data:      # 新增
  rabbitmq_data:
  es_data:
```

### 3.3 11 个微服务 environment 改动（以 mall-seckill 为例，其余相同）

```yaml
    environment:
      SPRING_PROFILES_ACTIVE: prod
      # 删除: SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_SENTINEL_MASTER: mymaster
      SPRING_DATA_REDIS_SENTINEL_NODES: redis-sentinel-1:26379,redis-sentinel-2:26379
      SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}
      # 其余环境变量不变
```

> Spring Boot 3.x 属性前缀为 `spring.data.redis.sentinel.*`；Lettuce 自动从哨兵获取当前主节点，客户端**不再需要 host**。

### 3.4 `.env` 新增

```
# Redis（开启认证）
REDIS_PASSWORD=<强随机值，建议 32 位以上>
```

> 注意：开启认证必须"服务端 requirepass + 11 微服务 SPRING_DATA_REDIS_PASSWORD"**同一维护窗口同步生效**（顺序坑见 §5.3）。

---

## 四、执行步骤（维护窗口，预计停机 5~10 分钟）

> 全部命令在服务器执行，需用户确认后进行。**先备份，再动手。**

### Step 0 备份
```bash
cd /data/csmall
cp docker-compose.yml docker-compose.yml.bak.$(date +%Y%m%d)
cp .env .env.bak.$(date +%Y%m%d)
# 导出当前 Redis 数据兜底
docker exec csmall-redis redis-cli --rdb /tmp/dump-$(date +%Y%m%d).rdb
docker cp csmall-redis:/tmp/dump-*.rdb ./
```

### Step 1 生成配置文件
```bash
mkdir -p /data/csmall/redis
# 用部署脚本从 .env 生成 4 份 conf（替换 <REDIS_PASSWORD> 占位符）
# 或手动按 §二 创建，注意权限 chmod 600
```

### Step 2 修改 .env 与 docker-compose.yml
按 §三 修改（从本地仓库评审稿同步，或直接编辑服务器文件）。

### Step 3 先拉起 Redis 集群（微服务暂不动）
```bash
docker compose up -d redis redis-replica redis-sentinel-1 redis-sentinel-2
# 等待 10~20s 后验证 §五 的前 4 项，再继续
```

### Step 4 重建微服务（切换连接到哨兵）
```bash
docker compose up -d
```

### Step 5 验证 + 回归
见 §五、§六。

---

## 五、验证清单

### 5.1 基础加固验证
```bash
# 无密码连接应被拒绝
docker exec csmall-redis redis-cli ping                # 期望: NOAUTH Authentication required
# 带密码连接
docker exec csmall-redis redis-cli -a '<REDIS_PASSWORD>' ping   # 期望: PONG
# 持久化/内存配置生效
docker exec csmall-redis redis-cli -a '<REDIS_PASSWORD>' CONFIG GET appendonly   # yes
docker exec csmall-redis redis-cli -a '<REDIS_PASSWORD>' CONFIG GET maxmemory    # 268435456
```

### 5.2 主从复制验证
```bash
docker exec csmall-redis redis-cli -a '<REDIS_PASSWORD>' INFO replication
# 期望: role:master, connected_slaves:1, slave0:...state=online
docker exec csmall-redis-replica redis-cli -a '<REDIS_PASSWORD>' -p 6380 INFO replication
# 期望: role:slave, master_link_status:up

# 写主读从
docker exec csmall-redis redis-cli -a '<REDIS_PASSWORD>' SET sentinel_test ok
docker exec csmall-redis-replica redis-cli -a '<REDIS_PASSWORD>' -p 6380 GET sentinel_test   # ok
docker exec csmall-redis-replica redis-cli -a '<REDIS_PASSWORD>' -p 6380 DEL sentinel_test
```

### 5.3 哨兵验证（低峰期可选故障演练）
```bash
# 哨兵识别的主节点
docker exec csmall-redis-sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
# 期望: 返回 redis-master 6379

# 故障演练：kill 主节点
docker stop csmall-redis
# 等 ~15s（down-after 5s + failover 10s），观察哨兵日志
docker logs csmall-redis-sentinel-1 --tail 30
# 期望: +failover-state-select-slave → +switch-master mymaster redis-master 6379 redis-replica 6380
# 再查当前主
docker exec csmall-redis-sentinel-1 redis-cli -p 26379 SENTINEL get-master-addr-by-name mymaster
# 期望: redis-replica 6380（已提升为主）

# 恢复原主（哨兵会自动把原主降级为从并同步新主；原主 conf 可写是前提）
docker start csmall-redis
# 等待后验证：原主以 slave 角色重新加入
docker exec csmall-redis-sentinel-1 redis-cli -p 26379 SENTINEL replicas mymaster
# 恢复完成后可手动做一次反向切换（或保留现状）
```

### 5.4 业务回归
- 登录/退出（token 黑名单走 Redis）
- 秒杀完整流程：列表 → 详情（随机码）→ 下单（库存 DECR）→ 支付 → 购买标记 reseckill 生效（防重复购买）
- 购物车增删改查、AI 多轮对话（会话上下文）

---

## 六、风险与缓解

| 风险 | 缓解 |
|------|------|
| OOM（无 Swap，available 仅 2.2G） | 所有新实例 `mem_limit`（主从 512m / 哨兵 128m），实际占用 <150M；执行后观察 `free -h` |
| 切换期间秒杀短暂不可用 | 哨兵判定 5s + 切换 ~10s；故障演练选低峰；日常运行不切换无影响 |
| 配置错误导致微服务连不上 Redis | Step 3 先单独拉起集群验证，再重建微服务；失败则 `cp` 备份回滚 `docker compose up -d` |
| 密码明文存于 conf | 服务器文件 `chmod 600`；conf 不入仓库（.gitignore）；.env 已 gitignore |
| 2 哨兵挂 1 后失去仲裁能力 | 演示场景可接受；日常主从复制不受影响，哨兵容器健康检查可监控 |
| Redis REWRITE 覆盖手工修改 | 正确做法：改部署脚本模板再重新生成，不手工改运行时 conf |

---

## 七、决策记录

| 日期 | 事项 |
|------|------|
| 2026-08-21 | 巡检确认 R1~R4；评估哨兵"值得做"（用户确认续费至年底）；方案定稿，**未执行** |
| 2026-09-07 | ✅ R1~R4 基础加固执行完毕（requirepass/AOF/256mb/conf 挂载 + 6379 收窄 127.0.0.1）；哨兵拓扑未做，保留待 TODO #9/#42 |
| 待定 | 哨兵模式维护窗口（如需 HA，建议选业务低峰） |

---

**关联文档**：`docs/评估报告/TODO文件.md`（R1~R4 条目 + 学习计划 #9）、`docs/阿里云ECS服务器情况.md`、`docs/项目上下文文档.md`
