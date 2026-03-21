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
  `name` varchar(128) NOT NULL COMMENT 'SPU 名称',
  `title` varchar(255) NOT NULL COMMENT '秒杀标题',
  `original_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '原价',
  `seckill_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '秒杀价',
  `stock` int(11) NOT NULL DEFAULT '0' COMMENT '秒杀库存',
  `start_time` datetime NOT NULL COMMENT '秒杀开始时间',
  `end_time` datetime NOT NULL COMMENT '秒杀结束时间',
  `status` int(1) DEFAULT '0' COMMENT '状态，0=未开始，1=进行中，2=已结束',
  `sort` int(11) DEFAULT '0' COMMENT '排序序号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀 SPU 表';

-- ----------------------------
-- 2. 秒杀 SKU 表
-- ----------------------------
DROP TABLE IF EXISTS `seckill_sku`;
CREATE TABLE `seckill_sku` (
  `id` bigint(20) NOT NULL COMMENT '秒杀 SKU id',
  `spu_id` bigint(20) NOT NULL COMMENT '秒杀 SPU id',
  `sku_id` bigint(20) NOT NULL COMMENT '商品 SKU id',
  `title` varchar(255) NOT NULL COMMENT '商品标题',
  `picture_url` varchar(500) DEFAULT NULL COMMENT '商品图片 URL',
  `original_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '原价',
  `seckill_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '秒杀价',
  `stock` int(11) NOT NULL DEFAULT '0' COMMENT '秒杀库存',
  `sold_count` int(11) DEFAULT '0' COMMENT '已售数量',
  `limit_count` int(11) DEFAULT '1' COMMENT '限购数量',
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
  `seckill_sku_id` bigint(20) NOT NULL COMMENT '秒杀 SKU id',
  `user_id` bigint(20) NOT NULL COMMENT '用户 id',
  `phone` varchar(20) NOT NULL COMMENT '用户手机号',
  `status` int(1) DEFAULT '0' COMMENT '状态，0=待支付，1=已支付，2=已取消',
  `order_id` bigint(20) DEFAULT NULL COMMENT '订单 id',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_user` (`seckill_sku_id`,`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀成功记录表';
