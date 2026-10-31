export const environment = {
  production: true,
  // By default, uses '/api' or runtime window.__ENV__.API_BASE_URL
  apiBaseUrl: (typeof window !== 'undefined' && (window as any).__ENV__?.API_BASE_URL) || '/api',
};
