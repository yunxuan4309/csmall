# TODO 第一批实现与原理（面试深挖应对）

> **创建日期**: 2026-09-07
> **状态**: 本地仓库已实现（compose 加固评审稿 + redis.conf 模板 + Dockerfile 清理 + .env.example 更新）；服务器部署待维护窗口执行（需 ecs-user 写 /data/csmall 文件 + sudo）
> **用途**: 面试深挖应对 —— 每条都含「原理 → 本项目实现 → 代码/服务器实证 → 企业演进 → 面试话术」
> **关联**: [[TODO文件]] 第一批（#25 / R7 / #24 / R1~R4 / #38）、[[Redis配置加固与哨兵模式方案]]、[[服务器内存优化方案]]

---

## 〇、第一批全景（先记住这张表）

| # | 事项 | 本质 | 为什么第一批 | 本地已做 | 服务器待做 |
|---|------|------|-------------|---------|-----------|
| **#25** | JWT_SECRET 生产随机化 | 安全洞（伪造 token） | clone 仓库即可伪造任意用户 JWT = **最严重** | `.env.example` 注释/生成说明 | .env 换强随机 + 重建 11 服务 |
| **R7** | 内存优化 | 资源红线（OOM 风险） | available 2.0G、无 Swap、mem_limit=0 | compose 加 mem_limit + Nacos/Sentinel 降堆 | `docker update` 即时生效 + 重建 nacos/sentinel + Swap（sudo）|
| **#24** | MySQL 强密码 + 端口收窄 | 安全（弱密码+全映射） | `MYSQL_ROOT_PASSWORD=root` | compose 端口 `127.0.0.1:3306` | .env 强密码 + `ALTER USER`（关键坑）+ 重建 7 个 DB 服务 |
| **R1~R4** | Redis 加固 | 安全 + 数据持久化 | 无密码/AOF/maxmemory | compose + conf 模板 | .env 密码 + 部署 conf + 重建 redis + 11 服务 |
| **#38** | Dockerfile 双份清理 | 运维洁癖（防踩坑） | 5 分钟低成本 | ✅ 已 git rm 11 份 | 无（纯仓库）|

**执行策略**: #38 + R7 可立即做（零/低风险）；#25/#24/R1~R4 需**同一维护窗口**原子切换（全站重建一次），Swap 需用户 sudo。

---

## 一、#25 JWT_SECRET 生产随机化（面试重点，必被深挖）

### 1.1 问题本质（为什么最严重）

**JWT = 无状态凭证**：服务器不存 session，验签只靠"签名是否匹配"。而本项目用的是 **HS512 对称签名**——签发与验签是**同一把密钥**。

```java
// mall-common JwtTokenUtils.java:45 —— 唯一密钥入口
.signWith(Keys.hmacShaKeyFor(secret.getBytes()), Jwts.SIG.HS512)
```

**实测发现（2026-08-28）**: 生产 `.env` 的 `JWT_SECRET` = 代码 yml 默认值 `CooxiaoMall2026JwtSecretKeyForHs512AlgorithmMustBeAtLeast64BytesLong`（68 字节）。攻击者 clone 公开仓库 → 拿到默认值 → 自己签发任意用户的合法 JWT → **冒充 admin/任意用户**，比爆破密码严重得多——这是全项目最优先安全洞。

**为什么"生产用环境变量"形同虚设**：compose 确实注入了 `JWT_SECRET: ${JWT_SECRET}`，yml 是 `secret: ${JWT_SECRET:默认值}`——**生产环境变量被设置了，但值恰好等于默认值**。防御机制在，但钥匙没换。

### 1.2 谁在用这把密钥（代码实证，面试讲得出细节）

| 角色 | 服务 | 代码位置 |
|---|---|---|
| **签发**（生成 token） | 仅 mall-sso | `UserSSOServiceImpl:50`、`AdminSSOServiceImpl:58` |
| **验签**（解析 token） | 9 个业务服务 + sso 自己 | 各模块 `SSOFilter.java:62`（`jwtTokenUtils.getUserInfo(authToken)`）|
| **不参与** | mall-gateway | 零引用（gateway 包 `com.cooxiao.mall.gateway` 默认扫描不含 common 的 JwtTokenUtils）|

