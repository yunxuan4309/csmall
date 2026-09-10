# TODO 第一批实现与原理（已切割为问题解决文档 · 现为索引 + 执行记录 + 面试话术）

> **状态**: ✅ **已执行完毕（2026-09-07 维护窗口；2026-09-10 逐项复核确认）**——#25 JWT 随机化、R7 内存（mem_limit+Nacos/Sentinel 降堆）、#24 MySQL 强密码+端口收窄、R1~R4 Redis 加固、#38 Dockerfile 清理 **全部完成并回归通过**；**Swap 2G 亦已执行**（复核实测 `/swapfile 2G`）。
> **⚠️ 2026-09-10 重要变更：本文件的技术正文已【切割】到 `docs/问题解决/` 的 8 个「一类问题」文档** —— 本文件现在只保留 **索引 + 执行记录 + 面试话术**。原因：原文按批次组织，而面试要讲的是"**一类问题**怎么识别与解决"；同一本质的问题（安全 / 资源 / 一致性 / 可靠性 / 静默失效…）分散在两批文件里，切割后按类聚合、**共性解法一眼可见**。
> **用途**: ① 批次一的**分类索引**（下节表格）② 批次一的**执行记录**（§六 部署清单 + §七 结果快照）③ **通用面试话术**（§五）
> **关联**: [[TODO文件]] 第一批（#25 / R7 / #24 / R1~R4 / #38）、[[TODO第二批实现与原理]]（同为已切割薄索引）、`docs/问题解决/` 8 个分类文档

---

## 一、📌 正文切割索引（2026-09-10）

> **技术正文（原理 / 实现 / 踩坑 / 逐条话术）已全部迁入下列文档**，本文件的对应章节已移除。

| 原章节 | 内容 | 现状去向 |
|---|---|---|
| §一 | **#25** JWT_SECRET 生产随机化 | → [[问题解决--生产安全加固与凭据治理]] §二 |
| §二 | **R7** 内存优化 | → [[问题解决--资源治理与流量防护]] §二 |
| §三 | **#24** MySQL 强密码 + 端口收窄 | → [[问题解决--生产安全加固与凭据治理]] §三 |
| §四 | **R1~R4** Redis 加固 | → [[问题解决--Redis加固与主从数据一致性]] §二 |
| §五 | **#38** Dockerfile 双份清理 | → [[问题解决--容器构建与编排卫生]] §二 |
| §六 | 本地改动清单 | → [[问题解决--容器构建与编排卫生]] §四 附录 |
| §七 | 服务器部署执行清单 | **保留在本文件 §六**（一次性执行手册，非"一类问题"）|
| §八 | 第一批通用面试话术 | **保留在本文件 §五** |
| §九 | 实战执行记录（3 个坑）| §9.1 结果快照 → **保留在本文件 §七**；§9.2 RDB→AOF 丢数据 + §9.4 conf 权限 → [[问题解决--Redis加固与主从数据一致性]] §四；§9.3 gateway 启动竞态 → [[问题解决--容器构建与编排卫生]] §三 |

**8 个分类文档总览**（两批共同切割，见 [[TODO第二批实现与原理]] §一 同一张表）：

| 分类文档 | 一类问题的本质 |
|---|---|
| [[问题解决--生产安全加固与凭据治理]] | 凭据/认证"看着配了、实际等于没有" |
| [[问题解决--Redis加固与主从数据一致性]] | 异步复制的中间件如何在故障下不丢数据、不失真 |
| [[问题解决--资源治理与流量防护]] | 怎么给系统设资源与流量的硬边界 |
| [[问题解决--服务注册与网关路由]] | 一个服务在注册中心有多个身份 → 路由打错端口 |
| [[问题解决--消息可靠性与毒消息治理]] | 消息不丢、不毒、不无限重试 |
| [[问题解决--请求校验与静默失效]] | 不报错 ≠ 生效 |
| [[问题解决--搜索双索引与降级分层]] | 架构债 + 降级分层：慢≠挂、进程级降级才是真降级 |
| [[问题解决--容器构建与编排卫生]] | 单一事实源 + 容器编排的隐含约束 |

