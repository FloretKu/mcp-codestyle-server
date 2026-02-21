@echo off
REM Codestyle Repository Initialization Script for Windows
REM Auto-clones codestyle-repository if local cache is empty

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "REPO_URL=https://github.com/itxaiohanglover/codestyle-repository.git"

REM 简化路径处理 - 直接使用默认路径，不再依赖 PowerShell 解析 JSON
set "CACHE_DIR=%USERPROFILE%\.codestyle\cache\codestyle-cache"

echo.
echo 🔍 Checking local repository...
echo    Path: %CACHE_DIR%
echo.

REM Check if cache directory exists
if not exist "%CACHE_DIR%" (
    echo 📁 Creating cache directory...
    mkdir "%CACHE_DIR%" 2>nul
    goto :clone_repo
)

REM Check if cache directory is empty (excluding lucene-index)
set "IS_EMPTY=1"
for /d %%D in ("%CACHE_DIR%\*") do (
    if /i not "%%~nxD"=="lucene-index" (
        set "IS_EMPTY=0"
        goto :check_done
    )
)
for %%F in ("%CACHE_DIR%\*") do (
    set "IS_EMPTY=0"
    goto :check_done
)

:check_done
if "%IS_EMPTY%"=="0" (
    echo ✓ Local repository already initialized
    exit /b 0
)

:clone_repo
echo 📦 Local repository is empty
echo 🚀 Cloning codestyle-repository...
echo    URL: %REPO_URL%
echo.

REM Check if git is available
where git >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Error: Git is not installed
    echo.
    echo Please install Git from: https://git-scm.com/download/win
    echo.
    echo Alternative: Download templates manually from:
    echo    https://github.com/itxaiohanglover/codestyle-repository/archive/refs/heads/main.zip
    echo.
    echo Extract to: %CACHE_DIR%
    exit /b 1
)

REM Clone repository to temp directory
set "TEMP_DIR=%TEMP%\codestyle-repo-%RANDOM%"
echo Cloning to temporary directory...
echo    Temp: %TEMP_DIR%
echo    This may take 10-30 seconds, please wait...
echo.

REM Start git clone in background (suppress output)
start /B git clone --depth 1 --quiet "%REPO_URL%" "%TEMP_DIR%" 2>nul

REM Wait for .git directory to appear (max 60 seconds)
set "WAIT_COUNT=0"
:wait_clone
if exist "%TEMP_DIR%\.git" goto :clone_success
ping 127.0.0.1 -n 2 >nul
set /a WAIT_COUNT+=1
if %WAIT_COUNT% lss 30 (
    echo    Cloning... %WAIT_COUNT%/30
    goto :wait_clone
)

REM Timeout - clone failed
echo.
echo ❌ Clone timeout ^(waited 60 seconds^)
echo.
echo Please check:
echo    1. Internet connection
echo    2. Repository URL: %REPO_URL%
echo    3. Firewall settings
echo.
echo Manual fix:
echo    1. Download: https://github.com/itxaiohanglover/codestyle-repository/archive/refs/heads/main.zip
echo    2. Extract to: %CACHE_DIR%
rd /s /q "%TEMP_DIR%" 2>nul
exit /b 1

:clone_success
REM Wait a bit more to ensure all files are written
ping 127.0.0.1 -n 3 >nul
echo.
echo ✓ Clone successful

REM Copy templates to cache directory
echo.
echo 📋 Copying templates to cache...
echo    From: %TEMP_DIR%
echo    To:   %CACHE_DIR%

REM Ensure cache directory exists
if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"

REM Copy files
xcopy /E /I /Y /Q "%TEMP_DIR%\*" "%CACHE_DIR%\" >nul 2>&1

REM Verify copy by checking if files exist
set "COPY_SUCCESS=0"
for /d %%D in ("%CACHE_DIR%\*") do (
    if /i not "%%~nxD"=="lucene-index" (
        set "COPY_SUCCESS=1"
        goto :copy_done
    )
)
for %%F in ("%CACHE_DIR%\*") do (
    set "COPY_SUCCESS=1"
    goto :copy_done
)

:copy_done
if "%COPY_SUCCESS%"=="0" (
    echo ❌ Copy failed - no files found in cache directory
    echo.
    echo Diagnostics:
    echo    Source directory contents:
    dir /b "%TEMP_DIR%" 2>nul
    echo.
    echo    Target directory: %CACHE_DIR%
    rd /s /q "%TEMP_DIR%" 2>nul
    exit /b 1
)

echo ✓ Copy successful

REM Clean up
rd /s /q "%TEMP_DIR%" 2>nul

REM Remove .git directory from cache
if exist "%CACHE_DIR%\.git" (
    rd /s /q "%CACHE_DIR%\.git" 2>nul
)

REM Verify templates were copied
echo.
echo 📊 Verifying templates...
set "TEMPLATE_COUNT=0"
for /d %%D in ("%CACHE_DIR%\*") do (
    if /i not "%%~nxD"=="lucene-index" (
        set /a TEMPLATE_COUNT+=1
        echo    ✓ %%~nxD
    )
)

if %TEMPLATE_COUNT%==0 (
    echo.
    echo ❌ No templates found after copy
    echo    Repository may be empty or copy failed
    echo.
    echo Manual fix:
    echo    1. Download: https://github.com/itxaiohanglover/codestyle-repository/archive/refs/heads/main.zip
    echo    2. Extract to: %CACHE_DIR%
    exit /b 1
)

echo.
echo ✓ Repository initialized successfully
echo    Found %TEMPLATE_COUNT% template group(s)
echo.

exit /b 0