> 💡 **面试点**：能画出"签发方只有 sso、验签方分散在 9 个 SSOFilter"的图，说明你真读过代码。

### 1.3 随机密钥怎么产生（面试高频追问）

```bash
openssl rand -base64 48    # → 64 个 ASCII 字符 = 64 字节 ≥ HS512 最低 64 字节 ✅
openssl rand -base64 64    # → 88 字节，更宽裕
openssl rand -hex 32       # → 64 个 hex 字符，等效
```

**为什么必须 openssl rand 而不是 uuid/时间戳**：
- `openssl rand` 走**内核熵池**（`/dev/urandom`，CSPRNG），**不可预测**——这是密钥的根基
- uuidv4 虽有随机性但格式固定、熵略低；时间戳/自定义字符串可被猜测——换了等于没换

**密钥会随时间变化吗？——不会（重要概念区分）**：
- **生成时**：每次运行结果都不同（随机）
- **落盘后**：`.env` 里的值是**固定的**，服务每次启动读同一串，1 天 1 年都不变
- 会变的是 **token 本身**（`jwt.expiration: 604800` = 7 天过期）和**生成机制**（熵源含时间）——密钥必须长期稳定，否则昨天签的 token 今天验签失败
- 要换 = 手动再跑一次 `openssl rand` 改 `.env`（企业叫"轮换"）

### 1.4 换密钥后 SSO 还能正常运行吗？服务器会增负担吗？（必问）

**能正常运行**，前提是 11 个服务拿到**同一个新密钥**：
- 换的只是"密钥的值"，机制（HS512 对称签名）完全不变
- 11 服务从同一 `.env` 经 compose 注入同一环境变量 → 签发/验签依然匹配
- 唯一行为变化 = 旧 token（旧密钥签的）验签失败 → **所有用户重新登录**——这是目的不是故障

**零性能负担**：
- HMAC 计算开销只取决于数据长度，与密钥随机与否**无关**（微秒级本地运算）
- 密钥不进 token（载荷只有用户信息 JSON）→ token 长度/网络传输不变
- 无新增服务/调用/存储

### 1.5 原子切换风险：11 个服务有一个没同步会怎样？（最容易被追问）

对称密钥**签发/验签必须同值**，漏一个 = 新旧密钥混用：

| 场景 | 结果 |
|---|---|
| **sso 没换**（签发方旧、验签方新） | 登录接口返回 200 + token（签发不验签），但**任何带 token 请求全 401** → "登录成功但什么都干不了" = 全站不可用 |
| **某个验签服务没换**（如 seckill 旧、其他新） | **只有该服务接口全 401** → "登录成功但秒杀/后台某一项全挂"，最阴险、最难排查 |
| **gateway 没换** | 当前零影响（不验签），但 compose 注入了 JWT_SECRET，未来加认证会踩雷 → 仍应统一换 |

**漏同步的两种形态**：
1. **只 restart 没 recreate**：`docker restart` 不重新读 env（env 是容器创建时快照），必须 `docker compose up -d` **重建**
2. **个别服务 environment 没写 `JWT_SECRET`**：回退 yml 硬编码默认值

**验证命令（执行时必跑）**：
```bash
for c in csmall-sso csmall-product csmall-order csmall-seckill csmall-ums csmall-ams csmall-front csmall-search csmall-ai csmall-resource csmall-gateway; do
  echo "$c: $(docker inspect $c --format '{{range .Config.Env}}{{println .}}{{end}}' | grep ^JWT_SECRET= | cut -c1-30)..."
done
# 回归：新 token 全链路 200 + 旧 token 被拒 401
```
> 2026-09-07 实测基线：11 容器 JWT_SECRET 全部一致 = `CooxiaoMall2026...`（与 .env、与 yml 默认值三相同）→ 一致性 ✅ 但等于默认值 ❌

### 1.6 企业怎么做？（拉开差距的加分回答）

