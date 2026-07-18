-- Database: cs_mall_oms
-- Table: oms_order_item

CREATE TABLE `oms_order_item` (
  `id` bigint NOT NULL COMMENT '订单项 id',
  `order_id` bigint NOT NULL COMMENT '订单 id',
  `sku_id` bigint NOT NULL COMMENT 'SKU id',
  `title` varchar(255) NOT NULL COMMENT '商品标题',
  `bar_code` varchar(64) DEFAULT NULL COMMENT '商品条形码',
  `picture_url` varchar(500) DEFAULT NULL COMMENT '商品图片 URL',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '商品价格',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '购买数量',
  `total_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '商品总价',
  `spu_name` varchar(128) DEFAULT NULL COMMENT 'SPU 名称（冗余）',
  `sku_properties` text COMMENT 'SKU 属性（冗余），JSON 格式',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单商品项表'
