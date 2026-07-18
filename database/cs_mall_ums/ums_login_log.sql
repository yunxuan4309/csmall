-- Database: cs_mall_ums
-- Table: ums_login_log

CREATE TABLE `ums_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '登录日志id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `ip` varchar(64) DEFAULT NULL COMMENT '登录IP地址',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '浏览器信息',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_login` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_login_time` (`gmt_login`)
) ENGINE=InnoDB AUTO_INCREMENT=2056763113849012226 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户登录日志表'
