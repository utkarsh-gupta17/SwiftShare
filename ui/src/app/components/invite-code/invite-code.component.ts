import { Component, EventEmitter, Input, Output, signal, computed, OnInit, OnDestroy, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-invite-code',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    @if (isVisible()) {
      <div
        class="mt-4 p-4 sm:p-5 rounded-2xl border transition-all duration-300 animate-in slide-in-from-bottom-2"
        [ngClass]="cardStyleClass"
        [@fadeSlideOut]="!isVisible()"
      >
        <!-- Header: Session info + Timer + Dismiss -->
        <div class="flex items-center justify-between gap-3 mb-3">
          <div class="flex items-center gap-3 min-w-0">
            <!-- Session badge -->
            @if (sessionNumber !== null) {
              <span class="flex-shrink-0 w-7 h-7 rounded-lg text-xs font-bold flex items-center justify-center" [ngClass]="sessionBadgeClass">
                #{{ sessionNumber }}
              </span>
            }

            <!-- File info -->
            @if (fileName) {
              <div class="min-w-0">
                <p class="text-sm font-semibold text-slate-800 dark:text-slate-200 truncate max-w-[180px] sm:max-w-[280px]" [title]="fileName">
                  {{ fileName }}
                </p>
                @if (fileSize) {
                  <p class="text-xs text-slate-500 dark:text-slate-400">{{ formatFileSize(fileSize) }}</p>
                }
              </div>
            }
          </div>

          <div class="flex items-center gap-2 flex-shrink-0">
            <!-- Timer -->
            <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold" [ngClass]="timerClass">
              <app-icon name="clock" [size]="12" />
              {{ formattedTimeLeft() }}
            </span>

            <!-- Dismiss button -->
            @if (showDismiss) {
              <button
                type="button"
                (click)="dismiss()"
                class="p-1.5 rounded-lg transition-colors"
                [ngClass]="dismissButtonClass"
                title="Remove session"
                aria-label="Remove session"
              >
                <app-icon name="x" [size]="14" />
              </button>
            }
          </div>
        </div>

        <!-- PIN Display -->
        <div class="flex items-center gap-3">
          <div class="flex-1 bg-white/95 dark:bg-slate-950/90 px-4 py-2.5 rounded-xl border border-white/50 dark:border-slate-700/50 shadow-inner">
            <div class="flex items-center justify-between gap-3">
              <div class="flex items-center gap-2">
                <span class="text-xs uppercase tracking-wider text-slate-400 dark:text-slate-500 font-semibold select-none">PIN</span>
                <span class="font-mono text-xl sm:text-2xl font-extrabold bg-gradient-to-r from-emerald-600 to-teal-600 dark:from-emerald-400 dark:to-teal-400 bg-clip-text text-transparent tracking-widest">
                  {{ token }}
                </span>
              </div>
              @if (copied()) {
                <span class="flex items-center gap-1.5 text-xs font-semibold text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-950/60 px-2.5 py-1 rounded-md border border-emerald-200 dark:border-emerald-800 animate-in fade-in">
                  <app-icon name="check" [size]="12" />
                  Copied!
                </span>
              }
            </div>
          </div>

          <button
            type="button"
            (click)="copyPinToClipboard()"
            class="h-[48px] px-4 rounded-xl font-semibold text-white transition-all flex items-center justify-center gap-2 shadow-md flex-shrink-0"
            [ngClass]="copyButtonClass"
            aria-label="Copy access PIN"
          >
            @if (copied()) {
              <app-icon name="check" [size]="18" />
              <span class="hidden sm:inline text-sm">Copied</span>
            } @else {
              <app-icon name="copy" [size]="18" />
              <span class="hidden sm:inline text-sm">Copy</span>
            }
          </button>
        </div>

        <!-- Peer port indicator -->
        @if (port) {
          <div class="mt-2.5 flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
            <app-icon name="server" [size]="13" />
            <span>Peer Port:</span>
            <span class="font-mono px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300">{{ port }}</span>
          </div>
        }
      </div>
    }
  `,
  styles: [`
    @keyframes fadeSlideOut {
      from {
        opacity: 1;
        transform: translateY(0);
        max-height: 200px;
      }
      to {
        opacity: 0;
        transform: translateY(-10px);
        max-height: 0;
        margin: 0;
        padding: 0;
      }
    }

    .animate-fade-out {
      animation: fadeSlideOut 0.4s ease-out forwards;
    }
  `]
})
export class InviteCodeComponent implements OnInit, OnDestroy, OnChanges {
  @Input() port: number | null = null;
  @Input() token: string | null = null;
  @Input() fileName: string = '';
  @Input() fileSize: number = 0;
  @Input() sessionNumber: number | null = null;
  @Input() expiresInSeconds: number = 90; // Default 1.5 minutes
  @Input() showDismiss: boolean = true;
  @Input() colorScheme: 'emerald' | 'blue' | 'violet' = 'emerald';
  @Output() onExpire = new EventEmitter<void>();
  @Output() onDismiss = new EventEmitter<void>();
  @Output() onReset = new EventEmitter<void>();

  readonly copied = signal<boolean>(false);
  readonly timeLeft = signal<number>(90);
  readonly isVisible = signal<boolean>(true);
  private timerInterval: ReturnType<typeof setInterval> | null = null;
  private isExpired = false;

  readonly formattedTimeLeft = computed(() => {
    const seconds = this.timeLeft();
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  });

  get cardStyleClass(): string {
    const base = 'bg-gradient-to-br border-slate-200/50 dark:border-slate-700/50 shadow-sm';
    switch (this.colorScheme) {
      case 'emerald':
        return `${base} from-emerald-50/80 via-teal-50/40 to-blue-50/40 dark:from-emerald-950/40 dark:via-teal-950/30 dark:to-blue-950/30 border-emerald-200/30 dark:border-emerald-800/30`;
      case 'blue':
        return `${base} from-blue-50/80 via-indigo-50/40 to-violet-50/40 dark:from-blue-950/40 dark:via-indigo-950/30 dark:to-violet-950/30 border-blue-200/30 dark:border-blue-800/30`;
      case 'violet':
        return `${base} from-violet-50/80 via-purple-50/40 to-fuchsia-50/40 dark:from-violet-950/40 dark:via-purple-950/30 dark:to-fuchsia-950/30 border-violet-200/30 dark:border-violet-800/30`;
    }
  }

  get sessionBadgeClass(): string {
    switch (this.colorScheme) {
      case 'emerald':
        return 'bg-emerald-100 dark:bg-emerald-900/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-700';
      case 'blue':
        return 'bg-blue-100 dark:bg-blue-900/60 text-blue-700 dark:text-blue-300 border border-blue-200 dark:border-blue-700';
      case 'violet':
        return 'bg-violet-100 dark:bg-violet-900/60 text-violet-700 dark:text-violet-300 border border-violet-200 dark:border-violet-700';
    }
  }

  get timerClass(): string {
    const seconds = this.timeLeft();
    if (seconds <= 10) {
      return 'bg-rose-100 dark:bg-rose-950/60 text-rose-700 dark:text-rose-300 border border-rose-200 dark:border-rose-800';
    } else if (seconds <= 30) {
      return 'bg-amber-100 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800';
    }
    switch (this.colorScheme) {
      case 'emerald':
        return 'bg-emerald-100 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800';
      case 'blue':
        return 'bg-blue-100 dark:bg-blue-950/60 text-blue-700 dark:text-blue-300 border border-blue-200 dark:border-blue-800';
      case 'violet':
        return 'bg-violet-100 dark:bg-violet-950/60 text-violet-700 dark:text-violet-300 border border-violet-200 dark:border-violet-800';
    }
  }

  get copyButtonClass(): string {
    const base = 'active:scale-95';
    switch (this.colorScheme) {
      case 'emerald':
        return `${base} ${this.copied() ? 'bg-emerald-500' : 'bg-emerald-600 hover:bg-emerald-700'} shadow-emerald-500/20`;
      case 'blue':
        return `${base} ${this.copied() ? 'bg-blue-500' : 'bg-blue-600 hover:bg-blue-700'} shadow-blue-500/20`;
      case 'violet':
        return `${base} ${this.copied() ? 'bg-violet-500' : 'bg-violet-600 hover:bg-violet-700'} shadow-violet-500/20`;
    }
  }

  get dismissButtonClass(): string {
    switch (this.colorScheme) {
      case 'emerald':
        return 'text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300 hover:bg-emerald-100 dark:hover:bg-emerald-900/40';
      case 'blue':
        return 'text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300 hover:bg-blue-100 dark:hover:bg-blue-900/40';
      case 'violet':
        return 'text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300 hover:bg-violet-100 dark:hover:bg-violet-900/40';
    }
  }

  ngOnInit(): void {
    this.timeLeft.set(this.expiresInSeconds);
    this.startTimer();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['expiresInSeconds']) {
      this.timeLeft.set(this.expiresInSeconds);
      this.restartTimer();
    }
  }

  ngOnDestroy(): void {
    this.stopTimer();
  }

  private startTimer(): void {
    this.stopTimer();
    this.timerInterval = setInterval(() => {
      const current = this.timeLeft();
      if (current > 0) {
        this.timeLeft.update(t => t - 1);
      } else if (!this.isExpired) {
        this.isExpired = true;
        this.triggerExpiry();
      }
    }, 1000);
  }

  private stopTimer(): void {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
  }

  private restartTimer(): void {
    this.stopTimer();
    this.isExpired = false;
    this.startTimer();
  }

  private triggerExpiry(): void {
    this.stopTimer();
    // Animate out then emit
    this.isVisible.set(false);
    setTimeout(() => {
      this.onExpire.emit();
    }, 400); // Wait for animation to complete
  }

  dismiss(): void {
    this.stopTimer();
    this.isVisible.set(false);
    setTimeout(() => {
      this.onDismiss.emit();
    }, 400);
  }

  copyPinToClipboard(): void {
    const textToCopy = this.token || '';
    if (!textToCopy) return;

    navigator.clipboard.writeText(textToCopy);
    this.copied.set(true);
    setTimeout(() => {
      this.copied.set(false);
    }, 2500);
  }

  formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }
}
