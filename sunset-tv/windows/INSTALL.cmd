@echo off
chcp 65001 >nul
cd /d "%~dp0"
where node.exe >nul 2>nul
if errorlevel 1 (
  echo Не найден Node.js. Установи Node.js 20 или новее с nodejs.org.
  pause
  exit /b 1
)
echo Установка «Сумерки ТВ» на Xiaomi MiTV-MSSP1. Адрес телевизора будет запрошен.
echo Приложение получит право изменять системную подсветку телевизора.
echo Сначала будет сохранено исходное значение. Автоматика до теста выключена.
node.exe "%~dp0install.cjs"
pause
