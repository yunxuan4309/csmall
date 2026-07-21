-- Database: cs_mall_oms
-- Table: oms_cart

CREATE TABLE `oms_cart` (
  `id` bigint NOT NULL COMMENT '购物车记录 id',
  `user_id` bigint NOT NULL COMMENT '用户 id',
  `sku_id` bigint NOT NULL COMMENT 'SKU id',
  `title` varchar(255) NOT NULL COMMENT '商品标题',
  `picture_url` varchar(500) DEFAULT NULL COMMENT '商品图片 URL',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '商品价格',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '购买数量',
  `bar_code` varchar(64) DEFAULT NULL COMMENT '商品条形码',
  `data` text COMMENT '商品 SKU 全部属性（冗余，历史），JSON 格式',
  `is_checked` int DEFAULT '1' COMMENT '是否选中，1=选中，0=未选中',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sku` (`user_id`,`sku_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车表'
