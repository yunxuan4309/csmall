#!/bin/bash
# ============================================================================= #
#  CoolShark 微服务 systemd 部署脚本                                            #
#  用途：将 nohup 启动方式迁移到 systemd 管理，解决旧进程未关闭导致新进程无法启动的问题   #
#  使用：sudo bash install-systemd.sh                                           #
# ============================================================================= #

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SYSTEMD_DIR="/etc/systemd/system"
JAR_DIR="/data/jars"
LOG_DIR="/data/jars/logs"
ENV_FILE="/data/jars/csmall.env"
BACKUP_DIR="/data/jars/backup_$(date +%Y%m%d_%H%M%S)"

echo "=============================================="
echo "  CoolShark systemd 部署脚本"
echo "=============================================="

# ---- 第 0 步：前置检查 ----
echo ""
echo "[0/6] 前置检查..."

if [ "$(id -u)" -ne 0 ]; then
    echo "错误：请使用 root 权限运行此脚本 (sudo bash install-systemd.sh)"
    exit 1
fi

if [ ! -d "$JAR_DIR" ]; then
    echo "错误：JAR 目录 $JAR_DIR 不存在"
    exit 1
fi

# 检查 Java 是否可用
JAVA_PATH=$(readlink -f $(which java) 2>/dev/null || echo "")
if [ -z "$JAVA_PATH" ]; then
    echo "错误：未找到 java 命令，请确认 Java 已安装"
    exit 1
fi
echo "  Java 路径: $JAVA_PATH"

# ---- 第 1 步：验证 JAR 文件名 ----
echo ""
echo "[1/6] 验证 JAR 文件..."

# 列出服务器上所有 mall- 开头的 JAR
echo "  当前 $JAR_DIR 中的 JAR 文件："
ls -la "$JAR_DIR"/mall-*.jar 2>/dev/null || echo "  (无 mall-*.jar 文件)"

echo ""
echo "  ⚠ 请确认以上 JAR 文件名与 systemd service 文件中的 ExecStart 路径一致。"
echo "  如果 JAR 文件名不同，请先编辑 service 文件再继续。"
echo ""
read -p "  JAR 文件名是否正确？(y/n): " confirm_jars
if [ "$confirm_jars" != "y" ]; then
    echo "请先修改 systemd service 文件中的 JAR 文件名，然后重新运行此脚本。"
    echo "修改位置：$SCRIPT_DIR/systemd/*.service 中的 ExecStart 行"
    exit 1
fi

# 更新 service 文件中的 Java 路径
echo "  更新 service 文件中的 Java 路径为 $JAVA_PATH ..."
for svc_file in "$SCRIPT_DIR"/systemd/mall-*.service; do
    if [ -f "$svc_file" ]; then
        sed -i "s|ExecStart=/usr/bin/java|ExecStart=$JAVA_PATH|g" "$svc_file"
    fi
done

# ---- 第 2 步：停止旧进程并备份 ----
echo ""
echo "[2/6] 停止旧的 nohup 进程并备份..."

mkdir -p "$BACKUP_DIR"

# 备份旧启动脚本
if [ -f "$JAR_DIR/start.sh" ]; then
    cp "$JAR_DIR/start.sh" "$BACKUP_DIR/start.sh.bak"
    echo "  已备份 start.sh -> $BACKUP_DIR/start.sh.bak"
fi

