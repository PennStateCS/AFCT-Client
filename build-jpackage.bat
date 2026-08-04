@echo off
echo Building AFCT Client installer (jpackage)...
set PROJECT_DIR=%~dp0
set MVN_CMD=

REM Check PATH first
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 ( set MVN_CMD=mvn & goto :build )

REM IntelliJ IDEA install path used by recent versions
if exist "C:\Users\%USERNAME%\AppData\Local\Programs\IntelliJ IDEA\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" (
    set MVN_CMD="C:\Users\%USERNAME%\AppData\Local\Programs\IntelliJ IDEA\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
    goto :build
)

REM Older IntelliJ packaging layout
if exist "C:\Users\%USERNAME%\AppData\Local\Programs\IntelliJ IDEA\plugins\maven\lib\maven3\bin\mvn.cmd" (
    set MVN_CMD="C:\Users\%USERNAME%\AppData\Local\Programs\IntelliJ IDEA\plugins\maven\lib\maven3\bin\mvn.cmd"
    goto :build
)

REM JetBrains default installs
for /d %%i in ("C:\Program Files\JetBrains\*") do (
    if exist "%%i\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" (
        set MVN_CMD="%%i\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
        goto :build
    )
    if exist "%%i\plugins\maven\lib\maven3\bin\mvn.cmd" (
        set MVN_CMD="%%i\plugins\maven\lib\maven3\bin\mvn.cmd"
        goto :build
    )
)

REM Toolbox installs
for /d %%a in ("%LOCALAPPDATA%\JetBrains\Toolbox\apps\IDEA-U\ch-0\*") do (
    if exist "%%a\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" (
        set MVN_CMD="%%a\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
        goto :build
    )
    if exist "%%a\plugins\maven\lib\maven3\bin\mvn.cmd" (
        set MVN_CMD="%%a\plugins\maven\lib\maven3\bin\mvn.cmd"
        goto :build
    )
)
for /d %%a in ("%LOCALAPPDATA%\JetBrains\Toolbox\apps\IDEA-C\ch-0\*") do (
    if exist "%%a\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" (
        set MVN_CMD="%%a\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
        goto :build
    )
    if exist "%%a\plugins\maven\lib\maven3\bin\mvn.cmd" (
        set MVN_CMD="%%a\plugins\maven\lib\maven3\bin\mvn.cmd"
        goto :build
    )
)

echo.
echo ERROR: Could not find Maven.
echo.
echo Searched:
echo   - PATH (mvn command)
echo   - %%LOCALAPPDATA%%\Programs\IntelliJ IDEA\plugins\maven-plugin\...
echo   - C:\Program Files\JetBrains\*\plugins\maven*\...
echo   - %%LOCALAPPDATA%%\JetBrains\Toolbox\...
echo.
echo Please open this project in IntelliJ IDEA and run:
echo   Maven panel (right side) -^> package -^> jpackage
pause
exit /b 1

:build
echo Using: %MVN_CMD%
echo.
cd /d "%PROJECT_DIR%"
%MVN_CMD% -P jpackage package
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==========================================
    echo JPACKAGE BUILD SUCCESSFUL
    echo Installer output: target\jpackage\^<version-timestamp^>\
    echo ==========================================
) else (
    echo.
    echo JPACKAGE BUILD FAILED - see errors above.
)
pause
