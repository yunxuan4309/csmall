-- Database: cs_mall_ams
-- Table: ams_login_log

CREATE TABLE `ams_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_id` bigint NOT NULL COMMENT '管理员 id',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `ip_address` varchar(64) DEFAULT NULL COMMENT '登录 IP 地址',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '浏览器信息',
  `login_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `status` int DEFAULT '1' COMMENT '登录状态，1=成功，0=失败',
  `message` varchar(500) DEFAULT NULL COMMENT '登录消息',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员登录日志表'
