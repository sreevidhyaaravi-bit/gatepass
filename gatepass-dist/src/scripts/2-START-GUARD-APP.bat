@echo off
title Digital Gate Pass - Guard App
rem Starts the security guard desktop application (Java Swing).
rem It uses the same database as the website, so you can run both at the same time.
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
    echo Java was not found. Install Java 17 or newer, then run this file again.
    pause
    exit /b 1
)
java --enable-native-access=ALL-UNNAMED -jar guard\gatepass-guard.jar
if errorlevel 1 pause
