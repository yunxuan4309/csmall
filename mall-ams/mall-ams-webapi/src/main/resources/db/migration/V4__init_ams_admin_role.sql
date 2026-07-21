-- Database: cs_mall_ams
-- Table: ams_admin_role

CREATE TABLE `ams_admin_role` (
  `id` bigint NOT NULL COMMENT '记录 id',
  `admin_id` bigint NOT NULL COMMENT '管理员 id',
  `role_id` bigint NOT NULL COMMENT '角色 id',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_role` (`admin_id`,`role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员角色关联表'
