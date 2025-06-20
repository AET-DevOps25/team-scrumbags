declare global {
  interface Window {
    env: {
      production?: boolean;
      apiUrl?: string;
      keycloakUrl?: string;
      keycloakRealm?: string;
      keycloakClient?: string;
      debug?: boolean;
    };
  }
}

export const environment = {
  production: window.env?.production ?? true,
  apiUrl: window.env?.apiUrl ?? 'http://localhost:8080',
  keycloakUrl: window.env?.keycloakUrl ?? 'http://localhost:8081',
  keycloakRealm: window.env?.keycloakRealm ?? 'trace',
  keycloakClient: window.env?.keycloakClient ?? 'trace-api',
  debug: window.env?.debug ?? false,
};
