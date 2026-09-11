# #48 模拟数据脚本（第一层：造数）

> **来源方案**：`docs/评估报告/Python模拟数据与AI并发测试方案.md`
> （规范设计定稿 2026-09-10 + **实施前复核 2026-09-11 §〇.1**，本节与方案文档**双向对应**）
> **对应 TODO**：`docs/评估报告/TODO文件.md` **#48**
> **本目录只做"第一层：造数"**；第二层 AI 并发压测与 mock LLM 见方案文档 **§3.5**。

## 一、目录内容

| 文件 | 作用 |
|---|---|
| `simulate_data.py` | 造数主脚本（登记驱动 · 逆序清理 · dry-run · fail-fast 预检） |
| `init_sim_db.sql` | 影子登记库 `cs_mall_sim` 的 DDL（`sim_batch` / `sim_entity` / `sim_baseline`） |
| `requirements.txt` | 依赖（`requests==2.31.0` / `PyMySQL==1.1.1`，版本钉死） |
| `README.md` | 本文件 |

> ⚠️ **`deploy/` 在 `.gitignore` 里**（`.gitignore:49`）→ 本目录需 `git add -f deploy/scripts/sim/` 才入库，
> 与 `deploy/docker/`、`deploy/scripts/graceful-stop.sh` 同一策略。

## 二、为什么必须是"新机 + venv"（实测，见 §〇.1 D6）

| 事实 | 实测（2026-09-11 新机 `47.109.70.197`） |
|---|---|
| Python | `python3 3.12.3` ✅ |
| `requests` | `2.31.0` 已有 ✅ |
| `pymysql` | **未装** ❌ |
| `pip install` | 被 **PEP 668**（`externally-managed-environment`）拒绝 → **必须用 venv** |
| `mysql` 客户端 | **未装** ❌（所以脚本用 PyMySQL，不走命令行客户端） |
| 新机 → 老机 | `3306` / `6379` / `10087` **全通**，网关 `/actuator/health` **26ms**（私网，不限速不计费）✅ |

```bash
# 新机上准备一次即可
python3 -m venv ~/sim-venv
~/sim-venv/bin/pip install -r requirements.txt        # 需要外网；不通就用国内源 -i https://pypi.tuna.tsinghua.edu.cn/simple
```

## 三、凭据（不落盘 —— §〇.1 D6）

```bash
export SIM_DB_PASSWORD='<老机 MySQL root 密码>'      # 只放环境变量，脚本里没有明文
# 可选覆盖：
export SIM_BASE='http://172.29.193.239:10087'        # 网关（内网！禁止公网 IP）
export SIM_DB_HOST='172.29.193.239'
export SIM_RESOURCE_HOST='http://8.156.77.197/'      # 图片前缀
```

> ❌ **禁止**把密码写进脚本、写进 `git`、或写进本文档。

## 四、SOP（严格按序，对应方案文档 §2.2.8 / §五）

```bash
# ⓪ 🔴 迁移先行（首次执行；否则 --preflight 会因"data_source 列缺失"直接拒绝）
#    4 个 Flyway 迁移文件已入库：ums V3 / oms V7 / seckill V6 / resource V2（方案 §2.2.9）
#    ① 低峰**逐个重启**（每个 ~40-60s）：mall-ums → mall-order → mall-seckill → mall-resource
#    ② 复核（期望输出 9 行）：
#       docker exec -i csmall-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B \
#         -e "SELECT CONCAT(table_schema,\".\",table_name) FROM information_schema.columns \
#             WHERE column_name=\"data_source\" ORDER BY 1"'
#    🔴 禁止手工 ALTER 加列：会与迁移冲突 → Duplicate column name → Flyway 失败 → 服务起不来
#    🔴 也**不要**去"统一行尾"：Flyway validate 比对迁移文件**字节的 checksum**。
#       实测本仓库 core.autocrlf=true 且无 *.sql 的 .gitattributes → 工作区行尾取决于谁写的
#       （18 个迁移：17 个 w/lf、1 个 w/crlf；索引里全是 i/lf）。**改动已应用过的迁移文件行尾
#       → checksum mismatch → 服务起不来**。构建 jar 请**始终用当前这份工作副本**。

# ① 建影子库（一次性；在老机执行）
docker exec -i csmall-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < init_sim_db.sql

# ② 预检（不写任何数据；不通过直接退出）
~/sim-venv/bin/python simulate_data.py --preflight --days 2 --per-day 1000

# ③ ★ 快照先行：造数前做全量快照（老机 MySQL 数据目录实测仅 207M，成本极低）
#    docker exec csmall-mysql mysqldump ... （复用 #29 的 cron 备份脚本）

# ④ 造数（慢节奏；每创建一个实体都写一行 sim_entity）
~/sim-venv/bin/python simulate_data.py --days 2 --per-day 1000 --users 20

# ⑤ 清理**预览**（默认 dry-run，只统计行数）
~/sim-venv/bin/python simulate_data.py --clean --batch sim_20260911_1530

# ⑥ 真删（加 --apply）
~/sim-venv/bin/python simulate_data.py --clean --batch sim_20260911_1530 --apply
```

