import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import {
  provideKeycloak,
  includeBearerTokenInterceptor,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  createInterceptorCondition,
  IncludeBearerTokenCondition
} from 'keycloak-angular';

import { routes } from './app.routes';

// Configure which URLs should include the Bearer token
const urlCondition = createInterceptorCondition<IncludeBearerTokenCondition>({
  urlPattern: /^(http:\/\/localhost:8080)(\/.*)?$/i,
  bearerPrefix: 'Bearer'
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideAnimationsAsync(),
    provideKeycloak({
      config: {
        url: window.env?.keycloakUrl || 'http://localhost:8081',
        realm: window.env?.keycloakRealm || 'trace',
        clientId: window.env?.keycloakClient || 'trace-api'
      },
      initOptions: {
        onLoad: 'login-required',
        checkLoginIframe: false, // Disable iframe checking
        flow: 'standard'
      }
    }),
    {
      provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
      useValue: [urlCondition]
    },
    provideRouter(routes),
    provideHttpClient(withInterceptors([includeBearerTokenInterceptor]))
  ]
};