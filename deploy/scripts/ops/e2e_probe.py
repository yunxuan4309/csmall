#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
外部端到端探活（TODO #61）—— 专治"21 个容器全 Up、网关 /actuator/health = 200，
业务却挂了 24 小时"这类**静默故障**。

为什么不能用 health 接口
------------------------
`/actuator/health` 只能证明"这个进程自己活着"。2026-09-10 那次故障的根因是
**nginx 静态解析上游主机名只在加载配置时解析一次并永久缓存**（等价写死 IP）→ 网关容器重建换 IP 后，
nginx 仍把 `/user /front /ai` 打到被别人占用的旧 IP → 全站 API 502 持续 **24.5 小时无人发现**。
当时每个组件都健康、health 全是 200 —— 因为**坏的是"组件之间的那条路"**，不是组件。

本探针只做一件事：**从系统外部，按真实业务路径打进去**，把"能不能用"直接量出来。

2026-09-12 实测得到的两条**判据**（决定了本脚本怎么写）
------------------------------------------------------
1. **本项目"鉴权失败"是 HTTP 200 + body 里 `state=401`（"您没有登录！"）**，不是 HTTP 401。
   ⇒ 只看 HTTP 状态码的监控会把"没登录"当成"一切正常"；**必须解析 body**。
2. **所有业务 API 都需要登录**（`/front/*`、`/seckill/*`、`/ai/*`、`/pms/*`、`/search/*`、`/admin/*` 实测均为 200+state=401）。
   ⇒ 不带 token 时，探针能证明的是「**nginx 路由 + 网关 + 鉴权链**是通的」（这正是那次故障坏掉的那一层）；
      带上 token（`--token`）则进一步验证「**业务真的能出数据**」（会真的打到 MySQL/Redis/ES）。
   另外：不在 nginx API 前缀里的路径（如 `/product/spu/list`）会**回落 SPA** ——
   所以"API 路径却返回 HTML"本身就是一种路由故障信号，本脚本会判 FAIL。

运行位置（重要）
----------------
必须跑在**被测系统之外**（新机 / 你的笔记本）。跨机请走**内网私网地址**（同 VPC 不计费、不限速）：

    # 路由层（不需要凭据，推荐放进 cron）
    python3 e2e_probe.py --base http://172.29.193.239 --gateway http://172.29.193.239:10087

    # 深度层（带一个浏览器里复制出来的 token，会真的查库）
    python3 e2e_probe.py --base http://172.29.193.239 --token 'eyJhbGciOi...' 

