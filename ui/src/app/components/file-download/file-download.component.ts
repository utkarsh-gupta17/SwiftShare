import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { IconComponent } from '../icon/icon.component';
import { ModalService } from '../../services/modal.service';

@Component({
  selector: 'app-file-download',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  template: `
    <div class="space-y-6">
      <!-- Instruction Banner -->
      <div class="p-5 rounded-2xl bg-gradient-to-r from-blue-50/80 to-indigo-50/80 dark:from-blue-950/40 dark:to-indigo-950/40 border border-blue-100 dark:border-blue-900/50 flex items-start gap-3.5">
        <div class="p-2.5 rounded-xl bg-blue-100/80 dark:bg-blue-900/50 text-blue-700 dark:text-blue-300 flex-shrink-0 mt-0.5">
          <app-icon name="download" [size]="20" />
        </div>
        <div>
          <h3 class="text-sm sm:text-base font-bold text-blue-950 dark:text-blue-100">Receive a Shared File</h3>
          <p class="text-xs sm:text-sm text-blue-700/90 dark:text-blue-300/90 mt-0.5 leading-relaxed">
            Enter the 6-digit access PIN provided by the sender. The file will stream directly from their peer session.
          </p>
        </div>
      </div>

      <!-- Form -->
      <form (ngSubmit)="handleSubmit()" class="space-y-4">
        <div>
          <div class="flex items-center justify-between mb-1.5">
            <label for="accessToken" class="block text-xs font-semibold uppercase tracking-wider text-slate-600 dark:text-slate-300">
              Access PIN
            </label>
            <span class="text-xs text-slate-400 dark:text-slate-500">6 digits (e.g. 123456)</span>
          </div>

          <div class="relative flex items-center">
            <div class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-slate-400 dark:text-slate-500">
              <span class="text-sm font-mono font-semibold">#</span>
            </div>

            <input
              type="text"
              id="accessToken"
              name="accessToken"
              [(ngModel)]="accessToken"
              (input)="onInputChange()"
              placeholder="Enter 6-digit PIN"
              maxlength="6"
              class="input-field pl-9 pr-24 font-mono text-xl font-bold tracking-widest text-slate-900 dark:text-slate-100"
              [disabled]="isDownloading"
              autocomplete="off"
              required
            />
          </div>

          @if (errorMessage) {
            <p class="mt-1.5 text-xs font-medium text-rose-600 dark:text-rose-400 flex items-center gap-1.5 animate-in fade-in">
              <app-icon name="alert-circle" [size]="14" />
              {{ errorMessage }}
            </p>
          }
        </div>

        <!-- Submit Button -->
        <button
          type="submit"
          class="btn-primary w-full py-3.5 text-base shadow-lg shadow-blue-500/25 flex items-center justify-center gap-2"
          [disabled]="isDownloading || !accessToken.trim()"
        >
          @if (isDownloading) {
            <app-icon name="loader-2" [size]="20" className="animate-spin text-white" />
            <span>Connecting to Peer & Downloading...</span>
          } @else {
            <app-icon name="download" [size]="20" />
            <span>Download File</span>
          }
        </button>
      </form>

      <!-- Security Info -->
      <div class="pt-2 border-t border-slate-100 dark:border-slate-800 grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs text-slate-500 dark:text-slate-400">
        <div class="flex items-center gap-2">
          <app-icon name="shield" [size]="15" className="text-emerald-500 dark:text-emerald-400 flex-shrink-0" />
          <span>Direct P2P transfer, protected by access PIN</span>
        </div>
        <div class="flex items-center gap-2">
          <app-icon name="server" [size]="15" className="text-blue-500 dark:text-blue-400 flex-shrink-0" />
          <span>Automatically deleted after download completes</span>
        </div>
      </div>
    </div>
  `
})
export class FileDownloadComponent {
  @Input() isDownloading: boolean = false;
  @Output() downloadRequested = new EventEmitter<string>();

  private modalService = inject(ModalService);

  accessToken: string = '';
  errorMessage: string = '';

  onInputChange(): void {
    if (this.errorMessage) {
      this.errorMessage = '';
    }
  }

  async pasteFromClipboard(): Promise<void> {
    try {
      const text = await navigator.clipboard.readText();
      if (text) {
        this.accessToken = text.trim();
        this.onInputChange();
      }
    } catch {
      // Gracefully ignore if browser permission was rejected
    }
  }

  handleSubmit(): void {
    this.errorMessage = '';
    const pin = this.accessToken.trim();

    if (!pin) {
      this.errorMessage = 'Please enter the 6-digit access PIN.';
      this.modalService.warning(
        'Missing PIN',
        'Enter the 6-digit access PIN given to you by the sender to begin the download.'
      );
      return;
    }

    if (pin.length !== 6 || !/^\d{6}$/.test(pin)) {
      this.errorMessage = 'Access PIN must be exactly 6 numeric digits.';
      this.modalService.warning(
        'Invalid PIN Format',
        'Please double-check the PIN. It should be a 6-digit number (e.g. 123456).'
      );
      return;
    }

    this.downloadRequested.emit(pin);
  }
}
