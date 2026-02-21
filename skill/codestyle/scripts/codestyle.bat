@echo off
REM Codestyle CLI Wrapper - 简化版
REM 静默自动初始化，只在首次使用时提示一次

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "JAR_FILE=%SCRIPT_DIR%codestyle-server.jar"
set "INIT_MARKER=%SCRIPT_DIR%.initialized"

REM 检查首次使用
if not exist "%INIT_MARKER%" (
    echo.
    echo [Codestyle] 首次使用，正在初始化...
    echo.
    
    REM 自动安装 JAR
    if not exist "%JAR_FILE%" (
        echo [1/2] 下载 JAR 文件...
        call "%SCRIPT_DIR%install.bat" >nul 2>&1
        if !errorlevel! neq 0 (
            echo JAR 下载失败
            echo.
            echo 请检查：
            echo   1. 网络连接
            echo   2. Java 17+ 已安装
            echo.
            exit /b 1
        )
        echo ✓ JAR 下载完成
    )
    
    REM 自动初始化仓库
    echo [2/2] 克隆模板仓库...
    call "%SCRIPT_DIR%init-repository.bat"
    if !errorlevel! neq 0 (
        echo ⚠️  仓库克隆失败（将使用空模板）
        echo.
        echo 手动修复：
        echo   1. 下载: https://github.com/itxaiohanglover/codestyle-repository/archive/refs/heads/main.zip
        echo   2. 解压到: %USERPROFILE%\.codestyle\cache\codestyle-cache
        echo   3. 重新运行命令
        echo.
    ) else (
        echo ✓ 仓库克隆完成
    )
    
    REM 创建标记
    echo. > "%INIT_MARKER%"
    echo.
    echo ✓ 初始化完成
    echo.
)

REM 执行命令
java -jar "%JAR_FILE%" %*
