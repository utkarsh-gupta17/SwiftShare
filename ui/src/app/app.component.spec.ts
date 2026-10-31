import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { AppComponent } from './app.component';
import { ModalService } from './services/modal.service';
import { FileTransferService } from './services/file-transfer.service';
import { ThemeService } from './services/theme.service';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(),
        ModalService,
        FileTransferService,
        ThemeService,
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should default to the upload tab', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app.activeTab()).toEqual('upload');
  });

  it('should toggle tabs between upload and download', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    app.setActiveTab('download');
    expect(app.activeTab()).toEqual('download');
    app.setActiveTab('upload');
    expect(app.activeTab()).toEqual('upload');
  });

  it('should toggle light and dark theme mode', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    const initialTheme = app.themeService.currentTheme();
    app.themeService.toggleTheme();
    expect(app.themeService.currentTheme()).not.toEqual(initialTheme);
  });
});
