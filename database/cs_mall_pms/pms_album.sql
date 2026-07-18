-- Database: cs_mall_pms
-- Table: pms_album

CREATE TABLE `pms_album` (
  `id` bigint NOT NULL COMMENT '相册 id',
  `name` varchar(64) NOT NULL COMMENT '相册名称',
  `description` varchar(500) DEFAULT NULL COMMENT '相册描述',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='相册表'
