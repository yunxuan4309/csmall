@echo off
chcp 65001 >nul
echo =====================================================
echo 电商平台系统 - 数据库初始化脚本
echo =====================================================
echo.

set /p MYSQL_USER="请输入 MySQL 用户名 (默认 root): "
if "%MYSQL_USER%"=="" set MYSQL_USER=root

set /p MYSQL_PASSWORD="请输入 MySQL 密码： "
if "%MYSQL_PASSWORD%"=="" (
    echo 警告：未输入密码，使用空密码连接
)

echo.
echo 正在执行数据库初始化脚本...
echo.

echo [1/5] 执行商品模块 (PMS) 数据库初始化...
mysql -u%MYSQL_USER% -p%MYSQL_PASSWORD% < database\01-pms-product.sql
if %ERRORLEVEL% NEQ 0 (
    echo ✗ 商品模块数据库初始化失败！
    pause
    exit /b 1
)
echo ✓ 商品模块数据库初始化完成
echo.

echo [2/5] 执行后台管理模块 (AMS) 数据库初始化...
mysql -u%MYSQL_USER% -p%MYSQL_PASSWORD% < database\02-ams-admin.sql
if %ERRORLEVEL% NEQ 0 (
    echo ✗ 后台管理模块数据库初始化失败！
    pause
    exit /b 1
)
echo ✓ 后台管理模块数据库初始化完成
echo.

echo [3/5] 执行订单模块 (OMS) 数据库初始化...
mysql -u%MYSQL_USER% -p%MYSQL_PASSWORD% < database\03-oms-order.sql
if %ERRORLEVEL% NEQ 0 (
    echo ✗ 订单模块数据库初始化失败！
    pause
    exit /b 1
)
echo ✓ 订单模块数据库初始化完成
echo.

echo [4/5] 执行用户模块 (UMS) 数据库初始化...
mysql -u%MYSQL_USER% -p%MYSQL_PASSWORD% < database\04-ums-user.sql
if %ERRORLEVEL% NEQ 0 (
    echo ✗ 用户模块数据库初始化失败！
    pause
    exit /b 1
)
echo ✓ 用户模块数据库初始化完成
echo.

echo [5/5] 执行秒杀模块数据库初始化...
mysql -u%MYSQL_USER% -p%MYSQL_PASSWORD% < database\05-seckill.sql
if %ERRORLEVEL% NEQ 0 (
    echo ✗ 秒杀模块数据库初始化失败！
    pause
    exit /b 1
)
echo ✓ 秒杀模块数据库初始化完成
echo.

echo =====================================================
echo 所有数据库初始化完成！
echo =====================================================
echo.
echo 已创建的数据库:
echo   - cs_mall_pms (商品模块)
echo   - cs_mall_ams (后台管理模块)
echo   - cs_mall_oms (订单模块)
echo   - cs_mall_ums (用户模块)
echo   - cs_mall_seckill (秒杀模块)
echo.
echo 默认管理员账号:
echo   用户名：admin
echo   注意：密码已在代码中加密存储
echo.

set /p LOAD_TEST_DATA="是否加载基础测试数据？(Y/N): "
if /i "%LOAD_TEST_DATA%"=="Y" (
    echo.
    echo 正在加载基础测试数据...
    echo.
    mysql -u%MYSQL_USER% -p%MYSQL_PASSWORD% < database\init-test-data.sql
    if %ERRORLEVEL% NEQ 0 (
        echo ✗ 测试数据加载失败！
        pause
        exit /b 1
    )
    echo ✓ 测试数据加载完成
    echo.
)

echo =====================================================
echo 全部完成！
echo =====================================================
pause
