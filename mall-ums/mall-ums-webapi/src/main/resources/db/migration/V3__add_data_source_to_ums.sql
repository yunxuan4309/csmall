-- ============================================================
-- V3: ums_user / ums_login_log 增加 data_source 列（数据来源标识）
-- 背景: TODO #48「Python 模拟数据」需要让造出来的数据**自证身份**——
--       事后能直接回答"这行到底是不是造的"。
--       原方案打算复用现有自由字段（oms_order.tag / oms_order_item.data），
--       但同一行会同时挂两种语义（展示标签 + 数据来源），反而更易混；
--       2026-09-11 用户拍板改为**专用列**，9 张表统一加 data_source。
-- 设计: NULL = 常规/真实数据；'SIM' = 模拟造数。
--       值由造数脚本**按影子登记表 cs_mall_sim.sim_entity 回填**，
--       服务端代码零改动（不读、也不写该列）。
--       ⚠️ ums_login_log 是**登录时服务端写的**，造数脚本没有它的插入点，
--          回填口径为 `user_id IN (登记 user_ref)`（实测该表有 user_id）。
-- 纪律: 🔴 只走迁移文件，**禁止手工 ALTER** —— 手工加列后再执行本迁移会报
--       `Duplicate column name 'data_source'` → Flyway 失败 → **应用启动失败**。
--       幂等性由 flyway_schema_history 保证（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）。
-- 回滚: ALTER TABLE ... DROP COLUMN data_source; 并删除本迁移的 flyway_schema_history 行。
-- 位置: 列追加在表末尾（不用 AFTER，避免因依赖具体列名而在新老环境产生漂移）。
-- ============================================================
ALTER TABLE `ums_user`
    ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '数据来源：NULL=常规；SIM=模拟造数(#48)';

ALTER TABLE `ums_login_log`
    ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '数据来源：NULL=常规；SIM=模拟造数(#48)';
