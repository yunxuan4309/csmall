-- =====================================================
-- 电商平台系统 - 基础测试数据初始化脚本
-- 说明：添加基础分类、品牌、属性模板等测试数据
-- 创建日期：2026-03-21
-- =====================================================

USE cs_mall_pms;

-- ----------------------------
-- 1. 商品分类数据
-- ----------------------------
INSERT INTO `pms_category` (`id`, `name`, `parent_id`, `depth`, `sort`, `enable`, `is_parent`, `is_display`) VALUES 
(1, '手机数码', 0, 1, 1, 1, 1, 1),
(2, '电脑办公', 0, 1, 2, 1, 1, 1),
(3, '服装鞋帽', 0, 1, 3, 1, 1, 1),
(4, '手机', 1, 2, 1, 1, 0, 1),
(5, '平板电脑', 1, 2, 2, 1, 0, 1),
(6, '笔记本', 2, 2, 1, 1, 0, 1),
(7, '台式机', 2, 2, 2, 1, 0, 1),
(8, '男装', 3, 2, 1, 1, 0, 1),
(9, '女装', 3, 2, 2, 1, 0, 1);

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
-- 8. SPU 数据 - 手机(分类4)
-- ----------------------------
INSERT INTO `pms_spu` (`id`, `name`, `type_number`, `title`, `description`, `list_price`, `stock`, `unit`, `brand_id`, `brand_name`, `category_id`, `category_name`, `attribute_template_id`, `is_published`, `is_new_arrival`, `is_recommend`, `is_checked`, `is_deleted`) VALUES
(1,  'iPhone 15 Pro',        'APL-IP15P-001',  'Apple iPhone 15 Pro 5G 手机',              'A17 Pro 芯片，钛金属设计',               7999.00, 100, '台', 1, '苹果',   4, '手机',     1, 1, 1, 1, 1, 0),
(2,  '华为 Mate 60 Pro',     'HW-M60P-001',    'HUAWEI Mate 60 Pro 5G 手机',              '麒麟 9000S 芯片，卫星通话',              6999.00, 150, '台', 2, '华为',   4, '手机',     1, 1, 1, 1, 1, 0),
(3,  '小米 14 Pro',          'MI-14P-001',     'Xiaomi 14 Pro 5G 手机',                   '骁龙 8 Gen3，徕卡影像',                  4999.00, 200, '台', 3, '小米',   4, '手机',     1, 1, 0, 1, 1, 0),
(4,  '华为 P60 Pro',         'HW-P60P-001',    'HUAWEI P60 Pro 旗舰影像手机',             '超聚光XMAGE影像系统',                    5988.00, 120, '台', 2, '华为',   4, '手机',     1, 1, 1, 0, 1, 0),
(5,  '小米 14',              'MI-14-001',      'Xiaomi 14 轻薄旗舰手机',                  '骁龙 8 Gen3，小尺寸旗舰',                3999.00, 300, '台', 3, '小米',   4, '手机',     1, 1, 0, 0, 1, 0);

-- ----------------------------
-- 9. SPU 数据 - 平板电脑(分类5)
-- ----------------------------
INSERT INTO `pms_spu` (`id`, `name`, `type_number`, `title`, `description`, `list_price`, `stock`, `unit`, `brand_id`, `brand_name`, `category_id`, `category_name`, `attribute_template_id`, `is_published`, `is_new_arrival`, `is_recommend`, `is_checked`, `is_deleted`) VALUES
(6,  'iPad Pro 12.9',        'APL-IPDP-001',   'Apple iPad Pro 12.9 英寸平板',            'M2 芯片，Liquid Retina XDR',             8999.00, 60,  '台', 1, '苹果',   5, '平板电脑', 1, 1, 1, 1, 1, 0),
(7,  '华为 MatePad Pro',     'HW-MPP-001',     'HUAWEI MatePad Pro 13.2 英寸',            '星闪连接，PC级办公体验',                 4699.00, 80,  '台', 2, '华为',   5, '平板电脑', 1, 1, 1, 0, 1, 0);

