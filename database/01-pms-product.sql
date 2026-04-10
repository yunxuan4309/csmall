-- =====================================================
-- 电商平台系统 - 商品模块 (PMS) 数据库初始化脚本
-- 数据库名称：cs_mall_pms
-- 创建日期：2026-03-21
-- =====================================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS cs_mall_pms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE cs_mall_pms;

-- ----------------------------
-- 1. 品牌表
-- ----------------------------
DROP TABLE IF EXISTS `pms_brand`;
CREATE TABLE `pms_brand` (
  `id` bigint(20) NOT NULL COMMENT '品牌 id',
  `name` varchar(64) NOT NULL COMMENT '品牌名称',
  `logo_url` varchar(500) DEFAULT NULL COMMENT '品牌 Logo URL',
  `description` varchar(500) DEFAULT NULL COMMENT '品牌描述',
  `first_letter` char(1) DEFAULT NULL COMMENT '首字母',
  `sort` int(11) DEFAULT '0' COMMENT '排序序号',
  `is_show` int(1) DEFAULT '1' COMMENT '是否显示，1=显示，0=不显示',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌表';

-- ----------------------------
-- 2. 品牌分类关联表
-- ----------------------------
DROP TABLE IF EXISTS `pms_brand_category`;
CREATE TABLE `pms_brand_category` (
  `id` bigint(20) NOT NULL COMMENT '记录 id',
  `brand_id` bigint(20) NOT NULL COMMENT '品牌 id',
  `category_id` bigint(20) NOT NULL COMMENT '分类 id',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_brand_category` (`brand_id`,`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌分类关联表';

-- ----------------------------
-- 3. 分类表
-- ----------------------------
DROP TABLE IF EXISTS `pms_category`;
CREATE TABLE `pms_category` (
  `id` bigint(20) NOT NULL COMMENT '分类 id',
  `name` varchar(64) NOT NULL COMMENT '分类名称',
  `parent_id` bigint(20) DEFAULT '0' COMMENT '父分类 id',
  `depth` int(1) DEFAULT '1' COMMENT '深度，1=一级，2=二级，3=三级',
  `keywords` varchar(255) DEFAULT NULL COMMENT '关键词列表，各关键词使用英文的逗号分隔',
  `sort` int(11) DEFAULT '0' COMMENT '排序序号',
  `icon` varchar(500) DEFAULT NULL COMMENT '图标图片 URL',
  `enable` int(1) DEFAULT '1' COMMENT '是否启用，1=启用，0=未启用',
  `is_parent` int(1) DEFAULT '0' COMMENT '是否为父级，1=是，0=否',
  `is_display` int(1) DEFAULT '1' COMMENT '是否显示在导航栏，1=显示，0=不显示',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ----------------------------
-- 4. 分类属性模板关联表
-- ----------------------------
DROP TABLE IF EXISTS `pms_category_attribute_template`;
CREATE TABLE `pms_category_attribute_template` (
  `id` bigint(20) NOT NULL COMMENT '记录 id',
  `category_id` bigint(20) NOT NULL COMMENT '分类 id',
  `attribute_template_id` bigint(20) NOT NULL COMMENT '属性模板 id',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_template` (`category_id`,`attribute_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类属性模板关联表';

-- ----------------------------
-- 5. 属性模板表
-- ----------------------------
DROP TABLE IF EXISTS `pms_attribute_template`;
CREATE TABLE `pms_attribute_template` (
  `id` bigint(20) NOT NULL COMMENT '属性模板 id',
  `name` varchar(64) NOT NULL COMMENT '模板名称',
  `type` int(1) DEFAULT '1' COMMENT '类型，1=销售属性，2=参数属性',
  `description` varchar(500) DEFAULT NULL COMMENT '模板描述',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='属性模板表';

-- ----------------------------
-- 6. 属性模板值表
-- ----------------------------
DROP TABLE IF EXISTS `pms_attribute_template_value`;
CREATE TABLE `pms_attribute_template_value` (
  `id` bigint(20) NOT NULL COMMENT '记录 id',
  `attribute_template_id` bigint(20) NOT NULL COMMENT '属性模板 id',
  `value` varchar(64) NOT NULL COMMENT '属性值',
  `sort` int(11) DEFAULT '0' COMMENT '排序序号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_template_id` (`attribute_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='属性模板值表';

-- ----------------------------
-- 7. 属性表
-- ----------------------------
DROP TABLE IF EXISTS `pms_attribute`;
CREATE TABLE `pms_attribute` (
  `id` bigint(20) NOT NULL COMMENT '属性 id',
  `attribute_template_id` bigint(20) NOT NULL COMMENT '属性模板 id',
  `name` varchar(64) NOT NULL COMMENT '属性名称',
  `type` int(1) DEFAULT '1' COMMENT '类型，1=单选，2=多选，3=输入',
  `values` text COMMENT '属性值列表，JSON 格式',
  `sort` int(11) DEFAULT '0' COMMENT '排序序号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_template_id` (`attribute_template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品属性表';

-- ----------------------------
-- 8. SPU 表
-- ----------------------------
DROP TABLE IF EXISTS `pms_spu`;
CREATE TABLE `pms_spu` (
  `id` bigint(20) NOT NULL COMMENT 'SPU id',
  `name` varchar(128) NOT NULL COMMENT 'SPU 名称',
  `type_number` varchar(64) NOT NULL COMMENT 'SPU 编号',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `description` text COMMENT '简介',
  `list_price` decimal(10,2) DEFAULT '0.00' COMMENT '价格（显示在列表中）',
  `stock` int(11) DEFAULT '0' COMMENT '当前库存（冗余）',
  `stock_threshold` int(11) DEFAULT '0' COMMENT '库存预警阈值（冗余）',
  `unit` varchar(16) DEFAULT NULL COMMENT '计件单位',
  `brand_id` bigint(20) DEFAULT NULL COMMENT '品牌 id',
  `brand_name` varchar(64) DEFAULT NULL COMMENT '品牌名称（冗余）',
  `category_id` bigint(20) DEFAULT NULL COMMENT '类别 id',
  `category_name` varchar(64) DEFAULT NULL COMMENT '类别名称（冗余）',
  `attribute_template_id` bigint(20) DEFAULT NULL COMMENT '属性模板 id',
  `album_id` bigint(20) DEFAULT NULL COMMENT '相册 id',
  `pictures` text COMMENT '组图 URLs，使用 JSON 数组表示',
  `keywords` text COMMENT '关键词列表，各关键词使用英文的逗号分隔',
  `tags` varchar(255) DEFAULT NULL COMMENT '标签列表，各标签使用英文的逗号分隔，原则上最多 3 个',
  `sales` int(11) DEFAULT '0' COMMENT '销量（冗余）',
  `comment_count` int(11) DEFAULT '0' COMMENT '买家评论数量总和（冗余）',
  `positive_comment_count` int(11) DEFAULT '0' COMMENT '买家好评数量总和（冗余）',
  `sort` int(11) DEFAULT '0' COMMENT '自定义排序序号',
  `is_deleted` int(1) DEFAULT '0' COMMENT '是否标记为删除，1=已删除，0=未删除',
  `is_published` int(1) DEFAULT '0' COMMENT '是否上架（发布），1=已上架，0=未上架',
  `is_new_arrival` int(1) DEFAULT '0' COMMENT '是否新品，1=新品，0=非新品',
  `is_recommend` int(1) DEFAULT '0' COMMENT '是否推荐，1=推荐，0=不推荐',
  `is_checked` int(1) DEFAULT '0' COMMENT '是否已审核，1=已审核，0=未审核',
  `check_user` varchar(64) DEFAULT NULL COMMENT '审核人（冗余）',
  `gmt_check` datetime DEFAULT NULL COMMENT '审核通过时间（冗余）',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据最后修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_number` (`type_number`),
  KEY `idx_name` (`name`),
  KEY `idx_brand_id` (`brand_id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_published` (`is_published`),
  KEY `idx_checked` (`is_checked`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPU（Standard Product Unit）表';

-- ----------------------------
-- 9. SPU 详情表
-- ----------------------------
DROP TABLE IF EXISTS `pms_spu_detail`;
CREATE TABLE `pms_spu_detail` (
  `id` bigint(20) NOT NULL COMMENT 'SPU 详情 id',
  `spu_id` bigint(20) NOT NULL COMMENT 'SPU id',
  `content` longtext COMMENT '详情内容，HTML 格式',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spu_id` (`spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPU 详情表';

-- ----------------------------
-- 10. SKU 表
-- ----------------------------
DROP TABLE IF EXISTS `pms_sku`;
CREATE TABLE `pms_sku` (
  `id` bigint(20) NOT NULL COMMENT 'SKU id',
  `spu_id` bigint(20) NOT NULL COMMENT 'SPU id',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `bar_code` varchar(64) DEFAULT NULL COMMENT '条形码',
  `attribute_template_id` bigint(20) DEFAULT NULL COMMENT '属性模板 id',
  `specifications` text COMMENT '全部属性，使用 JSON 格式表示（冗余）',
  `album_id` bigint(20) DEFAULT NULL COMMENT '相册 id',
  `pictures` text COMMENT '组图 URLs，使用 JSON 数组表示',
  `price` decimal(10,2) DEFAULT '0.00' COMMENT '单价',
  `stock` int(11) DEFAULT '0' COMMENT '当前库存',
  `stock_threshold` int(11) DEFAULT '0' COMMENT '库存预警阈值',
  `sales` int(11) DEFAULT '0' COMMENT '销量（冗余）',
  `comment_count` int(11) DEFAULT '0' COMMENT '买家评论数量总和（冗余）',
  `positive_comment_count` int(11) DEFAULT '0' COMMENT '买家好评数量总和（冗余）',
  `sort` int(11) DEFAULT '0' COMMENT '自定义排序序号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '数据创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '数据最后修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_spu_id` (`spu_id`),
  KEY `idx_bar_code` (`bar_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU（Stock Keeping Unit）表';

-- ----------------------------
-- 11. SKU 规格表
-- ----------------------------
DROP TABLE IF EXISTS `pms_sku_specification`;
CREATE TABLE `pms_sku_specification` (
  `id` bigint(20) NOT NULL COMMENT '记录 id',
  `sku_id` bigint(20) NOT NULL COMMENT 'SKU id',
  `attribute_id` bigint(20) NOT NULL COMMENT '属性 id',
  `attribute_value_id` bigint(20) NOT NULL COMMENT '属性值 id',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_sku_id` (`sku_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU 规格表';

-- ----------------------------
-- 12. 相册表
-- ----------------------------
DROP TABLE IF EXISTS `pms_album`;
CREATE TABLE `pms_album` (
  `id` bigint(20) NOT NULL COMMENT '相册 id',
  `name` varchar(64) NOT NULL COMMENT '相册名称',
  `description` varchar(500) DEFAULT NULL COMMENT '相册描述',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='相册表';

-- ----------------------------
-- 13. 图片表
-- ----------------------------
DROP TABLE IF EXISTS `pms_picture`;
CREATE TABLE `pms_picture` (
  `id` bigint(20) NOT NULL COMMENT '图片 id',
  `album_id` bigint(20) NOT NULL COMMENT '相册 id',
  `url` varchar(500) NOT NULL COMMENT '图片 URL',
  `title` varchar(128) DEFAULT NULL COMMENT '图片标题',
  `sort` int(11) DEFAULT '0' COMMENT '排序序号',
  `gmt_create` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_album_id` (`album_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片表';
