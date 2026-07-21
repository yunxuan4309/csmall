-- Database: cs_mall_resource
-- Table: res_upload_record

CREATE TABLE `res_upload_record` (
  `id` bigint NOT NULL COMMENT '记录 id',
  `user_id` bigint NOT NULL COMMENT '上传用户 id',
  `username` varchar(64) DEFAULT NULL COMMENT '上传用户名（冗余，便于展示）',
  `url` varchar(500) NOT NULL COMMENT '文件访问 URL',
  `content_type` varchar(64) DEFAULT NULL COMMENT '文件类型，如 image/jpeg',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小，单位：字节',
  `width` int DEFAULT NULL COMMENT '图片宽度，单位：px（仅图片类型有效）',
  `height` int DEFAULT NULL COMMENT '图片高度，单位：px（仅图片类型有效）',
  `original_filename` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据最后修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_gmt_create` (`gmt_create`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户上传文件记录表'
