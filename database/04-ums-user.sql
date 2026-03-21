-- =====================================================
-- 电商平台系统 - 用户模块 (UMS) 数据库初始化脚本
-- 数据库名称：cs_mall_ums
-- 创建日期：2026-03-21
-- =====================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS cs_mall_ums DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE cs_mall_ums;

-- ----------------------------
-- 1. 用户表
-- ----------------------------
DROP TABLE IF EXISTS `ums_user`;
CREATE TABLE `ums_user` (
  `id` bigint(20) NOT NULL COMMENT '用户 id',
  `username` varchar(64) NOT NULL COMMENT '用户名',
  `password` varchar(255) NOT NULL COMMENT '密码（冗余，密文）',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(500) DEFAULT NULL COMMENT '头像 URL',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号码',
  `email` varchar(128) DEFAULT NULL COMMENT '电子邮箱',
  `enable` int(1) DEFAULT '1' COMMENT '是否启用，1=启用，0=未启用',
  `reward_point` int(11) DEFAULT '0' COMMENT '积分（冗余）',
  `last_login_ip` varchar(64) DEFAULT NULL COMMENT '最后登录 IP 地址（冗余）',
  `login_count` int(11) DEFAULT '0' COMMENT '累计登录次数（冗余）',
  `gmt_last_login` datetime DEFAULT NULL COMMENT '最后登录时间（冗余）',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据最后修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基本信息表';
