-- =====================================================
-- 电商平台系统 - 基础测试数据初始化脚本
-- 说明：添加基础分类、品牌、属性模板等测试数据
-- 创建日期：2026-03-21
-- =====================================================

USE cs_mall_pms;

-- ----------------------------
-- 1. 商品分类数据
-- ----------------------------
INSERT INTO `pms_category` (`id`, `name`, `parent_id`, `level`, `sort`, `is_show`) VALUES 
(1, '手机数码', 0, 1, 1, 1),
(2, '电脑办公', 0, 1, 2, 1),
(3, '服装鞋帽', 0, 1, 3, 1),
(4, '手机', 1, 2, 1, 1),
(5, '平板电脑', 1, 2, 2, 1),
(6, '笔记本', 2, 2, 1, 1),
(7, '台式机', 2, 2, 2, 1),
(8, '男装', 3, 2, 1, 1),
(9, '女装', 3, 2, 2, 1);

-- ----------------------------
-- 2. 品牌数据
-- ----------------------------
INSERT INTO `pms_brand` (`id`, `name`, `sort`, `is_show`) VALUES 
(1, '苹果', 1, 1),
(2, '华为', 2, 1),
(3, '小米', 3, 1),
(4, '联想', 4, 1),
(5, '戴尔', 5, 1),
(6, '耐克', 6, 1),
(7, '阿迪达斯', 7, 1);

-- ----------------------------
-- 3. 品牌与分类关联
-- ----------------------------
INSERT INTO `pms_brand_category` (`id`, `brand_id`, `category_id`) VALUES 
(1, 1, 4), (2, 1, 5), (3, 1, 6),
(4, 2, 4), (5, 2, 5),
(6, 3, 4), (7, 3, 6),
(8, 4, 6), (9, 4, 7),
(10, 5, 6), (11, 5, 7),
(12, 6, 8), (13, 6, 9),
(14, 7, 8), (15, 7, 9);

-- ----------------------------
-- 4. 属性模板数据
-- ----------------------------
INSERT INTO `pms_attribute_template` (`id`, `name`, `type`) VALUES
(1, '手机参数', 2),
(2, '笔记本参数', 2),
(3, '服装尺码', 1);

-- ----------------------------
-- 5. 属性模板值 - 手机参数
-- ----------------------------
INSERT INTO `pms_attribute_template_value` (`id`, `attribute_template_id`, `value`, `sort`) VALUES 
(1, 1, '6GB', 1),
(2, 1, '8GB', 2),
(3, 1, '12GB', 3),
(4, 1, '128GB', 4),
(5, 1, '256GB', 5),
(6, 1, '512GB', 6);

-- ----------------------------
-- 6. 属性模板值 - 笔记本参数
-- ----------------------------
INSERT INTO `pms_attribute_template_value` (`id`, `attribute_template_id`, `value`, `sort`) VALUES 
(7, 2, 'Intel i5', 1),
(8, 2, 'Intel i7', 2),
(9, 2, 'Intel i9', 3),
(10, 2, '16GB', 4),
(11, 2, '32GB', 5),
(12, 2, '512GB SSD', 6),
(13, 2, '1TB SSD', 7);

-- ----------------------------
-- 7. 属性模板值 - 服装尺码
-- ----------------------------
INSERT INTO `pms_attribute_template_value` (`id`, `attribute_template_id`, `value`, `sort`) VALUES 
(14, 3, 'S', 1),
(15, 3, 'M', 2),
(16, 3, 'L', 3),
(17, 3, 'XL', 4),
(18, 3, 'XXL', 5);

-- ----------------------------
-- 8. SPU 数据 - 手机
-- ----------------------------
INSERT INTO `pms_spu` (`id`, `name`, `type_number`, `title`, `description`, `list_price`, `stock`, `unit`, `brand_id`, `brand_name`, `category_id`, `category_name`, `attribute_template_id`, `is_published`, `is_new_arrival`, `is_recommend`) VALUES 
(1, 'iPhone 15 Pro', 'APL-IP15P-001', 'Apple iPhone 15 Pro 5G 手机', 'A17 Pro 芯片，钛金属设计', 7999.00, 100, '台', 1, '苹果', 4, '手机', 1, 1, 1, 1),
(2, '华为 Mate 60 Pro', 'HW-M60P-001', 'HUAWEI Mate 60 Pro 5G 手机', '麒麟 9000S 芯片，卫星通话', 6999.00, 150, '台', 2, '华为', 4, '手机', 1, 1, 1, 1),
(3, '小米 14 Pro', 'MI-14P-001', 'Xiaomi 14 Pro 5G 手机', '骁龙 8 Gen3，徕卡影像', 4999.00, 200, '台', 3, '小米', 4, '手机', 1, 1, 0, 1);

