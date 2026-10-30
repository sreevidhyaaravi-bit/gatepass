@echo off
title Digital Gate Pass - Refresh demo passes
rem Run this on the morning of the demo: it re-creates the 7 demo passes (GP1001 to GP1007)
rem with dates relative to TODAY. Login accounts and parent email changes are kept.
cd /d "%~dp0"
echo This deletes ALL gate passes and re-creates the 7 demo passes for today's date.
echo Close the guard app first. The website can stay open (refresh the page afterwards).
pause
java --enable-native-access=ALL-UNNAMED -cp "guard\gatepass-guard.jar;guard\lib\*" com.gatepass.db.DatabaseInitializer refresh
pause
