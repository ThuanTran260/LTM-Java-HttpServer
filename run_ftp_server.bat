@echo off
setlocal
cd /d "%~dp0"
call compile.bat
if errorlevel 1 exit /b 1

if "%~1"=="--demo" (
    set FTP_ENABLE_DEMO_ACCOUNT=true
    echo [INFO] Demo mode enabled: student/123456 active (Slide 30 standard).
) else (
    set FTP_ENABLE_DEMO_ACCOUNT=false
    set "FTP_STUDENT_PASSWORD="
    echo [INFO] Standard mode: student account is disabled. Use run_ftp_server.bat --demo to enable.
)

java -Dftp.enable.demo=%FTP_ENABLE_DEMO_ACCOUNT% -cp out server.ftp.SimpleFtpServer
endlocal
