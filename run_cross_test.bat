@echo off
cd /d "%~dp0"
echo Dang chay CrossTestClient (Kiem tra ma tran moc cheo)...
java -cp out client.CrossTestClient
echo.
pause
