# TODO 第三批实现与原理（面试深挖应对）

> **创建日期**: 2026-09-09
> **状态**: 🟡 第三批"企业级演进 + 学习"逐项实施中 —— **#46 nacos 数据卷 ✅ / #47 备份恢复演练 ✅ / #4-阶段0 定时任务分布式锁 ✅（含 9 例单测 + 本地双实例联调）/ 阶段A 新机铺路 ✅**（均 2026-09-09）。跨机集群（#4/#9/#14-P2）已进入实施：**阶段 2a/2b 改动已备好，待维护窗口**。本文件随第三批逐项实施持续补充，格式与 [[TODO第一批实现与原理]] / [[TODO第二批实现与原理]] 对齐。
> **用途**: 面试深挖应对 —— 每条都含「原理 → 本项目实现 → 代码/服务器实证 → 遇到的问题/疑惑 → 面试话术」
> **关联**: [[TODO文件]] 第三批、[[TODO已完成]]、[[跨机集群实施执行清单-2026-09-09]]、[[跨机集群方案-讨论纪要-2026-09-09]]、[[本地双实例锁验证报告-2026-09-09]]、[[Redis配置加固与哨兵模式方案]]

---

## 〇、第三批全景（先记住这张表）

| 优先级 | 编号 | 事项 | 本质 | 状态（2026-09-09） |
|---|---|---|---|---|
| 🔴 P0 | **#46** | nacos 数据卷挂载重启 | 运维消雷（nacos 重建即丢配置） | ✅ **已完成（含卷名前缀大坑，见 §一）** |
| 🔴 P0 | **#47** | 数据库备份恢复演练 | 运维底线（备份没验证过=没有备份） | ✅ **已完成（独立临时容器方案，见 §三）** |
| 🔴 P0 | **#4-阶段0** | 秒杀定时任务分布式锁（集群化前置） | 集群正确性（防双实例重复发 MQ） | ✅ **已完成（9 例单测 + 本地双实例联调，见 §四）** |
| 🟠 P1 | **#4-阶段A** | 新机铺路（compose/配置/agent 就位） | 跨机集群基础设施 | ✅ **已完成（新机实测验证，见 §五）** |
| 🟠 P1 | **#4-阶段2** | 老机端口放行（MySQL/Redis + product/order Dubbo） | 跨机可达性（跨机 Dubbo 的核心障碍） | ✅ **已完成（2026-09-09 窗口 A，见 §5.2~§5.5）** |
| 🟠 P1 | **#4-阶段3/3.5/3.6/4** | Redis 从+3 哨兵 / 客户端迁哨兵 / 故障转移演练 / 秒杀副本 10017 | 面试主菜（负载均衡/故障剔除/HA） | ✅ **全部完成（见 §5.9、§六）** |
| 🟠 P1 | **#5-P1** | Sentinel 热点参数限流（秒杀按 spuId） | 面试主菜（热点限流） | ⏳ 待做 |
| 🟡 P2 | **#9** | Redis 主从 + 哨兵 | 学习 HA | ✅ **已完成（选主 6.1s / 客户端 9.1s 自愈 / 数据零丢失，见 §5.9）** |
| 🟡 P2 | **#14-P2** | Redis 主从防数据（`min-replicas-to-write 1`） | 防脑裂写丢失 | ⏳ **唯一剩余主线项**（实测仍为 0；§5.9④ 有官方佐证） |
| 🟡 P2 | **#40** | actuator healthcheck | 可观测基础课 | ⏳ 待做 |
| 🔴 P2 | **#51/#53** | 容器 restart 策略 / 网关重试与优雅下线 | 可用性（巡检与演练新发现） | ⏳ 待做（见 §6.4/§6.5） |
| 🟢 P3 | **#45** | 统一 Dubbo 应用名（front/search/ams） | 纯规范 | ⏳ 待做 |
| 🟢 P3 | #30/#41/#39/#15/#16/#22/#26/#35 | 监控/日志/CI/K8s/TraceId/CORS/网络/Jackson | 讲认知为主 | ⏳ 暂缓 |

> **本批次已完成清单（2026-09-09）**：#46 nacos 数据卷、#47 备份恢复演练、**跨机集群全链路（阶段 0/A/2/3/3.5/3.6/4 + 阶段 5 文档回填）**——#4 秒杀双实例 + #9 Redis 主从哨兵均已归档 [[TODO已完成]] §十三。
> **唯一剩余主线项 = #14-P2**（`min-replicas-to-write`）；另有两个新发现的可用性待办 #51 / #53。

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

## 四、#4 前置：秒杀定时任务分布式锁（✅ 已完成：代码 + 9 例单测 + 本地双实例联调）

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

**双实例本地联调（2026-09-09 实跑，详见 [[本地双实例锁验证报告-2026-09-09]]）**：本地起 Nacos + **两个真实 JVM**（10007/20880 + 10017/20881），注册到同一 Nacos（`mall-seckill` 双实例 healthy），人为制造 4 次"Redis 库存 ≠ DB 库存"漂移 → **恰好出现 4 条修正日志**（实例1 两条、实例2 两条，**无重复执行**），失败方累计 7 条「未抢到锁，跳过本次执行」；收尾 `KEYS mall:seckill:lock:*` 为空、Redis 回到 DB 基准值 45。→ **互斥、无重复、正确释放三项全部验证通过**。

**顺带踩的两个小坑**：
- **TTL 断言不能用秒**：`getExpire(key, SECONDS)` 对 1 秒 TTL 会取整成 0（断言误判）→ 改 `MILLISECONDS` 断言 `(0,1000]`。
- **`RedisTemplate.execute` 二义性**：`execute(RedisCallback)` 与 `execute(SessionCallback)` 在 lambda 下编译报"引用不明确"→ 显式转型 `(RedisCallback<String>) connection -> connection.ping()`。

### 4.5 疑惑点 1：为什么只改 mall-seckill，不动 mall-common？

`mall-common` 被 **11 个 jar 依赖**（统一异常/JWT/幂等 AOP…），改它意味着 **11 个 jar 全量重建重部署**；把锁工具放 `mall-seckill` 则**只重建 1 个 jar**。同理，锁 key 前缀常量写在 `RedisLockUtils` 里，而不是加进 `mall-common` 的 `PrefixConfiguration`（后者会触发同一条依赖链）。

### 4.6 疑惑点 2：为什么需要"分布式"锁？为什么是"定时"任务？

**① 为什么必须"分布式"（不能本地锁）**

```
老机 JVM #1（10007）        新机 JVM #2（10017）
  synchronized / ReentrantLock  ← 只在各自 JVM 内有效，互相看不见
                ↓
      需要外部协调者 → Redis（项目已有 + SETNX 单命令原子）
```
`synchronized`/`ReentrantLock`/`AtomicXxx` 只保证**单进程内**互斥；秒杀从 1 实例变 2 实例（且在两台机器）→ 本地锁**完全失效**（两个实例都能"抢到自己的锁"）。

**② 为什么这两个任务是"定时"的**

因为它们本质是**兜底/补偿**任务，处理的是"**不知道何时发生**的异常"，没有事件可订阅，只能定期巡检：

