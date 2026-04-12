fix(order): 修复订单模块实体类字段映射问题

1. OmsCart 实体类字段映射修复
   - 添加 @TableField("picture_url") 注解映射 mainPicture 字段
   - 解决 SQL 查询 "Unknown column 'main_picture'" 错误

2. OmsOrderItem 实体类字段映射修复
   - 添加 @TableField("sku_properties") 映射 data 字段
   - 添加 @TableField("picture_url") 映射 mainPicture 字段
   - 新增 totalPrice 字段对应数据库 total_price 列
   - 新增 spuName 字段对应数据库 spu_name 列

3. 数据库分类数据修正
   - 重新初始化 pms_category 表数据
   - 确保分类 ID 与产品表 category_id 外键一致

影响范围：
- mall-pojo/order/model/OmsCart.java
- mall-pojo/order/model/OmsOrderItem.java
- database/init-test-data.sql (分类数据)

问题修复：
- 购物车添加商品报错问题
- 订单项数据查询字段缺失问题
- 前端分类显示错误商品问题