-- ----------------------------
-- 9. SPU 数据 - 笔记本
-- ----------------------------
INSERT INTO `pms_spu` (`id`, `name`, `type_number`, `title`, `description`, `list_price`, `stock`, `unit`, `brand_id`, `brand_name`, `category_id`, `category_name`, `attribute_template_id`, `is_published`, `is_new_arrival`, `is_recommend`) VALUES 
(4, 'MacBook Pro 14', 'APL-MBP14-001', 'Apple MacBook Pro 14 英寸笔记本电脑', 'M3 Pro 芯片，18GB 内存', 12999.00, 50, '台', 1, '苹果', 6, '笔记本', 2, 1, 1, 1),
(5, 'ThinkPad X1 Carbon', 'LEN-X1C-001', 'Lenovo ThinkPad X1 Carbon 轻薄本', 'Intel Evo 认证，14 英寸 2.8K', 9999.00, 80, '台', 4, '联想', 6, '笔记本', 2, 1, 0, 1);

-- ----------------------------
-- 10. SKU 数据 - iPhone 15 Pro
-- ----------------------------
INSERT INTO `pms_sku` (`id`, `spu_id`, `title`, `bar_code`, `price`, `stock`) VALUES 
(1, 1, 'iPhone 15 Pro 6GB+128GB 深空黑色', 'SKU-1-001', 7999.00, 30),
(2, 1, 'iPhone 15 Pro 6GB+256GB 深空黑色', 'SKU-1-002', 8999.00, 25),
(3, 1, 'iPhone 15 Pro 8GB+256GB 原色钛金属', 'SKU-1-003', 9999.00, 20),
(4, 1, 'iPhone 15 Pro 8GB+512GB 原色钛金属', 'SKU-1-004', 11999.00, 15);

-- ----------------------------
-- 11. SKU 数据 - 华为 Mate 60 Pro
-- ----------------------------
INSERT INTO `pms_sku` (`id`, `spu_id`, `title`, `bar_code`, `price`, `stock`) VALUES 
(5, 2, '华为 Mate 60 Pro 12GB+256GB 雅川青', 'SKU-2-001', 6999.00, 50),
(6, 2, '华为 Mate 60 Pro 12GB+512GB 白沙银', 'SKU-2-002', 7499.00, 40),
(7, 2, '华为 Mate 60 Pro 12GB+1TB 南糯紫', 'SKU-2-003', 7999.00, 30);

-- ----------------------------
-- 12. SKU 数据 - 小米 14 Pro
-- ----------------------------
INSERT INTO `pms_sku` (`id`, `spu_id`, `title`, `bar_code`, `price`, `stock`) VALUES 
(8, 3, '小米 14 Pro 8GB+256GB 岩石青', 'SKU-3-001', 4999.00, 60),
(9, 3, '小米 14 Pro 12GB+256GB 雪山粉', 'SKU-3-002', 5299.00, 50),
(10, 3, '小米 14 Pro 16GB+512GB 钛金属特别版', 'SKU-3-003', 5999.00, 40);

-- ----------------------------
-- 13. SKU 数据 - MacBook Pro 14
-- ----------------------------
INSERT INTO `pms_sku` (`id`, `spu_id`, `title`, `bar_code`, `price`, `stock`) VALUES 
(11, 4, 'MacBook Pro 14 M3 Pro 18GB+512GB 深空灰色', 'SKU-4-001', 12999.00, 20),
(12, 4, 'MacBook Pro 14 M3 Pro 18GB+1TB 银色', 'SKU-4-002', 14999.00, 15);

-- ----------------------------
-- 14. SKU 数据 - ThinkPad X1 Carbon
-- ----------------------------
INSERT INTO `pms_sku` (`id`, `spu_id`, `title`, `bar_code`, `price`, `stock`) VALUES 
(13, 5, 'ThinkPad X1 Carbon i5 16GB+512GB', 'SKU-5-001', 9999.00, 30),
(14, 5, 'ThinkPad X1 Carbon i7 32GB+1TB', 'SKU-5-002', 12999.00, 25);

-- ----------------------------
-- 15. SPU 详情数据 (简化版)
-- ----------------------------
INSERT INTO `pms_spu_detail` (`id`, `spu_id`, `content`) VALUES 
(1, 1, '<div>iPhone 15 Pro 详情内容</div>'),
(2, 2, '<div>华为 Mate 60 Pro 详情内容</div>'),
(3, 3, '<div>小米 14 Pro 详情内容</div>'),
(4, 4, '<div>MacBook Pro 14 详情内容</div>'),
(5, 5, '<div>ThinkPad X1 Carbon 详情内容</div>');


-- =====================================================
-- 后台管理模块测试数据
-- =====================================================
USE cs_mall_ams;

-- ----------------------------
-- 16. 角色数据
-- ----------------------------
INSERT INTO `ams_role` (`id`, `name`, `code`, `sort`) VALUES 
(1, '超级管理员', 'SUPER_ADMIN', 1),
(2, '运营管理员', 'OPERATION_ADMIN', 2),
(3, '客服管理员', 'SERVICE_ADMIN', 3);

