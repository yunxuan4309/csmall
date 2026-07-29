@echo off
set JMETER_HOME=D:\Jmeter\apache-jmeter-5.6.3

if not exist "%JMETER_HOME%\bin\jmeter.bat" (
    echo [ERROR] JMeter not found at %JMETER_HOME%
    pause
    exit /b 1
)

echo CoolShark Seckill Load Test
echo JMeter 100 threads, 1sec ramp-up
echo.
echo BEFORE RUNNING:
echo   1. Sentinel Dashboard: http://localhost:8090 (sentinel/sentinel)
echo      - Add flow rule for "秒杀订单提交" with QPS=10
echo   2. SkyWalking UI: http://localhost:8088
echo      - Watch mall-seckill QPS spike
echo   3. All microservices running (mall-sso + mall-seckill required)
echo.
echo Press any key to start JMeter GUI...
pause >nul

start "JMeter" "%JMETER_HOME%\bin\jmeter.bat" -t "%~dp0csmall-seckill-demo.jmx"

echo.
echo JMeter started in GUI mode.
echo Check results:
echo   - View Results Tree: green=OK, red=blocked
echo   - Summary Report: throughput / avg response time
echo   - Sentinel: http://localhost:8090
echo   - SkyWalking: http://localhost:8088
pause