退出码：0 = 无 FAIL（允许 WARN）；1 = 有 FAIL（可直接接 cron 告警）。仅用标准库，无需 pip。
"""

import argparse
import json
import socket
import sys
import time
import urllib.error
import urllib.request

# ---- 探活清单：路径全部来自 2026-09-12 实测（依据 deploy/docker/frontend/nginx.conf 的 location 规则）----
CHECKS = [
    {
        "name": "前端 SPA 首页",
        "path": "/",
        "expect": "html",
        "why": "静态站点可用（SPA 入口）；这是 2026-09-10 故障里唯一没坏的东西",
    },
    {
        "name": "秒杀列表（网关→秒杀→商品）",
        "path": "/seckill/spu/list",
        "expect": "api",
        "why": "2026-09-10 被 nginx 静态上游打死的路径之一",
    },
    {
        "name": "前端商品（网关→mall-front）",
        "path": "/front/spu/list",
        "expect": "api",
        "why": "同上：/front 是那次故障的重灾区",
    },
    {
        "name": "AI 搜索补全（网关→mall-ai→ES）",
        "path": "/ai/search/suggest?prefix=mi",
        "expect": "api",
        "why": "同上：/ai 也是重灾区；且 #63 修复后此接口必须可用",
    },
    {
        "name": "搜索服务（网关→mall-search）",
        "path": "/search/spu/list",
        "expect": "api",
        "why": "另一条独立业务链路（搜索域）",
    },
    {
        "name": "商品域（网关→mall-product/pms）",
        "path": "/pms/spu/list",
        "expect": "api",
        "why": "覆盖商品域路由",
    },
    {
        "name": "管理后台入口",
        "path": "/admin/dashboard",
        "accept": "application/json",
        "expect": "api",
        "why": "覆盖后台域 + nginx 的 Accept 分支（nginx.conf：该路径**只有** Accept:application/json 才转网关，否则故意回落 SPA）",
    },
    {
        "name": "AI 对话入口",
        "method": "POST",
        "path": "/ai/chat/send",
        "body": '{"sessionId":"probe","content":"探活"}'.encode("utf-8"),
        "content_type": "application/json",
        "expect": "api",
        "why": "写路径的入口（未登录应被拦住；带 token 时可验证真实对话）",
    },
    {
        "name": "非 API 路径回落 SPA",
        "path": "/product/spu/list",
        "expect": "html",
        "why": "反证路由表正确：不在 nginx API 前缀里的路径必须回落 SPA，否则说明前缀配错/漏配",
    },
]

FAIL_STATUS = {502, 503, 504}
AUTH_REACHABLE_STATES = {401, 403}   # 路由通 + 鉴权正常（未登录）
BIZ_OK_STATES = {200, 20000}


def probe(base, item, timeout, token):
    """返回 (verdict, detail, http_status)；verdict ∈ {PASS, WARN, FAIL}"""
    url = base.rstrip("/") + item["path"]
    headers = {"User-Agent": "csmall-e2e-probe/1.0", "Accept": item.get("accept", "*/*")}
    if item.get("content_type"):
        headers["Content-Type"] = item["content_type"]
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=item.get("body"), headers=headers,
                                method=item.get("method", "GET"))
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            status, ctype, body = resp.getcode(), (resp.headers.get("Content-Type") or "").lower(), resp.read()
    except urllib.error.HTTPError as e:
        status = e.code
        ctype = (e.headers.get("Content-Type") or "").lower() if e.headers else ""
        body = e.read() if hasattr(e, "read") else b""
    except (urllib.error.URLError, socket.timeout, OSError) as e:
        return "FAIL", "连接/读取失败：{}".format(e), None

    if status in FAIL_STATUS:
        return "FAIL", "HTTP {}（网关或上游不可用 —— 2026-09-10 那次故障就是这个形态）".format(status), status

    text = body.decode("utf-8", "replace")

    if item["expect"] == "html":
        if status == 200 and "text/html" in ctype and len(body) > 200:
            return "PASS", "HTTP 200 html（{} 字节）".format(len(body)), status
        return "FAIL", "期望 HTML 页面，实际 HTTP {} / Content-Type={}".format(status, ctype or "-"), status

    # expect == "api"
    if "text/html" in ctype:
        return "FAIL", "API 路径却返回了 SPA 页面（路由被兜底吃掉 / 前缀漏配）", status
    try:
        payload = json.loads(text)
    except Exception:
        return "FAIL", "响应不是合法 JSON（前 120 字：{}）".format(text[:120]), status

    state = payload.get("state")
    if state in BIZ_OK_STATES:
        return "PASS", "state={}（业务数据可用）".format(state), status
    if state in AUTH_REACHABLE_STATES:
        if token:
            return "FAIL", "带 token 仍 state={}（token 过期或鉴权异常）：{}".format(
                state, str(payload.get("message"))[:60]), status
        return "PASS", "state={}（路由+网关+鉴权链均正常；未登录故不看业务数据）".format(state), status
    if status == 404:
        return "WARN", "HTTP 404（路径或路由变更）", status
    return "FAIL", "HTTP {} 且 state={} message={}".format(status, state, str(payload.get("message"))[:80]), status


def diag_gateway(gateway, timeout):
    """分层定位：直连网关（跳过 nginx）。业务路径失败时区分"nginx 挂了"还是"网关/服务挂了"。"""
    if not gateway:
        return
    url = gateway.rstrip("/") + "/actuator/health"
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            print("  [诊断] 直连网关 {} → HTTP {}".format(url, resp.getcode()))
            print("         ⇒ 直连正常而业务路径失败：问题多半在 **nginx 层**（上游 IP 漂移 / rewrite 漏配）")
    except Exception as e:  # noqa: BLE001 —— 诊断项，任何异常只打印
        print("  [诊断] 直连网关 {} → 失败：{}".format(url, e))
        print("         ⇒ 直连也失败：问题多半在 **网关/服务层或网络**，不一定是 nginx")


def main():
    ap = argparse.ArgumentParser(description="CoolShark 外部端到端探活（TODO #61）")
    ap.add_argument("--base", default="http://127.0.0.1", help="业务入口（前端 nginx），默认本机 80")
    ap.add_argument("--gateway", default="", help="可选：网关地址（如 http://172.29.193.239:10087），用于分层诊断")
    ap.add_argument("--token", default="", help="可选：JWT（浏览器里复制）—— 给出后会验证真实业务数据，而不是只看路由")
    ap.add_argument("--timeout", type=float, default=8.0, help="单请求超时秒数，默认 8")
    ap.add_argument("--json", action="store_true", help="以 JSON 输出（便于接告警/看板）")
    args = ap.parse_args()

    started = time.time()
    results = []
    for item in CHECKS:
        verdict, detail, status = probe(args.base, item, args.timeout, args.token)
        results.append({"name": item["name"], "path": item["path"], "verdict": verdict,
                        "detail": detail, "status": status, "why": item["why"]})
    failed = [r for r in results if r["verdict"] == "FAIL"]
    warned = [r for r in results if r["verdict"] == "WARN"]
    elapsed = round(time.time() - started, 2)

    if args.json:
        print(json.dumps({"base": args.base, "mode": "deep" if args.token else "route",
                          "elapsed_s": elapsed, "results": results,
                          "failed": len(failed), "warned": len(warned)},
                         ensure_ascii=False, indent=2))
    else:
        print("=== CoolShark 外部端到端探活 ===")
        print("入口：{}   模式：{}（耗时 {}s）".format(
            args.base, "深度（带 token，验业务数据）" if args.token else "路由层（无凭据）", elapsed))
        for r in results:
            print("  [{}] {}  {}  → {}".format(r["verdict"], r["name"], r["path"], r["detail"]))
            if r["verdict"] != "PASS":
                print("        为什么测它：{}".format(r["why"]))
        if failed:
            print("\n🔴 {} 项失败 —— 这不代表某个容器挂了，先看**路由层**。".format(len(failed)))
            diag_gateway(args.gateway, args.timeout)
            print("   排查顺序：① 直连网关（见上）→ ② `docker logs csmall-frontend` 看 502 的上游 IP 是不是\n"
                  "      **已失效的旧 IP**（2026-09-10 的根因）→ ③ 检查 Nacos 里服务实例地址是否过期。")
        elif warned:
            print("\n🟡 全通过，但 {} 项 WARN（多为路径变更，建议确认）。".format(len(warned)))
        else:
            print("\n✅ 全部通过：从**业务入口**看，系统是可用的。")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
