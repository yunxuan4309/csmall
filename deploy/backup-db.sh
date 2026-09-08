#!/bin/bash
# =====================================================================
# CoolShark 数据库每日备份脚本 (TODO #29)
# 部署位置: 服务器 /data/csmall/backup/backup-db.sh (ecs-user 所有)
# Cron: 30 2 * * * /data/csmall/backup/backup-db.sh >> /data/csmall/backup/backup.log 2>&1
#
# 本文件为仓库留档副本(单一事实来源), 服务器实际运行版若被手工改动
# 以服务器为准, 改动请同步回本文件。
#
# 说明:
# - 密码从 /data/csmall/.env 读取(不硬编码, 不入库)
# - docker exec mysqldump 6 库全量(ams/oms/pms/resource/seckill/ums)
# - --single-transaction: InnoDB 一致性快照, 备份期间不锁表
# - --routines --triggers: 含存储过程/触发器
# - gzip 压缩落盘 /data/csmall/backup/cs_mall_<时间戳>.sql.gz
# - 保留 7 天, find -mtime +7 自动清理
# - 恢复演练: zcat 备份 | docker exec -i csmall-mysql mysql -uroot -p'<密码>'
#   (TODO 铁律: 备份没验证过 = 没有备份, 建议定期恢复演练)
# =====================================================================

set -e

BACKUP_DIR=/data/csmall/backup
KEEP_DAYS=7
MYSQL_PASS=$(grep '^MYSQL_ROOT_PASSWORD=' /data/csmall/.env | cut -d= -f2- | tr -d '\r\n')
STAMP=$(date +%Y%m%d_%H%M)
OUTFILE="$BACKUP_DIR/cs_mall_${STAMP}.sql.gz"

echo "[$(date '+%F %T')] 开始备份 6 库 -> $OUTFILE"
docker exec csmall-mysql mysqldump -uroot -p"$MYSQL_PASS" \
  --single-transaction --routines --triggers \
  --databases cs_mall_ams cs_mall_oms cs_mall_pms cs_mall_resource cs_mall_seckill cs_mall_ums \
  2>/dev/null | gzip > "$OUTFILE"

# 验证备份非空
SIZE=$(stat -c%s "$OUTFILE" 2>/dev/null || echo 0)
if [ "$SIZE" -lt 1000 ]; then
  echo "[$(date '+%F %T')] ⚠️ 备份文件异常小(${SIZE}B), 疑似失败"
  exit 1
fi
echo "[$(date '+%F %T')] ✅ 备份完成: $OUTFILE ($(du -h $OUTFILE | cut -f1))"

# 清理 7 天前的备份
find "$BACKUP_DIR" -name "cs_mall_*.sql.gz" -mtime +$KEEP_DAYS -delete
echo "[$(date '+%F %T')] 清理完成(保留 $KEEP_DAYS 天)"