| 任务 | 兜什么底 | 为什么只能定时 |
|---|---|---|
| `MessageRetryTask`（5s） | MQ 发送失败 → 失败记录落 `seckill_message_retry` → 扫表重发 | 发送失败异步且偶发，没有"失败事件"；这是 **DB 轮询版延迟队列**（TODO #11 评估过换 ZSET） |
| `SeckillReconcileTask`（5min / 每日 3:30） | "Redis 预扣 + MQ 异步扣 DB"双写导致库存漂移 → 定时把 Redis 修回 DB（DB 是账本） | 漂移**静默发生**，只能靠定期对账（业界叫 reconciliation / compensating job） |

**③ 而"定时"正是"需要锁"的原因**：所有实例共享同一 cron/interval → **到点同时触发**（不像 HTTP 请求被网关分散）→ 冲突是**系统性的**，不是偶发的。

**④ 不加锁会怎样（具体到代码）**：

| 任务 | 后果 |
|---|---|
| `MessageRetryTask` | 两实例同时 `selectPending(3)` 拿到**同一批** status=0 记录 → 各发一次 MQ → **重复投递**（消费端 `uk_sku_user` 兜底不会写脏数据，但有无谓重试与报错日志） |
| `SeckillReconcileTask` | "连续同向差计数"`mall:seckill:reconcile:cnt:*` 被**加倍累计** → 提前误判漂移；极端下两实例竞争写同一库存键 |

**⑤ 关键认知（面试点）**：要防的是**"并发"**，不是"重复"——

```
串行天然安全：第一次执行已 updateStatusSent（status=1）→ 第二次 selectPending 取不到这批
并发才危险：两个实例在"标记之前"同时读到同一批
```
→ 锁的语义是"**每 tick 只有一个实例进入临界区**"，不是"这任务一辈子只跑一次"。

### 4.7 疑惑点 3：为什么不用 Redisson / ShedLock？现在用的是什么？

**现在用的**：自研 `RedisLockUtils`（`mall-seckill/utils`），底层是 Spring Data Redis + **Lettuce**（Spring Boot 3.2 默认客户端）：
- 加锁：`setIfAbsent(key, token, ttl)` → 实际命令 `SET key token NX PX ttl`
- 释放：Lua `if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end`

**Redisson 的现状（实测）**：`mall-seckill-webapi/pom.xml` 里**已引 `redisson 3.24.3`，但代码零使用**（无 `RedissonClient` 配置、无任何 API 调用）= **死依赖**。

**为什么没直接用 Redisson（四条理由）**：

| # | 理由 | 说明 |
|---|---|---|
| 1 | **需求不匹配** | 我们只要"一把简单互斥锁 + 抢不到就跳过"（非阻塞 tryLock）。Redisson `RLock` 提供的是可重入 / 看门狗续期 / pub-sub 等待队列 / Redlock / 公平锁 / 读写锁——为一个"门闩"引入整套分布式同步组件 |
| 2 | **多一份运行时成本** | Redisson 自带连接池（默认 `connectionPoolSize=64`）与 Netty 线程；老机内存本就紧（available 3.1G）。**没有收益的复杂度就是负债** |
| 3 | **多一套配置 + 与哨兵模式不兼容** | 需新增 `RedissonClient` Bean；阶段 3 要把 Redis 迁到**哨兵**，而 Redisson 的哨兵配置是另一套 API（`Config.useSentinelServers()`），与 Spring 的 `spring.data.redis.sentinel.*` 不通用 |
| 4 | **教学/可控性（本项目尤其重要）** | 30 行自研代码把"SETNX 原子性 / TTL 兜底 / Lua CAS 释放"逐行讲清；`RLock.lock()` 反而讲不出原理。出问题 `redis-cli` 直接看 key/value/TTL 即可，不用读 Redisson 内部机制 |

**但 Redisson 确实更强——什么时候该换**：

| 场景 | 为什么 Redisson 更好 |
|---|---|
| 任务耗时不固定、可能超过 TTL | **看门狗自动续期**（自研需自己写续期线程） |
| 需要可重入（同一线程重复加锁） | 自研要维护重入计数 |
| 抢不到需要**排队等待**而非跳过 | Redisson 用 pub/sub 唤醒，避免轮询空转 |
| 需要 Redlock（多 Redis 节点） | 自研实现复杂且极易写错 |
| 团队统一规范、少造轮子 | 长期维护成本更低 |

**为什么"现在不换、未来能换"是合理的**：
- **语义兼容**：`tryLock(key, ttl)` ↔ `RLock.tryLock(0, ttl, unit)`；替换只改 `RedisLockUtils` 一个类 + 加配置，**两个任务类零改动**
- **零新增依赖**：Redisson 已在 pom 里 → 想换随时能换
- **触发条件**：任务耗时可能超过 TTL / 需要可重入或排队 / Redis 上哨兵后想用 Redlock 增强

**Redisson 也不是银弹**：看门狗续期依赖客户端存活（GC 停顿/网络分区仍可能失效）；Redlock 本身有争议（Kleppmann 质疑 antirez）。→ **真正的正确性仍靠幂等 + DB 约束**（见 §4.8）。

**顺带对比 ShedLock**：它**更贴合本场景**（专为 `@Scheduled` 设计，`@SchedulerLock` + `lockAtMostFor`/`lockAtLeastFor`，语义比裸锁精确），但同样要引依赖 + 配 provider（Redis/JDBC），且**锁的可见性/排错不如自研直观** → 本项目选择"白盒教学版"，ShedLock/Redisson 作为演进选项记录在案。

### 4.8 疑惑点 4：这套锁对"未来"有什么影响？

| # | 影响 | 应对 / 现状 |
|---|---|---|
| 1 | **新增定时任务**若也改共享状态，必须同样加锁 | 已立规矩：`RedisLockUtils.key("xxx")` 统一前缀 |
| 2 | **任务耗时增长**（数据量上来）可能超过 TTL（30s/5min/30min）→ 锁提前过期、失去互斥 | 监控任务耗时；必要时换 Redisson 看门狗自动续期 |
| 3 | **Redis 变主从/哨兵**（阶段 3）：异步复制下主挂瞬间锁**可能双持**（Redlock 争议） | **靠幂等 + DB 约束兜底**：`updateStatusSent` / `seckill_stock >= qty` / `uk_sku_user`。锁只负责"减少并发"，**不是正确性的唯一保证** |
| 4 | **再扩实例（3+）** | SETNX 天然支持 N 个竞争者，代码不用改 |
| 5 | **换 ZSET 延迟队列 / MQ 延迟插件**（TODO #11） | `MessageRetryTask` 会被替换 → 锁随之废弃；我们只"包了一层"、业务代码零改动 → **易移除** |
| 6 | **换 ShedLock / Redisson** | 语义兼容可平滑替换（见 §4.7） |
| 7 | **锁泄漏**（异常未释放 / 进程被 kill） | TTL 兜底自动恢复；`mall:seckill:lock:*` 可巡检（本地联调实测收尾为空） |
| 8 | **执行频率语义**：两实例 tick 叠加 → 实际执行间隔可能小于配置值 | 已记录；若需严格"每 N 秒一次"，改成"锁持有到 TTL"（等价 ShedLock `lockAtLeastFor`） |

