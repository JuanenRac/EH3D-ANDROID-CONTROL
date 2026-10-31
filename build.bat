@echo off
REM EH3D-ANDROID-CONTROL build launcher: the tests and the debug APK. GPL-3.0-or-later.
cd /d "%~dp0"
call gradlew.bat test assembleDebug
exit /b %ERRORLEVEL%
