# 01 设备 MQTT 鉴权（核心亮点）

> **核心问题**：为什么设备要独立鉴权？怎么实现的？EMQX 
> 认证/ACL 原理？
> 难度：高（IoT 项目最核心的差异化亮点）

---

### Q1. 为什么设备需要独立鉴权？之前有什么问题？🔴

【追问1】没鉴权前是什么状态？
【追问2】风险是什么？

【简答】
之前所有设备共用 `backend/123456` 账号，无 
Topic 隔离，明文传输——任一泄露影响全局，设备可伪装其他设备。

【深挖】
- **改造前的问题**（文档记录）：
  | 问题 | 风险 |
  |------|------|
  | 设备无独立身份（共用 backend 账号） | 任一泄露影响全局 |
  | 无 Topic 隔离 | 任意客户端可发布到任意设备 Topic，设备可伪装 |
  | MQTT 明文传输（tcp://1883） | 网络嗅探直接获取密码 |
- **改造目标**（5 点）：
  1. 每设备独立凭据：用户名 = deviceId，密码 = 
     出厂编号 + 识别码
  2. BCrypt 哈希存储（数据库拖库无法还原）
  3. TLS 传输加密（ssl://8883）
  4. Topic ACL 隔离（设备只能操作自己的 
     `streetlight/{deviceId}/#`）
  5. 不影响现有功能（backend 服务账号保留）
- 面试话术：
  "物联网和普通后端最大的区别是**设备是物理资产**——设备伪装、
  密码泄露可能影响真实路灯。所以每台设备独立身份 + 加密 + 
  隔离是必须的"

---

### Q2. 设备鉴权整体架构怎么设计的？🔴

【追问1】认证链？
【追问2】ACL 怎么隔离？

【简答】
EMQX 6.2.1 配置认证链：built_in_database（
backend 服务账号）→ MySQL（设备凭证），BCrypt 
验证。ACL 用 `${username}` 变量做 
Topic 隔离。

【深挖】
- **认证链**（EMQX 原生支持多认证器串联）：
  ```
  客户端 CONNECT
    → 认证器1: built_in_database（backend 服务账号 → allow）
    → 认证器2: MySQL（device_credential 表 → BCrypt 验证）
  ```
- **设备凭证表**：`device_credential`（
  username、passwordHash BCrypt、
  factorySerialEncrypted AES、
  deviceIdCodeEncrypted）
- **ACL 隔离**：设备只能访问 
  `streetlight/${username}/#`（
  `${username}` = deviceId），
  `streetlight/SL_001/telemetry`、
  `command`、`config`
- **后端不受影响**：backend 服务账号在 built_in 
  认证器，订阅/发布权限保留
- 面试话术："设备鉴权用**认证链**——backend 服务走内置库，
  设备走 MySQL，两层认证器串联；ACL 用 
  `${username}` 占位符动态生成每个设备的 
  Topic 白名单"

---

### Q3. 密码怎么存储的？为什么 BCrypt + AES 双加密？🔴

【追问1】BCrypt 和 AES 的区别？
【追问2】为什么出厂编号要可逆加密？

【简答】
设备密码用 BCrypt 哈希存储（不可逆，防拖库）；
但出厂编号/识别码用 AES-256-CBC 可逆加密（
运维需要查看原始值）。

【深挖】
- **双加密设计**：
  | 数据 | 方式 | 原因 |
  |------|------|------|
  | 密码（passwordHash） | BCrypt 哈希 | 不可逆，登录验证用，泄露安全 |
  | 出厂编号/识别码 | AES-256-CBC | 可逆，运维要查原始密码/重建设备 |
- **AES-256-CBC 细节**：
  `AES/CBC/PKCS5Padding`，随机 IV，输出 
  `Base64(IV + 密文)`，AesUtil 封装 
  encrypt/decrypt
- **为什么可逆**：BCrypt 不可逆，
  但运维可能要根据出厂编号重建密码/重连设备，所以出厂编号用 
  AES 可逆存储
- 面试话术："**BCrypt 和 AES 
  用途不同**——密码只需要验证，用不可逆的 BCrypt；
  出厂编号运维要查看原始值，用可逆的 AES。按数据用途选加密方式"

---

### Q4. 凭证怎么和 EMQX 同步？生命周期怎么管理？🔴

