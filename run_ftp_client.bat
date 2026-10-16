@echo off
cd /d "%~dp0"
call compile.bat
if errorlevel 1 exit /b 1
start "" java -cp out client.FtpClientApp
