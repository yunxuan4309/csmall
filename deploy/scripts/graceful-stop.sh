#!/bin/bash
# ==============================================================================
# 优雅下线脚本（零失败窗口）— CoolShark 跨机集群（2026-09-09）
#
# 【为什么需要它】
#   直接 `docker stop` 一个实例：Nacos 会在 <1s 内摘除它（应用优雅停机主动注销），
#   但**客户端（网关）的负载均衡缓存有 ~16s 延迟** → 期间轮询会把一半请求打到
#   已经停掉的实例 → 500。
#   实测对比（同一台网关、同样停副本）：
#     · 粗暴 `docker stop`            → 9 次请求 **5 次 500**
#     · 本脚本（先摘除→等待→再停）   → 30 次请求 **0 次失败**
#
# 【原理】
#   先把实例在 Nacos 里标记为 `enabled=false`（实例**仍在服务**，只是不再被
#   负载均衡选中），等客户端缓存刷新完（> LB 缓存 TTL，默认 35s），再停容器——
#   此时已经没有流量发给它了，自然零失败。
#   等价于 K8s 的 `readiness probe` + `preStop: sleep 40`。
#
# 【用法】
#   ./graceful-stop.sh <service-name> <container-name> <instance-ip> <instance-port>
#   示例（停新机上的秒杀副本）：
#     ./graceful-stop.sh mall-seckill csmall-seckill-2 172.29.193.240 10017
#   示例（停老机上的秒杀实例1）：
#     ./graceful-stop.sh mall-seckill csmall-seckill 172.18.0.19 10007
#
# 【如何查实例坐标】
#   登录 Nacos → 服务列表 → 点服务名 → 看「实例 IP / 端口」；
#   或：curl -s "http://172.29.193.239:8848/nacos/v1/ns/instance/list?serviceName=mall-seckill&accessToken=$TOKEN"
#
# 【恢复】
#   `docker start <container>` 即可 —— 应用启动时会以 `enabled=true` 重新注册
#   （实测：重启后实例自动恢复 enabled=true + healthy=true，网关 10/10 成功）。
#
# 【注意】
#   WAIT_SECONDS 必须 > `spring.cloud.loadbalancer.cache.ttl`（默认 35s），
#   否则缓存还没刷新完就停了容器，窗口依旧存在。
# ==============================================================================
set -euo pipefail

if [ $# -lt 4 ]; then
  echo "用法: $0 <service-name> <container-name> <instance-ip> <instance-port>" >&2
  exit 2
fi

SERVICE="$1"
CONTAINER="$2"
IP="$3"
PORT="$4"

NACOS_ADDR="${NACOS_ADDR:-172.29.193.239:8848}"
ENV_FILE="${ENV_FILE:-/data/csmall/.env}"
WAIT_SECONDS="${WAIT_SECONDS:-40}"   # 必须 > LB 缓存 TTL（默认 35s）

NACOS_PW=$(grep -E '^NACOS_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
if [ -z "$NACOS_PW" ]; then
  echo "❌ 未能从 $ENV_FILE 读取 NACOS_PASSWORD" >&2
  exit 1
fi

TOKEN=$(curl -s -m 10 -X POST "http://${NACOS_ADDR}/nacos/v1/auth/login" \
  -d "username=nacos&password=${NACOS_PW}" \
  | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  echo "❌ Nacos 登录失败（${NACOS_ADDR}）" >&2
  exit 1
fi

echo "① 从 Nacos 摘除（enabled=false，实例仍在服务）：${SERVICE} ${IP}:${PORT}"
RESP=$(curl -s -m 10 -X PUT "http://${NACOS_ADDR}/nacos/v1/ns/instance?serviceName=${SERVICE}&ip=${IP}&port=${PORT}&enabled=false&ephemeral=true&accessToken=${TOKEN}")
echo "   Nacos 响应: ${RESP}"
if [ "${RESP}" != "ok" ]; then
  echo "❌ 摘除失败，已中止（未停容器，业务不受影响）" >&2
  exit 1
fi

echo "② 等待 ${WAIT_SECONDS}s（> LB 缓存 TTL 35s），期间实例继续服务 → 业务零失败"
sleep "${WAIT_SECONDS}"

echo "③ 停止容器：${CONTAINER}"
docker stop "${CONTAINER}"

echo "✅ 优雅下线完成。恢复：docker start ${CONTAINER}（会自动以 enabled=true 重新注册）"
