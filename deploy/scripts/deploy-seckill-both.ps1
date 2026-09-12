# deploy-seckill-both.ps1 —— 秒杀类改动的【双实例】部署与验收（G14 / TODO #69 的落地）
#
# 为什么必须有它：秒杀是**双实例**（老机 csmall-seckill:10007 + 新机 csmall-seckill-2:10017），
#   两者注册**同一个 Dubbo 服务**并**竞争消费同一个 MQ 队列** ⇒ **只升一台时，MQ 有一半消息走旧逻辑**。
#   2026-09-12 实测：#69（秒杀给错误商品加销量）修复后，只重建了老机，A/B **仍不通过** ——
#   那条 MQ 被新机副本（旧 jar）消费了（副本日志 05:48:38「秒杀成功记录处理完成」）。
#
# 用法（在本机执行；需 ai-deepseek 私钥，脚本只做「构建 + 校验 + docker compose」，
#       `/data/csmall/jars/` 的替换需 ecs-user，脚本会把命令打出来交给你执行）：
#   pwsh -File deploy-seckill-both.ps1 -CheckOnly          # 只读：两台逐台核对（随时可跑）
#   pwsh -File deploy-seckill-both.ps1                     # 全流程（会停下等你替换 jars）
#   pwsh -File deploy-seckill-both.ps1 -AssumeJarSynced    # 已替换好，直接建镜像 + 重建 + 校验
#
# 📌 本文件是 **UTF-8 with BOM**（PowerShell 5.1 读中文必需）——编辑时请保留 BOM，
#    否则中文会按 ANSI 解析、脚本报「字符串缺少终止符」之类的怪错。

# ⚠️ 踩坑记录（2026-09-12）：本仓库脚本里**不要用单字母函数名** —— PowerShell 的 `r`/`R` 是 `Invoke-History`
#   的别名，`R $host @'...'@` 会被解析成"调用历史命令"，**静默空跑**（我因此让一段 5 步编排全部没执行，
#   生产还停在"AI 指向 mock"的状态上）。本文件统一用 `Ssh-Run`。
[CmdletBinding()]
param(
    [switch]$CheckOnly,
    [switch]$AssumeJarSynced,
    [string]$JarPath = "D:\java\csmall\mall-seckill\mall-seckill-webapi\target\mall-seckill-webapi-0.0.1-SNAPSHOT.jar"
)

$ErrorActionPreference = 'Stop'
$Tag = Get-Date -Format 'yyyyMMdd-HHmm'   # 镜像版本 tag（G14：tag 即版本，两台必须一致）
$Key = "$env:USERPROFILE\AppData\Local\csmall-ssh\ai-deepseek_key"

# 两台实例的"身份"：容器名 / compose 服务名 / HTTP 端口 / jars 路径
$Nodes = @(
    [pscustomobject]@{ Name='老机'; HostIp='8.156.77.197'; Container='csmall-seckill';   Service='mall-seckill';   Port=10007; Jars='/data/csmall/jars/mall-seckill.jar' }
    [pscustomobject]@{ Name='新机'; HostIp='47.109.70.197'; Container='csmall-seckill-2'; Service='mall-seckill-2'; Port=10017; Jars='/data/csmall/jars/mall-seckill.jar' }
)

function Invoke-Remote {
    param([string]$HostIp, [string]$Script)
    $body = ($Script -replace "`r`n", "`n")
    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($body))
    ssh -i $Key -o BatchMode=yes -o ConnectTimeout=20 -o ServerAliveInterval=30 "ai-deepseek@$HostIp" "echo $b64 | base64 -d | bash"
}