## 五、脚本做了什么（与方案文档条款一一对应）

| 设计点 | 实现 | 对应 |
|---|---|---|
| 登记驱动 | `Registry.register()`：**每个实体一行** `sim_entity`，与业务写入**同一连接/事务** | 方案 §2.2.3 ② |
| 逆序清理 | `CLEAN_ORDER` 9 张表严格子→父；`oms_order_item` 按登记到的 `order_id` 反查（它自己没有 `user_id`）；**服务端写的 5 张（登录日志/支付记录/秒杀成功/秒杀重试/上传记录）按 `user_id` 删** | 方案 §2.2.5 |
| 🔴 **修掉一处漏删** | `oms_payment_record` 原挂 `pk`（按登记主键）模式，但脚本**从不登记支付记录** → 走"无登记 → 跳过" → **支付记录静默残留**（全库 0 外键，删漏不报错）。实测该表有 `user_id` → 改为 `user` 模式（2026-09-11） | 方案 §2.2.9「回填矩阵」 |
| 🏷️ **回填专用列** | `backfill()`：造完数按登记表 `UPDATE … SET data_source='SIM'`（**服务端零改动**，幂等）；`verify_backfill()` 逐表比对"应标/实标"行数，**漏标与误标都报** | 方案 §2.2.9 |
| 清理后自检 | `clean()` 删完再查 9 张表是否仍有 `data_source='SIM'` 残留（全库 0 外键，漏删不会报错，只能主动查） | 方案 §2.5 / §七 |
| 分批 + 单批事务 | 每 500 个 id 一批，`commit`/`rollback` | 方案 §2.2.5 |
| 默认 dry-run | 不带 `--apply` 只 `SELECT COUNT(*)` 打印将删行数 | 方案 §2.2.5 / §六 |
| 幂等 | 已 `cleaned` 的批次会提示；删不到就跳过 | 方案 §2.2.5 |
| fail-fast 预检 | 影子表在不在 / `test_sim_%` 是否为 0 / 库存够不够 / 地址模板在不在 / 目录非空 / 🆕 **9 张表的 `data_source` 列是否已就位**（列缺 = 迁移没跑 → 拒绝执行） | §〇.1 D7 / §2.2.9 |
| 库存预算 | `need_units = days × per_day × 5% × 2 件`，与 `SUM(pms_sku.stock)` 比对 | §〇.1 D7 |
| 不可逆字段不硬算 | 只告警，交给快照整库还原 | 方案 §2.2.7 |
| 凭据不落盘 | `SIM_DB_PASSWORD` 环境变量 | §〇.1 D6 |
| 🏷️ **模拟数据标识** | **让数据自证身份**，分两级：<br>① **专用列 `data_source`**（`NULL`=常规 / `SIM`=模拟造数；9 张表统一，由 **4 个 Flyway 迁移**加列）→ 值由脚本**按登记表回填**（**4 张脚本写的按主键**、**5 张服务端写的按 `user_id`**）<br>② **人眼可辨档**：用户名 `test_sim_*` · 手机号 `1390000xxxx`（假号段）· 昵称/地址/联系人含"模拟" · 邮箱 `@example.com`<br>**权威仍是登记表 `cs_mall_sim.sim_entity`** —— `data_source` 只表达"是不是造的"，**不表达"哪一批"** | 方案 **§2.2.9** |

## 六、端点事实来源（2026-09-11 读码，`文件:行`）

| 用途 | 端点 | 证据 |
|---|---|---|
| 注册模拟用户 | `POST /ums/user/register` | `mall-ums/.../controller/UserController.java:42,55`（`UserRegistryDTO` 必填 `username/nickname/email/phone/password/ackPassword`） |
| 登录 | `POST /user/sso/login` → `TokenVO{tokenHeader, tokenValue}` | `mall-sso/.../UserSSOController.java:26,35` |
| 浏览：全部 SPU | `GET /front/spu/list/all?page=&pageSize=` | `mall-front/.../FrontSpuController.java:26,47` |
| 浏览：SPU 详情 | `GET /front/spu/{spuId}` | 同上 `:61` |
| 加购 | `POST /oms/cart/add`（`CartAddDTO`：`skuId/title/price/quantity` 必填） | `mall-order/.../OmsCartController.java:24,30`；`mall-pojo/.../CartAddDTO.java:22,29,42,49` |
| 下单 | `POST /oms/order/add`（`OrderAddDTO` + `OrderItemAddDTO{skuId,title,data,mainPicture,price,quantity}`） | `mall-order/.../OmsOrderController.java:35,45`；`OrderItemAddDTO.java:23,29,40,46,53,60` |
| 支付（模拟） | `POST /oms/order/pay`（`PayOrderDTO{id, paymentType}`） | `OmsOrderController.java:76`；`PayOrderDTO.java:19` |
| 秒杀提交 | `POST /seckill/{randCode}`（`SeckillOrderAddDTO`） | `mall-seckill/.../SeckillController.java:32,40` |