-- ----------------------------
-- 10. SPU 数据 - 笔记本(分类6)
-- ----------------------------
INSERT INTO `pms_spu` (`id`, `name`, `type_number`, `title`, `description`, `list_price`, `stock`, `unit`, `brand_id`, `brand_name`, `category_id`, `category_name`, `attribute_template_id`, `is_published`, `is_new_arrival`, `is_recommend`, `is_checked`, `is_deleted`) VALUES
(8,  'MacBook Pro 14',       'APL-MBP14-001',  'Apple MacBook Pro 14 英寸笔记本',         'M3 Pro 芯片，18GB 内存',                 12999.00, 50, '台', 1, '苹果',   6, '笔记本',   2, 1, 1, 1, 1, 0),
(9,  'ThinkPad X1 Carbon',   'LEN-X1C-001',    'Lenovo ThinkPad X1 Carbon 轻薄本',        'Intel Evo 认证，14 英寸 2.8K',           9999.00, 80,  '台', 4, '联想',   6, '笔记本',   2, 1, 0, 1, 1, 0),
(10, '小米 RedmiBook Pro',   'MI-RBP-001',     'RedmiBook Pro 15 2024 锐龙版',             'AMD R7 7840HS，3.2K 120Hz',              4999.00, 100, '台', 3, '小米',   6, '笔记本',   2, 1, 0, 0, 1, 0),
(11, '戴尔 XPS 13',         'DL-XPS13-001',   'Dell XPS 13 Plus 超轻薄笔记本',           'Intel i7-1360P，OLED 屏',                8999.00, 40,  '台', 5, '戴尔',   6, '笔记本',   2, 1, 1, 1, 1, 0),
(12, 'MacBook Air 15',      'APL-MA15-001',   'Apple MacBook Air 15 英寸轻薄本',         'M3 芯片，长达18小时续航',                 9999.00, 70,  '台', 1, '苹果',   6, '笔记本',   2, 1, 1, 0, 1, 0);

-- ----------------------------
-- 11. SPU 数据 - 台式机(分类7)
-- ----------------------------
INSERT INTO `pms_spu` (`id`, `name`, `type_number`, `title`, `description`, `list_price`, `stock`, `unit`, `brand_id`, `brand_name`, `category_id`, `category_name`, `attribute_template_id`, `is_published`, `is_new_arrival`, `is_recommend`, `is_checked`, `is_deleted`) VALUES
(13, '联想 天逸510S',       'LEN-TY510-001',  'Lenovo 天逸510S 家用台式机',              'Intel i5-13400，16GB 512GB',             3999.00, 60,  '台', 4, '联想',   7, '台式机',   2, 1, 0, 0, 1, 0),
(14, '戴尔 OptiPlex',       'DL-OPT-001',     'Dell OptiPlex 7010 商务台式机',            'Intel i7-13700，稳定办公',               5999.00, 30,  '台', 5, '戴尔',   7, '台式机',   2, 1, 0, 0, 1, 0);

-- ----------------------------
-- 12. SPU 数据 - 男装(分类8)
-- ----------------------------
INSERT INTO `pms_spu` (`id`, `name`, `type_number`, `title`, `description`, `list_price`, `stock`, `unit`, `brand_id`, `brand_name`, `category_id`, `category_name`, `attribute_template_id`, `is_published`, `is_new_arrival`, `is_recommend`, `is_checked`, `is_deleted`) VALUES
(15, '耐克 Air Max 运动鞋',  'NK-AM-001',      'Nike Air Max 270 男子运动鞋',              '大容量气垫缓震，透气鞋面',               1099.00, 200, '双', 6, '耐克',    8, '男装',     3, 1, 0, 1, 1, 0),
(16, '阿迪达斯 三叶草卫衣',  'AD-CW-001',      'adidas Originals 经典三叶草卫衣',         '法式毛圈面料，宽松版型',                 599.00,  150, '件', 7, '阿迪达斯',8, '男装',     3, 1, 0, 0, 1, 0);

