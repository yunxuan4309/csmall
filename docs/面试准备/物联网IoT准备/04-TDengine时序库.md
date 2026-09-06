# 04 TDengine 时序数据库

> **核心问题**：为什么换 TDengine？
> 时序库和关系库区别？迁移方案？
> 难度：中-高

---

### Q1. 为什么把流水数据从 MySQL 迁到 TDengine？🔴

【追问1】MySQL 瓶颈？
【追问2】为什么不用其他时序库（InfluxDB）？

【简答】
接入真实硬件后，遥测数据频率从每 5 分钟提升到每 10-30 秒，
MySQL 写入和存储成瓶颈。TDengine 专为时序设计，写入快、
压缩率高、按时间聚合。

【深挖】
- **MySQL 瓶颈**：
  - 写入：每 10-30 秒一条，多设备并发，MySQL 写入压力大
  - 存储：时序数据持续增长，MySQL 存储膨胀
  - 查询：按时间范围聚合查询，MySQL 慢
- **TDengine 优势**：
  - **写入快**：时序优化，秒级批量写入
  - **压缩率高**：列式 + 时间有序，存储省
  - **查询高效**：按时间窗口聚合，内置函数
- **vs InfluxDB**：国产、社区、和 EMQX 
  规则引擎配合好；InfluxDB 也常用但当时选型 
  TDengine
- 面试话术："**数据特征决定存储选型**——遥测是'持续产生、
  按时间查'的时序数据，MySQL 是通用关系库不适合。
  TDengine 专为时序设计，写入压缩查询都优化，
  这是'选对工具'的思维"

---

### Q2. 超级表/子表模型怎么理解？🔴

【追问1】STable 和 Table 区别？
【追问2】TAGS 有什么用？

【简答】
超级表（STable）定义 schema（列结构 + TAGS 
设备属性），每设备建一个子表（`t_SL_001`），继承超级表。
查询支持按 tag 过滤。

【深挖】
- **超级表**：`CREATE STABLE telemetry 
  (ts TIMESTAMP, lux INT, ...
  ) TAGS 
  (device_id VARCHAR(50))`
- **子表**：每设备 `CREATE TABLE t_SL_001 
  USING telemetry TAGS ('SL_001')`
- **好处**：
  - 统一 schema，设备多了自动分表
  - 按 tag（device_id）查询/聚合
  - 数据按时间 + 设备组织，存储高效
- **面试话术**："超级表是**模板**，
  子表是**每设备的实例**——设备越多子表越多，但都继承同一个 
  schema，查询按设备 tag 过滤。这比 MySQL 
  每设备一张表灵活"

---

### Q3. TDengine 的写入方式？EMQX 直写？🔴

【追问1】规则引擎怎么写？
【追问2】绕过后端的好处？

【简答】
EMQX 规则引擎把 MQTT 消息直接写入 TDengine，
绕过后端 Spring Boot。好处是高频数据不经过应用层，
减少链路和资源消耗。

【深挖】
- **规则引擎配置**：EMQX 里建 SQL 规则，匹配 topic 
  → 转成 TDengine 写入语句
- **绕过后端**：设备 → EMQX → 规则引擎 → 
  TDengine，后端不参与写入
- **好处**：① 链路短（少一跳）；② 后端不背写入压力；③ 
  数据实时入库
- **坑**（历史）：EMQX 规则引擎 `payload.
  deviceId` 为 null（JSON 解析 bug），用 
  `nth(2, split(topic, '/'))` 从 
  Topic 提取
- 面试话术："**EMQX 规则引擎直写 TDengine** 
  让高频遥测绕过应用层——后端只做业务，
  数据管道在中间件层闭环。这是 
  IoT 架构的常见优化"

---

### Q4. 后端怎么读 TDengine？和 MyBatis-Plus 兼容吗？

【追问1】DAO 层？
【追问2】分页查询？

【简答】
后端用 TdengineTemplate（JDBC 封装）操作 
TDengine，手写 SQL（INSERT/查询/分页）。和 
MyBatis-Plus 不兼容（TDengine 不支持标准 
MySQL 语法），所以单独封装。

【深挖】
- **TdengineTemplate**：通用 JDBC 操作模板（
  insert、query、分页）
- **DAO**：TelemetryDao、
  VisionEventDao、VoiceEventDao
- **查询**：手写 SQL，按时间范围 + 设备过滤，分页查询
- **和 MyBatis-Plus 的区别**：TDengine 的 
  SQL 方言和 MySQL 不同，
  MyBatis-Plus 不兼容，
  用 JDBC 模板
- 面试话术："TDengine 有自己的 SQL 方言，
  **MyBatis-Plus 不适用**——我封装了 
  TdengineTemplate 做 JDBC 操作，
  这是适配新存储的常见做法（抽象一层 DAO）"

---

### Q5. 迁移后前端要改吗？

