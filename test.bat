@echo off
REM EH3D-ANDROID-CONTROL test launcher: only the tests. GPL-3.0-or-later.
cd /d "%~dp0"
call gradlew.bat test
exit /b %ERRORLEVEL%
