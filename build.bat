@echo off
echo Building AFCT Client...
set PROJECT_DIR=%~dp0
set MVN_CMD=

REM Check PATH first
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 ( set MVN_CMD=mvn & goto :build )

REM IntelliJ IDEA 2025.2 Ultimate
if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2025.2\plugins\maven\lib\maven3\bin\mvn.cmd" (
    set MVN_CMD="C:\Program Files\JetBrains\IntelliJ IDEA 2025.2\plugins\maven\lib\maven3\bin\mvn.cmd"
    goto :build
)

REM IntelliJ IDEA 2025.2 Community
if exist "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2\plugins\maven\lib\maven3\bin\mvn.cmd" (
    set MVN_CMD="C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2\plugins\maven\lib\maven3\bin\mvn.cmd"
    goto :build
)

REM Wider search - any JetBrains version
for /d %%i in ("C:\Program Files\JetBrains\*") do (
    if exist "%%i\plugins\maven\lib\maven3\bin\mvn.cmd" (
        set MVN_CMD="%%i\plugins\maven\lib\maven3\bin\mvn.cmd"
        goto :build
    )
)

REM Toolbox installs
for /d %%a in ("%LOCALAPPDATA%\JetBrains\Toolbox\apps\IDEA-U\ch-0\*") do (
    if exist "%%a\plugins\maven\lib\maven3\bin\mvn.cmd" (
        set MVN_CMD="%%a\plugins\maven\lib\maven3\bin\mvn.cmd"
        goto :build
    )
)
for /d %%a in ("%LOCALAPPDATA%\JetBrains\Toolbox\apps\IDEA-C\ch-0\*") do (
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
echo   - C:\Program Files\JetBrains\*\plugins\maven\...
echo   - %%LOCALAPPDATA%%\JetBrains\Toolbox\...
echo.
echo Please open this project in IntelliJ IDEA and run:
echo   Maven panel (right side) -^> package
pause
exit /b 1

:build
echo Using: %MVN_CMD%
echo.
cd /d "%PROJECT_DIR%"
%MVN_CMD% package -DskipTests
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==========================================
    echo BUILD SUCCESSFUL
    echo JAR: target\afct-client-v1.6.7.jar
    echo ==========================================
) else (
    echo.
    echo BUILD FAILED - see errors above.
)
pause