### 4.9 验证与边界

| 层 | 可测性 | 做法 |
|---|---|---|
| 锁语义 / 任务分支 | ✅ 已测（本地） | 9/9 单测通过 |
| 双实例"每 tick 只有一个执行" | ✅ 可测（同机双实例） | 本地 Nacos + 两个 Run Config（10007/10017，Dubbo 端口错开 20880/20881），看日志 |
| 跨机注册 IP / 哨兵切换 / `lb://` 跨机轮询 / Nacos 剔除 | ❌ 本地测不了 | 两台同 VPC 服务器验证（阶段 3/4） |

**待部署验证**：① 老机 seckill **也必须换新 jar**（否则老机无锁实例与新机有锁实例仍会并发双跑）；② 两实例日志对照——同一时刻只有一台打印「发现 N 条待重试消息」；③ `redis-cli --scan --pattern 'mall:seckill:lock:*'` 在任务间隙应为空。

**✅ 本地双实例联调结论（2026-09-09，详见 [[本地双实例锁验证报告-2026-09-09]]）**：本地起 Nacos + 两个真实 JVM（10007/20880 + 10017/20881），人为制造 4 次"Redis 库存 ≠ DB 库存"漂移 → **恰好 4 条修正日志**（实例1 两条、实例2 两条，**无重复执行**）；失败方累计 **7 条「未抢到锁，跳过本次执行」**；收尾 `KEYS mall:seckill:lock:*` 为空、Redis 回到 DB 基准值 → **互斥 / 无重复 / 正确释放 三项全过**。

### 4.10 面试话术

**主线**："秒杀集群化前我先补了定时任务的分布式锁。全项目 3 处 `@Scheduled` 都在秒杀模块，其中消息重试是'扫表→发 MQ→标记已发'——两个实例同时扫会拿到同一批 pending 记录，**重复投递**。我的锁是 `SET key token NX EX ttl` 单命令原子 + TTL 兜底 + **Lua 比较 token 再删**（防误删别人的锁）。这里最容易被追问的是'为什么不用 SETNX+EXPIRE'——两步之间宕机就留死锁；以及'为什么串行就安全'——因为后一次扫描时前一次已把记录标记为已发送。Quartz 那两个预热任务我**没改**，因为它们有 `hasKey` 幂等守卫，双跑只多日志。"

**被追问"你怎么验证锁真的生效"**："分两层：锁的**语义**用真实 Redis 做集成测试（双实例互斥、错误 token 删不掉、TTL 到期自动释放，5 例）；任务的**编排**用手写替身测（抢不到锁不扫表、异常路径仍释放锁，4 例），一共 9 例全绿。**为什么不用 Mockito 测锁**——锁的原子性是 Redis 服务端行为，mock 掉就成了自己造假 Redis 自证；这也是我判断'什么时候该 mock'的标准：被测逻辑自己算得出来才 mock，依赖中间件行为的必须打真实中间件。"

---

## 五、#4 跨机集群：基础设施与 Dubbo 原理（阶段A ✅ 铺路完成，阶段2 待窗口）

> 集群化的**逐条命令**见 [[跨机集群实施执行清单-2026-09-09]]；本节只讲「原理 + 为什么这么选 + 踩坑 + 话术」。

### 5.1 目标拓扑与"为什么只跨机这两个"

```
老机 8.156.77.197（4C16G）                 新机 csmall-node2（2C8G，同 VPC 同安全组）
├─ MySQL 主（数据唯一真源）                 ├─ Redis 从（6380，replicaof 老机主）
├─ Redis 主（6379）                        ├─ 哨兵 ×3（26379/26380/26381，quorum=2）
├─ Nacos / MQ / ES / Seata / Sentinel      ├─ mall-seckill-2（10017 + Dubbo 20880）
├─ 11 微服务 + gateway + 前端               └─ （预留）造数/压测执行位
└─ mall-seckill（10007）
```

**选型逻辑**：只把"面试价值最高"的两件事跨机——**Redis HA**（"Redis 挂了怎么办"必问）+ **秒杀双实例**（负载均衡/故障剔除/定时任务锁）。其余 19 个容器仍是老机单实例。
**诚实边界**：2 台小机 = 演示级 HA（防单机宕机，不防地域灾难）→ 话术是"验证机制"，不是"做了高可用架构"。

### 5.2 ⭐ 跨机 Dubbo 的根因与解法（本批最值钱的原理）

**根因**：Dubbo 的地址解析链是

```
消费方订阅接口 → Nacos 返回 provider 地址列表 → 消费方【直连】该地址
```

**Nacos 只做注册发现，不做流量代理**（它不是网关，不会替你转发）→ **注册进注册中心的地址必须是"消费者能直连的地址"**。
容器默认注册 bridge 网段 IP（`InetAddress.getLocalHost()` 拿到容器 IP），单机内同网段互通所以一直没暴露，**跨机立刻断**：

```
新机秒杀副本 → 问 Nacos 要 IForSeckillSpuService 地址
             → 拿到 172.18.0.18:20880（老机 bridge 网段）
             → 新机路由表无 172.18.0.0/16 → 连不上
             → No provider available from registry / client-side timeout
```

**服务器实测**（Nacos API）：`mall-product` = `172.18.0.18:20880`、`mall-order` = `172.18.0.20:20880`、`mall-seckill` = `172.18.0.19:10007` —— **HTTP 发现与 Dubbo 注册全是容器 IP**。

**两条解法**：

| 路径 | 做法 | 评价 |
|---|---|---|
| **✅ 注册层改写（选定）** | 老机 product/order 加 `DUBBO_IP_TO_REGISTRY=172.29.193.239` + `DUBBO_PORT_TO_REGISTRY` + compose 发布 `20880:20880` / `20881:20880` | Dubbo 官方为容器/NAT 场景提供的机制；声明式；无"网段变更即失效"隐患 |
| 网络层（回退） | 新机 host 网络 + 静态路由 `172.18.0.0/16 via 老机` | 老机零改动，但属**手工版 overlay**：依赖对端网段、无自愈、单向可达，非规范做法 |

**两个关键细节**：
1. **`dubbo.protocol.port: -1`（各模块默认）会自动选端口 → 跨机必须显式固定**（`DUBBO_PROTOCOL_PORT`），否则没法做端口映射。
2. **`DUBBO_PORT_TO_REGISTRY` 必须填映射后的宿主端口**（product 20880 / order 20881，宿主端口要错开），而不是容器内的 20880。
3. **实测支撑**：容器 → 宿主私网 IP 的映射端口**可达**（`csmall-mysql` 内 `/dev/tcp/172.29.193.239:8848|5672|9200` 全 OK）→ 本地消费方改走宿主 IP **只多一跳 docker-proxy**，无副作用。

**副产品**：改造后老机 order 会同时看到 **2 个 seckill provider** → 顺带验证了 **Dubbo 层负载均衡**（与 TODO #49 想验证的方向一致）。

### 5.3 三个"为什么"（容易被追问的配置）

