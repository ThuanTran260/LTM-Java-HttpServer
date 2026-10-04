@echo off
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out src\model\*.java src\service\*.java src\server\*.java src\mock\*.java src\client\*.java
if errorlevel 1 (echo COMPILE FAIL & exit /b 1)
echo COMPILED OK - javac 24