-- ----------------------------
-- 13. SPU 数据 - 女装(分类9)
-- ----------------------------
INSERT INTO `pms_spu` (`id`, `name`, `type_number`, `title`, `description`, `list_price`, `stock`, `unit`, `brand_id`, `brand_name`, `category_id`, `category_name`, `attribute_template_id`, `is_published`, `is_new_arrival`, `is_recommend`, `is_checked`, `is_deleted`) VALUES
(17, '耐克 Dunk 运动鞋',    'NK-DK-001',      'Nike Dunk Low 女子休闲运动鞋',            '复古篮球鞋设计，百搭潮流',               899.00,  180, '双', 6, '耐克',    9, '女装',     3, 1, 1, 1, 1, 0),
(18, '阿迪达斯 超跑裤',     'AD-SP-001',      'adidas Aeroready 女子训练长裤',            '吸湿速干面料，运动舒适',                 399.00,  160, '件', 7, '阿迪达斯',9, '女装',     3, 1, 0, 0, 1, 0);

-- ----------------------------
-- 14. SPU 数据 - 更多手机
-- ----------------------------
INSERT INTO `pms_spu` (`id`, `name`, `type_number`, `title`, `description`, `list_price`, `stock`, `unit`, `brand_id`, `brand_name`, `category_id`, `category_name`, `attribute_template_id`, `is_published`, `is_new_arrival`, `is_recommend`, `is_checked`, `is_deleted`) VALUES
(19, 'iPhone 15',           'APL-IP15-001',   'Apple iPhone 15 5G 手机',                 'A16 仿生芯片，灵动岛',                   5999.00, 180, '台', 1, '苹果',   4, '手机',     1, 1, 0, 1, 1, 0),
(20, 'Redmi K70 Pro',       'MI-K70P-001',    'Redmi K70 Pro 旗舰手机',                   '骁龙 8 Gen3，2K 中国屏',                 3299.00, 250, '台', 3, '小米',   4, '手机',     1, 1, 1, 0, 1, 0);

