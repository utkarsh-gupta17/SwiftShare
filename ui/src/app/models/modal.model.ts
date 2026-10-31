export type ModalType = 'error' | 'warning' | 'success' | 'info';

export interface ModalOptions {
  title: string;
  message: string;
  details?: string;
  type?: ModalType;
  confirmText?: string;
  cancelText?: string;
  showCancel?: boolean;
  onConfirm?: () => void;
  onCancel?: () => void;
}
