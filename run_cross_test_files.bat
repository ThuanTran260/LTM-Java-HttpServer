@echo off
setlocal
cd /d "%~dp0"
call compile.bat
if errorlevel 1 (
    echo COMPILE FAILED
    endlocal
    exit /b 1
)
java -cp out client.CrossTestFilesClient %*
set "testExit=%ERRORLEVEL%"
endlocal & exit /b %testExit%
