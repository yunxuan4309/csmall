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
| 逆序清理 | `CLEAN_ORDER` 9 张表严格子→父；`oms_order_item` 按登记到的 `order_id` 反查（它自己没有 `user_id`） | 方案 §2.2.5 |
| 分批 + 单批事务 | 每 500 个 id 一批，`commit`/`rollback` | 方案 §2.2.5 |
| 默认 dry-run | 不带 `--apply` 只 `SELECT COUNT(*)` 打印将删行数 | 方案 §2.2.5 / §六 |
| 幂等 | 已 `cleaned` 的批次会提示；删不到就跳过 | 方案 §2.2.5 |
| fail-fast 预检 | 影子表在不在 / `test_sim_%` 是否为 0 / 库存够不够 / 地址模板在不在 / 目录非空 | §〇.1 D7 |
| 库存预算 | `need_units = days × per_day × 5% × 2 件`，与 `SUM(pms_sku.stock)` 比对 | §〇.1 D7 |
| 不可逆字段不硬算 | 只告警，交给快照整库还原 | 方案 §2.2.7 |
| 凭据不落盘 | `SIM_DB_PASSWORD` 环境变量 | §〇.1 D6 |
| 🏷️ **模拟数据标识** | **让数据自证身份**（零 DDL，复用现有自由字段）：用户名 `test_sim_*` · 手机号 `1390000xxxx`（假号段）· 昵称/地址/联系人含"模拟" · 邮箱 `@example.com` · **订单 `tag=SIM`** · **订单项 `data={"sim":true,…}`** · 支付 `extra_data`（服务端写，按 order_id 反查）· **权威登记表 `cs_mall_sim.sim_entity`** | 方案 **§2.2.9** |

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
| 未验证 | ❌ 涉及**写**的整条链路（注册/登录/加购/下单/支付/清理）—— 需要 DB 密码与生产写权限，由用户在窗口内执行 |

## 九、关联文档

[[Python模拟数据与AI并发测试方案]]（§〇.1 复核 · §2.2 隔离 · §2.5 清理清单 · §3.5 压测剧本）
· [[TODO文件]]#48 · [[TODO已完成]]#29（cron 备份）/ #47（恢复演练）
· [[TODO第三批实现与原理-1]] §四（选型过程）
