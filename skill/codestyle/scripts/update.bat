@echo off
REM Update codestyle-server.jar to latest version for Windows

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "VERSION=2.1.0"
set "JAR_FILE=%SCRIPT_DIR%codestyle-server.jar"
set "BACKUP_FILE=%SCRIPT_DIR%codestyle-server.jar.backup"
set "DOWNLOAD_URL=https://github.com/itxaiohanglover/mcp-codestyle-server/releases/download/v%VERSION%/codestyle-server.jar"

echo.
echo 🔄 Codestyle Skill Updater
echo.

REM Check current version
if exist "%JAR_FILE%" (
    for %%A in ("%JAR_FILE%") do set "FILE_SIZE=%%~zA"
    set /a "FILE_SIZE_MB=!FILE_SIZE! / 1048576"
    echo 📦 Current version: !FILE_SIZE_MB! MB
) else (
    echo ⚠️  No existing installation found
)

echo 📦 Updating to v%VERSION%...
echo    URL: %DOWNLOAD_URL%
echo.

REM Backup old version
if exist "%JAR_FILE%" (
    move /y "%JAR_FILE%" "%BACKUP_FILE%" >nul 2>&1
    if !errorlevel! equ 0 (
        echo ✓ Backed up old version
    )
)

REM Try PowerShell first
where powershell >nul 2>&1
if %errorlevel% equ 0 (
    echo Using PowerShell to download...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $ProgressPreference = 'SilentlyContinue'; Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%JAR_FILE%' -UseBasicParsing}" 2>nul
    if !errorlevel! equ 0 goto :verify
    goto :failed
)

REM Try curl
where curl >nul 2>&1
if %errorlevel% equ 0 (
    echo Using curl to download...
    curl -L --progress-bar -o "%JAR_FILE%" "%DOWNLOAD_URL%" 2>nul
    if !errorlevel! equ 0 goto :verify
    goto :failed
)

REM Try wget
where wget >nul 2>&1
if %errorlevel% equ 0 (
    echo Using wget to download...
    wget --show-progress -O "%JAR_FILE%" "%DOWNLOAD_URL%" 2>nul
    if !errorlevel! equ 0 goto :verify
    goto :failed
)

echo ❌ Error: No download tool available
goto :failed

:verify
if exist "%JAR_FILE%" (
    del /f /q "%BACKUP_FILE%" >nul 2>&1
    for %%A in ("%JAR_FILE%") do set "FILE_SIZE=%%~zA"
    set /a "FILE_SIZE_MB=!FILE_SIZE! / 1048576"
    echo.
    echo ✓ Updated to v%VERSION% ^(!FILE_SIZE_MB! MB^)
    echo ✓ Update complete
    exit /b 0
) else (
    goto :failed
)

:failed
echo ❌ Update failed
if exist "%BACKUP_FILE%" (
    move /y "%BACKUP_FILE%" "%JAR_FILE%" >nul 2>&1
    echo ✓ Restored backup
)
exit /b 1

