# TODO 第三批实现与原理（面试深挖应对）

> **创建日期**: 2026-09-09
> **状态**: 🟡 第三批"企业级演进 + 学习"逐项实施中 —— **#46 nacos 数据卷挂载重启 ✅ 已完成（2026-09-09）**、**#47 数据库备份恢复演练 ✅ 已完成（2026-09-09，独立临时容器方案）**。本文件随第三批逐项实施持续补充，格式与 [[TODO第一批实现与原理]] / [[TODO第二批实现与原理]] 对齐。
> **用途**: 面试深挖应对 —— 每条都含「原理 → 本项目实现 → 代码/服务器实证 → 遇到的问题/疑惑 → 面试话术」
> **关联**: [[TODO文件]] 第三批（#46 / #47 / #4 / #5-P1 / #9 / #14-P2 / #40 / #45 等）、[[TODO已完成]]、[[集群化与配置中心迁移方案]]、[[Redis配置加固与哨兵模式方案]]

---

## 〇、第三批全景（先记住这张表）

| 优先级 | 编号 | 事项 | 本质 | 状态（2026-09-09） |
|---|---|---|---|---|
| 🔴 P0 | **#46** | nacos 数据卷挂载重启 | 运维消雷（nacos 重建即丢配置） | ✅ **已完成（2026-09-09，含卷名前缀大坑，见 §一）** |
| 🔴 P0 | **#47** | 数据库备份恢复演练 | 运维底线（备份没验证过=没有备份） | ✅ **已完成（2026-09-09，独立临时容器方案，见 §三）** |
| 🔴 P0 | **#4-阶段0** | 秒杀定时任务分布式锁（集群化前置） | 集群正确性（防双实例重复发 MQ） | ✅ **代码完成（2026-09-09，含"测试手法小插曲"，见 §四）** |
| 🟠 P1 | **#4** | 秒杀集群化 + 配置中心（单机 2 实例） | 面试主菜（负载均衡/故障剔除/定时任务锁） | ⏳ 待做 |
| 🟠 P1 | **#5-P1** | Sentinel 热点参数限流（秒杀按 spuId） | 面试主菜（热点限流） | ⏳ 待做 |
| 🟡 P2 | **#9** | Redis 主从 + 哨兵 | 学习 HA（搭 #9 的车做 #14-P2） | ⏳ 待做 |
| 🟡 P2 | **#14-P2** | 主从防数据 min-replicas-to-write 1 | 搭 #9 的车 | ⏳ 待做 |
| 🟡 P2 | **#40** | actuator healthcheck | 可观测基础课 | ⏳ 待做 |
| 🟢 P3 | **#45** | 统一 Dubbo 应用名（front/search/ams） | 纯规范 | ⏳ 待做 |
| 🟢 P3 | #30/#41/#39/#15/#16/#22/#26/#35 | 监控/日志/CI/K8s/TraceId/CORS/网络/Jackson | 讲认知为主 | ⏳ 暂缓 |

---

## 一、#46 nacos 数据卷挂载重启（⭐ 已完成：卷名前缀是最值钱的一个坑）

### 1.1 问题本质（为什么必须做）

**nacos 是 21 个容器里唯一没挂数据卷的中间件**（mysql/redis/es/rabbitmq 都有 `xxx_data:/data`）：
- derby 数据库（配置/用户/规则）+ JRaft protocol 日志全在容器**可写层** `/home/nacos/data`
- 容器重建（compose up / 版本升级 / 故障 recreate）→ 可写层清空 → **derby 数据全丢**
- 已在 #13 部署时实测踩过：重建后 6 条 Sentinel 规则 dataId 全丢（靠仓库 JSON 2 分钟重建找回）

