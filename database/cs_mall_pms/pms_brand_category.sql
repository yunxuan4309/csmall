-- Database: cs_mall_pms
-- Table: pms_brand_category

CREATE TABLE `pms_brand_category` (
  `id` bigint NOT NULL COMMENT '记录 id',
  `brand_id` bigint NOT NULL COMMENT '品牌 id',
  `category_id` bigint NOT NULL COMMENT '分类 id',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_brand_category` (`brand_id`,`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌分类关联表'
