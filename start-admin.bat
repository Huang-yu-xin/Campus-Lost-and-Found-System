@echo off
chcp 65001 >nul
title 校园失物招领 - 管理后台(5173)
cd /d "%~dp0apps\admin-web"

echo [管理后台] 正在启动...
echo [后台界面] http://localhost:5173  （登录账号见 deploy\.env 的 ADMIN_BOOTSTRAP_*）
start "" http://localhost:5173
call npm run dev
pause
