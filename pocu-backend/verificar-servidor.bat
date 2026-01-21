@echo off
echo Esperando 5 segundos para que el servidor inicie...
timeout /t 5 /nobreak

echo Verificando si el servidor está corriendo en puerto 3000...
netstat -ano | findstr :3000

echo.
echo Si ves una línea con "LISTENING" arriba, el servidor está activo.
echo Intenta acceder a: http://localhost:3000/api/health
pause