| 配置 | 为什么这么做 |
|---|---|
| **`replica-announce-ip 172.29.193.239` + `replica-announce-port 6379`**（老机 Redis） | 老机 Redis **被降级为从库时**，要向新主上报自己的地址；若上报容器 IP `172.18.0.22` → 新机不可达 → **复制断链**。这条是"切换后回不来"的隐形杀手 |
| **哨兵用 3 个而不是 2 个** | 哨兵是**多数派**机制：容忍度 =(哨兵数−1)/2 → 3 哨兵容忍挂 1、**2 哨兵容忍 0**（挂 1 即失去仲裁）。数据节点（主/从）不需要奇数——主从复制不是投票 |
| **Redis/哨兵用 `network_mode: host`，秒杀副本用 bridge** | Redis 没有注册中心，唯一要求是"主/哨兵看到可达地址"，host 网络天然满足；秒杀副本必须在 **Nacos/Dubbo 注册宿主 IP + 映射端口**，故用 bridge + `SPRING_CLOUD_NACOS_DISCOVERY_IP` / `DUBBO_IP_TO_REGISTRY` 显式覆盖（与老机同构） |

### 5.4 铺路阶段（阶段A）与踩坑

**产出**（仓库侧，老机 21 个服务**零改动**）：compose 新增 5 个服务（`redis-replica` / `redis-sentinel-1~3` / `mall-seckill-2`）+ 4 个数据卷；`redis-replica.conf` + `sentinel-1~3.conf`；`mall-seckill-replica.Dockerfile`。
**新机侧**（2026-09-09 实测验证）：`/data/csmall/` 下 compose + 4 conf + Dockerfile + `.env` + `skywalking-agent`(23,972,028 B) 全部就位；conf 密码行 2/1/1/1；`docker compose config` exit=0；**容器数 0**。

**踩坑 6 条（都是"流程"而非"技术"）**：

| # | 坑 | 教训 |
|---|---|---|
| 1 | `.env` 未同步 → `sed` 把 `<REDIS_PASSWORD>` 替换成**空值**（`requirepass`/`masterauth`/`sentinel auth-pass` 全空） | 凡"从 .env 取密码再写文件"必须先判空（`[ -n "$PW" ]`），否则静默破坏配置 |
| 2 | 守卫里写 `exit 1` → 粘贴到**交互式 SSH 会话**直接退出登录、连接断开 | 命令块**禁止 `exit`**，改用 `if/else` 或子 shell |
| 3 | 多机器命令块混贴 3 次（`scp`、`tar` 解压贴错机器） | 每个代码块标注 `【老机】`/`【新机】` |
| 4 | 服务器 `redis-master.conf` 属主 `dnsmasq`、权限 600、**容器内实际只有 9 行**（仓库模板 21 行）= 历史漂移且 AI 读不到 | 窗口里改为**幂等追加两行**（`grep -q … \|\| tee -a`），不覆盖、不碰密码 |
| 5 | 新机 `.env` 设 600 → **AI 跑不了 `docker compose`**（compose 启动时要读 `.env`）；两台机器都没装 `setfacl` | 改 `chown ecs-user:ai-deepseek` + `chmod 640`（比老机的 664 更严） |
| 6 | compose 变量未解析时满屏 `WARN variable is not set` | 那是**缺 .env 的正常提示**，不是配置错误；`.env` 到位即消失 |
| 7 | **Redis Sentinel 启动即退出**：`config file ... is not writable: Permission denied` | 容器内 redis 进程是 **uid 999**，而 conf 属 `ecs-user(1000)` → `chown 999:1000 <conf>`（顺带 644→600，防密码被本机其他用户读到） |
| 8 | 修完权限仍报 `Could not create tmp config file` | conf 挂载点 `/usr/local/etc/redis/` 在容器内属 **root** → 改挂到属 999 的 `/data` |
| 9 | 再修仍报 `Could not rename tmp config file (Resource busy)` | ⭐ **单文件 bind-mount 不能被 rename 覆盖**（Docker 挂载点固有限制）；哨兵重写配置是「写 .tmp → rename」→ **必须挂目录**（`./redis:/conf`）+ 宿主目录 `chown 999:1000`。**修好后日志出现 `Sentinel new configuration saved on disk`，conf 里出现 `myid`/`known-replica`/`known-sentinel` 字段** |

> **原理（为什么哨兵非写配置不可）**：Redis Sentinel 每次状态变化（发现从库/发现其他哨兵/故障转移选出新主）都会**重写自己的配置文件**（写 `.tmp` → `rename` 覆盖），用来在**重启后恢复状态**：`sentinel myid`（自身身份）、`known-replica`、`known-sentinel`，以及被改写的 `sentinel monitor <新主IP> <新主端口>`。
> 写不进去时，哨兵**内存里照常工作**（监控、投票、切换都正常），但**一旦重启就退回监视旧主地址**——而旧主此时已是只读从库 → **"切换成功但回不来"**。这类问题**不重启、不演练根本发现不了**，所以必须修而不是"先放着"。

### 5.5 当前进度与下一步

| 阶段 | 状态 |
|---|---|
| 0 定时任务分布式锁 | ✅ 代码 + 9/9 单测 + 本地双实例联调（见 §四） |
| A 新机铺路 | ✅ 完成 |
| 2a/2b 老机端口放行 | ✅ **已完成（2026-09-09 窗口 A）**：3306/6379 绑私网 IP；product/order 注册 `172.29.193.239:20880/20881`；新机→老机 4 端口全通；消费方 Dubbo 动态切换成功、错误 0 条；21 容器零重启 |
| 3 Redis 从 + 3 哨兵 | ✅ **已完成（2026-09-09）**：副本 `master_link_status:up`、主从 `DBSIZE 21=21`、副本 `READONLY` 拒写；3 哨兵视角一致 + `quorum=2` + 互认；**配置持久化已修通**（`Sentinel new configuration saved on disk`）；踩坑三层见 §5.4 |
| 3.5 客户端迁哨兵模式 | ✅ **已完成（2026-09-09）**：11 个服务注入 `SENTINEL_MASTER/NODES`，4 批灰度全部 Started + health 200、Redis 错误 0 条；**行为验证**：seckill 的 Redis 对端由 `172.18.0.5:6379`（容器 IP）变为 `172.29.193.239:6379`（宿主 IP）→ 确认走哨兵 |
| **3.6 故障转移演练** | ✅ **已完成（2026-09-09 19:34—19:36）**：停老机主库 → **6.1s 选主**（+odown→+elected-leader→+promoted-slave→+switch-master）、**9.1s 客户端 Lettuce 自动重连**、11 服务 Redis 错误 0、`DBSIZE 21` 全程不变、切回 **1.1s**；额外发现**脑裂窗口 10.9s** 与"哨兵 conf 必然漂移"红线 → 见 §5.9、[[跨机集群实施执行清单-2026-09-09]] §6.5 |
| **4 秒杀副本 10017** | ✅ **已完成（2026-09-09 21:19—21:45）**：副本 `Started in 78s`、老机换带锁 jar（停机 77s）、Nacos 2 实例（HTTP + Dubbo）、**锁互斥受控争用验证通过**、**负载分布 60 请求 30:30**、**停实例剔除通过**（⚠️ 16s 窗口）→ 见 §六 |
| 5 文档回填 | ✅ **已完成（2026-09-09）**：#4/#9 归档 [[TODO已完成]] §十三；原理/踩坑/话术补入本文件 §五/§六 |

