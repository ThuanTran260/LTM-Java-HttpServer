@echo off
cd /d "%~dp0"
call compile.bat
if errorlevel 1 (echo COMPILE FAILED & exit /b 1)
java --enable-native-access=ALL-UNNAMED -cp out client.CrossTestFtpClient
exit /b %ERRORLEVEL%