**为什么目录/地址直接读 MySQL 而不是调接口**：
① `CartAddDTO`/`OrderAddDTO` 的 `title`/`price`/`amount*` 都是**客户端自己算好传进来**的，必须知道真实 SKU 数据；
② 地址的省市区街道编码自己编造会被校验拒绝 → 从**一笔已有订单抄一份合法模板**（`ADDRESS_SQL`）。
这两条也正是本脚本需要 DB 权限的原因。

## 七、⚠️ 尚未实现 / 待补

| 项 | 状态 | 说明 |
|---|---|---|
| 秒杀动作（`--with-seckill`） | ❌ **未实现**（预检会检查，执行时打印跳过） | 需要先 `GET /seckill/spu/list` 取回 **randCode**（`mall:seckill:spu:url:rand:code:<spuId>`，**JDK 序列化**，不能用 redis-cli 手工写），再 `POST /seckill/{randCode}`。且注意**秒杀限购**：同一用户同一 SKU 支付后**永久不能买**（Redis `mall:seckill:reseckill:<skuId>:<userId>`）→ 必须换 SKU 或换用户 |
| 清理 Redis | 📝 打印告警，未实现 | 需按登记 user id 精确拼 key 删（`reseckill`/`ordered`/`orderLock`）；**禁止 `--scan --pattern` 全删**；当前这三类实测各 **0 个**（§〇.1 D9） |
| 基线快照表写入 | 📝 DDL 已备（`sim_baseline`），脚本未写入 | 清理后"行数对比基线"的比对逻辑待补 |
| 结果回填 | ⚠️ 部分 | 已写 `sim_batch.done_actions` / `note`；分级指标（压测那部分）属第二层 |

## 八、验证记录（2026-09-11）

| 验证 | 结果 |
|---|---|
| `python3 -m py_compile`（新机 3.12.3 + 老机） | ✅ 通过 |
| `--help` | ✅ 通过（依赖检查已后移到 `parse_args()` 之后，未装依赖也能看帮助） |
| 依赖缺失时的守卫 | ✅ 输出 venv 安装提示并退出（非 traceback） |
| 无 `SIM_DB_PASSWORD` 时的守卫 | ✅ 明确提示"凭据不落盘"并退出 |
| 清理 SQL 形态（显式 `IN` 列表，9 张表） | ✅ 在生产库上以 `SELECT COUNT(*)` 形式全部执行通过 |
| 目录 SQL（`pms_sku JOIN pms_spu`） | ✅ 命中 **36** 行在售 SKU（生产 38 个 SKU 中） |
| 地址 SQL | ✅ 取到合法样本（`street_code` 可空 5/86，已 `COALESCE`；`street_name` 全非空） |
| 图片字段真实格式 | ✅ `pictures` 是 **JSON 数组的相对文件名**（`["spu_1_1.jpg"]`）；已有订单项 `picture_url` 用的是**完整 URL** → 脚本统一拼 `SIM_RESOURCE_HOST` |
| 🆕 **专用列只读预检（C-4）** | ✅ 查 `information_schema`：6 个业务 schema **0 个** `data_source` 列 → **迁移可安全执行**（不会 `Duplicate column`） |
| 🆕 **Flyway 版本号核对** | ✅ 实测 `flyway_schema_history` 最高版本：ums **V2** / oms **V6** / seckill **V5** / resource **V1** → **V3 / V7 / V6 / V2 确认空闲** |
| 🆕 **9 张表与关键列实测** | ✅ 表名确认（是 `success` **不是** `seckill_success`）；9 张表**都有 `id`**；服务端写的 5 张**都有 `user_id`**；`oms_order_item` **无** `user_id` → 故按 `order_id` 反查 |
| 🆕 **迁移+脚本改动后自检** | ✅ 新机 `python3 -m py_compile` 通过；**md5 本地 = 远端 `640104bb…`**（字节一致）；导入模块断言 **9 张表 / 4+5 回填分组 / 清理表集 == 回填表集 / 无借用字段残留** → `STRUCT_CHECK_OK` |
| 🆕 **4 个迁移文件已落盘** | ✅ `ums V3` / `oms V7` / `seckill V6` / `resource V2`，**只加文件、未手工 ALTER**（守 Flyway 纪律） |
| 未验证 | ❌ 涉及**写**的整条链路（注册/登录/加购/下单/支付/**回填**/清理）—— 需要 DB 密码与生产写权限，由用户在窗口内执行；**且迁移本身尚未执行**（需先低峰重启 4 个服务） |

## 九、关联文档

[[Python模拟数据与AI并发测试方案]]（§〇.1 复核 · §2.2 隔离 · §2.5 清理清单 · §3.5 压测剧本）
· [[TODO文件]]#48 · [[TODO已完成]]#29（cron 备份）/ #47（恢复演练）
· [[TODO第三批实现与原理-1]] §四（选型过程）
