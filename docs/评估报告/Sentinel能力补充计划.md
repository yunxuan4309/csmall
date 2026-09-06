# Sentinel 能力补充计划（评估+计划，待实施）

> **创建**: 2026-08-26
> **状态**: 📋 已定稿待实施（用户确认：之后参考实施）
> **背景**: 2026-08-26 对项目 Sentinel 使用程度做全面评估（代码实证），
> 当前只用了"基础三件套"（流控 + 规则持久化 Nacos + 控制台），
> 企业级高级能力（热点限流/系统保护/授权/集群流控）均未使用。
> **关联**: [[集群化与配置中心迁移方案]]（集群流控与之配套）

---

## 一、现状盘点（2026-08-26 代码实证）

### 已用（基础三件套）
| 能力 | 现状 | 证据 |
|---|---|---|
| 流控 QPS | 秒杀 QPS=10（Nacos + 代码双保险） | SentinelFlowRuleConfig + Nacos flow-rules |
| 规则持久化 | 秒杀 flow 规则存 Nacos，重启不丢 | mall-seckill-flow-rules (SENTINEL_GROUP) |
| 控制台 | Sentinel dashboard 容器运行中（8090→8858） | csmall-sentinel |

### 4 个 @SentinelResource（含未配规则的空转）
| 接口 | 注解 | 规则 | 状态 |
|---|---|---|---|
| 秒杀订单提交 | ✅ | QPS=10（Nacos+代码） | ✅ 生效 |
| 新增订单 | ✅ | ❌ 无规则 | ⚠️ 空转（注解≠规则） |
| 支付订单 | ✅ | ❌ 无规则 | ⚠️ 空转 |
| adminLogin | ✅ | ❌ 无规则 | ⚠️ 空转 |

### 未用（企业级高级能力）
| 能力 | 现状 | 说明 |
|---|---|---|
| 熔断降级 degrade | dataSource 配了但 Nacos 无规则 | 空转（Issue 与 Q1 追问3 一致） |
| 系统自适应保护 | 无 SystemRule | CPU 低无场景 |
| 热点参数限流 | 无 ParamFlowRule | **秒杀爆款最该补** |
| 授权规则 | 无黑白名单 | Gateway+Security 已挡 |
| 集群流控 | 无 token server | 单机不需要，集群化配套 |

---

## 二、优先级划分

### 🔴 P0：补齐 4 个接口的规则（消除空转，极低成本）
- **新增订单 / 支付订单 / adminLogin 配 QPS 规则**（Nacos 建规则，不改代码）
- 参考阈值：新增订单 QPS=20、支付订单 QPS=20、adminLogin QPS=10（防爆破）
- 价值：把"只保护秒杀"升级为"关键接口全覆盖"，消除"注解但没规则"的空转
- 成本：极低（Nacos 建规则 + 重启验证）
- 风险：无（规则保守，不影响正常使用）

### 🟠 P1：热点参数限流（秒杀爆款精准保护）
- **秒杀商品详情/下单按 spuId 限流**：
  - 爆款 spuId（销量高）限 QPS=100
  - 普通 spuId 限 QPS=1000
  - 未配置的商品不限
- 价值：精准保护热点，不误伤其他商品（当前整接口共享 QPS=10 互相误伤）
- 成本：中（ParamFlowRule + Nacos 规则 + 秒杀接口改造）
- 面试价值：高（"热点参数限流 + 秒杀爆款"是电商标准玩法）

### 🟡 P2：集群流控（与秒杀集群化配套）
- **秒杀集群化（10007+10017 双实例）后启用**：
  - token server 统一计数，多实例共享 QPS 配额
  - 否则每实例各自 QPS=10 → 集群总量 20 失真
- 价值：集群化后限流准确（解 Q1 提到的"副本后 QPS 翻倍"的坑）
- 前置：[[集群化与配置中心迁移方案]] 阶段 B 实施后
- 成本：中高（需要 token server 部署 + 规则改造）

### ⚪ P3：暂不推荐（有触发条件再上）
| 能力 | 暂缓原因 | 触发条件 |
|---|---|---|
| 系统自适应保护 | 服务器 CPU <5%，无被打垮风险 | 上生产/有真实流量 |
| 授权规则 | Gateway 路由 + Spring Security 已挡内部接口 | 有真实安全需求 |

---

## 三、实施步骤（按优先级）

### P0：补齐接口规则
1. Nacos 建 3 条 flow 规则：
   - `新增订单`（QPS=20，快速失败）
   - `支付订单`（QPS=20，快速失败）
   - `adminLogin`（QPS=10，快速失败）
2. mall-order / mall-sso 的 application-prod.yml 加 Sentinel datasource（flow）
   （现只有 mall-seckill 配了 datasource）
3. 验证：压测触发限流 → blockHandler 生效返回友好提示

### P1：热点参数限流
1. 秒杀相关接口（商品详情/下单）加 ParamFlowRule：
   - 参数索引 = spuId
   - 爆款阈值 = 100，普通 = 1000
2. Nacos 建 `mall-seckill-hot-param-rules` dataId
3. 验证：压测爆款 spuId 限流生效、普通 spuId 不受影响

### P2：集群流控（集群化后）
1. 部署 token server（sentinel-token-server 独立服务）
2. 双实例配置 token-server 地址
3. 规则改集群模式（clusterMode=true）
4. 验证：两实例共享配额，总量 10 不翻倍

---

## 四、回滚与风险

| 风险 | 等级 | 缓解 |
|---|---|---|
| P0 规则太严误伤正常请求 | 低 | 阈值保守 + 控制台实时观察 QPS |
| P1 热点参数配置错误 | 低 | 先在测试环境验证 spuId 参数索引 |
| P2 token server 单点 | 中 | 集群流控仅在集群化方案内实施，可回退单机模式 |

---

## 五、执行清单（待实施）

- [ ] P0：Nacos 建新增订单/支付订单/adminLogin 三条 QPS 规则
- [ ] P0：order/sso 加 Sentinel datasource 配置
- [ ] P0：压测验证三接口限流生效
- [ ] P1：秒杀热点参数限流（ParamFlowRule + Nacos 规则）
- [ ] P1：压测验证爆款/普通商品差异化限流
- [ ] P2：秒杀集群化后部署 token server + 集群流控
- [ ] P2：验证双实例共享配额
