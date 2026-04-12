-- =====================================================
-- 电商平台系统 - 订单模块 (OMS) 数据库初始化脚本
-- 数据库名称：cs_mall_oms
-- 创建日期：2026-03-21
-- =====================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS cs_mall_oms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE cs_mall_oms;

-- ----------------------------
-- 1. 订单表
-- ----------------------------
DROP TABLE IF EXISTS `oms_order`;
CREATE TABLE `oms_order` (
  `id` bigint(20) NOT NULL COMMENT '订单 id',
  `sn` varchar(64) NOT NULL COMMENT '订单编号',
  `user_id` bigint(20) NOT NULL COMMENT '用户 id',
  `contact_name` varchar(64) DEFAULT NULL COMMENT '联系人姓名（冗余，历史）',
  `mobile_phone` varchar(20) DEFAULT NULL COMMENT '联系电话（冗余，历史）',
  `telephone` varchar(20) DEFAULT NULL COMMENT '固定电话（冗余，历史）',
  `province_code` varchar(32) DEFAULT NULL COMMENT '省 - 代号（冗余，历史）',
  `province_name` varchar(64) DEFAULT NULL COMMENT '省 - 名称（冗余，历史）',
  `city_code` varchar(32) DEFAULT NULL COMMENT '市 - 代号（冗余，历史）',
  `city_name` varchar(64) DEFAULT NULL COMMENT '市 - 名称（冗余，历史）',
  `district_code` varchar(32) DEFAULT NULL COMMENT '区 - 代号（冗余，历史）',
  `district_name` varchar(64) DEFAULT NULL COMMENT '区 - 名称（冗余，历史）',
  `street_code` varchar(32) DEFAULT NULL COMMENT '街道 - 代号（冗余，历史）',
  `street_name` varchar(128) DEFAULT NULL COMMENT '街道 - 名称（冗余，历史）',
  `detailed_address` varchar(500) DEFAULT NULL COMMENT '详细地址（冗余，历史）',
  `tag` varchar(32) DEFAULT NULL COMMENT '标签（冗余，历史），例如：家、公司、学校',
  `payment_type` int(1) DEFAULT '0' COMMENT '支付方式，0=银联，1=微信，2=支付宝',
  `state` int(1) DEFAULT '0' COMMENT '状态，0=未支付，1=已关闭（超时未支付），2=已取消，3=已支付，4=已签收，5=已拒收，6=退款处理中，7=已退款',
  `reward_point` int(11) DEFAULT '0' COMMENT '积分',
  `amount_of_original_price` decimal(10,2) DEFAULT '0.00' COMMENT '商品原总价',
  `amount_of_freight` decimal(10,2) DEFAULT '0.00' COMMENT '运费总价',
  `amount_of_discount` decimal(10,2) DEFAULT '0.00' COMMENT '优惠金额',
  `amount_of_actual_pay` decimal(10,2) DEFAULT '0.00' COMMENT '实际支付',
  `gmt_order` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `gmt_pay` datetime DEFAULT NULL COMMENT '支付时间',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据最后修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sn` (`sn`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_gmt_order` (`gmt_order`),
  KEY `idx_state` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单数据表';

-- ----------------------------
-- 2. 订单项表
-- ----------------------------
DROP TABLE IF EXISTS `oms_order_item`;
CREATE TABLE `oms_order_item` (
  `id` bigint(20) NOT NULL COMMENT '订单项 id',
  `order_id` bigint(20) NOT NULL COMMENT '订单 id',
  `sku_id` bigint(20) NOT NULL COMMENT 'SKU id',
  `title` varchar(255) NOT NULL COMMENT '商品标题',
  `bar_code` varchar(64) DEFAULT NULL COMMENT '商品条形码',
  `sku_properties` text COMMENT 'SKU 属性（冗余），JSON 格式',
  `picture_url` varchar(500) DEFAULT NULL COMMENT '商品图片 URL',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '商品价格',
  `quantity` int(11) NOT NULL DEFAULT '1' COMMENT '购买数量',
  `total_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '商品总价',
  `spu_name` varchar(128) DEFAULT NULL COMMENT 'SPU 名称（冗余）',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品项表';

-- ----------------------------
-- 3. 购物车表
-- ----------------------------
DROP TABLE IF EXISTS `oms_cart`;
CREATE TABLE `oms_cart` (
  `id` bigint(20) NOT NULL COMMENT '购物车记录 id',
  `user_id` bigint(20) NOT NULL COMMENT '用户 id',
  `sku_id` bigint(20) NOT NULL COMMENT 'SKU id',
  `title` varchar(255) NOT NULL COMMENT '商品标题',
  `bar_code` varchar(64) DEFAULT NULL COMMENT '商品条形码',
  `picture_url` varchar(500) DEFAULT NULL COMMENT '商品图片 URL',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '商品价格',
  `quantity` int(11) NOT NULL DEFAULT '1' COMMENT '购买数量',
  `data` text COMMENT '商品 SKU 全部属性（冗余历史），JSON 格式',
  `is_checked` int(1) DEFAULT '1' COMMENT '是否选中，1=选中，0=未选中',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sku` (`user_id`,`sku_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';
