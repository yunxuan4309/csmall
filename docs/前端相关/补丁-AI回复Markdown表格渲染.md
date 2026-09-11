# 补丁：AI 回复的 Markdown 表格渲染（治本）

> **归属**: docs/前端相关/ ｜ **对应条目**: [[前端TODO]] **#F1** ｜ **所属方案**: [[H5优化方案]] §三 P0
> **执行前置**: 无（纯前端改动）｜ **部署后有回滚**: dist 备份换回即可

> **问题**：`formatMarkdown()` 只处理 **加粗 / # 标题 / 列表 / 换行**，**没有表格解析** →
> 模型输出的 GFM 管道表格（`|机型|价格|说明|`）被当纯文本显示，用户看到一堆竖线。
> **另一处不一致**：`FloatingAI.vue` 里的同名函数**连标题和列表都不支持**（只有加粗+换行），
> 所以悬浮窗里的 AI 回复更"素"。两处实现已经漂移，**本补丁统一为一份公共工具**。

---

## 一、新增公共工具 `src/utils/formatMarkdown.js`

```js
/**
 * 轻量 Markdown 渲染（AI 回复专用，避免为此引入 marked/DOMPurify 依赖）
 * 支持子集：**加粗** / *斜体* / `行内代码` / # ## ### 标题 / 有序·无序列表 / GFM 管道表格 / 换行
 * ⚠️ 安全：先转义 < > 再拼我们自己生成的标签；不渲染模型给的任何原始 HTML
 */
export function formatMarkdown(text) {
  if (!text) return ''
  // SSE 传输时换行被转义成字面量 \n，先还原
  let html = String(text).replace(/\\n/g, '\n')
  // ① 先转义 HTML —— 此后所有标签都由本函数生成
  html = html.replace(/</g, '&lt;').replace(/>/g, '&gt;')

  // ② 表格：把连续的 "| ... |" 行（含 |---|---| 分隔行）整体转成 <table>
  html = html.replace(/(?:^\|.*\|[ \t]*$\n?)+/gm, (block) => {
    const rows = block.trim().split('\n').map(r => r.trim()).filter(Boolean)
    if (rows.length < 2) return block
    const split = (r) => r.replace(/^\|/, '').replace(/\|$/, '').split('|').map(c => c.trim())
    const isSep = (r) => /-/.test(r) && /^\|?[\s:|-]+\|?$/.test(r)
    const head = split(rows[0])
    const body = rows.slice(1).filter(r => !isSep(r)).map(split)
    const th = head.map(c => `<th>${c}</th>`).join('')
    const tr = body.map(r => `<tr>${r.map(c => `<td>${c}</td>`).join('')}</tr>`).join('')
    return `<table><thead><tr>${th}</tr></thead><tbody>${tr}</tbody></table>`
  })

  // ③ 行内样式
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/`([^`\n]+)`/g, '<code>$1</code>')
  html = html.replace(/\*([^*\n]+)\*/g, '<em>$1</em>')
  html = html.replace(/\*\*/g, '')          // 清掉未闭合的残留星号
  html = html.replace(/(^|[^*])\*(?!\*)/g, '$1')

  // ④ 标题
  html = html.replace(/^### (.+)$/gm, '<h4>$1</h4>')
  html = html.replace(/^## (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^# (.+)$/gm, '<h2>$1</h2>')

  // ⑤ 列表：连续项包进 <ol>/<ul>（原实现只输出裸 <li>，语义不完整）
  html = html.replace(/(?:^\d+\. .+$\n?)+/gm, (block) => {
    const items = block.trim().split('\n')
      .map(l => `<li>${l.replace(/^\d+\.\s*/, '')}</li>`).join('')
    return `<ol>${items}</ol>`
  })
  html = html.replace(/(?:^[-*] .+$\n?)+/gm, (block) => {
    const items = block.trim().split('\n')
      .map(l => `<li>${l.replace(/^[-*]\s*/, '')}</li>`).join('')
    return `<ul>${items}</ul>`
  })

  // ⑥ 其余换行；并清掉块级元素前后多余的空行
  html = html.replace(/\n/g, '<br>')
  html = html.replace(/<br>\s*(<(?:table|ul|ol)>)/g, '$1')
  html = html.replace(/(<\/(?:table|ul|ol)>)\s*<br>/g, '$1')
  return html
}
```

---

## 二、两个组件改为引用公共工具

**`src/views/front/ai/AIAssistant.vue`**：删除本文件内的 `function formatMarkdown(text) {...}`（约 L389-410），
在 `<script setup>` 顶部加：

```js
import { formatMarkdown } from '@/utils/formatMarkdown'
```

**`src/components/common/FloatingAI.vue`**：同样删除本文件内的 `function formatMarkdown(text) {...}`（约 L409-422），加同一行 import。
> 注意：如果 `@` 别名没配，就用相对路径 `../../utils/formatMarkdown`（AIAssistant 用 `@/utils/...` 通常可，vite 里已配 `@` → `src`）。

---

## 三、CSS（两个组件的 `<style scoped>` 里各加一份；或放进全局样式表）

```css
.message-text strong { font-weight: 600; }
.message-text h2, .message-text h3, .message-text h4 {
  margin: 10px 0 6px; font-size: 15px; font-weight: 600; line-height: 1.4;
}
.message-text ul, .message-text ol { margin: 6px 0 6px 18px; padding-left: 0; }
.message-text li { margin: 3px 0; }
.message-text code {
  background: rgba(0, 0, 0, .06); padding: 1px 4px; border-radius: 4px; font-size: 12px;
}
/* ★ 表格：气泡里也能对齐、窄屏可横向滚动 */
.message-text table {
  border-collapse: collapse; width: 100%; margin: 8px 0; font-size: 13px;
  display: block; overflow-x: auto;
}
.message-text th, .message-text td {
  border: 1px solid #e4e7ed; padding: 6px 10px; text-align: left; vertical-align: top;
  white-space: nowrap;
}
.message-text th { background: #f5f7fa; font-weight: 600; }
```

> `display:block + overflow-x:auto` 是为了**窄屏（手机）能横向滑动看表格**，否则长表格会把气泡撑破。

---

## 四、构建与部署（前端仓库 `D:\Vue-Workspace\csmall-vue`）

```powershell
cd D:\Vue-Workspace\csmall-vue
npm run build            # 产物在 dist/
```

部署有两条路（**推荐 B**，因为服务器上**没有 `nginx:alpine` 基础镜像**，`docker compose build frontend` 会去拉镜像）：

- **B（零重建，快）**：把新 dist 拷进正在运行的容器 ——
  ```powershell
  $key = "$env:USERPROFILE\AppData\Local\csmall-ssh\csmall_ecs_key"
  scp -i $key -r D:\Vue-Workspace\csmall-vue\dist ecs-user@8.156.77.197:/tmp/dist-new
  ```
  ```bash
  docker cp /tmp/dist-new/. csmall-frontend:/usr/share/nginx/html/
  docker exec csmall-frontend nginx -t && docker exec csmall-frontend nginx -s reload
  ```
  ⚠️ 注意：`docker cp` 的改动**只在当前容器里**，容器重建会丢（线上源文件在 `/data/csmall/frontend/dist`，建议同时覆盖那份）。
- **A（持久）**：覆盖 `/data/csmall/frontend/dist` → `docker compose build frontend && docker compose up -d frontend`（需先解决 nginx:alpine 拉取）。

**验证**：打开 AI 助手页，问"3000 元左右适合学生用的手机"，回复里的表格应渲染成**带边框的真实表格**；
悬浮窗（右下角）里标题/列表/表格也应正常。

---

## 五、附：如果暂时不想动前端 —— 后端"改用 UI 已支持的格式"的备选

在后端 `ChatServiceImpl.buildAgentSystemPrompt()` 的输出格式规则里加一条（成本：1 次 mall-ai 部署，前端零改动）：

```
输出格式（重要）：
- 不要使用 Markdown 表格（用 | 分隔的那种）
- 推荐多个商品时用有序列表，每款写成：1. **机型** — 价格 元
  下一行缩进说明：亮点 / 适合人群
- 允许多级标题（###）与加粗
```

原因：`AIAssistant.vue` 现有渲染**已支持** 标题 / 有序列表 / 加粗 / 换行 → 换这种格式立刻就好看；
但它**不支持表格**，所以后端继续输出表格，前端就继续显示竖线。两个方案可以并存（前端补丁之后再上，表格也能用）。