**服务器实测现状（2026-09-09，操作前）**：
| 项 | 实测 |
|---|---|
| nacos 容器 | Up 13h（#13 部署时 09-08 13:40 重建）、healthy、认证已开（403/真 JWT）|
| 挂载卷 | **无任何 Mounts** |
| 数据分布 | `/home/nacos/data` 共 587.6M：`protocol/` 584.9M（JRaft 运行日志）+ `derby-data/` 2.7M（**配置/用户/规则的真正存储**）+ config-data 32K + naming 8K |
| derby 内容 | 6 条 Sentinel 规则（SENTINEL_GROUP）+ nacos 用户表 + public 命名空间 |
| 服务注册 | 27 个服务（HTTP + `*-dubbo` + 接口级 providers）—— 动态数据，重启自动重注册，**无需备份** |
| 本地 compose | ✅ 已含 `nacos_data:/home/nacos/data` 卷定义（2026-09-08 提交）|
| 服务器 compose | ❌ 缺卷定义（与本地**仅差 9 行**：nacos volumes 段 + 底部卷声明）|

### 1.2 执行过程（2026-09-09 维护窗口，约 20 分钟含排坑）

```
Step 1  本地 compose scp 覆盖服务器（已 diff 确认仅差 nacos 卷 9 行）+ md5 校验
Step 2  docker compose config 验证语法 → docker volume create nacos_data → docker stop csmall-nacos
Step 3  docker cp 备份 derby → 灌入新卷 nacos_data → 验证卷内结构
Step 4  docker compose up -d nacos → 等 healthy
Step 5  验证：403 / 登录 / 规则 / 挂载 / 服务重注册 → ❌ 登录报 "User nacos not found"
→ 排坑（§1.4 卷名前缀）→ 修复 → 重验 ✅ 全部通过
```

### 1.3 疑惑点 1：备份大小 587M → 8.5M？（不是丢数据）

**现象**：`docker exec` 看容器内 `/home/nacos/data` 是 587.6M，但 `docker cp` 出来只有 8.5M，卷里也 8.5M。**一度怀疑备份不完整。**

**真相**（du 两次对账 + 目录构成分析）：
```
运行中 du 587.6M = protocol 584.9M（JRaft/RocksDB WAL 运行日志，膨胀态）
docker stop 优雅停机 → JRaft compact/truncate → protocol 缩到 5.8M（40 个文件，raft_meta/LOG 元数据保留）
docker cp 复制的是"停止后的真实状态" → 8.5M = protocol 5.8M + derby-data 2.8M + config/naming 若干
```

**判据**：真正"重建即丢且不可再生"的只有 **derby-data（运行中 2.7M ≈ 备份 2.8M，量级一致）**——它完整在卷里。protocol 是**可再生运行日志**（RocksDB WAL，重启自建），即使全丢 nacos 也能靠 derby 恢复配置 + 服务重新注册。**备份实质完整。**

> 💡 **面试点**：能解释"为什么运行中 du 587M、停后 cp 只有 8.5M"——**du 看的是含运行日志膨胀的目录，cp 复制的是停机 compact 后的持久状态**；容器数据备份要以**停机后**为准（或对运行中容器用 `docker exec tar` 而非直接 cp 目录），否则可能拷到不一致的中间态。

### 1.4 疑惑点 2（⭐ 最值钱的坑）：登录报 "User nacos not found" = 卷名接错

**现象**：重建后容器 healthy、无 token 403 正常，但管理员登录报 `User nacos not found`；derby 文件时间戳 = **本次启动时刻**（全新初始化空库）。

**排查链（三证据锁定根因）**：
| 证据 | 内容 | 结论 |
|---|---|---|
| ① 卷列表 | 存在**两个**卷：`nacos_data`（手动建的，8.5M 有数据）+ `csmall_nacos_data`（compose 自动建的）| 卷不止一个 |
| ② 挂载检查 | `docker inspect`：容器挂的是 `csmall_nacos_data` | 挂错卷 |
| ③ `docker compose config` | nacos 卷解析名 = `name: csmall_nacos_data` | 真相在此 |