function Get-NodeFacts {
    param([pscustomobject]$N)
    $script = @"
echo -n 'container_md5='; docker exec $($N.Container) md5sum /app/app.jar 2>/dev/null | awk '{print `$1}'
echo -n 'host_jar_md5=';  md5sum $($N.Jars) 2>/dev/null | awk '{print `$1}'
echo -n 'status=';        docker ps --filter name=$($N.Container) --format '{{.Status}}'
echo -n 'http=';          curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$($N.Port)/seckill/spu/list
echo
"@
    $out = Invoke-Remote -HostIp $N.HostIp -Script $script
    $facts = @{ container_md5 = ''; host_jar_md5 = ''; status = ''; http = '' }
    foreach ($line in ($out -split "`n")) {
        if ($line -match '^(\w+)=(.*)$') { $facts[$Matches[1]] = $Matches[2].Trim() }
    }
    return $facts
}

function Show-Check {
    param([string]$ExpectedMd5)
    Write-Host "`n=== 双实例核对（$(Get-Date -Format 'HH:mm:ss')）===" -ForegroundColor Cyan
    $rows = @()
    foreach ($n in $Nodes) {
        $f = Get-NodeFacts -N $n
        $ok = ($f.container_md5 -eq $f.host_jar_md5) -and ($f.status -like 'Up*') -and ($f.http -eq '200')
        if ($ExpectedMd5) { $ok = $ok -and ($f.container_md5 -eq $ExpectedMd5) }
        $rows += [pscustomobject]@{
            实例 = $n.Name; 容器 = $n.Container; 容器内jar = $f.container_md5.Substring(0,[Math]::Min(12,$f.container_md5.Length))
            jars文件 = $f.host_jar_md5.Substring(0,[Math]::Min(12,$f.host_jar_md5.Length)); 状态 = $f.status; HTTP = $f.http; 判定 = $(if ($ok) { 'OK' } else { 'FAIL' })
        }
    }
    $rows | Format-Table -AutoSize | Out-String | Write-Host
    $md5s = ($rows | ForEach-Object { $_.容器内jar } | Sort-Object -Unique)
    if ($md5s.Count -ne 1) {
        Write-Host "🔴 两台实例的 jar md5 不一致 ⇒ 违反 G14（双实例必须同版本），此时 MQ 会有一半消息走旧逻辑" -ForegroundColor Red
        return $false
    }
    Write-Host "✅ 两台实例 jar md5 一致（$($md5s[0])…）" -ForegroundColor Green
    return -not ($rows | Where-Object { $_.判定 -eq 'FAIL' })
}

if ($CheckOnly) {
    $ok = Show-Check
    if ($ok) { exit 0 } else { exit 1 }
}

# ---------- 全流程 ----------
if (-not (Test-Path $JarPath)) { throw "找不到 jar：$JarPath（先跑 mvn -o -B -DskipTests -pl mall-seckill/mall-seckill-webapi -am package）" }
$jarMd5 = (Get-FileHash $JarPath -Algorithm MD5).Hash.ToLower()
$jarSize = (Get-Item $JarPath).Length
Write-Host "`n① 本地 jar：$JarPath" -ForegroundColor Cyan
Write-Host "   大小=$jarSize 字节  md5=$jarMd5"

Write-Host "`n② 分发到两台 /tmp（ai-deepseek 可写）" -ForegroundColor Cyan
foreach ($n in $Nodes) {
    scp -i $Key -o BatchMode=yes -o ConnectTimeout=20 $JarPath "ai-deepseek@$($n.HostIp):/tmp/mall-seckill-new.jar"
    $remoteMd5 = (Invoke-Remote -HostIp $n.HostIp -Script "md5sum /tmp/mall-seckill-new.jar | awk '{print `$1}'").Trim()
    if ($remoteMd5 -ne $jarMd5) { throw "$($n.Name) 传输后 md5 不一致（$remoteMd5 ≠ $jarMd5）" }
    Write-Host "   $($n.Name) ✅ 已就位且 md5 一致"
}

Write-Host "`n③ 需要你用 ecs-user 在【两台】各执行一次（/data/csmall/jars 归 ecs-user）：" -ForegroundColor Yellow
foreach ($n in $Nodes) {
    Write-Host "`n   # --- $($n.Name) （$($n.HostIp)）---" -ForegroundColor Yellow
    Write-Host "   cp $($n.Jars) /data/csmall/jars/backup-$(Get-Date -Format 'yyyyMMdd')-mall-seckill-old.jar"
    Write-Host "   rm -f $($n.Jars)            # 旧件可能是 root 所有，故 rm 后 cp（目录属 ecs-user，可删）"
    Write-Host "   cp -v /tmp/mall-seckill-new.jar $($n.Jars)"
    Write-Host "   md5sum $($n.Jars)           # 期望 $jarMd5"
}

