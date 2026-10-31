#!/bin/bash

# SwiftShare VPS Setup Script
# This script helps set up SwiftShare on a fresh Ubuntu/Debian VPS

# Exit on error
set -e

echo "=== SwiftShare VPS Setup Script ==="
echo "This script will install Java, Node.js, Nginx, and set up SwiftShare."

# Update system
echo "Updating system packages..."
sudo apt update
sudo apt upgrade -y

# Install Java
echo "Installing Java..."
sudo apt install -y openjdk-17-jdk

# Install Node.js
echo "Installing Node.js..."
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs

# Install Nginx
echo "Installing Nginx..."
sudo apt install -y nginx

# Install PM2
echo "Installing PM2..."
sudo npm install -g pm2

# Install Maven
echo "Installing Maven..."
sudo apt install -y maven

# Build backend
echo "Building Java backend..."
mvn clean package -DskipTests

# Build frontend
echo "Building frontend..."
if [ -d "ui" ]; then
    cd ui
    if [ -f "package-lock.json" ]; then
        npm ci
    else
        echo "package-lock.json not found in ui/, running npm install instead"
        npm install
    fi
    npm run build
    cd ..
else
    echo "Error: ui directory not found!"
    exit 1
fi

# Set up Nginx
echo "Setting up Nginx..."

# Ensure the default site is removed to avoid conflicts
if [ -e /etc/nginx/sites-enabled/default ]; then
    sudo rm /etc/nginx/sites-enabled/default
    echo "Removed default Nginx site configuration."
fi

# Create the swiftshare configuration file
echo "Creating /etc/nginx/sites-available/swiftshare..."
sudo tee /etc/nginx/sites-available/swiftshare > /dev/null <<'EOF'
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # Serve Angular static files
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Backend API proxy
    location /api/ {
        proxy_pass http://localhost:8080/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
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

# Create the symbolic link to enable the swiftshare site
sudo ln -sf /etc/nginx/sites-available/swiftshare /etc/nginx/sites-enabled/swiftshare

sudo nginx -t
if [ $? -eq 0 ]; then
    sudo systemctl restart nginx
    echo "Nginx configured and restarted successfully."
else
    echo "Nginx configuration test failed. Please check /etc/nginx/nginx.conf and /etc/nginx/sites-available/swiftshare."
    exit 1
fi

# Set up SSL with Let's Encrypt (uncomment if needed)
# echo "Setting up SSL with Let's Encrypt..."
# sudo apt install -y certbot python3-certbot-nginx
# sudo certbot --nginx -d your-actual-domain.com

# Ensure any existing process is gone before starting
pm2 delete swiftshare-backend 2>/dev/null || true
pm2 delete swiftshare-frontend 2>/dev/null || true

# Start backend with PM2 (using explicit classpath — ensures main class is found without a manifest entry)
echo "Starting backend with PM2..."
CLASSPATH="target/swiftshare-1.0-SNAPSHOT.jar:$(mvn dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=/dev/stdout -q)"
echo "DEBUG: CLASSPATH is '$CLASSPATH'"
pm2 delete swiftshare-backend 2>/dev/null || true
pm2 start java --name "swiftshare-backend" -- -cp "$CLASSPATH" p2p.App daemon

# Serve Angular frontend with a static file server
echo "Starting frontend with PM2 (static server)..."
if [ -d "ui" ]; then
    cd ui
    pm2 start npx --name swiftshare-frontend -- serve -s dist/ui/browser -l 3000
    cd ..
else
    echo "Error: ui directory not found!"
    exit 1
fi

# Save PM2 configuration
pm2 save

# Set up PM2 to start on boot
echo "Setting up PM2 to start on boot..."
pm2 startup
# Follow the instructions printed by the above command

echo ""
echo "=== Setup Complete ==="
echo "SwiftShare is now running on your VPS!"
echo "Frontend: http://<your-vps-ip> (Nginx on port 80 serves Angular static files)"
echo "Backend API: http://<your-vps-ip>/api/ (proxied to port 8080)"
echo ""
echo "To serve Angular build output directly, copy dist/ui/browser to nginx root:"
echo "  sudo cp -r ui/dist/ui/browser /usr/share/nginx/html"
echo ""
echo "Then restart nginx: sudo systemctl restart nginx"
