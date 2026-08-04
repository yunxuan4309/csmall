# 数据库 Schema 漂移审计报告

> **日期**：2026-08-03
> **范围**：本地 MySQL 39 张表 × mall-pojo 32 个实体 × database/ 34 个 SQL 文件，三方对比
> **用途**：记录实体类、数据库、建表脚本三者之间的不一致点，作为企业级升级的前置修复清单

---

## 一、总体结论

**核心业务表（spu/sku/order/cart/brand/category/seckill/user/admin）实体与数据库完全对齐**，当前可正常运行。

存在 3 类问题：
1. **5 张表实体字段与数据库不一致**（已知遗留差异，靠自定义 Mapper XML 规避，暂不报错）
2. **4 张 ums 表整体缺失**（休眠，用到即报错）
3. **若干 SQL 文件/文档过期**

---

## 二、已修复（2026-08-03）

| 表 | 问题 | 处理 |
|----|------|------|
| `pms_attribute_template` | 实体有 pinyin/keywords/sort，本地+服务器数据库都没有 | 本地 ALTER 补 3 列 + 更新 `database/cs_mall_pms/pms_attribute_template.sql` |
| `pms_brand_category` / `pms_category_attribute_template` / `ams_role_permission` / `ams_admin_role` | 实体有 `gmt_modified`，数据库没有 | 本地 ALTER 补 `gmt_modified` 列 |
| `database/cs_mall_pms/pms_brand.sql` | SQL 只有 8 列，真实库 17 列 | 已更新为完整结构 |
| `database/README.md` | 目录树/表数/注意事项多处过期 | 已修正 |

> **⚠️ 服务器部署前需同步**：`pms_attribute_template` 的 3 列 + 4 张关联表的 `gmt_modified`，服务器数据库也需 ALTER（见文末）。

---

## 三、高危：实体与数据库字段不一致（待升级处理）

> 当前靠自定义 Mapper XML 规避不报错；**一旦使用 MyBatis-Plus BaseMapper 查询/插入就会报 `Unknown column`**。

| 表 | 实体有但数据库没有 | 数据库有但实体没有 | 说明 |
|----|------------------|-------------------|------|
| `pms_sku_specification` | `attribute_name`、`attribute_value`、`unit`、`sort` | `attribute_value_id` | SKU 规格表，升级 SKU 体系必用 |
| `pms_attribute` | `description`、`input_type`、`value_list`、`unit`、`allow_customize`；外键列 `template_id`（库是 `attribute_template_id`） | `type`、`values` | 属性表，模板生成 SKU 的核心 |
| `pms_attribute_template_value` | `attribute_id`；外键列 `template_id`（库是 `attribute_template_id`） | `value` | 模板可选值表 |
| `pms_picture` | `cover`、`description`、`width`、`height` | `title` | 相册图片表，相册系统启用时需修 |
| `pms_album` | `sort` | — | 相册表 |
| `ams_login_log` | `nickname`、`ip`（库是 `ip_address`）、`gmt_login`（库是 `login_time`） | — | 管理员登录日志，若改 BaseMapper 需对齐 |

**修复方向**（升级时）：以实体为准，ALTER 补列 + 对齐外键列名（`template_id` → `attribute_template_id`）。

---

## 四、表整体缺失（休眠）

以下 4 个 ums 实体在本地库**没有对应表**（`database/` 里也没有建表文件）：

| 实体 | 表名 | 现状 |
|------|------|------|
| `UserDetail` | `ums_user_detail` | 休眠 |
| `DeliveryAddress` | `ums_delivery_address` | 收货地址目前在订单里直接录，不走独立表 |
| `RewardPointLog` | `ums_reward_point_log` | 休眠 |
| `ChangePasswordLog` | `ums_change_password_log` | 休眠 |

**风险**：用到即报 `Table doesn't exist`。升级时可决定：补建表（启用功能）或删除实体（弃用）。

---

## 五、database/ SQL 文件与数据库的差异

| 问题 | 详情 |
|------|------|
| ~~pms_brand.sql 过期~~ | ✅ 已修复（2026-08-03 更新为 17 列） |
| ~~README.md 过期~~ | ✅ 已修复（目录树/表数/注意事项） |
| `success` 表命名不规范 | 秒杀库的表没 `seckill_` 前缀，跨库易混淆 |
| undo_log 字符集 | 3 份 undo_log 是 `utf8mb3`，其他全 `utf8mb4` |
| 主键策略矛盾 | README 说"应用层生成"，但 undo_log/ams_login_log/ums_login_log 用了 AUTO_INCREMENT，且 ums_login_log 自增起点是雪花式巨值 |
| init-test-data.sql 文案 | 结尾提示写"3 个测试用户"，实际插入 10 个 |

---

## 六、企业级升级前置修复清单（建议顺序）

1. **对齐 pms_sku_specification**：ALTER 补 `attribute_name`/`attribute_value`/`unit`/`sort`（或改实体为 `attribute_value_id`，取决于 SKU 规格设计）
2. **对齐 pms_attribute + pms_attribute_template_value**：外键列统一为 `attribute_template_id`，实体字段按需调整
3. **相册系统**：pms_album 补 `sort`，pms_picture 按设计决定用 `title` 还是 `cover`/`width`/`height`
4. **ums 4 张缺失表**：决定补建或删除实体
5. **服务器数据库同步**：部署前执行与本地一致的 ALTER

---

## 七、服务器部署前必做的 ALTER（汇总）

```bash
# 1. pms_attribute_template 补 3 列（本地已做，服务器也要）
ALTER TABLE cs_mall_pms.pms_attribute_template
  ADD COLUMN pinyin varchar(64) DEFAULT NULL COMMENT '模板名称拼音' AFTER name,
  ADD COLUMN keywords varchar(255) DEFAULT NULL COMMENT '关键词列表' AFTER pinyin,
  ADD COLUMN sort int DEFAULT 0 COMMENT '自定义排序序号' AFTER keywords;

# 2. 4 张关联表补 gmt_modified
ALTER TABLE cs_mall_pms.pms_brand_category ADD COLUMN gmt_modified datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间' AFTER gmt_create;
ALTER TABLE cs_mall_pms.pms_category_attribute_template ADD COLUMN gmt_modified datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间' AFTER gmt_create;
ALTER TABLE cs_mall_ams.ams_role_permission ADD COLUMN gmt_modified datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间' AFTER gmt_create;
ALTER TABLE cs_mall_ams.ams_admin_role ADD COLUMN gmt_modified datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间' AFTER gmt_create;
```

---

**关联**：`docs/企业级商品管理升级计划.md`（升级规划）、`docs/项目上下文文档.md` 8.4 节（实体映射遗留差异）