**根因（compose 卷命名机制）**：**compose 的卷名 = `项目名_卷名`**（本项目 = `csmall_` + `nacos_data` = `csmall_nacos_data`）。而我手动 `docker volume create nacos_data` 创建的是**无前缀卷**，compose **根本不认识它**——重建时发现 `csmall_nacos_data` 不存在，就**自动创建了一个空卷**挂给容器 → nacos 在空库上全新初始化 → 没用户、没规则。

**为什么 compose 不报错**：compose 对"声明了但不存在"的卷是**自动创建**（`docker volume create` 语义），这是设计行为不是错误——所以整个过程零报错，容器 healthy，只是数据没接上。

**修复**：
```bash
docker stop csmall-nacos && docker rm csmall-nacos
docker volume rm csmall_nacos_data                # 删 compose 自动建的空卷
docker volume create csmall_nacos_data            # 用正确卷名（带前缀）重建
# 从宿主机备份目录灌入（双保险：nacos_data 卷 + backup 目录都在）
docker run --rm -v csmall_nacos_data:/dst -v /data/csmall/backup/nacos-data-20260909/data:/src \
  alpine sh -c 'cp -a /src/. /dst/'
docker compose up -d nacos                        # 卷已存在且有数据 → 不再自动建空卷
```

**验证全通过**：403 ✅ / 登录 accessToken ✅ / **6 条规则**（sso/order×2/seckill×2/ai）✅ / 挂载 `csmall_nacos_data` ✅ / **27 服务重注册** ✅

**经验教训（面试可讲，与 #13 的 `${VAR:}` 坑同类——compose 机制类坑）**：
1. **compose 卷名带项目前缀**：compose 里写 `nacos_data:` 实际创建/引用的是 `csmall_nacos_data`（项目名前缀 + 下划线）。**手动 `docker volume create` 必须用全名**，或干脆不手动建卷（compose 自动建空卷后再灌数据也行，但要先确认卷名）
2. **`docker compose config` 是排坑神器**：它输出的 `name: csmall_nacos_data` 早就揭示了真实卷名——**操作前先 `docker compose config | grep volume` 核对卷名**，能省一次完整的"挂错卷 → 排坑 → 重灌"循环
3. **docker cp 前先停容器**：运行中 cp 可能拷到 derby 中间态；停机后 cp 才是持久状态（与 §1.3 呼应）
4. **数据安全双保险的价值**：宿主机备份目录 + 卷内数据两份，排坑过程零损失

### 1.5 面试话术

**主线**："#46 我给 nacos 补了数据卷。审计发现 nacos 是 21 个容器里唯一没挂卷的中间件——derby 配置/用户/规则裸存容器可写层，重建即丢（#13 已实测丢过 6 条规则）。我的执行是：本地 compose 加卷定义 → scp 服务器 → 停容器 → docker cp 备份 derby → 灌入新卷 → compose 重建挂卷 → 验证。过程踩了两个坑：**一是备份大小 587M vs 8.5M**——运行中 du 看到的是 JRaft protocol 日志的膨胀态，优雅停机后 RocksDB compact，cp 到的 8.5M 才是持久状态，真正的核心 derby-data 2.8M 完整无损；**二是更值钱的卷名前缀坑**——我手动 `docker volume create nacos_data`，但 compose 的卷名是 `项目名_卷名` = `csmall_nacos_data`，compose 不认识我建的卷，重建时自动创建了空卷挂上去，导致 nacos 在空库初始化、登录报 User not found。排坑靠三证据：卷列表出现两个卷、docker inspect 看挂载名、`docker compose config` 显示真实卷名。修复 = 删空卷 → 用正确卷名重建 → 从备份灌数据 → 重启，验证 6 规则 + 27 服务全恢复。数据全程零丢失（宿主机备份 + 卷双保险）。"

