-- ============================================================
-- V6: success / seckill_message_retry 增加 data_source 列（数据来源标识）
-- 背景: TODO #48「Python 模拟数据」需要让造出来的数据**自证身份**。
--       原方案打算复用现有自由字段，但属语义借用且易混；
--       2026-09-11 用户拍板改为**专用列**，9 张表统一加 data_source。
-- 设计: NULL = 常规/真实数据；'SIM' = 模拟造数。
--       值由造数脚本**按影子登记表 cs_mall_sim.sim_entity 回填**，
--       服务端代码零改动（不读、也不写该列）。
--       ⚠️ 这两张表都是**服务端/SDK 在秒杀链路中写的**（success=秒杀成功记录、
--          seckill_message_retry=MQ 失败重试），造数脚本没有插入点 →
--          回填口径为 `user_id IN (登记 user_ref)`（实测两表都有 user_id）。
--       ℹ️ 现状说明（2026-09-11）：造数脚本的 `--with-seckill` **尚未实现**，
--          所以本轮不会产生这两张表的 SIM 行；本迁移是为后续造秒杀数据预留，
--          列先加上无副作用（可空、无人读）。
-- 纪律: 🔴 只走迁移文件，**禁止手工 ALTER** —— 手工加列后再执行本迁移会报
--       `Duplicate column name 'data_source'` → Flyway 失败 → **应用启动失败**。
--       幂等性由 flyway_schema_history 保证（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）。
-- 回滚: ALTER TABLE ... DROP COLUMN data_source; 并删除本迁移的 flyway_schema_history 行。
-- 位置: 列追加在表末尾（不用 AFTER，避免因依赖具体列名而在新老环境产生漂移）。
-- ============================================================
ALTER TABLE `success`
    ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '数据来源：NULL=常规；SIM=模拟造数(#48)';

ALTER TABLE `seckill_message_retry`
    ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '数据来源：NULL=常规；SIM=模拟造数(#48)';
