-- Database: cs_mall_pms
-- Table: pms_sku_specification

CREATE TABLE `pms_sku_specification` (
  `id` bigint NOT NULL COMMENT '记录 id',
  `sku_id` bigint NOT NULL COMMENT 'SKU id',
  `attribute_id` bigint NOT NULL COMMENT '属性 id',
  `attribute_value_id` bigint NOT NULL COMMENT '属性值 id',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU 规格表'