---

## 二、第一批全景（先记住这张表）


| # | 事项 | 本质 | 为什么第一批 | 本地已做 | 服务器待做 |
|---|------|------|-------------|---------|-----------|
| **#25** | JWT_SECRET 生产随机化 | 安全洞（伪造 token） | clone 仓库即可伪造任意用户 JWT = **最严重** | `.env.example` 注释/生成说明 | .env 换强随机 + 重建 11 服务 |
| **R7** | 内存优化 | 资源红线（OOM 风险） | available 2.0G、无 Swap、mem_limit=0 | compose 加 mem_limit + Nacos/Sentinel 降堆 | `docker update` 即时生效 + 重建 nacos/sentinel + Swap（sudo）|
| **#24** | MySQL 强密码 + 端口收窄 | 安全（弱密码+全映射） | `MYSQL_ROOT_PASSWORD=root` | compose 端口 `127.0.0.1:3306` | .env 强密码 + `ALTER USER`（关键坑）+ 重建 7 个 DB 服务 |
| **R1~R4** | Redis 加固 | 安全 + 数据持久化 | 无密码/AOF/maxmemory | compose + conf 模板 | .env 密码 + 部署 conf + 重建 redis + 11 服务 |
| **#38** | Dockerfile 双份清理 | 运维洁癖（防踩坑） | 5 分钟低成本 | ✅ 已 git rm 11 份 | 无（纯仓库）|

**执行策略**: #38 + R7 可立即做（零/低风险）；#25/#24/R1~R4 需**同一维护窗口**原子切换（全站重建一次），Swap 需用户 sudo。

> 📌 **本表是"执行前"视角**（"服务器待做"列即当时的待办）。**7 项已全部执行完毕**，结果见 §九；表中 `127.0.0.1` 等细节的现状变化见 §3.3 ⚠️ 与 §六 ⚠️。

---


> 📌 **本表是"执行前"视角**（"服务器待做"列即当时的待办）。**7 项已全部执行完毕**，结果见 §七；表中 `127.0.0.1` 等细节的现状变化见 [[问题解决--生产安全加固与凭据治理]] §三（端口已改绑宿主私网 IP）。

---

## 三、执行记录：批次一服务器部署执行清单（✅ 已于 2026-09-07 执行完毕）

> ⚠️ **以下为批次一的"当时怎么做的"记录**，不是待办。相关**原理与踩坑**见 [[问题解决--生产安全加固与凭据治理]] / [[问题解决--Redis加固与主从数据一致性]] / [[问题解决--资源治理与流量防护]]。


> AI 无写 /data/csmall 权限 + 无 sudo → 以下文件操作用 ecs-user 执行，docker 操作用 <AI账号> 可执行。
> ✅ **本清单 7 个 Step 均已于 2026-09-07 维护窗口执行完成**（含 Step 7 Swap，2026-09-10 复核实测 `/swapfile 2G` 已在用）。下列内容**保留作为"当时怎么做的"记录**，不是待办。

### Step 0 备份（必须）
```bash
cd /data/csmall
cp docker-compose.yml docker-compose.yml.bak.$(date +%Y%m%d)
cp .env .env.bak.$(date +%Y%m%d)
docker exec csmall-redis redis-cli --rdb /tmp/dump-$(date +%Y%m%d).rdb && docker cp csmall-redis:/tmp/dump-*.rdb ./
```

### Step 1 生成强密码（openssl rand，见 §1.3）
```bash
echo "MYSQL=$(openssl rand -base64 32)"   # 记入 .env MYSQL_ROOT_PASSWORD
echo "REDIS=$(openssl rand -base64 32)"   # 记入 .env REDIS_PASSWORD
echo "JWT=$(openssl rand -base64 48)"     # 记入 .env JWT_SECRET
```

