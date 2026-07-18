-- Database: cs_mall_pms
-- Table: pms_brand

CREATE TABLE `pms_brand` (
  `id` bigint NOT NULL COMMENT '品牌 id',
  `name` varchar(64) NOT NULL COMMENT '品牌名称',
  `logo_url` varchar(500) DEFAULT NULL COMMENT '品牌 Logo URL',
  `description` varchar(500) DEFAULT NULL COMMENT '品牌描述',
  `first_letter` char(1) DEFAULT NULL COMMENT '首字母',
  `sort` int DEFAULT '0' COMMENT '排序序号',
  `is_show` int DEFAULT '1' COMMENT '是否显示，1=显示，0=不显示',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌表'
