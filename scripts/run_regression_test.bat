@echo off
REM =======================================================
REM N4-Monitor - Run Automated Regression Test Suite
REM =======================================================

echo Running N4-Monitor Regression Test Suite...
python "%~dp0regression_test.py" %*

if %ERRORLEVEL% EQU 0 (
    echo [SUCCESS] All regression tests passed!
) else (
    echo [ERROR] Regression tests failed with code %ERRORLEVEL%.
)
exit /b %ERRORLEVEL%
