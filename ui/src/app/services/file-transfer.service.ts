import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface UploadResponse {
  port: number;
  token: string;
}

export interface DownloadResult {
  filename: string;
  size: number;
}

@Injectable({
  providedIn: 'root'
})
export class FileTransferService {
  private http = inject(HttpClient);

  private get baseUrl(): string {
    const raw = environment.apiBaseUrl || '/api';
    return raw.endsWith('/') ? raw.slice(0, -1) : raw;
  }

  // Upload file as multipart/form-data and track real-time upload progress
  uploadFileWithProgress(file: File): Observable<{ progress: number; result?: UploadResponse }> {
    const formData = new FormData();
    formData.append('file', file, file.name);

    const req = new HttpRequest('POST', `${this.baseUrl}/upload`, formData, {
      reportProgress: true
    });

    return new Observable(observer => {
      this.http.request<UploadResponse>(req).subscribe({
        next: (event: HttpEvent<UploadResponse>) => {
          if (event.type === HttpEventType.UploadProgress && event.total) {
            const percent = Math.round((100 * event.loaded) / event.total);
            observer.next({ progress: percent });
          } else if (event instanceof HttpResponse) {
            observer.next({ progress: 100, result: event.body as UploadResponse });
            observer.complete();
          }
        },
        error: (err) => observer.error(err)
      });
    });
  }

  // Download file by PIN (and optional port), parsing filename from Content-Disposition header
  async downloadFile(token: string, port?: number): Promise<DownloadResult> {
    const cleanToken = (token || '').trim();
    // Skip the port path segment entirely — the backend resolves port from the PIN itself
    const url = `${this.baseUrl}/download?token=${encodeURIComponent(cleanToken)}`;

    const response = await firstValueFrom(
      this.http.get(url, {
        responseType: 'blob',
        observe: 'response',
      })
    );

    if (!response || !response.body) {
      throw new Error('No file data received from server');
    }

    const blob = response.body;

    // Extract filename from headers (supports case-insensitive check and UTF-8 encoding)
    let filename = 'downloaded-file';
    let contentDisposition = response.headers.get('content-disposition') || '';

    if (!contentDisposition) {
      for (const key of response.headers.keys()) {
        if (key.toLowerCase() === 'content-disposition') {
          contentDisposition = response.headers.get(key) || '';
          break;
        }
      }
    }

    if (contentDisposition) {
      const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
      if (utf8Match && utf8Match[1]) {
        try {
          filename = decodeURIComponent(utf8Match[1].trim());
        } catch {
          filename = utf8Match[1].trim();
        }
      } else {
        const quotedMatch = contentDisposition.match(/filename="([^"]+)"/i);
        if (quotedMatch && quotedMatch[1]) {
          filename = quotedMatch[1];
        } else {
          const unquotedMatch = contentDisposition.match(/filename=([^; ]+)/i);
          if (unquotedMatch && unquotedMatch[1]) {
            filename = unquotedMatch[1].trim();
          }
        }
      }
    }

    // Trigger browser download and revoke object URL
    const blobUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = blobUrl;
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => window.URL.revokeObjectURL(blobUrl), 1500);

    return {
      filename,
      size: blob.size,
    };
  }

  // Format bytes for human-friendly file size display
  formatBytes(bytes: number, decimals: number = 1): string {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + units[i];
  }
}
