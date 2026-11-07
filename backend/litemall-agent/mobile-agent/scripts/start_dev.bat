@echo off
rem mobile-agent 开发启动脚本（Windows）
rem 用法：双击或命令行执行 scripts\start_dev.bat
cd /d %~dp0..
if not exist .venv (
    echo [ERROR] 未找到 .venv，请先执行：python -m venv .venv && .venv\Scripts\pip install -r requirements.txt
    pause
    exit /b 1
)
call .venv\Scripts\activate.bat
echo [start] uvicorn app.main:app --host 127.0.0.1 --port 8100 --reload
uvicorn app.main:app --host 127.0.0.1 --port 8100 --reload