### Step 2 同步文件（ecs-user）
- 本地仓库 `deploy/docker/docker-compose.yml` → `/data/csmall/docker-compose.yml`
- 本地 `deploy/docker/redis/redis-master.conf` → `/data/csmall/redis/redis-master.conf`（**替换 `<REDIS_PASSWORD>`**，chmod 600）
- `.env` 更新三处密码

> ⚠️ **本步的 `chmod 600` 是错的（执行时踩坑，已修正）**：conf 属主是宿主机 `ecs-user`，而容器内 redis 以 **uid 999** 运行 → 600 导致 `can't open config file: Permission denied`。**最终解法是 `chown redis:redis` + 600**（属主必须是容器内运行用户）。完整踩坑记录见 **§9.4**。

### Step 3 MySQL 改密码（docker exec，AI 可执行）
```bash
docker exec csmall-mysql mysql -uroot -p'root' -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '<新MYSQL密码>'; ALTER USER 'root'@'%' IDENTIFIED BY '<新MYSQL密码>'; FLUSH PRIVILEGES;"
```

### Step 4 R7 即时生效（AI 可执行，零重启）
```bash
docker update --memory 1g csmall-product csmall-order csmall-seckill csmall-front csmall-ai
docker update --memory 768m csmall-sso csmall-ums csmall-ams csmall-gateway csmall-search csmall-resource
docker update --memory 1.5g csmall-es csmall-skywalking-oap
docker update --memory 1g csmall-nacos csmall-mysql
docker update --memory 512m csmall-sentinel csmall-seata csmall-skywalking-ui
docker update --memory 256m csmall-rabbitmq csmall-redis csmall-frontend
```

### Step 5 全量重建（AI 可执行，5~15 分钟窗口）
```bash
cd /data/csmall && docker compose up -d --remove-orphans
```

### Step 6 验证清单
```bash
# Redis 加固
docker exec csmall-redis redis-cli ping                              # NOAUTH（无密码被拒）
docker exec csmall-redis redis-cli -a '<REDIS>' ping                # PONG
docker exec csmall-redis redis-cli -a '<REDIS>' CONFIG GET appendonly maxmemory maxmemory-policy   # yes / 268435456 / volatile-lru
# JWT 一致性（11 容器必须全部 = 新值）
for c in csmall-sso csmall-product csmall-order csmall-seckill csmall-ums csmall-ams csmall-front csmall-search csmall-ai csmall-resource csmall-gateway; do
  docker inspect $c --format '{{.Name}}: {{range .Config.Env}}{{println .}}{{end}}' | grep ^JWT_SECRET= | cut -c1-40
done
# 内存
free -h; docker stats --no-stream | head -25
# 业务回归：登录（新 token 200）/ 旧 token 401 / 秒杀完整流程 / 后台管理
```

### Step 7 Swap（ecs-user sudo，独立执行）✅ **已执行**
```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```
> ✅ **2026-09-10 复核实测**：`swapon --show` → `/swapfile file 2G 0B -2`；`free -h` → `Swap: 2.0Gi 0B 2.0Gi`。**已落盘 fstab**（重启后仍生效）。

### 回滚
- mem_limit: `docker update --memory -1 <容器>`
- 密码类: .env 改回旧值 + `ALTER USER` 回旧密码 + 重建
- 全部: cp 备份还原 + `docker compose up -d`

---


---

## 四、📎 附带说明：批次一之后的两处形态变化（2026-09-10 复核）

| 项 | 批次一时 | 现在 |
|---|---|---|
| MySQL / Redis 宿主端口绑定 | `127.0.0.1:3306` / `127.0.0.1:6379` | **`172.29.193.239:3306` / `172.29.193.239:6379`**（第三批跨机集群为让新机访问而改绑宿主私网 IP，**不是 `0.0.0.0`**）|
| `redis-master.conf` | 10 行（R1~R4）| 追加 4 行（第 11/12/15/16 行：`replica-announce-ip/port` + `min-replicas-*`）—— ⚠️ **`bind 0.0.0.0` 是容器内监听，至今未变**（第三批改的是**宿主侧端口绑定**，不是这行 conf）|
| 仓库 compose 服务数 | 21（老机）| **26**（两机合并的单一文件：老机 21 + 新机 5）|
| `deploy/docker/dockerfiles/` | 11 份 | **12 份**（+ `mall-seckill-replica.Dockerfile`）|

