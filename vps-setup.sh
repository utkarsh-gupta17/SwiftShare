#!/bin/bash

# SwiftShare VPS Setup & Deployment Script
# Target: Ubuntu / Oracle Cloud Infrastructure or any similar vps service
#
# Architecture:
# Angular -> Nginx :80
# Java Backend -> PM2 -> localhost:8080
# API -> Nginx reverse proxy -> Java backend

set -Eeuo pipefail

# ------------------------------------------
# Configuration
# ------------------------------------------

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BACKEND_DIR="$APP_DIR"
FRONTEND_DIR="$APP_DIR/ui"

NGINX_ROOT="/usr/share/nginx/html/ui/browser"
NGINX_CONFIG="/etc/nginx/sites-available/swiftshare"

BACKEND_NAME="swiftshare-backend"
BACKEND_MAIN_CLASS="p2p.App"

JAVA_PACKAGE="target/swiftshare-1.0-SNAPSHOT.jar"

echo "======================================"
echo "       SwiftShare Deployment"
echo "======================================"

cd "$APP_DIR"

# ------------------------------------------
# Error handling
# ------------------------------------------

trap 'echo "ERROR: Deployment failed at line $LINENO. Check the output above."' ERR

# ------------------------------------------
# 1. Update system packages
# ------------------------------------------

echo "[1/9] Updating system packages..."

sudo apt update

# ------------------------------------------
# 2. Install dependencies
# ------------------------------------------

echo "[2/9] Installing dependencies..."

sudo apt install -y \
    openjdk-17-jdk \
    curl \
    nginx \
    maven \
    rsync

# Install Node.js only if missing.
if ! command -v node >/dev/null 2>&1; then
    echo "Installing Node.js 18..."

    curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
    sudo apt install -y nodejs
fi

# Install PM2 only if missing.
if ! command -v pm2 >/dev/null 2>&1; then
    sudo npm install -g pm2
fi

echo "Java version:"
java -version

echo "Node version:"
node --version

echo "NPM version:"
npm --version

# ------------------------------------------
# 3. Build Java backend
# ------------------------------------------

echo "[3/9] Building Java backend..."

cd "$BACKEND_DIR"

mvn clean package -DskipTests

if [ ! -f "$JAVA_PACKAGE" ]; then
    echo "ERROR: Backend JAR not found: $JAVA_PACKAGE"
    exit 1
fi

# Build runtime classpath.
DEPENDENCY_CP=$(mvn dependency:build-classpath \
    -DincludeScope=runtime \
    -Dmdep.outputFile=/dev/stdout \
    -q)

CLASSPATH="$BACKEND_DIR/$JAVA_PACKAGE:$DEPENDENCY_CP"

# ------------------------------------------
# 4. Build Angular frontend
# ------------------------------------------

echo "[4/9] Building Angular frontend..."

cd "$FRONTEND_DIR"

if [ -f package-lock.json ]; then
    npm ci
else
    npm install
fi

npm run build

FRONTEND_BUILD="$FRONTEND_DIR/dist/ui/browser"

if [ ! -f "$FRONTEND_BUILD/index.html" ]; then
    echo "ERROR: Angular index.html not found!"
    echo "Expected: $FRONTEND_BUILD/index.html"
    echo "Check your Angular build output configuration."
    exit 1
fi

echo "Angular build successful."

# ------------------------------------------
# 5. Deploy frontend to Nginx
# ------------------------------------------

echo "[5/9] Deploying frontend to Nginx..."

sudo mkdir -p "$NGINX_ROOT"

# Deploy fresh build and remove obsolete assets.
sudo rsync -a --delete \
    "$FRONTEND_BUILD/" \
    "$NGINX_ROOT/"

sudo chown -R www-data:www-data "$NGINX_ROOT"

echo "Frontend deployed successfully."

# ------------------------------------------
# 6. Configure Nginx
# ------------------------------------------

echo "[6/9] Configuring Nginx..."

sudo tee "$NGINX_CONFIG" > /dev/null <<'EOF'
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html/ui/browser;
    index index.html;

    # Angular frontend
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Java backend API
    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_http_version 1.1;

        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_cache_bypass $http_upgrade;
    }

    # Security headers
    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options SAMEORIGIN;
    add_header X-XSS-Protection "1; mode=block";
}
EOF

# Enable SwiftShare site.
sudo ln -sfn "$NGINX_CONFIG" \
    /etc/nginx/sites-enabled/swiftshare

# Remove default Nginx site.
sudo rm -f /etc/nginx/sites-enabled/default

# Validate before reloading.
sudo nginx -t

sudo systemctl enable nginx
sudo systemctl reload nginx

echo "Nginx configured successfully."

# ------------------------------------------
# 7. Clear existing PM2 processes
# ------------------------------------------

echo "[7/9] Clearing existing PM2 processes..."

pm2 delete all || true

echo "Existing PM2 processes cleared."

# ------------------------------------------
# 8. Start/restart Java backend using PM2
# ------------------------------------------

echo "[7/9] Deploying Java backend..."

# Restart existing process if present.
if pm2 describe "$BACKEND_NAME" >/dev/null 2>&1; then

    pm2 delete "$BACKEND_NAME"

fi

pm2 start java \
    --name "$BACKEND_NAME" \
    --cwd "$BACKEND_DIR" \
    -- -cp "$CLASSPATH" "$BACKEND_MAIN_CLASS" daemon

echo "Backend started through PM2."

# ------------------------------------------
# 9. Save PM2 process configuration
# ------------------------------------------

echo "[8/9] Saving PM2 configuration..."

pm2 save

# Configure PM2 to start on system boot
echo "Configuring PM2 startup..."

sudo env PATH=$PATH:/usr/bin \
    "$(command -v pm2)" startup systemd \
    -u ubuntu \
    --hp /home/ubuntu

pm2 save

echo "PM2 startup configured successfully."

# ------------------------------------------
# 10. Final verification
# ------------------------------------------

echo "[9/9] Verifying deployment..."

echo ""
echo "Nginx status:"
sudo systemctl is-active nginx

echo ""
echo "PM2 status:"
pm2 status

echo ""
echo "Listening ports:"
sudo ss -lntp | grep -E ':80|:8080' || true

echo ""
echo "Frontend response:"
curl -s -o /dev/null -w "HTTP %{http_code}\n" \
    http://localhost/

echo ""
echo "======================================"
echo "       Deployment Complete!"
echo "======================================"

echo "Frontend: http://YOUR_PUBLIC_IP/"
echo "Backend API: http://YOUR_PUBLIC_IP/api/"
echo ""
echo "Frontend directory: $NGINX_ROOT"
echo "Backend process: $BACKEND_NAME"
echo ""
echo "Note: Angular is served directly by Nginx."
echo "======================================"