### 5.6 疑惑点：公网 IP / 私网 IP / 绑定地址到底什么区别？

**① 阿里云 ECS 上的两个"IP"不是一回事**

| | 私网 IP | 公网 IP |
|---|---|---|
| 例子 | `172.29.193.239` | `8.156.77.197` |
| 是否在网卡上 | ✅ **真实绑在 eth0**（`ip addr` / `ss` 看得到） | ❌ **不在网卡上**——是阿里云侧的 **NAT 映射**（EIP / 公网带宽） |
| 可达性 | VPC 内互通，**免费不限速** | 走公网带宽（老机 5Mbps 固定 / 新机按量计费） |
| 谁能控制访问 | 安全组（公网规则）+ 内网组内互信 | 安全组公网规则 |

**关键认知**：因为公网 IP 是 NAT 映射，`bind 0.0.0.0` **并不会**让服务"直接监听在公网 IP 上"——公网能不能连，**由安全组决定**；绑定地址只决定"哪些网卡上的流量能进来"。

**② Docker 端口映射的两种写法**

```yaml
ports: ["20880:20880"]                  # = 0.0.0.0:20880 → 监听【所有网卡】
ports: ["172.29.193.239:20880:20880"]   # → 只监听【私网网卡这一个地址】
```

| 维度 | `0.0.0.0` | 指定私网 IP |
|---|---|---|
| 新机能连 / 本机容器能连 | ✅ / ✅ | ✅ / ✅ |
| **公网可达性** | ❌ 不可达（包经 NAT 打到私网 IP，但 SG 未放行） | ❌ 不可达（**与上面完全等价**） |
| **本地暴露面** | 还监听 loopback / docker0 等**所有本地地址**；将来新增网卡、绑辅助私网 IP/EIP，端口会自动跟着暴露 | 只监听 `172.29.193.239`；新增网卡不受影响 |
| 定位 | "门开在所有内墙和侧门" | "门只开在内部那面墙"——⚠️ **这只对内网/本地有意义，对公网没有区别**（见 ⑥） |

**③ 本项目实践（2026-09-09 窗口 A 实测）**

| 端口 | 改造前 | 改造后 | 说明 |
|---|---|---|---|
| 3306 / 6379 | `127.0.0.1`（第一批加固，仅本机） | **`172.29.193.239`** | 跨机必需；绑私网 IP 而非 `0.0.0.0`（⚠️ 仅纵深防御，公网闸门是 SG —— 见下方修正） |
| 20880 / 20881（Dubbo） | 未发布 | 初版漏写 IP → `0.0.0.0` → **已加固为 `172.29.193.239`** | 与 3306/6379 风格对齐 |
| 9010/10005/10087/10004…（HTTP） | `0.0.0.0`（历史风格） | 未改 | 公网由安全组挡（仅放行 22/80/443） |

**④ 实测确认的两条事实**（可用于面试）：
- **同安全组内网互通不受公网规则限制**：8848/5672/8091/9200/10007 都**不在**公网放行列表里，但新机 → 老机**内网全通**。
- **公网不可达自检**：老机上 `ss -lnt | grep 0.0.0.0:3306` 为 0（已绑私网 IP），且公网 3306 无安全组规则。

> ⚠️ **2026-09-09 修正（重要）**：上面第 2 条容易误读成"**绑私网 IP 所以公网不可达**"——**不成立**。因为公网 IP 是 NAT、不在网卡上（见 ①②），发往公网 IP 的包会被 DNAT 成私网 IP 再进网卡，**所以监听 `0.0.0.0` 与监听私网 IP 的公网可达性完全等价**；"公网不可达"**只来自安全组**。绑私网 IP 的价值是"只对 VPC 内可达 + 监听面收敛"，属**纵深防御**，不是边界。
>
> **实证（2026-09-09 巡检，本地非 VPC 探测）**：`8.156.77.197:6379/3306/10087` 与 `47.109.70.197:26379/6380` **全部 blocked**，仅 `8.156.77.197:80` 可达——与各端口绑的是私网 IP 还是 `0.0.0.0` 无关。这与第一批的结论一致（[[TODO第一批实现与原理]]：*"安全组挡了公网，但宿主机网卡全监听，纵深防御为零"*）。

**⑤ 为什么跨机一律走内网 IP**：内网**不限速、不计流量费**；老机公网只有 5Mbps 固定带宽（瓶颈），新机公网是按量计费（走公网=花钱且慢）。

**⑥ ⭐ 常见误解：「绑了私网 IP 就安全了」——在阿里云 NAT 架构下根本不成立**

这是我在本批自己踩到、也最值得讲的认知修正。

**误解**：把 `ports: "172.29.193.239:6379:6379"` 理解成"端口只对内网开放，公网连不上"。

**为什么不成立**：包到网卡之前，地址已经被改写过——

```
攻击者 → 8.156.77.197:6379        （公网 IP，不在网卡上）
        ↓ 阿里云 NAT 层 DNAT
        172.29.193.239:6379        （网卡上的目标地址 ← 包到这里才被"监听"看见）
        ↓
        监听 0.0.0.0 的进程 ✅ 命中   /   监听 172.29.193.239 的进程 ✅ 也命中
```

所以**绑定地址决定不了公网能不能连**——它只决定"本地哪些地址上的流量被接受"。真正决定公网能不能连的，**只有安全组**。

**实证**（2026-09-09 巡检，从本地非 VPC 探测）：

| 端口 | 绑定方式 | 公网探测 |
|---|---|---|
| 老机 `:6379` / `:3306` | **绑私网 IP** `172.29.193.239` | blocked |
| 老机 `:10087` | `0.0.0.0`（历史风格） | blocked |
| 新机 `:26379` / `:6380` | `0.0.0.0`（host 网络） | blocked |
| 老机 `:80` | `0.0.0.0` | **REACHABLE**（SG 放行了） |

→ 前四个全 blocked、`:80` 通，**与绑的是私网 IP 还是 `0.0.0.0` 毫无关系**，纯粹是 SG 规则。

**正确的边界模型（分层，从外到内）**：

| 层 | 手段 | 作用 | 在我们这儿的实际状态 |
|---|---|---|---|
| L1 **边界** | 安全组 / 防火墙 | 决定"包能不能到网卡" | ✅ 公网仅 22/80/443；**这是唯一真正的公网边界** |
| L2 监听面 | `bind` / `ports` 指定 IP | 决定"本地哪些地址接受连接" | 3306/6379/20880/20881 已绑私网 IP（**纵深防御**，非边界） |
| L3 身份 | `requirepass` / ACL | 决定"连上后能不能执行命令" | Redis 主/从有密码；**哨兵无密码**（见 #50，观察项） |
| L4 传输 | TLS | 防窃听/中间人 | 未做（内网自建，暂不需要） |

**一句话判断法**：**先问"包怎么到网卡"（L1），再问"谁在监听"（L2），最后才问"连上能干什么"（L3）。** 把 L2 当成 L1 是这套误解的根源。

