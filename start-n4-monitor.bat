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

set "BACKEND_PORT=8088"
if exist "%ROOT%.env" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%ROOT%.env") do (
    if "%%A"=="SERVER_PORT" set "BACKEND_PORT=%%B"
  )
)

rem Process variables override the shared remote-source .env.
if exist "%ROOT%.env.local" (
  echo Loading isolated local lab settings...
  for /f "usebackq tokens=1,* delims==" %%A in ("%ROOT%.env.local") do (
    if not "%%A"=="" (
      echo %%A| findstr /b "#" >nul
      if errorlevel 1 set "%%A=%%B"
    )
  )
  call :load_local_port
)

echo Checking for an existing N4 Monitor backend on port %BACKEND_PORT%...
call :stop_port %BACKEND_PORT% "N4 Monitor backend"

echo Checking for an existing N4 Monitor frontend on port 5173...
call :stop_port 5173 "N4 Monitor frontend"

echo Starting a fresh N4 Monitor backend on port %BACKEND_PORT%...
rem Build the reactor first so monitor-common and monitor-flink-job are available
rem to monitor-backend after a fresh pull or a clean local Maven repository.
start "N4 Monitor Backend" cmd.exe /k "cd /d ""%ROOT%"" && mvn.cmd -pl monitor-backend -am install -DskipTests && cd /d ""%BACKEND%"" && mvn.cmd spring-boot:run"

echo Starting a fresh N4 Monitor frontend on port 5173...
start "N4 Monitor Frontend" cmd.exe /k "cd /d ""%FRONTEND%"" && npm.cmd run dev"

echo Waiting for frontend and backend services to start...
timeout /t 10 /nobreak >nul
start "" "http://localhost:5173/"

echo.
echo N4 Monitor startup commands have been launched.
echo FUXA is not restarted by this script. Keep the configured FUXA service running.
exit /b 0

:load_local_port
set "BACKEND_PORT=%SERVER_PORT%"
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
