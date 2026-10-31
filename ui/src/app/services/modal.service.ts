import { Injectable, signal } from '@angular/core';
import { ModalOptions, ModalType } from '../models/modal.model';

@Injectable({
  providedIn: 'root'
})
export class ModalService {
  private readonly _modalState = signal<ModalOptions | null>(null);
  readonly modalState = this._modalState.asReadonly();

  show(options: ModalOptions): void {
    this._modalState.set({
      type: 'info',
      confirmText: 'Understood',
      ...options,
    });
  }

  error(title: string, message: string, details?: string): void {
    this.show({
      type: 'error',
      title,
      message,
      details,
      confirmText: 'Dismiss',
    });
  }

  warning(title: string, message: string, details?: string): void {
    this.show({
      type: 'warning',
      title,
      message,
      details,
      confirmText: 'Got It',
    });
  }

  success(title: string, message: string, details?: string): void {
    this.show({
      type: 'success',
      title,
      message,
      details,
      confirmText: 'Great!',
    });
  }

  info(title: string, message: string, details?: string): void {
    this.show({
      type: 'info',
      title,
      message,
      details,
      confirmText: 'Close',
    });
  }

  close(): void {
    const current = this._modalState();
    if (current?.onCancel) {
      current.onCancel();
    }
    this._modalState.set(null);
  }

  confirm(): void {
    const current = this._modalState();
    if (current?.onConfirm) {
      current.onConfirm();
    }
    this._modalState.set(null);
  }
}
