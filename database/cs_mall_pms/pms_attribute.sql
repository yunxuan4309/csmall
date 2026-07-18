-- Database: cs_mall_pms
-- Table: pms_attribute

CREATE TABLE `pms_attribute` (
  `id` bigint NOT NULL COMMENT '属性 id',
  `attribute_template_id` bigint NOT NULL COMMENT '属性模板 id',
  `name` varchar(64) NOT NULL COMMENT '属性名称',
  `type` int DEFAULT '1' COMMENT '类型，1=单选，2=多选，3=输入',
  `values` text COMMENT '属性值列表，JSON 格式',
  `sort` int DEFAULT '0' COMMENT '排序序号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_template_id` (`attribute_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品属性表'
