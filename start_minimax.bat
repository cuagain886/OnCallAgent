@echo off
REM MiniMax 配置测试脚本 (Windows)

setlocal enabledelayedexpansion

echo ================================
echo MiniMax LLM 配置验证脚本
echo ================================
echo.

REM 1. 验证环境变量
echo [1/5] 检查环境变量...
if "%NVIDIA_API_KEY%"=="" (
    echo ❌ 错误：NVIDIA_API_KEY 环境变量未设置
    echo 请运行: set NVIDIA_API_KEY=your-nvidia-api-key
    pause
    exit /b 1
) else (
    echo ✓ NVIDIA_API_KEY 已设置 (长度: !NVIDIA_API_KEY!)
)

REM 2. 检查 Java 版本
echo.
echo [2/5] 检查 Java 版本...
where java >nul 2>nul
if errorlevel 1 (
    echo ❌ 错误：未找到 Java，请先安装 JDK 17 或更高版本
    pause
    exit /b 1
)

for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /r "version"') do (
    for /f "tokens=1 delims=." %%j in ('echo %%i') do (
        set JAVA_VERSION=%%j
    )
)

if %JAVA_VERSION% LSS 17 (
    echo ❌ 错误：Java 版本过低 (当前: %JAVA_VERSION%，需要: 17+)
    pause
    exit /b 1
) else (
    echo ✓ Java 版本: %JAVA_VERSION%
)

REM 3. 检查 Maven
echo.
echo [3/5] 检查 Maven...
where mvn >nul 2>nul
if errorlevel 1 (
    echo ❌ 错误：未找到 Maven，请先安装 Maven 3.6 或更高版本
    pause
    exit /b 1
) else (
    echo ✓ Maven 已安装
)

REM 4. 编译项目
echo.
echo [4/5] 编译项目...
call mvn clean package -DskipTests=true

if errorlevel 1 (
    echo ❌ 编译失败
    pause
    exit /b 1
)
echo ✓ 编译完成

REM 5. 启动应用
echo.
echo [5/5] 启动应用...
echo.
echo ================================
echo 应用将在 http://localhost:9900 运行
echo 按 Ctrl+C 停止应用
echo ================================
echo.

java -jar target/super-biz-agent-1.0-SNAPSHOT.jar

pause
