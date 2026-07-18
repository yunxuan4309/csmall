-- Database: cs_mall_pms
-- Table: pms_sku

CREATE TABLE `pms_sku` (
  `id` bigint NOT NULL COMMENT 'SKU id',
  `spu_id` bigint NOT NULL COMMENT 'SPU id',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `bar_code` varchar(64) DEFAULT NULL COMMENT '条形码',
  `attribute_template_id` bigint DEFAULT NULL COMMENT '属性模板 id',
  `specifications` text COMMENT '全部属性，使用 JSON 格式表示（冗余）',
  `album_id` bigint DEFAULT NULL COMMENT '相册 id',
  `pictures` text COMMENT '组图 URLs，使用 JSON 数组表示',
  `price` decimal(10,2) DEFAULT '0.00' COMMENT '单价',
  `stock` int DEFAULT '0' COMMENT '当前库存',
  `stock_threshold` int DEFAULT '0' COMMENT '库存预警阈值',
  `sales` int DEFAULT '0' COMMENT '销量（冗余）',
  `comment_count` int DEFAULT '0' COMMENT '买家评论数量总和（冗余）',
  `positive_comment_count` int DEFAULT '0' COMMENT '买家好评数量总和（冗余）',
  `sort` int DEFAULT '0' COMMENT '自定义排序序号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据最后修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_spu_id` (`spu_id`),
  KEY `idx_bar_code` (`bar_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU（Stock Keeping Unit）表'