**被追问"compose 为什么不直接用我建的卷？"**："compose 的卷命名规范是 `projectname_volumename`，这是它的隔离机制（同机多项目不串卷）。我手动建卷时用了裸名 `nacos_data`，相当于绕过了 compose 的命名空间——compose 按自己的规则去找 `csmall_nacos_data`，找不到就**自动创建**（这是 compose 的设计行为，不是报错），所以整个过程零报错、容器 healthy，只是数据没接上。教训是：**要么手动建卷用全名，要么操作前先 `docker compose config` 核对解析后的卷名**。"

**被追问"为什么不直接挂 bind mount（宿主机目录）？"**："命名卷 vs bind mount 的取舍：bind mount（`/data/csmall/nacos:/home/nacos/data`）在单机运维时直观、好备份；命名卷由 docker 管理、跨机迁移/备份走 `docker volume` 命令更规范，且与项目其他中间件（mysql_data/redis_data 等）风格一致。本项目全用命名卷，nacos 归队。bind mount 在需要'直接看宿主机文件'的调试场景更有优势，但这里没有这个需求。"

---

## 二、#46 附带：服务器与本地 compose 一致性核对（运维规范）

**操作**：scp 前先 `git diff` 本地 compose 与服务器 compose（拉服务器文件到临时目录 diff）：
- 结果：**仅差 9 行**（nacos volumes 段 + 底部 nacos_data 卷声明），其余 535 行逐行一致
- 结论：本地 compose 是"事实来源 + 已修复版"，scp 覆盖零风险

**经验**：**服务器与本地 compose 的漂移是隐雷**——本地改了 compose 忘了同步服务器，下次服务器重建就用旧配置（#5 部署时 sso dashboard env 漏同步导致心跳连错地址，就是同类坑）。**规范动作**：每次改 compose 提交仓库后，同步服务器前先 diff 确认差异面，再覆盖。

---

## 三、#47 数据库备份恢复演练（✅ 已完成：独立临时容器方案）

### 3.1 问题本质（TODO 铁律）

#29 已上线每日备份（cron 02:30 `backup-db.sh`，`docker exec mysqldump --databases 6 库 | gzip` 落 `/data/csmall/backup/`），但 TODO 铁律"**备份没验证过 = 没有备份**"——从未做过恢复演练，备份文件可能因各种原因（密码变更后脚本失效 / 权限 / 半截写入）不可用而无人知晓。

**本轮验证对象**：cron 自动生成的 `cs_mall_20260909_0230.sql.gz`（49KB）—— 正好验证 cron 产物而非手工触发的那份。

### 3.2 方案演进（⭐ 先讲"为什么否掉第一版"——安全意识案例）

**第一版方案（生产容器内导临时库）——被用户质疑安全性后推翻**：
```
zcat 备份 | sed 改库名(cs_mall_X → cs_mall_vfy_X) | docker exec -i csmall-mysql mysql ...
```
- 前提：备份含 `CREATE DATABASE cs_mall_*` + `DROP TABLE IF EXISTS`，绝不能直接导回同名库 → 需 sed 把 6 个库名改成 `_vfy` 临时库
- **残余风险（用户敏锐指出）**：sed 替换的正确性依赖"正则 100% 覆盖 dump 中所有指向生产库的语句"（CREATE DATABASE / USE / 全限定表名 / 触发器存储过程里的库名引用 / 数据内容）。**只要一处漏网 → `USE cs_mall_ams` 以原名执行 → 后面的 `DROP TABLE IF EXISTS` 清空生产表**。即使可加 grep 预检兜底，"依赖替换完备性"的模型风险不为零——对"演练"不够纯粹

**最终方案 B（独立临时容器）——彻底消除 sed 风险**：
```
docker run -d --name mysql-restore-test（同版本 mysql:8.0、限 512m、匿名卷、不映射端口）
zcat 备份 | docker exec -i mysql-restore-test mysql ...    # 原名直接导入，无 sed
验证 39 表 + 抽样行数 vs 生产
docker rm -f mysql-restore-test                             # 用完即删，彻底清理
```