if (-not $AssumeJarSynced) {
    Write-Host "`n替换完成后回车继续（或 Ctrl+C 中止）..." -ForegroundColor Yellow
    Read-Host | Out-Null
}

Write-Host "`n④ 逐台核对 jars 是否已替换" -ForegroundColor Cyan
foreach ($n in $Nodes) {
    $f = Get-NodeFacts -N $n
    if ($f.host_jar_md5 -ne $jarMd5) { throw "$($n.Name) 的 $($n.Jars) md5=$($f.host_jar_md5) ≠ 期望 $jarMd5 —— 请先按 ③ 替换" }
    Write-Host "   $($n.Name) ✅ jars 已更新"
}

Write-Host "`n⑤ 两台各自构建镜像 + 重建容器（共用 tag：$Tag）" -ForegroundColor Cyan
# 🔴 2026-09-12 修复（实测踩到）：**镜像只有一个构建者 = `mall-seckill` 这个服务定义**。
#   G14 之后 `mall-seckill-2` **故意不带 build 段**（复用同一镜像），所以
#   `docker compose build mall-seckill-2` 在新机必然**失败**（无 build context），而它写在管道里
#   （`| tail -3` 会吞掉退出码）⇒ 构建被静默跳过；随后 `up -d mall-seckill-2`：
#     · 该 tag 本地不存在 ⇒ 去 Docker Hub 拉 `csmall-mall-seckill:<tag>` ⇒ 超时（新机拉不到）；
#     · 该 tag 恰好存在   ⇒ **静默用旧镜像**（违反 G14，且容器 Up、HTTP 200，看不出问题）。
#   ⇒ 正确做法：**两台都用 `mall-seckill` 这个服务定义来构建镜像**（context=./jars、
#     dockerfile=../dockerfiles/mall-seckill.Dockerfile，两台都有），再各自 `up -d` 自己的服务。
#   ⇒ 另外：**tag 要用新值，不要复用旧 tag** —— 万一没构建成功，`up -d` 会响亮失败而不是静默用旧镜像。
foreach ($n in $Nodes) {
    Write-Host "   --- $($n.Name)：docker compose build mall-seckill（共享镜像）&& up -d $($n.Service) ---"
    Invoke-Remote -HostIp $n.HostIp -Script @"
set -e
cd /data/csmall
MALL_SECKILL_TAG=$Tag docker compose build mall-seckill 2>&1 | tail -3
MALL_SECKILL_TAG=$Tag docker compose up -d $($n.Service) 2>&1 | tail -3
"@ | Write-Host
}

Write-Host "`n⑥ 等启动 + 逐台核对（容器内 jar md5 / 状态 / HTTP）" -ForegroundColor Cyan
$deadline = (Get-Date).AddMinutes(5)
do {
    Start-Sleep -Seconds 15
    $rows = @()
    foreach ($n in $Nodes) { $rows += [pscustomobject]@{ n = $n; f = (Get-NodeFacts -N $n) } }
    $ready = ($rows | Where-Object { $_.f.status -like 'Up*' -and $_.f.http -eq '200' }).Count -eq $Nodes.Count
} while (-not $ready -and (Get-Date) -lt $deadline)

$final = Show-Check -ExpectedMd5 $jarMd5
Write-Host "`n⑦ 收尾建议：跑一次秒杀 A/B（deploy/scripts/sim/simulate_data.py --with-seckill --seckill-restore-wait 20）" -ForegroundColor Cyan
if ($final) { Write-Host "✅ 双实例部署验收通过" -ForegroundColor Green; exit 0 }
Write-Host "🔴 仍有实例未就绪/版本不一致，请查 docker logs" -ForegroundColor Red
exit 1
