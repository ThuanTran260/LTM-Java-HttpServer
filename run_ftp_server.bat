@echo off
setlocal DisableDelayedExpansion
cd /d "%~dp0"

if "%~1"=="--help" (
    echo Usage: run_ftp_server.bat [--demo] [--help]
    echo   --demo  Enable student demo account [Slide 30 standard].
    echo   --help  Display this help message and exit.
    endlocal
    exit /b 0
)

if "%~1"=="-h" (
    echo Usage: run_ftp_server.bat [--demo] [--help]
    echo   --demo  Enable student demo account [Slide 30 standard].
    echo   --help  Display this help message and exit.
    endlocal
    exit /b 0
)

if exist "%~dp0ftp.local.bat" call "%~dp0ftp.local.bat"

call compile.bat
if errorlevel 1 exit /b 1

if "%~1"=="--demo" (
    set FTP_ENABLE_DEMO_ACCOUNT=true
    echo [INFO] Demo mode enabled: student account is active [Slide 30 standard].
) else (
    set FTP_ENABLE_DEMO_ACCOUNT=false
    set "FTP_STUDENT_PASSWORD="
    echo [INFO] Standard mode: student account is disabled. Use run_ftp_server.bat --demo to enable.
)

java -Dftp.enable.demo=%FTP_ENABLE_DEMO_ACCOUNT% -cp out server.ftp.SimpleFtpServer
endlocal
exit /b %ERRORLEVEL%
