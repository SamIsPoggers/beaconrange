@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0playtest-all.ps1" %*
if errorlevel 1 (
    echo.
    echo Playtest script exited with error code %errorlevel%.
    pause
)
endlocal