**📌 面试话术**："我原来也以为把端口绑到私网 IP 就等于不对外暴露——直到我在阿里云上实测：公网 IP 是 NAT 映射、根本不在网卡上，发往公网 IP 的包会被 DNAT 成私网 IP 再进网卡，所以 `bind 0.0.0.0` 和 `bind 172.29.193.239` 的**公网可达性完全等价**，真正决定公网能不能连的只有安全组。我实测 6379/3306/26379 这些端口全被 SG 挡在外面，而绑法五花八门。所以我把边界模型分成四层：SG 是边界、bind 是监听面收敛、认证是身份、TLS 是传输——**绑私网 IP 属于第二层，是纵深防御，不是边界**。顺带一提，Redis 官方对哨兵的立场也是同一套逻辑：`sentinel.conf` 明说 protected-mode 默认关闭、请'用防火墙或其他手段保护'，而不是让你靠绑地址或认证去挡公网。"

### 5.7 面试话术

**主线（跨机 Dubbo）**："跨机部署最大的坑是 **Dubbo 注册的是容器 IP**。我实测 Nacos 里 provider 全是 `172.18.0.x:20880`——单机内没问题，跨机直接连不上。根因是 **Nacos 只做注册发现、不做流量代理**，消费方拿到地址后是**直连**的，所以注册进去的地址必须是消费者能直连的。我用 Dubbo 官方为容器/NAT 场景提供的 `DUBBO_IP_TO_REGISTRY` + `DUBBO_PORT_TO_REGISTRY` 让 provider 注册宿主内网 IP + 映射端口，并且因为 `dubbo.protocol.port: -1` 是自动选端口，我还显式固定了协议端口才能做映射。另一条路是新机加静态路由打通老机 bridge 网段（手工版 overlay），老机零改动但依赖网段、无自愈——**我选了规范的那条，并把老机的改动收敛到 2 个服务**。"

**被追问"为什么不是全部服务都改"**："因为跨机依赖只有秒杀副本对 product/order 的 Dubbo 调用（我扫过代码，`mall-seckill` 没有任何 HTTP/Feign 调用）——**按需改造**，其余服务的注册不动，改动面最小。"

**被追问"Redis 主从切换后为什么能连回来"**："除了哨兵选主，还要给老机 Redis 配 `replica-announce-ip`——它被降级成从库时要向新主上报**宿主内网 IP**，否则上报容器 IP，新机连不回来，复制就断了。这种'切换能成功、但回不来'的坑，光看哨兵日志是发现不了的。"

### 5.8 疑惑点：为什么要用"哈希指纹"（md5）？算行业规范吗？

**起因**：本批每次跨机下发 compose/配置/jar，AI 都要求"本地算 md5 → 落位后 `md5sum` 比对"。

**① 原理：哈希是内容的"身份证"，不是版本号**

跨机下发经过 **4 跳**（本地仓库 → scp → 服务器 `/tmp` → sudo cp → `/data/csmall`），每跳都可能**静默改字节**：CRLF↔LF 转换、UTF-8 BOM、传输截断、`cp` 未报错的失败。哈希函数（md5/sha256）的雪崩效应保证：**内容差 1 个字节，32 位十六进制全变**。所以 `md5sum` 一致 ⟺ 服务器文件与仓库文件**字节级相同**。

| | 版本号 | 哈希指纹 |
|---|---|---|
| 语义 | 人为命名、单调递增（`v1.2.0`） | 内容的数学函数，**无顺序含义** |
| 用途 | 沟通、发布、回滚标记 | **校验"是不是同一份内容"** |
| 改内容 | 需人工改 | **自动全变**（这正是我们要的） |

它把"仓库为事实来源"这条纪律从**口号**变成**可验证的断言**——这是本批最实用的一条运维纪律。

**② 行业规范吗？—— 是，而且是同一个原理的自动版**

| 场景 | 行业做法 |
|---|---|
| 软件分发 | 发行版 ISO 公布 **SHA-256**；Maven Central 每个制品旁挂 `.sha1`/`.md5` |
| 容器 | 镜像 **digest = `sha256:…`**（内容寻址，`image@sha256:…` 拉取即校验） |
| K8s | ConfigMap 变更触发滚动更新靠**内容哈希**（Helm `checksum/config` 注解） |
| CI/CD | 制品完整性、SBOM、**SLSA** 供应链 |
| 配置管理 | Ansible/GitOps 下发后做**漂移检测** |

我们做的是**手工版**（AI 算 + 人比对）；企业里是**自动版**（CI 记录制品 digest，Argo CD/Ansible 下发后自动比对并报漂移）。**原理完全相同，只是被自动化了。**

**③ 诚实边界**：
- **MD5 已被证明可构造碰撞**（2004 年起）→ **防"意外损坏"够用，防"恶意篡改"不够**；安全场景应用 **SHA-256**。
- 演进路径：① 换 `sha256sum`（零成本）→ ② 配置纳入 git、服务器 `git pull`（git 对象哈希天然校验）→ ③ CI 构建 + digest 下发（对应 TODO #39）。
- **凭据文件例外**：`.env` 只统计行数/权限，**连哈希也不对外报**（避免任何形式的凭据侧信道）。

**④ 话术**："跨机下发我用哈希做端到端校验——多跳传输可能静默改字节，哈希让'仓库是事实来源'变成可验证断言。行业里这叫内容寻址，ISO、镜像 digest、K8s 滚动更新都是这套；我们目前是手工版，演进方向是 `git pull` 或 CI digest 下发。注意 MD5 只防意外不防篡改，安全场景要 SHA-256。"

> 命令级落地与指纹记录表：[[跨机集群实施执行清单-2026-09-09]] §十二

### 5.9 故障转移演练：切换到底怎么发生的（⭐ 本批最有说服力的证据）

**① 一次选主的四个阶段**（实测 6.1s 走完）

| 阶段 | 事件 | 谁在动 | 实测耗时 |
|---|---|---|---|
| 1 | **`+sdown` 主观下线**：单个哨兵发现主库 `PING` 超时 | 任一哨兵 | +0~5s |
| 2 | **`+odown` 客观下线**：达到 `quorum` 的哨兵都认为它挂了 | 多哨兵投票 | **+5.0s**（= `down-after-milliseconds 5000`） |
| 3 | **`+elected-leader`**：基于 Raft 的 leader 选举，选出一个哨兵主持切换 | 哨兵之间 | +5.1s（≈0.06s） |
| 4 | **`+promoted-slave` + `+switch-master`**：leader 提升从库、广播新拓扑 | leader 哨兵 | +6.1s |

**② 时间都花在哪**：**绝大部分是 `down-after-milliseconds 5000`**（必须等够 5s 才能确认主库真死，否则网络抖动就会误切）。真正的选举+提升只用了 **1.1s**。→ 这个值就是"**误判风险 vs 切换速度**"的旋钮：调小切得快但易误切，调大更稳但停机久。

**③ 客户端为什么能自动重连**（这是阶段 3.5 的回报）

Lettuce 的 `ConnectionWatchdog` 在连接断开后：① 按退避策略重连**旧地址**；② 连不上就**重新向哨兵询问主地址**；③ 拿到新地址后直接连新主。实测日志：

