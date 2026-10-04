@echo off
cd /d "%~dp0"
echo Dang tat dich vu IIS (w3svc)...
net stop w3svc
if errorlevel 1 (
    echo.
    echo [Chu y] Vui long chuot phai vao file nay va chon "Run as administrator"!
) else (
    echo [OK] Da tat IIS thanh cong. Cong 80 da duoc giai phong!
)
echo.
pause
