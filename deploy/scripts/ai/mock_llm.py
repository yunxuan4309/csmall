#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
mock_llm.py — 内网 mock LLM（OpenAI 兼容），供 TODO #67 之 ③「第二层 AI 并发压测」使用

定位
    **只测「服务端承载」**：把 mall-ai 的 base-url 临时指向本 mock，压 /ai/chat/stream 这条 SSE 长连接，
    量出「第几个并发开始降级」。**不调真实付费 LLM**、不消耗 2 元/日预算（预算口径见下方 🔴 ④）。

为什么不压真实 API（[[AI并发测试方案]] §一）
    · 有真实成本（TokenBudgetService 全局 2 元/天）
    · 速度瓶颈在 DeepSeek 而不在你的服务 → 压出来的"慢"是模型慢，不是后端慢

契约（**逐条来自生产代码，勿凭记忆改**）
    端点   POST {base-url}/v1/chat/completions
           —— DeepSeekAiClient.java:50 `CHAT_COMPLETIONS_PATH = "/v1/chat/completions"`
    鉴权   Authorization: Bearer <AI_API_KEY>（mock 不校验，仅记录）
    请求体 model / messages / max_tokens / thinking{type} / reasoning_effort? / temperature?
           / response_format?（仅 AiTask.JSON）/ tools? / tool_choice? / stream / stream_options
           —— DeepSeekAiClient.buildBody():393-419 + buildToolBody():157-164
    流式   解析口径在 openSseStream():325-368，四条**必须**对齐：
           ① 每行必须 `data: ` 前缀（`line.substring(6)` 才当 JSON；否则整行被跳过）
           ② `data: [DONE]` 是 `continue` **不是 break** ⇒ 流结束靠**连接 EOF**
              ⇒ 本 mock 用 chunked 编码写完 `0\\r\\n\\r\\n` 终结 body（**不能**只发 keep-alive 就等）
           ③ 顶层带 `usage` 的分片会被 `recordUsage` 记账后 `continue`
           ④ delta.content 逐片回吐；delta.tool_calls 按 `index` 累积，**首片带 id/name，后续片只带 arguments**
    非流式 choices[0].message.content（供 doChat/chatWithModel/chatWithTools 同步路径）

🔴 四个"会让压测结论作假"的坑（本 mock 已按此实现，改代码前先读）
    ① mock 必须**线程化**（ThreadingHTTPServer）—— 单线程 HTTPServer 会把并发 SSE 串行化，
       压到的是 mock 的吞吐，不是服务端的（[[AI并发测试方案]] §六·A · D5）
    ② 生产 **AI_AGENT_ENABLED=true** ⇒ 必须实现 tool_calls 工具轮，否则测的不是生产链路（D2）
    ③ `/ai/chat/stream` 的**限流不是 HTTP 429**：`AiController.chatStreamBlock` 返回 **HTTP 200 + SSE
       event:error**（"AI 服务繁忙…"）⇒ 压测脚本必须按 body 文案判限流，只看 HTTP 码会把限流算成成功
    ④ **闸门满与预算超限共用同一句文案**（`ChatServiceImpl:481` = 闸门/预算，:499 = 其它异常）
       ⇒ mock 返回的 `usage` 会被累加进 `ai:daily_cost:<日期>`：**真压起来可能先撞 2 元/天预算墙**
       ⇒ 压测前必须临时把预算调大（`COOXIAO_AI_DAILYBUDGET`，环境变量优先级高于 yml 的 2.0）
         或把本 mock 的 `--prompt-tokens/--completion-tokens` 调小

用法
    python3 mock_llm.py --port 9999                     # 起 mock（默认 0.0.0.0:9999）
    python3 mock_llm.py --port 9999 --stats-every 10    # 每 10s 打一次并发/完成统计
    python3 mock_llm.py --self-test                     # 自测（本机起临时端口，逐条验契约）
    curl -s localhost:9999/                             # 健康检查

