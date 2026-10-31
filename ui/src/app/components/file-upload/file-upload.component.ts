import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../icon/icon.component';
import { FileTransferService } from '../../services/file-transfer.service';
import { ModalService } from '../../services/modal.service';

@Component({
  selector: 'app-file-upload',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    <div class="space-y-4">
      <!-- Hidden Native File Input -->
      <input
        #fileInput
        type="file"
        class="hidden"
        (change)="onFileInputChange($event)"
        [disabled]="isUploading"
      />

      <!-- Dropzone Area -->
      <div
        class="relative group rounded-2xl border-2 border-dashed p-8 sm:p-10 text-center cursor-pointer transition-all duration-300"
        [ngClass]="{
          'border-blue-500 bg-blue-50/70 dark:bg-blue-950/40 shadow-lg shadow-blue-500/10 scale-[1.01]': isDragActive,
          'border-slate-300/80 dark:border-slate-700/80 bg-white/60 dark:bg-slate-900/40 hover:border-blue-400 dark:hover:border-blue-500/70 hover:bg-blue-50/20 dark:hover:bg-blue-950/20 hover:shadow-md': !isDragActive && !isUploading,
          'opacity-60 cursor-not-allowed pointer-events-none': isUploading
        }"
        (dragover)="onDragOver($event)"
        (dragleave)="onDragLeave($event)"
        (drop)="onDrop($event)"
        (click)="fileInput.click()"
        role="button"
        tabindex="0"
        (keydown.enter)="fileInput.click()"
        (keydown.space)="fileInput.click()"
      >
        <!-- Decorative Glow -->
        <div class="absolute -top-10 left-1/2 -translate-x-1/2 w-48 h-48 bg-blue-400/10 dark:bg-blue-500/5 rounded-full blur-2xl pointer-events-none"></div>

        <div class="flex flex-col items-center justify-center space-y-4 relative z-10">
          <!-- Animated Icon Circle -->
          <div 
            class="w-16 h-16 rounded-2xl flex items-center justify-center transition-all duration-300"
            [ngClass]="isDragActive 
              ? 'bg-blue-600 text-white scale-110 shadow-lg shadow-blue-500/30' 
              : 'bg-blue-50 text-blue-600 dark:bg-slate-800 dark:text-blue-400 group-hover:bg-blue-100 dark:group-hover:bg-slate-700 group-hover:scale-105'"
          >
            <app-icon name="upload" [size]="28" [strokeWidth]="2.2" />
          </div>

          <!-- Headline and Instructions -->
          <div>
            <p class="text-base sm:text-lg font-semibold text-slate-800 dark:text-slate-200">
              <span class="text-blue-600 dark:text-blue-400 hover:underline">Choose a file</span> or drag & drop here
            </p>
            <p class="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1">
              Any file type • Direct peer-to-peer transmission • End-to-end local streaming
            </p>
          </div>

          <!-- Pill Feature Tags -->
          <div class="flex flex-wrap items-center justify-center gap-2 pt-2 text-xs font-medium text-slate-500 dark:text-slate-400">
            <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-100/90 dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700">
              <app-icon name="shield" [size]="13" className="text-emerald-500 dark:text-emerald-400" />
              Private & Direct
            </span>
            <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-slate-100/90 dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700">
              <app-icon name="server" [size]="13" className="text-blue-500 dark:text-blue-400" />
              No Cloud Storage Limits
            </span>
          </div>
        </div>
      </div>
    </div>
  `
})
export class FileUploadComponent {
  @Input() isUploading: boolean = false;
  @Output() fileSelected = new EventEmitter<File>();

  private modalService = inject(ModalService);
  isDragActive: boolean = false;

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (!this.isUploading) {
      this.isDragActive = true;
    }
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragActive = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragActive = false;

    if (this.isUploading) return;

    const files = event.dataTransfer?.files;
    if (files && files.length > 0) {
      this.handleFile(files[0]);
    }
  }

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.handleFile(input.files[0]);
      input.value = ''; // Reset input to allow selecting same file again if needed
    }
  }

  private handleFile(file: File): void {
    if (!file) return;

    if (file.size === 0) {
      this.modalService.warning(
        'Empty File',
        `The selected file "${file.name}" is 0 bytes. Please select a valid file to transfer.`
      );
      return;
    }

    this.fileSelected.emit(file);
  }
}
