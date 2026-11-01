import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FileUploadComponent } from './components/file-upload/file-upload.component';
import { FileDownloadComponent } from './components/file-download/file-download.component';
import { InviteCodeComponent } from './components/invite-code/invite-code.component';
import { ModalComponent } from './components/modal/modal.component';
import { IconComponent } from './components/icon/icon.component';
import { FileTransferService } from './services/file-transfer.service';
import { ModalService } from './services/modal.service';
import { ThemeService } from './services/theme.service';

export interface SharingSession {
  id: string;
  fileName: string;
  fileSize: number;
  token: string;
  port: number;
  createdAt: Date;
  expiresInSeconds: number;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    FileUploadComponent,
    FileDownloadComponent,
    InviteCodeComponent,
    ModalComponent,
    IconComponent,
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  private fileTransfer = inject(FileTransferService);
  private modalService = inject(ModalService);
  readonly themeService = inject(ThemeService);

  activeTab = signal<'upload' | 'download'>('upload');
  isUploading = signal<boolean>(false);
  uploadProgress = signal<number>(0);
  isDownloading = signal<boolean>(false);
  sessions = signal<SharingSession[]>([]);

  currentYear = new Date().getFullYear();

  // Computed: active sessions (newest first - index 0 = newest)
  readonly activeSessions = computed(() => {
    return this.sessions()
      .slice()
      .reverse(); // Newest first
  });

  // Session counter for display
  readonly sessionCount = computed(() => this.sessions().length);

  // Color schemes for sessions (cycles through)
  private colorSchemes: ('emerald' | 'blue' | 'violet')[] = ['emerald', 'blue', 'violet'];
  private colorSchemeIndex = 0;

  getSessionColorScheme(index: number): 'emerald' | 'blue' | 'violet' {
    return this.colorSchemes[index % this.colorSchemes.length];
  }

  setActiveTab(tab: 'upload' | 'download'): void {
    this.activeTab.set(tab);
  }

  handleFileUpload(file: File): void {
    this.isUploading.set(true);
    this.uploadProgress.set(0);

    this.fileTransfer.uploadFileWithProgress(file).subscribe({
      next: (update) => {
        this.uploadProgress.set(update.progress);
        if (update.result) {
          // Create new session
          const newSession: SharingSession = {
            id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            fileName: file.name,
            fileSize: file.size,
            token: update.result.token,
            port: update.result.port,
            createdAt: new Date(),
            expiresInSeconds: 90, // 1.5 minutes
          };

          // Add to sessions array
          this.sessions.update(sessions => [...sessions, newSession]);

          // Show confirmation modal with PIN in the message so copy button works
          this.modalService.success(
            'File Ready to Share!',
            `"${file.name}" (${this.formatFileSize(file.size)}) is staged. Share PIN ${update.result.token} with your recipient.`,
            undefined
          );
        }
      },
      error: (error: any) => {
        this.isUploading.set(false);
        console.error('File upload failed:', error);

        // Try extracting specific backend message - show full message without truncation
        let errorMsg = 'Failed to upload file. Please check that the server is running.';
        if (error.error) {
          if (typeof error.error === 'string') {
            errorMsg = error.error;
          } else if (error.error.message) {
            errorMsg = error.error.message;
          }
        } else if (error.message) {
          errorMsg = error.message;
        }

        // Clean up common truncation patterns in error messages
        errorMsg = errorMsg.replace(/\.\.\./g, '…');

        this.modalService.error(
          'Upload Failed',
          errorMsg,
          error.status ? `HTTP ${error.status}: ${error.statusText || 'Error'}` : 'Unable to connect to server'
        );
      },
      complete: () => {
        this.isUploading.set(false);
      }
    });
  }

  async handleDownload(pin: string): Promise<void> {
    this.isDownloading.set(true);

    try {
      const result = await this.fileTransfer.downloadFile(pin);

      // Find the session and mark as completed (it will expire naturally)
      const sessions = this.sessions();
      const sessionIndex = sessions.findIndex(s => s.token === pin);

      this.modalService.success(
        'Download Finished',
        `Downloaded "${result.filename}" (${this.formatFileSize(result.size)}) directly from your peer!`
      );
    } catch (error: any) {
      console.error('File download failed:', error);

      let msg = 'Failed to download file. Please verify the access PIN and ensure the sender is still sharing.';
      if (error?.status === 403) {
        msg = 'Access denied: The access PIN entered is either invalid or this file transfer session has already finished.';
      } else if (error?.message) {
        msg = error.message;
      }

      this.modalService.error(
        'Download Failed',
        msg,
        error?.statusText || 'Connection error'
      );
    } finally {
      this.isDownloading.set(false);
    }
  }

  removeSession(sessionId: string): void {
    this.sessions.update(sessions => sessions.filter(s => s.id !== sessionId));
  }

  formatFileSize(bytes: number): string {
    return this.fileTransfer.formatBytes(bytes);
  }
}
