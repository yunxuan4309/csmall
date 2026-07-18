-- Database: cs_mall_ams
-- Table: ams_admin

CREATE TABLE `ams_admin` (
  `id` bigint NOT NULL COMMENT '管理员 id',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码（密文）',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像 URL',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号码',
  `email` varchar(128) DEFAULT NULL COMMENT '电子邮箱',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `enable` int DEFAULT '1' COMMENT '是否启用，1=启用，0=未启用',
  `last_login_ip` varchar(64) DEFAULT NULL COMMENT '最后登录 IP 地址（冗余）',
  `login_count` int DEFAULT '0' COMMENT '累计登录次数（冗余）',
  `gmt_last_login` datetime DEFAULT NULL COMMENT '最后登录时间（冗余）',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据最后修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员表'
