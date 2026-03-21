-- =====================================================
-- 电商平台系统 - 后台管理模块 (AMS) 数据库初始化脚本
-- 数据库名称：cs_mall_ams
-- 创建日期：2026-03-21
-- =====================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS cs_mall_ams DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE cs_mall_ams;

-- ----------------------------
-- 1. 管理员表
-- ----------------------------
DROP TABLE IF EXISTS `ams_admin`;
CREATE TABLE `ams_admin` (
  `id` bigint(20) NOT NULL COMMENT '管理员 id',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码（密文）',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像 URL',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号码',
  `email` varchar(128) DEFAULT NULL COMMENT '电子邮箱',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `enable` int(1) DEFAULT '1' COMMENT '是否启用，1=启用，0=未启用',
  `last_login_ip` varchar(64) DEFAULT NULL COMMENT '最后登录 IP 地址（冗余）',
  `login_count` int(11) DEFAULT '0' COMMENT '累计登录次数（冗余）',
  `gmt_last_login` datetime DEFAULT NULL COMMENT '最后登录时间（冗余）',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据最后修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- ----------------------------
-- 2. 角色表
-- ----------------------------
DROP TABLE IF EXISTS `ams_role`;
CREATE TABLE `ams_role` (
  `id` bigint(20) NOT NULL COMMENT '角色 id',
  `name` varchar(64) NOT NULL COMMENT '角色名称',
  `code` varchar(64) NOT NULL COMMENT '角色编码',
  `description` varchar(500) DEFAULT NULL COMMENT '角色描述',
  `sort` int(11) DEFAULT '0' COMMENT '排序序号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ----------------------------
-- 3. 权限表
-- ----------------------------
DROP TABLE IF EXISTS `ams_permission`;
CREATE TABLE `ams_permission` (
  `id` bigint(20) NOT NULL COMMENT '权限 id',
  `name` varchar(64) NOT NULL COMMENT '权限名称',
  `code` varchar(128) NOT NULL COMMENT '权限编码',
  `type` int(1) DEFAULT '1' COMMENT '权限类型，1=菜单，2=按钮，3=接口',
  `parent_id` bigint(20) DEFAULT '0' COMMENT '父权限 id',
  `url` varchar(500) DEFAULT NULL COMMENT 'URL 路径',
  `method` varchar(16) DEFAULT NULL COMMENT 'HTTP 方法',
  `icon` varchar(128) DEFAULT NULL COMMENT '图标',
  `sort` int(11) DEFAULT '0' COMMENT '排序序号',
  `description` varchar(500) DEFAULT NULL COMMENT '权限描述',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- ----------------------------
-- 4. 管理员角色关联表
-- ----------------------------
DROP TABLE IF EXISTS `ams_admin_role`;
CREATE TABLE `ams_admin_role` (
  `id` bigint(20) NOT NULL COMMENT '记录 id',
  `admin_id` bigint(20) NOT NULL COMMENT '管理员 id',
  `role_id` bigint(20) NOT NULL COMMENT '角色 id',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_role` (`admin_id`,`role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员角色关联表';

-- ----------------------------
-- 5. 角色权限关联表
-- ----------------------------
DROP TABLE IF EXISTS `ams_role_permission`;
CREATE TABLE `ams_role_permission` (
  `id` bigint(20) NOT NULL COMMENT '记录 id',
  `role_id` bigint(20) NOT NULL COMMENT '角色 id',
  `permission_id` bigint(20) NOT NULL COMMENT '权限 id',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- ----------------------------
-- 6. 登录日志表
-- ----------------------------
DROP TABLE IF EXISTS `ams_login_log`;
CREATE TABLE `ams_login_log` (
  `id` bigint(20) NOT NULL COMMENT '登录日志 id',
  `admin_id` bigint(20) NOT NULL COMMENT '管理员 id',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `ip_address` varchar(64) DEFAULT NULL COMMENT '登录 IP 地址',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '浏览器信息',
  `login_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `status` int(1) DEFAULT '1' COMMENT '登录状态，1=成功，0=失败',
  `message` varchar(500) DEFAULT NULL COMMENT '登录消息',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员登录日志表';

-- ----------------------------
-- 初始数据：默认管理员账号
-- ----------------------------
-- 注意：实际使用时需要在代码中通过 BCrypt 加密密码后更新到此字段
-- 默认用户名：admin
-- 默认密码：admin123 (需要在首次登录后修改)
-- INSERT INTO `ams_admin` (`id`, `username`, `password`, `nickname`, `enable`) 
-- VALUES (1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iDJfYR5sILt8BPLu5mZFBqg5RE0S', '超级管理员', 1);
