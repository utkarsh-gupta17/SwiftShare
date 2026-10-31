#!/bin/bash

# SwiftShare Start Script
# Builds and starts both the Java backend and Angular frontend

# Build the Java backend
echo "Building Java backend..."
mvn clean package -DskipTests

# Start the Java backend in the background
echo "Starting Java backend..."
java -jar target/swiftshare-1.0-SNAPSHOT.jar &
BACKEND_PID=$!

# Wait for the backend to start
echo "Waiting for backend to start..."
sleep 3

# Check if backend is still running
if ! kill -0 $BACKEND_PID 2>/dev/null; then
    echo "Backend failed to start. Check logs above."
    exit 1
fi

# Install frontend dependencies if node_modules doesn't exist
if [ ! -d "ui/node_modules" ]; then
    echo "Installing frontend dependencies..."
    cd ui && npm ci && cd ..
fi

# Build the frontend
echo "Building Angular frontend..."
cd ui && npm run build && cd ..

# Serve the frontend using npx serve (serves Angular build output from dist/ui/browser)
echo "Starting Angular frontend (static server on port 3000)..."
cd ui && npx serve -s dist/ui/browser -l 3000 &
FRONTEND_PID=$!

# Wait for the frontend to start
sleep 3

echo ""
echo "=== SwiftShare is running ==="
echo "Frontend: http://localhost:3000"
echo "Backend API: http://localhost:8080"
echo ""
echo "To stop: kill $BACKEND_PID $FRONTEND_PID"

# Wait for both processes
wait
