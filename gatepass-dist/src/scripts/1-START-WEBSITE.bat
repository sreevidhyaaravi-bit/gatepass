@echo off
title Digital Gate Pass - Website (Tomcat 10.1)
rem Starts Apache Tomcat 10.1 with the student + warden website at http://localhost:8080
rem Keep this window open while you use the website. Close it (or press Ctrl+C) to stop.
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
    echo Java was not found. Install Java 17 or newer, then run this file again.
    pause
    exit /b 1
)
java --enable-native-access=ALL-UNNAMED -jar server\gatepass-launcher.jar website
pause