| 对比 | 方案 A（生产容器内 + sed） | 方案 B（独立临时容器） |
|---|---|---|
| sed 改库名 | 必须（有漏网风险） | **不需要**（临时库无生产数据，原名导入无妨）|
| 生产容器 | 同实例（理论可误伤） | **零接触** |
| 演练真实性 | 一般 | 更真实（验证"备份能否在全新实例完整还原"）|
| 清理 | DROP 临时库 | **删容器**，彻底 |

### 3.3 执行过程（2026-09-09，约 5 分钟）

```
Step 1  docker run 临时容器（mysql:8.0、--memory 512m、匿名卷、无端口映射、--innodb-buffer-pool-size=64M）
Step 2  轮询直到可连（第 6 次尝试就绪，~12s）
Step 3  zcat 备份 | docker exec -i 导入 → 退出码 0
Step 4  验证：6 库表数 39 全表 + 5 张抽样业务表行数 vs 生产
Step 5  docker rm -f 临时容器 → 确认生产 csmall-mysql 无损（Up healthy）
```

### 3.4 验证结果（2026-09-09 实测）

| 验证项 | 恢复库（临时容器） | 生产 | 判定 |
|---|---|---|---|
| 全表总数 | **39**（ams7/oms6/pms15/resource2/seckill6/ums3） | 39 | ✅ 结构完整 |
| ums_user | 110 | 110 | ✅ |
| pms_spu | 20 | 20 | ✅ |
| ams_admin | 3 | 3 | ✅ |
| oms_order | 86 | 86 | ✅ |
| seckill_spu | 6 | 6 | ✅ |

**结论**：备份可在全新实例**完整还原**，6 库 39 表 + 抽样数据与生产逐行一致 → **#47 演练通过，备份验证可用**。生产全程零接触（独立容器 + 无端口映射 + 匿名卷随删随清）。

### 3.5 疑惑点与原理（面试深挖）

| 疑惑/问题 | 结论 |
|---|---|
| 为什么不在生产容器内导临时库？ | 备份含 `DROP TABLE IF EXISTS`，直接导同名库=清空生产。sed 改库名方案依赖"正则覆盖完备性"，漏一处就炸；**独立容器把风险面归零**——临时库没有生产数据，原名导入无妨 |
| 临时容器为什么安全？ | ① 同版本 mysql:8.0（生产 8.0.46，同镜像）→ 验证版本兼容；② **不映射端口** → 仅 docker 内网 + exec 可达，外部/生产连不到；③ 匿名卷随 `rm -f` 自动清理 → 零残留；④ `--memory 512m` 限流 → 不挤占生产（available 2.9G）|
| 为什么要限 `--innodb-buffer-pool-size=64M`？ | 空实例默认 buffer pool 128M+，演示数据 49KB 完全用不到；限小省内存，验证场景不追求性能 |
| 恢复库行数 vs 生产行数为什么一致？ | 备份 02:30 生成，演练 10:xx 执行，期间无新数据（演示项目低流量）→ 同源逐行一致是"备份完整"的最强证据；若生产有新数据，判据应为"恢复库 ≤ 生产 + 结构完整" |
| 备份恢复的边界（诚实） | ① 本次只验证了**单日全量备份可还原**，未验证"误删单表后的定点恢复"（`--databases` 全库模式不支持单表）；② 未做**跨机恢复**（备份在服务器 A，恢复到服务器 B）——真正灾难场景是全机宕机，需新机 + 备份异地。**建议演进**：备份文件定期 rsync 到异机/对象存储（TODO #39 镜像仓库同思路）|
| 为什么 mysqldump 要 `--single-transaction`？ | InnoDB 一致性快照，不加锁备份，不影响生产读写——备份命令本身无风险的前提 |

### 3.6 面试话术

