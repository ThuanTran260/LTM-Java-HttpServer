@echo off
cd /d "%~dp0"
call compile.bat
if errorlevel 1 exit /b 1
set FTP_ENABLE_DEMO_ACCOUNT=true
java -cp out server.ftp.SimpleFtpServer