【追问1】创建设备时？
【追问2】删除/更新设备时？

【简答】
设备创建时自动生成凭证并同步 EMQX；更新识别码时同步更新 
BCrypt + EMQX 密码；删除设备时同步清理 EMQX 用户。

【深挖】
- **创建**：`DeviceCredentialService.
  createCredential()` → 生成 
  username/password → 存 MySQL + 调 
  EMQX HTTP API 创建 built_in 用户
- **存量初始化**：
  `DeviceCredentialInitializer`（
  CommandLineRunner）启动时自动为存量设备补发凭证
- **更新**：`updateCredential()` 同步更新 
  MySQL BCrypt 哈希 + EMQX 
  built_in 密码
- **删除**：`deleteByDeviceId()` 删除 
  MySQL 记录 + 调 EMQX API 删用户
- **EMQX API**：`EMQX_API/login`、
  `/authentication/password_based:
  built_in_database/users/...`
- 面试话术：
  "凭证生命周期和**设备生命周期**一致——创建/更新/删除设备都自动同步到
  EMQX，通过 EMQX 的 HTTP API 管理 
  built_in 用户，保证 MySQL 和 EMQX 
  的数据一致"

---

### Q5. 遇到过的设备鉴权问题？（连环坑）🔴

【追问1】最致命的？
【追问2】怎么排查的？

【简答】
10 个问题连环踩：SSL 端口未开放、Docker 端口映射缺失、
容器重建配置丢失、Java SSL 证书验证失败、EMQX 
MySQL 认证 Prepared Statement bug。

【深挖】
- **完整问题清单**（文档记录 10 个）：
  | # | 问题 | 类型 |
  |---|------|------|
  | 1 | SSL 8883 未在安全组开放 → 连接拒绝 | 服务器配置 |
  | 2 | Docker 未映射 8883/8084 端口 | 服务器配置 |
  | 3 | 重建 EMQX 挂载空目录 → 配置丢失 | 操作失误 |
  | 4 | EMQX 重建后认证器/ACL/规则全丢 | 操作影响 |
  | 5 | Java SSL hostname 验证失败（自签名无 IP SAN） | 代码 |
  | 6 | device_credential 表结构不一致 | 数据库 |
  | 7 | EMQX MySQL 认证 Prepared Statement + caching_sha2_password 不兼容 | **EMQX Bug** |
  | 8 | disable_prepared_statements 设置不生效 | **EMQX Bug** |
  | 9 | 前端 KeepAlive 缓存白屏 | 前端 |
  | 10 | system/alarms 发布超时（单线程阻塞） | 代码 |
- **关键教训**：
  - SSL 就是 TCP 加密流量，安全组放行 TCP 端口即可
  - 安全组 + Docker 端口映射**两层都要检查**
  - EMQX 认证用 caching_sha2_password 
    密码格式不兼容，要处理
- 面试话术："设备鉴权踩了 **10 个连环坑**——从服务器端口到 
  Docker 映射到 EMQX bug。
  最有价值的是我学会了**分层排查**：安全组 → 
  Docker → 
  应用 → 中间件，每层都可能出问题"

---

### Q6. 为什么用 EMQX？不用其他 MQTT broker？🔴

【追问1】EMQX 特点？
【追问2】和 Mosquitto 等区别？

【简答】
EMQX 是国产开源 MQTT broker，支持海量连接、
内置认证/ACL/规则引擎、可视化 Dashboard，企业版功能全。

【深挖】
- **EMQX 特点**：百万级连接、支持 MQTT 3.1/5.0、
  内置认证链 + ACL + 规则引擎（直接转发数据到 
  TDengine）、Dashboard 可视化管理
- **vs Mosquitto**：Mosquitto 轻量但功能少（
  无内置规则引擎/认证链），EMQX 功能全适合生产
- **vs RabbitMQ MQTT**：RabbitMQ 有 
  MQTT 插件但核心是 AMQP，IoT 场景 EMQX 更专
- **你的场景**：用 EMQX 6.2.1 Enterprise，
  认证链（built_in + MySQL）+ ACL 
+ 规则引擎（
  数据直写 TDengine）
- 面试话术："选 EMQX 因为它**为 IoT 
  而生**——海量设备连接 + 内置认证/ACL/规则引擎，一个 
  broker 全搞定。RabbitMQ 做业务消息，EMQX 
  做设备消息，各司其职"

