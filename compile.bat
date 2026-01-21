@echo off
REM Script para compilar Pocu sin problemas de terminal
cd /d "C:\Users\herma\AndroidStudioProjects\Pocu2"

REM Limpiar
call gradlew.bat clean

REM Compilar
call gradlew.bat assembleDebug

REM Mostrar resultado
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===============================================
    echo BUILD SUCCESSFUL!
    echo ===============================================
    echo APK generado en: app\build\outputs\apk\debug\app-debug.apk
    pause
) else (
    echo.
    echo ===============================================
    echo BUILD FAILED!
    echo ===============================================
    pause
)
