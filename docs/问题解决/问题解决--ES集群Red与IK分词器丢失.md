# 问题解决 — ES 集群 Red 与 IK 分词器丢失

> **发现日期**: 2026-08-04
> **服务器**: 8.156.77.197 (阿里云 ECS, Docker Compose)
> **严重程度**: 🔴 P0（导致前台搜索卡死）

---

## 一、故障现象

用户在前端搜索框输入关键词（如"手机"）进行搜索，页面**卡死无响应**。

服务器 ES 集群状态为 **red**，13 个分片未分配，其中业务索引 `cool_shark_mall_index2` 的主分片状态为 `ALLOCATION_FAILED`。

```bash
# 集群健康
curl -s http://localhost:9200/_cluster/health?pretty
```
```json
{
  "status": "red",
  "active_shards_percent_as_number": 84.7,
  "unassigned_shards": 13
}
```

```bash
# 问题分片
curl -s 'http://localhost:9200/_cat/shards?v' | grep cool_shark_mall_index2
```
```
cool_shark_mall_index2  0  p  UNASSIGNED  ALLOCATION_FAILED
cool_shark_mall_index2  0  r  UNASSIGNED  CLUSTER_RECOVERED
```

```bash
# 直接查询该索引 → 503
curl -s 'http://localhost:9200/cool_shark_mall_index2/_search'
```
```json
{
  "error": {
    "type": "search_phase_execution_exception",
    "reason": "all shards failed",
    "failed_shards": [{
      "reason": {
        "type": "no_shard_available_action_exception"
      }
    }]
  },
  "status": 503
}
```

---

## 二、排查路径

### Step 1：定位故障分片

```bash
# 列出所有未分配分片及原因
curl -s 'http://localhost:9200/_cat/shards?v&h=index,shard,prirep,state,unassigned.reason' \
  | grep UNASSIGNED
```

结果：13 个未分配分片中，12 个是 SkyWalking 索引的副本分片（单节点正常现象），**1 个是 `cool_shark_mall_index2` 的主分片**（异常）。

### Step 2：诊断主分片分配失败原因

```bash
curl -s 'http://localhost:9200/_cluster/allocation/explain?pretty' \
  -H 'Content-Type: application/json' \
  -d '{"index": "cool_shark_mall_index2", "shard": 0, "primary": true}'
```

返回的关键信息：

```json
{
  "current_state": "unassigned",
  "unassigned_info": {
    "reason": "ALLOCATION_FAILED",
    "at": "2026-07-31T15:57:56.718Z",
    "failed_allocation_attempts": 5,
    "details": "failed shard on node [...] failed to update mapping for index,
                failure MapperParsingException: Failed to parse mapping:
                analyzer [ik_max_word] has not been configured in mappings"
  },
  "can_allocate": "no",
  "allocate_explanation": "...max_retry decider: shard has exceeded the
    maximum number of retries [5] on failed allocation attempts..."
}
```

**根因确认**：`analyzer [ik_max_word] has not been configured in mappings`

### Step 3：排查为什么 IK 分词器不可用

```bash
# 检查 ES 已安装插件
docker exec csmall-es bin/elasticsearch-plugin list
# 输出：（空）— IK 分词器未安装！

# 检查 IK 分词器是否可用
curl -s 'http://localhost:9200/_analyze?pretty' \
  -H 'Content-Type: application/json' \
  -d '{"analyzer":"ik_max_word","text":"手机"}'
```
```json
{ "error": { "reason": "failed to find global analyzer [ik_max_word]" } }
```

### Step 4：查索引的 mapping 依赖

```bash
curl -s 'http://localhost:9200/cool_shark_mall_index2/_mapping?pretty'
```
```json
{
  "cool_shark_mall_index2": {
    "mappings": {
      "properties": {
        "name":          { "type": "text", "analyzer": "ik_max_word" },
        "title":         { "type": "text", "analyzer": "ik_max_word" },
        "description":   { "type": "text", "analyzer": "ik_max_word" },
        "category_name": { "type": "text", "analyzer": "ik_max_word" }
      }
    }
  }
}
```

4 个字段依赖 `ik_max_word` 分词器。该 mapping 来自 `mall-pojo` 中 `SpuForElastic.java` 的 `@Field(analyzer = "ik_max_word")` 注解。

### Step 5：检查 docker-compose.yml 是否持久化了插件

```bash
docker inspect csmall-es --format '{{range .Mounts}}{{.Destination}}{{"\n"}}{{end}}'
# 输出只有: /usr/share/elasticsearch/data
# 没有 /usr/share/elasticsearch/plugins 的挂载
```

**持久化缺失确认**：ES 容器只挂载了数据目录，plugins 目录是容器内的临时文件系统。

---

## 三、根因分析

```
初始状态（7月31日前）
  ES 容器首次创建 → 手动 docker exec 安装了 IK 分词器
  → cool_shark_mall_index2 正常创建（mapping 含 ik_max_word）
  → 搜索功能正常

7月31日 大版本部署
  docker compose up -d → ES 容器重建
  → 基础镜像 elasticsearch:8.6.0 不含 IK 插件
  → plugins 目录未挂载，之前安装的 IK 随旧容器销毁

容器重建后
  ES 启动 → 尝试加载 cool_shark_mall_index2 分片
  → mapping 要求 ik_max_word 分词器
  → 插件不存在 → MapperParsingException
  → ES 自动重试 5 次，全部失败
  → max_retry 规则阻止继续重试 → 分片永久 UNASSIGNED
  → 集群状态 red
  → 前端搜索请求 → mall-search → ES 返回 503 → 页面卡死
```