-- ----------------------------
-- 15. SKU 数据 - iPhone 15 Pro
-- ----------------------------
INSERT INTO `pms_sku` (`id`, `spu_id`, `title`, `bar_code`, `price`, `stock`) VALUES
(1,  1, 'iPhone 15 Pro 128GB 深空黑色',    'SKU-1-001', 7999.00, 30),
(2,  1, 'iPhone 15 Pro 256GB 原色钛金属',  'SKU-1-002', 8999.00, 25),
(3,  1, 'iPhone 15 Pro 512GB 原色钛金属',  'SKU-1-003', 9999.00, 20),
(4,  1, 'iPhone 15 Pro 1TB 原色钛金属',    'SKU-1-004', 11999.00, 15),
-- 华为 Mate 60 Pro
(5,  2, '华为 Mate 60 Pro 256GB 雅川青',   'SKU-2-001', 6999.00, 50),
(6,  2, '华为 Mate 60 Pro 512GB 白沙银',   'SKU-2-002', 7499.00, 40),
-- 小米 14 Pro
(7,  3, '小米 14 Pro 256GB 岩石青',        'SKU-3-001', 4999.00, 60),
(8,  3, '小米 14 Pro 512GB 钛金属特别版',  'SKU-3-002', 5999.00, 40),
-- 华为 P60 Pro
(9,  4, '华为 P60 Pro 256GB 翡羽绿',       'SKU-4-001', 5988.00, 40),
(10, 4, '华为 P60 Pro 512GB 蓝天蓝',       'SKU-4-002', 6488.00, 30),
-- 小米 14
(11, 5, '小米 14 256GB 白色',              'SKU-5-001', 3999.00, 100),
(12, 5, '小米 14 512GB 黑色',              'SKU-5-002', 4299.00, 80),
-- iPad Pro
(13, 6, 'iPad Pro 12.9 256GB WiFi 深空灰', 'SKU-6-001', 8999.00, 30),
(14, 6, 'iPad Pro 12.9 512GB WiFi 银色',   'SKU-6-002', 10499.00, 20),
-- MatePad Pro
(15, 7, 'MatePad Pro 12+256GB 曜金黑',     'SKU-7-001', 4699.00, 40),
-- MacBook Pro 14
(16, 8, 'MacBook Pro 14 M3 Pro 18+512GB 深空灰', 'SKU-8-001', 12999.00, 20),
(17, 8, 'MacBook Pro 14 M3 Pro 18+1TB 银色',     'SKU-8-002', 14999.00, 15),
-- ThinkPad X1
(18, 9, 'ThinkPad X1 Carbon i5 16+512GB',  'SKU-9-001', 9999.00, 30),
(19, 9, 'ThinkPad X1 Carbon i7 32+1TB',    'SKU-9-002', 12999.00, 25),
-- RedmiBook
(20, 10, 'RedmiBook Pro R7 16+512GB 银色', 'SKU-10-001', 4999.00, 50),
-- XPS 13
(21, 11, 'XPS 13 i7 16+512GB 铂金银',      'SKU-11-001', 8999.00, 20),
-- MacBook Air
(22, 12, 'MacBook Air 15 M3 8+256GB 午夜色','SKU-12-001', 9999.00, 35),
(23, 12, 'MacBook Air 15 M3 16+512GB 星光色','SKU-12-002', 11499.00, 25),
-- 天逸510S
(24, 13, '天逸510S i5 16+512GB',            'SKU-13-001', 3999.00, 30),
-- OptiPlex
(25, 14, 'OptiPlex 7010 i7 16+512GB',      'SKU-14-001', 5999.00, 15),
-- 耐克 Air Max
(26, 15, 'Air Max 270 黑白 42码',           'SKU-15-001', 1099.00, 60),
(27, 15, 'Air Max 270 黑白 43码',           'SKU-15-002', 1099.00, 50),
-- 阿迪卫衣
(28, 16, '三叶草卫衣 黑色 M',              'SKU-16-001', 599.00, 40),
(29, 16, '三叶草卫衣 黑色 L',              'SKU-16-002', 599.00, 35),
-- 耐克 Dunk
(30, 17, 'Dunk Low 粉白 37码',             'SKU-17-001', 899.00, 50),
(31, 17, 'Dunk Low 粉白 38码',             'SKU-17-002', 899.00, 45),
-- 阿迪裤子
(32, 18, 'Aeroready 长裤 黑色 M',          'SKU-18-001', 399.00, 40),
-- iPhone 15
(33, 19, 'iPhone 15 128GB 粉色',           'SKU-19-001', 5999.00, 60),
(34, 19, 'iPhone 15 256GB 蓝色',           'SKU-19-002', 6999.00, 50),
-- Redmi K70 Pro
(35, 20, 'Redmi K70 Pro 256GB 墨羽',       'SKU-20-001', 3299.00, 80),
(36, 20, 'Redmi K70 Pro 512GB 晴雪',       'SKU-20-002', 3699.00, 60);