| 维度 | 本项目 #25（演示项目合理） | 企业标准 |
|---|---|---|
| 生成 | openssl rand 一次性 | CSPRNG + 制度化轮换 |
| 存储 | `.env`（gitignore，服务器明文） | **KMS/Vault/Secrets Manager**：加密存储 + 审计 + 访问控制 |
| 分发 | compose 注入环境变量 | 应用运行时拉取（SDK），不落盘明文 |
| 轮换 | 全量重建 + 全员重登（粗暴但演示够用） | **kid 版本化平滑轮换** |
| token 时效 | 7 天 | 15 分钟短 token + refresh token |

**kid 版本化平滑轮换（重点讲这个，证明你知道更好的方案）**：
```
JWT 头带 kid（密钥版本号）
签发: 用当前版本密钥，头里声明 kid=v2
验签: 读 kid → 查密钥表对应版本 → 验签

轮换: v2 上线（新签发全 v2）→ v1 保留在表里（存量 token 仍可验）
      → 宽限期（等 v1 token 自然过期，如 7 天）→ v1 从表删除
```
- 好处：**存量用户无感**，新旧 token 宽限期内共存，不用全员重登
- 代价：需要"密钥表 + kid 解析"代码改造（本项目 JwtTokenUtils 是单一固定密钥，TODO #17 P1 列为未来项）
- **面试话术**："我现在的 #25 是单密钥粗暴轮换，企业用 kid 版本化平滑轮换 + KMS 托管；我知道差距和演进路径"

### 1.7 #25 面试速答

**Q: 你怎么发现这个问题的？** A: 服务器 .env 与代码 yml 默认值比对——`JWT_SECRET` 生产值 = 仓库默认值 68 字节字符串，clone 仓库即可伪造任意用户 token。这是安全审计里最有力的案例（不是背流程，是真发现）。

**Q: 密钥多长合适？** A: HS512 要求 ≥64 字节（512 bit），`openssl rand -base64 48` 恰好 64 字符，宽裕点用 64。

**Q: 为什么是 HS512 不是 RS256？** A: HS512 对称（一把密钥签发+验签，快、简单），适合内部微服务；RS256 非对称（私钥签发/公钥验签），适合第三方信任场景。本项目全内部服务，HS512 合理。（诚实：若服务要独立给外部验签才需要 RS256。）

**Q: 密钥轮换时正在用的用户怎么办？** A: 本项目 = 全员重新登录（token 全失效，可接受）；企业 = kid 双密钥宽限期平滑过渡。

---

## 二、R7 内存优化（资源止血）

### 2.1 实测现状（为什么是前提）

- 服务器 4C16G，**available 仅 2.0G、无 Swap**、21 容器 **mem_limit 全为 0** → 任何进程失控可直接吃满宿主被 OOM Kill（历史杀过 ES）
- 内存账单：11 微服务 ~7.1G / ES 1.19G / OAP 1.08G / **Nacos 0.98G（堆 1g，最大可降点）** / Sentinel 0.28G（堆未限，默认上限 ~3.5G）

### 2.2 关键认知：线程栈是 JVM 调优后的隐形大头（进阶面试点）

8-04 JVM 调优后 csmall-product RSS 仍 965M，smaps 实测分解：堆 ~250M + Metaspace ~50M + **线程栈 350 线程 × 1M = 350M** + DirectMemory 64M + CodeCache 37M + SW agent。

**为什么不再压 `-Xss256k`**：收益每服务 ~200-300M，但 Dubbo 序列化/MyBatis 复杂 SQL 可能超栈 → 偶发 StackOverflow，排查困难。**结论：JVM 层已到合理下限，剩余靠 mem_limit + Swap 做安全网，不继续压榨**——这体现"知道什么时候该停"的工程判断。

### 2.3 四项优化（已在本地 compose 实现）

| 项 | 改动 | 收益/风险 |
|---|---|---|
| ① 全容器 mem_limit | 每个服务加 `mem_limit`（按当前 RSS +30% 余量：product/order/seckill/front/ai 1g，其余微服务 768m，es/oap 1.5g，nacos/mysql 1g，sentinel/seata/ui 512m，rabbitmq/redis/frontend 256m）| 零重启 `docker update --memory` 即时生效；重建后靠 compose 持久化 |
| ② Nacos 堆 1g→512m | compose 加 `JVM_XMS/XMX=512m, XMN=256m` | **省 ~480M**（最大单点）；重启 nacos 2~5 分钟注册抖动，微服务自动重连 |
| ③ Sentinel 显式限堆 | `JAVA_OPTS: -Dserver.port=8858 -Xms128m -Xmx256m` | 消除默认 ~3.5G 堆上限隐患；重启控制台无业务影响 |
| ④ Swap 2G | `sudo fallocate -l 2G /swapfile && mkswap && swapon` + fstab | **需 ecs-user sudo**（AI 无权限）；仅防瞬时峰值 OOM，SSD 慢不能依赖 |

