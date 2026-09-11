-- ============================================================
-- V7: oms_order / oms_order_item / oms_cart / oms_payment_record 增加 data_source 列
-- 背景: TODO #48「Python 模拟数据」需要让造出来的数据**自证身份**。
--       原方案打算复用现有自由字段（oms_order.tag / oms_order_item.data /
--       oms_payment_record.extra_data），但属于**语义借用**（tag 本是展示标签、
--       data 本是商品全属性 json），且 tag 会在订单页显示成标签；
--       2026-09-11 用户拍板改为**专用列**，9 张表统一加 data_source。
-- 设计: NULL = 常规/真实数据；'SIM' = 模拟造数。
--       值由造数脚本**按影子登记表 cs_mall_sim.sim_entity 回填**，
--       服务端代码零改动（不读、也不写该列）。
--       ⚠️ 回填口径按"谁写的"分两类：
--          · 脚本直接 INSERT 的（oms_order / oms_order_item / oms_cart）→ 按登记主键 `id`
--          · 服务端在链路中写的（oms_payment_record，支付时落库）→ 按 `user_id IN (登记 user_ref)`
--       另: oms_payment_record 此前在清理清单里挂的是"按登记主键"，但脚本从不登记它
--           → 清理会走"无登记→跳过"导致**残留**，已同步改为按 user_id 清理。
-- 纪律: 🔴 只走迁移文件，**禁止手工 ALTER** —— 手工加列后再执行本迁移会报
--       `Duplicate column name 'data_source'` → Flyway 失败 → **应用启动失败**。
--       幂等性由 flyway_schema_history 保证（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS）。
-- 回滚: ALTER TABLE ... DROP COLUMN data_source; 并删除本迁移的 flyway_schema_history 行。
-- 位置: 列追加在表末尾（不用 AFTER，避免因依赖具体列名而在新老环境产生漂移）。
-- ============================================================
ALTER TABLE `oms_order`
    ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '数据来源：NULL=常规；SIM=模拟造数(#48)';

ALTER TABLE `oms_order_item`
    ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '数据来源：NULL=常规；SIM=模拟造数(#48)';

ALTER TABLE `oms_cart`
    ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '数据来源：NULL=常规；SIM=模拟造数(#48)';

ALTER TABLE `oms_payment_record`
    ADD COLUMN `data_source` varchar(16) DEFAULT NULL COMMENT '数据来源：NULL=常规；SIM=模拟造数(#48)';