【追问1】接口变化？
【追问2】实体变化？

【简答】
前端零改动——Controller 返回结构（
Result<IPage<Entity>>）不变，实体字段不变，
接口路径不变。只是数据源换了。

【深挖】
- **不变**：前端代码、接口路径、返回结构、实体字段
- **变**：后端 DAO 层（MySQL Mapper → 
  TDengine DAO）
- **好处**：迁移对上层透明，前端无感
- 面试话术："迁移设计的关键是**接口稳定**——前端只认接口，
  不认存储。后端把 DAO 层换了，上层 
  Service/Controller 不动，前端零改动。
  这就是抽象的价值"

---

### Q6. 时序数据怎么查询？常见场景？

【追问1】历史遥测？
【追问2】聚合统计？

【简答】
场景：查设备历史遥测（时间范围）、健康评分（通信质量）、能耗统计。
TDengine 按时间窗口聚合（AVG/MAX/MIN）高效。

【深挖】
- **历史遥测**：`SELECT * FROM t_SL_001 
  WHERE ts BETWEEN ...`（按设备 + 时间）
- **健康评分通信质量**：查遥测频率/延迟
- **聚合**：按小时/天聚合（`INTERVAL` 窗口），能耗、趋势
- **面试话术**：
  "时序查询的核心是**时间窗口聚合**——'这个灯最近一小时的照度均值'用
  TDengine 的 INTERVAL 窗口一条 SQL 搞定，
  MySQL 要写复杂子查询"

---

### Q7. TDengine 部署在哪？和 MySQL 共存？

【追问1】容器部署？
【追问2】数据持久化？

【简答】
TDengine 和 MySQL 共存，TDengine 
专门存时序流水数据，MySQL 存业务主数据（设备、控制指令、告警）。
都用 Docker 部署。

【深挖】
- **分工**：
  | 数据 | 存储 |
  |------|------|
  | 遥测/视觉/语音流水 | TDengine |
  | 设备/控制/告警/策略/权限 | MySQL |
- **部署**：Docker 容器，数据卷持久化
- **面试话术**："**关系数据和时序数据分开存**——MySQL 
  管'现在是什么状态'，TDengine 管'过去发生了什么'。
  混合存储是 IoT 系统的常见架构"

---

### Q8. TDengine 和 EMQX 配置遇到过什么坑？

【追问1】规则引擎？
【追问2】连接配置？

【简答】
坑：EMQX 规则引擎 JSON 解析 bug（payload.
deviceId 为 null）、TDengine 连接配置、
容器重建后规则丢失。

【深挖】
- **payload.deviceId null**：EMQX 6.2.
  1 JSON 解析 bug，用 `nth(2, 
  split(topic, '/'))` 从 Topic 提取
- **容器重建**：TDengine/EMQX 容器重建后规则引擎、
  配置丢失 → 要持久化/重建规则
- **连接配置**：TDengine JDBC 驱动、
  REST/TAOS 连接参数
- 面试话术："TDengine + EMQX 
  的坑主要是**规则引擎和连接配置**——JSON 解析 
  bug 用 
  Topic 提取绕过，容器重建要重建规则。
  这些是中间件集成的真实经验"

---

### Q9. 为什么没把 device 主数据也迁到 TDengine？

【追问1】主数据和时序的区别？
【追问2】什么时候该迁？

【简答】
device 是主数据（设备台账，更新频率低），适合 MySQL。
时序迁移只针对高频流水数据（遥测/视觉/语音）。

【深挖】
- **主数据 vs 时序**：主数据（设备信息）低频更新，
  需要事务/外键/复杂查询 → MySQL；时序（遥测）高频追加，
  按时间查 → TDengine
- **什么时候该迁**：数据"持续产生 + 按时间查 + 
  量大"就考虑时序库；"低频更新 + 业务关系复杂"留关系库
- 面试话术："**不是所有数据都该进时序库**——设备台账要事务要关系，
  留 MySQL；只有高频流水（遥测/视觉/语音）进 
  TDengine。
  这是按数据特征做存储分层"

---

### Q10. 时序库和关系库你怎么选？（总结）🔴

【追问1】判断标准？
【追问2】反例？

【简答】
判断标准：写入频率、是否按时间查询、数据增长速度、是否需要事务/关系。
高频追加 + 时间查询 → 时序库；低频 + 强关系 → 关系库。

【深挖】
- **选时序库**：写入频率高、按时间范围/聚合查、数据持续增长
- **选关系库**：需要事务、外键关联、复杂业务查询、数据低频更新
- **反例**：订单不该进时序库（低频 + 强事务）；遥测不该进 
  MySQL（高频 + 时间查询）
- 面试话术：
  "存储选型看**数据访问模式**——'怎么写怎么读'决定了用什么库。
  我在 IoT 项目里用 MySQL + TDengine 混合，
  各存各擅长的，这就是数据分层的思想"
