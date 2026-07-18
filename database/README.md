# CoolShark 电商平台 — 数据库结构文档

> 最后更新：2026-07-18
> 每个数据库一个子文件夹，内含该库所有表的 CREATE TABLE 语句（从 MySQL 8.0 实时导出）

---

## 目录结构

```
database/
├── README.md                          # 本文件
├── init-test-data.sql                 # 商品测试数据（SPU/SKU/品牌/分类/图片）
├── 07-seckill-test-data.sql           # 秒杀测试数据
├── cs_mall_pms/                       # 商品库（13 张表 + undo_log）
│   ├── pms_spu.sql
│   ├── pms_spu_detail.sql
│   ├── pms_sku.sql
│   ├── pms_sku_specification.sql
│   ├── pms_brand.sql
│   ├── pms_brand_category.sql
│   ├── pms_category.sql
│   ├── pms_category_attribute_template.sql
│   ├── pms_attribute.sql
│   ├── pms_attribute_template.sql
│   ├── pms_attribute_template_value.sql
│   ├── pms_album.sql
│   ├── pms_picture.sql
│   └── undo_log.sql
├── cs_mall_ams/                       # 后台管理库（6 张表）
│   ├── ams_admin.sql
│   ├── ams_admin_role.sql
│   ├── ams_login_log.sql
│   ├── ams_permission.sql
│   ├── ams_role.sql
│   └── ams_role_permission.sql
├── cs_mall_oms/                       # 订单库（3 张表 + undo_log）
│   ├── oms_cart.sql
│   ├── oms_order.sql
│   ├── oms_order_item.sql
│   └── undo_log.sql
├── cs_mall_ums/                       # 用户库（3 张表）
│   ├── ums_user.sql
│   ├── ums_login_log.sql
│   └── pms_category.sql               # ⚠ 疑似误建（不属于此库）
├── cs_mall_seckill/                   # 秒杀库（3 张表 + undo_log）
│   ├── seckill_spu.sql
│   ├── seckill_sku.sql
│   ├── success.sql
│   └── undo_log.sql
└── cs_mall_resource/                  # 资源库（1 张表）
    └── res_upload_record.sql
```

---

## 数据库清单

| 数据库 | 用途 | 表数 | 模块 |
|--------|------|------|------|
| cs_mall_pms | 商品 SPU/SKU/品牌/分类/属性/相册 | 14 | mall-product |
| cs_mall_ams | 管理员/角色/权限/登录日志 | 6 | mall-ams, mall-sso |
| cs_mall_oms | 订单/订单项/购物车 | 4 | mall-order |
| cs_mall_ums | 用户/登录日志 | 3 | mall-ums, mall-sso |
| cs_mall_seckill | 秒杀 SPU/SKU/成功记录 | 4 | mall-seckill |
| cs_mall_resource | 用户上传文件记录 | 1 | mall-resource |

> **总表数**: 32 张（含 4 个 Seata undo_log 表）

---

## 如何初始化

### 方式一：逐表执行

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS cs_mall_pms DEFAULT CHARSET utf8mb4;"

# 执行单张表的建表语句
mysql -u root -p cs_mall_pms < database/cs_mall_pms/pms_spu.sql
```

### 方式二：批量执行整个库

```bash
# 批量执行某个库的所有表
for f in database/cs_mall_pms/*.sql; do
  mysql -u root -p cs_mall_pms < "$f"
done
```

### 方式三：加载测试数据

```bash
# 商品/品牌/分类/图片测试数据
mysql -u root -p < database/init-test-data.sql

# 秒杀测试数据
mysql -u root -p < database/07-seckill-test-data.sql
```

---

## 注意事项

1. **字符集**: 所有表使用 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`
2. **存储引擎**: 全部使用 InnoDB
3. **主键策略**: 所有主键由应用层生成（MyBatis-Plus `IdWorker` 雪花算法），非数据库自增
4. **时间字段**: `gmt_create` 默认 `CURRENT_TIMESTAMP`，`gmt_modified` 自动更新
5. **undo_log**: Seata AT 模式回滚日志表，每个参与分布式事务的数据库都需要
6. **cs_mall_ums.pms_category**: 该表不属于用户库，可能是误建或历史遗留，建议确认后清理
7. **is_ 前缀字段**: 注意 MyBatis-Plus 不会自动映射 `is_deleted`/`is_published` 等字段，需 `@TableField` 注解

---

## 表结构同步

当代码中的实体类字段变更后，需同步更新本目录下的对应 SQL 文件：

```bash
# 重新导出单表（示例）
mysql -u root -proot --default-character-set=utf8mb4 -N \
  -e "SHOW CREATE TABLE pms_spu\G" cs_mall_pms \
  | sed 's/^.*Create Table: //' | sed 's/\\n/\n/g' | sed '1,2d' \
  > database/cs_mall_pms/pms_spu.sql
```
