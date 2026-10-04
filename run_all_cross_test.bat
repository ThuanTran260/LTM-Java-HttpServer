@echo off
cd /d "%~dp0"
call compile.bat
if errorlevel 1 exit /b 1
echo [1/3] Start Mock IIS :8081 (fallback neu chua co IIS that :80)...
start "MockIIS" java -cp out mock.MockIisServer 8081
timeout /t 2 >nul
echo [2/3] Start Java Server :8080 (virtual threads)...
start "JavaServer" java -cp out server.SimpleHttpServer 8080
timeout /t 2 >nul
echo [3/3] Chay ma tran moc cheo...
java -cp out client.CrossTestClient
echo.
echo Firefox kiem tra tay:
echo   http://localhost:8080/  (Java Server)
echo   http://localhost:8080/iis/  (Proxy -^> IIS/Mock)
echo   http://localhost:80/  (IIS that, neu co)
pause
