-- Database: cs_mall_pms
-- Table: pms_picture

CREATE TABLE `pms_picture` (
  `id` bigint NOT NULL COMMENT '图片 id',
  `album_id` bigint NOT NULL COMMENT '相册 id',
  `url` varchar(500) NOT NULL COMMENT '图片 URL',
  `title` varchar(128) DEFAULT NULL COMMENT '图片标题',
  `sort` int DEFAULT '0' COMMENT '排序序号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_album_id` (`album_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='图片表'
