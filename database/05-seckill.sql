-- =====================================================
-- 电商平台系统 - 秒杀模块数据库初始化脚本
-- 数据库名称：cs_mall_seckill
-- 创建日期：2026-03-21
-- =====================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS cs_mall_seckill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE cs_mall_seckill;

-- ----------------------------
-- 1. 秒杀 SPU 表
-- ----------------------------
DROP TABLE IF EXISTS `seckill_spu`;
CREATE TABLE `seckill_spu` (
  `id` bigint(20) NOT NULL COMMENT '秒杀 SPU id',
  `spu_id` bigint(20) NOT NULL COMMENT '商品 SPU id',
  `list_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '秒杀价格',
  `start_time` datetime NOT NULL COMMENT '秒杀开始时间',
  `end_time` datetime NOT NULL COMMENT '秒杀结束时间',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_spu_id` (`spu_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_time_range` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀 SPU 表';

-- ----------------------------
-- 2. 秒杀 SKU 表
-- ----------------------------
DROP TABLE IF EXISTS `seckill_sku`;
CREATE TABLE `seckill_sku` (
  `id` bigint(20) NOT NULL COMMENT '秒杀 SKU id',
  `sku_id` bigint(20) NOT NULL COMMENT '商品 SKU id',
  `spu_id` bigint(20) NOT NULL COMMENT '秒杀 SPU id',
  `seckill_stock` int(11) NOT NULL DEFAULT '0' COMMENT '秒杀库存',
  `seckill_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '秒杀价',
  `seckill_limit` int(11) DEFAULT '1' COMMENT '限购数量',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_spu_id` (`spu_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀 SKU 表';

-- ----------------------------
-- 3. 秒杀成功记录表
-- ----------------------------
DROP TABLE IF EXISTS `success`;
CREATE TABLE `success` (
  `id` bigint(20) NOT NULL COMMENT '记录 id',
  `seckill_id` bigint(20) DEFAULT NULL COMMENT '关联秒杀id',
  `user_id` bigint(20) NOT NULL COMMENT '用户 id',
  `user_phone` varchar(20) DEFAULT NULL COMMENT '用户手机号',
  `sku_id` bigint(20) NOT NULL COMMENT '商品 SKU id',
  `title` varchar(255) DEFAULT NULL COMMENT '商品SKU标题(冗余)',
  `main_picture` varchar(500) DEFAULT NULL COMMENT '商品SKU图片URL',
  `seckill_price` decimal(10,2) DEFAULT NULL COMMENT '秒杀商品单价',
  `quantity` int(11) DEFAULT '1' COMMENT '秒杀商品数量',
  `bar_code` varchar(100) DEFAULT NULL COMMENT '条形码',
  `data` text DEFAULT NULL COMMENT '附加数据',
  `order_sn` varchar(64) DEFAULT NULL COMMENT '订单编号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_user` (`sku_id`,`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_sn` (`order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀成功记录表';
