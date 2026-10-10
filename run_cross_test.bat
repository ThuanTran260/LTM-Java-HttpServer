@echo off
cd /d "%~dp0"
java -cp out client.CrossTestClient
echo.
pause
