@echo off
REM Compile the project without packaging
echo Compiling AFCT Client...

REM Try to find Maven in common locations
set MAVEN_CMD=

REM Check if mvn is in PATH
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    set MAVEN_CMD=mvn
    goto :compile
)

REM Check common Maven installation paths
if exist "C:\Program Files\Apache\Maven\bin\mvn.cmd" (
    set MAVEN_CMD="C:\Program Files\Apache\Maven\bin\mvn.cmd"
    goto :compile
)

if exist "C:\Program Files\Maven\bin\mvn.cmd" (
    set MAVEN_CMD="C:\Program Files\Maven\bin\mvn.cmd"
    goto :compile
)

if exist "%USERPROFILE%\apache-maven\bin\mvn.cmd" (
    set MAVEN_CMD="%USERPROFILE%\apache-maven\bin\mvn.cmd"
    goto :compile
)

REM Maven not found
echo ERROR: Maven not found!
echo.
echo Please install Maven or use IntelliJ IDEA's built-in Maven:
echo   In IntelliJ: Right-click on pom.xml ^> Maven ^> Reload Project
echo   Then: Right-click on pom.xml ^> Maven ^> compile
echo.
pause
exit /b 1

:compile
echo Using Maven: %MAVEN_CMD%
%MAVEN_CMD% clean compile
echo.
echo Compilation complete! Classes are in target/classes
pause