**主线**："#47 我给数据库备份做了恢复演练——TODO 铁律是'备份没验证过 = 没有备份'，cron 每日 02:30 的备份从没还原过，可能不可用而无人知晓。我第一版方案是在生产 MySQL 容器里 sed 改库名导临时库，但**被我自己否掉了**：备份文件含 `DROP TABLE IF EXISTS`，sed 改写一旦有一处漏网（CREATE DATABASE / USE / 全限定名 / 触发器里的库名引用），`USE cs_mall_ams` 以原名执行就会清空生产表——'依赖替换完备性'的模型风险不为零。**最终用独立临时容器**：同版本 mysql:8.0 起一个 512M 限流、无端口映射、匿名卷的容器，备份原名直接导入（临时库没有生产数据，无需 sed），验证 6 库 39 表 + 抽样行数与生产逐行一致，然后 `rm -f` 删容器彻底清理——生产全程零接触。这个'把风险面从'小心替换'变成'物理隔离''的思路，比演练本身更值钱。"

**被追问"为什么不直接用生产库导一次再删？"**："备份里有 `CREATE DATABASE cs_mall_*` + `DROP TABLE IF EXISTS`，同实例任何'导回再删'都会先经过 DROP——哪怕目标库名改成 _vfy，只要 sed 有一处漏网就是生产事故。独立容器把这个问题物理消除：临时实例里 DROP 谁都无所谓，因为里面只有刚导入的备份数据。"

**被追问"单日全量验证够吗？"**："诚实边界：我验证的是'备份文件能完整还原出 39 表 + 正确数据'——这是'备份可用'的必要验证。没做的：单表定点恢复（当前 `--databases` 全库模式不支持，要加 `--databases` 拆库或 binlog）；跨机恢复（灾难场景需新机 + 备份异地，当前备份在服务器本地，建议 rsync 异机/对象存储）。这些我知道差距在哪，商业项目会补。"

---

## 四、#4 前置：秒杀定时任务分布式锁（✅ 代码完成 2026-09-09，待部署验证）

> 本项是 **#4 秒杀集群化的唯一硬代码改造点**（跨机方案阶段 0）。集群化本身（新机副本/Redis 主从哨兵/端口放行）见 [[跨机集群实施执行清单-2026-09-09]]。

### 4.1 问题本质（为什么集群化必须先做这个）

秒杀服务从 1 实例变 2 实例（老机 10007 + 新机 10017）后，**每个实例都会独立触发自己的定时任务**。全项目共 **3 处 `@Scheduled`**（均在 mall-seckill，2026-09-09 全仓库 grep 实测）：

| 任务 | 频率 | 双实例双跑的后果 |
|---|---|---|
| `MessageRetryTask.retryFailedMessages` | fixedDelay 5s | **重复发 MQ**：两实例同时 `selectPending` 会拿到**同一批** status=0 记录 → 各发一次 → 秒杀消息重复投递 |
| `SeckillReconcileTask.reconcileOnline` | fixedDelay 5min | 对账重复执行（Redis 库存被两次校准，含"连续同向差计数"被加倍累计） |
| `SeckillReconcileTask.reconcileDaily` | cron 03:30 | 同上，全量校准重复跑 |

> ✅ **Quartz 的两个任务（`SeckillInitialJob`/`SeckillBloomInitialJob`，每分钟）无需改造**：代码里都有 `redisTemplate.hasKey(...)` 幂等守卫（已缓存则跳过），双跑只多日志、不改数据。

### 4.2 原理：Redis 分布式锁三要素（面试必答）

```
① 加锁原子：SET key <token> NX EX <ttl>      ← 一条命令完成"判断+写入+过期"
② TTL 兜底：ttl 远大于任务耗时               ← 实例被 kill/网络断，锁自动释放，任务不停摆
③ 释放校验：Lua「比较 token + DEL」原子执行    ← 只删自己加的锁，防误删他人刚抢到的锁
```

