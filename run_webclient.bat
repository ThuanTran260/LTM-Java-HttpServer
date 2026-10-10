@echo off
cd /d "%~dp0"
call compile.bat
if errorlevel 1 exit /b 1
echo Dang mo Web Client (Swing). Nho bat Java Server truoc: run_server.bat
java -cp out client.WebClientApp %*
