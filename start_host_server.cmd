@echo off
rem Launcher so "start_host_server" works from the command line.
rem Runs python directly (no PowerShell wrapper) so Ctrl+C reaches the
rem server and stops it cleanly.
cd /d "%~dp0"
where py >nul 2>nul
if %errorlevel%==0 (
    py screenshot_server.py 8888
) else (
    python screenshot_server.py 8888
)