**执行方式（面试讲"两层"）**：
1. `docker update --memory <值> <容器>` —— **零重启即时生效**（安全网立刻到位）
2. 同步 compose `mem_limit` —— 否则 recreate 后丢失

**预期**：available 2.1G → ~2.7G，OOM 失控风险基本消除。

### 2.4 R7 面试速答

**Q: mem_limit 和 JVM -Xmx 什么关系？** A: -Xmx 是 JVM 自限（堆上限），mem_limit 是容器硬限（含堆外/线程栈/agent 的整机上限）——两者是"自限 + 硬限"两层，只设 -Xmx 挡不住线程栈/堆外失控。

**Q: 为什么 Nacos 512m 够？** A: standalone 模式只服务 11 个微服务注册/配置，注册量极小；512m 与之前 Seata/OAP 调优同款逻辑，实测注册列表 23 个服务。

**Q: Swap 该不该上？** A: 应急缓冲垫而非性能方案——防瞬时峰值被 OOM 杀进程（如秒杀突发、ES 段合并），SSD 上慢，不能承载业务。

---

## 三、#24 MySQL 强密码 + 端口收窄

### 3.1 实测现状

- `MYSQL_ROOT_PASSWORD=root`（仅 4 位）——内网可爆破
- 3306 全映射 `0.0.0.0:3306`——安全组挡了公网，但宿主机网卡全监听，纵深防御为零
- mysql 容器已非 root（500）✅

### 3.2 关键坑（面试问"你实施时最怕什么"就讲这个）

**⚠️ compose 的 `MYSQL_ROOT_PASSWORD` 环境变量只在数据卷首次初始化时生效**。MySQL 数据已存在 → **只改 .env 无效**，必须 docker exec 手动改：

```sql
docker exec csmall-mysql mysql -uroot -p'<旧密码>' -e "
  ALTER USER 'root'@'localhost' IDENTIFIED BY '<新强密码>';
  ALTER USER 'root'@'%' IDENTIFIED BY '<新强密码>';
  FLUSH PRIVILEGES;"
```

**顺序（同窗口原子）**: ① .env 改强密码 → ② ALTER USER → ③ 重建 7 个连 DB 服务（sso/product/order/seckill/ums/ams/resource，密码经 `MYSQL_PASSWORD=${MYSQL_ROOT_PASSWORD}` 注入）。错配 = 全站 DB 连接失败，回滚 = 改回旧密码。

### 3.3 端口收窄 127.0.0.1 为什么"零功能影响"（核心认知）

```
之前: 0.0.0.0:3306 → 宿主机网卡监听，外部可连（被安全组挡）
之后: 127.0.0.1:3306 → 仅宿主机本机可连

微服务连 mysql 走的是 docker bridge 网络（容器名解析直连 IP），
不经宿主机端口映射 → 端口收窄不影响服务间通信，只挡宿主机外部
```
同理适用于 Redis/Nacos 等所有中间件。安全组（22/80）挡公网 + 端口 127.0.0.1 挡内网/本机 = 纵深防御两层。

### 3.4 #24 面试速答

**Q: 为什么连 DB 都用 root？** A: 历史遗留（单机演示），compose 里 `MYSQL_USERNAME: root`。企业按库建最小权限账号（ams/ums/oms 各一），是后续 TODO 项。（诚实承认，不掩饰。）

**Q: 改了密码为什么服务要重建？** A: 密码经环境变量注入，env 是容器创建时快照，restart 不重读，必须 recreate。

---

## 四、R1~R4 Redis 加固（第一批最复杂）

### 4.1 实测现状（四连坑，2026-08-21 巡检）

