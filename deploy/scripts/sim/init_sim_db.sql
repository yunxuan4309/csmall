-- =============================================================================
-- CoolShark #48 模拟数据 · 影子登记库 DDL
-- -----------------------------------------------------------------------------
-- 位置：老机 MySQL（`cs_mall_mysql` 容器），与 6 个业务库同实例、独立 schema
-- 用途：造数时**每创建一个实体写一行** → 清理与审计的唯一依据（不靠 LIKE 猜）
-- 实测背景（2026-09-11）：
--   * `cs_mall_sim` 尚不存在 ✅
--   * 全库 **0 个外键** → 漏一张表就静默残留，所以必须"登记驱动 + 逆序删"
--   * 含 `user_id` 的表实测 **7 张**
-- 幂等：可重复执行（CREATE ... IF NOT EXISTS）
-- ⚠️ 本库是**运维/测试辅助库**，不进任何服务的数据源，也不进业务库迁移
-- =============================================================================

CREATE DATABASE IF NOT EXISTS cs_mall_sim
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 批次：一次造数运行 = 一个 batch_id
CREATE TABLE IF NOT EXISTS cs_mall_sim.sim_batch (
  batch_id     VARCHAR(40)  NOT NULL COMMENT '批次号，如 sim_20260911_1530',
  started_at   DATETIME     NOT NULL,
  finished_at  DATETIME     NULL,
  days         INT          NOT NULL DEFAULT 0 COMMENT '模拟天数',
  per_day      INT          NOT NULL DEFAULT 0 COMMENT '每天行为数',
  done_actions BIGINT       NOT NULL DEFAULT 0 COMMENT '已完成动作数',
  dump_file    VARCHAR(255) NULL COMMENT '造数前全量快照文件名（快照兜底用）',
  status       VARCHAR(16)  NOT NULL DEFAULT 'running' COMMENT 'running/finished/cleaned/failed',
  note         VARCHAR(2000) NULL COMMENT '结果回填：统计数字 / 压测结论（#48 §五 第 10 步）',
  PRIMARY KEY (batch_id)
) ENGINE=InnoDB COMMENT='模拟数据批次（影子登记）';

-- 实体登记：每个被创建的实体一行 —— 清理的唯一依据
CREATE TABLE IF NOT EXISTS cs_mall_sim.sim_entity (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  batch_id   VARCHAR(40) NOT NULL,
  db_name    VARCHAR(64) NOT NULL COMMENT 'cs_mall_ums / cs_mall_oms / cs_mall_seckill / cs_mall_resource',
  table_name VARCHAR(64) NOT NULL,
  pk_value   VARCHAR(64) NOT NULL COMMENT '主键值（字符串存，兼容雪花 ID / 自增）',
  user_ref   VARCHAR(64) NULL     COMMENT '关联的模拟用户 id，便于按用户精确清 Redis',
  created_at DATETIME    NOT NULL,
  PRIMARY KEY (id),
  KEY idx_batch  (batch_id),
  KEY idx_target (db_name, table_name),
  KEY idx_user   (user_ref)
) ENGINE=InnoDB COMMENT='模拟实体登记（影子表）';

-- 可选：基线快照（清理后比对照用）
CREATE TABLE IF NOT EXISTS cs_mall_sim.sim_baseline (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  batch_id    VARCHAR(40) NOT NULL,
  target      VARCHAR(128) NOT NULL COMMENT '如 cs_mall_ums.ums_user',
  metric      VARCHAR(64)  NOT NULL COMMENT '如 row_count / sum_sales / sum_stock',
  value       VARCHAR(64)  NOT NULL,
  created_at  DATETIME     NOT NULL,
  PRIMARY KEY (id),
  KEY idx_batch (batch_id)
) ENGINE=InnoDB COMMENT='造数前基线（清理后比对，差异要报告不要静默）';

-- 校验
SELECT table_name, table_comment
FROM information_schema.tables
WHERE table_schema = 'cs_mall_sim';
