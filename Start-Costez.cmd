@echo off
cd /d "%~dp0"
set DEBUG=false
echo Starting Costez. Open http://localhost:8080 after the server starts.
echo Demo login: demo@costez.local / CostezDemo2026!
java -jar costez.jar
pause
