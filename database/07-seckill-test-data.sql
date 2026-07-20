-- =====================================================
-- 电商平台系统 - 秒杀测试数据
-- 数据库名称：cs_mall_seckill
-- 说明：基于init-test-data.sql中的商品数据创建秒杀活动
-- =====================================================

USE cs_mall_seckill;

-- 清空旧数据
TRUNCATE TABLE success;
TRUNCATE TABLE seckill_sku;
TRUNCATE TABLE seckill_spu;

-- ----------------------------
-- 秒杀 SPU 数据
-- 选取热门商品参与秒杀，时间窗口设置在当天
-- ----------------------------
INSERT INTO seckill_spu (id, spu_id, list_price, start_time, end_time) VALUES
(1, 1,  6999.00, '2026-05-08 10:00:00', '2026-08-20 12:00:00'),
(2, 2,  5999.00, '2026-05-08 10:00:00', '2026-08-20 12:00:00'),
(3, 5,  3499.00, '2026-05-08 14:00:00', '2026-08-20 16:00:00'),
(4, 6,  7499.00, '2026-05-08 14:00:00', '2026-08-20 16:00:00'),
(5, 15,  799.00, '2026-05-08 20:00:00', '2026-08-20 22:00:00'),
(6, 20,  2799.00, '2026-05-08 20:00:00', '2026-08-20 22:00:00');

-- ----------------------------
-- 秒杀 SKU 数据
-- 每个 SPU 下选1-2个SKU参与秒杀
-- seckill_limit=1 表示每人限购1件
-- ----------------------------
-- SPU 1: iPhone 15 Pro (秒杀价6999, 原价7999)
INSERT INTO seckill_sku (id, sku_id, spu_id, seckill_stock, seckill_price, seckill_limit) VALUES
(1, 1, 1, 50,  6999.00, 1),
(2, 2, 1, 30,  7999.00, 1);

-- SPU 2: 华为 Mate 60 Pro (秒杀价5999, 原价6999)
INSERT INTO seckill_sku (id, sku_id, spu_id, seckill_stock, seckill_price, seckill_limit) VALUES
(3, 5, 2, 80,  5999.00, 1),
(4, 6, 2, 50,  6499.00, 1);

-- SPU 5: 小米 14 (秒杀价3499, 原价3999)
INSERT INTO seckill_sku (id, sku_id, spu_id, seckill_stock, seckill_price, seckill_limit) VALUES
(5, 11, 3, 100, 3499.00, 1),
(6, 12, 3, 80,  3799.00, 1);

-- SPU 6: iPad Pro 12.9 (秒杀价7499, 原价8999)
INSERT INTO seckill_sku (id, sku_id, spu_id, seckill_stock, seckill_price, seckill_limit) VALUES
(7, 13, 4, 40,  7499.00, 1),
(8, 14, 4, 25,  8999.00, 1);

-- SPU 15: 耐克 Air Max (秒杀价799, 原价1099)
INSERT INTO seckill_sku (id, sku_id, spu_id, seckill_stock, seckill_price, seckill_limit) VALUES
(9,  26, 5, 150, 799.00, 1),
(10, 27, 5, 120, 799.00, 1);

-- SPU 20: Redmi K70 Pro (秒杀价2799, 原价3299)
INSERT INTO seckill_sku (id, sku_id, spu_id, seckill_stock, seckill_price, seckill_limit) VALUES
(11, 35, 6, 100, 2799.00, 1),
(12, 36, 6, 80,  3199.00, 1);
