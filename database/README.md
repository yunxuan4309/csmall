# 电商平台系统 - 数据库初始化脚本说明

## 📁 文件列表

本项目包含以下数据库初始化脚本：

1. **01-pms-product.sql** - 商品模块（PMS）
   - 数据库：cs_mall_pms
   - 表数量：13 张
   - 功能：商品 SPU/SKU、品牌、分类、属性、相册等管理

2. **02-ams-admin.sql** - 后台管理模块（AMS）
   - 数据库：cs_mall_ams
   - 表数量：6 张
   - 功能：管理员、角色、权限、登录日志等管理
   - 包含初始数据：默认管理员账号（admin）

3. **03-oms-order.sql** - 订单模块（OMS）
   - 数据库：cs_mall_oms
   - 表数量：3 张
   - 功能：订单、订单项、购物车管理

4. **04-ums-user.sql** - 用户模块（UMS）
   - 数据库：cs_mall_ums
   - 表数量：1 张
   - 功能：用户基本信息管理

5. **05-seckill.sql** - 秒杀模块
   - 数据库：cs_mall_seckill
   - 表数量：3 张
   - 功能：秒杀活动、秒杀商品、秒杀记录管理

## 🚀 执行方式

### 方式一：使用命令行批量执行（推荐）

在 MySQL 命令行中执行：

```bash
# 进入 MySQL 命令行
mysql -u root -p

# 依次执行各个脚本
source D:\java\csmall\database\01-pms-product.sql;
source D:\java\csmall\database\02-ams-admin.sql;
source D:\java\csmall\database\03-oms-order.sql;
source D:\java\csmall\database\04-ums-user.sql;
source D:\java\csmall\database\05-seckill.sql;
```

### 方式二：使用图形化工具

1. 打开 Navicat、MySQL Workbench 或其他 MySQL 客户端工具
2. 连接到你的 MySQL 数据库
3. 依次打开并执行上述 SQL 文件

### 方式三：使用批处理脚本（Windows）

双击运行根目录下的 `init-database.bat` 文件即可自动执行所有 SQL 脚本。

## ⚠️ 注意事项

1. **执行顺序**
   - 建议按照编号顺序执行（01 → 02 → 03 → 04 → 05）
   - 虽然各模块独立，但某些业务可能依赖其他模块的数据

2. **字符集设置**
   - 所有数据库统一使用 utf8mb4 字符集
   - 支持完整的 Unicode 字符（包括 emoji 表情）

3. **存储引擎**
   - 所有表使用 InnoDB 引擎
   - 支持事务和外键约束

4. **自增主键**
   - 所有表的主键都是外部生成的分布式 ID（非数据库自增）
   - 应用层会使用雪花算法或其他 ID 生成器生成主键值

5. **时间字段**
   - gmt_create：记录创建时间，默认 CURRENT_TIMESTAMP
   - gmt_modified：记录修改时间，自动更新为 CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP

6. **索引设计**
   - 每个表都有主键索引
   - 常用查询字段建立了普通索引
   - 唯一性字段建立了唯一索引

## 📊 数据库统计

- 总数据库数：5 个
- 总表数：26 张
- 字符集：utf8mb4
- 存储引擎：InnoDB

## 🔧 后续操作

1. 执行完 SQL 脚本后，请验证数据库连接配置
2. 检查 application-*.yml 中的数据库连接信息是否正确
3. 启动项目进行测试

## 📝 默认账号信息

后台管理模块默认账号：
- 用户名：admin
- 密码：需要在代码中使用 BCrypt 加密后比对
- 加密后的密码示例：$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJfYR5sILt8BPLu5mZFBqg5RE0S

## 📞 技术支持

如有问题，请检查：
1. MySQL 版本是否兼容（建议 8.0+）
2. 数据库用户是否有足够的权限创建数据库和表
3. 字符集设置是否正确
