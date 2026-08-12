@echo off
rem Stops the screenshot receiver no matter how it was started.
rem Finds the process LISTENING on port 8888 and kills it.
set PORT=8888
set FOUND=0
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%PORT%" ^| findstr LISTENING') do (
    taskkill /PID %%a /F >nul 2>nul
    echo Killed PID %%a (server on port %PORT%)
    set FOUND=1
)
if not "%FOUND%"=="1" (
    echo No server found listening on port %PORT%.
)