-- ----------------------------
-- 17. 权限数据
-- ----------------------------
INSERT INTO `ams_permission` (`id`, `name`, `code`, `type`, `parent_id`, `sort`) VALUES 
(1, '商品管理', 'pms:manage', 1, 0, 1),
(2, '商品列表', 'pms:product:list', 2, 1, 1),
(3, '商品新增', 'pms:product:create', 2, 1, 2),
(4, '商品编辑', 'pms:product:edit', 2, 1, 3),
(5, '商品删除', 'pms:product:delete', 2, 1, 4),
(6, '订单管理', 'oms:manage', 1, 0, 2),
(7, '订单列表', 'oms:order:list', 2, 6, 1),
(8, '订单详情', 'oms:order:detail', 2, 6, 2),
(9, '用户管理', 'ums:manage', 1, 0, 3),
(10, '用户列表', 'ums:user:list', 2, 9, 1);

-- ----------------------------
-- 18. 管理员角色关联 (为 admin 分配超级管理员角色)
-- ----------------------------
INSERT INTO `ams_admin_role` (`id`, `admin_id`, `role_id`) VALUES 
(1, 1, 1);

-- ----------------------------
-- 19. 角色权限关联 (为超级管理员分配所有权限)
-- ----------------------------
INSERT INTO `ams_role_permission` (`id`, `role_id`, `permission_id`) VALUES 
(1, 1, 1), (2, 1, 2), (3, 1, 3), (4, 1, 4), (5, 1, 5),
(6, 1, 6), (7, 1, 7), (8, 1, 8),
(9, 1, 9), (10, 1, 10);


-- =====================================================
-- 用户模块测试数据
-- =====================================================
USE cs_mall_ums;

-- ----------------------------
-- 20. 测试用户数据
-- ----------------------------
INSERT INTO `ums_user` (`id`, `username`, `password`, `nickname`, `phone`, `email`, `enable`) VALUES 
(1, 'testuser1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJfYR5sILt8BPLu5mZFBqg5RE0S', '测试用户 1', '13800138001', 'test1@example.com', 1),
(2, 'testuser2', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJfYR5sILt8BPLu5mZFBqg5RE0S', '测试用户 2', '13800138002', 'test2@example.com', 1),
(3, 'zhangsan', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJfYR5sILt8BPLu5mZFBqg5RE0S', '张三', '13800138003', 'zhangsan@example.com', 1);


-- =====================================================
-- 完成提示
-- =====================================================
SELECT '✅ 基础测试数据初始化完成!' AS message;
SELECT '已添加:' AS info;
SELECT '  - 9 个商品分类' AS info;
SELECT '  - 7 个品牌' AS info;
SELECT '  - 3 个属性模板' AS info;
SELECT '  - 5 个 SPU' AS info;
SELECT '  - 14 个 SKU' AS info;
SELECT '  - 3 个角色' AS info;
SELECT '  - 10 个权限' AS info;
SELECT '  - 3 个测试用户' AS info;




-- =====================================================
-- 管理员账号测试数据
-- 密码说明: BCrypt 加密后的密码
--   - admin/123456  (超级管理员)
--   - liucs/123456    (运营管理员)
--   - wangkj/123456   (客服管理员)
-- =====================================================

USE cs_mall_ams;

-- 插入管理员账号 (密码均为 BCrypt 加密)
INSERT INTO `ams_admin` (`id`, `username`, `password`, `nickname`, `phone`, `email`, `enable`) VALUES
                                                                                                   (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJfYR5sILt8BPLu5mZFBqg5RE0S', '系统管理员', '13800000000', 'admin@csmall.com', 1),
                                                                                                   (2, 'liucs', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJfYR5sILt8BPLu5mZFBqg5RE0S', '刘苍松', '13800000001', 'liucs@csmall.com', 1),
                                                                                                   (3, 'wangkj', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJfYR5sILt8BPLu5mZFBqg5RE0S', '王可静', '13800000002', 'wangkj@csmall.com', 1);

-- 重新插入管理员角色关联 (之前插入的外键会报错，因为admin不存在)
-- 先清空再插入
DELETE FROM `ams_admin_role`;
INSERT INTO `ams_admin_role` (`id`, `admin_id`, `role_id`) VALUES
                                                               (1, 1, 1),  -- admin -> 超级管理员
                                                               (2, 2, 2),  -- liucs -> 运营管理员
                                                               (3, 3, 3);  -- wangkj -> 客服管理员

-- 重新插入角色权限关联 (确保超级管理员有所有权限)
DELETE FROM `ams_role_permission`;
INSERT INTO `ams_role_permission` (`id`, `role_id`, `permission_id`) VALUES
-- 超级管理员拥有所有权限
(1, 1, 1), (2, 1, 2), (3, 1, 3), (4, 1, 4), (5, 1, 5),
(6, 1, 6), (7, 1, 7), (8, 1, 8),
(9, 1, 9), (10, 1, 10),
-- 运营管理员权限
(11, 2, 1), (12, 2, 2), (13, 2, 3), (14, 2, 4), (15, 2, 5),
(16, 2, 6), (17, 2, 7), (18, 2, 8),
-- 客服管理员权限
(19, 3, 6), (20, 3, 7), (21, 3, 8),
(22, 3, 9), (23, 3, 10);

SELECT '✅ 管理员数据初始化完成!' AS message;