- **为什么不用 `SETNX` + `EXPIRE` 两步**：中间宕机就留下**永不过期的死锁**，任务永久停摆。
- **为什么释放要用 Lua**：`GET` 再 `DEL` 是两条命令，中间存在竞态（自己的锁恰好过期 + 别人抢到 → 删掉别人的锁）。Lua 在 Redis 端原子执行，比较与删除不可分割。
- **为什么"并发"才是问题**：串行执行是安全的——后一次扫描时，前一次已把记录 `updateStatusSent`（status=1），`selectPending` 不会再取到。所以锁只需保证**不并发**，不需要保证"每 tick 只跑一次"。
- **本项目一致性**：项目未用 Redis 事务（见上下文文档 §6.6），全部依赖"单命令原子 + 锁 + 补偿"；Lua 先例 `RedisBloomUtils`、SETNX 先例 `IdempotentAspect`，本改造沿用同风格。

### 4.3 本项目实现（文件级）

| 文件 | 类型 | 关键点 |
|---|---|---|
| `mall-seckill-webapi/.../utils/RedisLockUtils.java` | 新增 | `tryLock(key, ttl)` 返回 token / `unlock(key, token)` 走 Lua CAS / 统一前缀 `mall:seckill:lock:` |
| `.../task/MessageRetryTask.java` | 修改 | 包锁 key=`message-retry`、TTL=30s，`finally` 释放；抢不到直接 `return` |
| `.../task/SeckillReconcileTask.java` | 修改 | 两个 `@Scheduled` 各一把锁（`reconcile-online` 5min / `reconcile-daily` 30min） |

### 4.4 ⭐ 小插曲：测试手法怎么选（Mockito / 手写替身 / 真实中间件）

**第一版**：用 Mockito 写单测（`@Mock` 模板 + `@ExtendWith(MockitoExtension.class)`）→ **10 个用例全 ERROR**：

```
java.lang.IllegalStateException: Could not initialize plugin: interface org.mockito.plugins.MockMaker
  Caused by: MockitoInitializationException: Could not initialize inline Byte Buddy mock maker.
             It appears as if your JDK does not supply a working agent attachment mechanism.
  Caused by: java.lang.IllegalStateException: Could not self-attach to current VM using external process
```

**排查路径（三段，值得记）**：
1. 先怀疑版本不兼容 → `dependency:tree` 实测：Mockito 5.7.0 + ByteBuddy 1.14.13 + JDK 21，**版本全兼容**，排除。
2. 读 surefire 报告拿到**真正根因**：Mockito 5 默认 inline mock maker 需要把 byte-buddy agent **self-attach 到当前 JVM**（`ByteBuddyAgent.installExternal` 会拉起子进程），而 AI 执行沙箱禁止该操作。
3. **关键验证**：项目里 `mall-order/.../OmsOrderServiceImplTest`（5 例 Mockito 用例，早就存在）在**同一环境同样报错** → 结论：**是执行环境限制，不是项目缺陷**（曾一度写进文档说"本项目无法使用 Mockito"，已纠正）。

**重新设计测试（三选一的判断标准）**：

| 被测对象 | 手法 | 理由 |
|---|---|---|
| 锁的**互斥语义**（`RedisLockUtilsTest`，5 例） | **真实本地 Redis 集成测试** | SETNX 原子性 / TTL / Lua CAS 都是 **Redis 服务端行为**，mock 掉等于"自己造假 Redis 再自证"，证明不了任何东西 |
| 任务的**编排逻辑**（`MessageRetryTaskTest`，4 例） | **手写替身**（`FakeLock` 子类 + `Proxy` 假 Mapper + `RabbitTemplate` 子类 + `ReflectionTestUtils` 注入） | 只验证"抢不到锁→不扫表""异常路径仍释放"这类**自己算得出来**的分支，无需框架 |

