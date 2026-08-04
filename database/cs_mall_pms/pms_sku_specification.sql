-- Database: cs_mall_pms
-- Table: pms_sku_specification
-- 模型：属性定义模型（与 SkuSpecification.java 实体对齐）

CREATE TABLE `pms_sku_specification` (
  `id` bigint NOT NULL COMMENT '记录 id',
  `sku_id` bigint NOT NULL COMMENT 'SKU id',
  `attribute_id` bigint NOT NULL COMMENT '属性 id',
  `attribute_name` varchar(64) DEFAULT NULL COMMENT '属性名称',
  `attribute_value` varchar(64) DEFAULT NULL COMMENT '属性值',
  `unit` varchar(16) DEFAULT NULL COMMENT '计量单位',
  `sort` int DEFAULT '0' COMMENT '排序序号',
  `attribute_value_id` bigint DEFAULT NULL COMMENT '旧模型属性值id（已废弃，可空）',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU规格明细表'
