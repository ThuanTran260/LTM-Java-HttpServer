@echo off
cd /d "%~dp0"
echo Dang bat dich vu IIS (w3svc)...
net start w3svc
if errorlevel 1 (
    echo.
    echo [Chu y] Vui long chuot phai vao file nay va chon "Run as administrator"!
) else (
    echo [OK] Da bat IIS thanh cong tren cong 80!
)
echo.
pause
