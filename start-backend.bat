@echo off
chcp 65001 >nul
title 校园失物招领 - 后端(8080)
cd /d "%~dp0server"

rem JDK17（系统默认是 JDK8，必须指向这里）
set "JAVA_HOME=C:\Users\huangyx\.jdks\ms-17.0.18"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem 读取 deploy\.env 的配置（DB_PASSWORD / ADMIN_BOOTSTRAP_* 等）
for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%~dp0deploy\.env") do set "%%a=%%b"

set SPRING_PROFILES_ACTIVE=dev
set MOCK_LOGIN_ENABLED=true

echo [后端] 正在启动，首次编译约 30-60 秒，看到 "Started ..." 即成功
echo [后端] 接口健康检查: http://localhost:8080/api/v1/health
echo [后端] Swagger:       http://localhost:8080/api/v1/swagger-ui/index.html
call mvn spring-boot:run
pause
