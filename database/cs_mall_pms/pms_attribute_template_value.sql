-- Database: cs_mall_pms
-- Table: pms_attribute_template_value

CREATE TABLE `pms_attribute_template_value` (
  `id` bigint NOT NULL COMMENT '记录 id',
  `attribute_template_id` bigint NOT NULL COMMENT '属性模板 id',
  `value` varchar(64) NOT NULL COMMENT '属性值',
  `sort` int DEFAULT '0' COMMENT '排序序号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_template_id` (`attribute_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='属性模板值表'
