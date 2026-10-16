@echo off
setlocal DisableDelayedExpansion
cd /d "%~dp0"

if "%~1"=="--help" (
    echo Usage: run_server.bat [--help]
    echo   --help  Display this help message and exit.
    endlocal
    exit /b 0
)

if "%~1"=="-h" (
    echo Usage: run_server.bat [--help]
    echo   --help  Display this help message and exit.
    endlocal
    exit /b 0
)

if exist "%~dp0ftp.local.bat" call "%~dp0ftp.local.bat"

call compile.bat
if errorlevel 1 (
    endlocal
    exit /b 1
)

java -cp out server.SimpleHttpServer 8080
set "serverExit=%ERRORLEVEL%"
endlocal & exit /b %serverExit%
