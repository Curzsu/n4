@echo off
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "FRONTEND=%ROOT%frontend"

if not exist "%BACKEND%\pom.xml" (
  echo [ERROR] Backend project was not found: %BACKEND%
  pause
  exit /b 1
)

if not exist "%FRONTEND%\package.json" (
  echo [ERROR] Frontend project was not found: %FRONTEND%
  pause
  exit /b 1
)

where java.exe >nul 2>&1 || (
  echo [ERROR] Java was not found. Check JAVA_HOME and PATH.
  pause
  exit /b 1
)

where mvn.cmd >nul 2>&1 || (
  echo [ERROR] Maven was not found. Add Maven bin to PATH.
  pause
  exit /b 1
)

where npm.cmd >nul 2>&1 || (
  echo [ERROR] npm was not found. Install Node.js or fix PATH.
  pause
  exit /b 1
)

echo Checking for an existing N4 Monitor backend on port 8089...
call :stop_port 8089 "N4 Monitor backend"

echo Checking for an existing N4 Monitor frontend on port 5173...
call :stop_port 5173 "N4 Monitor frontend"

echo Starting a fresh N4 Monitor backend on port 8089...
start "N4 Monitor Backend" cmd.exe /k "cd /d ""%BACKEND%"" && mvn.cmd spring-boot:run"

echo Starting a fresh N4 Monitor frontend on port 5173...
start "N4 Monitor Frontend" cmd.exe /k "cd /d ""%FRONTEND%"" && npm.cmd run dev"

echo Waiting for frontend and backend services to start...
timeout /t 10 /nobreak >nul
start "" "http://localhost:5173/"

echo.
echo N4 Monitor startup commands have been launched.
echo FUXA is not restarted by this script. Keep the configured FUXA service running.
exit /b 0

:stop_port
set "PORT=%~1"
set "SERVICE_NAME=%~2"
set "FOUND_PROCESS="

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:":%PORT% .*LISTENING"') do (
  if not "%%P"=="0" (
    set "FOUND_PROCESS=1"
    echo Stopping %SERVICE_NAME% process %%P on port %PORT%...
    taskkill /pid %%P /f >nul 2>&1
  )
)

if defined FOUND_PROCESS (
  timeout /t 2 /nobreak >nul
)

exit /b 0
