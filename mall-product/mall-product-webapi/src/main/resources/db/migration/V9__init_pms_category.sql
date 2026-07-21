-- Database: cs_mall_pms
-- Table: pms_category

CREATE TABLE `pms_category` (
  `id` bigint NOT NULL COMMENT '分类 id',
  `name` varchar(64) NOT NULL COMMENT '分类名称',
  `parent_id` bigint DEFAULT '0' COMMENT '父分类 id',
  `depth` int DEFAULT '1' COMMENT '深度，1=一级，2=二级，3=三级',
  `keywords` varchar(255) DEFAULT NULL COMMENT '关键词列表',
  `sort` int DEFAULT '0' COMMENT '排序序号',
  `icon` varchar(500) DEFAULT NULL COMMENT '图标图片 URL',
  `enable` int DEFAULT '1' COMMENT '是否启用，1=启用，0=未启用',
  `is_parent` int DEFAULT '0' COMMENT '是否为父级，1=是，0=否',
  `is_display` int DEFAULT '1' COMMENT '是否显示在导航栏，1=显示，0=不显示',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类表'