```
11:34:01.810 Reconnecting, last destination was 172.29.193.239:6379
11:34:01.841 WARN Cannot reconnect: Connection refused: /172.29.193.239:6379
11:34:10.912 INFO Reconnected to 172.29.193.240:6380      ← 自动切到新主
11:35:59.710 INFO Reconnected to 172.29.193.239:6379      ← 切回老主
```

> **对照**：如果客户端还是 standalone 模式，它会**永远重连那个已死的旧地址**——主从切换对它是"从可用变成不可用"。这就是阶段 3.5 必须做、且必须全部 11 个服务都做的原因。

**④ ⭐ 脑裂窗口 = 10.9s（演练最大的发现）**

老主库 `docker start` 回来后，因为它的 `redis-master.conf` 里**没有 `replicaof`**，它会**按配置自认为主**（实测 `role:master` 持续 10.9s），直到哨兵下发 `+convert-to-slave` 才降级。

- **为什么这么久**：哨兵要等主库恢复被感知（`-sdown`）、再重新纳入拓扑、再下发降级指令。
- **为什么业务没事**：客户端只连**哨兵返回的地址**，不会主动连老地址。
- **为什么仍要防**：万一有客户端/脚本直连老地址，这 10.9s 内它会把数据写进一个**即将被全量覆盖的主库** → 数据丢失。这正是 **`min-replicas-to-write 1`（TODO #14-P2）** 的价值：让"没有从库确认"的主库**拒绝写**。
  > ✅ **Redis 官方文档佐证**（Sentinel 文档 "Example 2: basic setup with three boxes"）：官方用**同一场景**（分区里的旧主库继续被写）说明该配置，并直接给出：
  > ```conf
  > min-replicas-to-write 1
  > min-replicas-max-lag 10
  > ```
  > *"the old Redis master M1 … will become unavailable after 10 seconds. When the partition heals, the Sentinel configuration will converge to the new one"* —— 也就是说，**#14-P2 不是我们自己想出来的加固，而是官方对"哨兵架构固有缺陷"的标准补丁**，我们演练测到的 10.9s 窗口正是它要覆盖的时间。
- **顺带解释了另一个坑**：老机 `redis-master.conf` 是**单文件 bind mount**，Redis 改写配置需要"写临时文件 + rename"→ 必然 `Resource busy`（与哨兵踩的坑同源）→ **降级只能靠哨兵运行时下发，不可能落盘**。

**⑤ 🔴 红线：哨兵 conf 是"运行时状态"，不能反向覆盖**

演练后 3 个 `sentinel-*.conf` 都被哨兵**整体重写**，多了 `sentinel config-epoch mymaster 2`、`myid`、known-sentinel/known-replica 等运行时状态。→ **仓库里的 conf 只是"初始模板"**；任何时候都不要把模板反向覆盖到运行中的哨兵 conf，否则会丢 epoch 与拓扑认知（哨兵可能又按旧地址找主库）。

**⑥ 话术**："我做过真实的故障转移演练：停掉老机主库，**6.1s 哨兵完成选主**（`down-after 5s` + Raft 选举 + 提升），**9.1s Lettuce 客户端自动重连到新主**，11 个服务零硬错误、数据零丢失，之后 `SENTINEL FAILOVER` **1.1s 切回**。过程中我发现**老主库重启后有 10.9s 的脑裂窗口**——它按静态配置自认为主，直到哨兵降级；所以 `min-replicas-to-write 1` 不是可选项，而是防止这段窗口被直连客户端写脏数据的关键配置。另外哨兵的 conf 会被运行时重写，仓库模板绝不能反向覆盖。"

## 六、阶段 4：秒杀双实例与故障剔除（✅ 已完成 2026-09-09 21:19—21:32）

### 6.1 目标与最终拓扑

**目标**：把秒杀从"老机单实例"变成"跨机双实例"，验证 **Nacos 双实例注册 + 网关负载均衡 + 定时任务不双跑 + 实例挂掉不影响服务**。

| 角色 | 老机 | 新机 |
|---|---|---|
| 秒杀实例 | `csmall-seckill`（10007，**容器 IP** 注册 `172.18.0.19`） | `csmall-seckill-2`（10017，**宿主 IP** 注册 `172.29.193.240`） |
| Dubbo provider | `172.18.0.19:20880` | `172.29.193.240:20880` |
| Redis | 主（哨兵模式客户端） | 从 + 3 哨兵（客户端也走哨兵） |

> ⚠️ **新机副本的 compose 必须与老机口径一致**：阶段 3.5 把 11 个服务迁到哨兵模式后，`mall-seckill-2` 若仍用 `SPRING_DATA_REDIS_HOST`（standalone），**主从切换后它会连到只读从库报 `READONLY`** —— 这是本次实施前发现并修掉的配置缺口。

### 6.2 实施时间线与判据（2026-09-09 实测）

| 时刻 | 动作 | 结果 |
|---|---|---|
| 21:19:23 | 【新机】起副本 | `Started ... in 78.173s`，health 200 |
| 21:20:51 → 21:22:08 | 【老机】换带锁 jar 并重建 | **停机 77s**（期间网关路由到副本，业务未整体中断） |
| — | Nacos | HTTP 2 实例 + Dubbo 2 provider（注册 IP 覆盖生效） |
| — | 网关 | 同时与两个实例保持 ESTABLISHED 连接（TCP 取证） |
| — | 错误扫描 | 两实例各仅 1 条 ERROR（Dubbo 启动期既有报错），**Redis 错误 0** |

### 6.3 ⭐ 分布式锁在真实集群里的验证（Redis `MONITOR` 取证）

阶段 0 写的锁代码，**在真实双实例下才算真正验证**。用 `MONITOR` 抓真实命令：

**① 常态**：18 秒内抓到 8 次 `SET mall:seckill:lock:message-retry <uuid> EX 30 NX` + 8 次 Lua `EVALSHA` 解锁，来源交替为 `172.18.0.1`（老机）与 `172.29.193.240`（新机）→ 锁在两台都生效。

**② 受控争用**（人工 `SET ... MANUAL-CONTENTION-TEST EX 25 NX` 占锁，模拟"另一实例正在执行"）：

| 观察 | 结果 | 证明 |
|---|---|---|
| 20 秒内两实例 `SET NX` 尝试 | **10 次（各 5 次）全部失败**，锁值仍是人工 token | **`NX` 互斥生效** |
| 期间解锁次数 | **0**（时间线：`206.903` 新机取锁释放 → `206.962` 老机取锁释放 → `207.877` 人工占锁 → 之后无 EVALSHA） | 抢不到锁的实例**正确跳过** |
| 残留锁 key | 无 | 正常释放 |

**③ 为什么用 MONITOR 而不是日志**：两个任务的"未抢到锁"日志是 **debug** 级（默认 INFO 看不到），而 `MONITOR` 能直接看到**命令、参数、来源 IP、时间戳**——这是"可观测性优先级高于猜测"的典型例子。

### 6.4 ⭐ 停实例故障剔除：Nacos 即时摘除 vs 客户端缓存延迟（16s 窗口）

**方法**：老机起每 2 秒的探针（记录「网关 HTTP 码」+「Nacos 实例数」）→ 新机 `docker stop csmall-seckill-2` → 观察 → 恢复。

**实测时间线**