---

## 五、第一批通用面试话术（贯穿主线）


**主线叙事**："我给生产做了一轮安全+资源审计，实测发现 4 类真实问题（JWT 密钥=默认值、MySQL root 弱密码、Redis 裸奔、内存无硬限），按'会真出事'排序分三批修复。第一批的共性难点是**多服务配置原子切换**——JWT/MySQL/Redis 都要求'服务端 + 全部客户端同窗口变更'，我通过①同一维护窗口②改动前逐容器 env 基线核对③重建后逐容器验证一致性 来控制风险。过程中把'密钥轮换为什么不能只 restart''端口收窄为什么零影响''AOF 和 RDB 怎么选'这些原理也吃透了。"

**被追问"为什么不等公司方案/为什么自己动手"时**：这是个人项目（演示+面试用），我自己就是 owner——但每个决策都对齐企业做法（kid 轮换/KMS/最小权限），说明我知道生产标准是什么、当前取舍是为什么。

---

## 六、执行结果快照（2026-09-07，2026-09-10 复核）

| 验证项 | 结果（2026-09-07 当时） | 2026-09-10 复核 |
|---|---|---|
| 21 容器 | 全部 Up，RestartCount=0，无 unhealthy | ✅ 仍 21 容器全 Up；mem_limit 全部非 0 |
| 内存 | available **2.0G → 3.9G**（mem_limit + Nacos 512m 生效）| ✅ Nacos `JVM_XMX=512m`、Sentinel `-Xmx256m` 实测生效；available 3.5G（正常波动）|
| MySQL | root 强密码（43 位），`127.0.0.1:3306` 收窄，ALTER USER 双 host 完成 | ✅ 密码 **44 位**；监听 **`172.29.193.239:3306`**（第三批改绑私网 IP，见 §3.3 ⚠️）；`root@localhost` + `root@%` 均在 |
| Redis | requirepass/AOF/maxmemory 256mb/volatile-lru 全部生效，`127.0.0.1:6379` | ✅ `appendonly=yes`、`maxmemory=268435456`、`maxmemory-policy=volatile-lru`、`min-replicas-to-write=1`；宿主监听 **`172.29.193.239:6379`** |
| JWT | 11 容器 env 全部 = 新随机值且互相一致 | ✅ 11 容器 len=64、去重后同一值、非默认 |
| 认证链路 | admin 登录 ✅、带 token 访问后台返回真实数据 ✅、无/伪造 token 401 ✅ | 未复测（无变化）|
| 秒杀预热 | Redis 数据丢失后由 `SeckillInitialJob`（每分钟）自动重建，DBSIZE 0→15 | 未复测 |
| Nacos | 23 服务注册正常，gateway 修复后 healthy | ⚠️ 现为 **27 个服务名**。增量来自 Dubbo 应用名拆分的 3 个新增（`mall-seckill-dubbo` / `mall-ums-dubbo` / `mall-product-dubbo`，见 [[TODO第二批实现与原理]] §七·五）；另有原本就独立的 `mall-order-dubbo` / `mall-ai-dubbo`。各服务数据面正常 |


---

**维护提示**: 批次一条目**已全部完成并复核**（#38 → git 清理；#25/R7/#24/R1~R4 → 2026-09-07 执行；**Swap → 亦已执行**）。已移入 [[TODO已完成]]。**技术正文已切割到 `docs/问题解决/` 8 个分类文档（2026-09-10）**，本文件保持"索引 + 执行记录 + 话术"定位。
> 📄 **问题解决 + 面试话术已单独整理到 `docs/面试准备/10-1 ~ 10-5`**（2026-09-10）。
