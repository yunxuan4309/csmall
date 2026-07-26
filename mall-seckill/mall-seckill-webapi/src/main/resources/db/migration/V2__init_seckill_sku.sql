-- Database: cs_mall_seckill
-- Table: seckill_sku

CREATE TABLE IF NOT EXISTS `seckill_sku` (
  `id` bigint NOT NULL COMMENT '秒杀 SKU id',
  `sku_id` bigint NOT NULL COMMENT '商品 SKU id',
  `spu_id` bigint NOT NULL COMMENT '秒杀 SPU id',
  `seckill_stock` int NOT NULL DEFAULT '0' COMMENT '秒杀库存',
  `seckill_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '秒杀价',
  `seckill_limit` int DEFAULT '1' COMMENT '限购数量',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_spu_id` (`spu_id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀 SKU 表'
