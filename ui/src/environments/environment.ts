export const environment = {
  production: false,
  // By default, uses '/api' which proxy.conf.json forwards to http://localhost:8080
  // Can be configured to direct URL if needed
  apiBaseUrl: (typeof window !== 'undefined' && (window as any).__ENV__?.API_BASE_URL) || '/api',
};
