# mall-leaf 模块弃用说明

> 弃用时间：2026-05-09

## 弃用原因

1. **技术栈不兼容**：mall-leaf 基于 JDK 8 + Spring Boot 2.5.4 + Spring 5.x，与项目其他模块（JDK 21 + Spring Boot 3.2.5 + Spring 6.x）存在代差，无法统一构建
2. **上游停更**：美团 Leaf 开源项目自 2020 年起已停止维护，无适配 JDK 21 / Spring Boot 3 的计划
3. **功能替代**：MyBatis-Plus 内置的 `IdType.ASSIGN_ID`（雪花算法）和 `IdWorker.getId()` 已能满足项目的分布式 ID 需求，无需额外部署 Leaf 服务
4. **减少运维成本**：弃用后无需单独启动 Leaf 服务及其依赖的 MySQL/ZooKeeper，简化部署流程

## 替代方案

原 Leaf 调用已全部替换为 MyBatis-Plus 的 `IdWorker.getId()`：

| 原调用 | 替换为 |
|--------|--------|
| `IdGeneratorUtils.getDistributeId("order")` | `IdWorker.getId()` |
| `IdGeneratorUtils.getDistributeId("order_item")` | `IdWorker.getId()` |
| `IdGeneratorUtils.getDistributeId(Key.SPU)` | `IdWorker.getId()` |
| `IdGeneratorUtils.getDistributeId(Key.SKU)` | `IdWorker.getId()` |
| `IdGeneratorUtils.getDistributeId("user")` | `IdWorker.getId()` |
| `IdGeneratorUtils.generatId("admin")` | `IdWorker.getId()` |

## 涉及模块

- mall-order：OmsOrderServiceImpl.java
- mall-product：SpuServiceImpl.java、SkuServiceImpl.java
- mall-ums：UserServiceImpl.java
- mall-ams：AdminServiceImpl.java

## 如何恢复

如果将来需要重新启用 Leaf 服务：

1. 取消根 pom.xml 中 `mall-leaf` 模块的注释
2. 将 Leaf 升级至兼容 JDK 21 的版本（需自行迁移 Spring Boot 3 / Curator 5）
3. 恢复各模块的 `IdGeneratorUtils` 工具类，替换 `IdWorker.getId()` 调用
