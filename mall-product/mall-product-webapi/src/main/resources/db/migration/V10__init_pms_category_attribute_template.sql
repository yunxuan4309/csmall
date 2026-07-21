-- Database: cs_mall_pms
-- Table: pms_category_attribute_template

CREATE TABLE `pms_category_attribute_template` (
  `id` bigint NOT NULL COMMENT '记录 id',
  `category_id` bigint NOT NULL COMMENT '分类 id',
  `attribute_template_id` bigint NOT NULL COMMENT '属性模板 id',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_template` (`category_id`,`attribute_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类属性模板关联表'