| 编号 | 问题 | 实测 |
|---|---|---|
| R1 | 无密码 | `requirepass` 空；compose redis 无 environment；11 服务无 `SPRING_DATA_REDIS_PASSWORD` |
| R2 | 无 AOF | `appendonly no`，仅 RDB（丢 60s~15min 数据）；购买标记 reseckill 丢失 → 可能重复秒杀 |
| R3 | 无内存上限 | `maxmemory 0` + `noeviction` → 写满拒绝写入 = 功能停摆 |
| R4 | 无自定义 conf | 裸 `redis-server`，CONFIG SET 重启即失 |

### 4.2 redis-master.conf 设计（每行都能讲为什么）

```conf
bind 0.0.0.0            # 容器内监听（Docker 网络隔离，非裸奔）
port 6379
requirepass <密码>       # R1: 认证
masterauth <密码>        # 主从/哨兵互连用（本项目单机暂用，为 #9 哨兵铺路）
maxmemory 256mb         # R3: 内存硬顶（实测仅 21 键，256m 充裕）
maxmemory-policy volatile-lru  # 只淘汰带 TTL 的键 → 保护永久购买标记 reseckill
appendonly yes          # R2: AOF
appendfsync everysec    # 性能/安全平衡（秒杀标准做法）
dir /data               # 与数据卷 redis_data:/data 对应
```

**⚠️ 挂载不加 `:ro`**：Redis 运行时会自动 REWRITE 自身 conf（记录角色等），只读挂载会导致故障切换失败/脑裂（为哨兵铺路时的关键坑）。

**healthcheck 必须带密码**（易漏坑）：加 requirepass 后 `redis-cli ping` 返回 NOAUTH → healthcheck 误判 unhealthy → compose 里用 `redis-cli -a "$$REDIS_PASSWORD" ping`（`$$` 是 compose 转义，容器内展开环境变量）。

### 4.3 原子切换（与 #25 同构的坑，可类比讲）

**不能只开服务端 requirepass**（微服务全连不上）；**不能只给客户端配密码**（服务端无密码时 AUTH 报 "no password is set"）。两条必须**同一维护窗口**：
1. .env 加 `REDIS_PASSWORD=<强随机>`
2. 部署 conf 到 `/data/csmall/redis/redis-master.conf`（替换占位符）
3. compose redis 加 command + 挂载 + environment + healthcheck
4. **11 个微服务 environment 全部补 `SPRING_DATA_REDIS_PASSWORD: ${REDIS_PASSWORD}`**
5. 重建 redis → 验证 → 重建 11 服务

各模块 prod yml 已预留 `password: ${REDIS_PASSWORD:}`（2026-09 已核实 10 个 yml 一致），所以**只需 compose 注入，无需改 Java/yml**——这是当时埋好的接口。

**更稳的降级技巧**：重建前先在线 `CONFIG SET appendonly yes` 热开 AOF（Redis 自动做首次 rewrite，数据无损零停机），conf 只做固化——避免"已有 RDB、AOF 首启"的边界问题。

### 4.4 为什么 maxmemory-policy 用 volatile-lru 不用 allkeys-lru（细节加分）

- `volatile-lru`: 只淘汰**带 TTL** 的键（缓存类）→ `mall:seckill:reseckill:*` 永久购买标记**无 TTL，永不被淘汰** ✅
- `allkeys-lru`: 全部键可淘汰 → 可能误删购买标记 → 用户重复秒杀
- `noeviction`(现状): 写满直接拒绝写入 → 秒杀库存/随机码写入失败 = 功能停摆而非降级

### 4.5 R1~R4 面试速答

**Q: AOF 和 RDB 怎么选？** A: RDB 快照（省空间、恢复快、最多丢最后一次快照后数据）；AOF 日志（每命令追加、最多丢 1 秒 everysec）。生产两者并存：RDB 快速恢复 + AOF 保数据。秒杀标记/库存是"丢了就出事"的数据 → 必须 AOF。

**Q: 加密码对秒杀性能有影响吗？** A: AUTH 是连接时一次性握手，Lettuce 连接池复用连接，QPS 路径无额外开销。

**Q: 为什么不直接上哨兵？** A: 第一批只做单机加固（R1~R4）；哨兵（#9）是学习/HA 实验，需先 R7 腾内存 + 单机哨兵不防整机宕机，边界要讲清。

