-- Database: cs_mall_ams
-- Table: ams_permission

CREATE TABLE `ams_permission` (
  `id` bigint NOT NULL COMMENT '权限 id',
  `name` varchar(64) NOT NULL COMMENT '权限名称',
  `code` varchar(128) NOT NULL COMMENT '权限编码',
  `type` int DEFAULT '1' COMMENT '权限类型，1=菜单，2=按钮，3=接口',
  `parent_id` bigint DEFAULT '0' COMMENT '父权限 id',
  `url` varchar(500) DEFAULT NULL COMMENT 'URL 路径',
  `method` varchar(16) DEFAULT NULL COMMENT 'HTTP 方法',
  `icon` varchar(128) DEFAULT NULL COMMENT '图标',
  `sort` int DEFAULT '0' COMMENT '排序序号',
  `description` varchar(500) DEFAULT NULL COMMENT '权限描述',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表'
