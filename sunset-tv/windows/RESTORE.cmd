@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Остановка «Сумерки ТВ» и возврат исходной подсветки.
node.exe "%~dp0install.cjs" --restore
pause
