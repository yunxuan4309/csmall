# Dubbo 超时与 ES 同步修复 — AI 导购与推荐商品恢复

> 日期：2026-08-01
> 影响功能：AI 智能导购、商品搜索、相关商品推荐（"看了又看"）
> 根因：SkyWalking Agent 加重 Dubbo 调用延迟 + ES 索引损坏

---

## 一、现象

部署 SkyWalking + Debian 基础镜像后：
1. AI 导购对话正常但**不推荐商品**（无商品卡片）
2. 商品详情页"看了又看"区域**完全消失**
3. 本地正常，仅服务器有问题

## 二、排查

### 2.1 AI 服务同步日志

```bash
docker logs csmall-ai 2>&1 | grep "同步"
```

输出：
```
启动自动同步：开始同步商品数据到 ES...
启动自动同步失败，RAG 问答可能无数据
```

错误详情：
```
org.apache.dubbo.remoting.TimeoutException: Waiting server-side response timeout
timeout: 1000 ms
method: getSpuByPage
provider: mall-product
```

### 2.2 根因链

```
SkyWalking Agent 给 Dubbo 调用插入拦截器
  → 每次 RPC 调用额外耗时增加
  → mall-ai Dubbo consumer.timeout 默认 1000ms
  → 启动时 syncAll() → Dubbo 调 mall-product → 1000ms 超时
  → ES 索引无数据
  → AI 搜索 → ES 召回 0 条 → 无商品推荐
  → /ai/product/{id}/related → more_like_this → 无相关商品
```

### 2.3 ES 索引二次损坏

之前 `docker compose down` 全量停止时暴力关 ES，导致 `cool_shark_mall_ai` 索引分片损坏（`no_shard_available_action_exception`），即使 Dubbo 调用成功也无法写入。

## 三、修复

### 3.1 Dubbo 超时

`mall-ai application-prod.yml`：

```yaml
dubbo:
  consumer:
    timeout: 10000   # 默认 1000ms → 10000ms，给 SkyWalking 拦截器留余量
```

### 3.2 ES 索引重建

```bash
docker exec csmall-es curl -s -X DELETE "http://localhost:9200/cool_shark_mall_ai"
curl -s -X POST http://8.156.77.197/ai/sync -H "Authorization: Bearer xxx"
```

## 四、验证

```
同步完成，共 20 条商品
```

AI 导购推荐商品 + "看了又看" 恢复。

## 五、经验

1. SkyWalking Agent 会拦截 Dubbo 调用（`InstMethodsInter`），增加延迟。默认 1000ms 超时在 Agent 环境不够
2. `docker compose down` 暴力停 ES 会导致索引损坏，生产环境应先 flush 或设 `stop_grace_period`
3. 启动依赖链：mall-ai 需要在 mall-product 就绪后才能成功同步 ES
