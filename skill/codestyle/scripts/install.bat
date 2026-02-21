@echo off
REM Codestyle Skill Installation Script for Windows
REM Auto-downloads codestyle-server.jar on first use

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "VERSION=2.1.0"
set "JAR_FILE=%SCRIPT_DIR%codestyle-server.jar"
set "DOWNLOAD_URL=https://github.com/itxaiohanglover/mcp-codestyle-server/releases/download/v%VERSION%/codestyle-server.jar"

echo.
echo 🚀 Codestyle Skill Installer v%VERSION%
echo.

REM Check if JAR exists
if exist "%JAR_FILE%" (
    for %%A in ("%JAR_FILE%") do set "FILE_SIZE=%%~zA"
    set /a "FILE_SIZE_MB=!FILE_SIZE! / 1048576"
    echo ✓ codestyle-server.jar already exists ^(!FILE_SIZE_MB! MB^)
    echo ✓ Installation complete
    exit /b 0
)

echo 📦 Downloading codestyle-server v%VERSION%...
echo    URL: %DOWNLOAD_URL%
echo.

REM Try PowerShell first (Windows 7+)
where powershell >nul 2>&1
if %errorlevel% equ 0 (
    echo Using PowerShell to download...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $ProgressPreference = 'SilentlyContinue'; Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%JAR_FILE%' -UseBasicParsing}" 2>nul
    if !errorlevel! equ 0 goto :verify
)

REM Try curl (Windows 10+)
where curl >nul 2>&1
if %errorlevel% equ 0 (
    echo Using curl to download...
    curl -L --progress-bar -o "%JAR_FILE%" "%DOWNLOAD_URL%" 2>nul
    if !errorlevel! equ 0 goto :verify
)

REM Try wget if available
where wget >nul 2>&1
if %errorlevel% equ 0 (
    echo Using wget to download...
    wget --show-progress -O "%JAR_FILE%" "%DOWNLOAD_URL%" 2>nul
    if !errorlevel! equ 0 goto :verify
)

echo ❌ Error: No download tool available
echo    Please install PowerShell, curl, or wget
exit /b 1

:verify
if exist "%JAR_FILE%" (
    for %%A in ("%JAR_FILE%") do set "FILE_SIZE=%%~zA"
    set /a "FILE_SIZE_MB=!FILE_SIZE! / 1048576"
    echo.
    echo ✓ Downloaded successfully ^(!FILE_SIZE_MB! MB^)
    echo ✓ Installation complete
    echo.
    echo 💡 Usage:
    echo    %SCRIPT_DIR%codestyle.bat search "CRUD"
    echo    %SCRIPT_DIR%codestyle.bat get "path"
    exit /b 0
) else (
    echo ❌ Download failed
    exit /b 1
)