| 时刻 | 网关 | Nacos 实例数 | 说明 |
|---|---|---|---|
| 21:29:05.190 | — | — | 停副本 |
| **21:29:06** | 500 | **1** | **Nacos 不到 1 秒摘除** |
| 21:29:06—21:29:22 | **500/200 交替** | 1 | 网关仍打一半请求到死实例 |
| 21:29:24+ | **全部 200** | 1 | 单实例稳定服务 |
| 21:32:07 | 200 ×10 | **2** | 副本恢复，拓扑复原 |

**两个结论**

1. ✅ **故障剔除成立**：实例死后流量由存活实例承接，稳定后连续 40+ 次探测全 200、**无 5xx**。
2. ⚠️ **存在 ~16 秒失败窗口**（9 次探测中 5 次 500）：**Nacos 侧即时，客户端侧不即时**。根因日志：`finishConnect(..) failed: Connection refused: /172.29.193.240:10017` → `500 Server Error`。

### 6.5 疑惑点：为什么"高可用 ≠ 零中断"？怎么做到零中断？

**① 服务发现有三层，任何一层有延迟都会出现"打到死实例"的窗口**

```
实例存活  ──①心跳/主动注销──▶  注册中心  ──②推送/轮询──▶  客户端  ──③本地LB缓存TTL──▶  真正发请求
```

| 层 | 本次实测 | 关键机制 |
|---|---|---|
| ① 实例 → 注册中心 | **<1 秒** | Spring Cloud Alibaba **优雅停机主动注销**（`ContextClosedEvent` → `deregister`）；**若是 `docker kill`/进程崩溃，就只能等 15s 心跳超时** |
| ② 注册中心 → 客户端 | 秒级 | Nacos 推送（UDP/gRPC）+ 客户端兜底轮询 |
| ③ 客户端本地缓存 | **~16 秒** | Spring Cloud LoadBalancer 的实例列表缓存（`spring.cloud.loadbalancer.cache.ttl`，**默认 35s**） |

**② 所以"高可用"解决的是"服务还能用"，不是"请求一次都不失败"** —— 要做到后者，必须做**优雅下线（graceful shutdown / connection draining）**：

1. **先摘流量**：调 Nacos 注销接口（或把实例权重设为 0）
2. **等 > 客户端 LB 缓存 TTL**（默认 35s，所以滚动发布一般等 30–60s）
3. **再停容器**

> 这就是 K8s 里 **readiness probe + preStop sleep** 的等价物：K8s 先让 Pod 从 Service Endpoints 摘掉，再等 `preStop` 睡一会儿，最后才发 SIGTERM。
>
> **本次演练是"直接停容器"的粗暴版**，所以看到了 16s 窗口 —— 这恰好反证了优雅下线的必要性。

**③ 兜底手段**（即使不做优雅下线也能减少影响）：网关重试 / 断路器 / 缩短 LB 缓存 TTL（见 TODO #53）。

### 6.6 面试话术

**主线**："我把秒杀做成跨机双实例：Nacos 里两个实例、网关 `lb://mall-seckill` 轮询、Dubbo 也注册了两个 provider。关键是**定时任务不能双跑**——我用 `SET NX EX` + Lua CAS 的 Redis 锁，并用 `MONITOR` 做了**受控争用验证**：人工占锁 25 秒，两个实例共 10 次 `SET NX` 全部失败、0 次误删，证明互斥真的成立。"

**被追问"实例挂了会怎样"**："我实测停掉一个实例：**Nacos 不到 1 秒就摘除了**（因为 Spring Cloud Alibaba 优雅停机会主动注销），但**网关的本地 LB 缓存有 ~16 秒延迟**，这期间轮询会让一半请求打到死实例报 500。所以『高可用』不等于『零中断』——要真零中断得做**优雅下线**：先摘流量、等超过 LB 缓存 TTL（默认 35s）、再停容器，本质和 K8s 的 readiness probe + preStop 是一回事。这个窗口光看配置文件永远发现不了。"

### 6.7 ⭐ 负载均衡分布验证：怎么证明"请求真的被均分了"（60 次 → 30:30）

**① 难点：谁来告诉你"这个请求被哪个实例处理了"？**

| 常规手段 | 结果 |
|---|---|
| 服务访问日志 | 项目没配 |
| `actuator/metrics` | 被业务 `SSOFilter` 拦住（返回业务 401 体） |
| 网关日志 | 只在出错时才有 |

→ **可观测性不够时，就用"副作用"来归因**。

**② 办法：Redis `MONITOR` + 每个请求的 Redis 副作用**

`GET /seckill/spu/list?page=1&pageSize=10` 的实现里，对列表中的每个 SPU 都做 `redisTemplate.hasKey(...)` → **每个请求固定产生 12 条 `EXISTS mall:seckill:reseckill:<spuId>:<skuId>`**。而 `MONITOR` 记录**命令来源 IP**：

| 来源 IP | 对应实例 |
|---|---|
| `172.18.0.1` | 老机实例（容器出网经 docker bridge SNAT） |
| `172.29.193.240` | 新机实例（宿主 IP） |

**③ 实测结果**

| 项 | 值 |
|---|---|
| 请求数 | 60（全部 HTTP 200） |
| Redis `EXISTS` 总数 | **720 = 60 × 12** |
| 老机（`172.18.0.1`） | 360 条 → **30 个请求** |
| 新机（`172.29.193.240`） | 360 条 → **30 个请求** |
| **分布** | **50% : 50%（精确 30:30）** |

→ **Spring Cloud LoadBalancer 默认 `RoundRobin` 生效**，跨机轮询精确均分。

**④ 为什么这个验证值得记**

- **登录态怎么来**：`cs_mall_ums.ums_user` 有 110 个测试账号（`testuser1`~`testuser110`，统一弱密码，demo 数据）→ 经网关 `POST /user/sso/login` 拿 `data.tokenValue`，请求头带 `Authorization: Bearer <token>`。**注意 token 字段名是 `tokenValue` 不是 `token`，且必须带 `Bearer` 前缀**（第一次就踩了这个）。
- **接口需要分页参数**：`/seckill/spu/list` 不带 `page` 会报 `Cannot invoke "Integer.intValue()" because "page" is null`。
- **方法论**：**没有监控指标时，"命令流 + 来源 IP"就是最可靠的归因手段**——和 §6.3 用 `MONITOR` 验证锁是同一个思路。

### 6.8 阶段 4 四项判据汇总

| 判据 | 证据 | 结果 |
|---|---|---|
| 双实例注册 | Nacos：HTTP `10007`+`10017`、Dubbo 双 provider | ✅ |
| 负载均衡均分 | `MONITOR` 60 请求 → 30:30 | ✅ |
| 定时任务不双跑 | 受控争用：10 次 `SET NX` 全失败、0 次误删 | ✅ |
| 实例挂掉不影响服务 | 停实例后存活实例承接、稳定后 0 个 5xx | ✅（⚠️ 16s 窗口见 §6.4） |

---

**维护提示**: 本文件随第三批逐项实施持续补充；完成一项更新头部状态并回填细节。与 [[TODO文件]] 保持一致（TODO 是状态源，本文件是"原理+疑惑+话术"深挖）。




