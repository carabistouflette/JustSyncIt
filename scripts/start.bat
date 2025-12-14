@echo off
REM JustSyncIt Startup Script

REM Set default JVM options
set JVM_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC

REM Override with environment variable if set
if defined JUSTSYNCIT_JVM_OPTS (
    set JVM_OPTS=%JUSTSYNCIT_JVM_OPTS%
)

REM Get the directory where this script is located
set SCRIPT_DIR=%~dp0
set LIB_DIR=%SCRIPT_DIR%..\lib

REM Find the JAR file
for %%f in ("%LIB_DIR%\justsyncit-*-all.jar") do (
    set JAR_FILE=%%f
    goto :found
)

echo Error: JustSyncIt JAR file not found in %LIB_DIR%
exit /b 1

:found
echo Starting JustSyncIt...
echo JVM Options: %JVM_OPTS%
echo JAR File: %JAR_FILE%

REM Run the application
java %JVM_OPTS% -jar "%JAR_FILE%" %*
