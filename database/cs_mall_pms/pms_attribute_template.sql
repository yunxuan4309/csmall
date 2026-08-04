-- Database: cs_mall_pms
-- Table: pms_attribute_template

CREATE TABLE `pms_attribute_template` (
  `id` bigint NOT NULL COMMENT '属性模板 id',
  `name` varchar(64) NOT NULL COMMENT '模板名称',
  `pinyin` varchar(64) DEFAULT NULL COMMENT '模板名称拼音（预留，用于按拼音搜索/排序）',
  `keywords` varchar(255) DEFAULT NULL COMMENT '关键词列表，逗号分隔',
  `sort` int DEFAULT '0' COMMENT '自定义排序序号',
  `type` int DEFAULT '1' COMMENT '类型，1=销售属性，2=参数属性',
  `description` varchar(500) DEFAULT NULL COMMENT '模板描述',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='属性模板表'
