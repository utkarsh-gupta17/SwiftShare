@echo off
echo Building Java backend...
call mvn clean package -DskipTests

echo Starting Java backend...
start /b java -jar target\swiftshare-1.0-SNAPSHOT.jar

echo Waiting for backend to start...
timeout /t 3

echo Checking if node_modules exists...
if not exist ui\node_modules (
    echo Installing frontend dependencies...
    cd ui && call npm ci && cd ..
)

echo Building Angular frontend...
cd ui && call npm run build && cd ..

echo Starting Angular frontend (static server on port 3000)...
cd ui && start /b npx serve -s dist/ui/browser -l 3000

echo.
echo === SwiftShare is running ===
echo Frontend: http://localhost:3000
echo Backend API: http://localhost:8080
echo.
echo To stop: taskkill /f /im java.exe
