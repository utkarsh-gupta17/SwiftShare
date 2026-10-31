# SwiftShare UI (Angular Edition)

Modern, responsive, and secure frontend for the SwiftShare P2P file sharing application, migrated to **Angular 19** with **Tailwind CSS**, **Signals**, and **Standalone Components**.

## ✨ Enhancements & Features

- **Dark & Light Mode Support**: Instant theme toggle button in the header, with local storage persistence and system preference detection. All cards, dropzones, inputs, and modals adjust seamlessly.
- **Modern Glassmorphic UI**: Beautiful gradients, responsive layout across mobile, tablet, and desktop devices.
- **Enhanced Dropzone**: Animated drag & drop area with live hover states, file type detection, and size formatting (Bytes, KB, MB, GB).
- **Modal Dialog System**: Browser `alert()` replaced with modern, accessible modal dialogs for:
  - 🚨 **Errors**: Server offline, network disconnect, failed upload/download with technical detail toggles.
  - ⚠️ **Warnings**: Invalid port number (outside 1–65535), empty file selection, missing invite code.
  - ✅ **Success**: File ready to share with 1-click clipboard copy, and file download completion.
- **Identical Core Functionality**:
  - `POST /api/upload` (multipart/form-data) returning `{ port: number }`.
  - `GET /api/download/:port` receiving `blob` and extracting original filename from `Content-Disposition`.
  - Seamless dev-server proxy (`proxy.conf.json`) forwarding to `http://localhost:8080`.
- **Production & Deployment Ready**:
  - Environment switching via `.env` and `src/environments/environment.ts` / `environment.prod.ts`.
  - Static build output generated with `npm run build` ready for Docker, Nginx, Vercel, Netlify, or any static CDN.

## 🚀 Getting Started

### 1. Install Dependencies

```bash
cd ui
npm install
```

### 2. Run Development Server

```bash
npm run dev
# or: npm start
```

This starts the Angular dev server at [http://localhost:4200](http://localhost:4200). All `/api/*` calls are automatically proxied to the backend at `http://localhost:8080`.

### 3. Build for Production

```bash
npm run build
```

Production assets will be compiled to `dist/ui/browser/`.

## ⚙️ Environment Configuration

- `.env`: Defines `API_BASE_URL` (defaults to `/api` for dev proxy or production reverse-proxy).
- `proxy.conf.json`: Configures local dev proxy to `http://localhost:8080`.

## 📁 Directory Structure

```
ui/
├── src/
│   ├── app/
│   │   ├── components/
│   │   │   ├── file-upload/      # Drag & drop upload zone & file preview
│   │   │   ├── file-download/    # Port input form & validation
│   │   │   ├── invite-code/      # Port display card & clipboard copy
│   │   │   ├── modal/            # Error, warning, and success dialogs
│   │   │   └── icon/             # Clean Lucide SVG icons
│   │   ├── models/               # Modal and transfer TypeScript interfaces
│   │   ├── services/
│   │   │   ├── file-transfer.service.ts # Upload and download HTTP logic
│   │   │   └── modal.service.ts         # Reactive modal controller
│   │   ├── app.component.ts      # Main container & tab manager
│   │   └── app.component.html    # Layout & responsive grid
│   ├── environments/             # Environment configs (dev & prod)
│   ├── index.html                # Plus Jakarta Sans & JetBrains Mono fonts
│   └── styles.css                # Tailwind directives & design system
├── proxy.conf.json               # Backend proxy configuration
├── tailwind.config.js            # Tailwind theme configuration
└── angular.json                  # Angular CLI build settings
```
