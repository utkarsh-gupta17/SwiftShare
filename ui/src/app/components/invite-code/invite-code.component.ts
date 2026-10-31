import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-invite-code',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    @if (token) {
      <div class="mt-6 p-5 sm:p-6 bg-gradient-to-br from-emerald-500/10 via-teal-500/5 to-blue-500/10 dark:from-emerald-950/30 dark:via-teal-950/20 dark:to-blue-950/30 border border-emerald-500/20 dark:border-emerald-500/30 rounded-2xl shadow-sm relative overflow-hidden animate-in fade-in slide-in-from-bottom-2 duration-300">
        <!-- Accent glow -->
        <div class="absolute -top-12 -right-12 w-32 h-32 bg-emerald-400/20 dark:bg-emerald-500/10 rounded-full blur-2xl pointer-events-none"></div>

        <div class="flex items-center justify-between flex-wrap gap-2 mb-3">
          <div class="flex items-center gap-2">
            <span class="relative flex h-2.5 w-2.5">
              <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span class="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
            </span>
            <h3 class="text-base sm:text-lg font-bold text-emerald-950 dark:text-emerald-200">
              File Ready to Share!
            </h3>
          </div>
          <span class="text-xs font-semibold px-2.5 py-1 rounded-full bg-emerald-100 dark:bg-emerald-950/80 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
            Session Active
          </span>
        </div>

        <p class="text-xs sm:text-sm text-emerald-800/90 dark:text-emerald-300/80 mb-4">
          Share this 6-digit access PIN with your recipient. They will need it to download the file directly from you:
        </p>

        <!-- Access PIN box -->
        <div class="flex items-center gap-2">
          <div class="flex-1 bg-white/95 dark:bg-slate-950/90 px-4 py-3 rounded-xl border border-emerald-200 dark:border-emerald-800/50 shadow-inner flex items-center justify-between">
            <div class="flex items-center gap-3">
              <span class="text-xs uppercase tracking-wider text-slate-400 dark:text-slate-500 font-semibold select-none">PIN</span>
              <span class="font-mono text-2xl sm:text-3xl font-extrabold text-slate-900 dark:text-emerald-300 tracking-widest">
                {{ token }}
              </span>
            </div>
            @if (copied()) {
              <span class="text-xs font-semibold text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/60 px-2.5 py-1 rounded-md border border-emerald-200 dark:border-emerald-800 animate-in fade-in">
                Copied!
              </span>
            }
          </div>

          <button
            type="button"
            (click)="copyPinToClipboard()"
            class="h-[58px] px-5 rounded-xl font-semibold text-white transition-all flex items-center justify-center gap-2 shadow-md"
            [ngClass]="copied() ? 'bg-emerald-600 shadow-emerald-500/20' : 'bg-emerald-600 hover:bg-emerald-700 shadow-emerald-500/20 active:scale-95'"
            aria-label="Copy access PIN"
          >
            @if (copied()) {
              <app-icon name="check" [size]="20" />
              <span class="hidden sm:inline text-sm">Copied</span>
            } @else {
              <app-icon name="copy" [size]="20" />
              <span class="hidden sm:inline text-sm">Copy PIN</span>
            }
          </button>
        </div>

        <!-- Optional network port indicator -->
        @if (port) {
          <div class="mt-3 flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
            <span class="font-medium">Direct Peer Port:</span>
            <span class="font-mono px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300">
              {{ port }}
            </span>
          </div>
        }

        <div class="mt-4 flex items-center justify-between flex-wrap gap-2 text-xs text-slate-500 dark:text-slate-400 pt-3 border-t border-emerald-500/10">
          <p class="flex items-center gap-1.5">
            <app-icon name="shield" [size]="14" className="text-emerald-500" />
            File is automatically removed once the download finishes.
          </p>

          @if (onReset.observed) {
            <button
              type="button"
              (click)="onReset.emit()"
              class="text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 font-medium hover:underline inline-flex items-center gap-1"
            >
              Share another file
            </button>
          }
        </div>
      </div>
    }
  `
})
export class InviteCodeComponent {
  @Input() port: number | null = null;
  @Input() token: string | null = null;
  @Output() onReset = new EventEmitter<void>();

  readonly copied = signal<boolean>(false);

  copyPinToClipboard(): void {
    const textToCopy = this.token || (this.port ? this.port.toString() : '');
    if (!textToCopy) return;

    navigator.clipboard.writeText(textToCopy);
    this.copied.set(true);
    setTimeout(() => {
      this.copied.set(false);
    }, 2500);
  }
}
