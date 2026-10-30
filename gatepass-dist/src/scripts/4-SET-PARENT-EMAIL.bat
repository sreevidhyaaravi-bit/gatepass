@echo off
title Digital Gate Pass - Set a demo parent email
rem The demo parents have fictional addresses (parent.cs24001@example.test ...).
rem To SHOW a real email arriving, point one parent at an inbox your team can open.
cd /d "%~dp0"
set /p STUDENT=College ID of the student (for example CS24002): 
set /p PARENTMAIL=Email address to use for that student's parent: 
java --enable-native-access=ALL-UNNAMED -cp "guard\gatepass-guard.jar;guard\lib\*" com.gatepass.db.DatabaseInitializer parent-email "%STUDENT%" "%PARENTMAIL%"
pause
