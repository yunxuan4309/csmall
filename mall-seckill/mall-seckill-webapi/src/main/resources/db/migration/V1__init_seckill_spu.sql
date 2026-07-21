-- Database: cs_mall_seckill
-- Table: seckill_spu

CREATE TABLE `seckill_spu` (
  `id` bigint NOT NULL COMMENT '秒杀 SPU id',
  `spu_id` bigint NOT NULL COMMENT '商品 SPU id',
  `list_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '秒杀价格',
  `start_time` datetime NOT NULL COMMENT '秒杀开始时间',
  `end_time` datetime NOT NULL COMMENT '秒杀结束时间',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_spu_id` (`spu_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_time_range` (`start_time`,`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀 SPU 表'