# 停止所有旧的 nohup 进程
echo "  停止旧的微服务进程..."
for name in mall-gateway mall-front mall-resource mall-seckill mall-search mall-order mall-sso mall-product mall-ums mall-ams; do
    pids=$(pgrep -f "$name" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        echo "  停止 $name (PID: $pids)..."
        echo "$pids" | xargs kill -15 2>/dev/null || true
    fi
done

# 等待进程退出
echo "  等待进程退出 (最多 30 秒)..."
for i in $(seq 1 30); do
    remaining=$(pgrep -f "mall-" 2>/dev/null || true)
    if [ -z "$remaining" ]; then
        echo "  所有进程已退出"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "  超时，强制终止残留进程..."
        pgrep -f "mall-" | xargs kill -9 2>/dev/null || true
        sleep 2
    fi
    sleep 1
done

# 验证端口已释放
echo "  验证端口已释放..."
for port in 10003 10004 10005 10006 10007 10008 10009 9010 9060 10087; do
    if fuser "$port/tcp" >/dev/null 2>&1; then
        echo "  ⚠ 端口 $port 仍被占用，强制释放..."
        fuser -k "$port/tcp" >/dev/null 2>&1 || true
    fi
done

# ---- 第 3 步：安装环境变量文件 ----
echo ""
echo "[3/6] 安装共享环境变量文件..."

if [ -f "$ENV_FILE" ]; then
    cp "$ENV_FILE" "$BACKUP_DIR/csmall.env.bak"
    echo "  已备份旧环境变量文件 -> $BACKUP_DIR/csmall.env.bak"
fi

# 优先从 systemd 子目录读取，如果没有则从脚本同目录读取
if [ -f "$SCRIPT_DIR/csmall.env" ]; then
    cp "$SCRIPT_DIR/csmall.env" "$ENV_FILE"
else
    cp "$SCRIPT_DIR/systemd/csmall.env" "$ENV_FILE"
fi
chmod 600 "$ENV_FILE"
chown ecs-user:ecs-user "$ENV_FILE"
echo "  已安装 $ENV_FILE (权限 600)"
echo ""
echo "  ⚠ 请确认环境变量值正确："
cat "$ENV_FILE"
echo ""
read -p "  环境变量是否正确？(y/n): " confirm_env
if [ "$confirm_env" != "y" ]; then
    echo "请手动编辑 $ENV_FILE 后重新运行此脚本。"
    exit 1
fi

# ---- 第 4 步：安装 systemd 服务文件 ----
echo ""
echo "[4/6] 安装 systemd 服务文件..."

for svc_file in "$SCRIPT_DIR"/systemd/mall-*.service; do
    if [ -f "$svc_file" ]; then
        svc_name=$(basename "$svc_file")
        cp "$svc_file" "$SYSTEMD_DIR/$svc_name"
        chmod 644 "$SYSTEMD_DIR/$svc_name"
        echo "  已安装 $svc_name"
    fi
done

systemctl daemon-reload
echo "  已执行 daemon-reload"

# ---- 第 5 步：创建日志目录 ----
echo ""
echo "[5/6] 确保日志目录存在..."
mkdir -p "$LOG_DIR"
chown -R ecs-user:ecs-user "$LOG_DIR"
echo "  日志目录: $LOG_DIR"

# ---- 第 6 步：启动服务 ----
echo ""
echo "[6/6] 按顺序启动微服务..."

# Phase 1: Dubbo 提供者
echo ""
echo "  === Phase 1: Dubbo 提供者 ==="
for svc in mall-ams mall-ums mall-product; do
    echo "  启动 $svc..."
    systemctl start "$svc"
    sleep 5
    if systemctl is-active --quiet "$svc"; then
        echo "  ✓ $svc 启动成功"
    else
        echo "  ✗ $svc 启动失败，查看日志: journalctl -u $svc -n 50"
    fi
done

echo "  等待提供者注册到 Nacos..."
sleep 15

# Phase 2: Dubbo 消费者
echo ""
echo "  === Phase 2: Dubbo 消费者 ==="
for svc in mall-sso mall-order mall-search mall-seckill mall-resource mall-front; do
    echo "  启动 $svc..."
    systemctl start "$svc"
    sleep 5
    if systemctl is-active --quiet "$svc"; then
        echo "  ✓ $svc 启动成功"
    else
        echo "  ✗ $svc 启动失败，查看日志: journalctl -u $svc -n 50"
    fi
done

echo "  等待消费者就绪..."
sleep 15

# Phase 3: 网关
echo ""
echo "  === Phase 3: API 网关 ==="
echo "  启动 mall-gateway..."
systemctl start mall-gateway
sleep 5
if systemctl is-active --quiet mall-gateway; then
    echo "  ✓ mall-gateway 启动成功"
else
    echo "  ✗ mall-gateway 启动失败，查看日志: journalctl -u mall-gateway -n 50"
fi

# ---- 完成报告 ----
echo ""
echo "=============================================="
echo "  部署完成！服务状态："
echo "=============================================="

for svc in mall-ams mall-ums mall-product mall-sso mall-order mall-search mall-seckill mall-resource mall-front mall-gateway; do
    status=$(systemctl is-active "$svc" 2>/dev/null || echo "unknown")
    if [ "$status" = "active" ]; then
        echo "  ✓ $svc: $status"
    else
        echo "  ✗ $svc: $status"
    fi
done

echo ""
echo "常用命令："
echo "  查看单个服务状态:  sudo systemctl status mall-product"
echo "  启动单个服务:      sudo systemctl start mall-product"
echo "  停止单个服务:      sudo systemctl stop mall-product"
echo "  重启单个服务:      sudo systemctl restart mall-product"
echo "  查看服务日志:      sudo journalctl -u mall-product -f"
echo "  启动所有服务:      sudo systemctl start mall-ams mall-ums mall-product mall-sso mall-order mall-search mall-seckill mall-resource mall-front mall-gateway"
echo "  停止所有服务:      sudo systemctl stop mall-gateway mall-front mall-resource mall-seckill mall-search mall-order mall-sso mall-product mall-ums mall-ams"
echo "  设置开机自启:      sudo systemctl enable mall-ams mall-ums mall-product mall-sso mall-order mall-search mall-seckill mall-resource mall-front mall-gateway"
echo ""
echo "旧启动脚本备份位置: $BACKUP_DIR"