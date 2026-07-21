-- Database: cs_mall_ams
-- Table: ams_role_permission

CREATE TABLE `ams_role_permission` (
  `id` bigint NOT NULL COMMENT '记录 id',
  `role_id` bigint NOT NULL COMMENT '角色 id',
  `permission_id` bigint NOT NULL COMMENT '权限 id',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表'
