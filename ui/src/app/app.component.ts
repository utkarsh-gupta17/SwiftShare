import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FileUploadComponent } from './components/file-upload/file-upload.component';
import { FileDownloadComponent } from './components/file-download/file-download.component';
import { InviteCodeComponent } from './components/invite-code/invite-code.component';
import { ModalComponent } from './components/modal/modal.component';
import { IconComponent } from './components/icon/icon.component';
import { FileTransferService } from './services/file-transfer.service';
import { ModalService } from './services/modal.service';
import { ThemeService } from './services/theme.service';

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
  uploadedFile = signal<File | null>(null);
  isUploading = signal<boolean>(false);
  uploadProgress = signal<number>(0);
  isDownloading = signal<boolean>(false);
  port = signal<number | null>(null);
  token = signal<string | null>(null);

  currentYear = new Date().getFullYear();

  setActiveTab(tab: 'upload' | 'download'): void {
    this.activeTab.set(tab);
  }

  handleFileUpload(file: File): void {
    this.uploadedFile.set(file);
    this.isUploading.set(true);
    this.uploadProgress.set(0);
    this.port.set(null);
    this.token.set(null);

    this.fileTransfer.uploadFileWithProgress(file).subscribe({
      next: (update) => {
        this.uploadProgress.set(update.progress);
        if (update.result) {
          this.port.set(update.result.port);
          this.token.set(update.result.token);

          this.modalService.success(
            'Ready to Share!',
            `"${file.name}" (${this.formatFileSize(file.size)}) is staged. Share your 6-digit PIN (${update.result.token}) with your recipient.`
          );
        }
      },
      error: (error: any) => {
        this.isUploading.set(false);
        console.error('File upload failed:', error);

        // Try extracting specific backend message
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

        this.modalService.error(
          'Upload Failed',
          errorMsg,
          `HTTP ${error.status || 'Network error'}: ${error.statusText || 'Unable to connect'}`
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

  resetUpload(): void {
    this.uploadedFile.set(null);
    this.port.set(null);
    this.token.set(null);
    this.uploadProgress.set(0);
  }

  formatFileSize(bytes: number): string {
    return this.fileTransfer.formatBytes(bytes);
  }
}
