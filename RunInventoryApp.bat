@echo off
REM ===================================================================
REM  Double-click this to launch the Smart Inventory Management System.
REM  Keep this .bat file in the SAME folder as:
REM    - SmartInventoryManagementSystem.java
REM    - mysql-connector-j-8.0.31.jar
REM ===================================================================

cd /d "%~dp0"

if not exist SmartInventoryManagementSystem.class (
    echo Compiling for the first time...
    javac -cp mysql-connector-j-8.0.31.jar SmartInventoryManagementSystem.java
    if errorlevel 1 (
        echo.
        echo Compile failed - see the error above.
        pause
        exit /b
    )
)

java -cp ".;mysql-connector-j-8.0.31.jar" SmartInventoryManagementSystem

pause
