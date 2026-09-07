# 服务器 SSH 隧道访问指令
# 服务器 IP: 8.156.77.197
# 使用方法: 在本地终端执行 ssh 隧道命令，然后浏览器打开 localhost 地址

# Nacos 控制台（账号 nacos/nacos）
ssh -L 8848:localhost:8848 ecs-user@8.156.77.197
# → http://localhost:8848/nacos

# Sentinel Dashboard（账号 sentinel/sentinel）
ssh -L 18090:localhost:8090 ecs-user@8.156.77.197
# → http://localhost:18090

# RabbitMQ 管理界面（账号 guest/guest）
ssh -L 15672:localhost:15672 ecs-user@8.156.77.197
# → http://localhost:15672

# SkyWalking UI
ssh -L 18088:localhost:8088 ecs-user@8.156.77.197
# → http://localhost:18088

# 前端首页（无需隧道，公网可达）
# → http://8.156.77.197