---

### Q7. TLS/SSL 传输加密怎么做的？

【追问1】为什么需要？
【追问2】Java 端怎么配？

【简答】
设备 → EMQX 走 ssl://8883 加密通道，防止网络嗅探。
Java 端 Paho 客户端配置 trustAll + 
disableHostnameVerification（
自签名证书场景）。

【深挖】
- **为什么**：MQTT 明文传输，密码会被嗅探（改造前 tcp:
  //1883）
- **EMQX 侧**：SSL 监听器 8883 已配置证书，WSS 
  8084 给前端
- **Java 端**：Paho 客户端连 `ssl://`，
  因自签名证书无 IP SAN，配 trustAll + 
  disableHostnameVerification
- **坑**：自签名证书 hostname 验证失败（问题5）
  ——要么证书加 IP SAN，要么客户端关闭校验（开发场景）
- 面试话术："TLS 
  加密是**传输层安全**——即使被嗅探也拿不到密码。
  开发环境自签名证书要处理 hostname 验证，生产应该用正规 
  CA 证书"

---

### Q8. 设备怎么连接？MQTT 发布/订阅流程？

【追问1】设备发什么？收什么？
【追问2】Topic 设计？

【简答】
设备通过 MQTT 连接 EMQX，发布遥测数据到 
`streetlight/{deviceId}/telemetry`，
订阅 `command`/`config` 收控制指令。后端 
MqttSubscriber 订阅遥测处理。

【深挖】
- **Topic 设计**：
  ```
  streetlight/{deviceId}/telemetry  ← 设备发布遥测（光照/温湿度/PM2.5）
  streetlight/{deviceId}/command    ← 设备订阅控制指令
  streetlight/{deviceId}/config     ← 设备订阅配置
  system/alarms                     ← 系统告警
  ```
- **设备侧**：小熊派 BearPi 通过 MQTT 连 EMQX，
  发布遥测 + 订阅指令
- **后端侧**：MqttSubscriber 订阅遥测 topic，
  写入处理 + 触发决策引擎
- **模拟器**：MockDataGenerator 模拟设备数据（
  独立连接，每设备用自己的凭证）
- 面试话术："Topic 是 MQTT 的**路由核心**——按 
  `设备ID/数据类型` 分层设计，ACL 保证设备只能碰自己的，
  后端订阅处理，前端通过 WSS 看实时数据"

---

### Q9. 后端怎么和 MQTT 交互？

【追问1】订阅/发布组件？
【追问2】线程模型？

【简答】
后端有 MqttPublisher（发布）、
MqttSubscriber（订阅）、
SystemEventPublisher（系统事件）。
遥测处理用独立线程池，避免阻塞。

【深挖】
- **组件**：
  - MqttPublisher：下发控制指令（开/关/调光）
  - MqttSubscriber：接收遥测数据，处理 + 触发决策引擎
  - SystemEventPublisher：系统事件（告警）
- **优化**：遥测处理用**独立线程池**；
  MqttPublisher 从 SingleThread → 
  CachedThreadPool（解决 
  system/alarms 超时）
- **面试话术**："MQTT 交互的核心是**异步 + 
  线程池**——设备数据量大且持续，用独立线程池处理遥测，
  发布用缓存线程池避免单线程阻塞（这个我踩过 
  system/alarms 超时的坑）"

---

### Q10. 设备鉴权这个功能你最有成就感的地方？

【追问1】技术难点？
【追问2】业务价值？

【简答】
从"所有设备共用密码"到"每设备独立凭证 + TLS + ACL 
隔离"的完整安全体系，且解决了 10 个连环坑，接入了真实硬件验证。

【深挖】
- **技术深度**：EMQX 认证链、ACL 变量、BCrypt + 
  AES 双加密、EMQX HTTP API 管理
- **业务价值**：真实路灯设备的安全接入——防止设备伪装、
  密码泄露、数据被窃听
- **真实硬件验证**：小熊派 BearPi 用独立凭证接入成功
- 面试话术：
  "最有成就感的是把'能用'升级到'安全'——每台真实路灯都有自己的身份和访问边界
  。这不是 Demo 功能，是真实硬件在用的安全体系"
