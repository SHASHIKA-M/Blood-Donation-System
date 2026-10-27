@echo off
setlocal enabledelayedexpansion
title Blood Donation Management System

echo ===================================================
echo   Starting Blood Donation Management System
echo ===================================================
echo.

cd /d "%~dp0"

:: Use the local SQL Server Windows account when no database settings were supplied.
if not defined DB_URL set "DB_URL=jdbc:sqlserver://localhost:1433;databaseName=Blood_Connect;encrypt=true;trustServerCertificate=true;integratedSecurity=true;loginTimeout=10;"
if not defined DB_INTEGRATED_SECURITY set "DB_INTEGRATED_SECURITY=true"

:: Check if mvn is available in PATH
set "MVN_CMD=mvn"
where mvn >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    set "MVN_CMD="
    for /d %%D in ("C:\Program Files\JetBrains\IntelliJ IDEA*") do (
        if exist "%%D\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" (
            set "MVN_CMD=%%D\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
        )
    )
    if not defined MVN_CMD (
        echo [ERROR] Maven not found. Install Maven or open this project in IntelliJ IDEA.
        pause
        exit /b 1
    )
)

echo Using Maven: "!MVN_CMD!"
echo Application URL: http://localhost:8088
echo.
echo Starting Spring Boot server...
echo Press Ctrl+C to stop the server.
echo ===================================================
echo.

call "!MVN_CMD!" spring-boot:run

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Application failed to start or exited with an error.
    pause
)
