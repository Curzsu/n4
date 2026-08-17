@echo off
setlocal

set "ROOT=%~dp0"
set "BACKEND=%ROOT%monitor-backend"
set "FRONTEND=%ROOT%frontend"

if not exist "%ROOT%pom.xml" (
  echo [ERROR] Root aggregator pom was not found: %ROOT%pom.xml
  pause
  exit /b 1
)

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

echo Starting N4 Monitor backend on port 8089...
rem -pl monitor-backend -am: build monitor-common + monitor-flink-job in-reactor,
rem then run the Spring Boot app from the reactor root.
start "N4 Monitor Backend" cmd.exe /k "cd /d ""%ROOT%"" && mvn.cmd -pl monitor-backend -am spring-boot:run"

echo Starting N4 Monitor frontend on port 5173...
start "N4 Monitor Frontend" cmd.exe /k "cd /d ""%FRONTEND%"" && npm.cmd run dev"

echo Waiting for frontend and backend services to start...
timeout /t 10 /nobreak >nul
start "" "http://localhost:5173/"

echo.
echo N4 Monitor startup commands have been launched.
echo FUXA must also be running at http://127.0.0.1:1881/ for graphical monitoring.
exit /b 0
