import { Component, HostListener, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ModalService } from '../../services/modal.service';
import { IconComponent, IconName } from '../icon/icon.component';

@Component({
  selector: 'app-modal',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    @if (modalState()) {
      <div
        class="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6 overflow-y-auto animate-in fade-in duration-200"
        role="dialog"
        aria-modal="true"
        [attr.aria-labelledby]="'modal-title'"
      >
        <!-- Backdrop -->
        <div
          class="fixed inset-0 bg-slate-950/70 backdrop-blur-sm transition-opacity"
          (click)="onBackdropClick()"
        ></div>

        <!-- Dialog Window -->
        <div
          class="relative w-full max-w-lg transform overflow-hidden rounded-2xl bg-white dark:bg-slate-900 p-6 sm:p-8 text-left shadow-2xl transition-all border border-slate-100 dark:border-slate-800 z-10"
          (click)="$event.stopPropagation()"
        >
          <!-- Close button in top-right -->
          <button
            type="button"
            (click)="close()"
            class="absolute top-5 right-5 text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 p-1.5 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
            aria-label="Close dialog"
          >
            <app-icon name="x" [size]="18" />
          </button>

          <div class="flex items-start gap-4">
            <!-- Icon Badge -->
            <div
              class="flex-shrink-0 flex items-center justify-center w-12 h-12 rounded-2xl shadow-sm"
              [ngClass]="iconBadgeClass"
            >
              <app-icon [name]="iconName" [size]="24" [className]="iconColorClass" />
            </div>

            <div class="flex-1 min-w-0 pr-6">
              <!-- Title -->
              <h3
                id="modal-title"
                class="text-lg sm:text-xl font-bold text-slate-900 dark:text-white leading-tight tracking-tight"
              >
                {{ modalState()?.title }}
              </h3>

              <!-- Message (without PIN - PIN is shown in dedicated section below) -->
              <p class="mt-2.5 text-sm sm:text-base text-slate-600 dark:text-slate-300 leading-relaxed break-words">
                {{ getMessageWithoutPin() }}
              </p>

              <!-- PIN Display (if PIN is in message) -->
              @if (getPinFromMessage()) {
                <div class="mt-4 p-3 sm:p-4 bg-gradient-to-r from-emerald-50 to-teal-50 dark:from-emerald-950/60 dark:to-teal-950/60 border border-emerald-200 dark:border-emerald-800/60 rounded-xl">
                  <div class="flex items-center justify-between mb-2">
                    <p class="text-[10px] uppercase tracking-wider text-emerald-600 dark:text-emerald-400 font-bold">Your PIN</p>
                    <button
                      type="button"
                      (click)="copyPinToClipboard()"
                      class="inline-flex items-center gap-1 px-2 py-1 rounded-md text-xs font-semibold transition-all"
                      [ngClass]="pinCopied() ? 'bg-emerald-600 text-white shadow-sm' : 'bg-white dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-700 hover:bg-emerald-100 dark:hover:bg-emerald-900/60'"
                    >
                      @if (pinCopied()) {
                        <app-icon name="check" [size]="12" />
                        Copied!
                      } @else {
                        <app-icon name="copy" [size]="12" />
                        Copy
                      }
                    </button>
                  </div>
                  <p class="font-mono text-2xl sm:text-3xl font-extrabold bg-gradient-to-r from-emerald-600 to-teal-600 dark:from-emerald-400 dark:to-teal-400 bg-clip-text text-transparent tracking-widest text-center">
                    {{ getPinFromMessage() }}
                  </p>
                </div>
              }

              <!-- Optional Technical Details -->
              @if (modalState()?.details) {
                <div class="mt-4 p-3 bg-slate-100 dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700 rounded-xl">
                  <p class="text-[10px] uppercase tracking-wider text-slate-400 dark:text-slate-500 font-bold mb-1">Details</p>
                  <p class="text-xs font-mono text-slate-600 dark:text-slate-300 break-all leading-relaxed whitespace-pre-wrap">
                    {{ modalState()?.details }}
                  </p>
                </div>
              }
            </div>
          </div>

          <!-- Actions -->
          <div class="mt-6 flex flex-col-reverse sm:flex-row sm:justify-end gap-2.5">
            @if (modalState()?.showCancel) {
              <button
                type="button"
                (click)="close()"
                class="w-full sm:w-auto px-4 py-2.5 rounded-xl font-medium text-slate-700 dark:text-slate-200 bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 active:scale-[0.98] transition-all text-sm"
              >
                {{ modalState()?.cancelText || 'Cancel' }}
              </button>
            }

            <button
              type="button"
              (click)="confirm()"
              class="w-full sm:w-auto px-5 py-2.5 rounded-xl font-semibold text-white shadow-sm transition-all active:scale-[0.98] text-sm flex items-center justify-center"
              [ngClass]="confirmButtonClass"
            >
              {{ modalState()?.confirmText || 'OK' }}
            </button>
          </div>
        </div>
      </div>
    }
  `
})
export class ModalComponent {
  private modalService = inject(ModalService);
  readonly modalState = this.modalService.modalState;
  readonly pinCopied = signal<boolean>(false);

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.modalState()) {
      this.close();
    }
  }

  onBackdropClick(): void {
    this.close();
  }

  close(): void {
    this.modalService.close();
    this.pinCopied.set(false);
  }

  confirm(): void {
    this.modalService.confirm();
    this.pinCopied.set(false);
  }

  copyPinToClipboard(): void {
    const pin = this.getPinFromMessage();
    if (pin) {
      try {
        navigator.clipboard.writeText(pin).then(() => {
          this.pinCopied.set(true);
          setTimeout(() => this.pinCopied.set(false), 2500);
        }).catch(() => {
          // Fallback for browsers that don't support clipboard API
          this.fallbackCopy(pin);
        });
      } catch {
        this.fallbackCopy(pin);
      }
    }
  }

  private fallbackCopy(text: string): void {
    // Create a temporary textarea element
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    try {
      document.execCommand('copy');
      this.pinCopied.set(true);
      setTimeout(() => this.pinCopied.set(false), 2500);
    } catch {
      // Silent fail - copy didn't work
    } finally {
      document.body.removeChild(textarea);
    }
  }

  getMessageWithoutPin(): string {
    const message = this.modalState()?.message || '';
    const pin = this.getPinFromMessage();
    if (pin) {
      // Remove "Share PIN XXXXXX" pattern from message
      return message.replace(new RegExp(`\\s*Share\\s+PIN\\s+${pin}\\s*`, 'i'), '').trim();
    }
    return message;
  }

  getPinFromMessage(): string | null {
    const message = this.modalState()?.message || '';
    // Look for 6-digit PIN pattern with word boundaries
    const pinMatch = message.match(/\b(\d{6})\b/);
    return pinMatch ? pinMatch[1] : null;
  }

  get iconName(): IconName {
    switch (this.modalState()?.type) {
      case 'error':
        return 'alert-circle';
      case 'warning':
        return 'alert-triangle';
      case 'success':
        return 'check-circle-2';
      case 'info':
      default:
        return 'info';
    }
  }

  get iconBadgeClass(): string {
    switch (this.modalState()?.type) {
      case 'error':
        return 'bg-rose-50 dark:bg-rose-950/50 border border-rose-100 dark:border-rose-900/60 ring-4 ring-rose-50/50 dark:ring-rose-900/20';
      case 'warning':
        return 'bg-amber-50 dark:bg-amber-950/50 border border-amber-100 dark:border-amber-900/60 ring-4 ring-amber-50/50 dark:ring-amber-900/20';
      case 'success':
        return 'bg-emerald-50 dark:bg-emerald-950/50 border border-emerald-100 dark:border-emerald-900/60 ring-4 ring-emerald-50/50 dark:ring-emerald-900/20';
      case 'info':
      default:
        return 'bg-blue-50 dark:bg-blue-950/50 border border-blue-100 dark:border-blue-900/60 ring-4 ring-blue-50/50 dark:ring-blue-900/20';
    }
  }

  get iconColorClass(): string {
    switch (this.modalState()?.type) {
      case 'error':
        return 'text-rose-600 dark:text-rose-400';
      case 'warning':
        return 'text-amber-600 dark:text-amber-400';
      case 'success':
        return 'text-emerald-600 dark:text-emerald-400';
      case 'info':
      default:
        return 'text-blue-600 dark:text-blue-400';
    }
  }

  get confirmButtonClass(): string {
    switch (this.modalState()?.type) {
      case 'error':
        return 'bg-rose-600 hover:bg-rose-700 shadow-rose-600/20';
      case 'warning':
        return 'bg-amber-600 hover:bg-amber-700 shadow-amber-600/20';
      case 'success':
        return 'bg-emerald-600 hover:bg-emerald-700 shadow-emerald-600/20';
      case 'info':
      default:
        return 'bg-blue-600 hover:bg-blue-700 shadow-blue-600/20';
    }
  }
}