---

## 五、#38 Dockerfile 双份清理（运维洁癖案例）

**实测**：模块目录残留 11 份旧版（`mall-sso/Dockerfile` 等，`FROM eclipse-temurin:21-jre-alpine` + 无 JVM 参数 + 无 SW agent）+ `deploy/docker/dockerfiles/` 11 份正式版（Debian + 完整 G1GC/Metaspace/SW agent 参数）。compose 全部指向正式版，残留版零引用但**任何人手动 build 都会重踩 Alpine musl 与 MD5 不兼容的坑**（历史 Q12 事故）。

**处理**：`git rm` 11 份残留文件。**单一事实源 = `deploy/docker/dockerfiles/`**。

**面试话术**："我发现项目 Dockerfile 双份不一致（模块目录残留 Alpine 旧版会重踩 MD5 崩溃坑），清理统一到单一事实源"——运维洁癖 + 防坑意识。

**本次实装**（本地已执行）：mall-ai / mall-ams / mall-front / mall-gateway-server / mall-order / mall-product / mall-resource / mall-search / mall-seckill / mall-sso / mall-ums 共 11 份 `Dockerfile` 已 `git rm`。

---

## 六、本次本地改动清单（deploy/docker/ 为事实来源，与服务器原文件 MD5 一致）

| 文件 | 改动 |
|---|---|
| `deploy/docker/docker-compose.yml` | ① mysql/redis 端口 `127.0.0.1:` 收窄 ② redis 加 command/conf 挂载/REDIS_PASSWORD env/密码 healthcheck ③ nacos 加 JVM_XMS/XMX/XMN ④ sentinel JAVA_OPTS 限堆 ⑤ 全 21 服务加 mem_limit ⑥ 11 微服务补 `SPRING_DATA_REDIS_PASSWORD` ⑦ mysql 去掉 `:-root` 弱默认 |
| `deploy/docker/redis/redis-master.conf`（新） | R1~R4 加固模板（密码占位符 `<REDIS_PASSWORD>`，部署时替换）|
| `deploy/docker/.env.example` | 补充三处强密码生成说明（MYSQL/JWT/REDIS）|
| 11 个模块目录 `Dockerfile` | 已删除（#38）|

**注意**：compose 中 `${REDIS_PASSWORD}`/`${MYSQL_ROOT_PASSWORD}` 已**去掉弱默认值**——若 .env 未配，compose 会警告并使用空串 → 部署时 .env 必须先配好，否则服务起不来（fail-fast，比静默弱配置好）。

---

## 七、服务器部署执行清单（维护窗口，待用户确认执行）

> AI 无写 /data/csmall 权限 + 无 sudo → 以下文件操作用 ecs-user 执行，docker 操作用 ai-deepseek 可执行。

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

### Step 7 Swap（ecs-user sudo，独立执行）
```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 回滚
- mem_limit: `docker update --memory -1 <容器>`
- 密码类: .env 改回旧值 + `ALTER USER` 回旧密码 + 重建
- 全部: cp 备份还原 + `docker compose up -d`

---

## 八、第一批通用面试话术（贯穿主线）

**主线叙事**："我给生产做了一轮安全+资源审计，实测发现 4 类真实问题（JWT 密钥=默认值、MySQL root 弱密码、Redis 裸奔、内存无硬限），按'会真出事'排序分三批修复。第一批的共性难点是**多服务配置原子切换**——JWT/MySQL/Redis 都要求'服务端 + 全部客户端同窗口变更'，我通过①同一维护窗口②改动前逐容器 env 基线核对③重建后逐容器验证一致性 来控制风险。过程中把'密钥轮换为什么不能只 restart''端口收窄为什么零影响''AOF 和 RDB 怎么选'这些原理也吃透了。"

**被追问"为什么不等公司方案/为什么自己动手"时**：这是个人项目（演示+面试用），我自己就是 owner——但每个决策都对齐企业做法（kid 轮换/KMS/最小权限），说明我知道生产标准是什么、当前取舍是为什么。

---

**维护提示**: 服务器执行完成后，回填"执行日期/验证结果"到本文档头部，并把 TODO 文件第一批条目移入"已完成"表。