-- ----------------------------
-- 16. SPU 详情数据
-- ----------------------------
INSERT INTO `pms_spu_detail` (`id`, `spu_id`, `content`) VALUES
(1,  1,  '<div><h2>iPhone 15 Pro</h2><p>A17 Pro 芯片，钛金属设计，4800万像素主摄</p></div>'),
(2,  2,  '<div><h2>华为 Mate 60 Pro</h2><p>麒麟 9000S 芯片，卫星通话，超感知影像</p></div>'),
(3,  3,  '<div><h2>小米 14 Pro</h2><p>骁龙 8 Gen3，徕卡专业光学镜头，2K 屏幕</p></div>'),
(4,  4,  '<div><h2>华为 P60 Pro</h2><p>超聚光XMAGE影像，昆仑玻璃，双向北斗卫星消息</p></div>'),
(5,  5,  '<div><h2>小米 14</h2><p>骁龙 8 Gen3，小尺寸旗舰，1.5K 屏幕</p></div>'),
(6,  6,  '<div><h2>iPad Pro 12.9</h2><p>M2 芯片，Liquid Retina XDR 显示屏</p></div>'),
(7,  7,  '<div><h2>MatePad Pro 13.2</h2><p>星闪连接，PC级办公，13.2英寸OLED</p></div>'),
(8,  8,  '<div><h2>MacBook Pro 14</h2><p>M3 Pro 芯片，18小时续航，Liquid Retina XDR</p></div>'),
(9,  9,  '<div><h2>ThinkPad X1 Carbon</h2><p>Intel Evo 认证，14英寸2.8K OLED，轻至1.12kg</p></div>'),
(10, 10, '<div><h2>RedmiBook Pro 15</h2><p>AMD R7 7840HS，3.2K 120Hz 屏幕</p></div>'),
(11, 11, '<div><h2>Dell XPS 13 Plus</h2><p>Intel i7，OLED 触控屏，极致轻薄</p></div>'),
(12, 12, '<div><h2>MacBook Air 15</h2><p>M3 芯片，18小时续航，15.3英寸大屏</p></div>'),
(13, 13, '<div><h2>天逸510S</h2><p>Intel i5-13400，16GB内存，512GB SSD</p></div>'),
(14, 14, '<div><h2>OptiPlex 7010</h2><p>Intel i7-13700，商务稳定之选</p></div>'),
(15, 15, '<div><h2>Air Max 270</h2><p>大容量Air气垫缓震，透气网面鞋面</p></div>'),
(16, 16, '<div><h2>三叶草经典卫衣</h2><p>法式毛圈面料，经典三叶草Logo</p></div>'),
(17, 17, '<div><h2>Dunk Low</h2><p>复古篮球鞋设计，日常百搭潮流</p></div>'),
(18, 18, '<div><h2>Aeroready 训练长裤</h2><p>吸湿速干面料，运动自由舒适</p></div>'),
(19, 19, '<div><h2>iPhone 15</h2><p>A16 仿生芯片，灵动岛设计，4800万像素</p></div>'),
(20, 20, '<div><h2>Redmi K70 Pro</h2><p>骁龙 8 Gen3，2K中国屏，5000mAh</p></div>');


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
(1, 'testuser1', '$2a$10$LKBk.ZoWkmKyyExV39Yz7.EGAzMdX/aXbA0lvPpIAHgx9RsW3xZOm', '测试用户 1', '13800138001', 'test1@example.com', 1),
(2, 'testuser2', '$2a$10$LKBk.ZoWkmKyyExV39Yz7.EGAzMdX/aXbA0lvPpIAHgx9RsW3xZOm', '测试用户 2', '13800138002', 'test2@example.com', 1),
(3, 'zhangsan', '$2a$10$LKBk.ZoWkmKyyExV39Yz7.EGAzMdX/aXbA0lvPpIAHgx9RsW3xZOm', '张三', '13800138003', 'zhangsan@example.com', 1);


-- =====================================================
-- 完成提示
-- =====================================================
SELECT '✅ 基础测试数据初始化完成!' AS message;
SELECT '已添加:' AS info;
SELECT '  - 9 个商品分类' AS info;
SELECT '  - 7 个品牌' AS info;
SELECT '  - 3 个属性模板' AS info;
SELECT '  - 20 个 SPU' AS info;
SELECT '  - 36 个 SKU' AS info;
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