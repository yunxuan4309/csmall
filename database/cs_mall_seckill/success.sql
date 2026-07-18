-- Database: cs_mall_seckill
-- Table: success

CREATE TABLE `success` (
  `id` bigint NOT NULL COMMENT '记录 id',
  `seckill_id` bigint DEFAULT NULL COMMENT '关联秒杀id',
  `user_id` bigint NOT NULL COMMENT '用户 id',
  `user_phone` varchar(20) DEFAULT NULL COMMENT '用户手机号',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU id',
  `title` varchar(255) DEFAULT NULL COMMENT '商品SKU标题(冗余)',
  `main_picture` varchar(500) DEFAULT NULL COMMENT '商品SKU图片URL',
  `seckill_price` decimal(10,2) DEFAULT NULL COMMENT '秒杀商品单价',
  `quantity` int DEFAULT '1' COMMENT '秒杀商品数量',
  `bar_code` varchar(100) DEFAULT NULL COMMENT '条形码',
  `data` text COMMENT '附加数据',
  `order_sn` varchar(64) DEFAULT NULL COMMENT '订单编号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_user` (`sku_id`,`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_sn` (`order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀成功记录表'