**影响范围**：
- 🔴 前台商品搜索：卡死（唯一受影响的用户功能）
- 🟢 商品列表/详情/秒杀/购物车/订单/支付/后台管理：全部正常（走 Dubbo → MySQL）
- 🟢 AI 智能导购：正常（使用 `cool_shark_mall_ai` 索引，standard 分词器，不依赖 IK）

---

## 四、解决方案

### 4.1 本地找到 IK 插件 zip

本地 ES 已安装 IK 分词器，插件 zip 位于：
```
D:\浏览器下载\elasticsearch-analysis-ik-8.6.0.zip
```

### 4.2 上传并安装 IK

```bash
# 1. 上传到服务器
scp elasticsearch-analysis-ik-8.6.0.zip <AI账号>@8.156.77.197:/tmp/

# 2. 复制到容器并安装
docker cp /tmp/elasticsearch-analysis-ik-8.6.0.zip csmall-es:/tmp/
docker exec csmall-es bin/elasticsearch-plugin install \
  file:///tmp/elasticsearch-analysis-ik-8.6.0.zip --batch

# 3. 重启 ES
docker restart csmall-es

# 4. 验证插件已加载
docker exec csmall-es bin/elasticsearch-plugin list
# → analysis-ik
```

### 4.3 强制重试分片分配

```bash
# 数据在磁盘上完好 (in_sync: true)，IK 已就绪，重试即可
curl -X POST "http://localhost:9200/_cluster/reroute?retry_failed=true"

# 将所有索引副本数改为 0（单节点不需要副本）
curl -X PUT "http://localhost:9200/_all/_settings" \
  -H 'Content-Type: application/json' \
  -d '{"index": {"number_of_replicas": 0}}'
```

### 4.4 验证恢复

```bash
curl -s http://localhost:9200/_cluster/health?pretty
# "status": "green"
# "unassigned_shards": 0
# "active_shards_percent_as_number": 100.0

curl -s 'http://localhost:9200/cool_shark_mall_index2/_search'
# 正常返回 200（不再是 503）
```

---

## 五、持久化修复（防止复发）

> ⚠️ 以上修复只在运行中的容器内生效。如果 ES 容器再次重建（`docker compose up -d`），IK 会再次丢失。

✅ **已落地（2026-09-12 复核）**：该挂载早已在 `deploy/docker/docker-compose.yml` 里（`/data/csmall/es-plugins/analysis-ik:/usr/share/elasticsearch/plugins/analysis-ik`），姊妹篇 [[问题解决--ES搜索数据恢复与IK字典修复]] §四 也已标 ✅ ⇒ 下面的操作步骤留作历史。（原文：~~需以 ecs-user 登录服务器执行~~）

```bash
# 1. 宿主机创建插件目录
sudo mkdir -p /data/csmall/es-plugins/analysis-ik
sudo docker cp csmall-es:/usr/share/elasticsearch/plugins/analysis-ik/. \
  /data/csmall/es-plugins/analysis-ik/
sudo chown -R 1000:1000 /data/csmall/es-plugins/

# 2. 在 docker-compose.yml 的 elasticsearch 服务下添加挂载
#   elasticsearch:
#     volumes:
#       - es_data:/usr/share/elasticsearch/data
#       - /data/csmall/es-plugins/analysis-ik:/usr/share/elasticsearch/plugins/analysis-ik  ← 新增

# 3. 重建 ES 容器
cd /data/csmall && docker compose up -d elasticsearch

# 4. 确认插件持久化成功
docker exec csmall-es bin/elasticsearch-plugin list
# → analysis-ik
```

---

## 六、经验教训

| # | 教训 | 适用场景 |
|---|------|---------|
| 1 | **Docker 容器内手动安装的软件不持久** — 必须在 docker-compose.yml 或 Dockerfile 中声明，或挂载到宿主机 | 所有依赖插件的中间件（ES 插件、Redis 模块等） |
| 2 | **`_cluster/allocation/explain` 是 ES 排障第一工具** — 不要止步于看 `_cat/shards` 的 ALLOCATION_FAILED，要拿到完整堆栈 | ES 分片问题 |
| 3 | **容器重建 = 全新环境** — 只有 volumes 挂载和镜像自带的内容会保留。每次 `docker compose up -d` 都可能重建容器 | 所有 Docker 部署 |
| 4 | **业务影响面评估要先做** — 哪些功能走 ES、哪些走 MySQL，提前搞清楚才能准确判断优先级 | 故障定级 |

---

## 七、关联文档

- [[服务器巡检与待修复问题清单-2026-08-04]] — 本次巡检发现的完整问题清单
- [[项目上下文文档]] — ES 版本、索引名称、mapping 定义
- 本地 ES 插件: `D:\浏览器下载\elasticsearch-analysis-ik-8.6.0.zip`（v8.6.0，用于服务器安装）

---

**修复日期**: 2026-08-04
**修复人**: AI 助手（Claude Code）+ 用户确认
