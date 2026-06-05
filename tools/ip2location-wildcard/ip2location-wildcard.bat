@echo off
setlocal EnableExtensions
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%..\.."
for %%I in ("%ROOT_DIR%") do set "ROOT_DIR=%%~fI"

set "DB_PATH=%IP2LOCATION_DB%"
if "%DB_PATH%"=="" set "DB_PATH=%ROOT_DIR%\IP2LOCATION-LITE-DB11.IPV6.BIN"

set "IP2LOCATION_JAR=%USERPROFILE%\.m2\repository\com\ip2location\ip2location-java\8.13.0\ip2location-java-8.13.0.jar"
set "SOURCE_FILE=%SCRIPT_DIR%Ip2LocationWildcardLookup.java"
set "BUILD_DIR=%TEMP%\shopping-ip2location-wildcard-build"
set "INTERACTIVE=0"

if not "%~1"=="" goto args_mode

set "INTERACTIVE=1"
echo.
echo IP2Location local wildcard lookup
echo Example IP pattern: 255.*.255.254
echo.
set /p "IP_PATTERN=Input IP pattern: "
if "%IP_PATTERN%"=="" goto usage
set /p "TARGET_CITY=Input city filter, can be empty: "
set /p "TARGET_REGION=Input region/state filter, can be empty: "
goto validate

:args_mode
set "IP_PATTERN=%~1"
set "TARGET_CITY=%~2"
set "TARGET_REGION=%~3"

:validate

where java >nul 2>nul
if errorlevel 1 (
  echo java was not found in PATH. Install a JDK or add Java to PATH.
  set "EXIT_CODE=1"
  goto finish
)

where javac >nul 2>nul
if errorlevel 1 (
  echo javac was not found in PATH. Install a JDK or add javac to PATH.
  set "EXIT_CODE=1"
  goto finish
)

if not exist "%DB_PATH%" (
  echo BIN file not found: "%DB_PATH%"
  echo You can override it with: set IP2LOCATION_DB=C:\path\IP2LOCATION-LITE-DB11.IPV6.BIN
  set "EXIT_CODE=1"
  goto finish
)

if not exist "%IP2LOCATION_JAR%" (
  echo IP2Location Java jar not found: "%IP2LOCATION_JAR%"
  echo Run Maven once for the project, or adjust IP2LOCATION_JAR inside this BAT.
  set "EXIT_CODE=1"
  goto finish
)

if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%" >nul 2>nul

javac -encoding UTF-8 -cp "%IP2LOCATION_JAR%" -d "%BUILD_DIR%" "%SOURCE_FILE%"
if errorlevel 1 (
  set "EXIT_CODE=1"
  goto finish
)

java -Dfile.encoding=UTF-8 -cp "%BUILD_DIR%;%IP2LOCATION_JAR%" Ip2LocationWildcardLookup "%DB_PATH%" "%IP_PATTERN%" "%TARGET_CITY%" "%TARGET_REGION%"
set "EXIT_CODE=%ERRORLEVEL%"
goto finish

:usage
echo Usage:
echo   %~nx0 "66.93.67.*" "San Jose" "California"
echo   %~nx0 "66.93.67.*" "San Jose,California"
echo.
echo Default BIN:
echo   %DB_PATH%
echo.
echo Optional override:
echo   set IP2LOCATION_DB=C:\path\IP2LOCATION-LITE-DB11.IPV6.BIN
set "EXIT_CODE=2"

:finish
if "%INTERACTIVE%"=="1" (
  echo.
  pause
)
exit /b %EXIT_CODE%
