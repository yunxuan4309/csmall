# AI 导购模块部署问题 — 全链路排查与修复

> 排查日期：2026-07-31
> 服务器：阿里云 ECS 4C16G，Docker Compose 20 容器
> 涉及模块：mall-ai、mall-gateway、csmall-vue 前端

---

## 问题一：SSE 流式接口 403 Forbidden

**现象**：浏览器 POST `/ai/chat/stream` 返回 403，前端无法获取 AI 回复。

**根因**：`mall-ai` 模块的 `ResourceWebSecurityConfiguration` 中 CORS 配置只允许 `http://localhost:5173`，生产环境请求 Origin `http://8.156.77.197` 被拒绝。

**修复**：`ResourceWebSecurityConfiguration.java` — 增加生产 Origin。
```java
config.addAllowedOrigin("http://8.156.77.197");
```

同时修复 `AiController.java` 中 SSE 端点的 `@CrossOrigin` 注解。

---

## 问题二：DeepSeek API 401 Unauthorized

**现象**：`意图提取失败 → 401 Authorization Required`，最终所有 AI 调用失败，返回"抱歉，AI服务暂时不可用"。

**根因**：服务器 `.env` 中 `AI_API_KEY=sk-placeholder`（占位符），真实 Key 从未配置。且 `deploy/csmall.env` 中的 Key `sk-8f73ae...5a4c` 已过期。

**修复**：
1. 从 `deploy/systemd/csmall.env` 找到有效 Key `sk-0ac9a...2179`
2. 写入 `deploy/docker/.env` 并上传到服务器
3. 在 `deploy/csmall.env` 中注释过期 Key，标注日期

---

## 问题三：前端硬编码假回复"好的，已了解。"

**现象**：AI 未返回任何内容时，前端显示"好的，已了解。"，误导用户以为 AI 正常回复。

**根因**：`AIAssistant.vue` 和 `FloatingAI.vue` 的 `onDone()` 回调中：
```js
if (!messages.value[aiMsgIndex].text) {
    messages.value[aiMsgIndex].text = '好的，已了解。'
}
```
当 SSE 流收完 `done` 但未收到任何 `chunk` 时，填充了这条假回复。

**修复**：改为 `'AI 未返回有效内容，请重试或换个问法。'`，`onError` 回调改为显示后端发来的真实错误信息。

---

## 问题四：PreferenceExtractor 超时导致会话丢失

**现象**：第一轮对话正常，第二轮丢失上下文。日志显示 `偏好提取失败 → Read timed out`。

**根因**：`saveSession()` 方法先调 `preferenceExtractor.extract()`（调用 DeepSeek API，原超时 15s），再执行 `sessionManager.save()`。偏好提取超时后抛异常，`save()` 被跳过，会话未持久化到 Redis。

**修复**：
1. `ChatServiceImpl.saveSession()` — 重排顺序：先 `sessionManager.save()` 保消息，再 try-catch 尽力而为提取偏好
2. `AiProperties.timeout` — 默认值从 15000ms 提升到 60000ms

---

## 问题五：SSE 连接不释放导致后续请求堵塞

**现象**：第一次 AI 对话正常，第二次请求卡死。切换浏览器和账号均无效。

**根因**：`sendStream()` 方法中 `saveSession()`（含耗时偏好提取）在 `writeSSE("done")` 之前执行。阻塞期间 HTTP 连接未释放，Nginx → Gateway → AI 服务各层均等待响应完成，后续请求排队。

**修复**：`ChatServiceImpl.sendStream()` — `writeSSE("done")` + `outputStream.close()` 提前到 `saveSession()` 之前，先关 SSE 流释放连接，再后台保存会话。

---

## 问题六：Nacos 注册冲突 — Dubbo 与 Spring Cloud 同名

**现象**：Gateway 偶尔出现 `No servers available for service: mall-ai` 或 `Connection prematurely closed BEFORE response`，AI 功能随机不可用。

**根因**：`mall-ai` 同时通过 Spring Cloud（端口 10010，HTTP）和 Dubbo（端口 20880，RPC）注册到 Nacos，两个实例同名 `mall-ai`。Gateway 的 `lb://mall-ai` 负载均衡可能选中 Dubbo 端口，HTTP 连接失败。

**修复**：`application-prod.yml` 和 `application-test.yml` — Dubbo 应用名改为 `mall-ai-dubbo`，与 Spring Cloud 的服务名 `mall-ai` 分离。

---

## 前端修复清单

| 文件 | 问题 | 修复 |
|------|------|------|
| `api/ai.js` | `fetch()` 未加 `Bearer ` 前缀，SSOFilter 可能不识别 | 统一加前缀逻辑 |
| `AIAssistant.vue` | 假回复 + `\n` 转义未处理 + `onError` 丢弃真实报错 | 三条全修 |
| `FloatingAI.vue` | 同上 | 同上 |

## Nginx 修复

| 配置 | 修改 |
|------|------|
| SSE 路径 `proxy_read_timeout` | 默认 60s → 180s |
| SSE 路径 `proxy_send_timeout` | 新增 180s |

---

## 部署要点

1. **API Key 生命周期**：DeepSeek Key 可能过期，在 `.env` 中标注 Key 来源和更新时间
2. **Dubbo 服务名**：任何同时使用 Spring Cloud Gateway 路由 + Dubbo RPC 的模块，Dubbo 应用名必须与 `spring.application.name` 不同
3. **SSE 连接时序**：`done` 事件 + `outputStream.close()` 必须在任何阻塞操作之前
4. **前端 `fetch()` 与 axios**：`fetch()` 不会走 axios 拦截器，Token 格式需自行处理
