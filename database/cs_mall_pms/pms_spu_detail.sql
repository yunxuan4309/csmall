-- Database: cs_mall_pms
-- Table: pms_spu_detail

CREATE TABLE `pms_spu_detail` (
  `id` bigint NOT NULL COMMENT 'SPU 详情 id',
  `spu_id` bigint NOT NULL COMMENT 'SPU id',
  `content` longtext COMMENT '详情内容，HTML 格式',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spu_id` (`spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SPU 详情表'
