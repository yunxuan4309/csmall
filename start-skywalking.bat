@echo off
echo Starting SkyWalking OAP + UI...
cd /d "%~dp0deploy\skywalking\apache-skywalking-apm-bin\bin"
start "SkyWalking-OAP" cmd /c oapService.bat
timeout /t 10 /nobreak >nul
echo OAP started, now starting UI...
start "SkyWalking-UI" cmd /c webappService.bat
echo SkyWalking started: http://localhost:8088
