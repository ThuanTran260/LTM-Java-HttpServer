@echo off
setlocal
cd /d "%~dp0"
call compile.bat
if errorlevel 1 exit /b 1
echo Running HTTP integration tests. Real IIS checks require IIS on port 80.
java -cp out client.CrossTestClient
set "testExit=%ERRORLEVEL%"
echo.
echo Browser checks:
echo   http://localhost:8080/       Java Server - run_server.bat
echo   http://localhost:8080/iis/   Proxy to real IIS on port 80
echo   http://localhost/            Real IIS, if enabled
endlocal & exit /b %testExit%