> **一句话判断标准**：被测逻辑**自己算得出来** → 用 mock；被测逻辑**依赖中间件行为** → 必须打真实中间件。

**结果**：`mvn -pl mall-seckill/mall-seckill-webapi -am test -Dtest=RedisLockUtilsTest,MessageRetryTaskTest` → **Tests run: 9, Failures: 0, Errors: 0** ✅

**顺带踩的两个小坑**：
- **TTL 断言不能用秒**：`getExpire(key, SECONDS)` 对 1 秒 TTL 会取整成 0（断言误判）→ 改 `MILLISECONDS` 断言 `(0,1000]`。
- **`RedisTemplate.execute` 二义性**：`execute(RedisCallback)` 与 `execute(SessionCallback)` 在 lambda 下编译报"引用不明确"→ 显式转型 `(RedisCallback<String>) connection -> connection.ping()`。

### 4.5 疑惑点：为什么只改 mall-seckill，不动 mall-common？

`mall-common` 被 **11 个 jar 依赖**（统一异常/JWT/幂等 AOP…），改它意味着 **11 个 jar 全量重建重部署**；把锁工具放 `mall-seckill` 则**只重建 1 个 jar**。同理，锁 key 前缀常量写在 `RedisLockUtils` 里，而不是加进 `mall-common` 的 `PrefixConfiguration`（后者会触发同一条依赖链）。

### 4.6 验证与边界

| 层 | 可测性 | 做法 |
|---|---|---|
| 锁语义 / 任务分支 | ✅ 已测（本地） | 9/9 单测通过 |
| 双实例"每 tick 只有一个执行" | ✅ 可测（同机双实例） | 本地 Nacos + 两个 Run Config（10007/10017，Dubbo 端口错开 20880/20881），看日志 |
| 跨机注册 IP / 哨兵切换 / `lb://` 跨机轮询 / Nacos 剔除 | ❌ 本地测不了 | 两台同 VPC 服务器验证（阶段 3/4） |

**待部署验证**：① 老机 seckill **也必须换新 jar**（否则老机无锁实例与新机有锁实例仍会并发双跑）；② 两实例日志对照——同一时刻只有一台打印「发现 N 条待重试消息」；③ `redis-cli --scan --pattern 'mall:seckill:lock:*'` 在任务间隙应为空。

### 4.7 面试话术

**主线**："秒杀集群化前我先补了定时任务的分布式锁。全项目 3 处 `@Scheduled` 都在秒杀模块，其中消息重试是'扫表→发 MQ→标记已发'——两个实例同时扫会拿到同一批 pending 记录，**重复投递**。我的锁是 `SET key token NX EX ttl` 单命令原子 + TTL 兜底 + **Lua 比较 token 再删**（防误删别人的锁）。这里最容易被追问的是'为什么不用 SETNX+EXPIRE'——两步之间宕机就留死锁；以及'为什么串行就安全'——因为后一次扫描时前一次已把记录标记为已发送。Quartz 那两个预热任务我**没改**，因为它们有 `hasKey` 幂等守卫，双跑只多日志。"

**被追问"你怎么验证锁真的生效"**："分两层：锁的**语义**用真实 Redis 做集成测试（双实例互斥、错误 token 删不掉、TTL 到期自动释放，5 例）；任务的**编排**用手写替身测（抢不到锁不扫表、异常路径仍释放锁，4 例），一共 9 例全绿。**为什么不用 Mockito 测锁**——锁的原子性是 Redis 服务端行为，mock 掉就成了自己造假 Redis 自证；这也是我判断'什么时候该 mock'的标准：被测逻辑自己算得出来才 mock，依赖中间件行为的必须打真实中间件。"

---

**维护提示**: 本文件随第三批逐项实施持续补充；完成一项更新头部状态并回填细节。与 [[TODO文件]] 保持一致（TODO 是状态源，本文件是"原理+疑惑+话术"深挖）。


