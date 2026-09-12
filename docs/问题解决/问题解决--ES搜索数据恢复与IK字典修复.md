# 问题解决 — ES 搜索数据恢复与 IK 字典修复

> ⚠️ **2026-09-12 重要更正（③）**：本文修复的对象（**自建索引 `cool_shark_mall_index2`** + 新增并白名单放行 `/search/sync`）**已被 #33（2026-09-08「搜索双索引与降级分层」）整体废除** —— 那个索引后续已被删除。**照本文操作会走回废弃方案**；现行形态见 [[问题解决--搜索双索引与降级分层]]。本文仅作历史排障记录保留。

> **日期**: 2026-08-04 ~ 2026-08-05
> **服务器**: 8.156.77.197 (阿里云 ECS, Docker Compose)
> **严重程度**: 🔴 P0（前端搜索卡死无响应）
> **关联**: [[问题解决--ES集群Red与IK分词器丢失]]（IK 插件丢失是根因）、[[JVM调优方案]]（同日调优）

---

## 一、故障全貌

用户在前端搜索框输入关键词后页面**卡死**。排查链路如下：

```
前端搜索框输入"手机"
  → Gateway → mall-search:10008
    → 查询 ES cool_shark_mall_index2
      → ES 返回 503 no_shard_available_action_exception
        → 前端卡死
```

根因有三层，逐层剥开才完全修复。

---

## 二、排查过程

### 第一层：ES 集群 Red

```bash
curl -s http://localhost:9200/_cluster/health?pretty
```
```json
{ "status": "red", "unassigned_shards": 13, "active_shards_percent": 84.7 }
```

关键故障分片：
```
cool_shark_mall_index2  0  p  UNASSIGNED  ALLOCATION_FAILED
```

**修复**: 安装 IK 插件 → 重试分片分配 → 设置 replicas=0 → cluster green

> 详见 `问题解决--ES集群Red与IK分词器丢失.md`

### 第二层：ES 索引为空

集群恢复 green 后，`cool_shark_mall_index2` 文档数为 **0**。尝试通过 `/ai/sync` 同步数据，但该接口同步的是 `cool_shark_mall_ai`（AI 索引），不是搜索用的 `cool_shark_mall_index2`。

**根因**: `SearchServiceImpl.loadSpuByPage()` 方法（Dubbo 读 MySQL → 写入 ES）存在，但**从未暴露为 REST 端点**，无人能触发。

**修复**:

1. `SearchController.java` 新增同步端点：
```java
@GetMapping("/sync")
@ApiOperation("从数据库同步全部商品到 ES")
public JsonResult<String> syncAll() {
    searchService.loadSpuByPage();
    return JsonResult.ok("同步完成");
}
```

2. `ResourceWebSecurityConfiguration.java` 白名单放行：
```java
.requestMatchers("/",
        "/favicon.ico",
        "/error",
        "/swagger-resources/**",
        "/v2/api-docs/**",
        "/v3/api-docs/**",
        "/doc.html",
        "/search/sync").permitAll()  // ← 新增
```

3. 编包部署：`mvn package` → `scp` → `docker compose up -d --build`

### 第三层：IK 字典缺失导致写入失败

端点可访问后，触发同步返回 500：
```
Bulk operation has failures.
Cannot invoke "DictSegment.match(char[], int, int)" 
because "Dictionary.singleton._StopWords" is null
```

**根因**: ES 容器中 `plugins/analysis-ik/config/` 目录不存在。构成 IK 分词器的三部分：
1. ✅ `plugin-descriptor.properties` — 有（通过 `elasticsearch-plugin install` 安装）
2. ✅ Java JAR 包 — 有（同安装）
3. ❌ **字典文件**（`main.dic`、`stopword.dic`、`IKAnalyzer.cfg.xml` 等）— 全部缺失

`elasticsearch-plugin install` 只安装了 JAR 和 descriptor，但新版 IK 插件的字典文件需要**单独拷贝**。

**修复**:

从本地正常工作的 ES 8.6.0 拷贝完整 config 目录（8.44MB，含 main.dic 5MB、stopword.dic 等 8 个文件）：

```bash
# 本地 → 服务器
scp -r plugins/analysis-ik/config <AI账号>@8.156.77.197:/home/<AI账号>/ik-config

# 服务器上
sudo cp -r /home/<AI账号>/ik-config /tmp/ik-config
docker cp /tmp/ik-config/. csmall-es:/usr/share/elasticsearch/plugins/analysis-ik/config/
sudo cp -r /tmp/ik-config /data/csmall/es-plugins/analysis-ik/config  # 持久化
docker restart csmall-es
```

---

## 三、根因总结

```
ES 集群 Red 的根本原因: IK 插件丢失
  ├── 2026-07-31 docker compose up -d 重建了 ES 容器
  ├── IK 是手动装的，容器重建后消失
  ├── cool_shark_mall_index2 的 mapping 依赖 ik_max_word
  └── ES 无法加载分片 → 集群 red

ES 索引为空的根本原因: 数据从未被同步
  ├── SearchServiceImpl.loadSpuByPage() 存在但无 REST 端点
  └── SecurityConfig 拦截了未经认证的请求

IK 字典缺失的根本原因: elasticsearch-plugin install 不安装字典
  ├── plugin install 只装 JAR + descriptor
  └── config/ 目录（main.dic、stopword.dic 等）需手动拷贝
```

### 为什么之前能工作？

2026-07-31 之前，有人（或脚本）手动安装了 IK 插件**并拷贝了字典文件**。ES 重建后，除了 `es_data` volume 外的一切都丢失了。

---

## 四、完整修复清单

| 步骤 | 操作 | 状态 |
|------|------|------|
| 1 | 上传 elasticsearch-analysis-ik-8.6.0.zip 到服务器 | ✅ |
| 2 | 安装 IK 插件（`plugin install file://`） | ✅ |
| 3 | 从本地拷贝 config/ 字典文件 | ✅ |
| 4 | 重试分片分配（`_cluster/reroute?retry_failed=true`） | ✅ |
| 5 | 设置所有索引 replicas=0（单节点适配） | ✅ |
| 6 | SearchController 新增 `/search/sync` 端点 | ✅ |
| 7 | SecurityConfig 白名单 `/search/sync` | ✅ |
| 8 | 编包、上传、重建 mall-search 容器 | ✅ |
| 9 | 触发同步：`curl localhost:10008/search/sync` → 18 条商品入库 | ✅ |
| 10 | IK 插件持久化（es-plugins 目录挂载） | ✅ |
| 11 | RabbitMQ 健康检查超时 5s→10s | ✅ |

---

## 五、验证结果

```
ES 集群:   green, 75 shards, 0 unassigned, 100%
搜索索引:   18 条商品
IK 分词:    华为手机 → [华为, 手机]
前端:       HTTP 200
搜索功能:   输入"华为" → 返回 3 条结果 ✅
```

---

## 六、经验教训

| # | 教训 |
|---|------|
| 1 | **Docker 容器内手动安装的插件必须在 compose 中挂载**，否则重建即丢失 |
| 2 | **ES 插件 = JAR + descriptor + 字典文件** 三件套，缺一不可 |
| 3 | **`elasticsearch-plugin install` 不会安装 config/ 字典目录**，需自查 |
| 4 | **排查 503 时先查 `_cluster/allocation/explain`**，一次拿到完整堆栈 |
| 5 | **同步端点需要有 REST 接口暴露**，`loadSpuByPage()` 写了但没暴露等于没写 |
