@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
title TV Assistant - Assistant verification
where node.exe >nul 2>&1
if not errorlevel 1 goto run
if exist "%ProgramFiles%\nodejs\node.exe" set "PATH=%ProgramFiles%\nodejs;%PATH%"
if exist "%LOCALAPPDATA%\Programs\nodejs\node.exe" set "PATH=%LOCALAPPDATA%\Programs\nodejs;%PATH%"
:run
node.exe "%~dp0assistant-verify.cjs"
echo.
pause
