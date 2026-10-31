import { Injectable, signal, effect } from '@angular/core';

export type Theme = 'light' | 'dark';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly STORAGE_KEY = 'swiftshare-theme';
  readonly currentTheme = signal<Theme>(this.getInitialTheme());

  constructor() {
    // Apply theme whenever signal changes
    effect(() => {
      const theme = this.currentTheme();
      this.applyTheme(theme);
      if (typeof window !== 'undefined') {
        localStorage.setItem(this.STORAGE_KEY, theme);
      }
    });

    // Listen to OS preference change if on browser
    if (typeof window !== 'undefined') {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
        // Only adapt if user hasn't saved a custom choice
        if (!localStorage.getItem(this.STORAGE_KEY)) {
          this.currentTheme.set(e.matches ? 'dark' : 'light');
        }
      });
    }
  }

  toggleTheme(): void {
    this.currentTheme.update(prev => (prev === 'light' ? 'dark' : 'light'));
  }

  setTheme(theme: Theme): void {
    this.currentTheme.set(theme);
  }

  get isDark(): boolean {
    return this.currentTheme() === 'dark';
  }

  private getInitialTheme(): Theme {
    if (typeof window === 'undefined') return 'light';

    const saved = localStorage.getItem(this.STORAGE_KEY) as Theme | null;
    if (saved === 'light' || saved === 'dark') {
      return saved;
    }

    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  private applyTheme(theme: Theme): void {
    if (typeof document === 'undefined') return;

    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }
}