⚠️ 依赖：仅标准库（新机无外网也能跑）
"""
from __future__ import annotations

import argparse
import http.client
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, List, Optional

VERSION = "mock-llm/1.0 (TODO #67-③)"

# 正文：分片回吐（模拟逐字输出）
CONTENT = "这是一条模拟的 AI 导购回复，用于并发测试：这款手机性价比不错，预算内还可以看看同价位的另外两款。"

# 工具轮参数：按 index 分片拼接（首片带 id/name，后续片只带 arguments）
TOOL_ARGS = '{"keyword": "手机"}'
TOOL_NAME_FALLBACK = "searchProducts"

# 🔴 AiTask.JSON（意图提取 / 重排 / 偏好提取）会把 content 当 JSON 解析 ⇒ 必须回**超集对象**
#    · SearchIntent：budgetMin/budgetMax/brand/category/keywords/sortBy（fastjson 忽略未知键）
#    · SearchServiceImpl.callRerank：**必须有 `rankedIds` 数组**（`getJSONArray("rankedIds")` 为 null 会 NPE）
#    · PreferenceExtractor：任意对象，逐 key 取值
#    ⚠️ 本 mock **不参与真实重排**（rankedIds 回空数组）⇒ 排序质量不是本次测试目标；
#       本次量的是「承载」，链路能正常走到 event:done 即可。
JSON_SUPERSET: Dict[str, Any] = {
    "keywords": "手机",
    "brand": None,
    "category": None,
    "budgetMin": None,
    "budgetMax": None,
    "sortBy": None,
    "rankedIds": [],
    "explanation": "mock 未参与真实重排（#67-③ 只测承载）",
    "preferences": {},
}


class Metrics:
    """全局计数（线程安全）。并发/峰值是判断"mock 自己是不是瓶颈"的关键证据。"""

    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.t0 = time.time()
        self.total = 0
        self.active = 0
        self.peak = 0
        self.stream = 0
        self.nonstream = 0
        self.tool_rounds = 0
        self.content_rounds = 0
        self.json_rounds = 0
        self.chunks = 0
        self.bytes_out = 0
        self.injected_fail = 0
        self.bad_path = 0
        self.by_model: Dict[str, int] = {}

    def enter(self) -> None:
        with self.lock:
            self.total += 1
            self.active += 1
            if self.active > self.peak:
                self.peak = self.active

    def leave(self) -> None:
        with self.lock:
            self.active -= 1

    def incr(self, field: str, n: int = 1) -> None:
        with self.lock:
            setattr(self, field, getattr(self, field) + n)

    def model(self, name: str) -> None:
        with self.lock:
            self.by_model[name] = self.by_model.get(name, 0) + 1

    def snapshot(self) -> Dict[str, Any]:
        with self.lock:
            return {
                "elapsed_s": round(time.time() - self.t0, 1),
                "total": self.total,
                "active": self.active,
                "peak_active": self.peak,
                "stream": self.stream,
                "nonstream": self.nonstream,
                "tool_rounds": self.tool_rounds,
                "content_rounds": self.content_rounds,
                "json_rounds": self.json_rounds,
                "chunks_out": self.chunks,
                "bytes_out": self.bytes_out,
                "injected_fail": self.injected_fail,
                "bad_path": self.bad_path,
                "by_model": dict(self.by_model),
            }


class MockServer(ThreadingHTTPServer):
    """🔴 必须显式放大 accept 队列。

    `socketserver.TCPServer.request_queue_size` **默认只有 5** —— 100 并发时连接会在
    accept 队列里被丢弃，客户端报连接错误。2026-09-12 实测：默认值下并发 100（10s）出现
    **17 次失败**；放大到 512 后归零（本文件顶部的"先单独压 mock"就是为抓这类问题）。
    """
    daemon_threads = True
    allow_reuse_address = True
    request_queue_size = 512


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"      # 长连接：SSE 必须，否则连接复用行为不真实
    server_version = "mock-llm"
    sys_version = ""

    # --- 静音：压测时刷日志本身会拖慢 mock ---
    def log_message(self, *args: Any) -> None:      # noqa: D102
        pass

    # ============================ 基础写出 ============================

    def _write(self, data: bytes) -> None:
        """chunked 帧：<hex 长度>\\r\\n<data>\\r\\n（见契约 ② —— 必须能终结 body）"""
        self.wfile.write(b"%x\r\n" % len(data))
        self.wfile.write(data)
        self.wfile.write(b"\r\n")
        self.wfile.flush()
        M.incr("bytes_out", len(data))

    def _end_chunked(self) -> None:
        self.wfile.write(b"0\r\n\r\n")
        self.wfile.flush()

    def _sse(self, obj: Dict[str, Any]) -> None:
        payload = "data: " + json.dumps(obj, ensure_ascii=False) + "\n\n"
        self._write(payload.encode("utf-8"))
        M.incr("chunks")

    def _json_response(self, code: int, obj: Dict[str, Any]) -> None:
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        self.wfile.flush()
        M.incr("bytes_out", len(body))

    def _start_sse(self) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()

    # ============================ 分片生成 ============================

    def _chunk_text(self, text: str, size: int, delay_ms: float) -> List[str]:
        pieces = [text[i:i + size] for i in range(0, len(text), size)] or [""]
        out: List[str] = []
        for p in pieces:
            out.append(p)
            time.sleep(delay_ms / 1000.0)
        return out

    def _usage_chunk(self) -> None:
        """顶层带 usage 的末尾分片（stream_options.include_usage=true 时官方会返回）"""
        self._sse({"choices": [], "usage": {
            "prompt_tokens": ARGS.prompt_tokens,
            "completion_tokens": ARGS.completion_tokens,
            "total_tokens": ARGS.prompt_tokens + ARGS.completion_tokens,
        }})

    def _stream_tool_round(self, tool_name: str) -> None:
        """工具轮：首片带 id/name，后续片只带 arguments，全部带同一个 index（见契约 ④）"""
        time.sleep(ARGS.ttfb_ms / 1000.0)          # 真实 LLM 有首字延迟，mock 也对齐（TTFT 可测）
        self._sse({"choices": [{"delta": {"role": "assistant", "tool_calls": [
            {"index": 0, "id": "call_sim_1", "type": "function",
             "function": {"name": tool_name, "arguments": ""}}]},
            "finish_reason": None}]})
        for i in range(0, len(TOOL_ARGS), ARGS.tool_fragment):
            self._sse({"choices": [{"delta": {"tool_calls": [
                {"index": 0, "function": {"arguments": TOOL_ARGS[i:i + ARGS.tool_fragment]}}]},
                "finish_reason": None}]})
            time.sleep(ARGS.tool_delay_ms / 1000.0)
        self._sse({"choices": [{"delta": {}, "finish_reason": "tool_calls"}]})
        self._usage_chunk()
        self._write(b"data: [DONE]\n\n")
        self._end_chunked()
        M.incr("tool_rounds")

    def _stream_content_round(self, text: str) -> None:
        time.sleep(ARGS.ttfb_ms / 1000.0)          # 首字延迟（真实 LLM 0.5~2s；这里是可配的近似）
        self._sse({"choices": [{"delta": {"role": "assistant", "content": ""},
                                "finish_reason": None}]})
        for piece in self._chunk_text(text, ARGS.chunk_size, ARGS.chunk_delay_ms):
            self._sse({"choices": [{"delta": {"content": piece}, "finish_reason": None}]})
        self._sse({"choices": [{"delta": {}, "finish_reason": "stop"}]})
        self._usage_chunk()
        self._write(b"data: [DONE]\n\n")
        self._end_chunked()
        M.incr("content_rounds")

    # ============================ 请求体 ============================

    def _read_body(self) -> bytes:
        """读请求体 —— 🔴 **必须支持 chunked**。

        2026-09-12 实测抓到的真问题：`PreferenceExtractor` 的 `chatJson`（非流式、带
        `response_format`）是**分块传输**发来的；只按 `Content-Length` 读会得到**空体**
        → mock 误判成"普通任务"→ 回纯文本 → 应用 `JSON.parseObject` 抛
        `fastjson.JSONException: offset 1, character 这` → **每次对话都产生一个假 500**
        （SSE 里表现为 event:error → 压测会把降级率算高）。
        """
        te = (self.headers.get("Transfer-Encoding") or "").lower()
        if "chunked" in te:
            parts: List[bytes] = []
            while True:
                line = self.rfile.readline().strip()
                if not line:
                    continue
                try:
                    size = int(line.split(b";")[0], 16)
                except ValueError:
                    break
                if size == 0:
                    self.rfile.readline()          # 末尾 CRLF
                    break
                parts.append(self.rfile.read(size))
                self.rfile.read(2)                 # 块尾 CRLF
            return b"".join(parts)
        n = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(n) if n else b""

    # ============================ 路由 ============================

    def do_GET(self) -> None:      # noqa: N802
        if self.path in ("/", "/health", "/healthz"):
            self._json_response(200, {"ok": True, "mock": VERSION, **M.snapshot()})
        else:
            M.incr("bad_path")
            self._json_response(404, {"error": "not found", "path": self.path})

    def do_POST(self) -> None:      # noqa: N802
        M.enter()
        try:
            self._handle_post()
        except (BrokenPipeError, ConnectionResetError):
            pass                    # 压测中客户端提前断开是正常的
        except Exception as e:      # noqa: BLE001
            try:
                self._json_response(500, {"error": f"{type(e).__name__}: {e}"})
            except Exception:       # noqa: BLE001
                pass
        finally:
            M.leave()

    def _handle_post(self) -> None:
        raw = self._read_body()
        try:
            req: Dict[str, Any] = json.loads(raw or b"{}")
        except ValueError:
            req = {}
        if ARGS.verbose:
            print(f"[body] clen={self.headers.get('Content-Length')} "
                  f"te={self.headers.get('Transfer-Encoding')} rawlen={len(raw)} "
                  f"json_ok={bool(req)}", flush=True)

        if not self.path.rstrip("/").endswith("/chat/completions"):
            M.incr("bad_path")
            self._json_response(404, {"error": "mock 只实现 /v1/chat/completions", "path": self.path})
            return

        if ARGS.fail_rate > 0 and (M.snapshot()["total"] % max(1, int(1 / ARGS.fail_rate)) == 0):
            M.incr("injected_fail")
            self._json_response(500, {"error": "mock 注入的故障（--fail-rate）"})
            return

        model = req.get("model") or "mock-model"
        M.model(model)
        messages: List[Dict[str, Any]] = req.get("messages") or []
        tools: List[Dict[str, Any]] = req.get("tools") or []
        is_json_task = isinstance(req.get("response_format"), dict)
        # 已经调过工具 ⇒ 本轮必须收敛（复刻生产"第 1 轮调工具 → 第 2 轮收敛"）
        used_tool = any(m.get("role") == "assistant" and m.get("tool_calls") for m in messages)
        if ARGS.verbose:
            print(f"[req] stream={bool(req.get('stream'))} tools={len(tools)} "
                  f"tool_choice={req.get('tool_choice')} response_format={is_json_task} "
                  f"used_tool={used_tool} msgs={len(messages)} model={model}", flush=True)
        tool_name = TOOL_NAME_FALLBACK
        if tools and isinstance(tools[0], dict):
            fn = tools[0].get("function") or {}
            tool_name = fn.get("name") or TOOL_NAME_FALLBACK

        if req.get("stream"):
            M.incr("stream")
            self._start_sse()
            if is_json_task:
                # JSON 任务即使要流式也走 content（不回工具）
                self._stream_content_round(json.dumps(JSON_SUPERSET, ensure_ascii=False))
            elif tools and not used_tool:
                self._stream_tool_round(tool_name)
            else:
                self._stream_content_round(CONTENT)
            return

        # --- 非流式 ---
        M.incr("nonstream")
        if is_json_task:
            M.incr("json_rounds")
            content = json.dumps(JSON_SUPERSET, ensure_ascii=False)
            self._json_response(200, self._nonstream_body(content, None))
            return
        if tools and not used_tool:
            M.incr("tool_rounds")
            msg = {"role": "assistant", "content": "",
                   "tool_calls": [{"id": "call_sim_1", "type": "function",
                                   "function": {"name": tool_name, "arguments": TOOL_ARGS}}]}
            self._json_response(200, {"choices": [{"message": msg, "finish_reason": "tool_calls"}],
                                      "usage": self._usage_obj()})
            return
        M.incr("content_rounds")
        self._json_response(200, self._nonstream_body(CONTENT, "stop"))

    def _usage_obj(self) -> Dict[str, int]:
        return {"prompt_tokens": ARGS.prompt_tokens,
                "completion_tokens": ARGS.completion_tokens,
                "total_tokens": ARGS.prompt_tokens + ARGS.completion_tokens}

    def _nonstream_body(self, content: str, finish: Optional[str]) -> Dict[str, Any]:
        return {
            "id": "chatcmpl-mock",
            "object": "chat.completion",
            "model": "mock-model",
            "choices": [{"index": 0, "message": {"role": "assistant", "content": content},
                         "finish_reason": finish or "stop"}],
            "usage": self._usage_obj(),
        }


M = Metrics()
ARGS: argparse.Namespace


def _stats_loop(every: float) -> None:
    while True:
        time.sleep(every)
        s = M.snapshot()
        done = s["tool_rounds"] + s["content_rounds"] + s["json_rounds"]
        print(f"[mock {s['elapsed_s']:8.1f}s] 请求 {s['total']:7d}  并发 {s['active']:4d}"
              f"  峰值 {s['peak_active']:4d}  SSE {s['stream']:7d}  分片 {s['chunks_out']:8d}"
              f"  子轮(tool/content/json) {s['tool_rounds']}/{s['content_rounds']}/{s['json_rounds']}"
              f"  ≈{done}  出流量 {s['bytes_out'] / 1024 / 1024:.1f}MB", flush=True)


def _self_test(port: int) -> int:
    """本机自测：逐条验契约（含"body 必须能终结" —— 即客户端不会挂到读超时）"""
    fails: List[str] = []

    def check(name: str, cond: bool, extra: str = "") -> None:
        print(f"  {'✅' if cond else '❌'} {name}{('  ' + extra) if extra else ''}")
        if not cond:
            fails.append(name)

    srv = MockServer(("127.0.0.1", port), Handler)
    srv.daemon_threads = True
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    time.sleep(0.3)
    print(f"自测中（临时端口 {port}）…")

    def post(body: Dict[str, Any], stream: bool) -> Any:
        c = http.client.HTTPConnection("127.0.0.1", port, timeout=10)
        c.request("POST", "/v1/chat/completions", json.dumps(body).encode(),
                  {"Content-Type": "application/json", "Authorization": "Bearer sk-mock"})
        r = c.getresponse()
        if stream:
            lines = [ln.decode() for ln in r.read().split(b"\n")]   # read() 直到 EOF ⇒ 验"能终结"
            c.close()
            return r.status, r.getheader("Content-Type"), lines
        data = json.loads(r.read())
        c.close()
        return r.status, r.getheader("Content-Type"), data

    # ① 非流式
    st, ct, obj = post({"model": "m", "messages": [{"role": "user", "content": "hi"}]}, False)
    check("非流式 HTTP 200", st == 200, f"status={st}")
    check("非流式 choices[0].message.content 非空",
          bool((obj.get("choices") or [{}])[0].get("message", {}).get("content")))
    check("非流式带 usage", bool(obj.get("usage", {}).get("total_tokens")))

    # ② JSON 任务（AiTask.JSON）
    st, ct, obj = post({"model": "m", "response_format": {"type": "json_object"},
                        "messages": [{"role": "user", "content": "意图"}]}, False)
    content = (obj.get("choices") or [{}])[0].get("message", {}).get("content", "")
    try:
        parsed = json.loads(content)
    except ValueError:
        parsed = None
    check("JSON 任务回可解析 JSON", isinstance(parsed, dict))
    check("JSON 超集含 rankedIds 数组（否则 callRerank 会 NPE）",
          isinstance((parsed or {}).get("rankedIds"), list))

    # ③ 流式正文
    st, ct, lines = post({"model": "m", "stream": True,
                          "messages": [{"role": "user", "content": "hi"}]}, True)
    datas = [ln for ln in lines if ln.startswith("data: ")]
    check("流式 Content-Type 是 text/event-stream", "text/event-stream" in (ct or ""))
    check("所有分片都有 `data: ` 前缀", len(datas) > 0 and len(datas) == len([l for l in lines if l.strip()]))
    check("以 data: [DONE] 结束", any(l.strip() == "data: [DONE]" for l in lines))
    check("body 能自然终结（客户端不会挂到读超时）", True, "已 read() 到 EOF")
    ok_delta = False
    for ln in datas:
        if ln.strip() == "data: [DONE]":
            continue
        d = json.loads(ln[6:])
        if d.get("usage"):
            continue
        delta = (d.get("choices") or [{}])[0].get("delta") or {}
        if delta.get("content"):
            ok_delta = True
    check("收到 delta.content 分片", ok_delta)

    # ④ 工具轮
    tools = [{"type": "function", "function": {"name": "searchProducts",
                                               "parameters": {"type": "object"}}}]
    st, ct, lines = post({"model": "m", "stream": True, "tools": tools, "tool_choice": "required",
                          "messages": [{"role": "user", "content": "推荐手机"}]}, True)
    ids, names, args = set(), set(), []
    for ln in lines:
        if not ln.startswith("data: ") or ln.strip() == "data: [DONE]":
            continue
        d = json.loads(ln[6:])
        if d.get("usage"):
            continue
        for tc in (((d.get("choices") or [{}])[0].get("delta") or {}).get("tool_calls") or []):
            if tc.get("id"):
                ids.add(tc["id"])
            fn = tc.get("function") or {}
            if fn.get("name"):
                names.add(fn["name"])
            if fn.get("arguments"):
                args.append(fn["arguments"])
    check("工具轮首片带 id", ids == {"call_sim_1"})
    check("工具轮首片带 name", names == {"searchProducts"})
    check("arguments 分片拼接后是合法 JSON",
          (lambda s: s in ("{}",) or (s.startswith("{") and json.loads(s) is not None))("".join(args)),
          f"{len(args)} 片 → {''.join(args)[:40]}")

    # ⑤ 第二轮（已调过工具）必须收敛成 content
    st, ct, lines = post({"model": "m", "stream": True, "tools": tools,
                          "messages": [{"role": "user", "content": "推荐手机"},
                                       {"role": "assistant", "tool_calls": [
                                           {"id": "call_sim_1", "type": "function",
                                            "function": {"name": "searchProducts", "arguments": "{}"}}]},
                                       {"role": "tool", "tool_call_id": "call_sim_1", "content": "[]"}]}, True)
    has_content = any("content" in json.loads(l[6:]).get("choices", [{}])[0].get("delta", {})
                      for l in lines if l.startswith("data: ") and l.strip() != "data: [DONE]"
                      and not json.loads(l[6:]).get("usage"))
    check("已调过工具的第二轮回 content（收敛）", has_content)

    srv.shutdown()
    print(f"\n自测结果：{'✅ 全部通过' if not fails else '❌ 失败 ' + str(len(fails)) + ' 项: ' + ', '.join(fails)}")
    return 0 if not fails else 1


def main() -> int:
    global ARGS
    ap = argparse.ArgumentParser(description="内网 mock LLM（OpenAI 兼容 /v1/chat/completions）")
    ap.add_argument("--host", default="0.0.0.0", help="监听地址（默认 0.0.0.0，供老机容器内网访问）")
    ap.add_argument("--port", type=int, default=9999, help="监听端口（默认 9999）")
    ap.add_argument("--stats-every", type=float, default=10.0, help="统计打印间隔秒（0=不打印）")
    ap.add_argument("--ttfb-ms", type=float, default=250.0,
                    help="每轮首字延迟毫秒（默认 250 —— 真实 LLM 的 TTFT 是秒级；"
                         "调大=更真实但更慢，调小=能压出更高 RPS）")
    ap.add_argument("--chunk-size", type=int, default=6, help="正文分片字符数（默认 6，模拟逐字）")
    ap.add_argument("--chunk-delay-ms", type=float, default=30.0, help="每片正文的间隔毫秒（默认 30）")
    ap.add_argument("--tool-fragment", type=int, default=6, help="tool arguments 分片字符数（默认 6）")
    ap.add_argument("--tool-delay-ms", type=float, default=5.0, help="每个 tool arguments 分片的间隔毫秒")
    ap.add_argument("--prompt-tokens", type=int, default=50, help="usage 里的 prompt_tokens（影响预算记账）")
    ap.add_argument("--completion-tokens", type=int, default=30, help="usage 里的 completion_tokens")
    ap.add_argument("--fail-rate", type=float, default=0.0, help="故障注入比例（如 0.01=1%% 返回 500）")
    ap.add_argument("--verbose", action="store_true",
                    help="每个请求打一行摘要（stream/tools/response_format/used_tool）—— 排查契约问题用，"
                         "**压测时不要开**（刷日志会拖慢 mock）")
    ap.add_argument("--self-test", action="store_true", help="本机自测契约后退出")
    ARGS = ap.parse_args()

    if ARGS.self_test:
        return _self_test(ARGS.port)

    if ARGS.stats_every > 0:
        threading.Thread(target=_stats_loop, args=(ARGS.stats_every,), daemon=True).start()

    srv = MockServer((ARGS.host, ARGS.port), Handler)
    print(f"{VERSION} 已启动：http://{ARGS.host}:{ARGS.port}/v1/chat/completions", flush=True)
    print(f"  线程化: 是 · accept 队列 request_queue_size={srv.request_queue_size}"
          f"（默认仅 5，100 并发会丢连接 —— 已放大）", flush=True)
    print(f"  正文: 首字 {ARGS.ttfb_ms:.0f}ms + {ARGS.chunk_size} 字/片 × {ARGS.chunk_delay_ms:.0f}ms  |  "
          f"tool arguments: {ARGS.tool_fragment} 字/片 × {ARGS.tool_delay_ms:.0f}ms", flush=True)
    # 单价来自 application.yml:93-94（入 1 元/百万、出 2 元/百万），预算 2 元/天（:92）
    per_call = (1.0 * ARGS.prompt_tokens + 2.0 * ARGS.completion_tokens) / 1_000_000.0
    if per_call == 0:
        usage_note = "✅ 每次调用 0 元 → **不污染 `ai:daily_cost`**，无需动预算（承载压测的推荐姿势）"
    else:
        usage_note = (f"🔴 每次调用约 {per_call:.8f} 元 → 约 {int(2.0 / per_call):,} 次调用就打满 2 元/天；"
                      f"届时**每个请求都返回「服务繁忙」**（与闸门满同一句文案）")
    print(f"  usage: prompt={ARGS.prompt_tokens} completion={ARGS.completion_tokens}  {usage_note}", flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        s = M.snapshot()
        print(f"\n收到 Ctrl-C，退出。累计：请求 {s['total']} 峰值并发 {s['peak_active']}"
              f" SSE {s['stream']}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
