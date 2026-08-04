-- Database: cs_mall_pms
-- Table: pms_attribute
-- 模型：属性定义模型（与 Attribute.java 实体对齐）

CREATE TABLE `pms_attribute` (
  `id` bigint NOT NULL COMMENT '属性 id',
  `attribute_template_id` bigint NOT NULL COMMENT '属性模板 id',
  `name` varchar(64) NOT NULL COMMENT '属性名称',
  `description` varchar(500) DEFAULT NULL COMMENT '属性简介',
  `type` int DEFAULT '1' COMMENT '属性类型，1=销售属性，0=非销售属性',
  `input_type` int DEFAULT '0' COMMENT '输入类型，0=手动录入，1=单选，2=多选，3=单选下拉，4=多选下拉',
  `value_list` text COMMENT '备选值列表，JSON 格式',
  `unit` varchar(16) DEFAULT NULL COMMENT '计量单位',
  `values` text COMMENT '旧模型属性值列表（已废弃，保留兼容）',
  `sort` int DEFAULT '0' COMMENT '排序序号',
  `is_allow_customize` int DEFAULT '0' COMMENT '是否允许自定义，1=允许，0=禁止',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_template_id` (`attribute_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品属性表'
