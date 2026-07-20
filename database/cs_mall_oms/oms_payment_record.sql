-- Database: cs_mall_oms
-- Table: oms_payment_record
-- 支付流水记录表：记录每笔支付交易的全生命周期

CREATE TABLE `oms_payment_record` (
  `id`              bigint        NOT NULL COMMENT '流水ID',
  `order_id`        bigint        NOT NULL COMMENT '关联订单ID',
  `order_sn`        varchar(64)   NOT NULL COMMENT '订单编号（冗余，方便查询）',
  `user_id`         bigint        NOT NULL COMMENT '用户ID',
  `payment_type`    int           NOT NULL COMMENT '支付渠道：0=银联，1=微信，2=支付宝',
  `pay_amount`      decimal(10,2) NOT NULL COMMENT '支付金额',
  `trade_no`        varchar(128)  DEFAULT NULL COMMENT '第三方交易号（支付宝trade_no/微信transaction_id）',
  `out_trade_no`    varchar(128)  DEFAULT NULL COMMENT '商户订单号（传给第三方的订单号，可用于对账）',
  `payment_status`  int           DEFAULT 0 COMMENT '支付状态：0=待支付，1=支付成功，2=支付失败，3=已关闭，4=已退款',
  `buyer_info`      varchar(256)  DEFAULT NULL COMMENT '付款方信息（支付宝买家账号/微信openid）',
  `callback_log`    text          DEFAULT NULL COMMENT '回调原始数据（JSON），用于排查问题与对账',
  `refund_amount`   decimal(10,2) DEFAULT NULL COMMENT '退款金额（预留扩展）',
  `refund_trade_no` varchar(128)  DEFAULT NULL COMMENT '退款交易号（预留扩展）',
  `extra_data`      json          DEFAULT NULL COMMENT '扩展数据（JSON），存储渠道特定的附加信息，便于未来扩展',
  `gmt_request`     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '发起支付时间',
  `gmt_payment`     datetime      DEFAULT NULL COMMENT '支付完成时间（回调确认时间）',
  `gmt_create`      datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified`    datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_trade_no` (`trade_no`),
  KEY `idx_out_trade_no` (`out_trade_no`),
  KEY `idx_payment_status` (`payment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付流水记录表';
