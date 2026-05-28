# RabbitMQ 启动问题报告

## 环境信息

| 项目 | 信息 |
|------|------|
| 操作系统 | Windows 11 |
| RabbitMQ 版本 | 4.3.0 |
| 安装路径 | D:\rabbitMQ\rabbitmq_server-4.3.0 |
| 原计算机名 | 含中文字符（乱码显示为 `´ó¸ç4309`） |
| 修改后计算机名 | yunxuanxie |

---

## 问题描述

RabbitMQ 服务启动后，`rabbitmqctl status` 无法连接，AMQP 端口 5672 不监听，管理界面 http://localhost:15672 无法访问。

---

## 错误现象

### 现象一：端口 25672 被占用

```
ERROR: could not bind to distribution port 25672, it is in use by another node: rabbit@´ó¸ç4309
```

原因：之前启动的 RabbitMQ 实例残留，端口未释放。

### 现象二：rabbitmqctl 无法连接（Erlang distribution failed）

```
TCP connection succeeded but Erlang distribution failed
suggestion: check if the Erlang cookie is identical for all server nodes and CLI tools
```

原因：服务端节点名因中文计算机名变成乱码，CLI 工具无法与之通信。

### 现象三：服务启动成功但 5672 端口不监听

服务显示"启动成功"，但 `netstat -ano | findstr :5672` 无输出。

查看日志发现：

```
node           : rabbit@nohost
Error during startup: {error,no_epmd_port}
BOOT FAILED
```

原因：Erlang 无法解析中文主机名，回退为 `rabbit@nohost`，导致 epmd 注册失败，RabbitMQ 应用层启动后崩溃。

---

## 根本原因

**计算机名包含中文字符**，导致：

1. Erlang 节点名出现乱码（`rabbit@´ó¸ç4309`）
2. 服务端节点名回退为 `rabbit@nohost`
3. CLI 工具使用 `rabbit@乱码主机名` 连接，与服务端节点名不匹配
4. Erlang Cookie 因节点名不同无法同步
5. RabbitMQ 应用层因 epmd 端口注册失败而崩溃

---

## 解决过程

### 第一步：修改计算机名为纯英文

1. `Win + R` → 输入 `sysdm.cpl` → 回车
2. 点击"计算机名" → "更改"
3. 将计算机名改为 `yunxuanxie`
4. 重启电脑

### 第二步：彻底卸载旧版 RabbitMQ

1. 停止服务：`net stop RabbitMQ`
2. 卸载服务：`rabbitmq-service remove`
3. 通过"设置 → 应用"卸载 RabbitMQ 和 Erlang
4. 清理残留数据：
   ```powershell
   Remove-Item -Recurse -Force "C:\Users\17214\AppData\Roaming\RabbitMQ" -ErrorAction SilentlyContinue
   Remove-Item -Recurse -Force "C:\Windows\System32\config\systemprofile\AppData\Roaming\RabbitMQ" -ErrorAction SilentlyContinue
   Remove-Item -Force "c:\Users\17214\.erlang.cookie" -ErrorAction SilentlyContinue
   Remove-Item -Force "C:\Windows\System32\config\systemprofile\.erlang.cookie" -ErrorAction SilentlyContinue
   ```

### 第三步：重新安装 RabbitMQ

1. 安装 Erlang OTP
2. 安装 RabbitMQ（安装到 D:\rabbitMQ\rabbitmq_server-4.3.0）

### 第四步：启动前设置环境变量

```powershell
[System.Environment]::SetEnvironmentVariable("RABBITMQ_NODENAME", "rabbit@localhost", "Machine")
```

此变量确保 RabbitMQ 使用 `rabbit@localhost` 作为节点名，避免主机名相关问题。

### 第五步：启动服务

```cmd
net start RabbitMQ
```

### 第六步：启用管理界面

```cmd
"D:\rabbitMQ\rabbitmq_server-4.3.0\sbin\rabbitmq-plugins.bat" enable rabbitmq_management
net stop RabbitMQ
net start RabbitMQ
```

### 第七步：验证

```cmd
netstat -ano | findstr :5672
```

输出 `LISTENING` 即表示成功。浏览器访问 http://localhost:15672 ，账号密码 `guest/guest`。

---

## 经验总结

1. **Windows 上安装 RabbitMQ，计算机名必须为纯英文**，否则 Erlang 运行时会出现各种莫名其妙的节点名问题
2. **遇到 RabbitMQ 启动失败，第一时间查看日志**，日志路径通常在 `C:\Users\<用户名>\AppData\Roaming\RabbitMQ\log\`
3. **`rabbit@nohost` 是 Erlang 无法解析主机名的典型标志**，需要在 `C:\Windows\System32\drivers\etc\hosts` 中添加主机名映射，或设置 `RABBITMQ_NODENAME` 环境变量
4. **`RABBITMQ_NODENAME=rabbit@localhost` 是最稳妥的方案**，localhost 在任何环境下都能正确解析
5. **清理旧数据很重要**，旧的 mnesia 数据库绑定了旧的节点名，不清理会导致新节点无法正常启动
6. **Windows 服务模式启动失败时，可尝试命令行模式**（`rabbitmq-server`）直接运行，便于实时查看错误日志
7. **PowerShell 和 cmd 语法不同**，如 `rd /s /q` 在 PowerShell 中应改为 `Remove-Item -Recurse -Force`，`sc query` 在 PowerShell 中 `sc` 是 `Set-Content` 的别名
8. **`net stop/start` 需要管理员权限**，普通权限会报 `Access is denied`（错误码 5）
