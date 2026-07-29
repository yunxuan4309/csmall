CREATE TABLE `seckill_message_retry` (
  `id` bigint NOT NULL COMMENT '消息ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `order_sn` varchar(64) DEFAULT NULL COMMENT '订单编号',
  `message_body` text NOT NULL COMMENT 'Success 对象 JSON',
  `status` tinyint DEFAULT 0 COMMENT '0-待发送 1-已发送 2-失败达上限',
  `retry_count` int DEFAULT 0 COMMENT '重试次数',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '最近一次错误信息',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`, `retry_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀MQ消息重试表';
