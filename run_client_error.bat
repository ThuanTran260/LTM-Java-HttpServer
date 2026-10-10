@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist out call compile.bat
echo ============================================
echo  HTTP CLIENT XU LY LOI (Slide 19)
echo  Phan biet: HTTP Error vs Loi ket noi/Timeout
echo ============================================
echo.
echo [1/2] Truong hop THANH CONG - server dang chay tren :8080
echo --------------------------------------------
java -cp out client.HttpClientErrorDemo
echo.
echo [2/2] Truong hop LOI KET NOI - goi port 9099 khong co server
echo --------------------------------------------
java -cp out client.HttpClientErrorDemo http://localhost:9099/api/time
echo.
echo --------------------------------------------
echo  Xuat hien "HTTP Error: ..."  = CO phan hoi tu server (doc StatusCode)
echo  Xuat hien "Loi ket noi: ..." = KHONG co phan hoi tu server (bat Exception)
echo --------------------------------------------
echo.
pause
